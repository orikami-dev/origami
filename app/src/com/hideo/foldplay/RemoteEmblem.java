package com.hideo.foldplay;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Emblèmes maison des tuiles Remote Play (0.62.1) : une même manette qui émet des ondes, aux
 * couleurs de chaque plateforme, marquée d'une initiale. Rien n'est repris des logos officiels.
 */
final class RemoteEmblem {
    private RemoteEmblem() {
    }

    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint LINE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF R = new RectF();
    private static final Path P = new Path();

    /** Couleurs [fond haut, fond bas, trait] et initiale de chaque plateforme. */
    static int[] colors(String id) {
        if ("ps".equals(id)) return new int[] {0xFF2F6FE0, 0xFF0B2A6B, 0xFFE8F0FF};
        if ("xbox".equals(id)) return new int[] {0xFF4CB848, 0xFF125C1B, 0xFFEFFFEA};
        return new int[] {0xFF3A6E99, 0xFF0F1A2B, 0xFFCDE9FF};
    }

    static String letter(String id) {
        return "ps".equals(id) ? "P" : "xbox".equals(id) ? "X" : "S";
    }

    static boolean handles(String uri) {
        return uri.startsWith("action:remote:");
    }

    static void draw(Canvas c, String uri, float cx, float cy, float side) {
        String id = uri.substring("action:remote:".length());
        int[] col = colors(id);
        float h = side / 2f;
        // Médaillon arrondi aux couleurs de la plateforme.
        R.set(cx - h, cy - h, cx + h, cy + h);
        FILL.setShader(new LinearGradient(0, R.top, 0, R.bottom, col[0], col[1], Shader.TileMode.CLAMP));
        c.drawRoundRect(R, side * 0.24f, side * 0.24f, FILL);
        FILL.setShader(null);
        LINE.setStyle(Paint.Style.STROKE);
        LINE.setStrokeCap(Paint.Cap.ROUND);
        LINE.setStrokeJoin(Paint.Join.ROUND);
        LINE.setColor(col[2]);
        LINE.setStrokeWidth(side * 0.055f);
        // Manette stylisée en bas du médaillon.
        float pw = side * 0.56f, ph = side * 0.26f, py = cy + side * 0.14f;
        P.reset();
        P.moveTo(cx - pw / 2 + ph * 0.3f, py - ph / 2);
        P.lineTo(cx + pw / 2 - ph * 0.3f, py - ph / 2);
        P.quadTo(cx + pw / 2 + ph * 0.25f, py - ph / 2, cx + pw / 2, py + ph / 2);
        P.quadTo(cx + pw / 2 - ph * 0.25f, py + ph * 0.75f, cx + pw * 0.22f, py + ph * 0.25f);
        P.lineTo(cx - pw * 0.22f, py + ph * 0.25f);
        P.quadTo(cx - pw / 2 + ph * 0.25f, py + ph * 0.75f, cx - pw / 2, py + ph / 2);
        P.quadTo(cx - pw / 2 - ph * 0.25f, py - ph / 2, cx - pw / 2 + ph * 0.3f, py - ph / 2);
        P.close();
        c.drawPath(P, LINE);
        // Ondes de diffusion au-dessus, avec l'initiale au centre.
        LINE.setStrokeWidth(side * 0.045f);
        float wy = py - ph * 0.8f;
        for (int i = 1; i <= 2; i++) {
            float rr = side * (0.12f + 0.1f * i);
            R.set(cx - rr, wy - rr, cx + rr, wy + rr);
            LINE.setAlpha(255 - 70 * i);
            c.drawArc(R, 220, 100, false, LINE);
        }
        LINE.setAlpha(255);
        TEXT.setColor(col[2]);
        TEXT.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setTextSize(side * 0.2f);
        c.drawText(letter(id), cx, wy - side * 0.02f, TEXT);
        // Fin liseré clair pour s'accorder aux autres tuiles.
        LINE.setStrokeWidth(Math.max(1f, side * 0.015f));
        LINE.setColor(Color.argb(90, 255, 255, 255));
        R.set(cx - h, cy - h, cx + h, cy + h);
        c.drawRoundRect(R, side * 0.24f, side * 0.24f, LINE);
    }
}
