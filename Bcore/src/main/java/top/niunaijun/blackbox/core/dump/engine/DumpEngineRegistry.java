package top.niunaijun.blackbox.core.dump.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DumpEngineRegistry {

    private static final DumpEngineRegistry sInstance = new DumpEngineRegistry();
    private final List<DumpEngine> engines = new ArrayList<>();

    private DumpEngineRegistry() {
        engines.add(new CookieDumpEngine());
        engines.add(new HookDumpEngine());
    }

    public static DumpEngineRegistry get() {
        return sInstance;
    }

    public synchronized void register(DumpEngine engine) {
        if (engine == null) {
            return;
        }
        for (DumpEngine value : engines) {
            if (value.name().equals(engine.name())) {
                return;
            }
        }
        engines.add(engine);
    }

    public synchronized List<DumpEngine> list() {
        return Collections.unmodifiableList(new ArrayList<>(engines));
    }

    public List<EngineResult> executeAll(DumpContext context) {
        List<EngineResult> out = new ArrayList<>();
        for (DumpEngine engine : list()) {
            out.add(engine.dump(context));
        }
        return out;
    }
}
