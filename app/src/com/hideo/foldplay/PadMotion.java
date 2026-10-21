package com.hideo.foldplay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Ce qui bouge sur la façade de la manette (0.66.19), par-dessus le motif fixe de PadSkins, un par thème.
 * Argent : ondes autour de la lune (choix H de BOSS) ; les autres reprennent le geste de leur fond.
 * Tout reste discret et passe sous les touches ; rien ne bouge avec « Réduire les animations ».
 */
final class PadMotion {
    private PadMotion() {
    }

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final float TAU = (float) (Math.PI * 2);

    private static int a(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static float rnd(int i, int k) {
        double s = Math.sin(i * 127.1 + k * 311.7) * 43758.5453;
        return (float) (s - Math.floor(s));
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    /** Phase de 0 à 1 sur period millisecondes. */
    private static float phase(long now, long period) {
        return (now % period) / (float) period;
    }

    static void draw(Canvas c, RectF b, String id, int accent, float dp) {
        long now = android.os.SystemClock.uptimeMillis();
        float w = b.width(), h = b.height(), m = Math.min(w, h), cx = b.centerX(), cy = b.centerY();
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        if (id.equals("argent")) {
            // Reflet sur l'eau : quatre ondes partent de la lune et s'aplatissent en s'éloignant.
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(1.5f * dp);
            float t = phase(now, 9000), r0 = m * 0.16f;
            for (int i = 0; i < 4; i++) {
                float p = fract(t + i / 4f), r = r0 * (1f + p * 2.6f);
                P.setColor(a(0xFFD2DAE6, Math.round(0x60 * (1f - p))));
                c.drawOval(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.55f, P);
            }
        } else if (id.equals("arctique")) {
            // Neige fine qui tombe en se balançant.
            float t = phase(now, 12000);
            for (int i = 0; i < 26; i++) {
                float y = b.top + fract(rnd(i, 1) + t * (1 + i % 3)) * h;
                float x = b.left + rnd(i, 2) * w + 6 * dp * (float) Math.sin(TAU * (t * 2 + rnd(i, 3)));
                P.setColor(a(0xFFFFFFFF, 0x50 + (i % 3) * 0x18));
                c.drawCircle(x, y, (0.8f + (i % 3) * 0.5f) * dp, P);
            }
        } else if (id.equals("ecarlate")) {
            // Braises qui montent et s'éteignent.
            float t = phase(now, 10000);
            for (int i = 0; i < 18; i++) {
                float p = fract(rnd(i, 1) + t * (1 + i % 2));
                float x = b.left + rnd(i, 2) * w + 10 * dp * (float) Math.sin(TAU * (p + rnd(i, 3)));
                float y = b.bottom - p * h;
                P.setColor(a(i % 3 == 0 ? 0xFFFFC46B : accent, Math.round(0xB0 * (1f - p))));
                c.drawCircle(x, y, (1f + (i % 3) * 0.6f) * dp, P);
            }
        } else if (id.equals("sakura")) {
            // Pétales qui glissent en diagonale en tournant.
            float t = phase(now, 14000);
            for (int i = 0; i < 12; i++) {
                float p = fract(rnd(i, 1) + t * (1 + i % 2));
                float x = b.left + fract(rnd(i, 2) + p * 0.6f) * w, y = b.top + p * h;
                float rot = 360f * (rnd(i, 4) + p * 2);
                c.save();
                c.rotate(rot, x, y);
                P.setColor(a(accent, 0x70));
                c.drawOval(x - 3.5f * dp, y - 2f * dp, x + 3.5f * dp, y + 2f * dp, P);
                c.restore();
            }
        } else if (id.equals("ambre")) {
            // Traînées de phares qui filent en bas, comme une avenue la nuit.
            float t = phase(now, 6000);
            for (int i = 0; i < 6; i++) {
                float p = fract(rnd(i, 1) + t * (1 + i % 3)), y = b.bottom - h * (0.06f + 0.05f * (i % 3));
                float x = b.left - w * 0.2f + p * w * 1.4f, len = w * 0.12f;
                int col = i % 2 == 0 ? 0xFFFFD78B : 0xFFFF6B4E;
                P.setShader(new android.graphics.LinearGradient(x - len, 0, x, 0, a(col, 0), a(col, 0x90), Shader.TileMode.CLAMP));
                c.drawRect(x - len, y - 0.8f * dp, x, y + 0.8f * dp, P);
            }
            P.setShader(null);
        } else if (id.equals("neon")) {
            // Pluie fine, reflets magenta et cyan.
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(dp);
            float t = phase(now, 2400);
            for (int i = 0; i < 30; i++) {
                float p = fract(rnd(i, 1) + t * (1 + i % 2)), x = b.left + rnd(i, 2) * w, y = b.top + p * h;
                P.setColor(a(i % 3 == 0 ? 0xFF3FD7FF : accent, 0x48));
                c.drawLine(x, y, x - 3 * dp, y + 12 * dp, P);
            }
        } else if (id.equals("jade")) {
            // Lucioles qui flottent dans la brume.
            float t = phase(now, 16000);
            for (int i = 0; i < 12; i++) {
                float x = b.left + w * fract(rnd(i, 1) + 0.08f * (float) Math.sin(TAU * (t + rnd(i, 3))));
                float y = b.top + h * fract(rnd(i, 2) + 0.06f * (float) Math.cos(TAU * (t * 2 + rnd(i, 4))));
                float glow = 0.5f + 0.5f * (float) Math.sin(TAU * (t * 3 + i * 0.37f));
                float r = 6 * dp;
                P.setShader(new RadialGradient(x, y, r, a(0xFFCFFFB0, Math.round(0x90 * glow)), a(0xFFCFFFB0, 0), Shader.TileMode.CLAMP));
                c.drawCircle(x, y, r, P);
            }
            P.setShader(null);
        } else if (!id.equals("pad")) {
            // Radar : un balayage tourne autour du centre, avec sa traîne.
            float t = phase(now, 6000), r = Math.max(w, h) * 0.6f, ang = 360f * t;
            P.setShader(new android.graphics.SweepGradient(cx, cy, new int[] {a(accent, 0), a(accent, 0), a(accent, 0x30)},
                    new float[] {0f, 0.85f, 1f}));
            c.save();
            c.rotate(ang - 90f, cx, cy);
            c.drawCircle(cx, cy, r, P);
            c.restore();
            P.setShader(null);
        }
        P.setStyle(Paint.Style.FILL);
    }
}
