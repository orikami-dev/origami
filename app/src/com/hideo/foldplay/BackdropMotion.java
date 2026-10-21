package com.hideo.foldplay;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

/**
 * Ce qui bouge sur les fonds de thème (0.62.2), par-dessus le décor fixe de ThemeBackdrop.
 * t va de 0 à 1 sur un tour de boucle : chaque mouvement fait un nombre entier de cycles,
 * donc la fin rejoint exactement le début. Tout reste discret : la lecture passe avant.
 */
final class BackdropMotion {
    private BackdropMotion() {
    }

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final RectF R = new RectF();
    private static final Matrix M = new Matrix();
    private static final double TAU = Math.PI * 2;

    private static int a(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    /** Pseudo-hasard fixe pour la particule i (même tirage à chaque image). */
    private static float rnd(int i, int k) {
        int x = i * 374761393 + k * 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        return ((x ^ (x >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    // ------------------------------------------------------------------ Codec

    /** Radar : faisceau qui tourne (4 tours par boucle) et échos qui s'allument à son passage. */
    static void radar(Canvas c, float w, float h, float u, int accent, float t) {
        float cx = w * 0.80f, cy = h * 0.76f, r = u * 0.6f;
        float deg = 360f * fract(4 * t);
        SweepGradient sg = new SweepGradient(cx, cy, new int[] {a(accent, 0), a(accent, 0), a(accent, 0x26), a(accent, 0x55)},
                new float[] {0f, 0.78f, 0.97f, 1f});
        M.setRotate(deg, cx, cy);
        sg.setLocalMatrix(M);
        P.setShader(sg);
        P.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, r, P);
        P.setShader(null);
        // La ligne du faisceau, qui tourne avec lui.
        double br = Math.toRadians(deg);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1f, u / 600f));
        P.setColor(a(accent, 0x70));
        c.drawLine(cx, cy, cx + (float) (r * Math.cos(br)), cy + (float) (r * Math.sin(br)), P);
        P.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 9; i++) {
            float ang = 360f * rnd(i, 1), dist = r * (0.15f + 0.75f * rnd(i, 2));
            float since = fract((deg - ang) / 360f);              // temps depuis le passage du faisceau
            int al = (int) (180 * Math.max(0, 1 - since * 3));
            if (al <= 0) continue;
            double ar = Math.toRadians(ang);
            float x = cx + (float) (dist * Math.cos(ar)), y = cy + (float) (dist * Math.sin(ar));
            // Échos rouges qui clignotent au passage du faisceau (demande de BOSS, 0.62.3).
            int red = 0xFFFF3B3B;
            float blink = fract(since * 6) < 0.5f ? 1f : 0.35f;
            P.setColor(a(red, (int) (al * blink)));
            c.drawCircle(x, y, u * 0.004f * (1 + since * 2), P);
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(u * 0.0012f);
            P.setColor(a(red, al / 3));
            c.drawCircle(x, y, u * 0.004f + since * u * 0.03f, P);
            P.setStyle(Paint.Style.FILL);
        }
    }

    // ------------------------------------------------------------------ Arctique

    /** Aurore qui ondule en haut et neige fine qui tombe en dérivant. */
    static void snow(Canvas c, float w, float h, float u, int accent, float t) {
        float s = (float) Math.sin(TAU * 2 * t);
        P.setShader(new LinearGradient(0, h * (0.05f + 0.03f * s), 0, h * 0.4f,
                a(accent, (int) (0x22 + 0x14 * Math.sin(TAU * 3 * t))), a(accent, 0), Shader.TileMode.CLAMP));
        PATH.reset();
        PATH.moveTo(0, 0);
        for (int k = 0; k <= 12; k++) {
            float x = w * k / 12f;
            float y = h * (0.18f + 0.06f * (float) Math.sin(TAU * (k / 6f + 2 * t)) + 0.03f * (float) Math.sin(TAU * (k / 3f - 3 * t)));
            PATH.lineTo(x, y);
        }
        PATH.lineTo(w, 0);
        PATH.close();
        c.drawPath(PATH, P);
        P.setShader(null);
        for (int i = 0; i < 80; i++) {
            int laps = 1 + (int) (rnd(i, 1) * 3);
            float y = fract(rnd(i, 2) + laps * t) * (h + 20) - 10;
            float x = w * rnd(i, 3) + u * 0.02f * (float) Math.sin(TAU * (rnd(i, 4) + (1 + i % 3) * t));
            float sz = u * (0.0012f + 0.0025f * rnd(i, 5)) * laps / 2f;
            P.setColor(a(0xFFFFFFFF, (int) (40 + 90 * rnd(i, 6))));
            c.drawCircle(x, y, sz, P);
        }
    }

    // ------------------------------------------------------------------ Écarlate

    /** Escarbilles qui montent en scintillant, lueur de braise qui respire. */
    static void embers(Canvas c, float w, float h, float u, int accent, float t) {
        float breath = 0.5f + 0.5f * (float) Math.sin(TAU * 3 * t);
        P.setShader(new RadialGradient(w * 0.5f, h * 1.05f, u * (0.45f + 0.05f * breath),
                a(accent, (int) (0x18 + 0x18 * breath)), a(accent, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.4f, w, h, P);
        P.setShader(null);
        for (int i = 0; i < 60; i++) {
            int laps = 1 + (int) (rnd(i, 1) * 3);
            float k = fract(rnd(i, 2) + laps * t);                 // 0 en bas, 1 en haut
            float y = h * (1.02f - 1.1f * k);
            float x = w * rnd(i, 3) + u * 0.03f * (float) Math.sin(TAU * (rnd(i, 4) + 2 * t) + k * 6);
            float flick = 0.6f + 0.4f * (float) Math.sin(TAU * (7 + i % 5) * t + i);
            int al = (int) (200 * (1 - k) * flick);
            float sz = u * (0.0015f + 0.002f * rnd(i, 5));
            P.setColor(a(i % 3 == 0 ? 0xFFFFC46B : accent, al));
            c.drawCircle(x, y, sz, P);
        }
    }

    // ------------------------------------------------------------------ Sakura

    /** Pétales qui tombent en tournoyant, poussés par une brise. */
    static void petals(Canvas c, float w, float h, float u, int accent, float t) {
        float sz = u * 0.009f;
        for (int i = 0; i < 26; i++) {
            int laps = 1 + (int) (rnd(i, 1) * 2);
            float k = fract(rnd(i, 2) + laps * t);
            float y = -sz * 2 + k * (h + sz * 4);
            float x = fract(rnd(i, 3) + 0.25f * k) * (w + sz * 4) - sz * 2
                    + u * 0.02f * (float) Math.sin(TAU * (rnd(i, 4) + 3 * t));
            float rot = 360f * (rnd(i, 5) + (i % 2 == 0 ? 1 : -1) * 2 * t);
            float flip = (float) Math.cos(TAU * (rnd(i, 6) + 4 * t));
            float s = sz * (0.7f + 0.6f * rnd(i, 7));
            c.save();
            c.translate(x, y);
            c.rotate(rot);
            c.scale(1f, 0.35f + 0.65f * Math.abs(flip));
            PATH.reset();
            PATH.moveTo(0, -s);
            PATH.quadTo(s, -s * 0.2f, 0, s);
            PATH.quadTo(-s, -s * 0.2f, 0, -s);
            P.setColor(a(Theme.lighten(accent, 0.25f), (int) (70 + 80 * rnd(i, 8))));
            c.drawPath(PATH, P);
            c.restore();
        }
    }

    // ------------------------------------------------------------------ Ambre

    /**
     * Ville Art déco : deux projecteurs lents balaient le ciel depuis l'horizon (un tour de boucle,
     * aller-retour) et quelques étoiles en losange scintillent.
     */
    static void ville(Canvas c, float w, float h, float u, int accent, float t) {
        float ground = h * 0.9f;
        for (int i = 0; i < 2; i++) {
            float bx = w * (i == 0 ? 0.06f : 0.72f);
            double ang = -Math.PI / 2 + (i == 0 ? 0.55 : -0.5) * Math.sin(TAU * (t + i * 0.37f));
            float len = Math.max(h * 0.95f, w * 0.5f), spread = len * 0.045f;
            float tx = bx + (float) Math.cos(ang) * len, ty = ground + (float) Math.sin(ang) * len;
            float nx = (float) -Math.sin(ang) * spread, ny = (float) Math.cos(ang) * spread;
            PATH.reset();
            PATH.moveTo(bx, ground);
            PATH.lineTo(tx + nx, ty + ny);
            PATH.lineTo(tx - nx, ty - ny);
            PATH.close();
            P.setShader(new LinearGradient(bx, ground, tx, ty, a(accent, i == 0 ? 0x16 : 0x2A), a(accent, 0), Shader.TileMode.CLAMP));
            c.drawPath(PATH, P);
        }
        P.setShader(null);
        for (int i = 0; i < 14; i++) {
            float x = w * (0.35f + 0.65f * rnd(i, 1)), y = h * 0.04f + h * 0.5f * rnd(i, 2);
            float tw = 0.5f + 0.5f * (float) Math.sin(TAU * (1 + i % 3) * t + i * 2.1f);
            float s = u * (0.002f + 0.002f * rnd(i, 3));
            PATH.reset();
            PATH.moveTo(x, y - s * 2);
            PATH.lineTo(x + s, y);
            PATH.lineTo(x, y + s * 2);
            PATH.lineTo(x - s, y);
            PATH.close();
            P.setColor(a(0xFFFFE2A0, (int) (20 + 110 * tw)));
            c.drawPath(PATH, P);
        }
    }

    /**
     * Antenne radar à barre (vue de face, elle tourne autour du pylône) : la barre se raccourcit de profil,
     * son côté proche descend légèrement, un cornet d'alimentation dépasse côté avant. phase en radians.
     */
    static void antenna(Canvas c, float cx, float ay, float q, float lw, int tint, float phase) {
        float co = (float) Math.cos(phase), si = (float) Math.sin(phase);
        float len = q * 1.6f * Math.max(0.07f, Math.abs(co)), hb = q * 0.13f, dy = si * q * 0.12f;
        P.setStyle(Paint.Style.FILL);
        PATH.reset();
        PATH.moveTo(cx - len, ay - hb - dy);
        PATH.quadTo(cx, ay - hb * 2.2f, cx + len, ay - hb + dy);
        PATH.lineTo(cx + len, ay + hb + dy);
        PATH.quadTo(cx, ay + hb * 0.4f, cx - len, ay + hb - dy);
        PATH.close();
        P.setColor(0xFF0A100C);
        c.drawPath(PATH, P);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1f, lw * 1.3f));
        P.setStrokeCap(Paint.Cap.ROUND);
        P.setColor(a(tint, 0x90));
        c.drawLine(cx - len, ay - hb - dy, cx + len, ay - hb + dy, P);
        P.setColor(a(tint, 0x60));
        c.drawLine(cx, ay, cx + si * q * 0.35f, ay - q * 0.28f * (si > 0 ? 1f : 0.4f), P);
        P.setStyle(Paint.Style.FILL);
        P.setColor(0xFF0A100C);
        c.drawCircle(cx, ay, q * 0.12f, P);
    }

    /** Balise d'obstacle : halo et point, lum de 0 à 1 (au repos : lueur faible). */
    static void beacon(Canvas c, float x, float y, float q, int col, float lum) {
        P.setStyle(Paint.Style.FILL);
        P.setShader(new RadialGradient(x, y, Math.max(1f, q * 1.1f), a(col, Math.round(0x58 * lum)), a(col, 0), Shader.TileMode.CLAMP));
        c.drawCircle(x, y, q * 1.1f, P);
        P.setShader(null);
        P.setColor(a(col, Math.round(0x40 + 0xC0 * lum)));
        c.drawCircle(x, y, q * 0.11f, P);
        P.setColor(a(0xFFFFE0E0, Math.round(0xB0 * lum)));
        c.drawCircle(x, y, q * 0.05f, P);
    }

    /**
     * Base Codec : l'antenne radar de la tour tourne lentement (un tour toutes les 8 s), la balise rouge clignote,
     * quelques fenêtres de la cabine et de la tour de garde s'allument et s'éteignent, et les balises de la grue
     * et de la tour de garde pulsent. Plus de balayage ni d'échos : le sonar reste sur la manette.
     */
    static void base(Canvas c, float w, float h, float u, int accent, float t) {
        float[] pt = Facets.basePts(w, h), tw = Facets.towerPts(w, h);
        float q = Math.min(w, h * 1.2f) * 0.06f, lw = Math.max(1f, u / 1100f);
        // Les fenêtres : chacune a son rythme, allumée un peu moins d'une fois sur deux.
        P.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) {
            float wx = tw[0] + (i - 1) * q * 0.34f;
            if (fract(2 * t + rnd(i, 11)) < 0.55f) {
                P.setColor(a(0xFFFFE9A8, 0xB8));
                c.drawRect(wx - q * 0.08f, tw[1] + q * 0.22f, wx + q * 0.08f, tw[1] + q * 0.46f, P);
                P.setColor(a(0xFFFFE9A8, 0x1C));
                c.drawCircle(wx, tw[1] + q * 0.34f, q * 0.3f, P);
            }
        }
        for (int i = 0; i < 3; i++) {
            float wx = pt[4] + (i - 1) * q * 0.62f;
            if (fract(3 * t + rnd(i, 21)) < 0.5f) {
                P.setColor(a(0xFFFFE9A8, 0xA8));
                c.drawRect(wx - q * 0.12f, pt[5] + q * 0.12f, wx + q * 0.12f, pt[5] + q * 0.42f, P);
            }
        }
        antenna(c, tw[0], tw[2], q, lw, accent, (float) (TAU * 3 * t));
        // Balise rouge : deux brefs éclats puis une pause (une fois par seconde et demie environ).
        float ph = fract(16 * t);
        float lum = ph < 0.18f ? 1f : (ph > 0.3f && ph < 0.42f ? 0.8f : 0f);
        beacon(c, tw[0], tw[3], q, 0xFFFF3B3B, lum);
        for (int i = 1; i < 3; i++) {
            float ph2 = fract(2 * t + i * 0.31f);
            if (ph2 > 0.35f) continue;
            float bx = pt[i * 2], by = pt[i * 2 + 1], r = u * 0.003f;
            P.setColor(a(accent, 0x28));
            c.drawCircle(bx, by, r * 4f, P);
            P.setColor(a(0xFFB8FFD8, 0xC0));
            c.drawCircle(bx, by, r, P);
        }
    }

