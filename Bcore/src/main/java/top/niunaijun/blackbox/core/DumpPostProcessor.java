package top.niunaijun.blackbox.core;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import top.niunaijun.blackbox.utils.CloseUtils;
import top.niunaijun.blackbox.utils.DexUtils;
import top.niunaijun.blackbox.utils.FileUtils;

/**
 * Post processing for dumped dex files.
 *
 * Capabilities:
 * 1) DEX header validation
 * 2) Magic-offset carve to generate *_carved.dex
 * 3) Header magic repair to generate *_repaired.dex
 * 4) SHA-256 dedup statistics
 */
public class DumpPostProcessor {

    private static final String TAG = "DumpPostProcessor";
    private static final byte[] DEFAULT_MAGIC = new byte[]{0x64, 0x65, 0x78, 0x0a, 0x30, 0x33, 0x35, 0x00};
    private static final String REPORT_FILE = "dump_postprocess_report.json";

    private DumpPostProcessor() {
    }

    public static Result process(File dumpDir) {
        Result result = new Result();
        if (dumpDir == null || !dumpDir.exists() || !dumpDir.isDirectory()) {
            return result;
        }

        List<File> baseDexFiles = listBaseDexFiles(dumpDir);
        result.totalScanned = baseDexFiles.size();

        for (File dex : baseDexFiles) {
            byte[] bytes = readBytes(dex);
            if (bytes == null || bytes.length == 0) {
                continue;
            }

            if (isValidDexHeader(bytes, 0)) {
                result.headerValidCount++;
                continue;
            }

            int magicOffset = findMagicOffset(bytes);
            if (magicOffset > 0 && magicOffset < bytes.length) {
                File carved = new File(dex.getParentFile(), baseName(dex) + "_carved.dex");
                byte[] carvedBytes = new byte[bytes.length - magicOffset];
                System.arraycopy(bytes, magicOffset, carvedBytes, 0, carvedBytes.length);
                if (writeBytes(carved, carvedBytes)) {
                    DexUtils.fixDex(carved);
                    result.carvedCount++;
                }
            }

            if (bytes.length >= DEFAULT_MAGIC.length) {
                File repaired = new File(dex.getParentFile(), baseName(dex) + "_repaired.dex");
                byte[] repairedBytes = bytes.clone();
                System.arraycopy(DEFAULT_MAGIC, 0, repairedBytes, 0, DEFAULT_MAGIC.length);
                if (writeBytes(repaired, repairedBytes)) {
                    DexUtils.fixDex(repaired);
                    result.repairedCount++;
                }
            }
        }

        result.computeDedupStats(dumpDir);
        writeReport(dumpDir, result);
        Log.d(TAG, "post process summary: " + result.toString());
        return result;
    }

    private static List<File> listBaseDexFiles(File dumpDir) {
        File[] files = dumpDir.listFiles();
        List<File> out = new ArrayList<>();
        if (files == null) {
            return out;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (!name.endsWith(".dex")) {
                continue;
            }
            if (name.endsWith("_carved.dex") || name.endsWith("_repaired.dex") || name.endsWith("_fix.dex")) {
                continue;
            }
            out.add(file);
        }
        return out;
    }

    private static String baseName(File file) {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        if (idx > 0) {
            return name.substring(0, idx);
        }
        return name;
    }

    private static boolean isValidDexHeader(byte[] data, int offset) {
        if (data == null || data.length - offset < 8) {
            return false;
        }
        return data[offset] == 0x64
                && data[offset + 1] == 0x65
                && data[offset + 2] == 0x78
                && data[offset + 3] == 0x0a
                && Character.isDigit((char) data[offset + 4])
                && Character.isDigit((char) data[offset + 5])
                && Character.isDigit((char) data[offset + 6])
                && data[offset + 7] == 0x00;
    }

    private static int findMagicOffset(byte[] data) {
        if (data == null || data.length < 8) {
            return -1;
        }
        for (int i = 0; i <= data.length - 8; i++) {
            if (isValidDexHeader(data, i)) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] readBytes(File file) {
        try {
            return FileUtils.toByteArray(file);
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean writeBytes(File target, byte[] bytes) {
        try {
            FileUtils.writeToFile(bytes, target);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static String sha256(File file) {
        FileInputStream input = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            input = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String hex = Integer.toHexString((b & 0xFF));
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Throwable e) {
            return "";
        } finally {
            CloseUtils.close(input);
        }
    }

    private static void writeReport(File dumpDir, Result result) {
        FileOutputStream output = null;
        try {
            JSONObject report = new JSONObject();
            report.put("totalScanned", result.totalScanned);
            report.put("headerValidCount", result.headerValidCount);
            report.put("carvedCount", result.carvedCount);
            report.put("repairedCount", result.repairedCount);
            report.put("uniqueSha256", result.uniqueSha256);
            report.put("duplicateSha256", result.duplicateSha256);
            JSONArray duplicateGroups = new JSONArray();
            for (Map.Entry<String, Integer> entry : result.shaFrequency.entrySet()) {
                if (entry.getValue() <= 1) {
                    continue;
                }
                JSONObject group = new JSONObject();
                group.put("sha256", entry.getKey());
                group.put("count", entry.getValue());
                duplicateGroups.put(group);
            }
            report.put("duplicateGroups", duplicateGroups);

            output = new FileOutputStream(new File(dumpDir, REPORT_FILE));
            output.write(report.toString(2).getBytes());
            output.flush();
        } catch (Throwable ignored) {
        } finally {
            CloseUtils.close(output);
        }
    }

    public static class Result {
        int totalScanned;
        int headerValidCount;
        int carvedCount;
        int repairedCount;
        int uniqueSha256;
        int duplicateSha256;
        Map<String, Integer> shaFrequency = new HashMap<>();

        void computeDedupStats(File dumpDir) {
            File[] files = dumpDir.listFiles();
            if (files == null) {
                return;
            }
            for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(".dex")) {
                    continue;
                }
                String digest = sha256(file);
                if (digest.isEmpty()) {
                    continue;
                }
                Integer count = shaFrequency.get(digest);
                shaFrequency.put(digest, count == null ? 1 : count + 1);
            }

            uniqueSha256 = shaFrequency.size();
            int totalDex = 0;
            for (Integer value : shaFrequency.values()) {
                totalDex += value;
            }
            duplicateSha256 = Math.max(0, totalDex - uniqueSha256);
        }

        @Override
        public String toString() {
            return "Result{" +
                    "totalScanned=" + totalScanned +
                    ", headerValidCount=" + headerValidCount +
                    ", carvedCount=" + carvedCount +
                    ", repairedCount=" + repairedCount +
                    ", uniqueSha256=" + uniqueSha256 +
                    ", duplicateSha256=" + duplicateSha256 +
                    '}';
        }
    }
}
