package com.hideo.foldplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * L'écran de jeu intégré : l'image du cœur libretro dans la moitié haute, la manette Origami
 * (la disposition active) dans la moitié basse. Maison : un appui = options, deux = retour au Flex.
 */
public class GameActivity extends Activity {
    static final String EXTRA_URI = "uri";
    static final String EXTRA_PATH = "path";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_SYSTEM = "system";
    static final String EXTRA_CORE = "core";

    /** Formats de disque qui pointent vers d'autres fichiers : il faut le vrai chemin. */
    private static final Set<String> SHEETS = new HashSet<String>(Arrays.asList("cue", "m3u", "gdi", "ccd", "toc"));

    private Store store;
    private CoreManager cores;
    private String uri, path, name, title, core, system;
    private int frameW, frameH;
    private float coreAspect = 4f / 3f;
    private volatile boolean smooth = true;
    private String fileBase;

    private FrameLayout stage;
    private SurfaceView screen;
    private TextView overlay;
    private ControlDeckView deck;

    private final Object surfaceLock = new Object();
    private SurfaceHolder holder;
    private Thread emu;
    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean dialogOpen;
    private final ConcurrentLinkedQueue<Runnable> jobs = new ConcurrentLinkedQueue<Runnable>();
    private int buttons;
    private float aspect = 4f / 3f;
    private Runnable pendingHome;
    private boolean editedLayout;
    private volatile boolean fast;
    /** Dernier état d'accéléré transmis au pont (fil d'émulation). */
    private boolean ffSent;
    /** Cœurs qui tournent sur leur propre fil et règlent eux-mêmes leur vitesse. */
    private boolean selfPaced;
    private boolean padDown;
    private int padX, padY;

    @Override
    protected void onCreate(Bundle state) {
        Theme.inGame = true;
        super.onCreate(state);
        I18n.attach(this);
        Theme.sync(this);
        Theme.orientation(this);
        DebugLog.sync(this);
        store = new Store(this);
        cores = new CoreManager(this);
        Intent i = getIntent();
        uri = i.getStringExtra(EXTRA_URI);
        path = i.getStringExtra(EXTRA_PATH);
        name = i.getStringExtra(EXTRA_NAME);
        title = i.getStringExtra(EXTRA_TITLE);
        core = i.getStringExtra(EXTRA_CORE);
        system = String.valueOf(i.getStringExtra(EXTRA_SYSTEM));
        smooth = store.smooth(system);
        int dot = name == null ? -1 : name.lastIndexOf('.');
        fileBase = dot > 0 ? name.substring(0, dot) : String.valueOf(name);
        store.setSaveSystem(fileBase, system);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sensors = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
        pads = new Pads(this);
        build();
        hideBars();

        if (!Libretro.available()) {
            fail("Demande un appareil 64 bits (arm64).");
            return;
        }
        running = true;
        emu = new Thread(new Runnable() {
            public void run() {
                emulate();
            }
        }, "foldplay-emu");
        emu.setPriority(Thread.MAX_PRIORITY);
        emu.start();
    }

