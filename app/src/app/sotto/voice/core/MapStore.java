package app.sotto.voice.core;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Key/value store used for both snippets (spoken shorthand -> expansion) and
 * the custom dictionary (misheard word -> correct spelling).
 */
public final class MapStore {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final File file;

    public MapStore(Context context, String fileName) {
        this.file = new File(context.getApplicationContext().getFilesDir(), fileName);
    }

    public static MapStore snippets(Context c) {
        return new MapStore(c, "snippets.json");
    }

    public static MapStore dictionary(Context c) {
        return new MapStore(c, "dictionary.json");
    }

    public synchronized Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (!file.exists()) {
            return map;
        }
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            JSONObject o = new JSONObject(sb.toString());
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                map.put(k, o.optString(k, ""));
            }
        } catch (Exception ignored) {
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (Exception ignored) {
                }
            }
        }
        return map;
    }

    public synchronized void put(String key, String value) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        Map<String, String> map = all();
        map.put(key.trim(), value == null ? "" : value);
        save(map);
    }

    public synchronized void remove(String key) {
        Map<String, String> map = all();
        map.remove(key);
        save(map);
    }

    public synchronized void clear() {
        save(new LinkedHashMap<String, String>());
    }

    private void save(Map<String, String> map) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, String> e : map.entrySet()) {
                o.put(e.getKey(), e.getValue());
            }
        } catch (Exception ignored) {
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            OutputStreamWriter w = new OutputStreamWriter(out, UTF8);
            w.write(o.toString());
            w.flush();
        } catch (Exception ignored) {
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
