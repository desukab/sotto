package app.sotto.voice.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sotto's local writing brain.
 *
 * Pure Java on purpose: no Android dependencies, so the whole pipeline can be
 * unit-tested on a desktop JVM. Everything here runs on the device and never
 * needs a network call.
 */
public final class Engine {

    private Engine() {
    }

    // ------------------------------------------------------------------ style

    public enum Style {
        DEFAULT("Default", "default", "Standard formatting"),
        FORMAL("Formal", "formal", "Full punctuation, no contractions"),
        CASUAL("Casual", "casual", "Caps, lighter punctuation"),
        VERY_CASUAL("very casual", "very_casual", "No caps, no punctuation"),
        EXCITED("Excited!", "excited", "Caps and exclamation marks"),
        NOTES("Notes", "notes", "Bulleted, scannable lines");

        public final String label;
        public final String key;
        public final String blurb;

        Style(String label, String key, String blurb) {
            this.label = label;
            this.key = key;
            this.blurb = blurb;
        }

        public static Style fromKey(String key) {
            if (key == null) {
                return DEFAULT;
            }
            for (Style s : values()) {
                if (s.key.equals(key)) {
                    return s;
                }
            }
            return DEFAULT;
        }

        /**
         * Picks a sensible tone from the app the user is typing into. Only used
         * when the user leaves the style on Default and enables tone adaption.
         */
        public static Style inferForPackage(String pkg, Style fallback) {
            if (pkg == null) {
                return fallback;
            }
            String p = pkg.toLowerCase(Locale.US);
            if (containsAny(p, MESSENGERS)) {
                return CASUAL;
            }
            if (containsAny(p, MAIL)) {
                return FORMAL;
            }
            return fallback;
        }

        private static boolean containsAny(String haystack, String[] needles) {
            for (String n : needles) {
                if (haystack.contains(n)) {
                    return true;
                }
            }
            return false;
        }

        private static final String[] MESSENGERS = {
                "whatsapp", "telegram", "signal", "messaging", "messages",
                "instagram", "snapchat", "discord", "slack", "messenger",
                "wechat", "viber", "line.android"
        };

        private static final String[] MAIL = {
                "gmail", "mail", "outlook", "proton", "spark", "yahoo",
                "fastmail", "thunderbird"
        };
    }

    // ---------------------------------------------------------------- command

    public enum Command {
        NONE,
        UNDO,
        NEW_PARAGRAPH,
        NEW_LINE,
        SEND,
        SELECT_ALL,
        COPY,
        CUT,
        PASTE,
        STOP,
        CLEAR,
        RESTYLE_FORMAL,
        RESTYLE_CASUAL,
        RESTYLE_EXCITED,
        RESTYLE_NOTES
    }

    /**
     * Detects short, exact voice commands. Keeping this strict means normal
     * sentences such as "send it to him tomorrow" are dictated, not executed.
     */
    public static Command detectCommand(String raw) {
        if (raw == null) {
            return Command.NONE;
        }
        String s = raw.toLowerCase(Locale.US).replaceAll("[.!?,;:]+$", "").trim();
        s = s.replaceAll("\\s+", " ");
        if (s.isEmpty()) {
            return Command.NONE;
        }
        int words = s.split(" ").length;
        if (words > 4) {
            return Command.NONE;
        }
        if (s.equals("scratch that") || s.equals("undo that") || s.equals("undo")
                || s.equals("delete that") || s.equals("delete the last sentence")) {
            return Command.UNDO;
        }
        if (s.equals("new paragraph") || s.equals("next paragraph")) {
            return Command.NEW_PARAGRAPH;
        }
        if (s.equals("new line") || s.equals("next line") || s.equals("line break")) {
            return Command.NEW_LINE;
        }
        if (s.equals("send it") || s.equals("send message") || s.equals("send")) {
            return Command.SEND;
        }
        if (s.equals("select all")) {
            return Command.SELECT_ALL;
        }
        if (s.equals("copy that") || s.equals("copy")) {
            return Command.COPY;
        }
        if (s.equals("cut that") || s.equals("cut")) {
            return Command.CUT;
        }
        if (s.equals("paste that") || s.equals("paste")) {
            return Command.PASTE;
        }
        if (s.equals("stop listening") || s.equals("stop dictation") || s.equals("stop")) {
            return Command.STOP;
        }
        if (s.equals("clear the field") || s.equals("clear everything") || s.equals("clear all")) {
            return Command.CLEAR;
        }
        if (s.equals("make it formal") || s.equals("formal version")) {
            return Command.RESTYLE_FORMAL;
        }
        if (s.equals("make it casual") || s.equals("casual version")) {
            return Command.RESTYLE_CASUAL;
        }
        if (s.equals("make it excited") || s.equals("excited version")) {
            return Command.RESTYLE_EXCITED;
        }
        if (s.equals("make it notes") || s.equals("turn it into notes") || s.equals("make notes")) {
            return Command.RESTYLE_NOTES;
        }
        return Command.NONE;
    }

