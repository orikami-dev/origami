package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
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

    private static final float FOLD = Crane.FOLD;
    private static final float CY = Crane.CY;
    /** Base des ailes : elles se lèvent depuis cette ligne. */
    private static final float WING_BASE = Crane.WING_BASE;

    /** Nuances du papier, tirées de l'accent du thème en cours. */
    private final int[] shades = Crane.shades(Theme.logo(Theme.CODEC));

    private static final long UNFOLD = 700, WINGS = 450, HOLD = 250, OPEN = 450;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Camera camera = new Camera();
    private final Matrix matrix = new Matrix();
    private final float dp;
    private long start;
    private Done done;
    private boolean finished;

    OrigamiSplash(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        camera.setLocation(0, 0, -12);
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
        float unfold = ease(clamp(t / (float) UNFOLD));
        float wings = ease(clamp((t - UNFOLD) / (float) WINGS));
        float open = clamp((t - UNFOLD - WINGS - HOLD) / (float) OPEN);

        float size = Math.min(getWidth(), getHeight()) * 0.55f;
        float s = size / Crane.SPAN * (1f + open * open * 2.2f);
        float px = getWidth() / 2f;
        float py = getHeight() / 2f - 24 * dp;
        int alpha = Math.round(255 * (1f - open));

        // Moitié droite, à plat.
        c.save();
        c.translate(px - FOLD * s, py - CY * s);
        c.scale(s, s);
        for (float[] f : Crane.WING_R) facet(c, f, wings, alpha, false);
        for (float[] f : Crane.RIGHT) facet(c, f, 1f, alpha, false);
        c.restore();

        // Moitié gauche : tourne autour du pli, de repliée sur la droite (-180°) à ouverte (0°).
        float angle = -180f * (1f - unfold);
        boolean back = angle < -90f;
        camera.save();
        // Perspective douce, quelle que soit la taille de l'écran (la caméra compte en pouces de 72 px).
        camera.setLocation(0, 0, -Math.max(8f, size * 3f / 72f));
        camera.rotateY(angle);
        camera.getMatrix(matrix);
        camera.restore();
        matrix.preTranslate(-px, -py);
        matrix.postTranslate(px, py);
        c.save();
        c.concat(matrix);
        c.translate(px - FOLD * s, py - CY * s);
        c.scale(s, s);
        for (float[] f : Crane.WING_L) facet(c, f, wings, alpha, back);
        for (float[] f : Crane.LEFT) facet(c, f, 1f, alpha, back);
        c.restore();

        // Pli central aux couleurs du thème, le dernier posé.
        c.save();
        c.translate(px - FOLD * s, py - CY * s);
        c.scale(s, s);
        paint.setColor(Theme.fold());
        paint.setAlpha(alpha);
        c.drawRect(Crane.HINGE[0], Crane.HINGE[1], Crane.HINGE[2], Crane.HINGE[3], paint);
        c.restore();

        if (open >= 1f) {
            finish();
            return;
        }
        if (start >= 0) postInvalidateOnAnimation();
    }

    /** Une facette ; une aile se lève de sa base (k de 0 à 1) ; vue de dos, le papier est plus sombre. */
    private void facet(Canvas c, float[] f, float k, int alpha, boolean back) {
        if (k <= 0.01f) return;
        path.reset();
        for (int i = 1; i + 1 < f.length; i += 2) {
            float x = f[i];
            float y = f[i + 1];
            if (k < 1f) y = WING_BASE + (y - WING_BASE) * k;
            if (i == 1) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        int color = shades[(int) f[0]];
        paint.setColor(back ? Theme.darken(color, 0.55f) : color);
        paint.setAlpha(alpha);
        c.drawPath(path, paint);
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static float ease(float v) {
        return 1f - (1f - v) * (1f - v) * (1f - v);
    }
}
