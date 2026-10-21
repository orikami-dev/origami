package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.View;

/**
 * Emblème de console sur fond transparent : une silhouette simplifiée de chaque machine (aplat
 * avec découpes, même style partout) et le sigle au pochoir. Ça évoque la console sans la copier :
 * aucun logo, aucune marque, proportions et détails volontairement simplifiés.
 */
final class ConsoleEmblemView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private SystemInfo system;

    ConsoleEmblemView(Context ctx) {
        super(ctx);
        // Les découpes percent vraiment le dessin : le fond reste transparent.
        cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        label.setTypeface(Fonts.logo(ctx));
        label.setTextAlign(Paint.Align.CENTER);
    }

    void setSystem(SystemInfo s) {
        system = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (system == null) return;
        float w = getWidth();
        float h = getHeight();
        // Le pack d'icônes du thème habille aussi les emblèmes : même silhouette, autre finition.
        String pk = Icons.pack();
        int accent = Theme.CODEC;
        int tint = Theme.lighten(system.color, 0.35f);
        if (pk.equals("givre")) tint = Theme.blend(tint, 0xFFE6F4FF, 0.4f);
        else if (!pk.equals("trait")) tint = Theme.blend(tint, accent, 0.25f);
        fill.setColor(tint);
        label.setColor(tint);

        // Le pictogramme occupe les deux tiers du haut, le sigle le tiers du bas.
        // Paramètres : pas de sigle, son nom est déjà écrit dessous ; le pictogramme prend toute la place.
        // 0.66.16 (BOSS) : seules les consoles et Remote Play gardent un sigle ; les autres ont leur nom écrit dessous.
        boolean bare = system == SystemInfo.SETTINGS || system == SystemInfo.ANDROID || system == SystemInfo.ACTION;
        float size = bare ? Math.min(w, h) * 0.86f : Math.min(w, h * 0.62f);
        float cx = w / 2f;
        float cy = bare ? h / 2f : h * 0.34f;
        if (pk.equals("phosphore")) {
            // Rémanence : la silhouette, pâle et décalée, sous l'emblème.
            int ghost = c.saveLayerAlpha(0, 0, w, h, 110);
            c.translate(size * 0.035f, size * 0.035f);
            silhouette(c, cx, cy, size);
            c.restoreToCount(ghost);
        }
        int layer = c.saveLayer(0, 0, w, h, null);
        silhouette(c, cx, cy, size);
        finish(c, pk, cx, cy, size);
        c.restoreToCount(layer);

        if (bare) return;
        label.setTextSize(Math.min(h * 0.2f, w / Math.max(3, system.shortName.length()) * 1.1f));
        c.drawText(system.shortName, cx, h * 0.9f, label);
        mark(c, pk, cx + label.measureText(system.shortName) / 2f, h * 0.9f, label.getTextSize(), accent);
    }

    private void silhouette(Canvas c, float cx, float cy, float size) {
        if (!shape(c, system.id, cx, cy, size)) switch (family(system.id)) {
            case "handheld_v":
                handheldVertical(c, cx, cy, size);
                break;
            case "handheld_h":
                handheldHorizontal(c, cx, cy, size);
                break;
            case "dual":
                dualScreen(c, cx, cy, size);
                break;
            case "disc":
                disc(c, cx, cy, size);
                break;
            default:
                pad(c, cx, cy, size);
                break;
        }
    }

    private final Paint over = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Finition du pack, peinte seulement sur la silhouette (SRC_ATOP, les découpes restent vides). */
    private void finish(Canvas c, String pk, float cx, float cy, float s) {
        over.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        over.setStyle(Paint.Style.FILL);
        float l = cx - s * 0.5f, t = cy - s * 0.5f, rr = cx + s * 0.5f, b = cy + s * 0.5f;
        if (pk.equals("givre")) {
            // Reflets de givre : trois traits fins en biais.
            over.setStyle(Paint.Style.STROKE);
            over.setColor(0x66FFFFFF);
            over.setStrokeWidth(Math.max(1f, s * 0.022f));
            for (int i = 0; i < 3; i++) {
                float x = l + s * (0.18f + i * 0.09f);
                c.drawLine(x, b, x + s * 0.6f, t, over);
            }
        } else if (pk.equals("braise")) {
            // Pli : la moitié basse-droite passe dans l'ombre, comme une feuille pliée en deux.
            path.reset();
            path.moveTo(rr, t);
            path.lineTo(rr, b);
            path.lineTo(l, b);
            path.close();
            over.setColor(0x52000000);
            c.drawPath(path, over);
        } else if (pk.equals("petale")) {
            // Voile clair et arrondi en haut à gauche.
            over.setColor(0x4AFFFFFF);
            c.drawCircle(l + s * 0.12f, t + s * 0.12f, s * 0.52f, over);
        } else if (pk.equals("phosphore")) {
            // Lignes de balayage.
            over.setColor(0x47000000);
            float step = Math.max(3f, s * 0.04f);
            for (float y = t; y < b; y += step) c.drawRect(l, y, rr, y + step * 0.4f, over);
        } else if (pk.equals("neon")) {
            // Reflet sur le bitume mouillé : le bas prend la lueur cyan du thème.
            over.setColor(Theme.alpha(Theme.fold(), 0x48));
            c.drawRect(l, t + s * 0.68f, rr, b, over);
            over.setColor(0x30000000);
            c.drawRect(l, t + s * 0.66f, rr, t + s * 0.68f, over);
        } else if (pk.equals("bambou")) {
            // Tiges de bambou : fines rayures verticales et un nœud.
            over.setColor(0x2E000000);
            float step = Math.max(4f, s * 0.12f);
            for (float x = l; x < rr; x += step) c.drawRect(x, t, x + step * 0.18f, b, over);
            c.drawRect(l, t + s * 0.55f, rr, t + s * 0.57f, over);
        } else if (pk.equals("lune")) {
            // Clair de lune : halo pâle en haut à droite.
            over.setColor(0x3CFFFFFF);
            c.drawCircle(rr - s * 0.12f, t + s * 0.12f, s * 0.45f, over);
        }
    }

    /** La marque du pack, à la couleur du thème, juste après le sigle. */
    private void mark(Canvas c, String pk, float x, float base, float ts, int accent) {
        float m = ts * 0.32f;
        float mx = x + ts * 0.18f + m;
        float my = base - ts * 0.62f;
        over.setXfermode(null);
        over.setColor(accent);
        over.setStyle(Paint.Style.FILL);
        if (pk.equals("givre")) {
            over.setStyle(Paint.Style.STROKE);
            over.setStrokeWidth(Math.max(1f, ts * 0.07f));
            c.drawLine(mx, my - m, mx, my + m, over);
            c.drawLine(mx - m, my, mx + m, my, over);
        } else if (pk.equals("braise")) {
            path.reset();
            path.moveTo(mx, my + m);
            path.lineTo(mx + m * 0.6f, my);
            path.lineTo(mx, my - m);
            path.close();
            c.drawPath(path, over);
            over.setColor(Theme.alpha(accent, 0x80));
            path.reset();
            path.moveTo(mx, my + m);
            path.lineTo(mx - m * 0.6f, my);
            path.lineTo(mx, my - m);
            path.close();
            c.drawPath(path, over);
        } else if (pk.equals("petale")) {
            for (int i = 0; i < 5; i++) {
                double a = -Math.PI / 2 + i * Math.PI * 2 / 5;
                c.drawCircle(mx + (float) Math.cos(a) * m * 0.55f, my + (float) Math.sin(a) * m * 0.55f, m * 0.4f, over);
            }
        } else if (pk.equals("phosphore")) {
            // Curseur plein, posé sur la ligne du sigle.
            c.drawRect(x + ts * 0.2f, base - ts * 0.7f, x + ts * 0.2f + ts * 0.4f, base, over);
        } else if (pk.equals("neon")) {
            // Goutte de pluie.
            path.reset();
            path.moveTo(mx, my - m);
            path.quadTo(mx + m * 0.75f, my + m * 0.1f, mx + m * 0.6f, my + m * 0.45f);
            path.quadTo(mx, my + m * 1.05f, mx - m * 0.6f, my + m * 0.45f);
            path.quadTo(mx - m * 0.75f, my + m * 0.1f, mx, my - m);
            path.close();
            c.drawPath(path, over);
        } else if (pk.equals("bambou")) {
            path.reset();
            path.moveTo(mx - m, my + m);
            path.lineTo(mx, my + m * 0.2f);
            path.lineTo(mx + m, my - m);
            path.close();
            c.drawPath(path, over);
            over.setColor(Theme.alpha(accent, 0x80));
            path.reset();
            path.moveTo(mx - m, my + m);
            path.lineTo(mx + m * 0.4f, my + m * 0.4f);
            path.lineTo(mx + m, my - m);
            path.close();
            c.drawPath(path, over);
        } else if (pk.equals("lune")) {
            c.drawCircle(mx, my, m * 0.6f, over);
            over.setStyle(Paint.Style.STROKE);
            over.setStrokeWidth(Math.max(1f, ts * 0.04f));
            c.drawCircle(mx, my, m, over);
            over.setStyle(Paint.Style.FILL);
        }
    }

    /**
     * Silhouettes, en fractions de la taille (centre 0,0, de -0,5 à 0,5), opérations séparées par « ; » :
     * « F » remplit, « C » découpe, « S » découpe au trait ; r = rectangle arrondi (x0 y0 x1 y1 rayon),
     * o = disque (x y r, plus l'épaisseur pour un anneau S), p = polygone (x y …), l = trait
     * (x0 y0 x1 y1 épaisseur), x = croix directionnelle (x y taille).
     */
    private static final java.util.Map<String, String> SHAPES = new java.util.HashMap<String, String>();
    static {
        SHAPES.put("nes", "F r -0.48 -0.22 0.48 0.26 0.03;S l -0.46 -0.06 0.46 -0.06 0.018;C r -0.40 0.10 -0.30 0.18 0.01;C r -0.24 0.10 -0.14 0.18 0.01;C r 0.00 0.10 0.10 0.15 0.01;C r 0.14 0.10 0.24 0.15 0.01;S l 0.30 -0.18 0.44 -0.18 0.012;S l 0.30 -0.13 0.44 -0.13 0.012");
        SHAPES.put("snes", "F r -0.48 -0.18 0.48 0.26 0.12;F r -0.22 -0.30 0.22 -0.12 0.04;C r -0.16 -0.25 0.16 -0.21 0.01;C r -0.36 -0.04 -0.24 0.04 0.02;C r 0.24 -0.04 0.36 0.04 0.02;C r -0.05 0.00 0.05 0.06 0.01;C r -0.38 0.15 -0.28 0.21 0.01;C r -0.22 0.15 -0.12 0.21 0.01");
        SHAPES.put("n64", "F p -0.48 0.26 -0.42 -0.12 -0.18 -0.20 0.18 -0.20 0.42 -0.12 0.48 0.26;F r -0.18 -0.32 0.18 -0.14 0.03;C r -0.12 -0.28 0.12 -0.24 0.01;C r -0.34 0.12 -0.26 0.20 0.01;C r -0.18 0.12 -0.10 0.20 0.01;C r 0.10 0.12 0.18 0.20 0.01;C r 0.26 0.12 0.34 0.20 0.01;C o -0.30 -0.02 0.035;C o 0.30 -0.02 0.035");
        SHAPES.put("gc", "F r -0.30 -0.46 0.30 -0.28 0.06;C r -0.22 -0.42 0.22 -0.32 0.03;F r -0.36 -0.30 0.36 0.40 0.05;S o 0 0.02 0.20 0.025;C o 0 0.02 0.06;C r -0.30 0.30 -0.20 0.36 0.01;C r -0.16 0.30 -0.06 0.36 0.01;C r 0.06 0.30 0.16 0.36 0.01;C r 0.20 0.30 0.30 0.36 0.01");
        SHAPES.put("wii", "F r -0.14 -0.46 0.14 0.42 0.06;S l 0 -0.38 0 0.16 0.025;C o 0 0.28 0.03;F r -0.24 0.40 0.24 0.48 0.03");
        SHAPES.put("switch", "F r -0.50 -0.24 -0.30 0.24 0.12;F r -0.33 -0.24 0.33 0.24 0.02;F r 0.30 -0.24 0.50 0.24 0.12;S l -0.335 -0.24 -0.335 0.24 0.015;S l 0.335 -0.24 0.335 0.24 0.015;C r -0.27 -0.18 0.27 0.18 0.01;C o -0.42 -0.09 0.045;C x -0.42 0.10 0.10;C o 0.42 -0.15 0.02;C o 0.42 -0.03 0.02;C o 0.36 -0.09 0.02;C o 0.48 -0.09 0.02;C o 0.42 0.10 0.045");
        SHAPES.put("gb", "F p -0.32 -0.48 0.32 -0.48 0.32 0.30 0.30 0.38 0.25 0.44 0.18 0.48 -0.32 0.48;C r -0.25 -0.41 0.25 -0.02 0.02;F r -0.17 -0.34 0.17 -0.08 0;C x -0.15 0.15 0.17;C o 0.10 0.19 0.045;C o 0.22 0.12 0.045;C r -0.09 0.32 -0.02 0.345 0.01;C r 0.02 0.32 0.09 0.345 0.01;S l 0.16 0.42 0.26 0.34 0.012;S l 0.12 0.40 0.24 0.30 0.012");
        SHAPES.put("gbc", "F p -0.28 -0.46 0.28 -0.46 0.28 0.28 0.22 0.40 0.10 0.46 -0.10 0.46 -0.22 0.40 -0.28 0.28;C r -0.22 -0.38 0.22 -0.04 0.04;F r -0.15 -0.31 0.15 -0.10 0;C x -0.12 0.15 0.14;C o 0.09 0.18 0.04;C o 0.19 0.11 0.04;C r -0.06 0.33 0.06 0.355 0.01");
        SHAPES.put("gba", "F r -0.50 -0.22 0.50 0.24 0.20;C r -0.23 -0.17 0.23 0.15 0.02;F r -0.17 -0.12 0.17 0.10 0;C x -0.36 0.01 0.12;C o 0.33 0.05 0.04;C o 0.42 -0.03 0.04");
        SHAPES.put("nds", "F r -0.40 -0.48 0.40 -0.03 0.05;C r -0.18 -0.42 0.18 -0.10 0.01;C o -0.30 -0.26 0.02;C o 0.30 -0.26 0.02;F r -0.40 0.03 0.40 0.48 0.05;C r -0.18 0.09 0.18 0.40 0.01;C x -0.30 0.24 0.11;C o 0.30 0.18 0.022;C o 0.30 0.30 0.022;C o 0.25 0.24 0.022;C o 0.35 0.24 0.022");
        SHAPES.put("3ds", "F r -0.42 -0.48 0.42 -0.03 0.05;C r -0.32 -0.42 0.32 -0.10 0.01;F r -0.40 0.03 0.40 0.48 0.05;C r -0.18 0.09 0.18 0.40 0.01;C o -0.30 0.15 0.05;C x -0.30 0.34 0.09;C o 0.30 0.18 0.022;C o 0.30 0.30 0.022;C o 0.25 0.24 0.022;C o 0.35 0.24 0.022");
        SHAPES.put("psx", "F r -0.48 -0.22 0.48 0.26 0.04;S o -0.12 0.02 0.19 0.022;C o -0.12 0.02 0.045;C o 0.26 -0.08 0.04;C o 0.38 -0.08 0.04;C o 0.32 0.10 0.045;S l -0.48 0.20 0.48 0.20 0.012");
        SHAPES.put("ps2", "F r -0.20 -0.46 0.20 0.42 0.03;S l -0.20 -0.06 0.20 -0.06 0.025;S l -0.10 -0.40 -0.10 -0.12 0.02;C o 0.10 -0.38 0.02;S l -0.12 0.08 0.12 0.08 0.012;S l -0.12 0.14 0.12 0.14 0.012;S l -0.12 0.20 0.12 0.20 0.012;S l -0.12 0.26 0.12 0.26 0.012;F r -0.26 0.40 0.26 0.48 0.02");
        // PS3 slim couchée : bloc arrondi, fente du disque, voyant.
        SHAPES.put("x360", "F r -0.46 -0.18 0.46 0.18 0.07;S l -0.38 -0.06 0.18 -0.06 0.012;C o 0.31 0.00 0.065;S l -0.38 0.07 0.18 0.07 0.008");
        SHAPES.put("ps3", "F r -0.48 -0.20 0.48 0.20 0.10;S l -0.40 -0.04 0.30 -0.04 0.014;C o 0.38 -0.04 0.022;S l -0.40 0.08 0.40 0.08 0.008;F r -0.34 0.20 0.34 0.26 0.02");
        SHAPES.put("psp", "F r -0.50 -0.20 0.50 0.20 0.20;C r -0.26 -0.14 0.26 0.14 0.01;C x -0.38 -0.03 0.10;C o 0.38 -0.09 0.022;C o 0.38 0.03 0.022;C o 0.32 -0.03 0.022;C o 0.44 -0.03 0.022;C o -0.38 0.11 0.025");
        SHAPES.put("vita", "F r -0.50 -0.21 0.50 0.21 0.21;C r -0.25 -0.15 0.25 0.15 0.03;C x -0.38 -0.07 0.10;C o 0.38 -0.12 0.02;C o 0.38 -0.02 0.02;C o 0.33 -0.07 0.02;C o 0.43 -0.07 0.02;C o -0.35 0.11 0.035;C o 0.35 0.11 0.035");
        SHAPES.put("md", "F r -0.48 -0.16 0.48 0.26 0.06;S o 0.12 0.05 0.19 0.03;C r -0.04 0.02 0.28 0.08 0.01;C r -0.42 -0.02 -0.30 0.03 0.01;C r -0.42 0.11 -0.30 0.16 0.01;S l -0.48 0.21 -0.10 0.21 0.012");
        SHAPES.put("sms", "F p -0.48 0.24 -0.48 -0.06 -0.30 -0.20 0.48 -0.20 0.48 0.24;C r -0.06 -0.15 0.34 -0.10 0.01;S l -0.48 0.10 0.48 0.10 0.025;C r -0.40 -0.01 -0.30 0.04 0.01;C r -0.24 -0.01 -0.14 0.04 0.01");
        SHAPES.put("gg", "F r -0.50 -0.22 0.50 0.22 0.10;C r -0.21 -0.18 0.21 0.17 0.02;F r -0.15 -0.12 0.15 0.11 0;C x -0.37 0.01 0.12;C o 0.33 0.05 0.04;C o 0.43 -0.02 0.04;C o 0.37 -0.13 0.02");
        SHAPES.put("saturn", "F r -0.48 -0.22 0.48 0.26 0.08;S o 0 0.0 0.18 0.022;C o 0 0.0 0.05;C o -0.36 -0.08 0.035;C o 0.36 -0.08 0.035;C r -0.38 0.13 -0.26 0.19 0.01;C r 0.26 0.13 0.38 0.19 0.01");
        SHAPES.put("dc", "F r -0.38 -0.34 0.38 0.38 0.06;S o 0 -0.04 0.24 0.022;C o 0 -0.04 0.06;C o -0.29 -0.26 0.025;C r -0.31 0.28 -0.22 0.34 0.01;C r -0.16 0.28 -0.07 0.34 0.01;C r 0.07 0.28 0.16 0.34 0.01;C r 0.22 0.28 0.31 0.34 0.01");
        SHAPES.put("pce", "F r -0.30 -0.30 0.30 0.30 0.04;C r -0.20 0.15 0.20 0.21 0.01;S l -0.22 -0.20 0.22 -0.20 0.015;S l -0.22 -0.12 0.22 -0.12 0.015;S l -0.22 -0.04 0.22 -0.04 0.015;C o 0.20 0.06 0.025");
        // Catégories d'applications (0.60.5) : un téléphone tenu à plat avec une manette à l'écran,
        // et un écran qui reçoit le jeu avec une manette devant. Aucun logo de plateforme.
        SHAPES.put("android", "F r -0.48 -0.25 0.48 0.25 0.09;C r -0.37 -0.19 0.37 0.19 0.02;F r -0.25 -0.10 0.25 0.12 0.10;C x -0.12 0.01 0.09;C o 0.09 -0.02 0.028;C o 0.16 0.05 0.028;C o 0.43 0 0.018");
        SHAPES.put("remote", "F r -0.46 -0.40 0.30 0.12 0.04;C r -0.40 -0.34 0.24 0.06 0.01;F p -0.14 -0.24 -0.14 -0.04 0.04 -0.14;F r -0.14 0.12 -0.02 0.20 0.01;F r -0.26 0.20 0.10 0.24 0.02;C r -0.01 0.03 0.51 0.43 0.14;F r 0.03 0.07 0.47 0.39 0.12;C x 0.15 0.23 0.09;C o 0.33 0.19 0.028;C o 0.40 0.26 0.028");
        // Engrenage : disque, huit dents, trou central.
        SHAPES.put("settings", "F o 0 0 0.32;F r -0.07 -0.46 0.07 0.46 0.03;F r -0.46 -0.07 0.46 0.07 0.03;F p 0.375 0.276 0.276 0.375 -0.375 -0.276 -0.276 -0.375;F p -0.375 0.276 -0.276 0.375 0.375 -0.276 0.276 -0.375;C o 0 0 0.13");
        SHAPES.put("arcade", "F p -0.30 -0.48 0.30 -0.48 0.30 -0.30 0.24 -0.26 0.24 0.02 0.36 0.14 0.36 0.22 0.26 0.22 0.26 0.48 -0.26 0.48 -0.26 0.22 -0.36 0.22 -0.36 0.14 -0.24 0.02 -0.24 -0.26 -0.30 -0.30;S l -0.24 -0.30 0.24 -0.30 0.02;C r -0.18 -0.22 0.18 -0.02 0.02;C o -0.14 0.15 0.03;S l -0.14 0.15 -0.14 0.07 0.014;C o 0.04 0.16 0.022;C o 0.12 0.16 0.022;C o 0.20 0.16 0.022;C r -0.06 0.30 0.06 0.40 0.01");
    }

    private final android.graphics.Path path = new android.graphics.Path();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Dessine une silhouette de SHAPES ; faux si la console n'en a pas. */
    private boolean shape(Canvas c, String id, float cx, float cy, float s) {
        String spec = SHAPES.get(id);
        if (spec == null) return false;
        line.setXfermode(cut.getXfermode());
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.BUTT);
        for (String op : spec.split(";")) {
            String[] t = op.trim().split(" +");
            float[] a = new float[t.length - 2];
            for (int i = 0; i < a.length; i++) a[i] = Float.parseFloat(t[i + 2]);
            Paint p = t[0].equals("F") ? fill : cut;
            switch (t[1]) {
                case "r":
                    r.set(cx + a[0] * s, cy + a[1] * s, cx + a[2] * s, cy + a[3] * s);
                    c.drawRoundRect(r, a[4] * s, a[4] * s, p);
                    break;
                case "o":
                    if (t[0].equals("S")) {
                        line.setStrokeWidth(Math.max(1f, a[3] * s));
                        c.drawCircle(cx + a[0] * s, cy + a[1] * s, a[2] * s, line);
                    } else {
                        c.drawCircle(cx + a[0] * s, cy + a[1] * s, a[2] * s, p);
                    }
                    break;
                case "p":
                    path.reset();
                    for (int i = 0; i + 1 < a.length; i += 2) {
                        if (i == 0) path.moveTo(cx + a[i] * s, cy + a[i + 1] * s);
                        else path.lineTo(cx + a[i] * s, cy + a[i + 1] * s);
                    }
                    path.close();
                    c.drawPath(path, p);
                    break;
                case "l":
                    line.setStrokeWidth(Math.max(1f, a[4] * s));
                    c.drawLine(cx + a[0] * s, cy + a[1] * s, cx + a[2] * s, cy + a[3] * s, line);
                    break;
                case "x":
                    cross(c, cx + a[0] * s, cy + a[1] * s, a[2] * s);
                    break;
            }
        }
        return true;
    }

    private static String family(String id) {
        switch (id) {
            case "gb":
            case "gbc":
                return "handheld_v";
            case "gba":
            case "gg":
            case "psp":
            case "vita":
            case "switch":
                return "handheld_h";
            case "nds":
            case "3ds":
                return "dual";
            case "psx":
            case "ps2":
            case "ps3":
            case "x360":
            case "saturn":
            case "dc":
                return "disc";
            default:
                return "pad";
        }
    }

    /** Manette générique : corps arrondi, croix, deux boutons. */
    private void pad(Canvas c, float cx, float cy, float s) {
        r.set(cx - s * 0.48f, cy - s * 0.22f, cx + s * 0.48f, cy + s * 0.22f);
        c.drawRoundRect(r, s * 0.2f, s * 0.2f, fill);
        cross(c, cx - s * 0.24f, cy, s * 0.2f);
        c.drawCircle(cx + s * 0.2f, cy + s * 0.05f, s * 0.055f, cut);
        c.drawCircle(cx + s * 0.32f, cy - s * 0.05f, s * 0.055f, cut);
    }

    /** Portable vertical générique : écran en haut, croix et boutons en bas. */
    private void handheldVertical(Canvas c, float cx, float cy, float s) {
        r.set(cx - s * 0.3f, cy - s * 0.48f, cx + s * 0.3f, cy + s * 0.48f);
        c.drawRoundRect(r, s * 0.06f, s * 0.06f, fill);
        r.set(cx - s * 0.22f, cy - s * 0.4f, cx + s * 0.22f, cy - s * 0.04f);
        c.drawRect(r, cut);
        cross(c, cx - s * 0.12f, cy + s * 0.2f, s * 0.13f);
        c.drawCircle(cx + s * 0.13f, cy + s * 0.2f, s * 0.045f, cut);
    }

    /** Portable horizontal générique : écran au centre, commandes de chaque côté. */
    private void handheldHorizontal(Canvas c, float cx, float cy, float s) {
        r.set(cx - s * 0.5f, cy - s * 0.24f, cx + s * 0.5f, cy + s * 0.24f);
        c.drawRoundRect(r, s * 0.12f, s * 0.12f, fill);
        r.set(cx - s * 0.24f, cy - s * 0.16f, cx + s * 0.24f, cy + s * 0.16f);
        c.drawRect(r, cut);
        cross(c, cx - s * 0.37f, cy, s * 0.11f);
        c.drawCircle(cx + s * 0.37f, cy, s * 0.045f, cut);
    }

    /** Double écran générique : deux panneaux reliés par une charnière. */
    private void dualScreen(Canvas c, float cx, float cy, float s) {
        r.set(cx - s * 0.36f, cy - s * 0.48f, cx + s * 0.36f, cy - s * 0.03f);
        c.drawRoundRect(r, s * 0.05f, s * 0.05f, fill);
        r.set(cx - s * 0.36f, cy + s * 0.03f, cx + s * 0.36f, cy + s * 0.48f);
        c.drawRoundRect(r, s * 0.05f, s * 0.05f, fill);
        r.set(cx - s * 0.26f, cy - s * 0.4f, cx + s * 0.26f, cy - s * 0.1f);
        c.drawRect(r, cut);
        r.set(cx - s * 0.2f, cy + s * 0.1f, cx + s * 0.2f, cy + s * 0.4f);
        c.drawRect(r, cut);
    }

    /** Disque générique. */
    private void disc(Canvas c, float cx, float cy, float s) {
        c.drawCircle(cx, cy, s * 0.46f, fill);
        c.drawCircle(cx, cy, s * 0.12f, cut);
        Paint ring = new Paint(cut);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(s * 0.02f);
        c.drawCircle(cx, cy, s * 0.3f, ring);
    }

    private void cross(Canvas c, float x, float y, float s) {
        float t = s * 0.34f;
        c.drawRect(x - s / 2, y - t / 2, x + s / 2, y + t / 2, cut);
        c.drawRect(x - t / 2, y - s / 2, x + t / 2, y + s / 2, cut);
    }
}