    // ------------------------------------------------------------------ Ambre : métropole japonaise

    /** Deux projecteurs posés sur les toits des tours hautes (rf = {xg, yg, xd, yd}) ; t règle l'angle de balayage. */
    static void searchlights(Canvas c, float w, float h, int tint, float[] rf, float t) {
        for (int i = 0; i < 2; i++) {
            float bx = rf[i * 2], by = rf[i * 2 + 1];
            double ang = -Math.PI / 2 + (i == 0 ? 0.6 : -0.55) * Math.sin(TAU * (t + i * 0.37f));
            float len = Math.max(h * 0.9f, w * 0.45f), spread = len * 0.04f;
            float tx = bx + (float) Math.cos(ang) * len, ty = by + (float) Math.sin(ang) * len;
            float nx = (float) -Math.sin(ang) * spread, ny = (float) Math.cos(ang) * spread;
            PATH.reset();
            PATH.moveTo(bx, by);
            PATH.lineTo(tx + nx, ty + ny);
            PATH.lineTo(tx - nx, ty - ny);
            PATH.close();
            P.setStyle(Paint.Style.FILL);
            P.setShader(new LinearGradient(bx, by, tx, ty, a(tint, i == 0 ? 0x2A : 0x34), a(tint, 0), Shader.TileMode.CLAMP));
            c.drawPath(PATH, P);
            P.setShader(null);
            P.setColor(a(0xFFFFE2A0, 0xA0));
            c.drawCircle(bx, by, Math.max(2f, h * 0.004f), P);
        }
    }

