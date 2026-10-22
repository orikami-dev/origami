package com.hideo.foldplay;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Jeu PS3 : le cœur ARMSX3 dessine en Vulkan directement dans la SurfaceView, Origami fournit la
 * manette (tactile ou physique), le menu Maison et l'import du firmware. Processus « :ps3 » : le
 * cœur (GPL-2.0-only) ne partage jamais un programme avec celui de la PS2 (GPL-3), et un plantage
 * du moteur ne ferme pas la bibliothèque.
 */
public class Ps3Activity extends Activity {
    static final String EXTRA_PATH = "path";
    static final String EXTRA_TITLE = "title";
    /** Ouvre directement le choix du firmware (depuis les réglages), sans jeu. */
    static final String EXTRA_FIRMWARE = "firmware";

    private static final int REQ_FIRMWARE = 31;

    private Store store;
    private Pads pads;
    private FrameLayout stage;
    private Andon andon;
    private SurfaceView screen;
    private ControlDeckView deck;
    private TextView status;
    private String path, title;
    private volatile boolean ready, booted, surfaceSeen;
    private android.view.Surface surface;
    private int d1, d2;
    private float lx, ly, rx, ry;
    private boolean menuOpen;
    private HudOverlay hud;
    private FramePeak peak;
    private Boolean lastFill;

