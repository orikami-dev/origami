package com.hideo.foldplay;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Motifs gravés sur la façade de la manette (habillage « Codec », en portrait), un par thème,
 * toujours très discrets pour que les touches restent bien lisibles, et différents du fond de
 * l'écran du haut tout en restant du même univers (voir render) ;
 * - Ambre : métal brossé et trace d'oscilloscope pliée ;
 * - Couleurs de la manette : nid d'abeilles à la teinte de l'accent.
 * Tirages aléatoires à graine fixe : le motif ne bouge jamais d'un affichage à l'autre.
 */
final class PadSkins {
    private PadSkins() {
    }

    private static int a(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Motifs déjà dessinés (un par panneau : manette entière ou chaque moitié de la Flex). */
    private static final android.util.LruCache<String, android.graphics.Bitmap> CACHE =
            new android.util.LruCache<String, android.graphics.Bitmap>(3);

    /**
     * Dessine le motif du thème dans box (déjà découpée aux coins arrondis par l'appelant).
     * Le motif est rendu une fois dans une image, puis simplement recopié.
     */
    static void draw(Canvas c, RectF box, String id, int accent, float dp) {
        int w = Math.round(box.width()), h = Math.round(box.height());
        if (w <= 0 || h <= 0) return;
        String key = id + ":" + accent + ":" + Theme.fold() + ":" + w + "x" + h;
        android.graphics.Bitmap bm = CACHE.get(key);
        if (bm == null) {
            try {
                bm = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            } catch (OutOfMemoryError e) {
                return;
            }
            Canvas bc = new Canvas(bm);
            render(bc, new RectF(0, 0, w, h), id, accent, dp);
            CACHE.put(key, bm);
        }
        c.drawBitmap(bm, box.left, box.top, null);
    }

    /**
     * Le motif de la manette est le pendant de l'écran du haut (0.60.4) : même univers, autre image.
     * Codec : radar en haut, onde de transmission ici ; Arctique : glaciers et aurore en haut,
     * banquise ici ; Écarlate : flammes en haut, éventail et soleil levant ici ; Sakura : mont et
     * branche en haut, vagues et pétales ici ; Ambre : soleil Art déco en haut, oscilloscope ici.
     */
    private static void render(Canvas c, RectF box, String id, int accent, float dp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float w = box.width(), h = box.height();
        if (id.equals("arctique")) Facets.arctique(c, w, h, accent, Theme.fold(), 0.75f, 1);
        else if (id.equals("ecarlate")) Facets.ecarlate(c, w, h, accent, Theme.fold(), 0.75f, 1);
        else if (id.equals("sakura")) Facets.sakura(c, w, h, accent, Theme.fold(), 0.75f, 1);
        else if (id.equals("ambre")) {
            grille(c, box, accent, dp, p);
            Facets.ambre(c, w, h, accent, Theme.fold(), 0.75f, 0);
        }
        else if (id.equals("pad")) honeycomb(c, box, accent, dp, p);
        else Facets.codec(c, w, h, accent, Theme.fold(), 0.75f, 1);
    }

    /** Vrai si le motif remplace la trame de points d'origine. */
    static boolean replacesDots(String id) {
        return true;
    }

    private static void grille(Canvas c, RectF b, int accent, float dp, Paint p) {
        // Métal brossé : fines lignes horizontales irrégulières.
        Random r = new Random(17);
        p.setStyle(Paint.Style.FILL);
        for (float y = b.top; y < b.bottom; y += 2 * dp) {
            p.setColor(r.nextBoolean() ? 0x08FFFFFF : 0x0A000000);
            c.drawRect(b.left, y, b.right, y + dp * (0.5f + r.nextFloat()), p);
        }
    }

    private static void honeycomb(Canvas c, RectF b, int accent, float dp, Paint p) {
        float s = 14 * dp, h = s * 0.866f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp);
        p.setColor(a(accent, 0x16));
        Path hex = new Path();
        int row = 0;
        for (float y = b.top; y < b.bottom + h; y += h * 2, row++) {
            for (float x = b.left; x < b.right + s * 3; x += s * 3) {
                for (int k = 0; k < 2; k++) {
                    float cx = x + k * s * 1.5f, cy = y + k * h;
                    hex.reset();
                    for (int i = 0; i < 6; i++) {
                        double t = Math.PI / 3 * i;
                        float px = cx + (float) Math.cos(t) * s, py = cy + (float) Math.sin(t) * s;
                        if (i == 0) hex.moveTo(px, py);
                        else hex.lineTo(px, py);
                    }
                    hex.close();
                    c.drawPath(hex, p);
                }
            }
        }
        p.setStyle(Paint.Style.FILL);
    }
}
