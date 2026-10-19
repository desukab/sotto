package app.sotto.voice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.sotto.voice.core.AppStyles;
import app.sotto.voice.core.CloudPolish;
import app.sotto.voice.core.Engine;
import app.sotto.voice.core.HistoryStore;
import app.sotto.voice.core.MapStore;
import app.sotto.voice.core.NativeCore;
import app.sotto.voice.core.Settings;
import app.sotto.voice.core.Variables;
import app.sotto.voice.ui.BubbleView;
import app.sotto.voice.ui.Ui;
import app.sotto.voice.ui.WaveView;

/**
 * Foreground service that owns the floating bubble, the recognizer and the
 * polish pipeline.
 */
public final class SottoService extends Service {

    public static final String ACTION_START = "app.sotto.voice.START";
    public static final String ACTION_STOP = "app.sotto.voice.STOP";
    public static final String ACTION_TOGGLE = "app.sotto.voice.TOGGLE";
    public static final String ACTION_UNDO = "app.sotto.voice.UNDO";

    private static final int NOTIFICATION_ID = 41;
    private static final String CHANNEL = "sotto";
    private static final int MAX_WAVE = 48;

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
    private AppStyles appStyles;
    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private FrameLayout overlayRoot;
    private BubbleView bubble;
    private WaveView wave;
    private TextView partialView;
    private SpeechRecognizer recognizer;
    private ExecutorService cloudExecutor;
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
        boolean onDevice = Build.VERSION.SDK_INT >= 31
                && SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        if (settings.isOffline() && !onDevice) {
            toast("On-device speech is unavailable — turn off “On-device only”");
            return;
        }
        promoteToMicrophone();
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

