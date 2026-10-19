package app.sotto.voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.sotto.voice.core.Engine;
import app.sotto.voice.core.HistoryStore;
import app.sotto.voice.core.MapStore;
import app.sotto.voice.core.NativeCore;
import app.sotto.voice.core.Settings;
import app.sotto.voice.ui.BubbleView;
import app.sotto.voice.ui.Ui;

/**
 * Foreground service that owns the floating bubble and the recognizer.
 */
public final class SottoService extends Service {

    public static final String ACTION_START = "app.sotto.voice.START";
    public static final String ACTION_STOP = "app.sotto.voice.STOP";
    public static final String ACTION_TOGGLE = "app.sotto.voice.TOGGLE";
    public static final String ACTION_UNDO = "app.sotto.voice.UNDO";

    private static final int NOTIFICATION_ID = 41;
    private static final String CHANNEL = "sotto";

    public interface Listener {
        void onState(boolean listening, boolean running);

        void onPartial(String text);

        void onFinal(String text);

        void onLevel(float level);
    }

    private static volatile SottoService instance;
    private static volatile Listener listener;
    private static volatile boolean running;
    private static volatile boolean listening;

    public static void setListener(Listener l) {
        listener = l;
    }

    public static boolean isRunning() {
        return running;
    }

    public static boolean isListening() {
        return listening;
    }

    public static SottoService get() {
        return instance;
    }

    private Settings settings;
    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private FrameLayout overlayRoot;
    private BubbleView bubble;
    private TextView partialView;
    private SpeechRecognizer recognizer;
    private boolean destroyed;
    private long touchDownAt;
    private float touchDownX;
    private float touchDownY;
    private int windowDownX;
    private int windowDownY;
    private boolean dragging;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public void toggleListening() {
        if (listening) stopListening(); else startListening();
    }

    public void startListening() {
        if (listening) return;
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            toast("Microphone permission is needed");
            openApp();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("No speech recognizer is installed");
            return;
        }
        ensureRecognizer();
        if (recognizer == null) {
            toast("Could not start the recognizer");
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        String lang = settings.getLanguage();
        if (lang != null && lang.length() > 0) {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        }
        if (settings.isOffline()) {
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        }
        try {
            recognizer.startListening(intent);
            listening = true;
            applyListeningUi();
            notifyState();
        } catch (Throwable t) {
            toast("Recognizer unavailable");
        }
    }

    public void stopListening() {
        listening = false;
        if (recognizer != null) {
            try {
                recognizer.stopListening();
            } catch (Throwable ignored) {
            }
        }
        applyListeningUi();
        notifyState();
    }

    private void ensureRecognizer() {
        if (recognizer != null) return;
        try {
            if (settings.isOffline() && Build.VERSION.SDK_INT >= 31
                    && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    float level = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f));
                    if (bubble != null) bubble.setLevel(level);
                    if (listener != null) listener.onLevel(level);
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                }

