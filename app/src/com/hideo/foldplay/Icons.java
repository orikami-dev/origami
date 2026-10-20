package com.hideo.foldplay;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Icônes au trait, dessinées sur une grille de 24 × 24 : barre du haut, catégories du XMB, rail des
 * réglages, onglets de l'éditeur, profils d'émulation. Aucune image officielle, seulement des formes
 * simples. Packs (Thème > Pack d'icônes), un par thème, mêmes formes avec un trait et une marque :
 * - trait (Codec) : trait rond moyen ;
 * - givre (Arctique) : trait fin, petit éclat de givre en haut à droite ;
 * - braise (Écarlate) : trait épais, angles vifs, flammèche pliée en bas à droite ;
 * - petale (Sakura) : trait rond, formes fermées teintées, fleur en bas à droite ;
 * - phosphore (Ambre) : trait doublé d'une rémanence décalée, comme un écran à phosphore ; curseur.
 */
final class Icons extends Drawable {
    static final String[] PACKS = {"auto", "trait", "givre", "braise", "petale", "phosphore"};
    static final String[] PACK_NAMES = {"Suit le thème", "Trait", "Givre", "Braise", "Pétale", "Phosphore"};
    static final String[] PACK_HELP = {
        "Le pack du thème choisi",
        "Trait rond, net (Codec)",
        "Trait fin et éclat de givre (Arctique)",
        "Trait épais, angles vifs, flammèche (Écarlate)",
        "Formes teintées et fleur de cerisier (Sakura)",
        "Pointillés de terminal et curseur (Ambre)",
    };
    private static final String[] THEME_PACK = {"trait", "givre", "braise", "petale", "phosphore"};

    /** Pack en service, posé par Theme.sync. */
    static String PACK = "auto";

    static String pack() {
        if (!"auto".equals(PACK)) return PACK;
        return THEME_PACK[Theme.themeIndex(Theme.THEME_ID)];
    }

    static String packName(String id) {
        for (int i = 0; i < PACKS.length; i++) if (PACKS[i].equals(id)) return PACK_NAMES[i];
        return PACK_NAMES[0];
    }

    private final String name;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final int size;

    Icons(String name, int color, int sizePx) {
        this.name = name;
        this.size = sizePx;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
    }

    static Icons of(android.content.Context ctx, String name, int color, float dp) {
        return new Icons(name, color, Math.round(dp * ctx.getResources().getDisplayMetrics().density));
    }

    /** Couleur de la marque du pack (sinon celle de l'icône). */
    private int markColor;

    Icons mark(int color) {
        markColor = color;
        invalidateSelf();
        return this;
    }

    void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidateSelf();
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void draw(Canvas c) {
        android.graphics.Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        c.save();
        c.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
        c.scale(s, s);
        String pk = pack();
        stroke.setStrokeWidth(pk.equals("givre") ? 1.3f : pk.equals("braise") ? 2.4f : pk.equals("phosphore") ? 2f : 1.8f);
        boolean sharp = pk.equals("braise");
        stroke.setStrokeCap(sharp ? Paint.Cap.SQUARE : Paint.Cap.ROUND);
        stroke.setStrokeJoin(sharp ? Paint.Join.MITER : Paint.Join.ROUND);
        path.reset();
        if (pk.equals("phosphore")) {
            // Rémanence : la même forme, pâle et décalée, sous le trait.
            int sc = stroke.getColor(), fc = fill.getColor();
            stroke.setColor((sc & 0x00FFFFFF) | ((((sc >>> 24) & 0xFF) * 35 / 100) << 24));
            fill.setColor((fc & 0x00FFFFFF) | ((((fc >>> 24) & 0xFF) * 35 / 100) << 24));
            c.save();
            c.translate(1.1f, 1.1f);
            shape(c);
            c.restore();
            stroke.setColor(sc);
            fill.setColor(fc);
            path.reset();
        }
        shape(c);
        // Pétale : les formes fermées sont teintées d'un voile de la couleur.
        if (pk.equals("petale") && !path.isEmpty()) {
            int col = fill.getColor();
            fill.setColor((col & 0x00FFFFFF) | ((Math.round(((col >>> 24) & 0xFF) * 0.22f)) << 24));
            c.drawPath(path, fill);
            fill.setColor(col);
        }
        mark(c, pk);
        c.restore();
    }

    /** La marque du pack, petite, dans un coin. */
    private void mark(Canvas c, String pk) {
        int base = fill.getColor(), baseStroke = stroke.getColor();
        if (markColor != 0) {
            fill.setColor(markColor);
            stroke.setColor(markColor);
        }
        markShape(c, pk);
        fill.setColor(base);
        stroke.setColor(baseStroke);
    }

    private void markShape(Canvas c, String pk) {
        int col = fill.getColor();
        if (pk.equals("givre")) {
            // Éclat à quatre branches. Sur Actualiser, le coin du haut est celui de la pointe de
            // flèche : l'éclat descend en bas à droite pour ne pas s'y coller (0.60.5).
            float dy = name.equals("refresh") ? 17 : 0;
            stroke.setStrokeWidth(0.9f);
            c.drawLine(20.5f, 1.5f + dy, 20.5f, 5.5f + dy, stroke);
            c.drawLine(18.5f, 3.5f + dy, 22.5f, 3.5f + dy, stroke);
        } else if (pk.equals("braise")) {
            // Flammèche pliée : losange en deux faces.
            path.reset();
            path.moveTo(21, 23);
            path.lineTo(23, 19.5f);
            path.lineTo(21, 16);
            path.close();
            c.drawPath(path, fill);
            fill.setColor((col & 0x00FFFFFF) | ((((col >>> 24) & 0xFF) / 2) << 24));
            path.reset();
            path.moveTo(21, 23);
            path.lineTo(19, 19.5f);
            path.lineTo(21, 16);
            path.close();
            c.drawPath(path, fill);
            fill.setColor(col);
        } else if (pk.equals("petale")) {
            for (int i = 0; i < 5; i++) {
                double a = -Math.PI / 2 + i * Math.PI * 2 / 5;
                c.drawCircle(20.5f + (float) Math.cos(a) * 1.3f, 20.5f + (float) Math.sin(a) * 1.3f, 0.9f, fill);
            }
        } else if (pk.equals("phosphore")) {
            r.set(19.5f, 19, 22.5f, 23);
            c.drawRect(r, fill);
        }
    }

    private void shape(Canvas c) {
        switch (name) {
            case "menu":
                c.drawLine(4, 7, 20, 7, stroke);
                c.drawLine(4, 12, 20, 12, stroke);
                c.drawLine(4, 17, 20, 17, stroke);
                break;
            case "close":
                c.drawLine(6, 6, 18, 18, stroke);
                c.drawLine(18, 6, 6, 18, stroke);
                break;
            case "back":
                c.drawLine(19, 12, 5, 12, stroke);
                c.drawLine(11, 6, 5, 12, stroke);
                c.drawLine(11, 18, 5, 12, stroke);
                break;
            case "library":
                // Trois boîtiers de jeux rangés.
                r.set(4, 5, 8, 19);
                c.drawRoundRect(r, 1, 1, stroke);
                r.set(10, 5, 14, 19);
                c.drawRoundRect(r, 1, 1, stroke);
                path.moveTo(16, 6.5f);
                path.lineTo(19.5f, 5.5f);
                path.lineTo(21, 18.5f);
                path.lineTo(17.5f, 19.5f);
                path.close();
                c.drawPath(path, stroke);
                break;
            case "emulators":
            case "pads":
                // Manette.
                path.moveTo(7, 8);
                path.lineTo(17, 8);
                path.cubicTo(20.5f, 8, 22, 16, 20, 18);
                path.cubicTo(18.5f, 19.5f, 16.5f, 17, 15.5f, 15.5f);
                path.lineTo(8.5f, 15.5f);
                path.cubicTo(7.5f, 17, 5.5f, 19.5f, 4, 18);
                path.cubicTo(2, 16, 3.5f, 8, 7, 8);
                path.close();
                c.drawPath(path, stroke);
                c.drawLine(7, 10.5f, 7, 13.5f, stroke);
                c.drawLine(5.5f, 12, 8.5f, 12, stroke);
                c.drawCircle(16, 11, 0.9f, fill);
                c.drawCircle(17.8f, 13, 0.9f, fill);
                break;
            case "cores":
                // Puce.
                r.set(7, 7, 17, 17);
                c.drawRoundRect(r, 1.5f, 1.5f, stroke);
                r.set(10, 10, 14, 14);
                c.drawRect(r, fill);
                for (int i = 0; i < 3; i++) {
                    float p = 9 + i * 3;
                    c.drawLine(p, 4, p, 7, stroke);
                    c.drawLine(p, 17, p, 20, stroke);
                    c.drawLine(4, p, 7, p, stroke);
                    c.drawLine(17, p, 20, p, stroke);
                }
                break;
            case "covers":
                // Image : cadre, montagne, soleil.
                r.set(4, 5, 20, 19);
                c.drawRoundRect(r, 2, 2, stroke);
                path.moveTo(5, 17);
                path.lineTo(10, 11);
                path.lineTo(13.5f, 15);
                path.lineTo(15.5f, 13);
                path.lineTo(19, 17);
                c.drawPath(path, stroke);
                c.drawCircle(15.5f, 9, 1.3f, fill);
                break;
            case "display":
                r.set(3, 5, 21, 16);
                c.drawRoundRect(r, 1.5f, 1.5f, stroke);
                c.drawLine(12, 16, 12, 19, stroke);
                c.drawLine(8, 19.5f, 16, 19.5f, stroke);
                break;
            case "monitoring":
                // Courbe de mesures dans un cadre (ATH, RTSS).
                r.set(3.5f, 4.5f, 20.5f, 19.5f);
                c.drawRoundRect(r, 2, 2, stroke);
                path.moveTo(6.5f, 14.5f);
                path.lineTo(9.5f, 14.5f);
                path.lineTo(11, 9.5f);
                path.lineTo(13.5f, 16);
                path.lineTo(15, 12.5f);
                path.lineTo(17.5f, 12.5f);
                c.drawPath(path, stroke);
                break;
            case "sound":
                // Haut-parleur et deux ondes.
                path.moveTo(4, 9.5f);
                path.lineTo(7.5f, 9.5f);
                path.lineTo(12, 5.5f);
                path.lineTo(12, 18.5f);
                path.lineTo(7.5f, 14.5f);
                path.lineTo(4, 14.5f);
                path.close();
                c.drawPath(path, stroke);
                r.set(11, 8.5f, 18, 15.5f);
                c.drawArc(r, -50, 100, false, stroke);
                r.set(10, 5, 21.5f, 19);
                c.drawArc(r, -50, 100, false, stroke);
                break;
            case "a11y":
                // Silhouette bras ouverts dans un cercle (accessibilité).
                c.drawCircle(12, 12, 9.5f, stroke);
                c.drawCircle(12, 7.2f, 1.4f, fill);
                c.drawLine(6.5f, 9.8f, 17.5f, 9.8f, stroke);
                c.drawLine(12, 9.8f, 12, 13.5f, stroke);
                c.drawLine(12, 13.5f, 9.5f, 18, stroke);
                c.drawLine(12, 13.5f, 14.5f, 18, stroke);
                break;
            case "updates":
                // Flèche circulaire et flèche vers le bas.
                r.set(4, 4, 20, 20);
                c.drawArc(r, -60, 300, false, stroke);
                c.drawLine(16, 4.2f, 20, 5.3f, stroke);
                c.drawLine(16, 4.2f, 17, 8.2f, stroke);
                c.drawLine(12, 8, 12, 15.5f, stroke);
                c.drawLine(9, 12.8f, 12, 15.8f, stroke);
                c.drawLine(15, 12.8f, 12, 15.8f, stroke);
                break;
            case "about":
                c.drawCircle(12, 12, 8.5f, stroke);
                c.drawLine(12, 11, 12, 16.5f, stroke);
                c.drawCircle(12, 7.8f, 1.1f, fill);
                break;
            case "help":
                c.drawCircle(12, 12, 8.5f, stroke);
                path.moveTo(9.5f, 9.5f);
                path.cubicTo(9.5f, 6.5f, 14.5f, 6.5f, 14.5f, 9.5f);
                path.cubicTo(14.5f, 11.5f, 12, 11.5f, 12, 13.8f);
                c.drawPath(path, stroke);
                c.drawCircle(12, 16.8f, 1.1f, fill);
                break;
            case "performance":
                // Éclair.
                path.moveTo(13.5f, 3);
                path.lineTo(6, 13.5f);
                path.lineTo(11.5f, 13.5f);
                path.lineTo(10.5f, 21);
                path.lineTo(18, 10.5f);
                path.lineTo(12.5f, 10.5f);
                path.close();
                c.drawPath(path, stroke);
                break;
            case "balanced":
                // Balance.
                c.drawLine(12, 4, 12, 20, stroke);
                c.drawLine(8, 20, 16, 20, stroke);
                c.drawLine(5, 7, 19, 7, stroke);
                path.moveTo(5, 7);
                path.lineTo(2.5f, 13);
                path.lineTo(7.5f, 13);
                path.close();
                path.moveTo(19, 7);
                path.lineTo(16.5f, 13);
                path.lineTo(21.5f, 13);
                path.close();
                c.drawPath(path, stroke);
                break;
            case "quality":
                // Diamant.
                path.moveTo(7, 5);
                path.lineTo(17, 5);
                path.lineTo(21, 10);
                path.lineTo(12, 20);
                path.lineTo(3, 10);
                path.close();
                c.drawPath(path, stroke);
                c.drawLine(3, 10, 21, 10, stroke);
                path.reset();
                path.moveTo(9.5f, 5);
                path.lineTo(8, 10);
                path.lineTo(12, 20);
                path.lineTo(16, 10);
                path.lineTo(14.5f, 5);
                c.drawPath(path, stroke);
                break;
            case "keys":
                // Éditeur : touches.
                c.drawCircle(8, 8, 3, stroke);
                c.drawCircle(16, 8, 3, stroke);
                c.drawCircle(8, 16, 3, stroke);
                c.drawCircle(16, 16, 3, stroke);
                break;
            case "colors":
                c.drawCircle(12, 12, 8.5f, stroke);
                c.drawCircle(9, 9, 1.4f, fill);
                c.drawCircle(15, 9, 1.4f, fill);
                c.drawCircle(8, 14, 1.4f, fill);
                c.drawCircle(13, 16, 1.4f, fill);
                break;
            case "layouts":
                r.set(4, 4, 11, 11);
                c.drawRoundRect(r, 1.5f, 1.5f, stroke);
                r.set(13, 4, 20, 11);
                c.drawRoundRect(r, 1.5f, 1.5f, stroke);
                r.set(4, 13, 20, 20);
                c.drawRoundRect(r, 1.5f, 1.5f, stroke);
                break;
            case "vibration":
                r.set(8, 4, 16, 20);
                c.drawRoundRect(r, 2, 2, stroke);
                c.drawLine(4.5f, 9, 4.5f, 15, stroke);
                c.drawLine(19.5f, 9, 19.5f, 15, stroke);
                break;
            case "star":
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI / 2 + i * Math.PI / 5;
                    float rr = i % 2 == 0 ? 9f : 4f;
                    float x = 12 + (float) Math.cos(a) * rr;
                    float y = 12.5f - (float) Math.sin(a) * rr;
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
                c.drawPath(path, stroke);
                break;
            case "clock":
                c.drawCircle(12, 12, 8.5f, stroke);
                c.drawLine(12, 12, 12, 7, stroke);
                c.drawLine(12, 12, 15.5f, 14, stroke);
                break;
            case "grid":
                // Vue Washi : grille de vignettes.
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        r.set(4 + i * 9, 4 + j * 9, 11 + i * 9, 11 + j * 9);
                        c.drawRoundRect(r, 1.5f, 1.5f, stroke);
                    }
                }
                break;
            case "flex":
                // Mode Tsuru : pliant à moitié ouvert, écran haut incliné.
                path.moveTo(4, 19);
                path.lineTo(20, 19);
                path.lineTo(20, 14);
                path.lineTo(4, 14);
                path.close();
                path.moveTo(4, 14);
                path.lineTo(7, 4);
                path.lineTo(22, 4);
                path.lineTo(20, 14);
                c.drawPath(path, stroke);
                break;
            case "refresh": {
                // Un seul arc (de 30° à 320°, sens horaire) qui se termine par une pointe pleine posée
                // dans l'axe de sa tangente : la flèche prolonge le trait au lieu de flotter à côté.
                float sw = stroke.getStrokeWidth();
                r.set(4.5f, 4.5f, 19.5f, 19.5f);
                c.drawArc(r, 45, 275, false, stroke);
                double e = Math.toRadians(320);
                float px = 12 + 7.5f * (float) Math.cos(e), py = 12 + 7.5f * (float) Math.sin(e);
                float tx = (float) -Math.sin(e), ty = (float) Math.cos(e);
                float nx = (float) Math.cos(e), ny = (float) Math.sin(e);
                float half = 2.1f + sw * 0.55f, len = 2.6f + sw * 0.7f;
                path.reset();
                path.moveTo(px + tx * len, py + ty * len);
                path.lineTo(px + nx * half - tx * 0.4f, py + ny * half - ty * 0.4f);
                path.lineTo(px - nx * half - tx * 0.4f, py - ny * half - ty * 0.4f);
                path.close();
                c.drawPath(path, fill);
                path.reset();
                break;
            }
            case "palette":
                path.moveTo(12, 3.5f);
                path.cubicTo(6.5f, 3.5f, 3.5f, 7.5f, 3.5f, 12);
                path.cubicTo(3.5f, 16.5f, 7, 20.5f, 11.5f, 20.5f);
                path.cubicTo(13.5f, 20.5f, 13.5f, 18, 12.5f, 17);
                path.cubicTo(11.5f, 15.5f, 12.5f, 14, 14.5f, 14);
                path.lineTo(17, 14);
                path.cubicTo(19.5f, 14, 20.5f, 12.5f, 20.5f, 10.5f);
                path.cubicTo(20.5f, 6.5f, 16.5f, 3.5f, 12, 3.5f);
                path.close();
                c.drawPath(path, stroke);
                c.drawCircle(8, 10, 1.2f, fill);
                c.drawCircle(11.5f, 7.2f, 1.2f, fill);
                c.drawCircle(15.5f, 8.5f, 1.2f, fill);
                break;
            case "settings":
                // Engrenage à huit dents (chaque dent : pied, sommet, sommet, pied).
                for (int i = 0; i < 32; i++) {
                    double a = Math.PI * 2 * (i + 0.5) / 32;
                    float rr = (i % 4 == 1 || i % 4 == 2) ? 9.2f : 6.9f;
                    float x = 12 + (float) Math.cos(a) * rr, y = 12 + (float) Math.sin(a) * rr;
                    if (i == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                path.close();
                c.drawPath(path, stroke);
                c.drawCircle(12, 12, 2.8f, stroke);
                break;
            case "apps":
                // Applications : neuf points.
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) c.drawCircle(6 + i * 6, 6 + j * 6, 1.7f, fill);
                }
                break;
            case "share":
                c.drawCircle(17.5f, 6, 2.5f, stroke);
                c.drawCircle(6.5f, 12, 2.5f, stroke);
                c.drawCircle(17.5f, 18, 2.5f, stroke);
                c.drawLine(8.7f, 10.8f, 15.3f, 7.2f, stroke);
                c.drawLine(8.7f, 13.2f, 15.3f, 16.8f, stroke);
                break;
            default:
                c.drawCircle(12, 12, 3, fill);
        }
    }

    @Override
    public void setAlpha(int a) {
        stroke.setAlpha(a);
        fill.setAlpha(a);
    }

    @Override
    public void setColorFilter(ColorFilter f) {
        stroke.setColorFilter(f);
        fill.setColorFilter(f);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