    /**
     * Métropole japonaise : projecteurs qui balaient depuis les toits, balises rouges qui clignotent (sommet de la
     * tour, tours hautes), lumières qui montent le long de la tour, fenêtres qui s'allument et s'éteignent.
     */
    static void tokyo(Canvas c, float w, float h, float u, int accent, float t) {
        float H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f, ground = h * 0.9f, lw = Math.max(1f, u / 1100f);
        searchlights(c, w, h, accent, Facets.tokyoRoofs(w, h), t);
        // Lumières de la tour : une vague chaude qui monte le long des arêtes, deux fois par tour d'horloge de boucle.
        float[] tg = Facets.tokyoTower(w, h);
        int nb = 14;
        P.setStyle(Paint.Style.FILL);
        for (int k = 0; k <= nb; k++) {
            float s = k / (float) nb, y = ground - tg[3] * s, hw = Facets.towerHalf(tg, s);
            float d = fract(6 * t - s);
            float lum = d < 0.3f ? 1f - d / 0.3f : 0f;
            if (lum <= 0f) continue;
            for (int sd = -1; sd <= 1; sd += 2) {
                P.setColor(a(0xFFFFB347, Math.round(0x30 * lum)));
                c.drawCircle(tg[0] + sd * hw, y, q * 0.2f, P);
                P.setColor(a(0xFFFFE2A0, Math.round(0xD0 * lum)));
                c.drawCircle(tg[0] + sd * hw, y, q * 0.05f, P);
            }
        }
        // Balise rouge au sommet : deux éclats puis pause.
        float ph = fract(12 * t);
        beacon(c, tg[0], tg[4], q * 0.8f, 0xFFFF3B3B, ph < 0.16f ? 1f : (ph > 0.28f && ph < 0.4f ? 0.8f : 0f));
        // Fenêtres : sur les tours hautes, quelques cases s'allument et s'éteignent.
        float[][] near = Facets.tokyoBlocks(w, h, 1);
        int shown = 0;
        for (int i = 0; i < near.length; i++) {
            float[] b = near[i];
            if (b[2] < H0 * 0.22f) continue;
            float top = ground - b[2], cw = q * 0.2f, ch = q * 0.3f;
            int cols = Math.max(2, Math.round(b[1] / cw));
            float cwa = b[1] / cols;
            int rows = (int) ((b[2] - ch * 1.2f) / ch);
            for (int k = 0; k < 12 && rows > 1; k++) {
                int cx = (int) (rnd(i * 31 + k, 41) * cols), cy = (int) (rnd(i * 31 + k, 42) * rows);
                float ph2 = fract(2 * t + rnd(i * 31 + k, 43));
                float lum = ph2 < 0.5f ? 1f : 0f;
                if (lum == 0f) continue;
                float x = b[0] + cx * cwa, y = top + ch * 0.7f + cy * ch;
                P.setColor(a(rnd(i, k) < 0.2f ? 0xFFFFF0D0 : 0xFFFFC66B, 0xC0));
                c.drawRect(x + cwa * 0.2f, y, x + cwa * 0.8f, y + ch * 0.5f, P);
            }
            // Balise de toit : clignote à son rythme.
            float bp = fract(8 * t + rnd(i, 51));
            if (bp < 0.2f) {
                float bx = b[0] + b[1] * 0.75f, by = top - q * 0.8f;
                P.setColor(a(0xFFFF3B3B, 0x50));
                c.drawCircle(bx, by, q * 0.26f, P);
                P.setColor(a(0xFFFF6A5A, 0xE0));
                c.drawCircle(bx, by, q * 0.07f, P);
            }
            if (++shown >= 8) break;
        }
    }

