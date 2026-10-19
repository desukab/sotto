package app.sotto.voice.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class HistoryStore {

    private static final String FILE = "history.json";
    private static final int LIMIT = 200;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static final class Entry {
        public long time;
        public String text;
        public String app;
        public String style;
        public int words;

        public Entry(long time, String text, String app, String style, int words) {
            this.time = time;
            this.text = text;
            this.app = app;
            this.style = style;
            this.words = words;
        }

        public String timeLabel() {
            return new SimpleDateFormat("d MMM, HH:mm", Locale.US).format(new Date(time));
        }
    }

    private final File file;

    public HistoryStore(Context context) {
        this.file = new File(context.getApplicationContext().getFilesDir(), FILE);
    }

    public synchronized List<Entry> all() {
        List<Entry> list = new ArrayList<Entry>();
        if (!file.exists()) {
            return list;
        }
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                list.add(new Entry(
                        o.optLong("t", 0L),
                        o.optString("text", ""),
                        o.optString("app", ""),
                        o.optString("style", "default"),
                        o.optInt("words", 0)));
            }
        } catch (Exception ignored) {
            // A corrupt history must never break dictation.
        } finally {
            close(r);
        }
        return list;
    }

    public synchronized void add(String text, String app, String style, int words) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        List<Entry> list = all();
        list.add(0, new Entry(System.currentTimeMillis(), text, app, style, words));
        while (list.size() > LIMIT) {
            list.remove(list.size() - 1);
        }
        save(list);
    }

    public synchronized void updateLatest(String text, int words) {
        List<Entry> list = all();
        if (list.isEmpty()) {
            return;
        }
        Entry e = list.get(0);
        e.text = text;
        e.words = words;
        e.time = System.currentTimeMillis();
        save(list);
    }

    public synchronized void removeLatest() {
        List<Entry> list = all();
        if (list.isEmpty()) {
            return;
        }
        list.remove(0);
        save(list);
    }

    public synchronized void removeAt(int index) {
        List<Entry> list = all();
        if (index < 0 || index >= list.size()) {
            return;
        }
        list.remove(index);
        save(list);
    }

    public synchronized void clear() {
        save(new ArrayList<Entry>());
    }

    public synchronized String exportText() {
        List<Entry> list = all();
        StringBuilder sb = new StringBuilder();
        for (int i = list.size() - 1; i >= 0; i--) {
            Entry e = list.get(i);
            sb.append(e.timeLabel()).append("  \u00b7  ").append(e.words).append(" words\n");
            sb.append(e.text).append("\n\n");
        }
        return sb.toString().trim();
    }

    private void save(List<Entry> list) {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            JSONObject o = new JSONObject();
            try {
                o.put("t", e.time);
                o.put("text", e.text == null ? "" : e.text);
                o.put("app", e.app == null ? "" : e.app);
                o.put("style", e.style == null ? "default" : e.style);
                o.put("words", e.words);
            } catch (Exception ignored) {
            }
            arr.put(o);
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            OutputStreamWriter w = new OutputStreamWriter(out, UTF8);
            w.write(arr.toString());
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

    private static void close(BufferedReader r) {
        if (r != null) {
            try {
                r.close();
            } catch (Exception ignored) {
            }
        }
    }
}
