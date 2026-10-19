package app.sotto.voice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
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
import app.sotto.voice.ui.IconView;
import app.sotto.voice.ui.OrbView;
import app.sotto.voice.ui.Ui;

public final class MainActivity extends Activity {

    private static final int REQ_MIC = 11;
    private static final int REQ_NOTIF = 12;

    private Settings settings;
    private HistoryStore history;
    private MapStore snippets;
    private MapStore dictionary;
    private FrameLayout content;
    private LinearLayout[] navItems;
    private int tab;

    private TextView homeStatus;
    private TextView homeSub;
    private TextView homePrimary;
    private TextView homeWords;
    private TextView homeSessions;
    private TextView homeSaved;
    private OrbView homeOrb;
    private EditText tryField;
    private SpeechRecognizer inline;
    private boolean inlineListening;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new Settings(this);
        history = new HistoryStore(this);
        snippets = MapStore.snippets(this);
        dictionary = MapStore.dictionary(this);
        configureWindow();
        setContentView(buildRoot());
        showTab(0);
        requestNotificationPermission();
    }

    private void configureWindow() {
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Ui.BG);
        if (Build.VERSION.SDK_INT >= 21) {
            w.setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            w.getDecorView().setSystemUiVisibility(0);
        }
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dpi(this, 24);
    }

    private View buildRoot() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);

        View top = new View(this);
        top.setLayoutParams(new LinearLayout.LayoutParams(1, statusBarHeight()));
        root.addView(top);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View line = new View(this);
        line.setBackgroundColor(Ui.STROKE);
        root.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dpi(this, 1))));

        LinearLayout nav = Ui.row(this);
        nav.setBackgroundColor(Ui.BG);
        nav.setPadding(Ui.dpi(this, 8), Ui.dpi(this, 4), Ui.dpi(this, 8), Ui.dpi(this, 4));
        String[] labels = {"Dictate", "History", "Library", "Settings"};
        String[] icons = {"mic", "history", "book", "sliders"};
        navItems = new LinearLayout[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout item = Ui.navItem(this, icons[i], labels[i]);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTab(index);
                }
            });
            nav.addView(item, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            navItems[i] = item;
        }
        root.addView(nav);
        return root;
    }

    private void showTab(int index) {
        tab = index;
        content.removeAllViews();
        View screen;
        if (index == 0) {
            screen = settings.isOnboarded() ? buildHome() : buildOnboarding();
        } else if (index == 1) {
            screen = buildHistory();
        } else if (index == 2) {
            screen = buildLibrary();
        } else {
            screen = buildSettings();
        }
        content.addView(screen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (int i = 0; i < navItems.length; i++) {
            Ui.navSelected(navItems[i], i == index);
        }
    }

    private ScrollView scroll(LinearLayout body) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setVerticalScrollBarEnabled(false);
        s.setBackgroundColor(Ui.BG);
        s.addView(body);
        return s;
    }

    private LinearLayout page() {
        LinearLayout body = Ui.column(this);
        body.setPadding(Ui.dpi(this, 20), Ui.dpi(this, 14),
                Ui.dpi(this, 20), Ui.dpi(this, 28));
        return body;
    }

    private LinearLayout header(String title, String sub) {
        LinearLayout h = Ui.column(this);
        TextView t = Ui.bold(this, title, 26, Ui.TEXT);
        h.addView(t);
        if (sub != null) {
            TextView s = Ui.text(this, sub, 13, Ui.MUTED);
            s.setPadding(0, Ui.dpi(this, 4), 0, 0);
            h.addView(s);
        }
        h.setPadding(0, 0, 0, Ui.dpi(this, 18));
        return h;
    }

    private LinearLayout logoRow() {
        LinearLayout row = Ui.row(this);
        TextView logo = Ui.bold(this, "sotto", 28, Ui.TEXT);
        logo.setLetterSpacing(0.03f);
        row.addView(logo);
        row.addView(Ui.gap(this, 10));
        row.addView(Ui.pill(this, NativeCore.available() ? "RUST CORE" : "LOCAL", Ui.ACCENT));
        View spacer = new View(this);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView privacy = Ui.pill(this, "PRIVATE", Ui.ACCENT2);
        row.addView(privacy);
        return row;
    }

    // ------------------------------------------------------------------ home

    private View buildHome() {
        LinearLayout body = page();
        body.addView(logoRow());

        TextView tagline = Ui.text(this, "Speak. It's written.", 14, Ui.MUTED);
        tagline.setPadding(0, Ui.dpi(this, 8), 0, Ui.dpi(this, 18));
        body.addView(tagline);

        body.addView(buildHero());
        body.addView(Ui.spacer(this, 18));
        body.addView(buildSetup());
        body.addView(Ui.spacer(this, 18));
        body.addView(buildStats());
        body.addView(Ui.spacer(this, 18));
        body.addView(buildTone());
        body.addView(Ui.spacer(this, 18));
        body.addView(buildTry());
        body.addView(Ui.spacer(this, 18));
        body.addView(buildRecent());
        return scroll(body);
    }

    private View buildHero() {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dpi(this, 18), Ui.dpi(this, 22), Ui.dpi(this, 18), Ui.dpi(this, 18));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Ui.gradient(0xFF151A12, 0xFF0C0E10, 24, this));

        homeOrb = new OrbView(this);
        boolean listening = SottoService.isListening();
        homeOrb.setListening(listening);
        card.addView(homeOrb, new LinearLayout.LayoutParams(Ui.dpi(this, 190), Ui.dpi(this, 190)));

        homeStatus = Ui.bold(this, listening ? "Listening…" : "Ready when you are", 20, Ui.TEXT);
        homeStatus.setGravity(Gravity.CENTER);
        card.addView(homeStatus);

        homeSub = Ui.text(this,
                listening ? "Speak naturally. Tap stop when you're done."
                        : "Tap the orb or the button to dictate into any app.",
                12, Ui.MUTED);
        homeSub.setGravity(Gravity.CENTER);
        homeSub.setPadding(Ui.dpi(this, 16), Ui.dpi(this, 6), Ui.dpi(this, 16), 0);
        card.addView(homeSub);

        LinearLayout tags = Ui.row(this);
        tags.setGravity(Gravity.CENTER);
        tags.setPadding(0, Ui.dpi(this, 14), 0, Ui.dpi(this, 16));
        tags.addView(Ui.pill(this, "UNLIMITED", Ui.ACCENT));
        tags.addView(Ui.gap(this, 8));
        tags.addView(Ui.pill(this, "ON-DEVICE", Ui.ACCENT2));
        tags.addView(Ui.gap(this, 8));
        tags.addView(Ui.pill(this, "NO ACCOUNT", Ui.VIOLET));
        card.addView(tags);

        homePrimary = Ui.primary(this, listening ? "Stop dictating" : "Start dictating");
        homePrimary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDictation();
            }
        });
        card.addView(homePrimary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        homeOrb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDictation();
            }
        });
        return card;
    }

    private View buildSetup() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.sectionLabel(this, "Setup"));

        card.addView(permissionRow("Microphone", "Capture your voice",
                hasMic(), "Grant", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                    }
                }));
        card.addView(Ui.spacer(this, 10));
        card.addView(permissionRow("Accessibility", "Insert text into other apps",
                isAccessibilityEnabled(), "Open", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    }
                }));
        card.addView(Ui.spacer(this, 10));
        card.addView(permissionRow("Floating bubble", "Dictate without switching apps",
                android.provider.Settings.canDrawOverlays(this), "Open", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    }
                }));
        return card;
    }

    private View permissionRow(String title, String sub, boolean granted,
                               String action, View.OnClickListener listener) {
        LinearLayout row = Ui.row(this);
        LinearLayout texts = Ui.column(this);
        TextView t = Ui.bold(this, title, 14, Ui.TEXT);
        TextView s = Ui.text(this, sub, 11, Ui.MUTED);
        s.setPadding(0, Ui.dpi(this, 2), 0, 0);
        texts.addView(t);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (granted) {
            LinearLayout done = Ui.row(this);
            IconView check = new IconView(this, "check");
            check.setColor(Ui.ACCENT);
            done.addView(check, new LinearLayout.LayoutParams(Ui.dpi(this, 16), Ui.dpi(this, 16)));
            TextView label = Ui.bold(this, "Ready", 12, Ui.ACCENT);
            label.setPadding(Ui.dpi(this, 5), 0, 0, 0);
            done.addView(label);
            row.addView(done);
        } else {
            TextView btn = Ui.button(this, action, Ui.SURFACE2, Ui.TEXT, 10);
            btn.setTextSize(11);
            btn.setPadding(Ui.dpi(this, 14), Ui.dpi(this, 7),
                    Ui.dpi(this, 14), Ui.dpi(this, 7));
            btn.setOnClickListener(listener);
            row.addView(btn);
        }
        return row;
    }

    private View buildStats() {
        LinearLayout wrap = Ui.column(this);
        wrap.addView(Ui.sectionLabel(this, "Your voice, so far"));

        LinearLayout row = Ui.row(this);
        row.addView(statCard("words", String.valueOf(settings.getWords()), "dictated"));
        row.addView(Ui.gap(this, 10));
        row.addView(statCard("sessions", String.valueOf(settings.getSessions()), "sessions"));
        row.addView(Ui.gap(this, 10));
        row.addView(statCard("time", Settings.formatSaved(
                Settings.secondsSaved(settings.getWords())), "saved"));
        wrap.addView(row);
        return wrap;
    }

    private View statCard(String key, String value, String label) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dpi(this, 14), Ui.dpi(this, 14), Ui.dpi(this, 14), Ui.dpi(this, 14));
        card.setBackground(Ui.stroked(Ui.SURFACE, Ui.STROKE, 16, this));
        TextView v = Ui.bold(this, value, 19, Ui.ACCENT);
        TextView l = Ui.text(this, label, 10, Ui.MUTED);
        card.addView(v);
        card.addView(l);
        return card;
    }

    private View buildTone() {
        LinearLayout wrap = Ui.column(this);
        wrap.addView(Ui.sectionLabel(this, "Tone"));
        LinearLayout row = Ui.row(this);
        row.setPadding(0, 0, 0, 0);
        String[] labels = {"Default", "Formal", "Casual", "Very casual", "Excited", "Notes"};
        String[] keys = {"default", "formal", "casual", "very_casual", "excited", "notes"};
        String current = settings.getStyle();
        for (int i = 0; i < labels.length; i++) {
            final String key = keys[i];
            TextView chip = Ui.chip(this, labels[i]);
            Ui.selectedChip(this, chip, key.equals(current));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    settings.setStyle(key);
                    showTab(0);
                    toast("Tone: " + key.replace('_', ' '));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dpi(this, 8);
            lp.bottomMargin = Ui.dpi(this, 8);
            row.addView(chip, lp);
        }
        wrap.addView(row);
        TextView hint = Ui.text(this,
                "Tone adapts per app when set to Default: email becomes Formal, chat becomes Casual.",
                11, Ui.MUTED);
        wrap.addView(hint);
        return wrap;
    }

    private View buildTry() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.sectionLabel(this, "Try it here"));

        tryField = new EditText(this);
        tryField.setHint("Tap the mic, or type and press Polish…");
        tryField.setHintTextColor(Ui.MUTED);
        tryField.setTextColor(Ui.TEXT);
        tryField.setTextSize(14);
        tryField.setGravity(Gravity.TOP | Gravity.START);
        tryField.setMinLines(3);
        tryField.setMaxLines(6);
        tryField.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        tryField.setPadding(Ui.dpi(this, 14), Ui.dpi(this, 12),
                Ui.dpi(this, 14), Ui.dpi(this, 12));
        tryField.setBackground(Ui.stroked(Ui.SURFACE2, Ui.STROKE, 14, this));
        card.addView(tryField, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(Ui.spacer(this, 10));

        LinearLayout actions = Ui.row(this);
        TextView dictate = Ui.button(this, "Dictate", Ui.ACCENT, 0xFF07120A, 12);
        dictate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dictationIntoField();
            }
        });
        TextView polish = Ui.button(this, "Polish", Ui.SURFACE2, Ui.TEXT, 12);
        polish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                polishField();
            }
        });
        TextView copy = Ui.button(this, "Copy", Ui.SURFACE2, Ui.TEXT, 12);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tryField != null) copyText(tryField.getText().toString());
            }
        });
        actions.addView(dictate);
        actions.addView(Ui.gap(this, 8));
        actions.addView(polish);
        actions.addView(Ui.gap(this, 8));
        actions.addView(copy);
        card.addView(actions);

        TextView hint = Ui.text(this,
                "Dictate uses Android's recognizer. Polish runs entirely in Sotto's Rust core.",
                11, Ui.MUTED);
        hint.setPadding(0, Ui.dpi(this, 10), 0, 0);
        card.addView(hint);
        return card;
    }

    private View buildRecent() {
        LinearLayout wrap = Ui.column(this);
        LinearLayout head = Ui.row(this);
        head.addView(Ui.sectionLabel(this, "Recent"), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView all = Ui.bold(this, "See all", 11, Ui.ACCENT);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTab(1);
            }
        });
        head.addView(all);
        wrap.addView(head);

        List<HistoryStore.Entry> entries = history.all();
        if (entries.isEmpty()) {
            wrap.addView(emptyCard("Nothing yet.", "Your last dictations will appear here."));
            return wrap;
        }
        int count = Math.min(3, entries.size());
        for (int i = 0; i < count; i++) {
            wrap.addView(historyCard(entries.get(i), i, true));
            wrap.addView(Ui.spacer(this, 8));
        }
        return wrap;
    }

    // ------------------------------------------------------------ onboarding

    private View buildOnboarding() {
        LinearLayout body = page();
        body.addView(logoRow());
        body.addView(Ui.spacer(this, 24));

        TextView title = Ui.bold(this, "Your voice,\nwithout the meter.", 34, Ui.TEXT);
        title.setLineSpacing(Ui.dp(this, 2), 1f);
        body.addView(title);
        TextView sub = Ui.text(this,
                "Dictate anywhere. Sotto polishes the text in a Rust engine on your phone. "
                        + "No account, no word limits, no audio upload by default.",
                14, Ui.MUTED);
        sub.setPadding(0, Ui.dpi(this, 12), 0, Ui.dpi(this, 26));
        body.addView(sub);

        body.addView(feature("mic", "Tap the bubble, talk", "It appears above every app and inserts where your cursor is."));
        body.addView(feature("bolt", "Unlimited and fast", "No 5-minute session cap. No weekly word counter."));
        body.addView(feature("shield", "Private by design", "The text engine is local. Audio uses your device recognizer."));
        body.addView(feature("sparkle", "Voice commands", "Say “scratch that”, “new paragraph”, “send it”."));
        body.addView(Ui.spacer(this, 14));

        LinearLayout card = Ui.card(this);
        card.addView(Ui.sectionLabel(this, "Two permissions, then you're done"));
        card.addView(permissionRow("Microphone", "Required to hear you",
                hasMic(), "Grant", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                    }
                }));
        card.addView(Ui.spacer(this, 10));
        card.addView(permissionRow("Floating bubble", "Required to dictate in other apps",
                android.provider.Settings.canDrawOverlays(this), "Open", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    }
                }));
        card.addView(Ui.spacer(this, 10));
        card.addView(permissionRow("Accessibility", "Required to insert text",
                isAccessibilityEnabled(), "Open", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    }
                }));
        body.addView(card);
        body.addView(Ui.spacer(this, 16));

        TextView go = Ui.primary(this, "Start using Sotto");
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.setOnboarded(true);
                settings.setStartOnBoot(true);
                showTab(0);
            }
        });
        body.addView(go);
        return scroll(body);
    }

    private View feature(String icon, String title, String sub) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dpi(this, 10), 0, Ui.dpi(this, 10));
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(Ui.round(Ui.alpha(Ui.ACCENT, 0.10f), 14, this));
        IconView iv = new IconView(this, icon);
        iv.setColor(Ui.ACCENT);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                Ui.dpi(this, 20), Ui.dpi(this, 20));
        ilp.gravity = Gravity.CENTER;
        badge.addView(iv, ilp);
        row.addView(badge, new LinearLayout.LayoutParams(Ui.dpi(this, 42), Ui.dpi(this, 42)));
        row.addView(Ui.gap(this, 12));
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.bold(this, title, 14, Ui.TEXT));
        TextView s = Ui.text(this, sub, 12, Ui.MUTED);
        s.setPadding(0, Ui.dpi(this, 2), 0, 0);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    // --------------------------------------------------------------- history

    private View buildHistory() {
        LinearLayout body = page();
        LinearLayout head = Ui.row(this);
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.bold(this, "History", 26, Ui.TEXT));
        TextView sub = Ui.text(this, "Stored only on this device.", 12, Ui.MUTED);
        sub.setPadding(0, Ui.dpi(this, 3), 0, 0);
        texts.addView(sub);
        head.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView clear = Ui.bold(this, "Clear", 12, Ui.DANGER);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearHistory();
            }
        });
        head.addView(clear);
        head.setPadding(0, 0, 0, Ui.dpi(this, 18));
        body.addView(head);

        List<HistoryStore.Entry> entries = history.all();
        if (entries.isEmpty()) {
            body.addView(emptyCard("No dictations yet.", "Tap the bubble in any app to get started."));
            return scroll(body);
        }
        for (int i = 0; i < entries.size(); i++) {
            body.addView(historyCard(entries.get(i), i, false));
            body.addView(Ui.spacer(this, 8));
        }
        return scroll(body);
    }

    private View historyCard(final HistoryStore.Entry e, final int index, boolean compact) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dpi(this, 14), Ui.dpi(this, 13),
                Ui.dpi(this, 14), Ui.dpi(this, 10));
        card.setBackground(Ui.stroked(Ui.SURFACE, Ui.STROKE, 16, this));

        TextView text = Ui.text(this, e.text, 13, Ui.TEXT);
        text.setMaxLines(compact ? 2 : 6);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(text);
        card.addView(Ui.spacer(this, 7));

        LinearLayout meta = Ui.row(this);
        TextView metaText = Ui.text(this,
                e.timeLabel() + "  ·  " + (e.app == null || e.app.isEmpty() ? "Sotto" :
                        e.app.substring(e.app.lastIndexOf('.') + 1))
                        + "  ·  " + e.words + " words", 10, Ui.MUTED);
        meta.addView(metaText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView copy = Ui.bold(this, "Copy", 11, Ui.ACCENT);
        copy.setPadding(Ui.dpi(this, 8), Ui.dpi(this, 4), Ui.dpi(this, 8), Ui.dpi(this, 4));
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyText(e.text);
            }
        });
        meta.addView(copy);

        TextView del = Ui.bold(this, "Delete", 11, Ui.MUTED);
        del.setPadding(Ui.dpi(this, 8), Ui.dpi(this, 4), 0, Ui.dpi(this, 4));
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                history.removeAt(index);
                showTab(tab);
            }
        });
        meta.addView(del);
        card.addView(meta);
        return card;
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("Clear history?")
                .setMessage("This removes every transcript stored on this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        history.clear();
                        settings.resetStats();
                        showTab(1);
                    }
                })
                .show();
    }

    // --------------------------------------------------------------- library

    private View buildLibrary() {
        LinearLayout body = page();
        body.addView(header("Library",
                "Snippets expand as you speak. The dictionary fixes names and jargon."));

        LinearLayout add = Ui.card(this);
        add.addView(Ui.sectionLabel(this, "Add an entry"));
        final EditText key = libField("Trigger, e.g. “my address”");
        final EditText value = libField("Replacement, e.g. “221B Baker Street”");
        add.addView(key);
        add.addView(Ui.spacer(this, 8));
        add.addView(value);
        add.addView(Ui.spacer(this, 10));
        LinearLayout actions = Ui.row(this);
        TextView snippetBtn = Ui.button(this, "Add snippet", Ui.ACCENT, 0xFF07120A, 12);
        snippetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addEntry(snippets, key, value);
            }
        });
        TextView dictBtn = Ui.button(this, "Add to dictionary", Ui.SURFACE2, Ui.TEXT, 12);
        dictBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addEntry(dictionary, key, value);
            }
        });
        actions.addView(snippetBtn);
        actions.addView(Ui.gap(this, 8));
        actions.addView(dictBtn);
        add.addView(actions);
        body.addView(add);

        body.addView(Ui.spacer(this, 18));
        body.addView(entrySection("Snippets", snippets, "Speak the trigger, get the full text."));
        body.addView(Ui.spacer(this, 18));
        body.addView(entrySection("Dictionary", dictionary, "Corrects casing and spelling on the fly."));
        return scroll(body);
    }

    private EditText libField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Ui.MUTED);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setPadding(Ui.dpi(this, 12), Ui.dpi(this, 11),
                Ui.dpi(this, 12), Ui.dpi(this, 11));
        e.setBackground(Ui.stroked(Ui.SURFACE2, Ui.STROKE, 12, this));
        return e;
    }

    private void addEntry(MapStore store, EditText key, EditText value) {
        String k = key.getText().toString().trim();
        String v = value.getText().toString().trim();
        if (k.isEmpty() || v.isEmpty()) {
            toast("Both fields are needed");
            return;
        }
        store.put(k, v);
        key.setText("");
        value.setText("");
        toast("Saved");
        showTab(2);
    }

    private View entrySection(String title, MapStore store, String hint) {
        LinearLayout wrap = Ui.column(this);
        wrap.addView(Ui.sectionLabel(this, title));
        Map<String, String> all = store.all();
        if (all.isEmpty()) {
            TextView empty = Ui.text(this, "Nothing added yet. " + hint, 12, Ui.MUTED);
            empty.setPadding(0, 0, 0, Ui.dpi(this, 6));
            wrap.addView(empty);
            return wrap;
        }
        for (Map.Entry<String, String> e : all.entrySet()) {
            final String key = e.getKey();
            LinearLayout row = Ui.row(this);
            row.setPadding(Ui.dpi(this, 14), Ui.dpi(this, 11),
                    Ui.dpi(this, 12), Ui.dpi(this, 11));
            row.setBackground(Ui.stroked(Ui.SURFACE, Ui.STROKE, 14, this));

            LinearLayout texts = Ui.column(this);
            texts.addView(Ui.bold(this, key, 13, Ui.TEXT));
            TextView v = Ui.text(this, e.getValue(), 11, Ui.MUTED);
            v.setPadding(0, Ui.dpi(this, 2), 0, 0);
            texts.addView(v);
            row.addView(texts, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            IconView trash = new IconView(this, "trash");
            trash.setColor(Ui.MUTED);
            trash.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    store.remove(key);
                    showTab(2);
                }
            });
            row.addView(trash, new LinearLayout.LayoutParams(Ui.dpi(this, 20), Ui.dpi(this, 20)));
            wrap.addView(row);
            wrap.addView(Ui.spacer(this, 7));
        }
        return wrap;
    }

    // -------------------------------------------------------------- settings

    private View buildSettings() {
        LinearLayout body = page();
        body.addView(header("Settings", "Everything is stored on this device."));

        body.addView(Ui.sectionLabel(this, "Dictation"));
        LinearLayout dictation = Ui.card(this);
        dictation.addView(switchRow("On-device only",
                "Prefer the offline recognizer when the device has one",
                settings.isOffline(), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        settings.setOffline(v);
                        restartService();
                    }
                }));
        dictation.addView(Ui.divider(this));
        dictation.addView(switchRow("Insert automatically",
                "Place text in the focused field",
                settings.isAutoInsert(), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        settings.setAutoInsert(v);
                    }
                }));
        dictation.addView(Ui.divider(this));
        dictation.addView(switchRow("Keep listening",
                "Restart after each phrase",
                settings.isContinuous(), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        settings.setContinuous(v);
                    }
                }));
        dictation.addView(Ui.divider(this));
        dictation.addView(switchRow("Adapt tone per app",
                "Email becomes formal, chat becomes casual",
                settings.isAdaptTone(), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        settings.setAdaptTone(v);
                    }
                }));
        body.addView(dictation);

        body.addView(Ui.spacer(this, 18));
        body.addView(Ui.sectionLabel(this, "Text polish"));
        LinearLayout polish = Ui.card(this);
        polish.addView(switchRow("Remove filler words",
                "um, uh, erm, you know",
                settings.isFillers(), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        settings.setFillers(v);
                    }
                }));
        polish.addView(Ui.divider(this));
        polish.addView(switchRow("Spoken punctuation",
                "“comma”, “period”, “question mark”",
                settings.isSpokenPunctuation(), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        settings.setSpokenPunctuation(v);
                    }
                }));
        polish.addView(Ui.divider(this));
        polish.addView(switchRow("Auto-capitalize",
                "Sentences, names and “I”",
                settings.isCapitalize(), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        settings.setCapitalize(v);
                    }
                }));
        polish.addView(Ui.divider(this));
        polish.addView(switchRow("Trailing space",
                "Add a space after each insertion",
                settings.isTrailingSpace(), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        settings.setTrailingSpace(v);
                    }
                }));
        body.addView(polish);

        body.addView(Ui.spacer(this, 18));
        body.addView(Ui.sectionLabel(this, "Bubble"));
        LinearLayout bubbleCard = Ui.card(this);
        bubbleCard.addView(switchRow("Floating bubble",
                "Show Sotto above other apps",
                settings.isBubble(), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton b, boolean v) {
                        settings.setBubble(v);
                        restartService();
                    }
                }));
        bubbleCard.addView(Ui.divider(this));
        bubbleCard.addView(sliderRow("Size", 48, 84, settings.getBubbleSize(),
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) settings.setBubbleSize(progress);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        refreshBubble();
                    }
                }));
        bubbleCard.addView(Ui.divider(this));
        bubbleCard.addView(sliderRow("Opacity", 35, 100, settings.getBubbleOpacity(),
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser) settings.setBubbleOpacity(progress);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        refreshBubble();
                    }
                }));
        body.addView(bubbleCard);

        body.addView(Ui.spacer(this, 18));
        body.addView(Ui.sectionLabel(this, "Language"));
        LinearLayout langCard = Ui.card(this);
        langCard.addView(Ui.text(this,
                "BCP-47 language tag. Leave blank for the device default.",
                11, Ui.MUTED));
        langCard.addView(Ui.spacer(this, 8));
        final EditText lang = libField("en-US");
        lang.setText(settings.getLanguage());
        langCard.addView(lang);
        langCard.addView(Ui.spacer(this, 8));
        TextView save = Ui.button(this, "Save language", Ui.SURFACE2, Ui.TEXT, 12);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.setLanguage(lang.getText().toString().trim());
                toast("Language saved");
            }
        });
        langCard.addView(save);
        body.addView(langCard);

        body.addView(Ui.spacer(this, 18));
        body.addView(Ui.sectionLabel(this, "Data"));
        LinearLayout data = Ui.card(this);
        TextView clearHistory = Ui.bold(this, "Clear dictation history", 14, Ui.TEXT);
        clearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearHistory();
            }
        });
        data.addView(clearHistory);
        data.addView(Ui.divider(this));
        data.addView(Ui.spacer(this, 10));
        TextView reset = Ui.bold(this, "Reset statistics", 14, Ui.TEXT);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.resetStats();
                toast("Statistics reset");
                showTab(3);
            }
        });
        data.addView(reset);
        body.addView(data);

        body.addView(Ui.spacer(this, 18));
        body.addView(Ui.sectionLabel(this, "About"));
        LinearLayout about = Ui.card(this);
        about.addView(aboutRow("Version", "1.0.0"));
        about.addView(Ui.divider(this));
        about.addView(aboutRow("Text engine", NativeCore.version()));
        about.addView(Ui.divider(this));
        about.addView(aboutRow("Package", getPackageName()));
        about.addView(Ui.divider(this));
        TextView license = Ui.text(this,
                "Apache-2.0. No account, no analytics, no word limits.",
                11, Ui.MUTED);
        license.setPadding(0, Ui.dpi(this, 10), 0, 0);
        about.addView(license);
        body.addView(about);
        return scroll(body);
    }

    private View switchRow(String title, String sub, boolean checked,
                           CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dpi(this, 11), 0, Ui.dpi(this, 11));
        LinearLayout texts = Ui.column(this);
        texts.addView(Ui.bold(this, title, 14, Ui.TEXT));
        TextView s = Ui.text(this, sub, 11, Ui.MUTED);
        s.setPadding(0, Ui.dpi(this, 2), Ui.dpi(this, 8), 0);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle);
        return row;
    }

    private View sliderRow(String label, int min, int max, int value,
                           SeekBar.OnSeekBarChangeListener listener) {
        LinearLayout wrap = Ui.column(this);
        wrap.setPadding(0, Ui.dpi(this, 10), 0, Ui.dpi(this, 8));
        wrap.addView(Ui.bold(this, label, 14, Ui.TEXT));
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setOnSeekBarChangeListener(listener);
        wrap.addView(bar);
        return wrap;
    }

    private View aboutRow(String label, String value) {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dpi(this, 6), 0, Ui.dpi(this, 6));
        row.addView(Ui.text(this, label, 13, Ui.MUTED), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(this, value, 12, Ui.TEXT));
        return row;
    }

    private View emptyCard(String title, String sub) {
        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER);
        TextView t = Ui.bold(this, title, 15, Ui.TEXT);
        t.setGravity(Gravity.CENTER);
        card.addView(t);
        TextView s = Ui.text(this, sub, 12, Ui.MUTED);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, Ui.dpi(this, 5), 0, 0);
        card.addView(s);
        return card;
    }

    // ---------------------------------------------------------------- actions

    private void startDictation() {
        if (!hasMic()) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            toast("Allow the floating bubble first");
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        SottoService service = SottoService.get();
        if (service == null) {
            Intent i = new Intent(this, SottoService.class);
            i.setAction(SottoService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    SottoService s = SottoService.get();
                    if (s != null) s.startListening();
                    refreshHomeState();
                }
            }, 450);
        } else {
            service.toggleListening();
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    refreshHomeState();
                }
            }, 250);
        }
    }

    private void refreshHomeState() {
        boolean listening = SottoService.isListening();
        if (homeStatus != null) {
            homeStatus.setText(listening ? "Listening…" : "Ready when you are");
        }
        if (homeSub != null) {
            homeSub.setText(listening ? "Speak naturally. Tap stop when you're done."
                    : "Tap the orb or the button to dictate into any app.");
        }
        if (homePrimary != null) {
            homePrimary.setText(listening ? "Stop dictating" : "Start dictating");
        }
        if (homeOrb != null) {
            homeOrb.setListening(listening);
        }
    }

    private void dictationIntoField() {
        if (!hasMic()) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("No speech recognizer installed");
            return;
        }
        if (inline != null) {
            try {
                inline.destroy();
            } catch (Throwable ignored) {
            }
        }
        inline = SpeechRecognizer.createSpeechRecognizer(this);
        inlineListening = true;
        toast("Listening…");
        inline.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onError(int error) {
                inlineListening = false;
                toast("Didn't catch that");
            }

            @Override
            public void onResults(Bundle results) {
                inlineListening = false;
                ArrayList<String> list =
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (list == null || list.isEmpty()) return;
                String raw = list.get(0);
                String polished = NativeCore.process(raw, settings, getPackageName(),
                        snippets.all(), dictionary.all());
                if (tryField != null) tryField.setText(polished.trim());
                history.add(polished.trim(), getPackageName(), settings.getStyle(),
                        NativeCore.countWords(polished));
                settings.recordSession(NativeCore.countWords(polished));
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        if (settings.isOffline()) {
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        }
        if (settings.getLanguage() != null && settings.getLanguage().length() > 0) {
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, settings.getLanguage());
        }
        inline.startListening(intent);
    }

    private void polishField() {
        if (tryField == null) return;
        String raw = tryField.getText().toString();
        if (raw.trim().isEmpty()) {
            toast("Type something first");
            return;
        }
        String polished = NativeCore.process(raw, settings, getPackageName(),
                snippets.all(), dictionary.all());
        tryField.setText(polished.trim());
        history.add(polished.trim(), getPackageName(), settings.getStyle(),
                NativeCore.countWords(polished));
        settings.recordSession(NativeCore.countWords(polished));
        toast("Polished");
    }

    private void copyText(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Sotto", text));
            toast("Copied");
        } catch (Throwable t) {
            toast("Could not copy");
        }
    }

    private void restartService() {
        SottoService service = SottoService.get();
        if (service != null) {
            service.refreshBubble();
        }
    }

    private void refreshBubble() {
        SottoService service = SottoService.get();
        if (service != null) service.refreshBubble();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private boolean hasMic() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isAccessibilityEnabled() {
        if (SottoAccessibilityService.connected()) return true;
        try {
            String enabled = android.provider.Settings.Secure.getString(
                    getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            String target = getPackageName() + "/" + SottoAccessibilityService.class.getName();
            return enabled.contains(target);
        } catch (Throwable t) {
            return false;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MIC) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                toast("Microphone ready");
            } else {
                toast("Microphone is required to dictate");
            }
            showTab(tab);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SottoService.setListener(new SottoService.Listener() {
            @Override
            public void onState(boolean listening, boolean running) {
                refreshHomeState();
            }

            @Override
            public void onPartial(String text) {
            }

            @Override
            public void onFinal(String text) {
                if (tab == 0) showTab(0);
            }

            @Override
            public void onLevel(float level) {
            }
        });
        if (tab == 0) refreshHomeState();
    }

    @Override
    protected void onPause() {
        SottoService.setListener(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (inline != null) {
            try {
                inline.destroy();
            } catch (Throwable ignored) {
            }
            inline = null;
        }
        super.onDestroy();
    }
}
