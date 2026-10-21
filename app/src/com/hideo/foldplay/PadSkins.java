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
 * - Néon : bitume mouillé, colonnes de reflets, enseigne ronde reflétée, ronds de pluie ;
 * - Jade : bambouseraie de près, toit du temple dans la brume, deux lanternes suspendues ;
 * - Argent : patron de pliage de la grue au trait, pleine lune au centre, nuages au pied ;
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
     * branche en haut, vagues et pétales ici ; Ambre : soleil Art déco qui se lève en haut, écailles dorées et soleil suspendu ici.
     */
    private static void render(Canvas c, RectF box, String id, int accent, float dp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float w = box.width(), h = box.height();
        if (id.equals("arctique")) Facets.arctique(c, w, h, accent, Theme.fold(), 0.75f, ARCTIQUE_PAD_POLAIRE ? Facets.MANETTE : 1);
        else if (id.equals("ecarlate")) Facets.ecarlate(c, w, h, accent, Theme.fold(), 0.75f, ECARLATE_PAD_JAPON ? Facets.MANETTE : 1);
        else if (id.equals("sakura") && SAKURA_PAD_RIVIERE) Facets.sakura(c, w, h, accent, Theme.fold(), 0.75f, Facets.MANETTE);
        else if (id.equals("sakura")) sakura(c, box, accent, dp, p);
        else if (id.equals("ambre")) ambre(c, box, accent, dp, p);
        else if (id.equals("neon")) Facets.neon(c, w, h, accent, Theme.fold(), 0.75f, Facets.MANETTE);
        else if (id.equals("jade")) Facets.jade(c, w, h, accent, Theme.fold(), 0.75f, Facets.MANETTE);
        else if (id.equals("argent")) Facets.argent(c, w, h, accent, Theme.fold(), 0.75f, Facets.MANETTE);
        else if (id.equals("pad")) honeycomb(c, box, accent, dp, p);
        else Facets.codec(c, w, h, accent, Theme.fold(), 0.75f, CODEC_PAD_BASE ? Facets.MANETTE : 1);
    }

    /**
     * Façades assorties aux fonds « nuit » de l'écran du haut (Facets.MANETTE), comme le soleil d'Ambre l'est
     * à la ville : false tant que BOSS n'a pas validé, l'ancienne façade reste.
     * Codec : écran radar de la base ; Arctique : banquise sous l'aurore ; Écarlate : lune rouge et torii ;
     * Sakura : eau de la rivière, reflet de lune et cerisiers des berges.
     */
    static boolean CODEC_PAD_BASE = true;
    static boolean ARCTIQUE_PAD_POLAIRE = true;
    static boolean ECARLATE_PAD_JAPON = true;
    static boolean SAKURA_PAD_RIVIERE = true;

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

    // ------------------------------------------------------------ Ambre (0.65)

    /**
     * Ambre, nouvelle manette (0.65) : même univers Art déco que l'écran du haut, autre image. Là-haut
     * le soleil se lève du bas ; ici un éventail d'écailles dorées (coquilles Art déco) couvre la
     * façade, et un petit soleil à rayons alternés pend du bord haut, entre deux filets.
     */
    private static void ambre(Canvas c, RectF b, int accent, float dp, Paint p) {
        float w = b.width(), h = b.height();
        grille(c, b, accent, dp, p);
        // Écailles : rangées d'arcs concentriques en quinconce, trois filets chacun.
        float s = Math.max(22 * dp, Math.min(w, h) * 0.11f);
        p.setStyle(Paint.Style.STROKE);
        RectF arc = new RectF();
        int row = 0;
        for (float y = b.top + s * 0.5f; y < b.bottom + s; y += s * 0.5f, row++) {
            float off = (row & 1) == 0 ? 0 : s * 0.5f;
            for (float x = b.left - s + off; x < b.right + s; x += s) {
                for (int k = 0; k < 3; k++) {
                    float r = s * 0.5f * (1f - k * 0.28f);
                    p.setStrokeWidth(Math.max(1f, dp * (k == 0 ? 1f : 0.7f)));
                    p.setColor(a(accent, k == 0 ? 0x24 : 0x16));
                    arc.set(x - r + s * 0.5f, y - r, x + r + s * 0.5f, y + r);
                    c.drawArc(arc, 0, 180, false, p);
                }
            }
        }
        // Soleil suspendu au bord haut : disque, rayons alternés longs et courts.
        float cx = w / 2, cy = b.top + 6 * dp, R = Math.min(w, h) * 0.34f;
        p.setStyle(Paint.Style.FILL);
        Path ray = new Path();
        int n = 24;
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI * i / n, t1 = Math.PI * (i + 0.7) / n, tm = (t0 + t1) / 2;
            float r = R * ((i & 1) == 0 ? 1f : 0.7f);
            ray.reset();
            ray.moveTo(cx + (float) Math.cos(t0) * R * 0.3f, cy + (float) Math.sin(t0) * R * 0.3f);
            ray.lineTo(cx + (float) Math.cos(tm) * r, cy + (float) Math.sin(tm) * r);
            ray.lineTo(cx + (float) Math.cos(t1) * R * 0.3f, cy + (float) Math.sin(t1) * R * 0.3f);
            ray.close();
            p.setColor(a(accent, (i & 1) == 0 ? 0x30 : 0x1C));
            c.drawPath(ray, p);
        }
        p.setColor(a(accent, 0x30));
        arc.set(cx - R * 0.3f, cy - R * 0.3f, cx + R * 0.3f, cy + R * 0.3f);
        c.drawArc(arc, 0, 180, true, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, dp));
        p.setColor(a(accent, 0x4A));
        c.drawArc(arc, 0, 180, false, p);
        // Double filet en haut et en bas.
        p.setColor(a(accent, 0x3A));
        p.setStrokeWidth(Math.max(1f, dp * 0.8f));
        float in = 7 * dp;
        c.drawLine(in, b.top + in, w - in, b.top + in, p);
        c.drawLine(in, b.top + in + 3 * dp, w - in, b.top + in + 3 * dp, p);
        c.drawLine(in, b.bottom - in, w - in, b.bottom - in, p);
        c.drawLine(in, b.bottom - in - 3 * dp, w - in, b.bottom - in - 3 * dp, p);
        p.setStyle(Paint.Style.FILL);
    }
}
