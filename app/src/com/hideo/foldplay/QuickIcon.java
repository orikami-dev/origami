package com.hideo.foldplay;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * Pictogrammes de sauvegarde et de chargement rapides (0.67). Sauvegarde : disquette ; chargement : flèche circulaire de retour autour d'une disquette. Les thèmes doux (Sakura, Ambre, Hanami) arrondissent le trait, les autres le gardent net.
 */
final class QuickIcon extends Drawable {
    private final boolean save;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int ink;

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
            // Restaurer : flèche circulaire anti-horaire (retour à l'état sauvegardé) autour d'une petite disquette.
            android.graphics.RectF r = new android.graphics.RectF(cx - u, cy - u, cx + u, cy + u);
            c.drawArc(r, -60, -280, false, ring);
            // Pointe de flèche au début de l'arc (en haut à droite), orientée dans le sens du retour.
            float ax = cx + u * 0.5f, ay = cy - u * 0.87f;
            path.reset();
            path.moveTo(ax + u * 0.65f, ay - u * 0.05f);
            path.lineTo(ax, ay);
            path.lineTo(ax + u * 0.15f, ay - u * 0.7f);
            c.drawPath(path, ring);
            // Disquette réduite au centre : ce que l'on recharge.
            float k = u * 0.38f;
            path.reset();
            path.moveTo(cx - k, cy - k);
            path.lineTo(cx + k * 0.4f, cy - k);
            path.lineTo(cx + k, cy - k * 0.4f);
            path.lineTo(cx + k, cy + k);
            path.lineTo(cx - k, cy + k);
            path.close();
            c.drawPath(path, ring);
        }
        ring.setStrokeCap(Paint.Cap.BUTT);
        ring.setStrokeJoin(Paint.Join.MITER);
    }

    @Override public void setAlpha(int a) { p.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { p.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
