package app.sotto.voice.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class Settings {

    private static final String FILE = "sotto";

    public static final String K_STYLE = "style";
    public static final String K_OFFLINE = "offline";
    public static final String K_AUTO_INSERT = "auto_insert";
    public static final String K_CONTINUOUS = "continuous";
    public static final String K_ADAPT_TONE = "adapt_tone";
    public static final String K_TRAILING_SPACE = "trailing_space";
    public static final String K_CAPITALIZE = "capitalize";
    public static final String K_FILLERS = "fillers";
    public static final String K_SPOKEN_PUNCT = "spoken_punct";
    public static final String K_HAPTICS = "haptics";
    public static final String K_BUBBLE = "bubble";
    public static final String K_BUBBLE_SIZE = "bubble_size";
    public static final String K_BUBBLE_OPACITY = "bubble_opacity";
    public static final String K_BUBBLE_X = "bubble_x";
    public static final String K_BUBBLE_Y = "bubble_y";
    public static final String K_LANGUAGE = "language";
    public static final String K_ONBOARDED = "onboarded";
    public static final String K_BOOT = "start_on_boot";

    public static final String K_WORDS = "stat_words";
    public static final String K_SESSIONS = "stat_sessions";
    public static final String K_STREAK = "stat_streak";
    public static final String K_LAST_DAY = "stat_last_day";

    private final SharedPreferences prefs;

    public Settings(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String getStyle() {
        return prefs.getString(K_STYLE, "default");
    }

    public void setStyle(String style) {
        prefs.edit().putString(K_STYLE, style).apply();
    }

    public boolean isOffline() {
        return prefs.getBoolean(K_OFFLINE, false);
    }

    public void setOffline(boolean v) {
        prefs.edit().putBoolean(K_OFFLINE, v).apply();
    }

    public boolean isAutoInsert() {
        return prefs.getBoolean(K_AUTO_INSERT, true);
    }

    public void setAutoInsert(boolean v) {
        prefs.edit().putBoolean(K_AUTO_INSERT, v).apply();
    }

    public boolean isContinuous() {
        return prefs.getBoolean(K_CONTINUOUS, true);
    }

    public void setContinuous(boolean v) {
        prefs.edit().putBoolean(K_CONTINUOUS, v).apply();
    }

    public boolean isAdaptTone() {
        return prefs.getBoolean(K_ADAPT_TONE, true);
    }

    public void setAdaptTone(boolean v) {
        prefs.edit().putBoolean(K_ADAPT_TONE, v).apply();
    }

    public boolean isTrailingSpace() {
        return prefs.getBoolean(K_TRAILING_SPACE, true);
    }

    public void setTrailingSpace(boolean v) {
        prefs.edit().putBoolean(K_TRAILING_SPACE, v).apply();
    }

    public boolean isCapitalize() {
        return prefs.getBoolean(K_CAPITALIZE, true);
    }

    public void setCapitalize(boolean v) {
        prefs.edit().putBoolean(K_CAPITALIZE, v).apply();
    }

    public boolean isFillers() {
        return prefs.getBoolean(K_FILLERS, true);
    }

    public void setFillers(boolean v) {
        prefs.edit().putBoolean(K_FILLERS, v).apply();
    }

    public boolean isSpokenPunctuation() {
        return prefs.getBoolean(K_SPOKEN_PUNCT, true);
    }

    public void setSpokenPunctuation(boolean v) {
        prefs.edit().putBoolean(K_SPOKEN_PUNCT, v).apply();
    }

    public boolean isHaptics() {
        return prefs.getBoolean(K_HAPTICS, true);
    }

    public void setHaptics(boolean v) {
        prefs.edit().putBoolean(K_HAPTICS, v).apply();
    }

    public boolean isBubble() {
        return prefs.getBoolean(K_BUBBLE, true);
    }

    public void setBubble(boolean v) {
        prefs.edit().putBoolean(K_BUBBLE, v).apply();
    }

    public int getBubbleSize() {
        return prefs.getInt(K_BUBBLE_SIZE, 1);
    }

    public void setBubbleSize(int v) {
        prefs.edit().putInt(K_BUBBLE_SIZE, v).apply();
    }

    public int getBubbleOpacity() {
        return prefs.getInt(K_BUBBLE_OPACITY, 96);
    }

    public void setBubbleOpacity(int v) {
        prefs.edit().putInt(K_BUBBLE_OPACITY, v).apply();
    }

    public int getBubbleX() {
        return prefs.getInt(K_BUBBLE_X, -1);
    }

    public int getBubbleY() {
        return prefs.getInt(K_BUBBLE_Y, -1);
    }

    public void setBubblePosition(int x, int y) {
        prefs.edit().putInt(K_BUBBLE_X, x).putInt(K_BUBBLE_Y, y).apply();
    }

    public String getLanguage() {
        return prefs.getString(K_LANGUAGE, "en-US");
    }

    public void setLanguage(String v) {
        prefs.edit().putString(K_LANGUAGE, v).apply();
    }

    public boolean isOnboarded() {
        return prefs.getBoolean(K_ONBOARDED, false);
    }

    public void setOnboarded(boolean v) {
        prefs.edit().putBoolean(K_ONBOARDED, v).apply();
    }

    public boolean isStartOnBoot() {
        return prefs.getBoolean(K_BOOT, false);
    }

    public void setStartOnBoot(boolean v) {
        prefs.edit().putBoolean(K_BOOT, v).apply();
    }

    public int getWords() {
        return prefs.getInt(K_WORDS, 0);
    }

    public int getSessions() {
        return prefs.getInt(K_SESSIONS, 0);
    }

    public int getStreak() {
        return prefs.getInt(K_STREAK, 0);
    }

    public void recordSession(int words) {
        String today = dayKey(0);
        String last = prefs.getString(K_LAST_DAY, "");
        int streak = prefs.getInt(K_STREAK, 0);
        if (!today.equals(last)) {
            if (last.equals(dayKey(-1))) {
                streak = streak + 1;
            } else {
                streak = 1;
            }
        } else if (streak == 0) {
            streak = 1;
        }
        prefs.edit()
                .putInt(K_WORDS, prefs.getInt(K_WORDS, 0) + Math.max(0, words))
                .putInt(K_SESSIONS, prefs.getInt(K_SESSIONS, 0) + 1)
                .putInt(K_STREAK, streak)
                .putString(K_LAST_DAY, today)
                .apply();
    }

    public void resetStats() {
        prefs.edit()
                .putInt(K_WORDS, 0)
                .putInt(K_SESSIONS, 0)
                .putInt(K_STREAK, 0)
                .putString(K_LAST_DAY, "")
                .apply();
    }

    /** Seconds saved versus typing the same words at 40 wpm, minus speaking time. */
    public static int secondsSaved(int words) {
        return (int) Math.round(words * 1.1);
    }

    public static String formatSaved(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        int minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        int hours = minutes / 60;
        int rem = minutes % 60;
        return hours + "h " + rem + "m";
    }

    private static String dayKey(int offsetDays) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, offsetDays);
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date(c.getTimeInMillis()));
    }
}