    private void promoteToMicrophone() {
        if (Build.VERSION.SDK_INT < 29) return;
        try {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            if (Build.VERSION.SDK_INT >= 34) {
                type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
            }
            startForeground(NOTIFICATION_ID, buildNotification(), type);
        } catch (Throwable ignored) {
            // Android can refuse to promote to a microphone service from the
            // background. Recognition still works while the app is visible.
        }
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
                    if (partialView != null) {
                        partialView.setText("Listening…");
                        partialView.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    float level = Math.max(0f, Math.min(1f, (rmsdB + 2f) / 12f));
                    if (bubble != null) bubble.setLevel(level);
                    if (wave != null) wave.push(level);
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
                        if (error == SpeechRecognizer.ERROR_NO_MATCH
                                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            toast("Didn't catch that");
                        }
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
                    showPartial(text);
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

    private void showPartial(String text) {
        if (partialView == null) return;
        partialView.setText(text);
        partialView.setVisibility(View.VISIBLE);
    }

    private void hidePartialSoon(long delay) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (partialView != null && !listening) partialView.setVisibility(View.GONE);
            }
        }, delay);
    }

    private void handleUtterance(String raw) {
        int ordinal = NativeCore.command(raw);
        Engine.Command command = Engine.Command.values()[
                Math.max(0, Math.min(Engine.Command.values().length - 1, ordinal))];
        SottoAccessibilityService a11y = SottoAccessibilityService.get();

        if (command != Engine.Command.NONE) {
            hidePartialSoon(0);
            runCommand(command, raw);
            return;
        }

        Map<String, String> snippets = MapStore.snippets(this).all();
        Map<String, String> dictionary = MapStore.dictionary(this).all();
        String pkg = a11y != null ? a11y.focusedPackage() : "";
        final String styleKey = resolveStyle(pkg);

        String polished = NativeCore.processRaw(raw, styleKey, pkg,
                settings.isFillers(), settings.isSpokenPunctuation(),
                settings.isCapitalize(), settings.isTrailingSpace(),
                settings.isAdaptTone(), snippets, dictionary);
        if (polished == null) polished = raw;
        polished = polished.trim();
        if (settings.isVariables()) polished = Variables.expand(this, polished);

        final String local = polished;
        if (settings.isCloud() && settings.getCloudKey() != null
                && !settings.getCloudKey().trim().isEmpty() && isOnline()) {
            showPartial("Polishing with cloud…");
            if (cloudExecutor == null) cloudExecutor = Executors.newSingleThreadExecutor();
            cloudExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    String result = null;
                    String error = null;
                    try {
                        result = CloudPolish.polish(settings, local, styleKey);
                    } catch (Throwable t) {
                        error = t.getMessage();
                    }
                    final String ok = result;
                    final String fail = error;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (ok != null && !ok.isEmpty()) {
                                deliver(ok, styleKey, false);
                            } else {
                                if (fail != null) toast("Cloud polish failed: " + fail);
                                deliver(local, styleKey, false);
                            }
                        }
                    });
                }
            });
        } else {
            deliver(local, styleKey, true);
        }
    }

    /** Insert the finished text, record it and surface it to the UI. */
    private void deliver(String text, String styleKey, boolean record) {
        SottoAccessibilityService a11y = SottoAccessibilityService.get();
        String pkg = a11y != null ? a11y.focusedPackage() : "";
        String finalText = text == null ? "" : text.trim();
        if (finalText.isEmpty()) return;

        boolean inserted = false;
        if (settings.isAutoInsert() && a11y != null) {
            inserted = a11y.insert(finalText);
        }
        if (!inserted) {
            if (a11y != null) a11y.toClipboard(finalText);
            else copyFallback(finalText);
            toast(hasA11y() ? "Copied to clipboard" : "Enable Sotto accessibility to insert");
        }

        if (record) {
            new HistoryStore(this).add(finalText, pkg, styleKey,
                    NativeCore.countWords(finalText));
            settings.recordSession(NativeCore.countWords(finalText));
        }
        if (settings.isHaptics()) {
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) v.vibrate(12);
            } catch (Throwable ignored) {
            }
        }
        showPartial(finalText);
        hidePartialSoon(2600);
        if (listener != null) listener.onFinal(finalText);
    }

    private boolean hasA11y() {
        return SottoAccessibilityService.connected();
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    private String resolveStyle(String pkg) {
        String style = settings.getStyle();
        if (style == null || style.isEmpty()) style = "default";
        if (!"default".equals(style)) return style;
        if (settings.isAdaptTone() && settings.isAppToneMemory()) {
            String remembered = appStyles.styleFor(pkg);
            if (remembered != null) return remembered;
        }
        return style;
    }

    private void runCommand(Engine.Command command, String raw) {
        SottoAccessibilityService a11y = SottoAccessibilityService.get();
        switch (command) {
            case UNDO:
                if (a11y != null && a11y.undoLast()) toast("Undone");
                else toast("Nothing to undo");
                break;
            case NEW_PARAGRAPH:
                insertRaw(a11y, "\n\n");
                break;
            case NEW_LINE:
                insertRaw(a11y, "\n");
                break;
            case SEND:
                if (a11y != null && !a11y.send()) toast("Nothing to send");
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
                if (a11y != null && a11y.clear()) toast("Cleared");
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
        if (a11y != null && !a11y.insert(text)) toast("No text field found");
    }

    private void restyle(SottoAccessibilityService a11y, Engine.Style style) {
        if (a11y == null) {
            toast("Enable Sotto accessibility first");
            return;
        }
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
        if (!a11y.replaceText(result.text)) {
            toast("Could not update the field");
            return;
        }
        if (settings.isAppToneMemory()) {
            appStyles.remember(a11y.focusedPackage(), style.key);
        }
        toast("Tone: " + style.label);
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
        appStyles = new AppStyles(this);
        createChannel();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification());
            }
        } catch (Throwable ignored) {
        }
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
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NOTIFICATION_ID, buildNotification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification());
                }
            } catch (Throwable ignored) {
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        running = false;
        listening = false;
        handler.removeCallbacksAndMessages(null);
        if (cloudExecutor != null) {
            cloudExecutor.shutdownNow();
            cloudExecutor = null;
        }
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

        partialView = Ui.text(this, "", 13, Ui.TEXT);
        partialView.setMaxWidth(Ui.dpi(this, 280));
        partialView.setMaxLines(4);
        partialView.setPadding(Ui.dpi(this, 12), Ui.dpi(this, 9),
                Ui.dpi(this, 12), Ui.dpi(this, 9));
        partialView.setBackground(Ui.stroked(0xF212161B, Ui.STROKE, 16, this));
        partialView.setVisibility(View.GONE);
        column.addView(partialView);

        wave = new WaveView(this);
        wave.setBarColor(Ui.ACCENT);
        LinearLayout.LayoutParams waveLp = new LinearLayout.LayoutParams(
                Ui.dpi(this, 96), Ui.dpi(this, 20));
        waveLp.bottomMargin = Ui.dpi(this, 4);
        column.addView(wave, waveLp);

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
        wave = null;
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
