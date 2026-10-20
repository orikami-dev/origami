package com.hideo.foldplay;

import android.app.Activity;
import android.view.Gravity;
import android.widget.FrameLayout;

/**
 * Disposition portrait des moteurs à surface propre (PS3, Xbox 360) : une bande « barre d'état » en
 * haut (l'ATH s'y pose), puis deux pastilles de même hauteur, l'écran du jeu et la manette, séparées
 * au milieu de l'écran, puis une bande « barre de navigation » symétrique en bas.
 */
final class GameLayout {
    private GameLayout() {
    }

    /** Bande du haut utilisée (px), pour l'ATH. */
    static int portrait(Activity a, FrameLayout.LayoutParams screen, FrameLayout.LayoutParams deck) {
        int total = a.getResources().getDisplayMetrics().heightPixels;
        int top = Theme.statusBarPx(a);
        int bottom = Math.max(top, Theme.navBarPx(a));
        int gap = Math.round(6 * a.getResources().getDisplayMetrics().density);
        int pill = (total - top - bottom - gap) / 2;
        screen.topMargin = top;
        screen.height = pill;
        screen.gravity = Gravity.TOP;
        deck.bottomMargin = bottom;
        deck.height = pill;
        deck.gravity = Gravity.BOTTOM;
        return top;
    }

    /** Plein cadre (paysage ou manette physique) : plus de bandes. */
    static void full(FrameLayout.LayoutParams screen, FrameLayout.LayoutParams deck) {
        screen.topMargin = 0;
        screen.height = -1;
        screen.gravity = Gravity.TOP;
        deck.bottomMargin = 0;
        deck.height = -1;
        deck.gravity = Gravity.BOTTOM;
    }
}
