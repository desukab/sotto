package app.sotto.voice.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Optional cloud pass using any OpenAI-compatible chat endpoint (OpenAI,
 * Azure OpenAI, Groq, Together, a local gateway…). Off by default. Nothing is
 * sent unless the user turns it on and supplies a key.
 */
public final class CloudPolish {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static final class Failure extends Exception {
        public Failure(String message) {
            super(message);
        }
    }

    private CloudPolish() {
    }

    public static String polish(Settings settings, String text, String styleKey) throws Failure {
        if (text == null || text.trim().isEmpty()) throw new Failure("Nothing to polish");
        String key = settings.getCloudKey();
        if (key == null || key.trim().isEmpty()) throw new Failure("No API key set");

        String base = settings.getCloudBase();
        if (base == null || base.trim().isEmpty()) base = "https://api.openai.com/v1";
        base = base.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String endpoint = base.endsWith("/chat/completions")
                ? base
                : (base.endsWith("/v1") ? base + "/chat/completions" : base + "/chat/completions");

        try {
            JSONObject body = new JSONObject();
            body.put("model", settings.getCloudModel());
            body.put("temperature", 0.2);
            body.put("max_tokens", 1500);

            JSONArray messages = new JSONArray();
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", systemPrompt(styleKey));
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", text);
            messages.put(system);
            messages.put(user);
            body.put("messages", messages);

            HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(25000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Authorization", "Bearer " + key.trim());

            OutputStream out = connection.getOutputStream();
            out.write(body.toString().getBytes(UTF8));
            out.flush();
            out.close();

            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String raw = read(stream);
            if (code >= 400) {
                throw new Failure("HTTP " + code + " " + shorten(raw));
            }
            JSONObject response = new JSONObject(raw);
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) throw new Failure("Empty response");
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            String content = message == null ? null : message.optString("content", "");
            if (content == null || content.trim().isEmpty()) throw new Failure("Empty content");
            return content.trim();
        } catch (Failure f) {
            throw f;
        } catch (Throwable t) {
            String name = t.getClass().getSimpleName();
            throw new Failure(name + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
    }

    private static String systemPrompt(String styleKey) {
        String style = styleKey == null || styleKey.isEmpty() ? "default" : styleKey.replace('_', ' ');
        return "You clean up dictated text. Rewrite the user's transcript into well-formed "
                + "written text while preserving meaning, facts and the speaker's voice. "
                + "Style: " + style + ". Fix punctuation and casing, remove filler words and "
                + "false starts, keep the original language. Do not add commentary, do not "
                + "answer questions in the text, do not wrap the result in quotes. "
                + "Return only the rewritten text.";
    }

    private static String read(InputStream stream) {
        if (stream == null) return "";
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = stream.read(chunk)) > 0) {
                buffer.write(chunk, 0, n);
            }
            return buffer.toString(UTF8.name());
        } catch (Throwable t) {
            return "";
        } finally {
            try {
                stream.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String shorten(String value) {
        if (value == null) return "";
        String trimmed = value.replace('\n', ' ').trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed;
    }
}
