package com.hideo.foldplay;

import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Touche FORMAT des manettes : un appui passe au format suivant, un appui long le verrouille
 * (ou le déverrouille) pour éviter tout appui involontaire. Le choix est gardé par console.
 */
final class ScreenFormat {
    private ScreenFormat() {
    }

    /** Formats proposés aux moteurs à surface propre (PS3, Xbox 360). */
    static final String[] KEYS = {"core", "16:9", "4:3", "stretch"};
    static final String[] LABELS = {"Auto", "16:9", "4:3", "Étirer"};

    /** Texte porté par la touche, mis à jour à chaque changement. */
    static volatile String caption = "FORMAT";

    static String label(String mode) {
        if (mode.equals("integer")) return "Pixels";
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(mode)) return LABELS[i];
        return mode;
    }

    static void refreshCaption(Store store, String system) {
        caption = store.formatLocked() ? "[" + label(store.displayMode(system)) + "]" : label(store.displayMode(system));
    }

    /** Ce que la touche demande à l'écran qui l'héberge. */
    interface Host {
        void applyFormat();

        void say(String text);

        void refreshKeys();
    }

    /** Un appui court change de format, un appui long verrouille. */
    static final class Key {
        private static final long LONG_MS = 600;
        private final Store store;
        private final String system;
        private final String[] keys;
        private final Host host;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private boolean fired;
        private final Runnable lock = new Runnable() {
            public void run() {
                fired = true;
                store.setFormatLocked(!store.formatLocked());
                refreshCaption(store, system);
                host.refreshKeys();
                host.say(store.formatLocked() ? "Format verrouillé" : "Format déverrouillé");
            }
        };

        Key(Store store, String system, String[] keys, Host host) {
            this.store = store;
            this.system = system;
            this.keys = keys;
            this.host = host;
            refreshCaption(store, system);
        }

        void press(boolean down) {
            if (down) {
                fired = false;
                handler.postDelayed(lock, LONG_MS);
                return;
            }
            handler.removeCallbacks(lock);
            if (fired) return;
            if (store.formatLocked()) {
                host.say("Format verrouillé · appui long pour déverrouiller");
                return;
            }
            String cur = store.displayMode(system);
            int i = 0;
            for (int k = 0; k < keys.length; k++) if (keys[k].equals(cur)) i = k;
            String next = keys[(i + 1) % keys.length];
            store.setDisplayMode(system, next);
            refreshCaption(store, system);
            host.applyFormat();
            host.refreshKeys();
            host.say("Format : " + label(next));
        }
    }

    /**
     * Cadre la surface d'un moteur dans sa zone (w × h pixels) : Auto et Étirer la remplissent,
     * 16:9 et 4:3 la ramènent à ce rapport. Renvoie vrai si le moteur doit remplir sa surface.
     */
    static boolean fit(View screen, int w, int h, int gravity, String mode) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) screen.getLayoutParams();
        int sw = w, sh = h;
        float ratio = mode.equals("16:9") ? 16f / 9f : mode.equals("4:3") ? 4f / 3f : 0;
        if (ratio > 0 && w > 0 && h > 0) {
            if ((float) w / h > ratio) sw = Math.round(h * ratio);
            else sh = Math.round(w / ratio);
        }
        boolean fill = !mode.equals("core");
        if (lp.width != sw || lp.height != sh || lp.gravity != gravity) {
            lp.width = sw;
            lp.height = sh;
            lp.gravity = gravity;
            screen.setLayoutParams(lp);
        }
        return fill;
    }

    /** Zone laissée à l'écran : toute la scène en paysage, sa moitié haute en portrait sans manette. */
    static int gravityFor(boolean full) {
        return full ? Gravity.CENTER : Gravity.TOP | Gravity.CENTER_HORIZONTAL;
    }
}
