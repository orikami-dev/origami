package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Démarrage : la grue de l'icône arrive pliée en deux le long de son pli central, s'ouvre comme le
 * Fold, déploie ses ailes, puis vient vers l'utilisateur et s'efface sur l'accueil.
 * Mêmes facettes que le logo (Crane), aux couleurs du thème.
 */
final class OrigamiSplash extends View {
    interface Done {
        void run();
    }

    /** Nuances du papier, tirées de l'accent du thème en cours. */
    private final int[] shades = Crane.shades(Theme.logo(Theme.CODEC));

    private static final long UNFOLD = 700, WINGS = 450, HOLD = 250, OPEN = 450;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float dp;
    private long start;
    private Done done;
    private boolean finished;

    OrigamiSplash(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
    }

    void play(Done d) {
        done = d;
        start = 0;
        // « Supprimer les animations » : la grue s'affiche ouverte, sans mouvement.
        if (android.os.Build.VERSION.SDK_INT >= 26 && !android.animation.ValueAnimator.areAnimatorsEnabled()) {
            start = -1;
            postDelayed(new Runnable() { public void run() { finish(); } }, 600);
        }
        invalidate();
    }

    /** Un appui passe l'animation. */
    void skip() {
        finish();
    }

    private void finish() {
        if (finished) return;
        finished = true;
        if (done != null) done.run();
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = android.os.SystemClock.uptimeMillis();
        if (start == 0) start = now;
        long t = start < 0 ? UNFOLD + WINGS : now - start;
        float rise = ease(clamp(t / (float) UNFOLD));
        float open = clamp((t - UNFOLD - WINGS - HOLD) / (float) OPEN);

        // 0.66.14 : la grue en vol arrive du bas en battant des ailes, se pose au centre, puis
        // grossit vers l'utilisateur et s'efface sur l'accueil.
        float size = Math.min(getWidth(), getHeight()) * 0.6f * (1f + open * open * 2.2f);
        float x = getWidth() / 2f;
        float y = getHeight() / 2f - 24 * dp + (1f - rise) * 36 * dp;
        float flap = start < 0 ? 0.7f : 0.5f + 0.5f * (float) Math.cos(t / 110.0);
        int alpha = Math.round(255 * rise * (1f - open));
        Facets.crane(c, paint, path, shades, Theme.fold(), x, y, size, -0.12f * (1f - rise), flap, alpha, false);

        if (open >= 1f) {
            finish();
            return;
        }
        if (start >= 0) postInvalidateOnAnimation();
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static float ease(float v) {
        return 1f - (1f - v) * (1f - v) * (1f - v);
    }
}
