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
}
