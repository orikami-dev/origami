package com.hideo.foldplay;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;

import jp.xenia.emulator.WindowSurfaceView;
import jp.xenia.emulator.WindowedAppActivity;

/**
 * Jeu Xbox 360 : Xenia (BSD) dessine en Vulkan dans la WindowSurfaceView, Origami fournit la
 * manette (tactile ou physique, poussée au pilote « origami » de Xenia) et le menu Maison.
 * Processus « :x360 » : un plantage du moteur ne ferme pas la bibliothèque, et sa mémoire est
 * rendue à la sortie du jeu.
 */
public class X360Activity extends WindowedAppActivity {
    static final String EXTRA_PATH = "path";
    static final String EXTRA_TITLE = "title";

    // Touches XInput (X_INPUT_GAMEPAD_BUTTON de Xenia).
    static final int UP = 0x0001, DOWN = 0x0002, LEFT = 0x0004, RIGHT = 0x0008, START = 0x0010,
            BACK = 0x0020, LS = 0x0040, RS = 0x0080, LB = 0x0100, RB = 0x0200, GUIDE = 0x0400,
            A = 0x1000, B = 0x2000, X = 0x4000, Y = 0x8000;

    private Store store;
    private Pads pads;
    private FrameLayout stage;
    private Andon andon;
    private WindowSurfaceView screen;
    private ControlDeckView deck;
    private TextView status;
    private String path, title;
    private boolean started, menuOpen;
    private HudOverlay hud;
    private FramePeak peak;
    private Boolean lastFill;
    private long lastFrames = -1;
    private int buttons, lt, rt;
    private float lx, ly, rx, ry;

    static native void nativePad(int port, boolean connected, int buttons, int lt, int rt,
                                 int lx, int ly, int rx, int ry);

    static native int nativeRumble(int port);

    /** Images du jeu présentées depuis le lancement (moteur 0.60.12 et suivants). */
    static native long nativeFrames();

    /** Pause (menu Codec ouvert) ou reprise du jeu. */
    static native void nativePause(boolean on);

    /** Étirer : le moteur remplit la surface au lieu de garder le rapport du jeu. */
    static native void nativeSetFill(boolean fill);

    /** Le moteur Xenia est livré dans l'APK : sinon pas de Xbox 360 intégrée. */
    static boolean present(Context ctx) {
        return new File(ctx.getApplicationInfo().nativeLibraryDir, "libxenia-app.so").isFile();
    }

    /** Dossier du moteur : configuration, contenu (sauvegardes, DLC) et cache. */
    static File root(Context ctx) {
        File base = ctx.getExternalFilesDir(null);
        File d = new File(base != null ? base : ctx.getFilesDir(), "x360");
        d.mkdirs();
        return d;
    }

    @Override
    protected String getWindowedAppIdentifier() {
        return "xenia";
    }

    private static boolean USED;

