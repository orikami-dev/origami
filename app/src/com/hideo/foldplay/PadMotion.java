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
            torii(c, b, w, h, m, accent, now, dp);
        } else if (id.equals("sakura")) {
            hanami(c, b, w, h, m, accent, now, dp);
        } else if (id.equals("ambre")) {
            ambre(c, b, w, h, m, now, dp);
        } else if (id.equals("neon")) {
            // Pluie fine, puis les gouttes qui touchent le bitume : un trait qui tombe, deux ronds qui s'élargissent
            // et trois éclaboussures, en cyan et magenta comme les reflets.
            c.save();
            c.translate(b.left, b.top);
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(dp);
            float t = phase(now, 2400);
            for (int i = 0; i < 30; i++) {
                float p = fract(rnd(i, 1) + t * (1 + i % 2)), x = rnd(i, 2) * w, y = p * h;
                P.setColor(a(i % 3 == 0 ? 0xFF3FD7FF : accent, 0x48));
                c.drawLine(x, y, x - 3 * dp, y + 12 * dp, P);
            }
            for (int i = 0; i < 16; i++) {
                float per = 1500f + 900f * rnd(i, 5), tt = now / per + rnd(i, 6);
                int cyc = (int) Math.floor(tt);
                float p = tt - cyc;
                float x = w * (0.04f + 0.92f * rnd(i * 7 + cyc, 1)), y = h * (0.25f + 0.72f * rnd(i * 7 + cyc, 2));
                int col = i % 2 == 1 ? 0xFF5CF4FF : accent;
                float rmax = m * (0.05f + 0.05f * rnd(i, 8));
                if (p < 0.12f) {
                    float k = p / 0.12f;
                    P.setColor(a(0xFFE8E0FF, 0x70));
                    c.drawLine(x + 2 * dp * (1 - k), y - m * 0.12f * (1 - k), x + 2 * dp * (1 - k) - 1.5f * dp, y - m * 0.12f * (1 - k) + 9 * dp, P);
                    continue;
                }
                float k = (p - 0.12f) / 0.88f;
                for (int r = 0; r < 2; r++) {
                    float kk = k - r * 0.18f;
                    if (kk <= 0) continue;
                    float rr = rmax * (float) Math.sqrt(kk);
                    P.setStrokeWidth(dp * (1.2f - 0.4f * r));
                    P.setColor(a(r == 1 ? col : Theme.lighten(col, 0.45f), Math.round((r == 1 ? 0x60 : 0x90) * (1f - kk))));
                    c.drawOval(x - rr, y - rr * 0.32f, x + rr, y + rr * 0.32f, P);
                }
                if (k < 0.3f) {
                    float e = k / 0.3f;
                    P.setStyle(Paint.Style.FILL);
                    for (int sp = 0; sp < 3; sp++) {
                        double ang = -Math.PI * (0.2 + 0.3 * sp);
                        float d = rmax * 0.5f * e;
                        P.setColor(a(Theme.lighten(col, 0.5f), Math.round(0xB0 * (1f - e))));
                        c.drawCircle(x + (float) Math.cos(ang) * d, y + (float) Math.sin(ang) * d * 0.9f - m * 0.03f * (float) Math.sin(Math.PI * e), 1.1f * dp, P);
                    }
                    P.setStyle(Paint.Style.STROKE);
                }
            }
            c.restore();
        } else if (id.equals("jade")) {
            // Lanternes qui respirent, puis lucioles qui flottent dans la brume.
            float[] lp = Facets.jadePadLanterns(w, h);
            for (int i = 0; i < lp.length / 4; i++) {
                float br = 0.55f + 0.45f * (float) Math.sin(now / 1000.0 * 1.3 + i * 1.1), r = lp[i * 4 + 2] * 5;
                P.setShader(new RadialGradient(b.left + lp[i * 4], b.top + lp[i * 4 + 1], r, a(0xFFFFC478, Math.round(0x50 * br)), a(0xFFFFC478, 0), Shader.TileMode.CLAMP));
                c.drawCircle(b.left + lp[i * 4], b.top + lp[i * 4 + 1], r, P);
            }
            P.setShader(null);
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
            radar(c, b, w, h, m, accent, now, dp);
        }
        P.setStyle(Paint.Style.FILL);
    }

    /** Angle du faisceau : un tour complet, la moitié cachée sous la manette passe vite. */
    private static float radarAngle(float t) {
        return t < 0.8f ? (float) (-Math.PI + Math.PI * (t / 0.8f)) : (float) (Math.PI * ((t - 0.8f) / 0.2f));
    }

    private static final android.graphics.RectF OVAL = new android.graphics.RectF();

    /**
     * Radar (0.66.21) : un seul faisceau, qui part du radar au pied de la façade (celui des anneaux) et tourne
     * avec sa traîne. Des échos répartis sur toute la façade s'allument en rouge à son passage puis s'éteignent ;
     * un sur quatre clignote seul.
     */
    private static void radar(Canvas c, RectF b, float w, float h, float m, int accent, long now, float dp) {
        int light = Theme.lighten(accent, 0.35f), pale = Theme.lighten(accent, 0.62f);
        float cx = b.left + w * 0.5f, cy = b.top + h * 1.04f, R = (float) Math.hypot(w * 0.5f, h * 1.04f) * 1.02f;
        float t = phase(now, 5000), ang = radarAngle(t), trail = 1.1f;
        c.save();
        c.clipRect(b);
        P.setStyle(Paint.Style.FILL);
        OVAL.set(cx - R, cy - R, cx + R, cy + R);
        int slices = 28;
        for (int s = 0; s < slices; s++) {
            float a0 = ang - trail * s / slices, a1 = ang - trail * (s + 1) / slices;
            P.setColor(a(light, Math.round(0x2A * (float) Math.pow(1f - s / (float) slices, 2))));
            c.drawArc(OVAL, (float) Math.toDegrees(a1), (float) Math.toDegrees(a0 - a1), true, P);
        }
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(1.3f * dp);
        P.setColor(a(pale, 0xA0));
        c.drawLine(cx, cy, cx + (float) Math.cos(ang) * R, cy + (float) Math.sin(ang) * R, P);
        P.setStyle(Paint.Style.FILL);
        int red = 0xFFFF3B3B;
        for (int i = 0; i < 24; i++) {
            float ba = (float) (-Math.PI * (0.05 + 0.9 * rnd(i, 1))), d = m * (0.2f + 1.05f * (float) Math.sqrt(rnd(i, 2)));
            float x = cx + (float) Math.cos(ba) * d, y = cy + (float) Math.sin(ba) * d;
            if (x < b.left + 4 || x > b.right - 4 || y < b.top + 4 || y > b.bottom - 6) continue;
            float tb = (float) ((ba + Math.PI) / Math.PI * 0.8), age = fract(t - tb);
            float lit = (float) Math.exp(-age * 5.5f);
            if (i % 4 == 0 && Math.sin(now / 1000.0 * Math.PI * 2 * (0.8 + 0.4 * rnd(i, 3)) + i) > 0.2) lit = Math.max(lit, 0.8f);
            float r = (1.6f + 0.8f * rnd(i, 4)) * dp;
            P.setColor(a(red, 0x30 + Math.round(0xC0 * lit)));
            c.drawCircle(x, y, r, P);
            P.setShader(new RadialGradient(x, y, r * 5, a(red, Math.round(0x70 * lit)), a(red, 0), Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r * 5, P);
            P.setShader(null);
            if (age < 0.16f) {
                float p = age / 0.16f;
                P.setStyle(Paint.Style.STROKE);
                P.setStrokeWidth(dp);
                P.setColor(a(red, Math.round(0x90 * (1f - p))));
                c.drawCircle(x, y, r * (1.5f + 5 * p), P);
                P.setStyle(Paint.Style.FILL);
            }
        }
        c.restore();
    }

    /**
     * Ambre, métropole (0.66.21) : projecteurs qui balaient le ciel depuis les toits des deux plus hautes tours,
     * feux d'avion rouges qui clignotent chacun à leur rythme, lumière qui court sur la plate-forme, quelques
     * fenêtres qui s'allument et s'éteignent.
     */
    private static void ambre(Canvas c, RectF b, float w, float h, float m, long now, float dp) {
        float[] pts = Facets.megapolePts(w, h);
        float s = now / 1000f;
        c.save();
        c.clipRect(b);
        c.translate(b.left, b.top);
        P.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 2; i++) {
            float rx = pts[i == 0 ? 0 : 10], ry = pts[i == 0 ? 1 : 11];
            float ang = (float) (-Math.PI / 2 + (i == 1 ? -1 : 1) * (0.25 + 0.35 * Math.sin(s * Math.PI * 2 / 12 + i * 1.7))), L = h * 1.2f, sp = 0.06f;
            P.setShader(new android.graphics.LinearGradient(rx, ry, rx + (float) Math.cos(ang) * L, ry + (float) Math.sin(ang) * L,
                    a(0xFFFFE2A0, 0x30), a(0xFFFFE2A0, 0), Shader.TileMode.CLAMP));
            android.graphics.Path pa = new android.graphics.Path();
            pa.moveTo(rx, ry);
            pa.lineTo(rx + (float) Math.cos(ang - sp) * L, ry + (float) Math.sin(ang - sp) * L);
            pa.lineTo(rx + (float) Math.cos(ang + sp) * L, ry + (float) Math.sin(ang + sp) * L);
            pa.close();
            c.drawPath(pa, P);
        }
        P.setShader(null);
        for (int i = 0; i < 6; i++) {
            float ah = m * (0.05f + 0.04f * rnd(i, 9)), x = pts[i * 2], y = pts[i * 2 + 1] - ah;
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(1.2f * dp);
            P.setColor(0xF2050302);
            c.drawLine(pts[i * 2], pts[i * 2 + 1], x, y, P);
            P.setStyle(Paint.Style.FILL);
            beacon(c, x, y, rnd(i, 7), s, dp);
        }
        float tcx = pts[12], yd = pts[13], dw = pts[14], dh = pts[15];
        beacon(c, tcx - dw * 0.8f, yd - dh, 0f, s, dp);
        beacon(c, tcx + dw * 0.8f, yd - dh, 0.5f, s, dp);
        for (int k = 0; k < 10; k++) {
            float x = tcx - dw * 0.85f + k * dw * 0.18f, pl = 0.5f + 0.5f * (float) Math.sin(s * 2 - k * 0.6f);
            P.setColor(a(0xFFFFE2A0, 0x60 + Math.round(0x80 * pl)));
            c.drawRect(x, yd - dh * 0.65f, x + dw * 0.09f, yd - dh * 0.23f, P);
        }
        for (int i = 0; i < 10; i++) {
            int cyc = (int) Math.floor(s / 3 + rnd(i, 1));
            if (rnd(i, cyc) <= 0.5f) continue;
            float x = i % 2 == 1 ? w * (0.8f + 0.17f * rnd(i, 2)) : w * (0.02f + 0.17f * rnd(i, 2)), y = h * (0.2f + 0.6f * rnd(i, 3));
            P.setColor(a(0xFFFFF0D0, 0x70));
            c.drawRect(x, y, x + m * 0.012f, y + m * 0.018f, P);
        }
        c.restore();
    }

    /** Feu d'avion : halo rouge et point clair qui s'allume un tiers du temps. */
    private static void beacon(Canvas c, float x, float y, float ph, float s, float dp) {
        float on = fract(s * 0.8f + ph) < 0.35f ? 1f : 0.12f;
        P.setShader(new RadialGradient(x, y, 7 * dp, a(0xFFFF3B3B, Math.round(0xD0 * on)), a(0xFFFF3B3B, 0), Shader.TileMode.CLAMP));
        c.drawCircle(x, y, 7 * dp, P);
        P.setShader(null);
        P.setColor(a(0xFFFFD0D0, Math.round(0xF0 * on)));
        c.drawCircle(x, y, 1.4f * dp, P);
    }

    /**
     * Torii (0.66.21) : deux lanternes de papier sous le nuki qui se balancent et respirent, braises qui montent,
     * feuilles d'érable qui tombent en tournant.
     */
    private static void torii(Canvas c, RectF b, float w, float h, float m, int accent, long now, float dp) {
        float[] T = Facets.toriiPadPts(w, h);
        float s = now / 1000f, nyb = T[4];
        int gold = 0xFFFFB347;
        c.save();
        c.clipRect(b);
        c.translate(b.left, b.top);
        for (int i = 0; i < 2; i++) {
            float x = w * (i == 0 ? 0.42f : 0.58f), y = nyb + m * 0.11f, r = m * 0.042f;
            float sw = (float) Math.sin(s * 1.1f + i * 1.7f) * 0.06f, lx = x + (float) Math.sin(sw) * (y - nyb);
            float br = 0.6f + 0.4f * (float) Math.sin(s * 1.7f + i);
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(1.2f * dp);
            P.setColor(0xE60A0505);
            c.drawLine(x, nyb, lx, y - r, P);
            P.setStyle(Paint.Style.FILL);
            P.setShader(new RadialGradient(lx, y, r * 4.5f, a(gold, Math.round(0x60 * br)), a(gold, 0), Shader.TileMode.CLAMP));
            c.drawCircle(lx, y, r * 4.5f, P);
            P.setShader(null);
            P.setColor(a(0xFFE0452B, 0xD0));
            c.drawOval(lx - r * 0.75f, y - r, lx + r * 0.75f, y + r, P);
            P.setColor(a(Theme.lighten(gold, 0.5f), Math.round(0x90 * br)));
            c.drawOval(lx - r * 0.32f, y - r * 0.6f, lx + r * 0.32f, y + r * 0.6f, P);
            P.setColor(0xF20A0505);
            c.drawRect(lx - r * 0.45f, y - r * 1.12f, lx + r * 0.45f, y - r * 0.88f, P);
            c.drawRect(lx - r * 0.45f, y + r * 0.88f, lx + r * 0.45f, y + r * 1.12f, P);
        }
        float t = phase(now, 10000);
        for (int i = 0; i < 18; i++) {
            float p = fract(rnd(i, 1) + t * (1 + i % 2));
            float x = rnd(i, 2) * w + 10 * dp * (float) Math.sin(TAU * (p + rnd(i, 3))), y = h - p * h;
            P.setColor(a(i % 3 == 0 ? 0xFFFFC46B : accent, Math.round(0xB0 * (1f - p))));
            c.drawCircle(x, y, (1f + (i % 3) * 0.6f) * dp, P);
        }
        float t2 = phase(now, 14000), s2 = m * 0.014f;
        android.graphics.Path leaf = new android.graphics.Path();
        for (int i = 0; i < 7; i++) {
            float p = fract(rnd(i, 5) + t2 * (1 + i % 2));
            float x = w * fract(rnd(i, 6) + 0.15f * (float) Math.sin(TAU * (p * 2 + rnd(i, 7)))), y = p * h;
            c.save();
            c.translate(x, y);
            c.rotate(360f * (rnd(i, 8) + p * 3));
            leaf.reset();
            for (int k = 0; k < 5; k++) {
                double ang = -Math.PI / 2 + (k - 2) * 0.55;
                leaf.moveTo(0, 0);
                leaf.lineTo((float) Math.cos(ang - 0.25) * s2, (float) Math.sin(ang - 0.25) * s2);
                leaf.lineTo((float) Math.cos(ang) * s2 * 2, (float) Math.sin(ang) * s2 * 2);
                leaf.lineTo((float) Math.cos(ang + 0.25) * s2, (float) Math.sin(ang + 0.25) * s2);
                leaf.close();
            }
            P.setColor(a(i % 2 == 1 ? accent : gold, 0x90));
            c.drawPath(leaf, P);
            c.restore();
        }
        c.restore();
    }

    /**
     * Hanami (0.66.23) : sous la ligne d'eau, fines ondulations qui glissent et s'espacent vers le bas, plus claires
     * sous la lune ; pétales qui flottent avec un rond qui s'élargit, d'autres qui tombent des branches.
     */
    private static void hanami(Canvas c, RectF b, float w, float h, float m, int accent, long now, float dp) {
        float s = now / 1000f, bank = h * 0.5f, mx = w * 0.5f, mr = m * 0.156f, q = m * 0.0595f, lw = Math.max(1f, Math.max(w, h) / 900f);
        int light = Theme.lighten(accent, 0.35f), pale = Theme.lighten(accent, 0.62f);
        android.graphics.Path path = new android.graphics.Path();
        c.save();
        c.clipRect(b);
        c.translate(b.left, b.top);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 26; i++) {
            float t = (i + 0.5f) / 26, y = bank + (h - bank) * (float) Math.pow(t, 1.6) + q * 0.15f;
            float amp = q * (0.04f + 0.16f * t), wl = q * (1.6f + 5f * t), ph = s * (0.25f + 0.35f * t) + i * 1.7f, seg = wl * 1.4f;
            P.setStrokeWidth(lw * (0.8f + 1.2f * t));
            float x0 = -seg + fract(rnd(i, 1) + s * 0.02f * (1 + t)) * seg;
            for (int n = 0; x0 < w + seg && n < 200; n++) {
                int k = (int) Math.floor(x0 / seg);
                float len = seg * (0.45f + 0.4f * rnd(i, k + 9)), cx = x0 + len / 2;
                float near = Math.max(0f, 1f - Math.abs(cx - mx) / (mr * (1.2f + 3.2f * t)));
                int col = near > 0.2f ? pale : (i % 3 == 0 ? light : accent);
                P.setColor(a(col, Math.round(0x10 + 0x18 * t + 0x70 * near * near * (0.6f + 0.4f * (float) Math.sin(s * 3 + i * 2.3f + x0)))));
                path.reset();
                for (int j = 0; j <= 10; j++) {
                    float x = x0 + len * j / 10f, yy = y + (float) Math.sin(x / wl * TAU + ph) * amp;
                    if (j == 0) path.moveTo(x, yy);
                    else path.lineTo(x, yy);
                }
                c.drawPath(path, P);
                x0 += seg * (1.15f + 0.5f * rnd(i, k + 50));
            }
        }
        for (int i = 0; i < 14; i++) {
            float t = rnd(i, 2), y = bank + (h - bank) * (0.08f + 0.9f * t), x = w * fract(rnd(i, 3) + s * 0.006f * (1 + t));
            float s2 = q * (0.12f + 0.12f * t) * (0.8f + 0.4f * rnd(i, 4)), rp = fract(s / 4 + rnd(i, 6)), rr = s2 * (1.2f + 2.5f * rp);
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(lw);
            P.setColor(a(light, Math.round(0x40 * (1f - rp))));
            c.drawOval(x - rr, y - rr * 0.3f, x + rr, y + rr * 0.3f, P);
            P.setStyle(Paint.Style.FILL);
            P.setColor(a(i % 2 == 1 ? light : 0xFFFFF0F7, 0x9A));
            c.save();
            c.rotate((float) Math.toDegrees(rnd(i, 5) * TAU + 0.3 * Math.sin(s * 0.7f + i)), x, y);
            c.drawOval(x - s2, y - s2 * 0.55f, x + s2, y + s2 * 0.55f, P);
            c.restore();
        }
        float tt = phase(now, 14000);
        for (int i = 0; i < 14; i++) {
            float p = fract(rnd(i, 11) + tt * (1 + i % 2)), x = w * fract(rnd(i, 12) * 0.6f + p * 0.4f), y = p * h;
            c.save();
            c.rotate(360f * (rnd(i, 13) + p * 2), x, y);
            P.setColor(a(light, 0x80));
            c.drawOval(x - 3.5f * dp, y - 2f * dp, x + 3.5f * dp, y + 2f * dp, P);
            c.restore();
        }
        c.restore();
    }
}
