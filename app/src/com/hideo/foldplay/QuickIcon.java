package com.hideo.foldplay;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * Pictogrammes de sauvegarde et de chargement rapides (0.67). Sauvegarde : disquette ; chargement : flèche de retour en arrière
 * autour d'une horloge. Les thèmes doux (Sakura, Ambre, Hanami) arrondissent le trait, les autres le gardent net.
 */
final class QuickIcon extends Drawable {
    private final boolean save;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int ink;
    private static final android.graphics.RectF RECT = new android.graphics.RectF();

    QuickIcon(boolean save, int ink) {
        this.save = save;
        this.ink = ink;
        p.setStyle(Paint.Style.STROKE);
    }

    static boolean soft() {
        String id = Theme.THEME_ID == null ? "" : Theme.THEME_ID;
        return id.equals("sakura") || id.equals("ambre") || id.equals("hanami");
    }

    @Override
    public void draw(Canvas c) {
        float w = getBounds().width(), h = getBounds().height();
        paint(c, p, path, getBounds().left + w / 2, getBounds().top + h / 2, Math.min(w, h) * 0.3f, save, ink);
    }

    static void paint(Canvas c, Paint ring, Path path, float cx, float cy, float u, boolean save, int ink) {
        boolean soft = soft();
        ring.setShader(null);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(ink);
        ring.setStrokeWidth(Math.max(1.5f, u * 0.2f));
        ring.setStrokeCap(soft ? Paint.Cap.ROUND : Paint.Cap.SQUARE);
        ring.setStrokeJoin(soft ? Paint.Join.ROUND : Paint.Join.MITER);
        path.reset();
        if (save) {
            path.moveTo(cx - u, cy - u);
            path.lineTo(cx + u * 0.6f, cy - u);
            path.lineTo(cx + u, cy - u * 0.6f);
            path.lineTo(cx + u, cy + u);
            path.lineTo(cx - u, cy + u);
            path.close();
            c.drawPath(path, ring);
            c.drawLine(cx - u * 0.5f, cy - u, cx - u * 0.5f, cy - u * 0.35f, ring);
            c.drawLine(cx - u * 0.5f, cy - u * 0.35f, cx + u * 0.4f, cy - u * 0.35f, ring);
            c.drawLine(cx - u * 0.55f, cy + u, cx - u * 0.55f, cy + u * 0.35f, ring);
            c.drawLine(cx - u * 0.55f, cy + u * 0.35f, cx + u * 0.55f, cy + u * 0.35f, ring);
            c.drawLine(cx + u * 0.55f, cy + u * 0.35f, cx + u * 0.55f, cy + u, ring);
        } else {
            // Chargement : flèche qui revient en arrière autour d'une horloge (« restaurer »), lisible d'un coup d'œil.
            float r = u * 0.95f;
            RECT.set(cx - r, cy - r, cx + r, cy + r);
            path.addArc(RECT, -150f, 285f);
            c.drawPath(path, ring);
            // Pointe de flèche au départ de l'arc (haut gauche), tournée dans le sens inverse des aiguilles.
            double a0 = Math.toRadians(-150), t = a0 - Math.PI / 2, hl = u * 0.5;
            float sx = cx + (float) Math.cos(a0) * r, sy = cy + (float) Math.sin(a0) * r;
            path.reset();
            path.moveTo(sx + (float) Math.cos(t + Math.toRadians(145)) * (float) hl, sy + (float) Math.sin(t + Math.toRadians(145)) * (float) hl);
            path.lineTo(sx, sy);
            path.lineTo(sx + (float) Math.cos(t - Math.toRadians(145)) * (float) hl, sy + (float) Math.sin(t - Math.toRadians(145)) * (float) hl);
            c.drawPath(path, ring);
            // Aiguilles.
            path.reset();
            path.moveTo(cx, cy - u * 0.5f);
            path.lineTo(cx, cy);
            path.lineTo(cx + u * 0.4f, cy + u * 0.25f);
            c.drawPath(path, ring);
        }
        ring.setStrokeCap(Paint.Cap.BUTT);
        ring.setStrokeJoin(Paint.Join.MITER);
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
