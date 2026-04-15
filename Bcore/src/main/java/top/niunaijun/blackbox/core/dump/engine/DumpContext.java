package top.niunaijun.blackbox.core.dump.engine;

public class DumpContext {
    public final ClassLoader classLoader;
    public final String packageName;
    public final String dumpDir;
    public final boolean fixCodeItem;

    public DumpContext(ClassLoader classLoader, String packageName, String dumpDir, boolean fixCodeItem) {
        this.classLoader = classLoader;
        this.packageName = packageName;
        this.dumpDir = dumpDir;
        this.fixCodeItem = fixCodeItem;
    }
}
