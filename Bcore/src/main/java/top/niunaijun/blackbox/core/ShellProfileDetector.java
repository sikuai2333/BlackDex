package top.niunaijun.blackbox.core;

import java.util.Locale;

/**
 * Lightweight shell profile detector.
 *
 * The signatures below are common indicators widely discussed in Android
 * reverse-engineering communities, e.g. 360/梆梆/爱加密 wrapper entry classes.
 */
public class ShellProfileDetector {

    public static final String SHELL_UNKNOWN = "unknown";
    public static final String SHELL_QIHOO_360 = "360";
    public static final String SHELL_BANGCLE = "bangcle";
    public static final String SHELL_IJIAMI = "ijiami";

    private static final String[] QIHOO_MARKERS = new String[]{
            "com.stub.StubApp",
            "com.qihoo.util.StubApp"
    };

    private static final String[] BANGCLE_MARKERS = new String[]{
            "com.secneo.apkwrapper.ApplicationWrapper",
            "com.secneo.guard.Application"
    };

    private static final String[] IJIAMI_MARKERS = new String[]{
            "s.h.e.l.l.S",
            "com.shell.SuperApplication"
    };

    private ShellProfileDetector() {
    }

    public static String detect(ClassLoader classLoader, String packageName) {
        if (containsAny(classLoader, QIHOO_MARKERS) || containsKeyword(packageName, "qihoo", "jiagu", "360")) {
            return SHELL_QIHOO_360;
        }

        if (containsAny(classLoader, BANGCLE_MARKERS) || containsKeyword(packageName, "secneo", "bangcle")) {
            return SHELL_BANGCLE;
        }

        if (containsAny(classLoader, IJIAMI_MARKERS) || containsKeyword(packageName, "ijiami", "ijiagu")) {
            return SHELL_IJIAMI;
        }

        return SHELL_UNKNOWN;
    }

    public static int[] dumpDelayStrategy(String shellProfile, boolean fixCodeItem) {
        if (SHELL_QIHOO_360.equals(shellProfile) || SHELL_BANGCLE.equals(shellProfile)) {
            return fixCodeItem ? new int[]{500, 2000, 6000, 10000, 15000} : new int[]{500, 2000, 5000, 9000};
        }
        if (SHELL_IJIAMI.equals(shellProfile)) {
            return fixCodeItem ? new int[]{500, 1500, 4000, 7000, 12000} : new int[]{500, 1500, 3500, 6500};
        }
        return fixCodeItem ? new int[]{500, 1500, 3500, 7000} : new int[]{500, 1500, 3000};
    }

    private static boolean containsAny(ClassLoader classLoader, String[] markers) {
        if (classLoader == null) {
            return false;
        }
        for (String marker : markers) {
            try {
                Class.forName(marker, false, classLoader);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean containsKeyword(String input, String... keywords) {
        if (input == null) {
            return false;
        }
        String value = input.toLowerCase(Locale.US);
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
