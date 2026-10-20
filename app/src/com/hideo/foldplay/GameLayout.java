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
    static int portrait(Activity a, int stageHeight, FrameLayout.LayoutParams screen, FrameLayout.LayoutParams deck) {
        // Hauteur réelle de la scène (change au pliage et avec les barres) ; l'écran sert de repli.
        int total = stageHeight > 0 ? stageHeight : a.getResources().getDisplayMetrics().heightPixels;
        int top = Theme.statusBarPx(a);
        int bottom = Theme.navBarPx(a);
        int gap = Math.round(6 * a.getResources().getDisplayMetrics().density);
        // Coupure exactement au milieu de l'écran, là où passe la charnière du pliant tenu en
        // portrait (BOSS, 20/10) : chaque pastille remplit sa moitié, barres système exceptées.
        int mid = total / 2;
        screen.topMargin = top;
        screen.width = -1;
        deck.width = -1;
        screen.height = mid - gap / 2 - top;
        screen.gravity = Gravity.TOP;
        deck.bottomMargin = bottom;
        deck.height = total - bottom - (mid + gap - gap / 2);
        deck.gravity = Gravity.BOTTOM;
        return top;
    }

    /** Plein cadre (paysage ou manette physique) : plus de bandes. */
    static void full(FrameLayout.LayoutParams screen, FrameLayout.LayoutParams deck) {
        screen.width = -1;
        deck.width = -1;
        screen.topMargin = 0;
        screen.height = -1;
        screen.gravity = Gravity.TOP;
        deck.bottomMargin = 0;
        deck.height = -1;
        deck.gravity = Gravity.BOTTOM;
    }
}
