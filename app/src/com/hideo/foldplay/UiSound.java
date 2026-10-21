package com.hideo.foldplay;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * Bruits de l'interface et musique d'accueil, propres à chaque thème (assets/sounds/<thème>/,
 * assets/music/<thème>.ogg, synthétisés par tools/make-sounds.py). Volumes séparés dans
 * Réglages > Son ; « Son des menus » coupe les bruits, « Musique » coupe la musique.
 */
final class UiSound {
    static final String MOVE = "move", SELECT = "select", BACK = "back", OPEN = "open", LAUNCH = "launch",
            NEXT = "next", TAB = "tab", ON = "on", OFF = "off", ERROR = "error", DONE = "done", CLOSE = "close";
    private static final String[] NAMES = {MOVE, SELECT, BACK, OPEN, LAUNCH, NEXT, TAB, ON, OFF, ERROR, DONE, CLOSE};

    private static SoundPool pool;
    /** Sons chargés : un appui avant la fin du chargement ne joue rien (plus de « not READY », 0.66.9). */
    private static final java.util.Set<Integer> ready = java.util.Collections.synchronizedSet(new java.util.HashSet<Integer>());
    private static String poolTheme;
    private static final int[] ids = new int[NAMES.length];
    private static long lastMove, lastAny;

    /** Un bruit de l'interface a-t-il été demandé dans les ms dernières millisecondes ? */
    static boolean playedRecently(long ms) {
        return android.os.SystemClock.uptimeMillis() - lastAny < ms;
    }

    private static String musicTheme;
    private static boolean wanted;

    private UiSound() {
    }

    /** Joue un bruit de l'interface du thème actif. */
    static void play(Context ctx, String name) {
        lastAny = android.os.SystemClock.uptimeMillis();
        Store store = new Store(ctx);
        if (!store.menuSound() || !ringing(ctx)) return;
        float vol = store.uiVolume() / 100f;
        if (vol <= 0f) return;
        if (MOVE.equals(name) || TAB.equals(name)) {
            // Défilement rapide : un seul tic toutes les 45 ms.
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastMove < 45) return;
            lastMove = now;
        }
        try {
            load(ctx.getApplicationContext(), store.theme());
            for (int i = 0; i < NAMES.length; i++) {
                if (NAMES[i].equals(name) && ids[i] != 0 && ready.contains(ids[i])) pool.play(ids[i], vol, vol, 1, 0, 1f);
            }
        } catch (Exception ignored) {
            // Pas de son plutôt qu'un plantage.
        }
    }

    private static synchronized void load(Context app, String theme) throws java.io.IOException {
        if (pool != null && theme.equals(poolTheme)) return;
        if (pool != null) pool.release();
        pool = new SoundPool.Builder().setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        poolTheme = theme;
        ready.clear();
        pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool sp, int sampleId, int status) {
                if (status == 0) ready.add(sampleId);
            }
        });
        for (int i = 0; i < NAMES.length; i++) {
            ids[i] = 0;
            AssetFileDescriptor fd = open(app, "sounds/" + theme + "/" + NAMES[i] + ".ogg");
            if (fd == null) fd = open(app, "sounds/codec/" + NAMES[i] + ".ogg");
            if (fd != null) ids[i] = pool.load(fd, 1);
        }
    }

    private static AssetFileDescriptor open(Context app, String path) {
        try {
            return app.getAssets().openFd(path);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ musique

    private static MusicLoop music;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    /** Pause retardée : passer d'un écran à l'autre ne coupe plus la musique (0.62.1). */
    private static final Runnable PAUSE = new Runnable() {
        public void run() {
            if (music != null) music.pause();
        }
    };

    /**
     * Sons seulement en mode sonnerie (choix de BOSS, 0.62.2) : rien en vibreur ni en silencieux.
     * Le changement de mode coupe ou relance la musique aussitôt.
     */
    static boolean ringing(Context ctx) {
        watchRinger(ctx.getApplicationContext());
        android.media.AudioManager am = (android.media.AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        return am == null || am.getRingerMode() == android.media.AudioManager.RINGER_MODE_NORMAL;
    }

    private static boolean ringerWatched;

    private static void watchRinger(final Context app) {
        if (ringerWatched) return;
        ringerWatched = true;
        android.content.BroadcastReceiver r = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context c, android.content.Intent i) {
                if (wanted) musicStart(app);
            }
        };
        android.content.IntentFilter f = new android.content.IntentFilter(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION);
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
            else app.registerReceiver(r, f);
        } catch (Exception ignored) {
            // Sans écoute : le mode est relu au prochain écran.
        }
    }

    /** Lance (ou reprend) la musique d'accueil du thème actif, en boucle sans blanc. */
    static void musicStart(Context ctx) {
        MAIN.removeCallbacks(PAUSE);
        wanted = true;
        Store store = new Store(ctx);
        String theme = store.theme();
        float vol = store.musicOn() && ringing(ctx) ? store.musicVolume() / 100f : 0f;
        try {
            if (vol <= 0f) {
                if (music != null) music.pause();
                return;
            }
            if (music != null && !theme.equals(musicTheme)) {
                music.release();
                music = null;
            }
            if (music == null) {
                AssetFileDescriptor fd = open(ctx.getApplicationContext(), "music/" + theme + ".ogg");
                if (fd == null) return;
                music = new MusicLoop(fd);
                fd.close();
                musicTheme = theme;
            }
            music.play(vol);
        } catch (Exception e) {
            musicStop();
        }
    }

    /** Met la musique en pause (jeu lancé, app en arrière-plan), après un court délai. */
    static void musicPause() {
        wanted = false;
        MAIN.removeCallbacks(PAUSE);
        MAIN.postDelayed(PAUSE, 700);
    }

    /** Thème ou volume changé : on recharge si la musique devait jouer. */
    static void refresh(Context ctx) {
        if (wanted) musicStart(ctx);
    }

    static void musicStop() {
        wanted = false;
        MAIN.removeCallbacks(PAUSE);
        if (music != null) music.release();
        music = null;
        musicTheme = null;
    }
}
