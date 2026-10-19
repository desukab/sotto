package app.sotto.voice.core;

import android.content.Context;

import java.util.Map;

/**
 * Remembers the tone the user last picked for a given app, so "make it formal"
 * in Gmail sticks and "very casual" in WhatsApp sticks.
 */
public final class AppStyles {

    private final MapStore store;

    public AppStyles(Context context) {
        this.store = new MapStore(context, "app_styles.json");
    }

    public String styleFor(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        String s = store.all().get(pkg);
        return s == null || s.isEmpty() ? null : s;
    }

    public void remember(String pkg, String style) {
        if (pkg == null || pkg.isEmpty() || style == null) return;
        if ("default".equals(style) || style.isEmpty()) {
            store.remove(pkg);
            return;
        }
        store.put(pkg, style);
    }

    public void forget(String pkg) {
        if (pkg != null) store.remove(pkg);
    }

    public Map<String, String> all() {
        return store.all();
    }

    public int count() {
        return store.all().size();
    }

    public static String label(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "Unknown";
        int i = pkg.lastIndexOf('.');
        String tail = i >= 0 && i + 1 < pkg.length() ? pkg.substring(i + 1) : pkg;
        if (tail.isEmpty()) return pkg;
        return Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
    }

    public static String prettyStyle(String key) {
        if (key == null || key.isEmpty()) return "Default";
        if ("very_casual".equals(key)) return "Very casual";
        return Character.toUpperCase(key.charAt(0)) + key.substring(1).replace('_', ' ');
    }
}