                @Override
                public void onError(int error) {
                    listening = false;
                    applyListeningUi();
                    if (settings.isContinuous() && !destroyed
                            && (error == SpeechRecognizer.ERROR_NO_MATCH
                            || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!listening && !destroyed) startListening();
                            }
                        }, 350);
                    } else {
                        notifyState();
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    listening = false;
                    applyListeningUi();
                    String text = best(results);
                    if (text.length() > 0) handleUtterance(text);
                    notifyState();
                    if (settings.isContinuous() && !destroyed) {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (!destroyed) startListening();
                            }
                        }, 400);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    String text = best(partialResults);
                    if (text.length() == 0) return;
                    if (partialView != null) {
                        partialView.setText(text);
                        partialView.setVisibility(View.VISIBLE);
                    }
                    if (listener != null) listener.onPartial(text);
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });
        } catch (Throwable t) {
            recognizer = null;
        }
    }

    private String best(Bundle bundle) {
        if (bundle == null) return "";
        ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) return "";
        String s = list.get(0);
        return s == null ? "" : s.trim();
    }

    private void handleUtterance(String raw) {
        int ordinal = NativeCore.command(raw);
        Engine.Command command = Engine.Command.values()[
                Math.max(0, Math.min(Engine.Command.values().length - 1, ordinal))];
        SottoAccessibilityService a11y = SottoAccessibilityService.get();

        if (command != Engine.Command.NONE) {
            if (partialView != null) partialView.setVisibility(View.GONE);
            runCommand(command, raw);
            return;
        }

        Map<String, String> snippets = MapStore.snippets(this).all();
        Map<String, String> dictionary = MapStore.dictionary(this).all();
        String pkg = a11y != null ? a11y.focusedPackage() : "";
        String polished = NativeCore.process(raw, settings, pkg, snippets, dictionary);
        if (polished == null) polished = raw;
        polished = polished.trim();

        boolean inserted = false;
        if (settings.isAutoInsert() && a11y != null) {
            inserted = a11y.insert(polished);
        }
        if (!inserted && a11y != null) {
            a11y.toClipboard(polished);
            toast("Copied to clipboard");
        } else if (!inserted) {
            toast("Text copied — enable Sotto accessibility to insert");
            copyFallback(polished);
        }

        HistoryStore history = new HistoryStore(this);
        history.add(polished, pkg, settings.getStyle(), NativeCore.countWords(polished));
        settings.recordSession(NativeCore.countWords(polished));
        if (settings.isHaptics()) {
            try {
                android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) v.vibrate(12);
            } catch (Throwable ignored) {
            }
        }
        if (partialView != null) {
            partialView.setText(polished);
            partialView.setVisibility(View.VISIBLE);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (partialView != null) partialView.setVisibility(View.GONE);
                }
            }, 2600);
        }
        if (listener != null) listener.onFinal(polished);
    }

    private void runCommand(Engine.Command command, String raw) {
        SottoAccessibilityService a11y = SottoAccessibilityService.get();
        switch (command) {
            case UNDO:
                if (a11y != null && a11y.undoLast()) {
                    toast("Undone");
                } else if (a11y != null) {
                    a11y.toClipboard("");
                    toast("Nothing to undo");
                }
                break;
            case NEW_PARAGRAPH:
                insertRaw(a11y, "\n\n");
                break;
            case NEW_LINE:
                insertRaw(a11y, "\n");
                break;
            case SEND:
                if (a11y != null) a11y.send();
                break;
            case SELECT_ALL:
                if (a11y != null) a11y.selectAll();
                break;
            case COPY:
                if (a11y != null) a11y.copy();
                break;
            case CUT:
                if (a11y != null) a11y.cut();
                break;
            case PASTE:
                if (a11y != null) a11y.paste();
                break;
            case CLEAR:
                if (a11y != null) a11y.clear();
                break;
            case STOP:
                stopListening();
                toast("Stopped");
                break;
            case RESTYLE_FORMAL:
                restyle(a11y, Engine.Style.FORMAL);
                break;
            case RESTYLE_CASUAL:
                restyle(a11y, Engine.Style.CASUAL);
                break;
            case RESTYLE_EXCITED:
                restyle(a11y, Engine.Style.EXCITED);
                break;
            case RESTYLE_NOTES:
                restyle(a11y, Engine.Style.NOTES);
                break;
            default:
                break;
        }
    }

    private void insertRaw(SottoAccessibilityService a11y, String text) {
        if (a11y != null) a11y.insert(text);
    }

    private void restyle(SottoAccessibilityService a11y, Engine.Style style) {
        if (a11y == null) return;
        String current = a11y.currentText();
        if (current == null || current.trim().isEmpty()) {
            toast("Nothing to restyle");
            return;
        }
        Engine.Config cfg = new Engine.Config();
        cfg.removeFillers = false;
        cfg.spokenPunctuation = false;
        cfg.autoCapitalize = true;
        cfg.adaptTone = false;
        Engine.Result result = Engine.process(current, style, "", cfg);
        a11y.clear();
        a11y.insert(result.text);
        toast("Restyle: " + style.label);
    }

    private void copyFallback(String text) {
        try {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Sotto", text));
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyListeningUi() {
        if (bubble != null) bubble.setListening(listening);
        if (!listening && partialView != null) partialView.setVisibility(View.GONE);
        updateNotification();
    }

    private void notifyState() {
        if (listener != null) listener.onState(listening, running);
    }

    private void toast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private void openApp() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        settings = new Settings(this);
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        running = true;
        if (settings.isBubble()) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    showBubble();
                }
            });
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopListening();
            removeBubble();
            stopForeground(true);
            running = false;
            notifyState();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_UNDO.equals(action)) {
            SottoAccessibilityService a11y = SottoAccessibilityService.get();
            if (a11y != null && a11y.undoLast()) toast("Undone");
            return START_STICKY;
        }
        if (ACTION_TOGGLE.equals(action)) {
            toggleListening();
            return START_STICKY;
        }
        if (!running) {
            running = true;
            startForeground(NOTIFICATION_ID, buildNotification());
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        running = false;
        listening = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Throwable ignored) {
            }
            recognizer = null;
        }
        removeBubble();
        instance = null;
        notifyState();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------------------------------------------------------------- bubble

    private void showBubble() {
        if (overlayRoot != null) return;
        if (!android.provider.Settings.canDrawOverlays(this)) return;
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return;

        int size = settings.getBubbleSize();
        int bubblePx = Ui.dpi(this, size);

        LinearLayout column = Ui.column(this);
        column.setGravity(Gravity.CENTER_HORIZONTAL);

        partialView = Ui.text(this, "", 12, Ui.TEXT);
        partialView.setMaxWidth(Ui.dpi(this, 250));
        partialView.setMaxLines(3);
        partialView.setPadding(Ui.dpi(this, 10), Ui.dpi(this, 7),
                Ui.dpi(this, 10), Ui.dpi(this, 7));
        partialView.setBackground(Ui.stroked(0xEE12161B, Ui.STROKE, 14, this));
        partialView.setVisibility(View.GONE);
        column.addView(partialView);
        column.addView(Ui.spacer(this, 6));

        bubble = new BubbleView(this);
        bubble.setAlpha(Math.max(0.35f, Math.min(1f, settings.getBubbleOpacity() / 100f)));
        column.addView(bubble, new LinearLayout.LayoutParams(bubblePx, bubblePx));

        overlayRoot = new FrameLayout(this);
        overlayRoot.addView(column);

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = settings.getBubbleX();
        overlayParams.y = settings.getBubbleY();
        if (overlayParams.x == 0 && overlayParams.y == 0) {
            overlayParams.x = Ui.dpi(this, 12);
            overlayParams.y = getResources().getDisplayMetrics().heightPixels / 2;
        }

        bubble.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return onBubbleTouch(event);
            }
        });

        try {
            windowManager.addView(overlayRoot, overlayParams);
        } catch (Throwable t) {
            overlayRoot = null;
        }
    }

    private boolean onBubbleTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownAt = SystemClock.uptimeMillis();
                touchDownX = event.getRawX();
                touchDownY = event.getRawY();
                windowDownX = overlayParams.x;
                windowDownY = overlayParams.y;
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - touchDownX;
                float dy = event.getRawY() - touchDownY;
                if (Math.abs(dx) > Ui.dp(this, 8) || Math.abs(dy) > Ui.dp(this, 8)) {
                    dragging = true;
                }
                if (dragging) {
                    overlayParams.x = windowDownX + (int) dx;
                    overlayParams.y = windowDownY + (int) dy;
                    try {
                        windowManager.updateViewLayout(overlayRoot, overlayParams);
                    } catch (Throwable ignored) {
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                long held = SystemClock.uptimeMillis() - touchDownAt;
                if (dragging) {
                    settings.setBubblePosition(overlayParams.x, overlayParams.y);
                } else if (held > 550) {
                    openApp();
                } else {
                    toggleListening();
                }
                return true;
            default:
                return false;
        }
    }

    private void removeBubble() {
        if (overlayRoot != null && windowManager != null) {
            try {
                windowManager.removeView(overlayRoot);
            } catch (Throwable ignored) {
            }
        }
        overlayRoot = null;
        bubble = null;
        partialView = null;
    }

    public void refreshBubble() {
        removeBubble();
        if (settings.isBubble()) showBubble();
    }

    // ---------------------------------------------------------- notification

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW);
                channel.setDescription(getString(R.string.notif_channel_desc));
                channel.setShowBadge(false);
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent toggle = new Intent(this, SottoService.class);
        toggle.setAction(ACTION_TOGGLE);
        PendingIntent togglePi = PendingIntent.getService(this, 2, toggle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent undo = new Intent(this, SottoService.class);
        undo.setAction(ACTION_UNDO);
        PendingIntent undoPi = PendingIntent.getService(this, 3, undo,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle(listening ? "Listening…" : "Sotto is ready")
                .setContentText(listening ? "Tap the bubble to stop" : "Tap the bubble in any app")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(openPi)
                .setOngoing(true)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause, "Toggle", togglePi).build())
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_revert, "Undo", undoPi).build());
        return b.build();
    }

    private void updateNotification() {
        if (!running) return;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!settings.isBubble()) {
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }
}
