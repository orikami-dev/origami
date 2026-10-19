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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
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
                boolean ok = Ps3Core.nativeInitialize(Ps3Core.root(Ps3Activity.this), "00000001",
                        Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : Build.HARDWARE);
                ready = ok;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (!ready) fail("Le moteur PS3 n'a pas pu démarrer.");
                        else next();
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

    private void build() {
        stage = new FrameLayout(this);
        stage.setBackgroundColor(0xFF000000);
        screen = new SurfaceView(this);
        screen.getHolder().addCallback(new SurfaceHolder.Callback() {
            public void surfaceCreated(SurfaceHolder h) {
            }

            public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) {
                if (!ready) return;
                // La taille avant l'événement : le moteur la lit dès qu'il a une fenêtre.
                Ps3Core.nativeSurfaceSize(w, hh);
                int event = surface == null ? Ps3Core.SURFACE_CREATED : Ps3Core.SURFACE_CHANGED;
                surface = h.getSurface();
                Ps3Core.nativeSurface(surface, event);
                surfaceSeen = true;
                maybeBoot();
            }

            public void surfaceDestroyed(SurfaceHolder h) {
                if (ready && surface != null) Ps3Core.nativeSurface(surface, Ps3Core.SURFACE_DESTROYED);
                surface = null;
            }
        });
        stage.addView(screen, new FrameLayout.LayoutParams(-1, -1));

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
                if (r - l != or - ol || b - t != ob - ot) applyFormat();
            }
        });
        formatKey();
        // ATH : le moteur donne la durée de sa dernière image.
        hud = new HudOverlay(this, stage, store, "armsx3", new HudOverlay.Source() {
            public float fps(float seconds) {
                if (!ready) return -1;
                try {
                    long ns = Ps3Core.nativeFramePeriodNs();
                    return ns > 0 ? 1e9f / ns : -1;
                } catch (Throwable t) {
                    return -1;
                }
            }

            public float frameMaxMs() {
                return -1;
            }

            public boolean paused() {
                return menuOpen;
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
        boolean pad = pads != null && pads.any();
        FrameLayout.LayoutParams sl = (FrameLayout.LayoutParams) screen.getLayoutParams();
        FrameLayout.LayoutParams dl = (FrameLayout.LayoutParams) deck.getLayoutParams();
        int h = getResources().getDisplayMetrics().heightPixels;
        if (land || pad) {
            sl.height = -1;
            sl.gravity = Gravity.TOP;
            dl.height = -1;
            dl.gravity = Gravity.BOTTOM;
            deck.setOverlay(true, store.overlayOpacity());
        } else {
            sl.height = h / 2;
            sl.gravity = Gravity.TOP;
            dl.height = h / 2;
            dl.gravity = Gravity.BOTTOM;
            deck.setOverlay(false, store.overlayOpacity());
        }
        deck.setVisibility(pad ? View.GONE : View.VISIBLE);
        screen.setLayoutParams(sl);
        deck.setLayoutParams(dl);
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
        new Thread(new Runnable() {
            public void run() {
                EngineSettings.applyPs3(Ps3Activity.this);
                final int r = Ps3Core.nativeBoot(path);
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
    private void watch() {
        stage.postDelayed(new Runnable() {
            public void run() {
                if (isFinishing()) return;
                int s = Ps3Core.nativeState();
                if (s == Ps3Core.STOPPED || s == Ps3Core.READY) {
                    finish();
                    return;
                }
                stage.postDelayed(this, 1000);
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
                public void applyFormat() { applyFormat(); }

                public void say(String text) {
                    I18n.toast(Ps3Activity.this, text, android.widget.Toast.LENGTH_SHORT).show();
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
        boolean full = land || (pads != null && pads.any());
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int w = stage.getWidth() > 0 ? stage.getWidth() : dm.widthPixels;
        int h = full ? (stage.getHeight() > 0 ? stage.getHeight() : dm.heightPixels) : dm.heightPixels / 2;
        boolean fill = ScreenFormat.fit(screen, w, h, ScreenFormat.gravityFor(full), store.displayMode("ps3"));
        try { Ps3Core.nativeSettingsSet("Video@@Stretch To Display Area", String.valueOf(fill)); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ manette

    /** Touches Origami vers la manette PS3, par position : A = Croix, B = Rond, X = Carré, Y = Triangle. */
    private void press(String key, boolean down) {
        if (key.equals("ASPECT")) {
            formatKey().press(down);
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
                .item("Sauvegarde rapide", "Emplacement 1", new Runnable() {
                    public void run() {
                        resumeGame();
                        quickSave();
                    }
                })
                .item("Chargement rapide", "Emplacement 1", new Runnable() {
                    public void run() {
                        resumeGame();
                        quickLoad();
                    }
                })
                .item("Menu de la console", "Le menu PS du jeu (trophées, quitter…)", new Runnable() {
                    public void run() {
                        resumeGame();
                        Ps3Core.nativeHomeMenu();
                    }
                })
                .sub("Profil et réglages", EngineSettings.profileName(this, EngineSettings.PS3), new Runnable() {
                    public void run() {
                        EngineSettings.menu(Ps3Activity.this, stage, EngineSettings.PS3, "PLAYSTATION 3", backHome());
                    }
                })
                .check("ATH", "Images/s, processeur, batterie…", store.hud(), new Runnable() {
                    public void run() {
                        store.setHud(!store.hud());
                        if (hud != null) hud.apply();
                    }
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

    private void quickSave() {
        boolean ok = Ps3Core.nativeSaveState(1);
        I18n.toast(this, ok ? "Partie sauvegardée." : "Sauvegarde impossible ici.", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void quickLoad() {
        if (!Ps3Core.nativeHasState(1)) {
            I18n.toast(this, "Aucune sauvegarde rapide.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        Ps3Core.nativeLoadState(1);
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

    // ------------------------------------------------------------------ cycle de vie

    @Override
    protected void onResume() {
        super.onResume();
        if (hud != null) hud.apply();
        Theme.fullscreen(this);
        if (ready) Ps3Core.nativeBackground(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ready) Ps3Core.nativeBackground(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hud != null) hud.stop();
        Theme.inGame = false;
        // Le processus « :ps3 » ne sert qu'au jeu : on le termine pour libérer la mémoire du moteur.
        if (isFinishing() && booted) {
            try {
                Ps3Core.nativeKill();
            } catch (Throwable ignored) {
                // Déjà arrêté.
            }
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