    private void build() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Theme.gap());
        rootFrame = frame;
        LinearLayout root = new LinearLayout(this);
        column = root;
        root.setOrientation(LinearLayout.VERTICAL);
        // Même allure que la vue Flex : deux pilules séparées par un mince filet.
        root.setPadding(dp(4), dp(4), dp(4), dp(4));
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));

        stage = new FrameLayout(this);
        stage.setOnHierarchyChangeListener(new android.view.ViewGroup.OnHierarchyChangeListener() {
            public void onChildViewAdded(View parent, View child) {
                deckUnderMenu();
            }

            public void onChildViewRemoved(View parent, View child) {
                deckUnderMenu();
            }
        });
        stage.setBackground(Theme.pill(Color.BLACK, dp(28)));
        stage.setClipToOutline(true);
        // L'image du jeu reste à l'écart des coins arrondis.
        stage.setPadding(dp(10), dp(10), dp(10), dp(10));
        screen = new SurfaceView(this);
        screen.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder h) {
                synchronized (surfaceLock) {
                    holder = h;
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder h) {
                synchronized (surfaceLock) {
                    holder = null;
                }
            }
        });
        stage.addView(screen, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        stage.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != or - ol || b - t != ob - ot) applyDisplay();
            }
        });
        overlay = new TextView(this);
        overlay.setTextColor(Theme.TEXT);
        overlay.setTextSize(14);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(24), dp(12), dp(24), dp(12));
        overlay.setText("Chargement de « " + title + " »…");
        stage.addView(overlay, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));

        gap = new View(this);
        root.addView(gap, new LinearLayout.LayoutParams(-1, dp(4)));

        deck = new ControlDeckView(this);
        deck.setSystem(system);
        formatKey();
        deck.setSource(store);
        deck.setKeyListener(new ControlView.Listener() {
            @Override
            public void onDown(String key) {
                press(key, true);
            }

            @Override
            public void onUp(String key) {
                press(key, false);
            }

            @Override
            public void onAxis(String key, float x, float y) {
                axis(key, x, y);
            }
        }, store.haptics());
        deck.setFollowScreen(true);
        root.addView(deck, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(frame);
        hinge = sensors == null ? null : sensors.getDefaultSensor(36);
        // La disposition arrive une fois la manette mesurée : on vérifie alors s'il faut la superposer.
        deck.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                v.post(new Runnable() {
                    public void run() { updateOverlay(); }
                });
            }
        });
    }

    // ------------------------------------------------------------------ disposition divisée

    private FrameLayout rootFrame;

    // ------------------------------------------------------------------ ATH de monitoring

    private TextView hud;
    private volatile int framesRun, framesShown;
    /** Échantillons stéréo rendus par le cœur : le temps émulé, quelle que soit la cadence d'images du jeu. */
    private volatile long audioFrames;
    private volatile int audioRate;
    private long lastAudio;
    private volatile double targetFps = 60;
    // Pic du temps entre deux images affichées depuis la dernière mesure (écrit par le fil d'émulation).
    private volatile long lastShownNs, maxFrameNs;
    private int lastRun, lastShown;
    private long lastHudAt, lastCpuMs;
    private final android.os.Handler hudTimer = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable hudTick = new Runnable() {
        public void run() {
            updateHud();
            if (hud != null && hud.getVisibility() == View.VISIBLE) hudTimer.postDelayed(this, store.hudRate());
        }
    };

    /** Affiche ou cache l'ATH selon les réglages (Réglages > Affichage, ou menu pause). */
    private void applyHud() {
        boolean on = store.hud();
        if (on && hud == null) {
            hud = new TextView(this);
            hud.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE,
                    android.graphics.Typeface.BOLD));
            hud.setTextColor(Color.WHITE);
            hud.setIncludeFontPadding(false);
            rootFrame.addView(hud);
        }
        if (hud == null) return;
        boolean line = !"block".equals(store.hudStyle());
        String pos = store.hudPos();
        int gravity = Gravity.TOP | ("center".equals(pos) ? Gravity.CENTER_HORIZONTAL
                : "right".equals(pos) ? Gravity.END : Gravity.START);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, gravity);
        int m = line ? dp(6) : dp(20);
        lp.setMargins(m, m, m, 0);
        hud.setLayoutParams(lp);
        hud.setTranslationY(0);
        Hud.avoidCutout(hud, m);
        hud.setTextSize(Hud.SIZES[Math.max(0, Math.min(2, store.hudSize()))]);
        hud.setSingleLine(line);
        hud.setMaxLines(line ? 1 : 20);
        hud.setShadowLayer(dp(2), 0, 0, Color.BLACK);
        if (store.hudBackground()) {
            hud.setBackground(Theme.pill(line ? 0x8C000000 : 0x99000000, dp(line ? 6 : 12)));
            hud.setPadding(dp(line ? 8 : 10), dp(line ? 3 : 6), dp(line ? 8 : 10), dp(line ? 3 : 6));
        } else {
            hud.setBackground(null);
            hud.setPadding(dp(2), 0, dp(2), 0);
        }
        hudTimer.removeCallbacks(hudTick);
        hud.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            hud.bringToFront();
            lastHudAt = 0;
            hudTimer.post(hudTick);
        }
    }

    private void updateHud() {
        if (hud == null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        long cpu = android.os.Process.getElapsedCpuTime();
        int run = framesRun;
        int shown = framesShown;
        boolean block = "block".equals(store.hudStyle());
        int label = Hud.labelColor(this, store.hudColor());
        if (lastHudAt == 0) {
            lastHudAt = now;
            lastCpuMs = cpu;
            lastRun = run;
            lastShown = shown;
            lastAudio = audioFrames;
            maxFrameNs = 0;
            android.text.SpannableString s = new android.text.SpannableString("ATH  mesure…");
            s.setSpan(new android.text.style.ForegroundColorSpan(label), 0, 3, 0);
            hud.setText(s);
            return;
        }
        float sec = Math.max(0.001f, (now - lastHudAt) / 1000f);
        Hud.Sample h = new Hud.Sample();
        h.paused = paused;
        h.fps = (shown - lastShown) / sec;
        h.frameMs = h.fps > 0.5f ? 1000f / h.fps : 0;
        h.frameMaxMs = maxFrameNs / 1e6f;
        maxFrameNs = 0;
        // Un cœur à fil propre rend une image par image émulée : c'est elle qui donne sa vitesse.
        h.speed = (float) (((selfPaced ? shown - lastShown : run - lastRun)) / sec / targetFps * 100);
        // Un jeu PAL ou à demi-cadence (25 ou 30 images/s) tourne pourtant à pleine vitesse : le son
        // émulé, lui, avance toujours au rythme réel. Il donne la vitesse quand le cœur en rend.
        long au = audioFrames;
        if (selfPaced && audioRate > 0 && au > lastAudio && lastHudAt > 0) h.speed = (au - lastAudio) / sec / audioRate * 100;
        lastAudio = au;
        // Temps processeur de l'app sur le temps écoulé : 100 % = un cœur plein.
        h.cpu = (cpu - lastCpuMs) / 10f / sec;
        lastHudAt = now;
        lastCpuMs = cpu;
        lastRun = run;
        lastShown = shown;

        java.util.Set<String> items = store.hudItems();
        if (items.contains("mhz")) h.mhz = Hud.cpuMhz();
        Runtime rt = Runtime.getRuntime();
        h.ramMb = (int) ((android.os.Debug.getNativeHeapAllocatedSize() >> 20)
                + ((rt.totalMemory() - rt.freeMemory()) >> 20));
        Intent bat = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat != null) {
            int level = bat.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = bat.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0) h.battery = level * 100 / Math.max(1, scale);
            h.charging = bat.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0;
            h.tempC = bat.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        }
        h.fahrenheit = "f".equals(store.tempUnit());
        if (items.contains("heat")) {
            h.cpuC = Hud.cpuC();
            h.gpuC = Hud.gpuC();
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            h.heat = pm.getCurrentThermalStatus();
        }
        h.core = core;
        h.width = frameW;
        h.height = frameH;
        hud.setText(Hud.compose(h, items, block, label));
        Hud.avoidCutout(hud, block ? dp(20) : dp(6));
    }
    private LinearLayout column;
    private View gap;
    private android.hardware.SensorManager sensors;
    private android.hardware.Sensor hinge;
    private float hingeAngle = -1;
    private boolean overlayMode;

    private final android.hardware.SensorEventListener hingeListener = new android.hardware.SensorEventListener() {
        public void onSensorChanged(android.hardware.SensorEvent e) {
            hingeAngle = e.values[0];
            updateOverlay();
        }

        public void onAccuracyChanged(android.hardware.Sensor s, int a) {
        }
    };

    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        Theme.orientation(this);
        // Une retouche ne survit pas à un changement d'écran : sa grille n'existe plus.
        if (editBar != null) endDeckEdit(false);
        if (deck != null) deck.post(new Runnable() {
            public void run() { updateOverlay(); }
        });
    }

    /**
     * Téléphone tenu en paysage (ouvert à plat ou écran externe) : au choix du joueur, le jeu prend
     * tout l'écran et la manette se pose par-dessus avec l'opacité réglée, ou elle reste séparée
     * dans la pilule du bas. Plié en mode Flex ou en portrait, elle reste toujours séparée.
     */
    private void updateOverlay() {
        if (deck == null || column == null) return;
        android.content.res.Configuration c = getResources().getConfiguration();
        // Fermé (écran externe) ou ouvert à plat : seul l'entre-deux (mode Flex) garde la manette séparée.
        boolean flat = hingeAngle < 0 || hingeAngle > 150f || hingeAngle < 30f;
        int want = 0;
        // Manette physique branchée : le jeu prend tout l'écran, plus de manette à l'écran.
        if (store.padFullscreen() && pads.any()) want = 2;
        // Manette Mini : le jeu passe en plein écran, la petite manette carrée flotte dessus.
        else if (deck.layout() != null && deck.layout().floating()) want = 3;
        // Manette Flex : le jeu prend tout l'écran, ses deux moitiés se posent sur les bords.
        else if (deck.layout() != null && deck.layout().overlaid()) want = 1;
        // Paysage (hors mode Tsuru) : jeu en plein écran, manette transparente par-dessus.
        // Portrait : manette visible dans sa moitié d'écran.
        else if (flat && c.screenWidthDp > c.screenHeightDp) want = 1;
        // Petit écran presque carré (écran externe d'un Z Flip ou d'un Razr) : deux moitiés seraient
        // minuscules, la manette se superpose au jeu.
        else if (c.smallestScreenWidthDp < 480 && Math.max(c.screenWidthDp, c.screenHeightDp)
                < 1.3f * Math.min(c.screenWidthDp, c.screenHeightDp)) want = 1;
        updateQuickBar(want == 2);
        if (want == screenMode) {
            if (want == 1 && deck.getParent() == rootFrame) deck.setLayoutParams(safeArea());
            if (want == 3) placeMini();
            deck.setOverlay(want == 1, store.overlayOpacity());
            return;
        }
        screenMode = want;
        overlayMode = want == 1;
        if (deck.getParent() != null) ((android.view.ViewGroup) deck.getParent()).removeView(deck);
        if (miniBox != null && miniBox.getParent() != null) ((android.view.ViewGroup) miniBox.getParent()).removeView(miniBox);
        if (want != 0) {
            gap.setVisibility(View.GONE);
            column.setPadding(0, 0, 0, 0);
            stage.setBackground(new android.graphics.drawable.ColorDrawable(Color.BLACK));
            stage.setPadding(0, 0, 0, 0);
            if (want == 1) rootFrame.addView(deck, safeArea());
            if (want == 3) {
                buildMini();
                miniDeckHolder.addView(deck, new FrameLayout.LayoutParams(-1, -1));
                rootFrame.addView(miniBox);
                placeMini();
            }
        } else {
            gap.setVisibility(View.VISIBLE);
            column.setPadding(dp(4), dp(4), dp(4), dp(4));
            stage.setBackground(Theme.pill(Color.BLACK, dp(28)));
            stage.setPadding(dp(10), dp(10), dp(10), dp(10));
            column.addView(deck, new LinearLayout.LayoutParams(-1, 0, 1f));
        }
        deck.setOverlay(want == 1, store.overlayOpacity());
        deckUnderMenu();
        updateFlexLock();
    }

    // ------------------------------------------------------------------ Flex : cadenas

    private TextView flexLock;
    private View flexShield;

    /**
     * Manette Flex superposée : un cadenas en haut au centre. Ouvert, la manette glisse vers le haut
     * ou le bas sous le doigt ; refermé, les touches reprennent la main. La hauteur est gardée.
     */
    private void updateFlexLock() {
        boolean on = screenMode == 1 && deck.layout() != null && deck.layout().overlaid();
        if (!on) {
            if (flexLock != null && flexLock.getParent() != null) rootFrame.removeView(flexLock);
            if (flexShield != null && flexShield.getParent() != null) rootFrame.removeView(flexShield);
            deck.setTranslationY(0);
            return;
        }
        if (flexLock == null) {
            flexLock = new TextView(this);
            flexLock.setGravity(Gravity.CENTER);
            flexLock.setTextSize(16);
            flexLock.setBackground(Theme.pill(0x99000000, dp(18)));
            flexLock.setContentDescription("Déplacer la manette");
            flexLock.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { setFlexUnlocked(flexShield == null || flexShield.getParent() == null); }
            });
        }
        if (flexLock.getParent() == null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(44), dp(36), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            lp.topMargin = dp(8);
            rootFrame.addView(flexLock, lp);
            flexLock.setText("🔒");
        }
        flexLock.bringToFront();
        deck.post(new Runnable() { public void run() { applyFlexOffset(store.flexOffset() * deck.getHeight()); } });
    }

    private void setFlexUnlocked(boolean open) {
        flexLock.setText(open ? "🔓" : "🔒");
        if (!open) {
            if (flexShield != null && flexShield.getParent() != null) rootFrame.removeView(flexShield);
            if (deck.getHeight() > 0) store.setFlexOffset(deck.getTranslationY() / deck.getHeight());
            return;
        }
        if (flexShield == null) {
            flexShield = new View(this);
            flexShield.setBackgroundColor(0x2200FF88);
            flexShield.setOnTouchListener(new View.OnTouchListener() {
                float sy, oy;
                public boolean onTouch(View v, android.view.MotionEvent e) {
                    if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                        sy = e.getRawY();
                        oy = deck.getTranslationY();
                    } else if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                        applyFlexOffset(oy + e.getRawY() - sy);
                    }
                    return true;
                }
            });
        }
        if (flexShield.getParent() == null) rootFrame.addView(flexShield, new FrameLayout.LayoutParams(-1, -1));
        flexLock.bringToFront();
        I18n.toast(this, "Glisse pour monter ou descendre la manette, puis referme le cadenas.",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    /** Décale la manette sans qu'aucune touche ne sorte de l'écran. */
    private void applyFlexOffset(float dy) {
        int top = Integer.MAX_VALUE, bottom = 0;
        for (int i = 0; i < deck.getChildCount(); i++) {
            View k = deck.getChildAt(i);
            if (k.getVisibility() != View.VISIBLE || k.getHeight() == 0) continue;
            top = Math.min(top, k.getTop());
            bottom = Math.max(bottom, k.getBottom());
        }
        if (top == Integer.MAX_VALUE) return;
        float min = -top + dp(4), max = deck.getHeight() - bottom - dp(4);
        deck.setTranslationY(Math.max(Math.min(min, 0), Math.min(Math.max(max, 0), dy)));
    }

    // ------------------------------------------------------------------ manette Mini

    private FloatingPad miniBox;
    private FrameLayout miniDeckHolder;

    /** Manette Mini : fenêtre flottante (poignée en haut pour la déplacer, coins pour la taille). */
    private void buildMini() {
        if (miniBox != null) return;
        miniBox = new FloatingPad(this, store);
        miniDeckHolder = miniBox.content();
    }

    private void placeMini() {
        if (miniBox != null) miniBox.place();
    }

    // ------------------------------------------------------------------ retouche sur le jeu

    /** Barre d'outils de la retouche sur le jeu (null hors retouche). */
    private LinearLayout editBar;
    private String editBackup;
    private boolean editBarTop = true;

    /**
     * La manette se retouche là où elle sert, par-dessus le jeu en pause : même grille, même
     * échelle, et ce qu'on pose est ce qu'on obtient. La retouche vaut pour cet écran et cette
     * orientation seulement.
     */
    private void startDeckEdit() {
        if (deck == null || deck.layout() == null || editBar != null) return;
        paused = true;
        try {
            editBackup = deck.layout().toJson().toString();
        } catch (org.json.JSONException e) {
            editBackup = null;
        }
        deck.pinHere();
        deck.setVisibility(View.VISIBLE);
        deck.setEditing(true);
        editBar = new LinearLayout(this);
        editBar.setOrientation(LinearLayout.HORIZONTAL);
        editBar.setGravity(Gravity.CENTER);
        editBar.setPadding(dp(6), dp(6), dp(6), dp(6));
        editBar.setBackground(Theme.pill(0xE00B0D10, dp(18)));
        editBar.setElevation(dp(8));
        editTool("Ajouter", new Runnable() { public void run() { addKeyMenu(); } });
        editTool("Retirer", new Runnable() { public void run() { deck.removePicked(); } });
        editTool("−", new Runnable() { public void run() { deck.resizePicked(-1); } });
        editTool("+", new Runnable() { public void run() { deck.resizePicked(1); } });
        editTool("⇅", new Runnable() {
            public void run() {
                editBarTop = !editBarTop;
                placeEditBar();
            }
        });
        editTool("Annuler", new Runnable() { public void run() { endDeckEdit(false); } });
        editTool("Terminer", new Runnable() { public void run() { endDeckEdit(true); } });
        rootFrame.addView(editBar);
        placeEditBar();
    }

    private void placeEditBar() {
        if (editBar == null) return;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2,
                Gravity.CENTER_HORIZONTAL | (editBarTop ? Gravity.TOP : Gravity.BOTTOM));
        lp.topMargin = lp.bottomMargin = dp(10);
        editBar.setLayoutParams(lp);
    }

    private void editTool(String label, final Runnable action) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(13);
        b.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,
                android.graphics.Typeface.BOLD));
        b.setTextColor(label.equals("Terminer") ? Theme.CODEC : Theme.TEXT);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setBackground(Theme.pill(0x33FFFFFF, dp(14)));
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { action.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = lp.rightMargin = dp(3);
        editBar.addView(b, lp);
    }

    private void addKeyMenu() {
        CodecMenu m = codec("Ajouter une touche", "RETOUCHE SUR LE JEU");
        for (final String t : ControlLayout.TYPES) {
            m.item(ControlLayout.label(t), null, new Runnable() {
                public void run() { deck.addControl(t); }
            });
        }
        m.show(stage);
    }

    private void endDeckEdit(boolean keep) {
        if (editBar == null) return;
        deck.setEditing(false);
        rootFrame.removeView(editBar);
        editBar = null;
        if (keep) {
            store.setScreenLayout(deck.columns(), deck.rows(), deck.layout());
        } else if (editBackup != null) {
            try {
                deck.setLayout(ControlLayout.fromJson(new org.json.JSONObject(editBackup)));
            } catch (org.json.JSONException e) {
                deck.reloadFromSource();
            }
        }
        deck.setOverlay(overlayMode, store.overlayOpacity());
        paused = dialogOpen;
    }

    // ------------------------------------------------------------------ boutons rapides

    private LinearLayout quickBar;

    /**
     * Manette physique en plein écran : sauvegarde rapide, chargement rapide et ×2 restent à
     * portée de doigt, petits et translucides, en haut à droite.
     */
    private void updateQuickBar(boolean padMode) {
        boolean on = padMode && store.padQuickButtons();
        if (!on) {
            if (quickBar != null) quickBar.setVisibility(View.GONE);
            return;
        }
        if (quickBar == null) {
            quickBar = new LinearLayout(this);
            quickBar.setOrientation(LinearLayout.HORIZONTAL);
            String[][] keys = {{"QSAVE", "SAVE"}, {"QLOAD", "LOAD"}, {"FAST", "×2"}};
            for (final String[] k : keys) {
                TextView b = new TextView(this);
                b.setText(k[1]);
                b.setTextSize(12);
                b.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF,
                        android.graphics.Typeface.BOLD));
                b.setTextColor(Color.WHITE);
                b.setGravity(Gravity.CENTER);
                b.setBackground(Theme.pill(0x66000000, dp(16)));
                b.setAlpha(0.75f);
                b.setOnTouchListener(new View.OnTouchListener() {
                    public boolean onTouch(View v, android.view.MotionEvent e) {
                        int a = e.getActionMasked();
                        if (a == android.view.MotionEvent.ACTION_DOWN) {
                            v.setAlpha(1f);
                            press(k[0], true);
                        } else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
                            v.setAlpha(0.75f);
                            press(k[0], false);
                        }
                        return true;
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(58), dp(32));
                lp.setMargins(dp(4), 0, dp(4), 0);
                quickBar.addView(b, lp);
            }
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            lp.setMargins(0, dp(8), dp(8), 0);
            rootFrame.addView(quickBar, lp);
        }
        quickBar.setVisibility(View.VISIBLE);
        quickBar.bringToFront();
    }

    /**
     * Manette en surimpression : cachée tant qu'un menu est ouvert, sinon elle le recouvre et prend
     * ses touches. On choisit alors au doigt ou à la manette physique.
     */
    /**
     * Manette superposée : une marge tout autour, élargie à l'encoche, pour qu'aucune touche ne passe
     * sous les coins arrondis ou le bord de l'écran.
     */
    private FrameLayout.LayoutParams safeArea() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        int m = dp(8);
        int l = m, t = m, r = m, b = m;
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.view.WindowInsets in = getWindow().getDecorView().getRootWindowInsets();
            android.view.DisplayCutout cut = in == null ? null : in.getDisplayCutout();
            if (cut != null) {
                l = Math.max(l, cut.getSafeInsetLeft());
                t = Math.max(t, cut.getSafeInsetTop());
                r = Math.max(r, cut.getSafeInsetRight());
                b = Math.max(b, cut.getSafeInsetBottom());
            }
        }
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private void deckUnderMenu() {
        if (deck == null || stage == null) return;
        boolean hide = overlayMode && CodecMenu.find(stage) != null;
        deck.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);
    }

    private int screenMode;

    // ------------------------------------------------------------------ manettes physiques

    private Pads pads;
    private final java.util.Map<Integer, Pads.Profile> profiles = new java.util.HashMap<Integer, Pads.Profile>();
    private final java.util.Map<Integer, boolean[]> padState = new java.util.HashMap<Integer, boolean[]>();
    private final int[] portButtons = new int[4];

    private final android.hardware.input.InputManager.InputDeviceListener padListener =
            new android.hardware.input.InputManager.InputDeviceListener() {
                public void onInputDeviceAdded(int id) {
                    android.view.InputDevice d = android.view.InputDevice.getDevice(id);
                    if (!Pads.isPad(d)) return;
                    Pads.Profile p = profile(d);
                    PadBanner.show(rootFrame, "MANETTE CONNECTÉE", p.name + " · joueur " + p.player, true);
                    Theme.orientation(GameActivity.this);
                    PadBanner.animateLayout(rootFrame);
                    updateOverlay();
                }

                public void onInputDeviceRemoved(int id) {
                    Pads.Profile p = profiles.remove(id);
                    padState.remove(id);
                    if (p != null) {
                        PadBanner.show(rootFrame, "MANETTE DÉCONNECTÉE", p.name
                                + (pads.any() || !store.padFullscreen() ? "" : " · manette tactile de retour"), false);
                        if (p.player >= 2) {
                            portButtons[p.player - 1] = 0;
                            if (Libretro.available()) Libretro.nativeSetPortButtons(p.player - 1, 0);
                        }
                    }
                    Theme.orientation(GameActivity.this);
                    PadBanner.animateLayout(rootFrame);
                    updateOverlay();
                }

                public void onInputDeviceChanged(int id) {
                    profiles.remove(id);
                }
            };

    private Pads.Profile profile(android.view.InputDevice d) {
        Pads.Profile p = profiles.get(d.getId());
        if (p == null) {
            p = pads.profile(d);
            profiles.put(d.getId(), p);
        }
        return p;
    }

    /** Une touche d'un joueur : le joueur 1 partage tout avec la manette à l'écran (menus compris). */
    private void playerPress(int player, String key, boolean down) {
        if (player <= 1) {
            press(key, down);
            return;
        }
        if (CodecMenu.find(stage) != null || player > 4) return;
        int id = Libretro.retroId(key, system);
        if (id < 0) return;
        int port = player - 1;
        if (down) portButtons[port] |= 1 << id;
        else portButtons[port] &= ~(1 << id);
        if (Libretro.available()) Libretro.nativeSetPortButtons(port, portButtons[port]);
    }

    private static final String[] PAD_DIRS = {"UP", "RIGHT", "DOWN", "LEFT"};

    /** Sticks, croix analogique et gâchettes des manettes physiques. */
    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent e) {
        android.view.InputDevice d = e.getDevice();
        if (!Pads.isPad(d) || e.getActionMasked() != android.view.MotionEvent.ACTION_MOVE
                || (e.getSource() & android.view.InputDevice.SOURCE_JOYSTICK) != android.view.InputDevice.SOURCE_JOYSTICK) {
            return super.dispatchGenericMotionEvent(e);
        }
        Pads.Profile p = profile(d);
        float dz = p.deadzone / 100f;
        float lx = dead(e.getAxisValue(android.view.MotionEvent.AXIS_X), dz);
        float ly = dead(e.getAxisValue(android.view.MotionEvent.AXIS_Y), dz);
        boolean rxry = d.getMotionRange(android.view.MotionEvent.AXIS_RX) != null
                && d.getMotionRange(android.view.MotionEvent.AXIS_Z) == null;
        float rx = dead(e.getAxisValue(rxry ? android.view.MotionEvent.AXIS_RX : android.view.MotionEvent.AXIS_Z), dz);
        float ry = dead(e.getAxisValue(rxry ? android.view.MotionEvent.AXIS_RY : android.view.MotionEvent.AXIS_RZ), dz);
        float hx = e.getAxisValue(android.view.MotionEvent.AXIS_HAT_X);
        float hy = e.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y);
        float lt = Math.max(e.getAxisValue(android.view.MotionEvent.AXIS_LTRIGGER), e.getAxisValue(android.view.MotionEvent.AXIS_BRAKE));
        float rt = Math.max(e.getAxisValue(android.view.MotionEvent.AXIS_RTRIGGER), e.getAxisValue(android.view.MotionEvent.AXIS_GAS));

        boolean[] st = padState.get(d.getId());
        if (st == null) {
            st = new boolean[10];
            padState.put(d.getId(), st);
        }
        // Croix analogique (hat) → directions.
        boolean[] hat = {hy < -0.5f, hx > 0.5f, hy > 0.5f, hx < -0.5f};
        for (int i = 0; i < 4; i++) {
            if (hat[i] != st[i]) {
                st[i] = hat[i];
                playerPress(p.player, PAD_DIRS[i], hat[i]);
            }
        }
        boolean l2 = lt > 0.5f;
        boolean r2 = rt > 0.5f;
        if (l2 != st[4]) {
            st[4] = l2;
            playerPress(p.player, "L2", l2);
        }
        if (r2 != st[5]) {
            st[5] = r2;
            playerPress(p.player, "R2", r2);
        }
        if (p.player <= 1 && CodecMenu.find(stage) != null) {
            // Menu ouvert : le stick gauche sert aussi à choisir.
            boolean[] dirs = {ly < -0.6f, lx > 0.6f, ly > 0.6f, lx < -0.6f};
            for (int i = 0; i < 4; i++) {
                if (dirs[i] != st[6 + i]) {
                    st[6 + i] = dirs[i];
                    if (dirs[i]) press(PAD_DIRS[i], true);
                }
            }
            return true;
        }
        if (!Libretro.available()) return true;
        int port = Math.max(0, Math.min(3, p.player - 1));
        Libretro.nativeSetPortAnalog(port, 0, Math.round(lx * 32767), Math.round(ly * 32767));
        Libretro.nativeSetPortAnalog(port, 1, Math.round(rx * 32767), Math.round(ry * 32767));
        return true;
    }

    private static float dead(float v, float dz) {
        if (Math.abs(v) < dz) return 0f;
        return Math.signum(v) * (Math.abs(v) - dz) / (1f - dz);
    }

    // ------------------------------------------------------------------ touches

    private ScreenFormat.Key formatKey;

    private ScreenFormat.Key formatKey() {
        if (formatKey == null) {
            formatKey = new ScreenFormat.Key(store, system, DISPLAY_KEYS, new ScreenFormat.Host() {
                public void applyFormat() { applyDisplay(); }

                public void say(String text) { toastUi(text); }

                public void refreshKeys() { if (deck != null) deck.refreshKeys(); }
            });
        }
        return formatKey;
    }

    private void press(String key, boolean down) {
        CodecMenu open = CodecMenu.find(stage);
        if (open != null) {
            // Menu ouvert : la manette le pilote, le jeu ne reçoit rien.
            if (down) open.key(key);
            return;
        }
        if (key.equals("HOME")) {
            if (down) homePressed();
            return;
        }
        if (key.equals("ASPECT")) {
            formatKey().press(down);
            return;
        }
        if (key.equals("FAST")) {
            if (down) {
                fast = !fast;
                toastUi(fast ? "Jeu accéléré ×2" : "Vitesse normale");
            }
            return;
        }
        if (key.equals("QSAVE") || key.equals("QLOAD")) {
            if (down) {
                final boolean save = key.equals("QSAVE");
                jobs.add(new Runnable() {
                    public void run() {
                        if (save) saveState();
                        else loadState();
                    }
                });
            }
            return;
        }
        if (key.equals("TOUCHPAD")) {
            padDown = down;
            if (Libretro.available()) Libretro.nativeSetPointer(padX, padY, down);
            return;
        }
        if (dpadAsStick() && dirIndex(key) >= 0) {
            dirAsStick(dirIndex(key), down);
            return;
        }
        int id = Libretro.retroId(key, system);
        if (id < 0) return;
        if (down) buttons |= 1 << id;
        else buttons &= ~(1 << id);
        if (Libretro.available()) Libretro.nativeSetButtons(buttons);
    }

    /** Joysticks → axes analogiques (-32767..32767) ; pavé tactile → pointeur absolu. */
    private void axis(String key, float x, float y) {
        if (!Libretro.available()) return;
        if (key.equals("TOUCHPAD")) {
            padX = Math.round((x * 2 - 1) * 32767);
            padY = Math.round((y * 2 - 1) * 32767);
            Libretro.nativeSetPointer(padX, padY, padDown);
            return;
        }
        int stick = key.equals("STICK_R") ? 1 : 0;
        Libretro.nativeSetAnalog(stick, Math.round(x * 32767), Math.round(y * 32767));
        // Consoles à stick analogique (N64, PSP, Dreamcast) : le joystick reste le stick, il n'envoie
        // pas la croix en plus (sur Midnight Club, la croix tourne la caméra pendant qu'on roule).
        if (stick == 0 && !hasDpad() && !analogSystem()) stickAsDpad(x, y);
    }

    /**
     * N64 sans joystick à l'écran (disposition Classique) : la croix pilote le stick analogique,
     * dont presque tous les jeux ont besoin, plutôt que la croix de la manette N64.
     */
    private boolean analogSystem() {
        return " n64 psp dc ".contains(" " + system + " ");
    }

    private boolean dpadAsStick() {
        if (!"n64".equals(system)) return false;
        ControlLayout l = deck.layout();
        if (l == null) return false;
        for (ControlLayout.Control c : l.controls) if (c.type.equals("STICK_L")) return false;
        return true;
    }

    private final boolean[] dirHeld = new boolean[4];

    private static int dirIndex(String key) {
        for (int i = 0; i < 4; i++) if (DIRS[i].equals(key)) return i;
        return -1;
    }

    private void dirAsStick(int dir, boolean down) {
        dirHeld[dir] = down;
        float x = (dirHeld[1] ? 1 : 0) - (dirHeld[3] ? 1 : 0);
        float y = (dirHeld[2] ? 1 : 0) - (dirHeld[0] ? 1 : 0);
        if (x != 0 && y != 0) {
            x *= 0.7071f;
            y *= 0.7071f;
        }
        if (Libretro.available()) Libretro.nativeSetAnalog(0, Math.round(x * 32767), Math.round(y * 32767));
    }

    /** Sans croix à l'écran (disposition Flex), le joystick gauche envoie aussi ses directions. */
    private final boolean[] stickDirs = new boolean[4];
    private static final String[] DIRS = {"UP", "RIGHT", "DOWN", "LEFT"};

    private void stickAsDpad(float x, float y) {
        boolean[] now = {y < -0.45f, x > 0.45f, y > 0.45f, x < -0.45f};
        for (int i = 0; i < 4; i++) {
            if (now[i] != stickDirs[i]) {
                stickDirs[i] = now[i];
                press(DIRS[i], now[i]);
            }
        }
    }

    private boolean hasDpad() {
        ControlLayout l = deck.layout();
        if (l == null) return true;
        for (ControlLayout.Control c : l.controls) if (c.type.equals("DPAD")) return true;
        return false;
    }

    private void homePressed() {
        if (pendingHome != null) {
            stage.removeCallbacks(pendingHome);
            pendingHome = null;
            finish();
            return;
        }
        pendingHome = new Runnable() {
            public void run() {
                pendingHome = null;
                showOptions();
            }
        };
        stage.postDelayed(pendingHome, 350);
    }

    /** Manette physique (Bluetooth ou USB) : mêmes touches que la manette à l'écran. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        android.view.InputDevice dev = e.getDevice();
        if (Pads.isPad(dev) && e.getKeyCode() != KeyEvent.KEYCODE_BACK) {
            Pads.Profile p = profile(dev);
            String k = Pads.key(p, e.getKeyCode());
            if (k == null) return super.dispatchKeyEvent(e);
            if (e.getRepeatCount() > 0) return true;
            playerPress(p.player, k, e.getAction() == KeyEvent.ACTION_DOWN);
            return true;
        }
        String key = physicalKey(e.getKeyCode());
        if (key == null) return super.dispatchKeyEvent(e);
        if (e.getRepeatCount() > 0) return true;
        if (key.equals("BACK")) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                CodecMenu open = CodecMenu.find(stage);
                if (open != null) open.key("B");
                else if (editBar != null) endDeckEdit(false);
                else showOptions();
            }
            return true;
        }
        press(key, e.getAction() == KeyEvent.ACTION_DOWN);
        return true;
    }

    private static String physicalKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "L";
            case KeyEvent.KEYCODE_BUTTON_R1: return "R";
            case KeyEvent.KEYCODE_BUTTON_L2: return "L2";
            case KeyEvent.KEYCODE_BUTTON_R2: return "R2";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "SELECT";
            case KeyEvent.KEYCODE_BUTTON_START: return "START";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "HOME";
            case KeyEvent.KEYCODE_DPAD_UP: return "UP";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "DOWN";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "RIGHT";
            case KeyEvent.KEYCODE_BACK: return "BACK";
            default: return null;
        }
    }

    // ------------------------------------------------------------------ menu maison

    /** Une fenêtre de menu en jeu : le jeu reste en pause tant qu'une fenêtre est ouverte. */
    private void menu(AlertDialog.Builder b) {
        dialogOpen = true;
        paused = true;
        b.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                // Une sous-fenêtre a pris le relais : on reste en pause.
                if (chaining) {
                    chaining = false;
                    return;
                }
                if (CodecMenu.find(stage) != null) return;
                dialogOpen = false;
                paused = false;
                hideBars();
            }
        });
        Theme.show(b);
    }

    private boolean chaining;

    private AlertDialog.Builder dialog(String t) {
        return new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle(t);
    }

    /** Un menu codec dans la moitié haute : le jeu est en pause tant qu'il est ouvert. */
    private CodecMenu codec(String t, String sub) {
        dialogOpen = true;
        paused = true;
        return new CodecMenu(this).title(t, sub).listener(new CodecMenu.Listener() {
            public void onClosed() {
                dialogOpen = false;
                paused = editBar != null;
                hideBars();
            }
        });
    }

    private void showOptions() {
        if (isFinishing()) return;
        if (CodecMenu.find(stage) != null) return;
        CodecMenu pause = codec(title, "PAUSE · " + core.toUpperCase())
                .item("Reprendre", null, null);
        if (CoreProfiles.matters(this, core)) {
            pause.choice("Profil", CoreProfiles.IDS, CoreProfiles.NAMES,
                    CoreProfiles.HELP, CoreProfiles.index(store.gameProfile(Store.gameId(uri, path))), new CodecMenu.Pick() {
                        public void picked(int i) { applyProfile(CoreProfiles.IDS[i]); }
                    });
        }
        pause
                .item("Sauvegarde rapide", "Remplace l'état sauvegardé de ce jeu", new Runnable() {
                    public void run() { jobs.add(new Runnable() { public void run() { saveState(); } }); }
                })
                .item("Chargement rapide", "Revient au dernier état sauvegardé", new Runnable() {
                    public void run() { jobs.add(new Runnable() { public void run() { loadState(); } }); }
                })
                .sub("Réglages de l'émulateur", CoreCatalog.name(core) + " · affichage, image, performances, son…",
                        new Runnable() { public void run() { showCoreOptions(); } })
                .sub("Options contrôleur", "Manette tactile et manettes physiques", new Runnable() {
                    public void run() { showControllerOptions(); }
                })
                .sub("Sauvegardes", "Importer ou exporter, vers une autre app ou un dossier", new Runnable() {
                    public void run() { showSaves(); }
                })
                .sub("Codes de triche", null, new Runnable() { public void run() { showCheats(); } })
                .check("ATH de monitoring", "RTSS, à régler dans Réglages > Affichage", store.hud(),
                        new Runnable() {
                            public void run() {
                                store.setHud(!store.hud());
                                applyHud();
                                showOptions2();
                            }
                        })
                .item("Redémarrer le jeu", null, new Runnable() {
                    public void run() { jobs.add(new Runnable() { public void run() { Libretro.nativeReset(); } }); }
                })
                .item("Quitter", "Retour à l'accueil, sauvegarde écrite", new Runnable() {
                    public void run() { finish(); }
                })
                .show(stage);
    }

    // ------------------------------------------------------------------ options contrôleur

    private static final int[] OPACITIES = {10, 20, 35, 50, 70};

    private void showControllerOptions() {
        final Runnable back = new Runnable() { public void run() { showControllerOptions(); } };
        CodecMenu m = codec("Options contrôleur", title).onBack(new Runnable() { public void run() { showOptions(); } });
        m.sub("Manette", store.activeLayout() + " · Flex, 2D, 3D ou les tiennes", new Runnable() {
            public void run() {
                CodecMenu p = codec("Manette", null).onBack(back);
                String cur = store.activeLayout();
                for (final ControlLayout l : store.layouts()) {
                    // Mini : en construction, visible mais pas sélectionnable (0.60.3).
                    if (l.floating()) {
                        p.soon(null, l.name, "👷‍♂️🚧");
                        continue;
                    }
                    p.radio(l.name, l.kind.equals("flex") && !l.split ? "La manette de la console du jeu" : null, l.name.equals(cur), new Runnable() {
                        public void run() {
                            store.setActiveLayout(l.name);
                            deck.reloadFromSource();
                            updateOverlay();
                        }
                    });
                }
                p.show(stage);
            }
        });
        if (screenMode != 2) {
            m.item("Modifier la manette ici", "Sur le jeu, à l'échelle de cet écran", new Runnable() {
                public void run() {
                    deck.post(new Runnable() { public void run() { startDeckEdit(); } });
                }
            });
        }
        if (store.screenLayout(store.activeLayout(), deck.columns(), deck.rows()) != null) {
            m.item("Revenir à la disposition commune", "Oublie les retouches faites sur cet écran", new Runnable() {
                public void run() {
                    store.setScreenLayout(deck.columns(), deck.rows(), null);
                    deck.reloadFromSource();
                    updateOverlay();
                }
            });
        }
        m.item("Éditeur complet", "Dispositions, couleurs, fond, vibrations", new Runnable() {
            public void run() {
                editedLayout = true;
                startActivity(new Intent(GameActivity.this, LayoutEditorActivity.class));
            }
        });
        // En paysage, la manette est toujours superposée au jeu en plein écran (0.42.3).
        m.sub("Transparence en superposition", "Opacité " + store.overlayOpacity() + " %", new Runnable() {
            public void run() {
                CodecMenu r = codec("Transparence", "MANETTE SUPERPOSÉE").onBack(back);
                for (final int v : OPACITIES) {
                    r.radio(v + " %", v == 10 ? "Presque invisible" : v == 35 ? "Par défaut" : v == 70 ? "Bien visible" : null,
                            store.overlayOpacity() == v, new Runnable() {
                                public void run() { store.setOverlayOpacity(v); updateOverlay(); }
                            });
                }
                r.show(stage);
            }
        });
        m.check("Plein écran avec une manette", "Masquée quand une manette est branchée",
                store.padFullscreen(), new Runnable() {
                    public void run() { store.setPadFullscreen(!store.padFullscreen()); updateOverlay(); showControllerOptions(); }
                });
        m.check("Boutons rapides avec une manette", "SAVE, LOAD et ×2 restent à l'écran", store.padQuickButtons(),
                new Runnable() {
                    public void run() { store.setPadQuickButtons(!store.padQuickButtons()); updateOverlay(); showControllerOptions(); }
                });
        for (final android.view.InputDevice d : pads.connected()) {
            final Pads.Profile p = profile(d);
            m.sub(p.name, PadView.familyLabel(PadView.family(d)) + " · joueur " + p.player
                    + (p.nintendo ? " · Nintendo" : ""), new Runnable() {
                public void run() { showPadOptions(d, p); }
            });
        }
        m.show(stage);
    }

    private void showPadOptions(final android.view.InputDevice d, final Pads.Profile p) {
        final Runnable back = new Runnable() { public void run() { showPadOptions(d, p); } };
        CodecMenu m = codec(p.name, "MANETTE · JOUEUR " + p.player)
                .onBack(new Runnable() { public void run() { showControllerOptions(); } });
        m.sub("Joueur", "Joueur " + p.player, new Runnable() {
            public void run() {
                CodecMenu r = codec("Joueur", p.name).onBack(back);
                for (int i = 1; i <= 4; i++) {
                    final int n = i;
                    r.radio("Joueur " + i, null, p.player == i, new Runnable() {
                        public void run() { p.player = n; pads.save(p); }
                    });
                }
                r.show(stage);
            }
        });
        m.check("Disposition Nintendo", "A à droite, B en bas", p.nintendo, new Runnable() {
            public void run() { p.nintendo = !p.nintendo; pads.save(p); showPadOptions(d, p); }
        });
        m.item("Voir la manette et réattribuer les touches", "Ouvre Réglages > Manettes (jeu en pause)",
                new Runnable() {
                    public void run() {
                        startActivity(new Intent(GameActivity.this, SettingsActivity.class)
                                .putExtra(SettingsActivity.EXTRA_SECTION, "pads"));
                    }
                });
        m.show(stage);
    }

    // ------------------------------------------------------------------ format d'affichage

    private static final String[] DISPLAY_KEYS = {"core", "4:3", "16:9", "stretch", "integer"};
    private static final String[] DISPLAY_LABELS = {"Format d'origine", "4:3", "16:9",
            "Étirer", "Pixels entiers"};
    private static final String[] DISPLAY_DETAILS = {"Le rapport prévu par la console", null, null,
            "Remplit toute la moitié haute", "Multiple exact de la résolution, sans flou"};

    private static String displayLabel(String key) {
        for (int i = 0; i < DISPLAY_KEYS.length; i++) if (DISPLAY_KEYS[i].equals(key)) return DISPLAY_LABELS[i];
        return key;
    }

    private void showDisplay() {
        String mode = store.displayMode(system);
        CodecMenu m = codec("Format d'affichage", system.toUpperCase()).onBack(new Runnable() {
            public void run() { showCoreOptions(); }
        });
        for (int i = 0; i < DISPLAY_KEYS.length; i++) {
            final String key = DISPLAY_KEYS[i];
            m.radio(DISPLAY_LABELS[i], DISPLAY_DETAILS[i], key.equals(mode), new Runnable() {
                public void run() {
                    store.setDisplayMode(system, key);
                    applyDisplay();
                }
            });
        }
        m.check("Image lissée", "Décoché : pixels nets", smooth, new Runnable() {
            public void run() {
                smooth = !smooth;
                store.setSmooth(system, smooth);
                applyDisplay();
                showDisplay();
            }
        });
        m.show(stage);
    }

    /** Fige les valeurs du profil en cours dans les options du cœur, puis passe en Personnalisé. */
    private void toCustom() {
        String gid = Store.gameId(uri, path);
        String cur = store.gameProfile(gid);
        if (CoreProfiles.CUSTOM.equals(cur)) return;
        java.util.Map<String, String> vals = new java.util.HashMap<String, String>();
        if (!cur.equals("performance")) vals.putAll(store.coreProfile(core));
        vals.putAll(CoreProfiles.resolve(this, core, cur, store.coreSpecs(core)));
        for (java.util.Map.Entry<String, String> o : vals.entrySet()) {
            if (CoreProfiles.keys(this, core).contains(o.getKey())) store.setCoreOption(core, o.getKey(), o.getValue());
        }
        store.setGameProfile(gid, CoreProfiles.CUSTOM);
    }

    /**
     * Nouveau profil en cours de partie : il remplace les retouches faites à la main sur ses
     * réglages et s'applique tout de suite ; certains cœurs ne relisent la définition qu'au
     * redémarrage du jeu.
     */
    private void applyProfile(final String id) {
        if (CoreProfiles.CUSTOM.equals(id)) {
            toCustom();
            return;
        }
        store.setGameProfile(Store.gameId(uri, path), id);
        store.clearCoreOptions(core, CoreProfiles.keys(this, core));
        final java.util.Map<String, String> vals = CoreProfiles.resolve(this, core, id, store.coreSpecs(core));
        if (!id.equals("performance")) {
            for (java.util.Map.Entry<String, String> o : store.coreProfile(core).entrySet()) {
                if (!vals.containsKey(o.getKey()) && CoreProfiles.keys(this, core).contains(o.getKey())) {
                    vals.put(o.getKey(), o.getValue());
                }
            }
        }
        jobs.add(new Runnable() {
            public void run() {
                for (java.util.Map.Entry<String, String> o : vals.entrySet()) {
                    Libretro.nativeSetVariable(o.getKey(), o.getValue());
                }
            }
        });
        I18n.toast(this, "Profil " + CoreProfiles.name(id)
                + " · certains réglages demandent de redémarrer le jeu", android.widget.Toast.LENGTH_SHORT).show();
    }

    /** Retour au menu principal depuis un sous-menu (remplace le sous-menu). */
    private void showOptions2() {
        CodecMenu cur = CodecMenu.find(stage);
        if (cur != null) stage.removeView(cur);
        showOptions();
    }

    // ------------------------------------------------------------------ options du cœur

    private void showCoreOptions() {
        // La liste se lit sur le fil d'émulation, puis s'affiche ici.
        jobs.add(new Runnable() {
            public void run() {
                final String[] vars = Libretro.nativeVariables();
                store.setCoreSpecs(core, vars);
                runOnUiThread(new Runnable() {
                    public void run() {
                        coreOptionsMenu(vars);
                    }
                });
            }
        });
    }

    /** Réglages de l'émulateur : affichage, puis les options du cœur rangées par thème. */
    private void coreOptionsMenu(final String[] vars) {
        final int n = vars.length / 3;
        CodecMenu m = codec("Réglages de l'émulateur", CoreCatalog.name(core).toUpperCase() + " · " + n + " RÉGLAGES")
                .onBack(new Runnable() {
                    public void run() { showOptions2(); }
                });
        m.sub("Format d'affichage", displayLabel(store.displayMode(system)) + (smooth ? " · lissé" : " · pixels nets"),
                new Runnable() { public void run() { showDisplay(); } });
        if (n == 0) m.item("Ce cœur ne propose pas d'autres réglages", null, null);
        for (final java.util.Map.Entry<String, java.util.List<Integer>> g : CoreOptionGroups.split(vars).entrySet()) {
            m.sub(g.getKey() + " (" + g.getValue().size() + ")", CoreOptionGroups.detail(g.getKey()), new Runnable() {
                public void run() { optionGroupMenu(vars, g.getKey(), g.getValue()); }
            });
        }
        m.item("Tout remettre par défaut", "Au prochain lancement du jeu", new Runnable() {
            public void run() {
                store.clearCoreOptions(core);
                toastUi("Réglages effacés.");
            }
        });
        m.show(stage);
    }

    private void optionGroupMenu(final String[] vars, final String group, final java.util.List<Integer> idx) {
        CodecMenu m = codec(group, CoreCatalog.name(core).toUpperCase()).onBack(new Runnable() {
            public void run() { coreOptionsMenu(vars); }
        });
        for (final int i : idx) {
            m.sub(optionLabel(vars[i * 3 + 1], vars[i * 3]), vars[i * 3 + 2], new Runnable() {
                public void run() { chooseValue(vars, i, group, idx); }
            });
        }
        m.show(stage);
    }

    private void chooseValue(final String[] vars, final int i, final String group, final java.util.List<Integer> idx) {
        final String key = vars[i * 3];
        String spec = vars[i * 3 + 1];
        int semi = spec.indexOf(';');
        final String[] values = (semi >= 0 ? spec.substring(semi + 1) : spec).trim().split("\\|");
        CodecMenu m = codec(optionLabel(spec, key), group.toUpperCase()).onBack(new Runnable() {
            public void run() { optionGroupMenu(vars, group, idx); }
        });
        for (final String v : values) {
            m.radio(v, null, v.equals(vars[i * 3 + 2]), new Runnable() {
                public void run() {
                    toCustom();
                    store.setCoreOption(core, key, v);
                    vars[i * 3 + 2] = v;
                    jobs.add(new Runnable() {
                        public void run() {
                            Libretro.nativeSetVariable(key, v);
                        }
                    });
                    toastUi("Appliqué. Effet au redémarrage du jeu.");
                    optionGroupMenu(vars, group, idx);
                }
            });
        }
        m.show(stage);
    }

    private static String optionLabel(String spec, String key) {
        int semi = spec.indexOf(';');
        return semi > 0 ? spec.substring(0, semi).trim() : key;
    }

    // ------------------------------------------------------------------ codes de triche

    private static final class Cheat {
        String name;
        String code;
        boolean on;
    }

    private java.util.List<Cheat> readCheats() {
        java.util.List<Cheat> out = new java.util.ArrayList<Cheat>();
        File f = saveFile("cheats.json");
        if (!f.isFile()) return out;
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                org.json.JSONArray arr = new org.json.JSONArray(new String(readAll(in), "UTF-8"));
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    Cheat c = new Cheat();
                    c.name = o.optString("name");
                    c.code = o.optString("code");
                    c.on = o.optBoolean("on");
                    out.add(c);
                }
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
            // Fichier abîmé : aucune triche.
        }
        return out;
    }

    private void writeCheats(java.util.List<Cheat> cheats) {
        org.json.JSONArray arr = new org.json.JSONArray();
        try {
            for (Cheat c : cheats) {
                arr.put(new org.json.JSONObject().put("name", c.name).put("code", c.code).put("on", c.on));
            }
            writeFile(saveFile("cheats.json"), arr.toString().getBytes("UTF-8"));
        } catch (Exception ignored) {
            // rien
        }
    }

    /** Envoie au cœur les codes cochés (à appeler sur le fil d'émulation). */
    private void applyCheats() {
        java.util.List<Cheat> cheats = readCheats();
        if (!Libretro.nativeCheatReset()) return;
        for (int i = 0; i < cheats.size(); i++) {
            Cheat c = cheats.get(i);
            // Plusieurs lignes = un seul code en plusieurs parties, séparées par « + » comme dans RetroArch.
            String code = c.code.trim().replaceAll("[\\s]*\\n[\\s]*", "+");
            Libretro.nativeCheatSet(i, c.on, code);
        }
    }

    private void showCheats() {
        final java.util.List<Cheat> cheats = readCheats();
        CodecMenu m = codec("Codes de triche", cheats.isEmpty() ? "AUCUN CODE" : cheats.size() + " CODE(S)")
                .onBack(new Runnable() { public void run() { showOptions2(); } });
        for (int i = 0; i < cheats.size(); i++) {
            final Cheat c = cheats.get(i);
            m.check(c.name, c.code.replace('\n', ' '), c.on, new Runnable() {
                public void run() {
                    c.on = !c.on;
                    writeCheats(cheats);
                    jobs.add(new Runnable() { public void run() { applyCheats(); } });
                    showCheats();
                }
            });
        }
        m.sub("＋ Ajouter un code", "Action Replay, GameShark, Game Genie ou CodeBreaker", new Runnable() {
            public void run() { addCheat(cheats); }
        });
        if (!cheats.isEmpty()) {
            m.sub("Supprimer un code", null, new Runnable() { public void run() { deleteCheat(cheats); } });
        }
        m.show(stage);
    }

    private void addCheat(final java.util.List<Cheat> cheats) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final android.widget.EditText nameField = new android.widget.EditText(this);
        nameField.setHint("Nom (ex. : Argent infini)");
        nameField.setTextColor(Theme.TEXT);
        box.addView(nameField);
        final android.widget.EditText codeField = new android.widget.EditText(this);
        codeField.setHint("Code (une partie par ligne)");
        codeField.setTextColor(Theme.TEXT);
        codeField.setTypeface(android.graphics.Typeface.MONOSPACE);
        codeField.setMinLines(3);
        codeField.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.addView(codeField);
        menu(dialog("Nouveau code").setView(box)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Activer", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String code = codeField.getText().toString().trim();
                        if (code.isEmpty()) return;
                        Cheat c = new Cheat();
                        String nm = nameField.getText().toString().trim();
                        c.name = nm.isEmpty() ? "Code " + (cheats.size() + 1) : nm;
                        c.code = code;
                        c.on = true;
                        cheats.add(c);
                        writeCheats(cheats);
                        jobs.add(new Runnable() { public void run() { applyCheats(); } });
                        toastUi("Code « " + c.name + " » activé.");
                        showCheats();
                    }
                }));
    }

    private void deleteCheat(final java.util.List<Cheat> cheats) {
        CodecMenu m = codec("Supprimer un code", null).onBack(new Runnable() { public void run() { showCheats(); } });
        for (int i = 0; i < cheats.size(); i++) {
            final int idx = i;
            m.item(cheats.get(i).name, cheats.get(i).code.replace('\n', ' '), new Runnable() {
                public void run() {
                    cheats.remove(idx);
                    writeCheats(cheats);
                    jobs.add(new Runnable() { public void run() { applyCheats(); } });
                    showCheats();
                }
            });
        }
        m.show(stage);
    }

    // ------------------------------------------------------------------ fil d'émulation

    private void emulate() {
        boolean opened = false;
        AudioTrack track = null;
        try {
            if ("armsx2".equals(core) && !cores.hasPs2Bios()) {
                fail("ARMSX2 a besoin du BIOS de ta PS2. Importe-le dans Réglages > Émulateurs > "
                        + "Importer un BIOS (.bin de 4 Mo).");
                return;
            }
            if (cores.installed(core) && cores.needsSystemFiles(core)) {
                ui(CoreManager.bundled(core) ? "Installation des ressources de " + core + "…"
                        : "Téléchargement des fichiers de " + core + "…");
                try {
                    cores.systemFiles(core, null);
                } catch (IOException e) {
                    // Le jeu démarre quand même (sans les polices système, pour la PSP).
                }
            }
            if (!cores.installed(core)) {
                ui("Téléchargement du cœur " + core + "…");
                cores.download(core, new CoreManager.Progress() {
                    long last;

                    public void onProgress(long done, long total) {
                        if (done - last < 128 * 1024 && done != total) return;
                        last = done;
                        ui("Téléchargement du cœur " + core + " · "
                                + (total > 0 ? (done * 100 / total) + " %" : (done / 1024) + " Ko"));
                    }
                });
                // Script d'installation : vérification, fichiers d'appoint, réglages adaptés à l'appareil.
                CoreInstaller.run(this, core, new CoreInstaller.Log() {
                    public void step(String line) { ui("Installation de " + CoreCatalog.name(core) + " · " + line); }
                });
            } else if (store.coreInstallNote(core) == null) {
                try {
                    CoreInstaller.run(this, core, new CoreInstaller.Log() {
                        public void step(String line) { ui("Optimisation de " + CoreCatalog.name(core) + " · " + line); }
                    });
                } catch (IOException e) {
                    // Cœur déjà là mais script en échec : il tourne avec ses réglages d'origine.
                }
            }
            File sys = dir("system");
            File saves = dir("saves");
            Libretro.nativeSetDebug(store.debug());
            if (!Libretro.nativeOpen(cores.file(core).getPath(), sys.getPath(), saves.getPath())) {
                fail("Le cœur " + core + " ne se charge pas : " + Libretro.nativeLastError());
                return;
            }
            opened = true;
            // Options par défaut de Origami, puis celles choisies pour ce cœur, avant le chargement du jeu.
            for (String[] d : CoreManager.DEFAULT_OPTIONS) {
                if (d[0].equals(core)) Libretro.nativeSetVariable(d[1], d[2]);
            }
            // Profil d'émulation du jeu : Performance garde les réglages d'origine ; Équilibré part
            // du profil du script d'installation (selon la puce) ; puis les valeurs du profil, et
            // les choix de l'utilisateur par-dessus.
            String prof = store.gameProfile(Store.gameId(uri, path));
            if (!prof.equals("performance")) {
                for (java.util.Map.Entry<String, String> o : store.coreProfile(core).entrySet()) {
                    Libretro.nativeSetVariable(o.getKey(), o.getValue());
                }
            }
            for (java.util.Map.Entry<String, String> o
                    : CoreProfiles.resolve(this, core, prof, Libretro.nativeVariables()).entrySet()) {
                Libretro.nativeSetVariable(o.getKey(), o.getValue());
            }
            for (java.util.Map.Entry<String, String> o : store.coreOptions(core).entrySet()) {
                Libretro.nativeSetVariable(o.getKey(), o.getValue());
            }
            // Gardée pour la page du cœur, qui règle ses options hors jeu.
            store.setCoreSpecs(core, Libretro.nativeVariables());
            String[] info = Libretro.nativeSystemInfo();
            Set<String> exts = new HashSet<String>(Arrays.asList(info[2].toLowerCase(Locale.ROOT).split("\\|")));
            boolean needFull = "1".equals(info[3]);
            ui("Démarrage de « " + title + " » · " + info[0] + " " + info[1]);

            Content content = prepare(exts, needFull);
            if (content == null) return;
            if (!Libretro.nativeLoadGame(content.path, content.data)) {
                fail(info[0] + " n'a pas pu ouvrir ce jeu : " + Libretro.nativeLastError());
                return;
            }
            loadSaveRam();
            applyCheats();

            double[] av = Libretro.nativeAvInfo();
            double fps = av[0] > 0 ? av[0] : 60;
            targetFps = fps;
            int rate = (int) Math.round(av[1] > 0 ? av[1] : 44100);
            audioRate = rate;
            int maxW = Math.max(64, (int) av[3]);
            int maxH = Math.max(64, (int) av[4]);
            // Cœurs 3D : leur maximum peut viser une très haute définition ; le tampon s'agrandit au
            // besoin (nativeRun renvoie 2), inutile de réserver d'emblée des dizaines de Mo.
            if ((long) maxW * maxH > 2048L * 2048L) {
                maxW = 2048;
                maxH = 2048;
            }
            ByteBuffer frame = ByteBuffer.allocateDirect(maxW * maxH * 4).order(ByteOrder.nativeOrder());
            Libretro.nativeSetFrameBuffer(frame);
            track = audioTrack(rate);
            short[] audio = new short[rate * 2];
            Bitmap bitmap = null;
            Rect dst = new Rect();
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            ui(null);

            long frameNs = (long) (1e9 / fps);
            selfPaced = "armsx2".equals(core);
            long next = System.nanoTime();
            long lastSave = System.currentTimeMillis();
            while (running) {
                Runnable job;
                while ((job = jobs.poll()) != null) job.run();
                if (paused) {
                    if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) track.pause();
                    sleep(16);
                    next = System.nanoTime();
                    lastShownNs = 0;
                    continue;
                }
                if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) track.play();

                // Accéléré ×2, son coupé. Cœur classique : deux images calculées par tour, une affichée.
                // Cœur à fil propre (ARMSX2) : il accélère lui-même (GET_FASTFORWARDING), on l'appelle
                // simplement deux fois plus souvent pour récupérer ses images.
                boolean speed = fast;
                if (speed != ffSent) {
                    ffSent = speed;
                    Libretro.nativeSetFastForward(speed);
                }
                int r = 0;
                for (int k = 0; k < (speed && !selfPaced ? 2 : 1); k++) {
                    int rr = Libretro.nativeRun();
                    if (rr == 2) {
                        int w = Libretro.nativeFrameWidth();
                        int h = Libretro.nativeFrameHeight();
                        frame = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
                        Libretro.nativeSetFrameBuffer(frame);
                    } else if (rr == 1) {
                        r = 1;
                    }
                }
                framesRun += speed && !selfPaced ? 2 : 1;
                if (r == 1) {
                    framesShown++;
                    long t = System.nanoTime();
                    if (lastShownNs != 0 && t - lastShownNs > maxFrameNs) maxFrameNs = t - lastShownNs;
                    lastShownNs = t;
                    int w = Libretro.nativeFrameWidth();
                    int h = Libretro.nativeFrameHeight();
                    if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
                        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        resize(w, h, Libretro.nativeAspect());
                    }
                    frame.rewind();
                    bitmap.copyPixelsFromBuffer(frame);
                    draw(bitmap, dst, paint);
                }

                int n = Libretro.nativeDrainAudio(audio);
                if (n > 0) audioFrames += n / 2;
                if (n > 0 && !speed) {
                    // L'écriture bloque quand le tampon est plein : c'est elle qui cale la vitesse du jeu.
                    track.write(audio, 0, n);
                    next = System.nanoTime();
                } else {
                    next += speed && selfPaced ? frameNs / 2 : frameNs;
                    long wait = next - System.nanoTime();
                    if (wait > 0) sleep(wait / 1000000L);
                    else next = System.nanoTime();
                }

                if (System.currentTimeMillis() - lastSave > 30000) {
                    writeSaveRam();
                    lastSave = System.currentTimeMillis();
                }
            }
            writeSaveRam();
        } catch (IOException e) {
            fail("Impossible de préparer le jeu : " + e.getMessage());
        } catch (Throwable t) {
            fail("Erreur du moteur : " + t);
        } finally {
            if (track != null) track.release();
            if (opened) Libretro.nativeClose();
        }
    }

    private AudioTrack audioTrack(int rate) {
        int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int size = Math.max(min * 2, rate / 15 * 4);
        AudioTrack.Builder b = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build())
                .setBufferSizeInBytes(size)
                .setTransferMode(AudioTrack.MODE_STREAM);
        b.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
        AudioTrack t = b.build();
        t.play();
        return t;
    }

    private void draw(Bitmap bitmap, Rect dst, Paint paint) {
        synchronized (surfaceLock) {
            if (holder == null) return;
            Canvas c = null;
            try {
                c = holder.lockHardwareCanvas();
                if (c == null) return;
                dst.set(0, 0, c.getWidth(), c.getHeight());
                paint.setFilterBitmap(smooth);
                c.drawBitmap(bitmap, null, dst, paint);
            } catch (IllegalArgumentException e) {
                // Surface en cours de remplacement : on saute cette image.
            } finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c);
                    } catch (IllegalStateException | IllegalArgumentException ignored) {
                        // idem
                    }
                }
            }
        }
    }

    private void resize(final int w, final int h, final float a) {
        runOnUiThread(new Runnable() {
            public void run() {
                frameW = w;
                frameH = h;
                coreAspect = a > 0 ? a : (float) w / h;
                applyDisplay();
            }
        });
    }

    /**
     * Applique le format choisi. En image lissée, la surface garde la résolution du jeu et l'écran
     * l'agrandit ; en pixels nets, on agrandit nous-mêmes d'un multiple entier, sans filtrage.
     */
    private void applyDisplay() {
        if (frameW == 0 || frameH == 0) return;
        String mode = store.displayMode(system);
        if (mode.equals("4:3")) aspect = 4f / 3f;
        else if (mode.equals("16:9")) aspect = 16f / 9f;
        else aspect = coreAspect;
        fitScreen();
        int k = 1;
        if (!smooth) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) screen.getLayoutParams();
            int vw = lp.width > 0 ? lp.width : frameW;
            int vh = lp.height > 0 ? lp.height : frameH;
            k = Math.max(1, Math.min(6, (int) Math.ceil(Math.max((float) vw / frameW, (float) vh / frameH))));
        }
        screen.getHolder().setFixedSize(frameW * k, frameH * k);
    }

    private void fitScreen() {
        int sw = stage.getWidth() - stage.getPaddingLeft() - stage.getPaddingRight();
        int sh = stage.getHeight() - stage.getPaddingTop() - stage.getPaddingBottom();
        if (sw == 0 || sh == 0) return;
        String mode = store.displayMode(system);
        int w, h;
        if (mode.equals("stretch")) {
            w = sw;
            h = sh;
        } else if (mode.equals("integer") && frameW > 0 && frameH > 0) {
            // Le plus grand multiple entier qui tient, en respectant le rapport d'origine.
            float hpx = frameW / coreAspect;
            int k = Math.max(1, (int) Math.min(sw / (float) frameW, sh / hpx));
            w = frameW * k;
            h = Math.round(hpx * k);
        } else if ((float) sw / sh > aspect) {
            h = sh;
            w = Math.round(sh * aspect);
        } else {
            w = sw;
            h = Math.round(sw / aspect);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) screen.getLayoutParams();
        if (lp.width == w && lp.height == h) return;
        lp.width = w;
        lp.height = h;
        lp.gravity = Gravity.CENTER;
        screen.setLayoutParams(lp);
    }

    // ------------------------------------------------------------------ contenu du jeu

    private static final class Content {
        String path;
        byte[] data;
    }

    private Content prepare(Set<String> exts, boolean needFull) throws IOException {
        String ext = ext(name);
        Content c = new Content();
        File cache = new File(getCacheDir(), "rom");
        if (ext.equals("zip") && !exts.contains("zip")) {
            // Le cœur ne lit pas les .zip : on sort le jeu de l'archive.
            clear(cache);
            ZipInputStream zip = new ZipInputStream(open());
            try {
                ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    String entry = new File(e.getName()).getName();
                    if (e.isDirectory() || !exts.contains(ext(entry))) continue;
                    if (needFull) {
                        File out = new File(cache, entry);
                        copy(zip, out, -1);
                        c.path = out.getPath();
                    } else {
                        c.data = readAll(zip);
                        c.path = new File(cache, entry).getPath();
                    }
                    return c;
                }
            } finally {
                zip.close();
            }
            fail("Aucun fichier de jeu reconnu par " + core + " dans cette archive.");
            return null;
        }
        if (!needFull) {
            InputStream in = open();
            try {
                c.data = readAll(in);
            } finally {
                in.close();
            }
            c.path = path != null ? path : new File(cache, name).getPath();
            return c;
        }
        if (path != null && new File(path).canRead()) {
            c.path = path;
            return c;
        }
        if (SHEETS.contains(ext) || needsAllFiles()) {
            askAllFiles();
            return null;
        }
        // Le cœur veut un chemin : copie temporaire du jeu dans le cache de l'app.
        clear(cache);
        File out = new File(cache, name);
        InputStream in = open();
        try {
            copy(in, out, size());
        } finally {
            in.close();
        }
        c.path = out.getPath();
        return c;
    }

    /** Les gros disques ne sont pas recopiés : on demande plutôt l'accès direct aux fichiers. */
    private boolean needsAllFiles() {
        return size() > 1024L * 1024 * 1024;
    }

    private long size() {
        try {
            android.content.res.AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(Uri.parse(uri), "r");
            long n = fd.getLength();
            fd.close();
            return n;
        } catch (Exception e) {
            return -1;
        }
    }

    private InputStream open() throws IOException {
        InputStream in = getContentResolver().openInputStream(Uri.parse(uri));
        if (in == null) throw new IOException("fichier illisible");
        return in;
    }

    private void copy(InputStream in, File out, long total) throws IOException {
        out.getParentFile().mkdirs();
        OutputStream o = new FileOutputStream(out);
        try {
            byte[] buf = new byte[1024 * 1024];
            long done = 0, last = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                o.write(buf, 0, n);
                done += n;
                if (total > 0 && done - last > 8L * 1024 * 1024) {
                    last = done;
                    ui("Préparation du jeu · " + (done * 100 / total) + " %");
                }
            }
        } finally {
            o.close();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[256 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static void clear(File dir) {
        File[] all = dir.listFiles();
        if (all != null) for (File f : all) f.delete();
        dir.mkdirs();
    }

    private static String ext(String n) {
        int dot = n == null ? -1 : n.lastIndexOf('.');
        return dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private File dir(String sub) {
        File base = getExternalFilesDir(null);
        File d = new File(base != null ? base : getFilesDir(), sub);
        d.mkdirs();
        return d;
    }

    private void askAllFiles() {
        runOnUiThread(new Runnable() {
            public void run() {
                if (isFinishing()) return;
                final Runnable back = new Runnable() {
                    public void run() { finish(); }
                };
                new CodecMenu(GameActivity.this).title("Accès aux fichiers", title)
                        .message("Jeu multi-fichiers ou très gros : " + core
                                + " doit les lire directement. Autorise l'accès à tous les fichiers, "
                                + "puis relance le jeu.")
                        .item("Autoriser", null, new Runnable() {
                            public void run() {
                                try {
                                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:" + getPackageName())));
                                } catch (ActivityNotFoundException e) {
                                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + getPackageName())));
                                }
                                finish();
                            }
                        })
                        .item("Retour", null, back)
                        .onBack(back)
                        .show(stage);
            }
        });
    }

    // ------------------------------------------------------------------ sauvegardes

    private static final int REQ_SAVE = 21;

    FrameLayout menuHost() {
        return stage;
    }

    private void showSaves() {
        codec("Sauvegardes", title).onBack(new Runnable() { public void run() { showOptions(); } })
                .item("Importer une sauvegarde", SaveImport.formats(system), new Runnable() {
                    public void run() { pickSave(); }
                })
                .sub("Exporter la sauvegarde", "Partager ou enregistrer dans un dossier", new Runnable() {
                    public void run() {
                        // La sauvegarde en mémoire d'abord, sur le fil d'émulation, puis le menu d'export.
                        jobs.add(new Runnable() {
                            public void run() {
                                writeSaveRam();
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        SaveTransfer.export(GameActivity.this, system, fileBase, new Runnable() {
                                            public void run() { showSaves(); }
                                        });
                                    }
                                });
                            }
                        });
                    }
                })
                .show(stage);
    }

    private void pickSave() {
        if (java.util.Arrays.asList(SaveImport.SYSTEMS).indexOf(system) < 0) {
            toastUi("Import non géré pour cette console.");
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try {
            startActivityForResult(i, REQ_SAVE);
        } catch (ActivityNotFoundException e) {
            toastUi("Sélecteur de fichiers introuvable.");
        }
    }

    /** Lit le fichier choisi, puis l'installe sur le fil d'émulation pour ne pas croiser l'écriture du .srm. */
    private void importSave(final Uri uri) {
        new Thread(new Runnable() {
            public void run() {
                final String name = SettingsActivity.displayName(GameActivity.this, uri);
                final byte[] bytes;
                try {
                    InputStream in = getContentResolver().openInputStream(uri);
                    try {
                        bytes = readAll(in);
                    } finally {
                        in.close();
                    }
                } catch (IOException | SecurityException e) {
                    toastUi("Lecture impossible : " + e.getMessage());
                    return;
                }
                jobs.add(new Runnable() {
                    public void run() {
                        try {
                            // L'état en mémoire d'abord, pour qu'un ajout N64 complète le bon .srm.
                            writeSaveRam();
                            File base = getExternalFilesDir(null);
                            String msg = SaveImport.install(base != null ? base : getFilesDir(), system, name, bytes,
                                    fileBase);
                            if (SaveImport.perGame(system) && Libretro.nativeMemory(Libretro.MEMORY_SAVE_RAM) != null) {
                                loadSaveRam();
                                Libretro.nativeReset();
                                msg += " Le jeu redémarre.";
                            }
                            toastUi(msg);
                        } catch (IOException e) {
                            toastUi("Sauvegarde refusée : " + e.getMessage());
                        }
                    }
                });
            }
        }).start();
    }

    private File saveFile(String ext) {
        return new File(dir("saves"), fileBase + "." + ext);
    }

    private void loadSaveRam() {
        ByteBuffer mem = Libretro.nativeMemory(Libretro.MEMORY_SAVE_RAM);
        File f = saveFile("srm");
        if (mem == null || !f.isFile()) return;
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] data;
            try {
                data = readAll(in);
            } finally {
                in.close();
            }
            mem.rewind();
            mem.put(data, 0, Math.min(data.length, mem.capacity()));
        } catch (IOException ignored) {
            // Sauvegarde illisible : le jeu démarre sans.
        }
    }

    private void writeSaveRam() {
        ByteBuffer mem = Libretro.nativeMemory(Libretro.MEMORY_SAVE_RAM);
        if (mem == null) return;
        byte[] data = new byte[mem.capacity()];
        mem.rewind();
        mem.get(data);
        writeFile(saveFile("srm"), data);
    }

    private void saveState() {
        byte[] s = Libretro.nativeSaveState();
        if (s != null && writeFile(saveFile("state"), s)) toastUi("État sauvegardé.");
        else toastUi("Ce cœur ne sait pas sauvegarder l'état.");
    }

    private void loadState() {
        File f = saveFile("state");
        if (!f.isFile()) {
            toastUi("Aucun état sauvegardé pour ce jeu.");
            return;
        }
        try {
            FileInputStream in = new FileInputStream(f);
            byte[] data;
            try {
                data = readAll(in);
            } finally {
                in.close();
            }
            boolean ok = Libretro.nativeLoadState(data);
            toastUi(ok ? "État chargé." : "État incompatible avec ce cœur.");
        } catch (IOException e) {
            toastUi("État illisible.");
        }
    }

    private static boolean writeFile(File f, byte[] data) {
        File tmp = new File(f.getPath() + ".tmp");
        try {
            FileOutputStream o = new FileOutputStream(tmp);
            try {
                o.write(data);
            } finally {
                o.close();
            }
            return tmp.renameTo(f);
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (SaveTransfer.onResult(this, request, result, data)) return;
        if (request != REQ_SAVE || result != RESULT_OK || data == null || data.getData() == null) return;
        importSave(data.getData());
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        if (hinge != null) sensors.registerListener(hingeListener, hinge, android.hardware.SensorManager.SENSOR_DELAY_UI);
        profiles.clear();
        pads.input().registerInputDeviceListener(padListener, null);
        updateOverlay();
        applyHud();
        if (!dialogOpen) paused = false;
        // Au retour de l'éditeur, la manette suit la disposition active.
        if (editedLayout) {
            editedLayout = false;
            deck.reloadFromSource();
            updateOverlay();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (hinge != null) sensors.unregisterListener(hingeListener);
        pads.input().unregisterInputDeviceListener(padListener);
        hudTimer.removeCallbacks(hudTick);
        paused = true;
        jobs.add(new Runnable() {
            public void run() {
                writeSaveRam();
            }
        });
        buttons = 0;
        if (Libretro.available()) Libretro.nativeSetButtons(0);
    }

    @Override
    protected void onDestroy() {
        Theme.inGame = false;
        super.onDestroy();
        running = false;
        if (emu != null) {
            try {
                emu.join(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideBars();
    }

    private void hideBars() {
        Theme.fullscreen(this);
    }

    // ------------------------------------------------------------------ outils

    private void ui(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                overlay.setVisibility(msg == null ? View.GONE : View.VISIBLE);
                if (msg != null) overlay.setText(msg);
            }
        });
    }

    private void fail(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                if (isFinishing()) return;
                overlay.setVisibility(View.VISIBLE);
                overlay.setText(msg);
                final Runnable back = new Runnable() {
                    public void run() { finish(); }
                };
                new CodecMenu(GameActivity.this).title(title, "ERREUR").message(msg)
                        .item("Retour", null, back)
                        .onBack(back)
                        .show(stage);
            }
        });
    }

    private void toastUi(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                I18n.say(GameActivity.this, msg);
            }
        });
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(Math.max(1, ms));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
