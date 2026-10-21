package com.hideo.foldplay;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Décors facettés, dans la ligne du logo (Crane) : chaque forme est pliée en deux le long de son
 * axe, la face gauche dans l'ombre, la face droite dans la lumière, avec les cinq nuances de
 * l'accent et un fin pli à la couleur du pli du thème. Tout reste très transparent : le texte passe
 * avant le décor. k règle l'intensité (1 pour le fond de l'app, moins sur la façade d'une manette).
 * - Arctique : glaciers en dents de scie en bas, aurore en facettes en haut, cristaux hexagonaux ;
 * - Sakura : mont plié en deux faces, branche anguleuse, fleurs de cerisier en papier, pétales ;
 * - Écarlate : flammes en losanges pliés qui montent du bas, escarbilles, chevrons d'alerte pliés (l'éventail reste en variante 1).
 * Tirages à graine fixe : le décor ne bouge pas d'un affichage à l'autre.
 */
final class Facets {
    private final Canvas c;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int[] sh;
    private final int fold;
    private final float k;
    private long seed;

    private Facets(Canvas c, int accent, int fold, float k, long seed) {
        this.c = c;
        this.sh = Crane.shades(accent);
        this.fold = fold;
        this.k = k;
        this.seed = seed;
        p.setStyle(Paint.Style.FILL);
    }

    /** Petit générateur maison (même suite partout, sans dépendre de java.util.Random). */
    private float rnd() {
        seed = (seed * 6364136223846793005L + 1442695040888963407L);
        return ((seed >>> 40) & 0xFFFFFF) / (float) 0x1000000;
    }

    private int a(int color, int alpha) {
        int v = Math.max(0, Math.min(255, Math.round(alpha * k)));
        return (color & 0x00FFFFFF) | (v << 24);
    }

    private void tri(float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        path.reset();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        path.lineTo(x3, y3);
        path.close();
        p.setColor(color);
        c.drawPath(path, p);
    }

    /**
     * Losange plié : de la base (bx, by) à la pointe, longueur len, demi-largeur wid (au tiers),
     * angle en radians. Face gauche = ombre, face droite = lumière, pli au milieu.
     */
    private void kite(float bx, float by, float len, float wid, double ang, int dark, int light, int alpha, boolean crease) {
        float ux = (float) Math.cos(ang), uy = (float) Math.sin(ang);
        float tx = bx + ux * len, ty = by + uy * len;
        float mx = bx + ux * len * 0.38f, my = by + uy * len * 0.38f;
        float lx = mx + uy * wid, ly = my - ux * wid;
        float rx = mx - uy * wid, ry = my + ux * wid;
        tri(bx, by, tx, ty, lx, ly, a(sh[dark], alpha));
        tri(bx, by, tx, ty, rx, ry, a(sh[light], alpha));
        if (crease) line(bx, by, tx, ty, Math.max(1f, len / 60f), a(fold, alpha + 0x10));
    }

    private void line(float x1, float y1, float x2, float y2, float wpx, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(wpx);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        c.drawLine(x1, y1, x2, y2, p);
        p.setStyle(Paint.Style.FILL);
    }

    // ------------------------------------------------------------ Arctique

    static void arctique(Canvas c, float w, float h, int accent, int fold, float k, float dp) {
        new Facets(c, accent, fold, k, 21).ice(w, h, dp);
    }

