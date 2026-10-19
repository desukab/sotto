package app.sotto.voice.core;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Expands {date}, {time}, {day}, {datetime} and {clipboard} inside snippets and
 * dictation. Runs after the polish engine so the tokens are never reformatted.
 */
public final class Variables {

    public static final String[] TOKENS = {
            "{date}", "{time}", "{day}", "{datetime}", "{clipboard}"
    };

    private Variables() {
    }

    public static String expand(Context context, String text) {
        if (text == null || text.indexOf('{') < 0) return text;
        long now = System.currentTimeMillis();
        String out = text;
        out = out.replace("{date}", format(now, "d MMMM yyyy"));
        out = out.replace("{date:short}", format(now, "yyyy-MM-dd"));
        out = out.replace("{time}", format(now, "HH:mm"));
        out = out.replace("{day}", format(now, "EEEE"));
        out = out.replace("{datetime}", format(now, "d MMMM yyyy 'at' HH:mm"));
        if (out.contains("{clipboard}")) {
            String clip = readClipboard(context);
            if (clip != null) {
                out = out.replace("{clipboard}", clip);
            }
        }
        return out;
    }

    private static String format(long time, String pattern) {
        try {
            return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(time));
        } catch (Throwable t) {
            return "";
        }
    }

    private static String readClipboard(Context context) {
        try {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return null;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence text = clip.getItemAt(0).coerceToText(context);
            return text == null ? null : text.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
