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

    private static final long UNFOLD = 900, WINGS = 500, HOLD = 450, OPEN = 450;

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
        // 0.66.17 (choix F de BOSS) : les plis se tracent au trait, les facettes se remplissent, un reflet
        // traverse la grue, puis elle s'efface sur l'accueil. Grue au centre, immobile.
        float draw = clamp(t / (float) UNFOLD);
        float fillK = ease(clamp((t - UNFOLD) / (float) WINGS));
        float open = clamp((t - UNFOLD - WINGS - HOLD) / (float) OPEN);
        float size = Math.min(getWidth(), getHeight()) * 0.6f * (1f + open * 0.15f);
        float s = size / 84f, cx = getWidth() / 2f, cy = getHeight() / 2f - 24 * dp;
        int alpha = Math.round(255 * (1f - open));
        int layer = c.saveLayer(null, null);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(Math.max(1.5f, 1.4f * s));
        for (int i = 0; i < Crane.FLY.length; i++) {
            float[] f = Crane.FLY[i];
            path.reset();
            float per = 0, lx = 0, ly = 0;
            for (int k = 1; k + 1 < f.length; k += 2) {
                float x = cx + (f[k] - Crane.FLY_CX) * s, y = cy + (f[k + 1] - Crane.FLY_CY) * s;
                if (k == 1) path.moveTo(x, y);
                else { path.lineTo(x, y); per += (float) Math.hypot(x - lx, y - ly); }
                lx = x;
                ly = y;
            }
            path.close();
            per += (float) Math.hypot(cx + (f[1] - Crane.FLY_CX) * s - lx, cy + (f[2] - Crane.FLY_CY) * s - ly);
            int col = i == 0 ? Theme.fold() : shades[(int) f[0]];
            if (fillK > 0f) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(col);
                paint.setAlpha(Math.round(alpha * fillK));
                c.drawPath(path, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(fillK > 0f ? Theme.darken(col, 0.45f) : shades[3]);
            paint.setAlpha(alpha);
            paint.setPathEffect(draw < 1f ? new android.graphics.DashPathEffect(new float[] {per * draw, per}, 0) : null);
            c.drawPath(path, paint);
            paint.setPathEffect(null);
        }
        paint.setStyle(Paint.Style.FILL);
        // Reflet qui traverse la grue pendant la pause.
        float g = clamp((t - UNFOLD - WINGS + 150) / (float) (HOLD + 300));
        if (g > 0f && g < 1f && open <= 0f) {
            float band = size * 0.4f, bx = cx - size + 2 * size * g;
            paint.setShader(new android.graphics.LinearGradient(bx - band, cy - size / 2, bx + band, cy + size / 2,
                    new int[] {0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null, android.graphics.Shader.TileMode.CLAMP));
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP));
            c.drawRect(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, paint);
            paint.setXfermode(null);
            paint.setShader(null);
        }
        c.restoreToCount(layer);

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