    /** Nuit polaire : l'aurore ondule (rayons qui se balancent) et la glace scintille. */
    static void polaire(Canvas c, float w, float h, float u, int accent, float t) {
        float top = h * 0.06f, bot = h * 0.52f;
        for (int i = 0; i < 18; i++) {
            float x = w * (i + 0.5f) / 18f + u * 0.015f * (float) Math.sin(TAU * (t + rnd(i, 1)));
            float sw = u * 0.03f * (float) Math.sin(TAU * (2 * t + rnd(i, 2)));
            float al = (float) Math.max(0, Math.sin(TAU * (t + rnd(i, 3))));
            int alpha = Math.round((4 + 16 * al) * (0.25f + 0.75f * x / w));
            P.setColor(a(i % 3 == 0 ? 0xFF9C8CFF : (i % 3 == 1 ? 0xFF6CFFC0 : accent), alpha));
            PATH.reset();
            PATH.moveTo(x - u * 0.004f, bot);
            PATH.lineTo(x + sw - u * 0.012f, top);
            PATH.lineTo(x + sw + u * 0.012f, top);
            PATH.lineTo(x + u * 0.004f, bot);
            PATH.close();
            c.drawPath(PATH, P);
        }
        for (int i = 0; i < 24; i++) {
            float x = w * (0.35f + 0.65f * rnd(i, 4)), y = h * (0.55f + 0.42f * rnd(i, 5));
            float tw = Math.max(0f, (float) Math.sin(TAU * (1 + i % 3) * t + i * 1.7f));
            P.setColor(a(0xFFEAF9FF, Math.round(20 + 150 * tw * tw)));
            c.drawCircle(x, y, u * (0.0012f + 0.0012f * rnd(i, 6)), P);
        }
    }

