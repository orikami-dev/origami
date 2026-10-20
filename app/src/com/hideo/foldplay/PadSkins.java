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
 * - Sakura : vagues seigaiha au trait, branche en fleurs dans un coin, pétales qui dérivent ;
 * - Ambre : métal brossé, soleil Art déco qui se lève du bas, arcs à gradins et filets dorés ;
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
        else if (id.equals("sakura")) sakura(c, box, accent, dp, p);
        else if (id.equals("ambre")) ambre(c, box, accent, dp, p);
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

    // ------------------------------------------------------------ Sakura (0.62.5)

    /**
     * Sakura, refaite à la demande de BOSS : de l'eau au trait (vagues seigaiha, trois anneaux
     * fins par écaille) qui s'efface vers le haut, une branche en fleurs qui entre par le coin
     * haut droit, et quelques pétales posés dessus. Rien de plein : la façade reste calme.
     */
    private static void sakura(Canvas c, RectF b, int accent, float dp, Paint p) {
        float w = b.width(), h = b.height(), m = Math.min(w, h);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, 0, h, a(accent, 0x12), a(accent, 0x00), Shader.TileMode.CLAMP));
        c.drawRect(b, p);
        p.setShader(null);
        // Vagues : rangées décalées de demi-cercles, dans la moitié basse.
        float r = Math.max(14 * dp, m * 0.075f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, dp * 0.9f));
        RectF arc = new RectF();
        int row = 0;
        for (float y = h + r * 0.4f; y > h * 0.42f; y -= r * 0.55f, row++) {
            float fade = (y - h * 0.42f) / (h * 0.58f);
            int al = Math.round(0x08 + 0x22 * Math.max(0, Math.min(1, fade)));
            float off = (row & 1) == 0 ? 0 : r;
            for (float x = -r + off; x < w + r; x += 2 * r) {
                for (int ring = 0; ring < 3; ring++) {
                    float rr = r * (1f - ring * 0.3f);
                    arc.set(x - rr, y - rr, x + rr, y + rr);
                    p.setColor(a(ring == 0 ? accent : 0xFFFFFFFF, ring == 0 ? al : al / 2));
                    c.drawArc(arc, 180, 180, false, p);
                }
            }
        }
        // Branche : trois segments qui s'affinent, depuis le coin haut droit.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        float[][] br = {{1.02f, 0.02f}, {0.86f, 0.10f}, {0.74f, 0.08f}, {0.62f, 0.16f}};
        for (int i = 0; i + 1 < br.length; i++) {
            p.setStrokeWidth(Math.max(1.5f, m * (0.022f - i * 0.006f)));
            p.setColor(0x38000000);
            c.drawLine(w * br[i][0], h * br[i][1], w * br[i + 1][0], h * br[i + 1][1], p);
        }
        p.setStrokeWidth(Math.max(1f, m * 0.008f));
        c.drawLine(w * 0.86f, h * 0.10f, w * 0.83f, h * 0.22f, p);
        p.setStrokeCap(Paint.Cap.BUTT);
        p.setStyle(Paint.Style.FILL);
        Random rnd = new Random(23);
        float[][] fl = {{0.88f, 0.08f, 1f}, {0.78f, 0.12f, 0.8f}, {0.66f, 0.13f, 0.7f}, {0.83f, 0.21f, 0.6f}, {0.95f, 0.15f, 0.55f}};
        for (float[] f : fl) blossom(c, w * f[0], h * f[1], m * 0.035f * f[2], rnd.nextFloat() * 6.28f, accent, p);
        // Pétales qui dérivent, plus nombreux près de l'eau.
        for (int i = 0; i < 16; i++) {
            float x = w * rnd.nextFloat(), y = h * (0.3f + 0.7f * rnd.nextFloat());
            float sz = m * (0.012f + 0.01f * rnd.nextFloat());
            c.save();
            c.rotate(rnd.nextFloat() * 360, x, y);
            p.setColor(a(accent, 0x22 + rnd.nextInt(0x18)));
            c.drawOval(new RectF(x - sz, y - sz * 0.45f, x + sz, y + sz * 0.45f), p);
            c.restore();
        }
    }

    /** Fleur de cerisier : cinq pétales ronds et un cœur clair. */
    private static void blossom(Canvas c, float cx, float cy, float r, float rot, int accent, Paint p) {
        for (int i = 0; i < 5; i++) {
            double t = rot + Math.PI * 2 * i / 5;
            float px = cx + (float) Math.cos(t) * r * 0.62f, py = cy + (float) Math.sin(t) * r * 0.62f;
            p.setColor(a(accent, 0x46));
            c.drawCircle(px, py, r * 0.5f, p);
        }
        p.setColor(0x40FFFFFF);
        c.drawCircle(cx, cy, r * 0.22f, p);
    }

    // ------------------------------------------------------------ Ambre (0.62.5)

    /**
     * Ambre, refaite : façade de radio Art déco. Métal brossé, soleil aux rayons alternés qui se
     * lève du bas, trois arcs dorés à gradins autour de lui, éventails dans les coins du haut et
     * double filet en haut et en bas.
     */
    private static void ambre(Canvas c, RectF b, int accent, float dp, Paint p) {
        float w = b.width(), h = b.height();
        grille(c, b, accent, dp, p);
        float cx = w / 2, cy = h * 1.04f, R = (float) Math.hypot(w / 2, h * 1.04f) + 4 * dp;
        // Rayons : 28 coins alternés, plus marqués au centre.
        Path ray = new Path();
        p.setStyle(Paint.Style.FILL);
        int n = 28;
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI + Math.PI * i / n, t1 = Math.PI + Math.PI * (i + 1) / n;
            ray.reset();
            ray.moveTo(cx, cy);
            ray.lineTo(cx + (float) Math.cos(t0) * R, cy + (float) Math.sin(t0) * R);
            ray.lineTo(cx + (float) Math.cos(t1) * R, cy + (float) Math.sin(t1) * R);
            ray.close();
            float mid = 1f - Math.abs((i + 0.5f) / n - 0.5f) * 2;
            p.setColor(a(accent, (i & 1) == 0 ? Math.round(0x06 + 0x0C * mid) : 0x03));
            c.drawPath(ray, p);
        }
        // Arcs à gradins.
        p.setStyle(Paint.Style.STROKE);
        RectF arc = new RectF();
        float[] radii = {0.30f, 0.46f, 0.62f};
        for (int i = 0; i < radii.length; i++) {
            float rr = h * radii[i];
            p.setStrokeWidth(Math.max(1f, dp * (1.4f - i * 0.3f)));
            p.setColor(a(accent, 0x34 - i * 0x0A));
            arc.set(cx - rr, cy - rr, cx + rr, cy + rr);
            c.drawArc(arc, 180, 180, false, p);
            // Gradins : petites marches aux deux pieds de l'arc.
            float step = 5 * dp;
            c.drawLine(cx - rr, cy - step, cx - rr - step, cy - step, p);
            c.drawLine(cx + rr, cy - step, cx + rr + step, cy - step, p);
        }
        // Éventails dans les coins du haut.
        p.setStrokeWidth(Math.max(1f, dp * 0.8f));
        float fr = Math.min(w, h) * 0.22f;
        for (int k = 0; k < 5; k++) {
            float rr = fr * (1f - k * 0.18f);
            p.setColor(a(accent, 0x22));
            arc.set(-rr, -rr, rr, rr);
            c.drawArc(arc, 0, 90, false, p);
            arc.set(w - rr, -rr, w + rr, rr);
            c.drawArc(arc, 90, 90, false, p);
        }
        // Double filet en haut et en bas.
        p.setColor(a(accent, 0x3A));
        p.setStrokeWidth(Math.max(1f, dp * 0.8f));
        float in = 7 * dp;
        c.drawLine(in, in, w - in, in, p);
        c.drawLine(in, in + 3 * dp, w - in, in + 3 * dp, p);
        c.drawLine(in, h - in, w - in, h - in, p);
        c.drawLine(in, h - in - 3 * dp, w - in, h - in - 3 * dp, p);
        p.setStyle(Paint.Style.FILL);
    }
}