    @Override
    protected void onCreate(Bundle state) {
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
        DebugLog.sync(this);
        store = new Store(this);
        pads = new Pads(this);
        path = getIntent().getStringExtra(EXTRA_PATH);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null) title = "Xbox 360";
        boolean canStart = isLibraryLoaded() && path != null
                && (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager());
        if (canStart) {
            // Lu par Xenia au démarrage (cvars), avant super.onCreate qui lance le moteur.
            File r = root(this);
            Bundle cvars = new Bundle();
            cvars.putString("target", path);
            cvars.putString("storage_root", r.getPath());
            cvars.putString("content_root", new File(r, "content").getPath());
            // Le cache (XexInfoCache) est projeté en mémoire puis agrandi : sur le stockage partagé (FUSE)
            // le fichier ne grandit pas et l'écriture plante en SIGBUS. Il va donc en stockage interne.
            File cache = new File(getCacheDir(), "x360");
            cache.mkdirs();
            cvars.putString("cache_root", cache.getPath());
            // Le moteur crée executable_addr_flags.bin à 0 octet puis écrit dedans : SIGBUS (0.60.10).
            // Les cvars booléennes sont lues par getBoolean : une chaîne « true » donnait false (0.60.11).
            // On coupe ce cache (il ne sert qu'à accélérer les lancements suivants) et on purge les restes vides.
            cvars.putBoolean("disable_instruction_infocache", true);
            purgeEmpty(cache);
            purgeEmpty(new File(r, "cache"));
            cvars.putString("hid", "origami");
            // Les fenêtres du système 360 (« connectez-vous au Xbox LIVE »…) ne se touchent pas :
            // en mode headless, le moteur choisit tout seul le bouton par défaut (0.62.1).
            cvars.putBoolean("headless", true);
            // Diagnostic de l'écran noir (0.66.22, à retirer ensuite) : journal détaillé du moteur dans logcat.
            cvars.putInt("log_level", 3);
            // Profil et réglages graphiques choisis dans le menu Home (0.60.14).
            EngineSettings.putCvars(this, cvars);
            getIntent().putExtra(EXTRA_CVARS, cvars);
            started = true;
        }
        super.onCreate(state);
        Theme.fullscreen(this);
        build();
        if (!isLibraryLoaded()) {
            fail("Le moteur Xbox 360 n'est pas dans cette version d'Origami.");
        } else if (path == null) {
            fail("Ce jeu n'a pas de chemin lisible : place-le dans un dossier du téléphone.");
        } else if (!started) {
            askAllFiles();
        } else {
            DebugLog.event("[360] onCreate : jeu « " + title + " », chemin " + path + ", profil "
                    + EngineSettings.profile(this, EngineSettings.X360));
            DebugLog.snapshot(this, "360 création");
            DebugLog.perfStart(this, "360");
            setWindowSurfaceView(screen);
            nativePad(0, true, 0, 0, 0, 0, 0, 0, 0);
            status("Chargement de " + title + "…");
            stage.postDelayed(new Runnable() {
                public void run() { status(null); }
            }, 4000);
            rumble();
        }
    }

    @Override
    protected boolean shouldStartWindowedApp() {
        return started;
    }

    @Override
    protected void onWindowedAppFailed(String message) {
        started = false;
        if (stage == null) build();
        fail("Le moteur Xbox 360 n'a pas pu démarrer (Vulkan 1.1 requis).");
    }

    // ------------------------------------------------------------------ écran

    private void build() {
        if (stage != null) return;
        stage = new FrameLayout(this);
        stage.setBackgroundColor(0xFF000000);
        screen = new WindowSurfaceView(this);
        stage.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        andon = Andon.attach(stage, screen, store);

        deck = new ControlDeckView(this);
        deck.setSystem("x360");
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
        // ATH : le moteur compte les images que le jeu présente.
        peak = new FramePeak(new FramePeak.Probe() {
            public long read() {
                return started ? nativeFrames() : -1;
            }
        }, true);
        hud = new HudOverlay(this, stage, store, "xenia", new HudOverlay.Source() {
            public float fps(float seconds) {
                if (!started) return -1;
                try {
                    long n = nativeFrames();
                    float f = lastFrames >= 0 ? (n - lastFrames) / seconds : -1;
                    lastFrames = n;
                    return f;
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
                try {
                    int k = Integer.parseInt(EngineSettings.get(X360Activity.this, EngineSettings.X360,
                            EngineSettings.opts(EngineSettings.X360)[0]).trim());
                    return (1280 * k) + "×" + (720 * k);
                } catch (Throwable e) {
                    return null;
                }
            }
        });
        hud.setScreen(screen);
        hud.apply();
        place();
    }

    /** Même règle que les autres consoles : portrait = manette en bas, paysage = superposée. */
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
        if (stage != null) place();
    }

    private void status(String msg) {
        status.setText(msg);
        status.setVisibility(msg == null ? View.GONE : View.VISIBLE);
    }

    private void askAllFiles() {
        new CodecMenu(this).title("Accès aux fichiers", title)
                .message("Le moteur Xbox 360 lit le jeu directement dans son dossier. Autorise Origami "
                        + "à accéder à tous les fichiers, puis relance le jeu.")
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


    // ------------------------------------------------------------------ format d'image (touche FORMAT)

    private ScreenFormat.Key formatKey;

    private ScreenFormat.Key formatKey() {
        if (formatKey == null) {
            formatKey = new ScreenFormat.Key(store, "x360", ScreenFormat.KEYS, new ScreenFormat.Host() {
                public void applyFormat() { X360Activity.this.applyFormat(); }

                public void say(String text) {
                    I18n.say(X360Activity.this, text);
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
        boolean fill = ScreenFormat.fit(screen, w, h, ScreenFormat.gravityFor(full), store.displayMode("x360"));
        if (lastFill != null && lastFill == fill) return;
        lastFill = fill;
        try { nativeSetFill(fill); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ manette

    /** Les touches Origami portent déjà les noms Xbox (A, B, X, Y) : correspondance directe. */
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
        int b = 0;
        if (key.equals("QSAVE") || key.equals("QLOAD") || key.equals("FAST")) {
            if (down) {
                I18n.toast(this, "Pas de sauvegarde rapide ni d'accélération sur Xbox 360 : le moteur ne les offre pas.",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (key.equals("A")) b = A;
        else if (key.equals("B")) b = B;
        else if (key.equals("X")) b = X;
        else if (key.equals("Y")) b = Y;
        else if (key.equals("L")) b = LB;
        else if (key.equals("R")) b = RB;
        else if (key.equals("UP")) b = UP;
        else if (key.equals("DOWN")) b = DOWN;
        else if (key.equals("LEFT")) b = LEFT;
        else if (key.equals("RIGHT")) b = RIGHT;
        else if (key.equals("SELECT")) b = BACK;
        else if (key.equals("START")) b = START;
        else if (key.equals("L3")) b = LS;
        else if (key.equals("R3")) b = RS;
        else if (key.equals("L2")) {
            lt = down ? 255 : 0;
        } else if (key.equals("R2")) {
            rt = down ? 255 : 0;
        } else return;
        if (down) buttons |= b;
        else buttons &= ~b;
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

    /** Stick XInput : -32768 à 32767, haut positif (l'inverse d'Android). */
    private static int stick(float v) {
        return Math.max(-32768, Math.min(32767, Math.round(v * 32767f)));
    }

    private void send() {
        if (!started || menuOpen) return;
        nativePad(0, true, buttons, lt, rt, stick(lx), stick(-ly), stick(rx), stick(-ry));
    }

    /** Vibration demandée par le jeu, rendue par le téléphone si les retours haptiques sont actifs. */
    private void rumble() {
        if (!store.haptics()) return;
        final android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        stage.postDelayed(new Runnable() {
            int last;

            public void run() {
                if (isFinishing() || !started) return;
                int r = nativeRumble(0);
                int strength = Math.max(r >>> 16, r & 0xFFFF) >> 8;
                if (strength > 24 && !menuOpen) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        v.vibrate(android.os.VibrationEffect.createOneShot(60, Math.min(255, strength)));
                    } else {
                        v.vibrate(60);
                    }
                } else if (last > 24) {
                    v.cancel();
                }
                last = strength;
                stage.postDelayed(this, 50);
            }
        }, 50);
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
        CodecMenu m = stage != null ? CodecMenu.find(stage) : null;
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
        buttons &= ~(UP | DOWN | LEFT | RIGHT);
        if (hx < -0.5f) buttons |= LEFT;
        if (hx > 0.5f) buttons |= RIGHT;
        if (hy < -0.5f) buttons |= UP;
        if (hy > 0.5f) buttons |= DOWN;
        float l = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
        float r = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
        lt = Math.round(Math.max(0, Math.min(1, l)) * 255);
        rt = Math.round(Math.max(0, Math.min(1, r)) * 255);
        send();
        return true;
    }

    // ------------------------------------------------------------------ menu Maison

    private void home() {
        if (stage == null || CodecMenu.find(stage) != null) return;
        if (!started) {
            finish();
            return;
        }
        menuOpen = true;
        nativePad(0, true, 0, 0, 0, 0, 0, 0, 0);
        pause(true);
        new CodecMenu(this).title(title, "XBOX 360")
                .item("Reprendre", null, new Runnable() {
                    public void run() { resumeGame(); }
                })
                .sub("Affichage", "ATH, format d'image, Lumen", new Runnable() {
                    public void run() {
                        EngineMenus.display(X360Activity.this, stage, store, "x360", "XBOX 360", backHome(), new Runnable() {
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
                        EngineMenus.controller(X360Activity.this, stage, store, deck, backHome(), new Runnable() {
                            public void run() { place(); }
                        });
                    }
                })
                .sub("Profil et réglages", EngineSettings.profileName(this, EngineSettings.X360), new Runnable() {
                    public void run() {
                        EngineSettings.menu(X360Activity.this, stage, EngineSettings.X360, "XBOX 360", backHome());
                    }
                })
                .item("Redémarrer le jeu", "Relance le jeu depuis le début", new Runnable() {
                    public void run() { restart(); }
                })
                .item("Quitter le jeu", null, new Runnable() {
                    public void run() { finish(); }
                })
                .onBack(new Runnable() {
                    public void run() { resumeGame(); }
                })
                .show(stage);
    }

    /**
     * Relance le jeu : Xenia ne sait pas repartir proprement. Une passerelle noire (processus principal)
     * relance l'activité une fois ce processus mort (onDestroy le termine), sans passer par l'accueil.
     */
    private void restart() {
        RelaunchActivity.from(this);
        finish();
    }

    /** Pause en cours côté moteur : la reprise n'est demandée que si la pause a été demandée. */
    private boolean enginePaused;

    /**
     * Pause ou reprise du moteur, jamais sur le fil de l'interface (0.66.21) : Emulator::Pause attend que le fil
     * audio de Xenia l'acquitte, et tant que le jeu n'a pas démarré ce fil ne répond pas (ANR puis arrêt
     * de l'app, journal du 21/10). Pas de pause non plus avant la première image : il n'y a rien à figer.
     */
    private void pause(final boolean on) {
        if (on) {
            long n;
            try { n = nativeFrames(); } catch (Throwable t) { n = 0; }
            if (n <= 0) return;
        } else if (!enginePaused) {
            return;
        }
        enginePaused = on;
        PAUSER.execute(new Runnable() {
            public void run() {
                try { nativePause(on); } catch (Throwable ignored) { }
            }
        });
    }

    private static final java.util.concurrent.ExecutorService PAUSER = java.util.concurrent.Executors.newSingleThreadExecutor();

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
        pause(false);
        Theme.fullscreen(this);
        send();
    }

    private void fail(String msg) {
        DebugLog.event("[360] échec affiché : " + msg);
        new CodecMenu(this).title("Xbox 360", title)
                .message(msg)
                .item("Retour", null, new Runnable() {
                    public void run() { finish(); }
                })
                .onBack(new Runnable() {
                    public void run() { finish(); }
                })
                .show(stage);
    }

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        ScreenCapture.onResult(this, request, result, data);
    }

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
    }

    /** Qui ferme l'écran du jeu, et quand : la pile d'appels est écrite au journal. */
    @Override
    public void finish() {
        DebugLog.event("[360] finish() appelé, isFinishing=" + isFinishing(), new Throwable("finish"));
        super.finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        DebugLog.event("[360] onStart");
    }

    @Override
    protected void onStop() {
        super.onStop();
        DebugLog.event("[360] onStop, isFinishing=" + isFinishing());
        DebugLog.snapshot(this, "360 arrêt de l'écran");
    }

    @Override
    protected void onDestroy() {
        DebugLog.event("[360] onDestroy, isFinishing=" + isFinishing());
        ScreenCapture.gameClosed(this);
        DebugLog.perfStop();
        super.onDestroy();
        if (hud != null) hud.stop();
        if (peak != null) peak.stop();
        Theme.inGame = false;
        // Le processus « :x360 » ne sert qu'au jeu : Xenia ne sait pas s'arrêter proprement, on
        // termine le processus pour rendre sa mémoire.
        if (isFinishing()) {
            DebugLog.markClean(this);
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    /** Supprime les fichiers vides laissés par un cache planté (dossier parcouru en profondeur). */
    private static void purgeEmpty(File dir) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) purgeEmpty(f);
            else if (f.length() == 0 && f.getName().endsWith(".bin")) f.delete();
        }
    }
}