    /** Arctique : 0 glaciers et aurore (écran du haut), 1 banquise et flocons (manette). */
    static void arctique(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 21);
        if (variant == 1) f.floes(w, h);
        else f.ice(w, h, 1f);
    }

    /**
     * Banquise vue d'en haut : plaques de glace irrégulières pliées en deux tons, chenaux d'eau
     * sombre entre elles, reflets d'aurore en biais et quelques cristaux posés dessus.
     */
    private void floes(float w, float h) {
        float u = Math.max(w, h);
        int cols = Math.max(4, Math.round(w / (u * 0.11f))), rows = Math.max(3, Math.round(h / (u * 0.11f)));
        float cw = w / cols, ch = h / rows;
        float[][] px = new float[rows + 1][cols + 1], py = new float[rows + 1][cols + 1];
        for (int j = 0; j <= rows; j++) {
            for (int i = 0; i <= cols; i++) {
                px[j][i] = cw * i + (i == 0 || i == cols ? 0 : (rnd() - 0.5f) * cw * 0.5f);
                py[j][i] = ch * j + (j == 0 || j == rows ? 0 : (rnd() - 0.5f) * ch * 0.5f);
            }
        }
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                // Plaque rétrécie vers son centre : le chenal d'eau reste entre deux plaques.
                float cx = (px[j][i] + px[j][i + 1] + px[j + 1][i] + px[j + 1][i + 1]) / 4;
                float cy = (py[j][i] + py[j][i + 1] + py[j + 1][i] + py[j + 1][i + 1]) / 4;
                float g = 0.86f - 0.1f * rnd();
                float ax = cx + (px[j][i] - cx) * g, ay = cy + (py[j][i] - cy) * g;
                float bx = cx + (px[j][i + 1] - cx) * g, by = cy + (py[j][i + 1] - cy) * g;
                float dx = cx + (px[j + 1][i + 1] - cx) * g, dy = cy + (py[j + 1][i + 1] - cy) * g;
                float ex = cx + (px[j + 1][i] - cx) * g, ey = cy + (py[j + 1][i] - cy) * g;
                int al = 0x0A + Math.round(rnd() * 0x0E);
                boolean flip = rnd() < 0.5f;
                if (flip) {
                    tri(ax, ay, bx, by, dx, dy, a(sh[3], al));
                    tri(ax, ay, dx, dy, ex, ey, a(sh[1], al));
                    line(ax, ay, dx, dy, Math.max(1f, u / 1200f), a(fold, al / 2));
                } else {
                    tri(ax, ay, bx, by, ex, ey, a(sh[2], al));
                    tri(bx, by, dx, dy, ex, ey, a(sh[0], al));
                    line(bx, by, ex, ey, Math.max(1f, u / 1200f), a(fold, al / 2));
                }
            }
        }
        // Reflets d'aurore : trois longues bandes fines en biais, comme sur l'écran du haut.
        for (int i = 0; i < 3; i++) {
            float y0 = h * (0.15f + 0.3f * i) + (rnd() - 0.5f) * h * 0.1f;
            tri(0, y0, w, y0 - h * 0.25f, w, y0 - h * 0.25f + u * 0.006f, a(sh[4], 0x10));
            tri(0, y0, w, y0 - h * 0.25f + u * 0.006f, 0, y0 + u * 0.006f, a(sh[3], 0x10));
        }
        for (int i = 0; i < 4; i++) {
            crystal(w * (0.05f + 0.9f * rnd()), h * (0.1f + 0.8f * rnd()), u * (0.012f + 0.016f * rnd()), 0x20);
        }
    }

    private void ice(float w, float h, float dp) {
        float u = Math.max(w, h);
        // Aurore : un ruban de triangles qui ondule en haut, plus dense au centre.
        int n = 22;
        float[] top = new float[n + 1], bot = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            float mid = h * 0.13f + h * 0.05f * (float) Math.sin(t * 5.2 + 0.6) + h * 0.02f * (float) Math.sin(t * 13);
            float thick = h * (0.05f + 0.06f * (float) Math.sin(Math.PI * t)) * (0.7f + 0.6f * rnd());
            top[i] = mid - thick * 0.5f;
            bot[i] = mid + thick * 0.5f;
        }
        for (int i = 0; i < n; i++) {
            float x0 = w * i / n, x1 = w * (i + 1) / n;
            float fade = (float) Math.sin(Math.PI * (i + 0.5f) / n);
            int al = Math.round(0x26 * fade);
            tri(x0, top[i], x1, top[i + 1], x0, bot[i], a(sh[(i & 1) == 0 ? 3 : 2], al));
            tri(x1, top[i + 1], x1, bot[i + 1], x0, bot[i], a(sh[(i & 1) == 0 ? 1 : 4], al));
        }
        // Cristaux : hexagones en six facettes, l'ombre à gauche du pli vertical.
        for (int i = 0; i < 6; i++) {
            float cx = w * (0.08f + 0.84f * rnd()), cy = h * (0.30f + 0.38f * rnd());
            float r = u * (0.018f + 0.03f * rnd());
            crystal(cx, cy, r, 0x22 + Math.round(rnd() * 0x18));
        }
        // Glaciers : deux chaînes de pics, chaque pic plié en deux faces, neige au sommet.
        ridge(w, h, h * 0.66f, h * 0.80f, 0x16, 5, true);
        ridge(w, h, h * 0.74f, h * 0.90f, 0x2E, 5, false);
    }

    private void crystal(float cx, float cy, float r, int alpha) {
        int[] tone = {3, 4, 2, 1, 0, 1};
        for (int s = 0; s < 6; s++) {
            double t0 = Math.PI / 3 * s - Math.PI / 2, t1 = t0 + Math.PI / 3;
            tri(cx, cy, cx + (float) Math.cos(t0) * r, cy + (float) Math.sin(t0) * r,
                    cx + (float) Math.cos(t1) * r, cy + (float) Math.sin(t1) * r, a(sh[tone[s]], alpha));
        }
        line(cx, cy - r, cx, cy + r, Math.max(1f, r / 14f), a(fold, alpha + 0x14));
    }

    private void ridge(float w, float h, float peakMin, float peakMax, int alpha, int peaks, boolean far) {
        float step = w / peaks;
        float vx = -step * 0.3f, vy = h;
        for (int i = 0; i <= peaks; i++) {
            float px = vx + step * (0.35f + 0.35f * rnd());
            float py = peakMin + (peakMax - peakMin) * rnd();
            float nx = vx + step * (0.9f + 0.3f * rnd());
            float ny = h - (h - peakMax) * 0.4f * rnd();
            // Face à l'ombre, face à la lumière, partagées par l'arête qui descend du sommet.
            float fx = px + (nx - px) * 0.15f;
            tri(vx, vy, px, py, fx, h, a(sh[far ? 0 : 1], alpha));
            tri(px, py, nx, ny, fx, h, a(sh[far ? 1 : 2], alpha));
            tri(vx, vy, fx, h, nx, ny, a(sh[0], alpha));
            // Neige : petite facette claire sous le sommet.
            float sy = py + (h - py) * 0.16f;
            tri(px, py, px + (fx - px) * 0.16f - (px - vx) * 0.12f, sy, px + (nx - px) * 0.18f, sy + (h - py) * 0.04f,
                    a(0xFFFFFFFF, alpha + 0x08));
            if (!far) line(px, py, fx, h, Math.max(1f, w / 900f), a(fold, alpha / 2));
            vx = nx;
            vy = ny;
        }
        // Le bas des glaciers, plein, pour que la chaîne repose sur le bord.
        p.setColor(a(sh[0], alpha / 2));
        c.drawRect(0, h - 1, w, h, p);
    }

    // ------------------------------------------------------------ Sakura

    static void sakura(Canvas c, float w, float h, int accent, int fold, float k, float dp) {
        new Facets(c, accent, fold, k, 8).blossom(w, h, dp);
    }

    /** Sakura : 0 mont et branche en fleurs (écran du haut), 1 vagues et pétales sur l'eau (manette). */
    static void sakura(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 8);
        if (variant == 1) f.waves(w, h);
        else f.blossom(w, h, 1f);
    }

    /**
     * Vagues en écailles (seigaiha) pliées : rangées de demi-cercles en éventails de facettes, qui
     * se chevauchent ; des pétales et deux fleurs tombés du cerisier flottent dessus.
     */
    private void waves(float w, float h) {
        float m = Math.min(w, h);
        float r = Math.max(m * 0.2f, Math.max(w, h) * 0.085f);
        int row = 0;
        for (float y = r * 0.5f; y < h + r; y += r * 0.5f, row++) {
            float off = (row & 1) == 0 ? 0 : r;
            for (float x = -r + off; x < w + r; x += 2 * r) {
                // Trois anneaux par écaille, chacun en huit facettes alternées.
                for (int ring = 0; ring < 3; ring++) {
                    float ro = r * (1f - ring * 0.3f), ri = ro - r * 0.2f;
                    for (int s = 0; s < 8; s++) {
                        double t0 = Math.PI + Math.PI * s / 8, t1 = Math.PI + Math.PI * (s + 1) / 8;
                        float x0 = x + (float) Math.cos(t0) * ro, y0 = y + (float) Math.sin(t0) * ro;
                        float x1 = x + (float) Math.cos(t1) * ro, y1 = y + (float) Math.sin(t1) * ro;
                        float x2 = x + (float) Math.cos(t1) * ri, y2 = y + (float) Math.sin(t1) * ri;
                        float x3 = x + (float) Math.cos(t0) * ri, y3 = y + (float) Math.sin(t0) * ri;
                        int al = 0x10 + ring * 4;
                        tri(x0, y0, x1, y1, x2, y2, a(sh[(s & 1) == 0 ? 1 : 3], al));
                        tri(x0, y0, x2, y2, x3, y3, a(sh[(s & 1) == 0 ? 0 : 2], al));
                    }
                }
            }
        }
        // Pétales et fleurs portés par l'eau.
        for (int i = 0; i < 14; i++) {
            float x = w * rnd(), y = h * rnd();
            float s = m * (0.02f + 0.02f * rnd());
            kite(x, y, s * 2f, s * 0.7f, rnd() * 6.28, 2, 4, 0x22 + Math.round(rnd() * 0x14), false);
        }
        flower(w * 0.86f, h * 0.24f, m * 0.1f, 0.4f, 0x24);
        flower(w * 0.18f, h * 0.78f, m * 0.07f, 1.1f, 0x20);
    }

    private void blossom(float w, float h, float dp) {
        float u = Math.max(w, h), m = Math.min(w, h);
        // Mont plié en deux faces, en bas à droite, avec sa neige.
        float mx = w * 0.72f, my = h * 0.56f, base = h * 1.02f, half = Math.max(w * 0.36f, (base - my) * 1.15f);
        // Chaque face est elle-même pliée une fois : quatre facettes, de l'ombre à la lumière.
        float lq = mx - half * 0.42f, rq = mx + half * 0.48f;
        tri(mx - half, base, mx, my, lq, base, a(sh[0], 0x24));
        tri(lq, base, mx, my, mx, base, a(sh[0], 0x34));
        tri(mx, my, rq, base, mx, base, a(sh[1], 0x30));
        tri(mx, my, mx + half, base, rq, base, a(sh[1], 0x24));
        float cap = (base - my) * 0.22f, capW = half * 0.22f;
        tri(mx - capW, my + cap, mx, my, mx, my + cap * 0.8f, a(0xFFFFFFFF, 0x1E));
        tri(mx, my, mx + capW, my + cap, mx, my + cap * 0.8f, a(0xFFFFFFFF, 0x30));
        line(mx, my, mx, base, Math.max(1f, u / 800f), a(fold, 0x22));
        // Branche anguleuse depuis le coin haut gauche : segments en deux facettes.
        float[] bx = {-0.04f, 0.14f, 0.26f, 0.40f, 0.55f};
        float[] by = {0.04f, 0.10f, 0.22f, 0.26f, 0.36f};
        float thick = m * 0.018f;
        float[][] nodes = new float[bx.length][];
        for (int i = 0; i < bx.length; i++) nodes[i] = new float[] {w * bx[i], h * by[i] + (rnd() - 0.5f) * m * 0.03f};
        for (int i = 0; i + 1 < nodes.length; i++) {
            float t = thick * (1f - i * 0.18f);
            float[] s = nodes[i], e = nodes[i + 1];
            tri(s[0], s[1] - t, e[0], e[1] - t * 0.8f, e[0], e[1], a(sh[1], 0x3A));
            tri(s[0], s[1] - t, e[0], e[1], s[0], s[1] + t, a(sh[0], 0x3A));
        }
        // Rameaux secondaires.
        float[][] twigs = {{1, 0.06f, -0.08f}, {2, 0.05f, 0.10f}, {3, 0.07f, -0.06f}};
        for (float[] tw : twigs) {
            float[] s = nodes[(int) tw[0]];
            float ex = s[0] + w * tw[1], ey = s[1] + h * tw[2];
            tri(s[0], s[1] - thick * 0.4f, ex, ey, s[0], s[1] + thick * 0.4f, a(sh[0], 0x34));
        }
        // Fleurs en papier : cinq pétales pliés, cœur à la couleur du pli.
        for (int i = 0; i < 9; i++) {
            float[] s = nodes[1 + (i % (nodes.length - 1))];
            float fx = s[0] + (rnd() - 0.3f) * m * 0.16f, fy = s[1] + (rnd() - 0.4f) * m * 0.12f;
            flower(fx, fy, m * (0.028f + 0.03f * rnd()), rnd() * 6.28f, 0x34 + Math.round(rnd() * 0x18));
        }
        // Une grande fleur en bas à gauche, en partie hors champ.
        flower(w * 0.06f, h * 0.9f, m * 0.12f, 0.3f, 0x1C);
        // Pétales qui tombent en biais.
        for (int i = 0; i < 18; i++) {
            float x = w * rnd(), y = h * (0.25f + 0.7f * rnd());
            float s = m * (0.012f + 0.014f * rnd());
            kite(x, y, s * 2f, s * 0.7f, 0.6 + rnd() * 1.2, 2, 4, 0x1C + Math.round(rnd() * 0x14), false);
        }
    }

    private void flower(float cx, float cy, float r, double rot, int alpha) {
        for (int i = 0; i < 5; i++) {
            double t = rot + Math.PI * 2 * i / 5;
            kite(cx, cy, r, r * 0.42f, t, 2, 3, alpha, false);
        }
        // Cœur : petit pentagone au ton du pli.
        path.reset();
        for (int i = 0; i < 5; i++) {
            double t = rot + Math.PI * 2 * (i + 0.5) / 5;
            float x = cx + (float) Math.cos(t) * r * 0.2f, y = cy + (float) Math.sin(t) * r * 0.2f;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        p.setColor(a(fold, alpha + 0x30));
        c.drawPath(path, p);
    }

    // ------------------------------------------------------------ Écarlate

    /** Fond retenu par BOSS (19/10) : les flammes. */
    static void ecarlate(Canvas c, float w, float h, int accent, int fold, float k, float dp) {
        ecarlate(c, w, h, accent, fold, k, 0);
    }

    /** Écarlate : 0 flammes pliées, 1 éventail plié et soleil levant en facettes. */
    static void ecarlate(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 13);
        if (variant == 1) f.fan(w, h);
        else f.fire(w, h);
    }

    private void fan(float w, float h) {
        float u = Math.max(w, h), m = Math.min(w, h);
        // Soleil levant : disque à seize facettes en haut à droite, ombre à gauche du pli.
        float sx = w * 0.78f, sy = h * 0.24f, sr = m * 0.2f;
        int n = 16;
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI * 2 * i / n, t1 = t0 + Math.PI * 2 / n;
            boolean left = Math.cos((t0 + t1) / 2) < 0;
            tri(sx, sy, sx + (float) Math.cos(t0) * sr, sy + (float) Math.sin(t0) * sr,
                    sx + (float) Math.cos(t1) * sr, sy + (float) Math.sin(t1) * sr, a(sh[left ? 1 : 2], (i & 1) == 0 ? 0x22 : 0x2C));
        }
        line(sx, sy - sr, sx, sy + sr, Math.max(1f, u / 800f), a(fold, 0x30));
        // Éventail plié posé en bas à gauche : plis alternés vallée / montagne.
        float fx = w * 0.12f, fy = h * 1.02f, R = Math.min(w * 0.5f, h * 0.62f), r0 = R * 0.18f;
        int pleats = 16;
        double a0 = -Math.PI * 0.92, a1 = -Math.PI * 0.18;
        for (int i = 0; i < pleats; i++) {
            double t0 = a0 + (a1 - a0) * i / pleats, t1 = a0 + (a1 - a0) * (i + 1) / pleats;
            float ox0 = fx + (float) Math.cos(t0) * R, oy0 = fy + (float) Math.sin(t0) * R;
            float ox1 = fx + (float) Math.cos(t1) * R, oy1 = fy + (float) Math.sin(t1) * R;
            float ix0 = fx + (float) Math.cos(t0) * r0, iy0 = fy + (float) Math.sin(t0) * r0;
            float ix1 = fx + (float) Math.cos(t1) * r0, iy1 = fy + (float) Math.sin(t1) * r0;
            int tone = (i & 1) == 0 ? 1 : 3;
            tri(ix0, iy0, ox0, oy0, ox1, oy1, a(sh[tone], 0x1E));
            tri(ix0, iy0, ox1, oy1, ix1, iy1, a(sh[tone - 1], 0x1E));
            // Baguettes : un trait au ton du pli tous les deux plis.
            if ((i & 1) == 0) line(fx, fy, ox0, oy0, Math.max(1f, u / 900f), a(fold, 0x1C));
        }
        // Liseré du bord en petits losanges.
        for (int i = 0; i <= pleats; i++) {
            double t = a0 + (a1 - a0) * i / pleats;
            float x = fx + (float) Math.cos(t) * R, y = fy + (float) Math.sin(t) * R;
            kite(x, y, m * 0.022f, m * 0.008f, t, 2, 4, 0x34, false);
        }
        // Quelques escarbilles.
        for (int i = 0; i < 12; i++) {
            float s = m * (0.008f + 0.01f * rnd());
            kite(w * rnd(), h * (0.3f + 0.6f * rnd()), s * 2.4f, s * 0.6f, -Math.PI / 2 + (rnd() - 0.5f) * 0.8, 3, 4, 0x20, false);
        }
    }

    private void fire(float w, float h) {
        float m = Math.min(w, h);
        // Chevrons d'alerte pliés dans le coin haut gauche : chaque branche en deux facettes.
        float cw = m * 0.06f, step = m * 0.1f;
        for (int i = 0; i < 4; i++) {
            float x = -m * 0.05f + i * step, y = m * 0.06f, d = m * 0.14f;
            int al = 0x1C - i * 4;
            tri(x, y, x + d, y + d, x + cw, y, a(sh[1], al));
            tri(x + cw, y, x + d, y + d, x + d + cw, y + d, a(sh[2], al));
            tri(x + d, y + d, x, y + d * 2, x + d + cw, y + d, a(sh[0], al));
            tri(x + d + cw, y + d, x, y + d * 2, x + cw, y + d * 2, a(sh[1], al));
        }
        // Deux rangs de flammes : losanges pliés pointés vers le haut, plus hauts au centre.
        for (int row = 0; row < 2; row++) {
            int n = row == 0 ? 9 : 13;
            for (int i = 0; i < n; i++) {
                float t = (i + 0.5f) / n;
                float x = w * t + (rnd() - 0.5f) * w / n * 0.6f;
                float peak = (float) Math.sin(Math.PI * t);
                float len = h * (row == 0 ? 0.30f + 0.22f * peak : 0.16f + 0.14f * peak) * (0.75f + 0.5f * rnd());
                float wid = len * (0.16f + 0.08f * rnd());
                double ang = -Math.PI / 2 + (rnd() - 0.5f) * 0.35;
                kite(x, h + len * 0.05f, len, wid, ang, row == 0 ? 0 : 1, row == 0 ? 1 : 3,
                        row == 0 ? 0x22 : 0x30, row == 1 && (i % 3 == 1));
            }
        }
        // Escarbilles : petits losanges qui montent, plus nombreux près des flammes.
        for (int i = 0; i < 26; i++) {
            float y = h * (0.25f + 0.6f * (float) Math.sqrt(rnd()));
            float s = m * (0.008f + 0.012f * rnd());
            kite(w * rnd(), y, s * 2.4f, s * 0.6f, -Math.PI / 2 + (rnd() - 0.5f) * 0.8, 3, 4,
                    0x22 + Math.round(rnd() * 0x22), false);
        }
    }

    // ------------------------------------------------------------ Ambre

    /** Variante Ambre en service : soleil Art déco, choisi par BOSS le 19/10. */
    static final int AMBRE = 1;

    /** Fond d'écran d'Ambre : la ville Art déco la nuit (le soleil, variante 1, reste celui de la manette). */
    static final int AMBRE_FOND = 3;

    /** Ambre : 0 trace d'oscilloscope pliée, 1 soleil Art déco en gradins, 2 cascade de curseurs, 3 ville Art déco. */
    static void ambre(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 47);
        if (variant == 1) f.deco(w, h);
        else if (variant == 2) f.cursors(w, h);
        else if (variant == 3) f.ville(w, h);
        else f.scope(w, h);
    }

    private void box(float x0, float y0, float x1, float y1, int color) {
        p.setColor(color);
        c.drawRect(x0, y0, x1, y1, p);
    }

    /**
     * Gratte-ciel à gradins : chaque étage est plié en deux (face gauche dans l'ombre, face droite
     * un peu plus claire), liseré doré sur l'arête droite et sur le toit, fenêtres allumées au hasard.
     * lum règle l'éclat (faible au centre, là où passent les listes).
     */
    private void tour(float cx, float base, float bw, float th, int tiers, boolean spire, float lum, float q) {
        float y = base;
        float hw = bw / 2;
        float lw = Math.max(1f, q / 22f);
        for (int i = 0; i < tiers; i++) {
            float eh = th * (i == 0 ? 0.42f : 0.58f / Math.max(1, tiers - 1)) ;
            float x0 = cx - hw, x1 = cx + hw, top = y - eh;
            box(x0, top, cx, y + 1, a(0xFF0A0705, 0xF2));
            box(cx, top, x1, y + 1, a(0xFF14100A, 0xF2));
            // Pilastres : fines lignes dorées verticales, à la place des pavés de fenêtres.
            int cols = Math.max(2, Math.round(hw * 2 / (q * 0.42f)));
            float cw = (x1 - x0) / cols;
            for (int k2 = 1; k2 < cols; k2++) {
                line(x0 + k2 * cw, top + eh * 0.06f, x0 + k2 * cw, y - eh * 0.04f, lw, a(fold, Math.round(0x16 * lum)));
            }
            // Fenêtres : petites fentes ambrées, rares et dispersées.
            float wh = Math.min(q * 0.28f, eh * 0.09f);
            for (float wy = top + eh * 0.14f; wy < y - eh * 0.1f; wy += wh * 2.6f) {
                for (int k2 = 0; k2 < cols; k2++) {
                    if (rnd() > 0.34f * lum + 0.05f) continue;
                    float wx = x0 + k2 * cw + cw * 0.3f;
                    box(wx, wy, wx + cw * 0.4f, wy + wh, a(sh[rnd() < 0.3f ? 4 : 3], Math.round((0x30 + 0x50 * rnd()) * lum)));
                }
            }
            // Liserés de lumière : arête droite et toit.
            line(x1, top, x1, y, lw * 1.4f, a(sh[3], Math.round(0x68 * lum)));
            line(x0, top, x1, top, lw, a(fold, Math.round(0x5A * lum)));
            y = top;
            hw *= 0.74f;
        }
        if (spire) {
            // Flèche : triangle plié en deux, trait d'or au centre et deux barrettes.
            float sh2 = th * 0.34f;
            tri(cx - hw * 0.8f, y, cx, y - sh2, cx, y, a(0xFF0A0705, 0xF2));
            tri(cx, y, cx, y - sh2, cx + hw * 0.8f, y, a(0xFF14100A, 0xF2));
            line(cx, y, cx, y - sh2, lw, a(fold, Math.round(0x78 * lum)));
            line(cx + hw * 0.8f, y, cx, y - sh2, lw, a(sh[3], Math.round(0x70 * lum)));
            line(cx - hw * 0.45f, y - sh2 * 0.3f, cx + hw * 0.45f, y - sh2 * 0.3f, lw, a(fold, Math.round(0x50 * lum)));
            line(cx - hw * 0.25f, y - sh2 * 0.55f, cx + hw * 0.25f, y - sh2 * 0.55f, lw, a(fold, Math.round(0x50 * lum)));
        }
    }

    /**
     * Ville Art déco la nuit (fond d'écran) : éventail de rayons très discret dans le coin haut droit,
     * frise de chevrons en haut, rayons montant de la tour maîtresse, deux plans de gratte-ciel à gradins
     * (reflet doré sur les arêtes). Les tours hautes sont aux bords ; le centre et la gauche, où se posent
     * les listes, restent bas et sombres. Le ciel (dégradé, lueur d'horizon) est posé par ThemeBackdrop.
     */
    private void ville(float w, float h) {
        float u = Math.max(w, h);
        float H0 = Math.min(h, w * 0.9f);
        float q = Math.min(w, h * 1.2f) * 0.06f;
        float ground = h * 0.9f;
        float lw = Math.max(1f, u / 1100f);
        // Éventail Art déco dans le coin haut droit : rayons fins en triangles pliés, arcs concentriques.
        float fx = w, fy = 0, fr = Math.min(w * 0.75f, h * 0.7f);
        int rays = 14;
        for (int i = 0; i < rays; i++) {
            double t0 = Math.PI / 2 + Math.PI / 2 * (i + 0.12) / rays, t1 = Math.PI / 2 + Math.PI / 2 * (i + 0.88) / rays;
            double tm = (t0 + t1) / 2;
            float r = fr * ((i & 1) == 0 ? 1f : 0.82f);
            float ex = fx + (float) Math.cos(tm) * r, ey = fy + (float) Math.sin(tm) * r;
            tri(fx, fy, ex, ey, fx + (float) Math.cos(t0) * r * 0.9f, fy + (float) Math.sin(t0) * r * 0.9f, a(sh[1], 0x12));
            tri(fx, fy, ex, ey, fx + (float) Math.cos(t1) * r * 0.9f, fy + (float) Math.sin(t1) * r * 0.9f, a(sh[3], 0x0E));
        }
        p.setStyle(Paint.Style.STROKE);
        android.graphics.RectF ring = new android.graphics.RectF();
        for (int i = 1; i <= 4; i++) {
            float rr = fr * (0.2f + 0.2f * i);
            p.setStrokeWidth(lw);
            p.setColor(a(fold, 0x2A - i * 5));
            ring.set(fx - rr, fy - rr, fx + rr, fy + rr);
            c.drawArc(ring, 90, 90, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        // Frise de chevrons pliés le long du bord haut, qui s'éteint vers la gauche.
        float cw = q * 0.55f;
        for (float x = w * 0.45f; x < w + cw; x += cw * 2) {
            float fade = Math.min(1f, (x - w * 0.45f) / (w * 0.4f));
            tri(x, q * 0.5f, x + cw, q * 0.5f + cw, x + cw, q * 0.5f, a(sh[1], Math.round(0x24 * fade)));
            tri(x + cw, q * 0.5f, x + cw, q * 0.5f + cw, x + cw * 2, q * 0.5f, a(sh[3], Math.round(0x24 * fade)));
        }
        line(w * 0.45f, q * 0.5f, w, q * 0.5f, lw, a(fold, 0x20));
        line(w * 0.45f, q * 0.5f + cw * 1.3f, w, q * 0.5f + cw * 1.3f, lw, a(fold, 0x14));
        // Rayons montant de la tour maîtresse (très discrets).
        float hx = w * 0.845f;
        for (int i = -4; i <= 4; i++) {
            double ang = -Math.PI / 2 + i * 0.2;
            line(hx, ground - H0 * 0.7f, hx + (float) Math.cos(ang) * u, ground - H0 * 0.7f + (float) Math.sin(ang) * u,
                    lw, a(fold, 0x0C + (i % 2 == 0 ? 6 : 0)));
        }
        // Second plan : tours lointaines, pâles dans la brume de l'horizon.
        long keep = seed;
        float bx = -q;
        while (bx < w + q) {
            float bw = q * (1.2f + 1.6f * rnd());
            float x = bx + bw / 2, fx2 = x / w;
            float edge = Math.min(1f, Math.abs(fx2 - 0.4f) * 2.2f);
            float bh = H0 * (0.08f + 0.2f * rnd()) * (0.5f + 0.7f * edge);
            box(bx, ground - bh, bx + bw, ground + 1, a(0xFF1B130A, 0xB0));
            box(bx + bw * 0.5f, ground - bh, bx + bw, ground + 1, a(0xFF241A0E, 0x90));
            line(bx + bw, ground - bh, bx + bw, ground, lw, a(fold, 0x28));
            if (rnd() < 0.4f) box(bx + bw * 0.3f, ground - bh - q * 0.5f, bx + bw * 0.7f, ground - bh, a(0xFF1B130A, 0xB0));
            bx += bw * (0.55f + 0.5f * rnd());
        }
        seed = keep;
        // Premier plan : x (part de la largeur), largeur (en q), hauteur (part de H0), étages, flèche, éclat.
        float[][] t = {
            {0.035f, 2.4f, 0.34f, 3, 1, 0.9f}, {0.115f, 1.9f, 0.20f, 2, 0, 0.7f},
            {0.20f, 2.4f, 0.09f, 2, 0, 0.35f}, {0.29f, 2.0f, 0.13f, 2, 0, 0.35f}, {0.385f, 2.7f, 0.08f, 1, 0, 0.3f},
            {0.48f, 2.2f, 0.12f, 2, 0, 0.35f}, {0.57f, 2.0f, 0.09f, 1, 0, 0.4f},
            {0.665f, 2.4f, 0.27f, 3, 0, 0.8f}, {0.75f, 2.8f, 0.42f, 4, 1, 0.9f},
            {0.845f, 3.6f, 0.66f, 5, 1, 1f}, {0.935f, 2.6f, 0.46f, 4, 1, 0.9f}, {0.995f, 2.3f, 0.30f, 3, 0, 0.8f},
        };
        for (int i = 0; i < t.length; i++) {
            tour(w * t[i][0], ground, q * t[i][1], H0 * t[i][2], Math.round(t[i][3]), t[i][4] > 0, t[i][5], q);
        }
        // Sol : quai sombre et filet d'or sur la ligne d'horizon, reflets en traits.
        box(0, ground, w, h, a(0xFF080604, 0xF6));
        line(0, ground, w, ground, lw * 1.5f, a(fold, 0x50));
        line(0, ground + q * 0.35f, w, ground + q * 0.35f, lw, a(fold, 0x18));
        for (int i = 0; i < 9; i++) {
            float x = w * (0.5f + 0.5f * rnd());
            line(x, ground + q * 0.6f, x, ground + q * (0.9f + 1.2f * rnd()), lw, a(sh[3], 0x22));
        }
    }

    /** Petit carré plié en deux triangles (diagonale = pli). */
    private void square(float x, float y, float s, int dark, int light, int alpha) {
        tri(x, y, x + s, y, x, y + s, a(sh[light], alpha));
        tri(x + s, y, x + s, y + s, x, y + s, a(sh[dark], alpha));
    }

    /**
     * Oscilloscope : quadrillage de petits carrés pliés, et une trace en ruban plié (dents de scie
     * qui s'amortissent) qui traverse l'écran, chaque segment en deux facettes.
     */
    private void scope(float w, float h) {
        float m = Math.min(w, h), u = Math.max(w, h);
        // Graduations : croisillons pliés aux nœuds d'une grille lâche.
        float g = m * 0.16f;
        for (float y = g * 0.5f; y < h; y += g) {
            for (float x = g * 0.5f; x < w; x += g) square(x - m * 0.006f, y - m * 0.006f, m * 0.012f, 1, 2, 0x22);
        }
        line(0, h * 0.58f, w, h * 0.58f, Math.max(1f, u / 900f), a(sh[2], 0x18));
        line(w * 0.5f, 0, w * 0.5f, h, Math.max(1f, u / 900f), a(sh[2], 0x10));
        // Trace : ruban de largeur bw, plié à chaque sommet (face haute claire, face basse sombre).
        int n = 13;
        float base = h * 0.58f, bw = m * 0.045f;
        float px = -w * 0.05f, py = base;
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            float x = -w * 0.05f + w * 1.1f * t;
            float amp = h * 0.3f * (float) Math.exp(-2.6 * t) + h * 0.025f;
            float y = base + ((i & 1) == 0 ? -amp : amp * 0.6f);
            tri(px, py - bw, x, y - bw, px, py, a(sh[3], 0x3C));
            tri(x, y - bw, x, y, px, py, a(sh[1], 0x3C));
            line(px, py - bw * 0.5f, x, y - bw * 0.5f, Math.max(1f, u / 1200f), a(fold, 0x22));
            if (i < n) kite(x, y - bw * 0.5f, m * 0.03f, m * 0.008f, ((i & 1) == 0 ? -1 : 1) * Math.PI / 2, 2, 4, 0x26, false);
            px = x;
            py = y;
        }
        // Lueur résiduelle : quelques échos en losanges sous la trace.
        for (int i = 0; i < 10; i++) {
            float s = m * (0.01f + 0.012f * rnd());
            kite(w * rnd(), base + h * (0.12f + 0.25f * rnd()), s * 2.2f, s * 0.55f, -Math.PI / 2, 2, 3, 0x1A, false);
        }
    }

    /** Art déco : soleil en gradins qui se lève du bas, rayons pliés, frise de chevrons en haut. */
    private void deco(float w, float h) {
        float m = Math.min(w, h), u = Math.max(w, h);
        float cx = w * 0.5f, cy = h * 1.02f;
        float R = Math.max(w * 0.62f, h * 0.7f);
        // Rayons : triangles longs, pliés au milieu, en éventail de -170° à -10°.
        int rays = 18;
        for (int i = 0; i < rays; i++) {
            double t0 = -Math.PI + Math.PI * (i + 0.15) / rays, t1 = -Math.PI + Math.PI * (i + 0.85) / rays;
            double tm = (t0 + t1) / 2;
            float r = R * ((i & 1) == 0 ? 1f : 0.8f);
            float ex = cx + (float) Math.cos(tm) * r, ey = cy + (float) Math.sin(tm) * r;
            float ax = cx + (float) Math.cos(t0) * r * 0.32f, ay = cy + (float) Math.sin(t0) * r * 0.32f;
            float bx = cx + (float) Math.cos(t1) * r * 0.32f, by = cy + (float) Math.sin(t1) * r * 0.32f;
            float mx = cx + (float) Math.cos(tm) * r * 0.32f, my = cy + (float) Math.sin(tm) * r * 0.32f;
            tri(ax, ay, ex, ey, mx, my, a(sh[1], (i & 1) == 0 ? 0x34 : 0x22));
            tri(mx, my, ex, ey, bx, by, a(sh[3], (i & 1) == 0 ? 0x3C : 0x28));
        }
        // Gradins : demi-octogones concentriques, chaque marche en deux facettes.
        int steps = 4, sides = 8;
        for (int st = steps; st >= 1; st--) {
            float r = R * 0.3f * st / steps;
            for (int i = 0; i < sides / 2; i++) {
                double t0 = -Math.PI + Math.PI * i / (sides / 2), t1 = -Math.PI + Math.PI * (i + 1) / (sides / 2);
                tri(cx, cy, cx + (float) Math.cos(t0) * r, cy + (float) Math.sin(t0) * r,
                        cx + (float) Math.cos(t1) * r, cy + (float) Math.sin(t1) * r,
                        a(sh[(i < sides / 4) ? 1 + (st & 1) : 2 + (st & 1)], 0x2C + st * 6));
            }
        }
        line(cx, cy - R * 0.3f, cx, cy, Math.max(1f, u / 700f), a(fold, 0x34));
        // Anneaux dorés : arcs fins autour du soleil, comme sur l'affiche.
        p.setStyle(Paint.Style.STROKE);
        android.graphics.RectF ring = new android.graphics.RectF();
        for (int i = 1; i <= 3; i++) {
            float rr = R * (0.3f + 0.17f * i);
            p.setStrokeWidth(Math.max(1f, u / (i == 1 ? 520f : 900f)));
            p.setColor(a(fold, 0x40 - i * 0xC));
            ring.set(cx - rr, cy - rr, cx + rr, cy + rr);
            c.drawArc(ring, 180, 180, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        // Frise de chevrons pliés le long du bord haut.
        float cw = m * 0.05f;
        for (float x = -cw; x < w + cw; x += cw * 2) {
            float y = m * 0.05f;
            tri(x, y, x + cw, y + cw, x + cw, y, a(sh[1], 0x20));
            tri(x + cw, y, x + cw, y + cw, x + cw * 2, y, a(sh[3], 0x20));
        }
        line(0, m * 0.05f, w, m * 0.05f, Math.max(1f, u / 1000f), a(fold, 0x1C));
    }

    /** Cascade de curseurs : colonnes de pavés pliés qui s'estompent vers le bas, gros curseur plié. */
    private void cursors(float w, float h) {
        float m = Math.min(w, h), u = Math.max(w, h);
        float cell = m * 0.035f;
        int cols = Math.max(6, Math.round(w / (cell * 2.1f)));
        for (int col = 0; col < cols; col++) {
            if (rnd() < 0.2f) continue;
            float x = (col + 0.5f) * w / cols - cell * 0.5f;
            float top = -h * 0.2f + h * 0.7f * rnd();
            int len = 4 + Math.round(rnd() * 9);
            for (int j = 0; j < len; j++) {
                float y = top + j * cell * 1.35f;
                if (y > h) break;
                // La tête de colonne est la plus claire, la traîne s'éteint.
                int al = j == len - 1 ? 0x3A : Math.max(0x08, 0x26 - (len - 1 - j) * 3);
                boolean head = j == len - 1;
                if (rnd() < 0.2f && !head) continue;
                square(x, y, cell, head ? 2 : 0, head ? 4 : 1, al);
            }
        }
        // Gros curseur clignotant en bas à droite : pavé plié avec son pli au ton du thème.
        float s = m * 0.12f, x = w * 0.78f, y = h * 0.74f;
        tri(x, y, x + s * 0.6f, y, x, y + s, a(sh[3], 0x2C));
        tri(x + s * 0.6f, y, x + s * 0.6f, y + s, x, y + s, a(sh[1], 0x2C));
        line(x + s * 0.6f, y, x, y + s, Math.max(1f, u / 700f), a(fold, 0x40));
        // Invite « > » en deux facettes à gauche du curseur.
        float gx = x - s * 0.7f, gy = y + s * 0.5f, gs = s * 0.35f;
        tri(gx, gy - gs, gx + gs, gy, gx + gs * 0.3f, gy - gs, a(sh[2], 0x30));
        tri(gx, gy + gs, gx + gs, gy, gx + gs * 0.3f, gy + gs, a(sh[1], 0x30));
    }

    // ------------------------------------------------------------ Codec

    /** Codec : 0 radar plié (retenu), 1 onde de transmission, 2 circuit en rubans. */
    static void codec(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 31);
        if (variant == 1) f.wave(w, h);
        else if (variant == 2) f.ribbons(w, h);
        else f.radar(w, h);
    }

    /** Vrai pendant le dessin du décor fixe d'un fond animé : ce qui bouge n'y est pas dessiné. */
    static boolean MOVING;

    /** Radar : anneaux octogonaux en facettes, faisceau en éventail plié, échos en losanges. */
    private void radar(float w, float h) {
        float u = Math.max(w, h);
        float cx = w * 0.80f, cy = h * 0.76f;
        int sides = 8;
        for (int ring = 5; ring >= 1; ring--) {
            float r0 = u * 0.11f * ring, r1 = u * 0.11f * (ring - 0.18f);
            for (int s = 0; s < sides; s++) {
                double t0 = Math.PI * 2 * s / sides - Math.PI / 8, t1 = t0 + Math.PI * 2 / sides;
                float ax = cx + (float) Math.cos(t0) * r0, ay = cy + (float) Math.sin(t0) * r0;
                float bx = cx + (float) Math.cos(t1) * r0, by = cy + (float) Math.sin(t1) * r0;
                float ix = cx + (float) Math.cos(t0) * r1, iy = cy + (float) Math.sin(t0) * r1;
                float jx = cx + (float) Math.cos(t1) * r1, jy = cy + (float) Math.sin(t1) * r1;
                // Côté gauche du pli vertical dans l'ombre, côté droit dans la lumière.
                boolean left = Math.cos((t0 + t1) / 2) < 0;
                int al = 0x10 + ring * 2;
                tri(ax, ay, bx, by, ix, iy, a(sh[left ? 0 : 2], al));
                tri(bx, by, jx, jy, ix, iy, a(sh[left ? 1 : 3], al));
            }
        }
        // Fond animé (0.62.3) : faisceau et échos sont dessinés en mouvement par BackdropMotion.
        if (MOVING) {
            scan(w, h);
            return;
        }
        // Faisceau : éventail de triangles qui s'éteint en tournant.
        int blades = 9;
        float R = u * 0.55f;
        for (int b = 0; b < blades; b++) {
            double t0 = -Math.PI * 0.62 - b * 0.07, t1 = t0 - 0.07;
            int al = Math.round(0x2C * (1f - b / (float) blades));
            tri(cx, cy, cx + (float) Math.cos(t0) * R, cy + (float) Math.sin(t0) * R,
                    cx + (float) Math.cos(t1) * R, cy + (float) Math.sin(t1) * R, a(sh[(b & 1) == 0 ? 3 : 2], al));
        }
        line(cx, cy, cx + (float) Math.cos(-Math.PI * 0.62) * R, cy + (float) Math.sin(-Math.PI * 0.62) * R,
                Math.max(1f, u / 700f), a(fold, 0x40));
        // Échos.
        for (int i = 0; i < 5; i++) {
            double t = -Math.PI * (0.55 + 0.4 * rnd());
            float d = u * (0.12f + 0.4f * rnd());
            float ex = cx + (float) Math.cos(t) * d, ey = cy + (float) Math.sin(t) * d;
            float s = u * 0.008f;
            kite(ex, ey + s, s * 2, s * 0.9f, -Math.PI / 2, 2, 4, 0x50, false);
        }
        scan(w, h);
    }

    /** Onde : les barres de fréquence d'un appel codec, en prismes pliés autour d'un pli horizontal. */
    private void wave(float w, float h) {
        float mid = h * 0.62f;
        int n = 34;
        float bw = w / n;
        for (int i = 0; i < n; i++) {
            float t = (i + 0.5f) / n;
            float env = (float) (0.35 + 0.65 * Math.pow(Math.sin(Math.PI * t), 1.5));
            float amp = h * 0.16f * env * (0.35f + 0.65f * Math.abs((float) Math.sin(i * 0.9 + 1.3) * (0.6f + 0.4f * rnd())));
            float x0 = i * bw + bw * 0.12f, x1 = (i + 1) * bw - bw * 0.12f, xm = (x0 + x1) / 2;
            int al = 0x1E + Math.round(0x16 * env);
            // Moitié haute : pointe en haut, ombre à gauche ; moitié basse en reflet, plus pâle.
            tri(x0, mid, xm, mid - amp, xm, mid, a(sh[1], al));
            tri(xm, mid - amp, x1, mid, xm, mid, a(sh[3], al));
            tri(x0, mid, xm, mid + amp * 0.6f, xm, mid, a(sh[0], al / 2));
            tri(xm, mid + amp * 0.6f, x1, mid, xm, mid, a(sh[2], al / 2));
        }
        line(0, mid, w, mid, Math.max(1f, h / 600f), a(fold, 0x30));
        // Fréquence en haut : petits repères en losanges, comme le cadran 140.85.
        for (int i = 0; i <= 20; i++) {
            float x = w * (0.1f + 0.8f * i / 20f), s = h * (i % 5 == 0 ? 0.016f : 0.008f);
            kite(x, h * 0.2f + s, s * 2, s * 0.5f, -Math.PI / 2, 1, 3, i % 5 == 0 ? 0x40 : 0x24, false);
        }
        scan(w, h);
    }

    /** Circuit : pistes en rubans de papier pliés à 45°, chaque segment ombré d'un côté. */
    private void ribbons(float w, float h) {
        float m = Math.min(w, h), rw = m * 0.022f;
        for (int k = 0; k < 7; k++) {
            float x = w * rnd(), y = h * (0.35f + 0.65f * rnd());
            int segs = 4 + (int) (rnd() * 4);
            boolean flip = false;
            for (int s = 0; s < segs; s++) {
                float len = m * (0.08f + 0.14f * rnd());
                float dx, dy;
                int dir = (int) (rnd() * 3);
                if (dir == 0) { dx = len; dy = 0; }
                else if (dir == 1) { dx = len * 0.7f; dy = -len * 0.7f; }
                else { dx = 0; dy = -len; }
                if (rnd() < 0.4f) dx = -dx;
                float nx = x + dx, ny = y + dy;
                float l = (float) Math.hypot(dx, dy);
                float px = -dy / l * rw, py = dx / l * rw;
                // Chaque pli inverse la face visible du ruban.
                int c0 = flip ? 3 : 1, c1 = flip ? 2 : 0;
                tri(x + px, y + py, nx + px, ny + py, nx - px, ny - py, a(sh[c0], 0x2A));
                tri(x + px, y + py, nx - px, ny - py, x - px, y - py, a(sh[c1], 0x2A));
                line(x, y, nx, ny, Math.max(1f, m / 900f), a(fold, 0x18));
                flip = !flip;
                x = nx;
                y = ny;
            }
            // Pastille d'arrivée : losange plié.
            kite(x, y + rw * 1.5f, rw * 3f, rw * 1.2f, -Math.PI / 2, 1, 4, 0x48, true);
        }
        scan(w, h);
    }

    /** Lignes de balayage d'écran, la signature Codec. */
    private void scan(float w, float h) {
        p.setColor(a(0xFFFFFFFF, 0x08));
        float step = Math.max(3f, Math.max(w, h) / 300f);
        for (float y = 0; y < h; y += step * 2) c.drawRect(0, y, w, y + step * 0.5f, p);
    }
}
