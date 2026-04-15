package top.niunaijun.blackdex.data

import android.content.pm.ApplicationInfo
import android.net.Uri
import android.webkit.URLUtil
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.BlackBoxCore.getPackageManager
import top.niunaijun.blackbox.BlackDexCore
import top.niunaijun.blackbox.entity.pm.InstallResult
import top.niunaijun.blackbox.utils.AbiUtils
import top.niunaijun.blackdex.R
import top.niunaijun.blackdex.app.App
import top.niunaijun.blackdex.app.AppManager
import top.niunaijun.blackdex.data.entity.AppInfo
import top.niunaijun.blackdex.data.entity.DumpInfo
import java.io.File

/**
 *
 * @Description:
 * @Author: wukaicheng
 * @CreateDate: 2021/5/23 14:29
 */
class DexDumpRepository {

    private var dumpTaskId = 0

    fun getAppList(mAppListLiveData: MutableLiveData<List<AppInfo>>) {

        val installedApplications: List<ApplicationInfo> =
                getPackageManager().getInstalledApplications(0)
        val installedList = mutableListOf<AppInfo>()

        for (installedApplication in installedApplications) {
            val file = File(installedApplication.sourceDir)

            if ((installedApplication.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue

            if (!AbiUtils.isSupport(file)) continue


            val info = AppInfo(
                    installedApplication.loadLabel(getPackageManager()).toString(),
                    installedApplication.packageName,
                    installedApplication.loadIcon(getPackageManager())
            )
            installedList.add(info)
        }

        mAppListLiveData.postValue(installedList)
    }

    fun dumpDex(source: String, dexDumpLiveData: MutableLiveData<DumpInfo>) {
        val startedAt = System.currentTimeMillis()
        val sourceType = detectSourceType(source)
        dexDumpLiveData.postValue(DumpInfo(DumpInfo.LOADING, App.getContext().getString(R.string.dumping)))

        val attemptResult = dumpDexWithRetry(source, sourceType, dexDumpLiveData)
        if (attemptResult.installResult != null) {
            dumpTaskId++
            startCountdown(
                    installResult = attemptResult.installResult,
                    source = source,
                    sourceType = sourceType,
                    startedAt = startedAt,
                    attempts = attemptResult.attempts,
                    lastError = attemptResult.lastError,
                    dexDumpLiveData = dexDumpLiveData
            )
        } else {
            writeDumpReport(
                    source = source,
                    sourceType = sourceType,
                    packageName = null,
                    startedAt = startedAt,
                    endedAt = System.currentTimeMillis(),
                    attempts = attemptResult.attempts,
                    resultState = DumpInfo.TIMEOUT,
                    message = attemptResult.lastError ?: "dump task timed out"
            )
            dexDumpLiveData.postValue(DumpInfo(DumpInfo.TIMEOUT))
        }
    }


    fun dumpSuccess() {
        dumpTaskId++
    }

    private fun startCountdown(
            installResult: InstallResult,
            source: String,
            sourceType: String,
            startedAt: Long,
            attempts: Int,
            lastError: String?,
            dexDumpLiveData: MutableLiveData<DumpInfo>
    ) {
        GlobalScope.launch {
            val tempId = dumpTaskId
            while (BlackDexCore.get().isRunning) {
                delay(20000)
                //fixCodeItem 需要长时间运行，普通内存dump不需要
                if (!AppManager.mBlackBoxLoader.isFixCodeItem()) {
                    break
                }
            }
            if (tempId == dumpTaskId) {
                val outputDir = File(BlackBoxCore.get().dexDumpDir, installResult.packageName)
                if (BlackDexCore.get().isExistDexFile(installResult.packageName)) {
                    writeDumpReport(
                            source = source,
                            sourceType = sourceType,
                            packageName = installResult.packageName,
                            startedAt = startedAt,
                            endedAt = System.currentTimeMillis(),
                            attempts = attempts,
                            resultState = DumpInfo.SUCCESS,
                            message = "success",
                            outputDir = outputDir
                    )
                    dexDumpLiveData.postValue(DumpInfo(
                            DumpInfo.SUCCESS,
                            App.getContext().getString(R.string.dex_save, outputDir.absolutePath)
                    ))
                } else {
                    writeDumpReport(
                            source = source,
                            sourceType = sourceType,
                            packageName = installResult.packageName,
                            startedAt = startedAt,
                            endedAt = System.currentTimeMillis(),
                            attempts = attempts,
                            resultState = DumpInfo.TIMEOUT,
                            message = lastError ?: "output dex not found",
                            outputDir = outputDir
                    )
                    dexDumpLiveData.postValue(DumpInfo(DumpInfo.TIMEOUT))
                }
            }
        }
    }

    private fun dumpDexWithRetry(
            source: String,
            sourceType: String,
            dexDumpLiveData: MutableLiveData<DumpInfo>
    ): AttemptResult {
        val maxAttempts = if (AppManager.mBlackBoxLoader.isFixCodeItem()) 4 else 2
        var lastError: String? = null

        for (attempt in 1..maxAttempts) {
            dexDumpLiveData.postValue(DumpInfo(DumpInfo.LOADING, "dump attempt $attempt/$maxAttempts"))
            val result = tryDump(source, sourceType)
            if (result != null) {
                return AttemptResult(result, attempt, lastError)
            }
            lastError = "attempt $attempt failed"
        }
        return AttemptResult(null, maxAttempts, lastError)
    }

    private fun tryDump(source: String, sourceType: String): InstallResult? {
        return when (sourceType) {
            SOURCE_TYPE_URL -> BlackDexCore.get().dumpDex(Uri.parse(source))
            SOURCE_TYPE_FILE -> BlackDexCore.get().dumpDex(File(source))
            else -> BlackDexCore.get().dumpDex(source)
        }
    }

    private fun detectSourceType(source: String): String {
        return when {
            URLUtil.isValidUrl(source) -> SOURCE_TYPE_URL
            source.contains("/") -> SOURCE_TYPE_FILE
            else -> SOURCE_TYPE_PACKAGE
        }
    }

    private fun writeDumpReport(
            source: String,
            sourceType: String,
            packageName: String?,
            startedAt: Long,
            endedAt: Long,
            attempts: Int,
            resultState: Int,
            message: String,
            outputDir: File? = null
    ) {
        runCatching {
            val reportDir = File(BlackBoxCore.get().dexDumpDir, REPORT_DIR)
            if (!reportDir.exists()) {
                reportDir.mkdirs()
            }
            val report = JSONObject()
            report.put("source", source)
            report.put("sourceType", sourceType)
            report.put("packageName", packageName ?: "")
            report.put("startedAt", startedAt)
            report.put("endedAt", endedAt)
            report.put("durationMs", endedAt - startedAt)
            report.put("attempts", attempts)
            report.put("fixCodeItem", AppManager.mBlackBoxLoader.isFixCodeItem())
            report.put("hookDump", AppManager.mBlackBoxLoader.isHookDump())
            report.put("resultState", resultState)
            report.put("message", message)

            if (outputDir != null && outputDir.exists()) {
                report.put("outputDir", outputDir.absolutePath)
                val outputFiles = JSONArray()
                outputDir.listFiles()?.sortedBy { it.name }?.forEach {
                    outputFiles.put(it.name)
                }
                report.put("outputFiles", outputFiles)
            }

            val reportName = "dump_report_${endedAt}.json"
            File(reportDir, reportName).writeText(report.toString(2))
        }
    }

    private data class AttemptResult(
            val installResult: InstallResult?,
            val attempts: Int,
            val lastError: String?
    )

    companion object {
        private const val REPORT_DIR = "dump_reports"
        private const val SOURCE_TYPE_URL = "url"
        private const val SOURCE_TYPE_FILE = "file"
        private const val SOURCE_TYPE_PACKAGE = "package"
    }
}