    // ----------------------------------------------------------------- config

    public static final class Config {
        public boolean removeFillers = true;
        public boolean spokenPunctuation = true;
        public boolean autoCapitalize = true;
        public boolean trailingSpace = false;
        public boolean adaptTone = true;
        public Map<String, String> dictionary = new LinkedHashMap<String, String>();
        public Map<String, String> snippets = new LinkedHashMap<String, String>();
    }

    public static final class Result {
        public final String text;
        public final int words;
        public final Style style;
        public final Command command;

        Result(String text, int words, Style style, Command command) {
            this.text = text;
            this.words = words;
            this.style = style;
            this.command = command;
        }

        public boolean isEmpty() {
            return text == null || text.trim().isEmpty();
        }
    }

    // ---------------------------------------------------------------- process

    private static final Pattern FILLER = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])(?:um+|uh+|erm+|hmm+|mmm+|ah+)(?![\\p{L}\\p{N}])[,.]?");

    private static final Pattern YOU_KNOW = Pattern.compile(
            "(?i),\\s*you know\\s*,");

    private static final Pattern I_MEAN = Pattern.compile(
            "(?i)(?:^|(?<=[.!?]\\s))\\s*i mean,?\\s*");

    private static final Pattern PROTECT = Pattern.compile(
            "(?i)(https?://\\S+|www\\.\\S+|[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}|\\d+\\.\\d+|\\d+:\\d+)");

    private static final String[] PUNCT_WORDS = {
            "new paragraph", "next paragraph",
            "new line", "next line", "line break",
            "question mark",
            "exclamation mark", "exclamation point",
            "full stop", "period",
            "comma", "colon", "semicolon",
            "open parenthesis", "close parenthesis",
            "dot dot dot", "ellipsis"
    };

    private static final String[] PUNCT_SYMBOLS = {
            "\n\n", "\n\n",
            "\n", "\n", "\n",
            "?",
            "!", "!",
            ".", ".",
            ",", ":", ";",
            " (", ")",
            "...", "..."
    };

    private static final Map<String, String> CONTRACTIONS = new LinkedHashMap<String, String>();

    static {
        CONTRACTIONS.put("don't", "do not");
        CONTRACTIONS.put("can't", "cannot");
        CONTRACTIONS.put("won't", "will not");
        CONTRACTIONS.put("i'm", "I am");
        CONTRACTIONS.put("i've", "I have");
        CONTRACTIONS.put("i'll", "I will");
        CONTRACTIONS.put("i'd", "I would");
        CONTRACTIONS.put("it's", "it is");
        CONTRACTIONS.put("that's", "that is");
        CONTRACTIONS.put("there's", "there is");
        CONTRACTIONS.put("we're", "we are");
        CONTRACTIONS.put("we've", "we have");
        CONTRACTIONS.put("we'll", "we will");
        CONTRACTIONS.put("they're", "they are");
        CONTRACTIONS.put("they've", "they have");
        CONTRACTIONS.put("they'll", "they will");
        CONTRACTIONS.put("you're", "you are");
        CONTRACTIONS.put("you've", "you have");
        CONTRACTIONS.put("you'll", "you will");
        CONTRACTIONS.put("let's", "let us");
        CONTRACTIONS.put("isn't", "is not");
        CONTRACTIONS.put("aren't", "are not");
        CONTRACTIONS.put("wasn't", "was not");
        CONTRACTIONS.put("weren't", "were not");
        CONTRACTIONS.put("doesn't", "does not");
        CONTRACTIONS.put("didn't", "did not");
        CONTRACTIONS.put("couldn't", "could not");
        CONTRACTIONS.put("wouldn't", "would not");
        CONTRACTIONS.put("shouldn't", "should not");
        CONTRACTIONS.put("haven't", "have not");
        CONTRACTIONS.put("hasn't", "has not");
        CONTRACTIONS.put("hadn't", "had not");
        CONTRACTIONS.put("mustn't", "must not");
    }

    public static Result process(String raw, Style requestedStyle, String pkg, Config cfg) {
        if (raw == null) {
            raw = "";
        }
        if (cfg == null) {
            cfg = new Config();
        }
        Style style = requestedStyle == null ? Style.DEFAULT : requestedStyle;
        if (cfg.adaptTone && style == Style.DEFAULT) {
            style = Style.inferForPackage(pkg, style);
        }

        String text = raw.replace("\r\n", "\n").replace('\r', '\n');

        List<String> protectedValues = new ArrayList<String>();
        if (cfg.spokenPunctuation || true) {
            text = protect(text, protectedValues);
        }

        text = collapseSpaces(text);

        if (cfg.spokenPunctuation) {
            text = expandPunctuation(text);
        }

        if (cfg.snippets != null && !cfg.snippets.isEmpty()) {
            text = replaceAll(text, cfg.snippets);
        }
        if (cfg.dictionary != null && !cfg.dictionary.isEmpty()) {
            text = replaceAll(text, cfg.dictionary);
        }

        if (cfg.removeFillers) {
            text = removeFillers(text);
        }

        text = normalizePunctuation(text);
        text = collapseSpaces(text);

        switch (style) {
            case FORMAL:
                text = replaceAll(text, CONTRACTIONS);
                break;
            case CASUAL:
                text = text.replaceAll(",(?!\\d)", "");
                text = collapseSpaces(text);
                break;
            case VERY_CASUAL:
                text = text.replaceAll(",(?!\\d)", "");
                break;
            case EXCITED:
                break;
            case NOTES:
                break;
            case DEFAULT:
            default:
                break;
        }

        text = trimLines(text);

        if (style == Style.VERY_CASUAL) {
            text = text.toLowerCase(Locale.US);
        } else if (cfg.autoCapitalize) {
            text = capitalizeSentences(text);
        }

        text = finishSentence(text, style);

        if (style == Style.NOTES) {
            text = bulletize(text);
        }

        text = restore(text, protectedValues);
        text = trimLines(text);

        if (cfg.trailingSpace && text.length() > 0 && !text.endsWith("\n")) {
            text = text + " ";
        }

        int words = countWords(text);
        return new Result(text, words, style, Command.NONE);
    }

    public static int countWords(String text) {
        if (text == null) {
            return 0;
        }
        int count = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (!inWord) {
                    count++;
                    inWord = true;
                }
            } else {
                inWord = false;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- helpers

    private static String protect(String text, List<String> out) {
        Matcher m = PROTECT.matcher(text);
        StringBuffer sb = new StringBuffer();
        int i = 0;
        while (m.find()) {
            String value = m.group(0);
            out.add(value);
            m.appendReplacement(sb, Matcher.quoteReplacement("\uE000" + i + "\uE001"));
            i++;
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String restore(String text, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            text = text.replace("\uE000" + i + "\uE001", values.get(i));
        }
        return text;
    }

    private static String collapseSpaces(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(lines[i].replaceAll("[\\t\\x0B\\f ]+", " ").trim());
        }
        return out.toString();
    }

    private static String trimLines(String text) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(lines[i].trim());
        }
        return out.toString().trim();
    }

    private static String expandPunctuation(String text) {
        for (int i = 0; i < PUNCT_WORDS.length; i++) {
            String word = PUNCT_WORDS[i];
            String symbol = PUNCT_SYMBOLS[i];
            Pattern p = Pattern.compile("(?i)(?<![\\p{L}\\p{N}])" + Pattern.quote(word)
                    + "(?![\\p{L}\\p{N}])");
            Matcher m = p.matcher(text);
            StringBuffer sb = new StringBuffer();
            boolean newline = symbol.startsWith("\n");
            while (m.find()) {
                String rep = newline ? symbol : symbol + " ";
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
            m.appendTail(sb);
            text = sb.toString();
        }
        return text;
    }

    private static String removeFillers(String text) {
        text = FILLER.matcher(text).replaceAll("");
        text = YOU_KNOW.matcher(text).replaceAll(", ");
        text = I_MEAN.matcher(text).replaceAll("");
        return text;
    }

    /**
     * Removes spaces before punctuation and makes sure a space follows
     * punctuation when needed. Decimal points, times, emails and URLs are
     * protected by placeholders before this runs.
     */
    private static String normalizePunctuation(String text) {
        StringBuilder out = new StringBuilder();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (isPunctuation(c)) {
                while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
                    out.setLength(out.length() - 1);
                }
                out.append(c);
                if (i + 1 < n) {
                    char next = text.charAt(i + 1);
                    if (!Character.isWhitespace(next) && !isPunctuation(next)) {
                        out.append(' ');
                    }
                }
            } else if (c == ' ' || c == '\t') {
                if (out.length() > 0) {
                    char last = out.charAt(out.length() - 1);
                    if (last != ' ' && last != '\n') {
                        out.append(' ');
                    }
                }
            } else if (c == '\n') {
                while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
                    out.setLength(out.length() - 1);
                }
                out.append('\n');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean isPunctuation(char c) {
        return c == ',' || c == '.' || c == '!' || c == '?' || c == ';' || c == ':';
    }

    private static String capitalizeSentences(String text) {
        StringBuilder out = new StringBuilder(text);
        boolean capitalizeNext = true;
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (capitalizeNext && Character.isLetter(c)) {
                out.setCharAt(i, Character.toUpperCase(c));
                capitalizeNext = false;
            } else if (c == '.' || c == '!' || c == '?' || c == '\n') {
                capitalizeNext = true;
            } else if (!Character.isWhitespace(c)) {
                capitalizeNext = false;
            }
        }
        String s = out.toString();
        s = s.replaceAll("(?<![\\p{L}\\p{N}])i(?![\\p{L}\\p{N}])", "I");
        return s;
    }

    private static String finishSentence(String text, Style style) {
        if (text.isEmpty()) {
            return text;
        }
        char last = text.charAt(text.length() - 1);
        switch (style) {
            case VERY_CASUAL:
                text = text.replaceAll("[.!?;:]+$", "").trim();
                break;
            case EXCITED:
                text = text.replaceAll("[.!?]+$", "");
                text = text + "!";
                break;
            case FORMAL:
            case DEFAULT:
            case CASUAL:
                if (last != '.' && last != '!' && last != '?' && last != ')' && last != ']'
                        && last != '"' && last != '\'') {
                    text = text + ".";
                }
                break;
            case NOTES:
                break;
            default:
                break;
        }
        return text;
    }

    private static String bulletize(String text) {
        String[] parts = text.split("(?<=[.!?])\\s+|\\n+");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) {
                continue;
            }
            p = p.replaceAll("[.!?]+$", "").trim();
            if (p.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append("\u2022 ").append(p);
        }
        return out.toString();
    }

    private static String replaceAll(String text, Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return text;
        }
        List<String> keys = new ArrayList<String>(map.keySet());
        Collections.sort(keys, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return b.length() - a.length();
            }
        });
        StringBuilder alt = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            if (k == null || k.trim().isEmpty()) {
                continue;
            }
            if (alt.length() > 0) {
                alt.append('|');
            }
            alt.append(Pattern.quote(k));
        }
        if (alt.length() == 0) {
            return text;
        }
        Pattern p = Pattern.compile("(?i)(?<![\\p{L}\\p{N}])(" + alt + ")(?![\\p{L}\\p{N}])");
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String found = m.group(1);
            String rep = map.get(found);
            if (rep == null) {
                for (Map.Entry<String, String> e : map.entrySet()) {
                    if (e.getKey().equalsIgnoreCase(found)) {
                        rep = e.getValue();
                        break;
                    }
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep == null ? found : rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