    /** Nuit japonaise : braises qui montent et lanternes qui se balancent sous la poutre du torii. */
    static void japon(Canvas c, float w, float h, float u, int accent, float t) {
        embers(c, w, h, u, accent, t);
        float[] tp = Facets.japonPts(w, h);
        float q = Math.min(w, h * 1.2f) * 0.06f;
        for (int i = 0; i < 3; i++) {
            float x = tp[0] + (i - 1) * tp[2] * 0.3f, y = tp[1] + q * 0.1f;
            float sw = (float) Math.sin(TAU * (t + i * 0.23f)) * 0.12f;
            float len = q * (0.5f + 0.12f * (i & 1)), lx = x + (float) Math.sin(sw) * len, ly = y + (float) Math.cos(sw) * len;
            P.setColor(a(0xFFFFB347, 0x70));
            P.setStyle(Paint.Style.STROKE);
            P.setStrokeWidth(Math.max(1f, u / 1100f));
            c.drawLine(x, y, lx, ly, P);
            P.setStyle(Paint.Style.FILL);
            R.set(lx - q * 0.2f, ly, lx + q * 0.2f, ly + q * 0.5f);
            float fl = 0.8f + 0.2f * (float) Math.sin(TAU * (6 * t + i));
            P.setColor(a(accent, (int) (0x24 * fl)));
            c.drawCircle(lx, ly + q * 0.25f, q * 0.8f, P);
            P.setColor(a(0xFFFF6A3C, (int) (0xC0 * fl)));
            c.drawOval(R, P);
            P.setColor(a(0xFFFFE0A0, (int) (0x60 * fl)));
            R.set(lx - q * 0.08f, ly + q * 0.12f, lx + q * 0.08f, ly + q * 0.38f);
            c.drawOval(R, P);
        }
    }

