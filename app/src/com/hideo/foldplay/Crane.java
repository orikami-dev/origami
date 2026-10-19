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
        /** Position du reflet, de 0 à 1 (hors de cet intervalle : pas de reflet). */
        float shine = -1f;
        private final Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);

        {
            gloss.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP));
        }

        /**
         * Fait briller la grue de temps en temps pour montrer que c'est un bouton (0.62) : un reflet
         * la traverse, puis une pause. S'arrête seul quand la vue quitte l'écran.
         */
        void animate(final android.view.View host, final long pauseMs) {
            final android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(-0.2f, 1.2f);
            a.setDuration(1100);
            a.setStartDelay(600);
            a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(android.animation.ValueAnimator v) {
                    shine = (Float) v.getAnimatedValue();
                    invalidateSelf();
                    host.invalidate();
                }
            });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator anim) {
                    shine = -1f;
                    host.invalidate();
                    if (host.isAttachedToWindow() && pauseMs > 0) {
                        a.setStartDelay(pauseMs);
                        a.start();
                    }
                }
            });
            host.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
                public void onViewAttachedToWindow(android.view.View v) {
                    if (!a.isStarted()) a.start();
                }

                public void onViewDetachedFromWindow(android.view.View v) {
                    a.cancel();
                }
            });
            if (host.isAttachedToWindow()) a.start();
        }

        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            float s = Math.min(b.width(), b.height()) / SPAN;
            int[] sh = shades(accent != 0 ? accent : Theme.logo(Theme.CODEC));
            int save = c.saveLayerAlpha(b.left, b.top, b.right, b.bottom, alpha);
            c.save();
            c.translate(b.exactCenterX() - FOLD * s, b.exactCenterY() - CY * s);
            c.scale(s, s);
            for (float[] f : WING_L) facet(c, path, paint, f, sh);
            for (float[] f : WING_R) facet(c, path, paint, f, sh);
            for (float[] f : LEFT) facet(c, path, paint, f, sh);
            for (float[] f : RIGHT) facet(c, path, paint, f, sh);
            paint.setColor(fold != 0 ? fold : Theme.fold());
            c.drawRect(HINGE[0], HINGE[1], HINGE[2], HINGE[3], paint);
            c.restore();
            if (shine >= 0f && shine <= 1f) {
                // Reflet qui traverse la grue en diagonale, posé seulement sur ses facettes.
                float band = b.width() * 0.45f;
                float x = b.left - band + (b.width() + 2 * band) * shine;
                gloss.setShader(new android.graphics.LinearGradient(x - band / 2, b.top, x + band / 2, b.bottom,
                        new int[] {0x00FFFFFF, 0xB0FFFFFF, 0x00FFFFFF}, new float[] {0f, 0.5f, 1f},
                        android.graphics.Shader.TileMode.CLAMP));
                c.drawRect(b, gloss);
            }
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
