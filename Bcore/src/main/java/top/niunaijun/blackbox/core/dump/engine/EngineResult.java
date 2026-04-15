package top.niunaijun.blackbox.core.dump.engine;

public class EngineResult {
    public final String engine;
    public final boolean success;
    public final boolean skipped;
    public final int dexCount;
    public final long durationMs;
    public final String message;

    public EngineResult(String engine, boolean success, boolean skipped, int dexCount, long durationMs, String message) {
        this.engine = engine;
        this.success = success;
        this.skipped = skipped;
        this.dexCount = dexCount;
        this.durationMs = durationMs;
        this.message = message;
    }

    public static EngineResult skipped(String engine, long durationMs, String message) {
        return new EngineResult(engine, false, true, 0, durationMs, message);
    }
}