    /** Rivière de printemps : pétales qui tombent et rides qui s'élargissent sur l'eau. */
    static void riviere(Canvas c, float w, float h, float u, int accent, float t) {
        petals(c, w, h, u, accent, t);
        float bank = h * 0.64f, q = Math.min(w, h * 1.2f) * 0.06f;
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1f, u / 1000f));
        for (int i = 0; i < 9; i++) {
            float ph = fract(t * (1 + i % 2) + rnd(i, 1));
            float x = w * (0.3f + 0.7f * rnd(i, 2)), y = bank + (h - bank) * (0.15f + 0.8f * rnd(i, 3));
            float depth = (y - bank) / (h - bank);
            float r = q * (0.3f + 2.2f * ph) * (0.6f + depth);
            P.setColor(a(Theme.lighten(accent, 0.4f), Math.round(0x40 * (1f - ph))));
            R.set(x - r, y - r * 0.28f, x + r, y + r * 0.28f);
            c.drawOval(R, P);
        }
        P.setStyle(Paint.Style.FILL);
    }

    /** Vieux terminal : bande claire qui descend, scintillement de tube et curseur qui clignote. */
    static void terminal(Canvas c, float w, float h, float u, int accent, float t) {
        float y = fract(3 * t) * (h * 1.3f) - h * 0.15f;
        P.setShader(new LinearGradient(0, y - h * 0.08f, 0, y + h * 0.08f,
                new int[] {a(accent, 0), a(accent, 0x16), a(accent, 0)}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, y - h * 0.08f, w, y + h * 0.08f, P);
        P.setShader(null);
        float fl = (float) Math.sin(TAU * 37 * t) * (float) Math.sin(TAU * 11 * t);
        P.setColor(a(accent, (int) (6 + 6 * fl)));
        c.drawRect(0, 0, w, h, P);
        if (fract(48 * t) < 0.5f) {
            float cs = u * 0.012f;
            P.setColor(a(accent, 0x70));
            R.set(w * 0.05f, h * 0.92f - cs * 1.6f, w * 0.05f + cs, h * 0.92f);
            c.drawRect(R, P);
        }
    }

    // ------------------------------------------------------------------ Néon, Jade, Argent (0.66)

    /** Ruelle de Tokyo : la pluie tombe en biais, des gouttes éclatent sur le bitume, deux enseignes grésillent. */
    static void ruelle(Canvas c, float w, float h, float u, int accent, int fold, float t) {
        float m = Math.min(w * 0.9f, h) * 0.05f;
        float[] sg = Facets.neonSigns(w, h);
        int[] flick = {2, 6};
        for (int f = 0; f < flick.length; f++) {
            int i = flick[f];
            float x0 = sg[i * 7], y0 = sg[i * 7 + 1], x1 = sg[i * 7 + 2], y1 = sg[i * 7 + 3];
            int col = sg[i * 7 + 4] == 0 ? accent : fold;
            float ph = fract(3 * t + f * 0.41f);
            boolean off = (ph > 0.62f && ph < 0.66f) || (ph > 0.70f && ph < 0.72f) || (ph > 0.75f && ph < 0.86f);
            if (off) {
                P.setColor(0xB0100A16);
                R.set(x0, y0, x1, y1);
                c.drawRoundRect(R, m * 0.2f, m * 0.2f, P);
            } else {
                float g = 0.5f + 0.5f * (float) Math.sin(TAU * (5 * t + f));
                P.setShader(new RadialGradient((x0 + x1) / 2, (y0 + y1) / 2, Math.max(1f, (y1 - y0) * 0.8f),
                        a(col, Math.round(0x10 + 0x14 * g)), a(col, 0), Shader.TileMode.CLAMP));
                c.drawCircle((x0 + x1) / 2, (y0 + y1) / 2, (y1 - y0) * 0.8f, P);
                P.setShader(null);
            }
        }
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 120; i++) {
            int laps = 3 + (int) (rnd(i, 1) * 3);
            float len = m * (0.8f + 1.4f * rnd(i, 5)), y = fract(rnd(i, 2) + laps * t) * (h + len * 2) - len;
            float x = w * rnd(i, 3) - (y / h) * len * 0.6f;
            P.setStrokeWidth(Math.max(1f, u / 1500f) * (0.7f + rnd(i, 6)));
            P.setColor(a(i % 4 == 0 ? fold : (i % 5 == 0 ? accent : 0xFFD8D0FF), Math.round(0x20 + 0x30 * rnd(i, 4))));
            c.drawLine(x, y, x - len * 0.18f, y + len, P);
        }
        float vy = h * 0.5f, gN = h * 0.95f, gF = gN + (vy - gN) * 0.8f;
        P.setStrokeWidth(Math.max(1f, u / 1400f));
        for (int i = 0; i < 18; i++) {
            float ph = fract(2 * t + rnd(i, 7));
            float y = gF + (h - gF) * (0.1f + 0.9f * rnd(i, 8)), depth = (y - gF) / (h - gF);
            float x = w * (0.1f + 0.8f * rnd(i, 9)), r = m * (0.15f + 0.9f * ph) * (0.4f + depth);
            P.setColor(a(i % 2 == 0 ? fold : accent, Math.round(0x50 * (1f - ph))));
            R.set(x - r, y - r * 0.25f, x + r, y + r * 0.25f);
            c.drawOval(R, P);
        }
        P.setStyle(Paint.Style.FILL);
    }

    /** Temple dans la brume : bancs de brume qui dérivent, lucioles, lanternes qui respirent. */
    static void temple(Canvas c, float w, float h, float u, int accent, int fold, float t) {
        float[] lp = Facets.jadePts(w, h);
        for (int i = 0; i < lp.length / 3; i++) {
            float br = 0.5f + 0.5f * (float) Math.sin(TAU * (2 * t + i * 0.17f));
            float r = lp[i * 3 + 2] * (4f + 1.5f * br);
            P.setShader(new RadialGradient(lp[i * 3], lp[i * 3 + 1], Math.max(1f, r), a(fold, Math.round(0x10 + 0x18 * br)), a(fold, 0), Shader.TileMode.CLAMP));
            c.drawCircle(lp[i * 3], lp[i * 3 + 1], r, P);
        }
        float H0 = Math.min(h, w * 0.9f);
        float[] bands = {h * 0.6f, h * 0.72f, h * 0.88f};
        for (int i = 0; i < 18; i++) {
            float R0 = H0 * (0.12f + 0.12f * rnd(i, 1)), y = bands[i % 3] + (rnd(i, 2) - 0.5f) * H0 * 0.06f;
            float x = fract(rnd(i, 3) + (i % 2 == 0 ? t : -t)) * (w + 2 * R0) - R0;
            P.setShader(new RadialGradient(x, y, R0, a(0xFFB6D8D0, 0x16), a(0xFFB6D8D0, 0), Shader.TileMode.CLAMP));
            c.drawCircle(x, y, R0, P);
        }
        P.setShader(null);
        for (int i = 0; i < 16; i++) {
            float x = w * (0.12f + 0.76f * rnd(i, 4)) + u * 0.03f * (float) Math.sin(TAU * (t * (1 + i % 2) + rnd(i, 5)));
            float y = h * (0.55f + 0.4f * rnd(i, 6)) + u * 0.02f * (float) Math.sin(TAU * (t * (2 + i % 3) + rnd(i, 7)));
            float g = Math.max(0f, (float) Math.sin(TAU * ((3 + i % 3) * t + rnd(i, 8))));
            P.setColor(a(fold, Math.round(0x18 * g)));
            c.drawCircle(x, y, u * 0.006f, P);
            P.setColor(a(Theme.lighten(fold, 0.5f), Math.round(0xC0 * g)));
            c.drawCircle(x, y, u * 0.0016f, P);
        }
    }

    /** Pleine lune : les grues traversent le ciel en battant des ailes, les étoiles scintillent, le halo respire. */
    static void lune(Canvas c, float w, float h, float u, int accent, int fold, float t) {
        float[] lp = Facets.lunePts(w, h);
        float br = 0.5f + 0.5f * (float) Math.sin(TAU * 2 * t);
        P.setShader(new RadialGradient(lp[0], lp[1], lp[2] * 3f, a(0xFFFFFFFF, Math.round(0x08 + 0x0C * br)), a(0xFFFFFFFF, 0), Shader.TileMode.CLAMP));
        c.drawCircle(lp[0], lp[1], lp[2] * 3f, P);
        P.setShader(null);
        for (int i = 0; i < 20; i++) {
            float tw = Math.max(0f, (float) Math.sin(TAU * (1 + i % 3) * t + i * 2.3f));
            P.setColor(a(0xFFFFFFFF, Math.round(0xB0 * tw * tw)));
            c.drawCircle(w * rnd(i, 1), h * 0.6f * rnd(i, 2), u * 0.0012f, P);
        }
        // Étoiles filantes (0.66.17, à la place des grues) : une traînée de temps en temps, jamais deux à la fois.
        {
            // Une étoile toutes les 6 s (4 par boucle de 24 s), qui file pendant un peu plus d'une seconde.
            int i = (int) (t * 4f);
            float p = fract(t * 4f) / 0.18f;
            if (p < 1f) {
                float sx = w * (0.1f + 0.5f * rnd(i, 7)) + w * 0.3f * p, sy = h * (0.05f + 0.25f * rnd(i, 8)) + h * 0.15f * p;
                float len = u * 0.12f, fade = (float) Math.sin(Math.PI * p);
                P.setShader(new android.graphics.LinearGradient(sx, sy, sx - len, sy - len * 0.5f,
                        a(0xFFFFFFFF, Math.round(0xE0 * fade)), a(0xFFFFFFFF, 0), Shader.TileMode.CLAMP));
                P.setStrokeWidth(Math.max(1f, u * 0.0022f));
                P.setStyle(android.graphics.Paint.Style.STROKE);
                c.drawLine(sx, sy, sx - len, sy - len * 0.5f, P);
                P.setStyle(android.graphics.Paint.Style.FILL);
                P.setShader(null);
            }
        }
        // Brume qui passe lentement devant la lune.
        for (int i = 0; i < 2; i++) {
            float fx = fract(t * (1 + i) + i * 0.5f) * (w * 1.6f) - w * 0.3f, fy = lp[1] + lp[2] * (i == 0 ? 0.4f : -0.6f);
            P.setShader(new RadialGradient(fx, fy, lp[2] * 3.2f, a(0xFFDCE2EC, 0x16), a(0xFFDCE2EC, 0), Shader.TileMode.CLAMP));
            c.drawCircle(fx, fy, lp[2] * 3.2f, P);
            P.setShader(null);
        }
    }
}
