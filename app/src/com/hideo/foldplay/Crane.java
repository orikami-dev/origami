package com.hideo.foldplay;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * Le logo Origami : une grue en papier vue de face, en miroir parfait autour du pli central
 * (x = 54, repère 108 × 108), comme le Fold autour de sa charnière.
 * Chaque facette porte une nuance (0 la plus sombre, 4 la plus claire) tirée de l'accent du thème.
 * res/drawable/ic_foreground.xml (icône du lanceur) reprend le même dessin en vert Codec.
 */
final class Crane {
    static final float FOLD = 54f;
    /** Centre vertical du dessin. */
    static final float CY = 51f;
    /** Ligne d'où se lèvent les ailes pendant l'animation de démarrage. */
    static final float WING_BASE = 56f;
    static final float SPAN = 84f;

    // Nuance, puis x, y, x, y…
    static final float[][] WING_L = {
        {1, 21, 24, 48, 51, 42.5f, 57.5f},
        {3, 21, 24, 54, 44, 48, 51},
    };
    static final float[][] LEFT = {
        {0, 47, 66.5f, 17, 43, 51, 72},
        {1, 41.5f, 60.5f, 17, 43, 47, 66.5f},
        {2, 17, 43, 21, 45.5f, 12.5f, 50},
        {2, 54, 44, 40, 60, 54, 62},
        {1, 40, 60, 54, 78, 54, 62},
    };
    static final float[][] WING_R = mirror(WING_L, null);
    /** À droite du pli, le corps prend la lumière : ses deux faces sont plus claires. */
    static final float[][] RIGHT = mirror(LEFT, new int[] {0, 1, 2, 4, 3});
    static final float[] HINGE = {53.4f, 44f, 54.6f, 78f};
    /** Pli d'origine (thème Codec) ; ailleurs le pli suit le thème, voir Theme.fold(). */
    static final int GOLD = 0xFFFFD23F;

    private Crane() {
    }

    private static float[][] mirror(float[][] src, int[] shades) {
        float[][] out = new float[src.length][];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i].clone();
            if (shades != null) out[i][0] = shades[i];
            for (int k = 1; k < out[i].length; k += 2) out[i][k] = 2 * FOLD - out[i][k];
        }
        return out;
    }

    /** Les cinq nuances du papier pour un accent donné. */
    static int[] shades(int accent) {
        return new int[] {
            Theme.darken(accent, 0.62f), Theme.darken(accent, 0.38f), accent | 0xFF000000,
            Theme.lighten(accent, 0.35f), Theme.lighten(accent, 0.62f),
        };
    }

    static void facet(Canvas c, Path path, Paint paint, float[] f, int[] shades) {
        path.reset();
        for (int i = 1; i + 1 < f.length; i += 2) {
            if (i == 1) path.moveTo(f[i], f[i + 1]);
            else path.lineTo(f[i], f[i + 1]);
        }
        path.close();
        paint.setColor(shades[(int) f[0]]);
        c.drawPath(path, paint);
    }

    /** La grue entière, centrée dans ses limites, aux couleurs de l'accent en cours. */
    static final class Logo extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int alpha = 255;
        /** 0 : accent du thème en cours, lu à chaque dessin. */
        int accent;
        /** 0 : pli du thème en cours. */
        int fold;

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float s = Math.min(b.width(), b.height()) / SPAN;
            int[] sh = shades(accent != 0 ? accent : Theme.logo(Theme.CODEC));
            int save = c.saveLayerAlpha(b.left, b.top, b.right, b.bottom, alpha);
            c.translate(b.exactCenterX() - FOLD * s, b.exactCenterY() - CY * s);
            c.scale(s, s);
            for (float[] f : WING_L) facet(c, path, paint, f, sh);
            for (float[] f : WING_R) facet(c, path, paint, f, sh);
            for (float[] f : LEFT) facet(c, path, paint, f, sh);
            for (float[] f : RIGHT) facet(c, path, paint, f, sh);
            paint.setColor(fold != 0 ? fold : Theme.fold());
            c.drawRect(HINGE[0], HINGE[1], HINGE[2], HINGE[3], paint);
            c.restoreToCount(save);
        }

        @Override
        public void setAlpha(int a) {
            alpha = a;
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter f) {
            paint.setColorFilter(f);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
