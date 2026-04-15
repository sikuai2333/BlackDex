package top.niunaijun.blackbox.core.dump.engine;

import java.io.File;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.VMCore;

public class HookDumpEngine implements DumpEngine {

    @Override
    public String name() {
        return "hook";
    }

    @Override
    public EngineResult dump(DumpContext context) {
        long start = System.currentTimeMillis();
        if (!BlackBoxCore.get().isEnableHookDump()) {
            return EngineResult.skipped(name(), System.currentTimeMillis() - start, "hook dump disabled");
        }
        try {
            VMCore.triggerHookDump(context.packageName);
            int dexCount = countDex(context.dumpDir);
            return new EngineResult(name(), dexCount > 0, false, dexCount, System.currentTimeMillis() - start, "ok");
        } catch (Throwable e) {
            return new EngineResult(name(), false, false, 0, System.currentTimeMillis() - start, e.getMessage());
        }
    }

    private int countDex(String path) {
        File dir = new File(path);
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".dex")) {
                count++;
            }
        }
        return count;
    }
}
