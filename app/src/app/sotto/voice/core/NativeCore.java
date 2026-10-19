package app.sotto.voice.core;

import java.util.Map;

/**
 * Bridge to the Rust text engine (libsotto.so).
 *
 * If the native library cannot be loaded — for example on an emulator with an
 * unsupported ABI — the pure-Java {@link Engine} is used instead. Both
 * implementations follow the same rules; the Rust path is simply faster.
 */
public final class NativeCore {

    private static final boolean LOADED;

    static {
        boolean ok;
        try {
            System.loadLibrary("sotto");
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        LOADED = ok;
    }

    private NativeCore() {
    }

    public static boolean available() {
        return LOADED;
    }

    public static String version() {
        if (!LOADED) return "java-fallback";
        try {
            return nativeVersion();
        } catch (Throwable t) {
            return "java-fallback";
        }
    }

    public static int command(String text) {
        if (!LOADED) {
            return Engine.detectCommand(text).ordinal();
        }
        try {
            return nativeCommand(text);
        } catch (Throwable t) {
            return Engine.detectCommand(text).ordinal();
        }
    }

    public static int countWords(String text) {
        if (!LOADED) return Engine.countWords(text);
        try {
            return nativeCountWords(text);
        } catch (Throwable t) {
            return Engine.countWords(text);
        }
    }

    public static String process(String text, Settings settings, String pkg,
                                 Map<String, String> snippets,
                                 Map<String, String> dictionary) {
        return processRaw(text, settings.getStyle(), pkg,
                settings.isFillers(), settings.isSpokenPunctuation(),
                settings.isCapitalize(), settings.isTrailingSpace(),
                settings.isAdaptTone(), snippets, dictionary);
    }

    /**
     * Same pipeline with an explicit style key, used when the per-app tone
     * memory has already chosen a style.
     */
    public static String processRaw(String text, String styleKey, String pkg,
                                    boolean removeFillers, boolean spokenPunctuation,
                                    boolean autoCapitalize, boolean trailingSpace,
                                    boolean adaptTone,
                                    Map<String, String> snippets,
                                    Map<String, String> dictionary) {
        if (LOADED) {
            try {
                return nativeProcess(
                        text,
                        styleKey == null ? "default" : styleKey,
                        pkg == null ? "" : pkg,
                        removeFillers,
                        spokenPunctuation,
                        autoCapitalize,
                        trailingSpace,
                        adaptTone,
                        encode(snippets),
                        encode(dictionary));
            } catch (Throwable ignored) {
                // fall through to the Java implementation
            }
        }
        Engine.Config cfg = new Engine.Config();
        cfg.removeFillers = removeFillers;
        cfg.spokenPunctuation = spokenPunctuation;
        cfg.autoCapitalize = autoCapitalize;
        cfg.trailingSpace = trailingSpace;
        cfg.adaptTone = adaptTone;
        Engine.Style style = Engine.Style.fromKey(styleKey);
        Engine.Result result = Engine.process(text, style, pkg, cfg);
        return result.text;
    }

    private static String encode(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append('\u001e');
            sb.append(e.getKey()).append('\u001f').append(e.getValue());
        }
        return sb.toString();
    }

    private static native String nativeVersion();

    private static native int nativeCommand(String text);

    private static native int nativeCountWords(String text);

    private static native String nativeProcess(
            String text,
            String styleKey,
            String pkg,
            boolean removeFillers,
            boolean spokenPunctuation,
            boolean autoCapitalize,
            boolean trailingSpace,
            boolean adaptTone,
            String snippets,
            String dictionary);
}
