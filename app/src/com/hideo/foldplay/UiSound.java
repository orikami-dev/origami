package com.hideo.foldplay;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;

/**
 * Bruits de l'interface et musique d'accueil, propres à chaque thème (assets/sounds/<thème>/,
 * assets/music/<thème>.ogg, synthétisés par tools/make-sounds.py). Volumes séparés dans
 * Réglages > Son ; « Son des menus » coupe les bruits, « Musique » coupe la musique.
 */
final class UiSound {
    static final String MOVE = "move", SELECT = "select", BACK = "back", OPEN = "open", LAUNCH = "launch";
    private static final String[] NAMES = {MOVE, SELECT, BACK, OPEN, LAUNCH};

    private static SoundPool pool;
    private static String poolTheme;
    private static final int[] ids = new int[NAMES.length];
    private static long lastMove;

    private static MediaPlayer music;
    private static String musicTheme;
    private static boolean wanted;

    private UiSound() {
    }

    /** Joue un bruit de l'interface du thème actif. */
    static void play(Context ctx, String name) {
        Store store = new Store(ctx);
        if (!store.menuSound()) return;
        float vol = store.uiVolume() / 100f;
        if (vol <= 0f) return;
        if (MOVE.equals(name)) {
            // Défilement rapide : un seul tic toutes les 45 ms.
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastMove < 45) return;
            lastMove = now;
        }
        try {
            load(ctx.getApplicationContext(), store.theme());
            for (int i = 0; i < NAMES.length; i++) {
                if (NAMES[i].equals(name) && ids[i] != 0) pool.play(ids[i], vol, vol, 1, 0, 1f);
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
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        poolTheme = theme;
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

    /** Lance (ou reprend) la musique d'accueil du thème actif, en boucle. */
    static void musicStart(Context ctx) {
        wanted = true;
        Store store = new Store(ctx);
        String theme = store.theme();
        float vol = store.musicOn() ? store.musicVolume() / 100f : 0f;
        try {
            if (vol <= 0f) {
                musicStop();
                wanted = true;
                return;
            }
            if (music != null && !theme.equals(musicTheme)) musicStop();
            wanted = true;
            if (music == null) {
                AssetFileDescriptor fd = open(ctx.getApplicationContext(), "music/" + theme + ".ogg");
                if (fd == null) return;
                music = new MediaPlayer();
                music.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                music.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                fd.close();
                music.setLooping(true);
                music.prepare();
                musicTheme = theme;
            }
            music.setVolume(vol, vol);
            if (!music.isPlaying()) music.start();
        } catch (Exception e) {
            musicStop();
        }
    }

    /** Met la musique en pause (jeu lancé, app en arrière-plan). */
    static void musicPause() {
        wanted = false;
        try {
            if (music != null && music.isPlaying()) music.pause();
        } catch (Exception ignored) {
        }
    }

    /** Thème ou volume changé : on recharge si la musique devait jouer. */
    static void refresh(Context ctx) {
        if (wanted) musicStart(ctx);
    }

    static void musicStop() {
        wanted = false;
        if (music != null) {
            try {
                music.release();
            } catch (Exception ignored) {
            }
        }
        music = null;
        musicTheme = null;
    }
}
