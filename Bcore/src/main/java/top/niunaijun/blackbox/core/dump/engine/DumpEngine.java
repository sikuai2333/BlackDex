package top.niunaijun.blackbox.core.dump.engine;

public interface DumpEngine {
    String name();

    EngineResult dump(DumpContext context);
}