    private static boolean USED;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Ce processus a déjà servi à un jeu (sortie avant la fin du chargement, par exemple) : le moteur
        // y est resté à moitié initialisé et ne lirait rien. On relance le jeu dans un processus neuf.
        if (USED) {
            startActivity(new android.content.Intent(this, MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_RESTART, new android.content.Intent(getIntent())));
            DebugLog.markClean(this);
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        }
        USED = true;
        Theme.inGame = true;
        I18n.attach(this);
        Theme.sync(this);
        Theme.fullscreen(this);
        DebugLog.sync(this);
        store = new Store(this);
        pads = new Pads(this);
        path = getIntent().getStringExtra(EXTRA_PATH);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null) title = "PlayStation 3";
        build();
        DebugLog.event("[PS3] onCreate : jeu « " + title + " », chemin " + path + ", profil "
                + EngineSettings.profile(this, EngineSettings.PS3));
        DebugLog.snapshot(this, "PS3 création");
        DebugLog.perfStart(this, "PS3");
        if (!Ps3Core.present(this)) {
            fail("Le moteur PS3 n'est pas dans cette version d'Origami.");
            return;
        }
        if (!Ps3Core.load(this)) {
            fail("Le moteur PS3 ne se charge pas : " + safeError());
            return;
        }
        status("Démarrage du moteur PS3…");
        new Thread(new Runnable() {
            public void run() {
                GpuDriver.apply(Ps3Activity.this, store);
                boolean ok = Ps3Core.nativeInitialize(Ps3Core.root(Ps3Activity.this), "00000001",
                        Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : Build.HARDWARE);
                ready = ok;
                DebugLog.event("[PS3] nativeInitialize ok=" + ok + (ok ? "" : ", erreur " + safeError()));
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (!ready) {
                            fail("Le moteur PS3 n'a pas pu démarrer.");
                            return;
                        }
                        // La fenêtre a pu arriver pendant l'initialisation (course) : on la donne maintenant.
                        android.view.SurfaceHolder sh = screen.getHolder();
                        if (!surfaceSeen && sh.getSurface() != null && sh.getSurface().isValid()) {
                            android.graphics.Rect r = sh.getSurfaceFrame();
                            giveSurface(sh, r.width(), r.height());
                        }
                        next();
                    }
                });
            }
        }, "ps3-init").start();
    }

    private static String safeError() {
        try {
            return Ps3Core.nativeLastError();
        } catch (Throwable t) {
            return t.getMessage();
        }
    }

    // ------------------------------------------------------------------ écran

    /** Remet la fenêtre au moteur (sa taille d'abord : il la lit dès qu'il a une fenêtre). */
    private void giveSurface(SurfaceHolder h, int w, int hh) {
        Ps3Core.nativeSurfaceSize(w, hh);
        int event = surface == null ? Ps3Core.SURFACE_CREATED : Ps3Core.SURFACE_CHANGED;
        surface = h.getSurface();
        Ps3Core.nativeSurface(surface, event);
        surfaceSeen = true;
        DebugLog.event("[PS3] surface reçue " + w + "×" + hh);
        maybeBoot();
    }

    private void build() {
        stage = new FrameLayout(this);
        stage.setBackgroundColor(0xFF000000);
        screen = new SurfaceView(this);
        screen.getHolder().addCallback(new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder h) {
            }

            public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) {
                if (!ready) {
                    DebugLog.event("[PS3] surface " + w + "×" + hh + " arrivée avant le moteur : prise en compte dès qu'il est prêt");
                    return;
                }
                giveSurface(h, w, hh);
            }

            public void surfaceDestroyed(SurfaceHolder h) {
                if (ready && surface != null) Ps3Core.nativeSurface(surface, Ps3Core.SURFACE_DESTROYED);
                surface = null;
            }
        });
        stage.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        andon = Andon.attach(stage, screen, store);

        deck = new ControlDeckView(this);
        deck.setSystem("ps3");
        deck.setSource(store);
        deck.setKeyListener(new ControlView.Listener() {
            public void onDown(String key) {
                press(key, true);
            }

            public void onUp(String key) {
                press(key, false);
            }

            public void onAxis(String key, float x, float y) {
                axis(key, x, y);
            }
        }, store.haptics());
        deck.setFollowScreen(true);
        stage.addView(deck, new FrameLayout.LayoutParams(-1, -1));

        status = new TextView(this);
        status.setTextColor(Theme.TEXT);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        int p = Math.round(16 * getResources().getDisplayMetrics().density);
        status.setPadding(p, p, p, p);
        stage.addView(status, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        setContentView(stage);
        stage.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (b - t != ob - ot || r - l != or - ol) place();
            }
        });
        formatKey();
        // ATH : le moteur donne la durée de sa dernière image.
        peak = new FramePeak(new FramePeak.Probe() {
            public long read() {
                return ready ? Ps3Core.nativeFramePeriodNs() : -1;
            }
        }, false);
        final FramePeak logPeak = new FramePeak(new FramePeak.Probe() {
            public long read() {
                return ready ? Ps3Core.nativeFramePeriodNs() : -1;
            }
        }, false);
        logPeak.start();
        startPrecompileWatch();
        final ShaderMeter shaders = new ShaderMeter(new java.io.File(Ps3Core.root(this)));
        HudOverlay.shaderMeter = new ShaderMeter(new java.io.File(Ps3Core.root(this))); // une mesure pour l'ATH, une pour le journal
        DebugLog.perfExtra = new DebugLog.Extra() {
            public String get() {
                StringBuilder b = new StringBuilder("jeu : ");
                float fps = logPeak.takeFps();
                b.append(fps > 0 ? Math.round(fps * 10) / 10f + " i/s" : "i/s ?");
                float ms = logPeak.takeMs();
                b.append(", image la plus lente ").append(ms > 0 ? Math.round(ms) + " ms" : "?");
                b.append(", images > 40 ms ").append(logPeak.takeSlow());
                try {
                    b.append(", moteur état ").append(Ps3Core.nativeState()).append(" titre ").append(Ps3Core.nativeTitleId());
                    b.append(", échelle ").append(Ps3Core.nativeSettingsGet("Video@@Resolution Scale").trim());
                } catch (Throwable t) {
                    b.append(", moteur illisible");
                }
                b.append(", cœurs ").append(store.coresMode()).append(", pilote ").append(store.gpuDriverEff());
                b.append(", compilation : code ").append(shaders.codePct()).append(" %, shaders ").append(shaders.shaderPct()).append(" %, cache ").append(shaders.cache());
                b.append(Ps3Audio.describe(Ps3Activity.this));
                return b.toString();
            }
        };
        hud = new HudOverlay(this, stage, store, "armsx3", new HudOverlay.Source() {
            public float fps(float seconds) {
                if (!ready) return -1;
                peak.start();
                float avg = peak.takeFps();
                if (avg > 0) return avg;
                try {
                    long ns = Ps3Core.nativeFramePeriodNs();
                    return ns > 0 ? 1e9f / ns : -1;
                } catch (Throwable t) {
                    return -1;
                }
            }

            public float frameMaxMs() {
                peak.start();
                return peak.takeMs();
            }

            public boolean paused() {
                return menuOpen;
            }

            public String renderRes() {
                // Définition réellement appliquée par le moteur : 720p × l'échelle choisie (Demon's Souls plafonné).
                try {
                    int pct = Integer.parseInt(Ps3Core.nativeSettingsGet("Video@@Resolution Scale").trim());
                    return Math.round(1280 * pct / 100f) + "×" + Math.round(720 * pct / 100f);
                } catch (Throwable e) {
                    return null;
                }
            }
        });
        hud.setScreen(screen);
        hud.apply();
        place();
    }

    /**
     * Portrait : le jeu en haut, la manette dans la moitié basse. Paysage : jeu en plein écran,
     * manette transparente par-dessus (règle de BOSS, comme les autres consoles).
     */
    private void place() {
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean pad = pads != null && pads.any() && store.padFullscreen();
        FrameLayout.LayoutParams sl = (FrameLayout.LayoutParams) screen.getLayoutParams();
        FrameLayout.LayoutParams dl = (FrameLayout.LayoutParams) deck.getLayoutParams();
        int h = getResources().getDisplayMetrics().heightPixels;
        int strip = 0;
        if (land || pad) {
            GameLayout.full(sl, dl);
            deck.setOverlay(true, store.overlayOpacity());
        } else {
            strip = GameLayout.portrait(this, stage.getHeight(), sl, dl);
            deck.setOverlay(false, store.overlayOpacity());
        }
        if (hud != null) {
            hud.setStrip(strip);
            hud.apply();
        }
        deck.setVisibility(pad ? View.GONE : View.VISIBLE);
        screen.setLayoutParams(sl);
        deck.setLayoutParams(dl);
        // Messages du moteur (chargement, compilation) : au milieu de l'écran du haut en portrait,
        // au milieu de l'écran en paysage (demande BOSS, 0.62).
        if (status != null) {
            FrameLayout.LayoutParams tl = (FrameLayout.LayoutParams) status.getLayoutParams();
            tl.height = sl.height;
            tl.topMargin = sl.topMargin;
            tl.gravity = Gravity.TOP;
            status.setLayoutParams(tl);
        }
        applyFormat();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        place();
    }

    private void status(String msg) {
        status.setText(msg);
        status.setVisibility(msg == null ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ démarrage

    /** Ce qu'il faut avant de lancer : firmware, accès aux fichiers, puis le jeu. */
    private void next() {
        if (getIntent().getBooleanExtra(EXTRA_FIRMWARE, false)) {
            pickFirmware();
            return;
        }
        if (!Ps3Core.firmwareInstalled(this)) {
            new CodecMenu(this).title("Firmware PS3", title)
                    .message("Le moteur PS3 a besoin du firmware officiel de la console (PS3UPDAT.PUP). "
                            + "Télécharge-le sur le site de PlayStation, puis choisis le fichier ici. "
                            + "L'installation prend quelques minutes, une seule fois.")
                    .item("Choisir PS3UPDAT.PUP", null, new Runnable() {
                        public void run() { pickFirmware(); }
                    })
                    .item("Retour", null, new Runnable() {
                        public void run() { finish(); }
                    })
                    .onBack(new Runnable() {
                        public void run() { finish(); }
                    })
                    .show(stage);
            return;
        }
        if (path == null) {
            fail("Ce jeu n'a pas de chemin lisible : place-le dans un dossier du téléphone.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            askAllFiles();
            return;
        }
        status("Chargement de " + title + "…\nLa première fois, le moteur compile le jeu : ça peut prendre plusieurs minutes.");
        maybeBoot();
    }

    private void maybeBoot() {
        if (!ready || booted || !surfaceSeen || path == null || !Ps3Core.firmwareInstalled(this)) return;
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) return;
        if (CodecMenu.find(stage) != null) return;
        booted = true;
        DebugLog.event("[PS3] démarrage du jeu : applyPs3 puis nativeBoot");
        CoreAffinity.start(this, "ps3");
        new Thread(new Runnable() {
            public void run() {
                EngineSettings.applyPs3(Ps3Activity.this, title + " " + path);
                final int r = Ps3Core.nativeBoot(path);
                DebugLog.event("[PS3] nativeBoot a rendu " + r + " (" + Ps3Core.bootName(r) + ")");
                DebugLog.snapshot(Ps3Activity.this, "PS3 après nativeBoot");
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == Ps3Core.BOOT_OK || r == Ps3Core.BOOT_ALREADY_ADDED) {
                            status(null);
                            watch();
                        } else {
                            fail(bootMessage(r));
                        }
                    }
                });
            }
        }, "ps3-boot").start();
    }

    private static String bootMessage(int r) {
        String n = Ps3Core.bootName(r);
        if (n.equals("DecryptionError")) return "Ce jeu est chiffré : il faut sa licence (fichier .rap) ou une copie déchiffrée.";
        if (n.equals("FirmwareMissing") || n.equals("FirmwareVersion")) return "Le firmware PS3 manque ou est trop ancien : réinstalle PS3UPDAT.PUP.";
        if (n.equals("InvalidFileOrFolder") || n.equals("NothingToBoot")) return "Le moteur PS3 ne trouve rien à lancer ici (image .iso ou dossier avec PS3_GAME).";
        return "Le jeu n'a pas démarré (" + n + ").";
    }

    /** Le jeu s'arrête de lui-même (Quitter depuis le menu du jeu) : on revient à la bibliothèque. */
    private int lastState = -99;

    private void watch() {
        stage.postDelayed(new Runnable() {
            public void run() {
                if (isFinishing()) return;
                int s = Ps3Core.nativeState();
                if (s != lastState) {
                    DebugLog.event("[PS3] état du moteur " + lastState + " -> " + s + " (titre " + Ps3Core.nativeTitleId() + ")");
                    lastState = s;
                }
                boolean moving = android.os.SystemClock.uptimeMillis() < stateUntil;
                if (s == Ps3Core.RUNNING && stateMsg != null) {
                    // Le moteur est reparti de l'état écrit (sauvegarde) ou relu (chargement).
                    I18n.toast(Ps3Activity.this, stateMsg, android.widget.Toast.LENGTH_SHORT).show();
                    stateMsg = null;
                    stateUntil = 0;
                    status(null);
                } else if ((s == Ps3Core.STOPPED || s == Ps3Core.READY) && !moving) {
                    if (stateMsg != null) {
                        stateMsg = null;
                        fail("Le moteur ne s'est pas relancé après la sauvegarde rapide. Ce jeu ne la "
                                + "permet peut-être pas : utilise la sauvegarde du jeu.");
                        return;
                    }
                    finish();
                    return;
                }
                stage.postDelayed(this, moving ? 500 : 1000);
            }
        }, 3000);
    }

    private void askAllFiles() {
        new CodecMenu(this).title("Accès aux fichiers", title)
                .message("Le moteur PS3 lit le jeu directement dans son dossier. Autorise Origami à accéder "
                        + "à tous les fichiers, puis relance le jeu.")
                .item("Autoriser", null, new Runnable() {
                    public void run() {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + getPackageName())));
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + getPackageName())));
                        }
                        finish();
                    }
                })
                .item("Retour", null, new Runnable() {
                    public void run() { finish(); }
                })
                .show(stage);
    }

    // ------------------------------------------------------------------ firmware

    private void pickFirmware() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*"), REQ_FIRMWARE);
        } catch (Exception e) {
            fail("Impossible d'ouvrir le sélecteur de fichiers.");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (ScreenCapture.onResult(this, request, result, data)) return;
        if (request != REQ_FIRMWARE) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            if (getIntent().getBooleanExtra(EXTRA_FIRMWARE, false) || !Ps3Core.firmwareInstalled(this)) finish();
            return;
        }
        installFirmware(data.getData());
    }

    private void installFirmware(final Uri uri) {
        status("Installation du firmware PS3…");
        net.rpcsx.ProgressRepository.listener = new net.rpcsx.ProgressRepository.Listener() {
            public void onProgress(long id, final long value, final long max, final String message) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (value < 0) status("Échec : " + (message != null ? message : "firmware illisible"));
                        else status("Installation du firmware PS3… " + (max > 0 ? value * 100 / max + " %" : "")
                                + (message != null ? "\n" + message : ""));
                    }
                });
            }
        };
        new Thread(new Runnable() {
            public void run() {
                boolean ok = false;
                ParcelFileDescriptor pfd = null;
                try {
                    pfd = getContentResolver().openFileDescriptor(uri, "r");
                    // Le moteur garde le descripteur et le ferme lui-même.
                    if (pfd != null) ok = Ps3Core.nativeInstallFw(pfd.detachFd(), 1);
                } catch (Exception ignored) {
                    ok = false;
                } finally {
                    try {
                        if (pfd != null) pfd.close();
                    } catch (Exception ignored) {
                        // Déjà détaché.
                    }
                }
                final boolean done = ok && Ps3Core.firmwareInstalled(Ps3Activity.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        net.rpcsx.ProgressRepository.listener = null;
                        if (!done) {
                            fail("Ce fichier n'est pas un firmware PS3 valide (PS3UPDAT.PUP).");
                        } else if (getIntent().getBooleanExtra(EXTRA_FIRMWARE, false)) {
                            I18n.toast(Ps3Activity.this, "Firmware PS3 installé.", android.widget.Toast.LENGTH_LONG).show();
                            finish();
                        } else {
                            next();
                        }
                    }
                });
            }
        }, "ps3-firmware").start();
    }


    // ------------------------------------------------------------------ format d'image (touche FORMAT)

    private ScreenFormat.Key formatKey;

    private ScreenFormat.Key formatKey() {
        if (formatKey == null) {
            formatKey = new ScreenFormat.Key(store, "ps3", ScreenFormat.KEYS, new ScreenFormat.Host() {
                public void applyFormat() { Ps3Activity.this.applyFormat(); }

                public void say(String text) {
                    I18n.say(Ps3Activity.this, text);
                }

                public void refreshKeys() { if (deck != null) deck.refreshKeys(); }
            });
        }
        return formatKey;
    }

    /** Cadre la surface du moteur selon le format choisi (Auto, 16:9, 4:3 ou Étirer). */
    private void applyFormat() {
        if (stage == null || screen == null) return;
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean full = land || (pads != null && pads.any() && store.padFullscreen());
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = stage.getWidth() > 0 ? stage.getWidth() : dm.widthPixels;
        int h = full ? (stage.getHeight() > 0 ? stage.getHeight() : dm.heightPixels) : dm.heightPixels / 2;
        boolean fill = ScreenFormat.fit(screen, w, h, ScreenFormat.gravityFor(full), store.displayMode("ps3"));
        if (lastFill != null && lastFill == fill) return;
        lastFill = fill;
        try { Ps3Core.nativeSettingsSet("Video@@Stretch To Display Area", String.valueOf(fill)); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ manette

    /** Touches Origami vers la manette PS3, par position : A = Croix, B = Rond, X = Carré, Y = Triangle. */
    private void press(String key, boolean down) {
        if (key.equals("ASPECT")) {
            formatKey().press(down);
            return;
        }
        if (key.equals("SHOT") || key.equals("REC")) {
            if (down) {
                if (key.equals("SHOT")) ScreenCapture.shot(this, screen, title);
                else ScreenCapture.video(this, title);
            }
            return;
        }
        if (key.equals("HOME")) {
            if (down) home();
            return;
        }
        int b1 = 0, b2 = 0;
        if (key.equals("A")) b2 = Ps3Core.D2_CROSS;
        else if (key.equals("B")) b2 = Ps3Core.D2_CIRCLE;
        else if (key.equals("X")) b2 = Ps3Core.D2_SQUARE;
        else if (key.equals("Y")) b2 = Ps3Core.D2_TRIANGLE;
        else if (key.equals("L")) b2 = Ps3Core.D2_L1;
        else if (key.equals("R")) b2 = Ps3Core.D2_R1;
        else if (key.equals("L2")) b2 = Ps3Core.D2_L2;
        else if (key.equals("R2")) b2 = Ps3Core.D2_R2;
        else if (key.equals("UP")) b1 = Ps3Core.D1_UP;
        else if (key.equals("DOWN")) b1 = Ps3Core.D1_DOWN;
        else if (key.equals("LEFT")) b1 = Ps3Core.D1_LEFT;
        else if (key.equals("RIGHT")) b1 = Ps3Core.D1_RIGHT;
        else if (key.equals("SELECT")) b1 = Ps3Core.D1_SELECT;
        else if (key.equals("START")) b1 = Ps3Core.D1_START;
        else if (key.equals("L3")) b1 = Ps3Core.D1_L3;
        else if (key.equals("R3")) b1 = Ps3Core.D1_R3;
        else if (key.equals("QSAVE")) {
            if (down) quickSave();
            return;
        } else if (key.equals("QLOAD")) {
            if (down) quickLoad();
            return;
        } else if (key.equals("FAST")) {
            if (down) {
                I18n.toast(this, "Pas d'accélération sur PlayStation 3 : le moteur ne l'offre pas.",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        } else return;
        if (down) {
            d1 |= b1;
            d2 |= b2;
        } else {
            d1 &= ~b1;
            d2 &= ~b2;
        }
        send();
    }

    private void axis(String key, float x, float y) {
        if (key.equals("TOUCHPAD")) return;
        if (key.equals("STICK_R")) {
            rx = x;
            ry = y;
        } else {
            lx = x;
            ly = y;
        }
        send();
    }

    private void send() {
        if (!ready || menuOpen) return;
        Ps3Core.nativePad(0, d1, d2, Ps3Core.stick(lx), Ps3Core.stick(ly), Ps3Core.stick(rx), Ps3Core.stick(ry));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        InputDevice d = e.getDevice();
        if (!Pads.isPad(d)) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && e.getAction() == KeyEvent.ACTION_UP) {
                home();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (pads == null) pads = new Pads(this);
        String k = Pads.key(pads.profile(d), e.getKeyCode());
        if (k == null) return true;
        CodecMenu m = CodecMenu.find(stage);
        if (m != null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) m.key(k);
            return true;
        }
        if (e.getRepeatCount() == 0) press(k, e.getAction() == KeyEvent.ACTION_DOWN);
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (!Pads.isPad(e.getDevice()) || e.getActionMasked() != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(e);
        }
        lx = e.getAxisValue(MotionEvent.AXIS_X);
        ly = e.getAxisValue(MotionEvent.AXIS_Y);
        rx = e.getAxisValue(MotionEvent.AXIS_Z);
        ry = e.getAxisValue(MotionEvent.AXIS_RZ);
        float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X), hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        d1 &= ~(Ps3Core.D1_UP | Ps3Core.D1_DOWN | Ps3Core.D1_LEFT | Ps3Core.D1_RIGHT);
        if (hx < -0.5f) d1 |= Ps3Core.D1_LEFT;
        if (hx > 0.5f) d1 |= Ps3Core.D1_RIGHT;
        if (hy < -0.5f) d1 |= Ps3Core.D1_UP;
        if (hy > 0.5f) d1 |= Ps3Core.D1_DOWN;
        float lt = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
        float rt = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
        if (lt > 0.5f) d2 |= Ps3Core.D2_L2;
        else d2 &= ~Ps3Core.D2_L2;
        if (rt > 0.5f) d2 |= Ps3Core.D2_R2;
        else d2 &= ~Ps3Core.D2_R2;
        send();
        return true;
    }

    // ------------------------------------------------------------------ menu Maison

    private void home() {
        if (CodecMenu.find(stage) != null) return;
        if (!booted) {
            finish();
            return;
        }
        menuOpen = true;
        Ps3Core.nativePad(0, 0, 0, 128, 128, 128, 128);
        Ps3Core.nativePause();
        new CodecMenu(this).title(title, "PLAYSTATION 3")
                .item("Reprendre", null, new Runnable() {
                    public void run() { resumeGame(); }
                })
                .item("Menu de la console", "Le menu PlayStation du jeu (trophées, quitter…)", new Runnable() {
                    public void run() {
                        resumeGame();
                        Ps3Core.nativeHomeMenu();
                    }
                })
                .sub("Affichage", "ATH, format d'image, Lumen", new Runnable() {
                    public void run() {
                        EngineMenus.display(Ps3Activity.this, stage, store, "ps3", "PLAYSTATION 3", backHome(), new Runnable() {
                            public void run() { if (hud != null) hud.apply(); }
                        }, new Runnable() {
                            public void run() {
                                applyFormat();
                                if (deck != null) deck.refreshKeys();
                            }
                        }, new Runnable() {
                            public void run() { if (andon != null) andon.refresh(); }
                        });
                    }
                })
                .sub("Options contrôleur", "Manette, transparence, gâchettes", new Runnable() {
                    public void run() {
                        EngineMenus.controller(Ps3Activity.this, stage, store, deck, backHome(), new Runnable() {
                            public void run() { place(); }
                        });
                    }
                })
                .sub("Profil et réglages", EngineSettings.profileName(this, EngineSettings.PS3), new Runnable() {
                    public void run() {
                        EngineSettings.menu(Ps3Activity.this, stage, EngineSettings.PS3, "PLAYSTATION 3", backHome());
                    }
                })
                .item("Redémarrer le jeu", "Relance le jeu depuis le début", new Runnable() {
                    public void run() { restart(); }
                })
                .item("Quitter le jeu", null, new Runnable() {
                    public void run() { quit(); }
                })
                .onBack(new Runnable() {
                    public void run() { resumeGame(); }
                })
                .show(stage);
    }

    /** Retour du sous-menu vers le menu Home. */
    private Runnable backHome() {
        return new Runnable() {
            public void run() {
                CodecMenu m = CodecMenu.find(stage);
                if (m != null) m.dismiss();
                home();
            }
        };
    }

    private void resumeGame() {
        CodecMenu m = CodecMenu.find(stage);
        if (m != null) m.dismiss();
        menuOpen = false;
        Ps3Core.nativeResume();
        Theme.fullscreen(this);
    }

    /**
     * Sur PS3, un état se sauvegarde en arrêtant la machine, qui repart aussitôt de l'état écrit
     * (ARMSX3 : Emu.Kill puis Restart). Pendant ce passage le moteur est « arrêté » : watch() ne doit
     * pas prendre ça pour la fin du jeu, sinon on revenait à l'accueil sans rien dire.
     */
    private volatile long stateUntil;
    private volatile String stateMsg;

    private void quickSave() {
        if (stateMsg != null) return;
        stateUntil = android.os.SystemClock.uptimeMillis() + 90000;
        stateMsg = "Partie sauvegardée.";
        status("Sauvegarde rapide…");
        new Thread(new Runnable() {
            public void run() {
                // Le moteur attend une image pour la vignette : pas sur le fil de l'interface.
                final boolean ok = Ps3Core.nativeSaveState(1);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (ok) return;
                        stateUntil = 0;
                        stateMsg = null;
                        status(null);
                        I18n.toast(Ps3Activity.this, "Sauvegarde impossible ici.", android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, "ps3-save").start();
    }

    private void quickLoad() {
        if (stateMsg != null) return;
        if (!Ps3Core.nativeHasState(1)) {
            I18n.toast(this, "Aucune sauvegarde rapide.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        stateUntil = android.os.SystemClock.uptimeMillis() + 120000;
        stateMsg = "Sauvegarde rapide chargée.";
        status("Chargement de la sauvegarde rapide…");
        if (!Ps3Core.nativeLoadState(1)) {
            stateUntil = 0;
            stateMsg = null;
            status(null);
            I18n.toast(this, "Chargement impossible.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Relance le jeu : le moteur tourne dans son propre processus, qui meurt à la sortie. L'accueil
     * (autre processus) reçoit l'intention et rouvre le jeu une fois le moteur arrêté.
     */
    private void restart() {
        // Passerelle noire dans le processus principal : elle relance le jeu une fois ce processus mort,
        // sans repasser par l'accueil (l'ancien EXTRA_RESTART n'est plus utilisé ici).
        RelaunchActivity.from(this);
        quit();
    }

    private void quit() {
        CodecMenu m = CodecMenu.find(stage);
        if (m != null) m.dismiss();
        status("Arrêt du jeu…");
        new Thread(new Runnable() {
            public void run() {
                Ps3Core.nativeKill();
                runOnUiThread(new Runnable() {
                    public void run() { finish(); }
                });
            }
        }, "ps3-stop").start();
    }

    private void fail(String msg) {
        DebugLog.event("[PS3] échec affiché : " + msg);
        new CodecMenu(this).title("PlayStation 3", title)
                .message(msg)
                .item("Retour", null, new Runnable() {
                    public void run() { finish(); }
                })
                .onBack(new Runnable() {
                    public void run() { finish(); }
                })
                .show(stage);
    }

    // ------------------------------------------------------------------ précompilation (essais)

    private final android.os.Handler watchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean precompileDone;
    /** L'utilisateur est passé sur une autre app : la notification de compilation est alors affichée (0.67). */
    private boolean leftApp;

    /**
     * Essai de précompilation : le moteur continue même écran éteint (service de premier plan), puis, quand les fils de compilation sont restés calmes 90 s après le démarrage, le jeu est mis en pause et le
     * chronomètre de session s'arrête : la durée exacte de la compilation est écrite au journal.
     */
    /** Luminosité forcée au minimum par la précompilation : rendue dès que l'utilisateur touche l'écran ou une touche. */
    private boolean dimmed;

    private void restoreBrightness() {
        if (!dimmed) return;
        dimmed = false;
        android.view.WindowManager.LayoutParams back = getWindow().getAttributes();
        back.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(back);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        // La compilation continue écran allumé ; seule la luminosité du réglage système revient (elle restait bloquée au minimum).
        restoreBrightness();
    }

    private void startPrecompileWatch() {
        if (!store.precompile()) return;
        // Le moteur ne démarre son rendu (donc la compilation des shaders) qu'avec une surface : écran éteint, il attend sans fin
        // (journal du Fold 5, 22/10). L'écran reste donc allumé au minimum de luminosité, le téléphone branché.
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        android.view.WindowManager.LayoutParams wl = getWindow().getAttributes();
        wl.screenBrightness = 0.01f;
        getWindow().setAttributes(wl);
        dimmed = true;
        // Service de premier plan + processeur éveillé ; le moteur n'est pas mis en arrière-plan.
        final ShaderMeter m = new ShaderMeter(new java.io.File(Ps3Core.root(this)));
        final long[] calmSince = {0};
        final long[] lastLog = {0};
        CoreAffinity.boost = true;
        watchHandler.postDelayed(new Runnable() {
            public void run() {
                if (precompileDone || isFinishing()) {
                    CoreAffinity.boost = false;
                    return;
                }
                long now = android.os.SystemClock.elapsedRealtime();
                int code = m.codePct(), shader = m.shaderPct();
                boolean running = booted && ready && Ps3Core.nativeState() == 3;
                for (String mod : m.newModules()) DebugLog.event("[PS3] module compilé : " + mod);
                // Progression en direct dans la notification (pas à l'écran), toutes les 5 s.
                if (!precompileDone && leftApp) {
                    try {
                        long sec = DebugLog.sessionMs() / 1000;
                        String text = "Charge : code PS3 " + Math.max(code, 0) + " % · shaders " + Math.max(shader, 0) + " % d'un cœur · cache " + m.cache()
                                + " · " + String.format(java.util.Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
                        startForegroundService(new Intent(Ps3Activity.this, PrecompileService.class)
                                .setAction(PrecompileService.ACTION_PROGRESS).putExtra("text", text));
                    } catch (Throwable ignored) {
                        // Notification non mise à jour : la compilation continue.
                    }
                }
                if (now - lastLog[0] >= 30000L) {
                    lastLog[0] = now;
                    DebugLog.event("[PS3] précompilation : code PS3 " + code + " %, shaders " + shader + " %, cœurs " + CoreAffinity.freqs());
                }
                // Deux jauges séparées : le chrono s'arrête quand le code PS3 ET les shaders sont restés calmes 90 s.
                if (running && code >= 0 && shader >= 0 && code < 5 && shader < 5) {
                    if (calmSince[0] == 0) calmSince[0] = now;
                    if (now - calmSince[0] >= 90000L && DebugLog.sessionMs() > 120000L) {
                        precompileDone = true;
                        CoreAffinity.boost = false;
                        restoreBrightness();
                        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        DebugLog.stopSession();
                        DebugLog.event("[PS3] compilation terminée en " + DebugLog.clock(DebugLog.sessionMs() - 90000L)
                                + " (chrono arrêté à " + DebugLog.clock(DebugLog.sessionMs()) + ", 90 s de calme comprises) : jeu mis en pause");
                        try {
                            Ps3Core.nativePause();
                        } catch (Throwable ignored) {
                            // Déjà en pause.
                        }
                        try {
                            startForegroundService(new Intent(Ps3Activity.this, PrecompileService.class).setAction(PrecompileService.ACTION_DONE));
                        } catch (Throwable t) {
                            stopService(new Intent(Ps3Activity.this, PrecompileService.class));
                        }
                        return;
                    }
                } else {
                    calmSince[0] = 0;
                }
                watchHandler.postDelayed(this, 5000);
            }
        }, 5000);
    }

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void onResume() {
        super.onResume();
        if (hud != null) hud.apply();
        Theme.fullscreen(this);
        // Retour de l'éditeur de manette : la disposition a pu changer.
        if (deck != null) {
            deck.reloadFromSource();
            place();
        }
        if (ready) Ps3Core.nativeBackground(false);
        if (leftApp) {
            // De retour dans le jeu : la notification de compilation disparaît.
            leftApp = false;
            stopService(new Intent(this, PrecompileService.class));
        }
    }

    /** Fenêtre réduite (écran partagé, fenêtre flottante) : l'app n'est plus en plein écran, la notification de compilation apparaît. */
    @Override
    public void onMultiWindowModeChanged(boolean inMultiWindow) {
        super.onMultiWindowModeChanged(inMultiWindow);
        if (!store.precompile() || precompileDone || !ready) return;
        if (inMultiWindow && !leftApp) {
            leftApp = true;
            try {
                startForegroundService(new Intent(this, PrecompileService.class));
            } catch (Throwable t) {
                DebugLog.event("[PS3] service de précompilation impossible", t);
            }
        } else if (!inMultiWindow && leftApp) {
            leftApp = false;
            stopService(new Intent(this, PrecompileService.class));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Précompilation en cours : le moteur continue écran éteint (service de premier plan).
        if (ready && !(store.precompile() && !precompileDone)) Ps3Core.nativeBackground(true);
        if (ready && store.precompile() && !precompileDone && !isFinishing()) {
            // Passage sur une autre app pendant la compilation : le service de premier plan et sa notification démarrent.
            leftApp = true;
            try {
                startForegroundService(new Intent(this, PrecompileService.class));
            } catch (Throwable t) {
                DebugLog.event("[PS3] service de précompilation impossible", t);
            }
        }
    }

    /** Qui ferme l'écran du jeu, et quand : la pile d'appels est écrite au journal. */
    @Override
    public void finish() {
        DebugLog.event("[PS3] finish() appelé, isFinishing=" + isFinishing(), new Throwable("finish"));
        super.finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        DebugLog.event("[PS3] onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        DebugLog.event("[PS3] onStop, isFinishing=" + isFinishing());
        DebugLog.snapshot(this, "PS3 arrêt de l'écran");
    }

    @Override
    protected void onDestroy() {
        ScreenCapture.gameClosed(this);
        DebugLog.event("[PS3] onDestroy, isFinishing=" + isFinishing());
        DebugLog.perfStop();
        super.onDestroy();
        if (hud != null) hud.stop();
        if (peak != null) peak.stop();
        Theme.inGame = false;
        // Le processus « :ps3 » ne sert qu'au jeu : on le termine pour libérer la mémoire du moteur.
        if (isFinishing()) {
            try {
                Ps3Core.nativeKill();
            } catch (Throwable ignored) {
                // Déjà arrêté.
            }
            DebugLog.markClean(this);
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
