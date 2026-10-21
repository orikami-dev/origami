package com.hideo.foldplay;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.RectF;
import android.graphics.Shader;
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

    /** Arctique : 0 glaciers et aurore, 1 banquise et flocons (manette), 3 nuit polaire (fond), 4 banquise de nuit (manette assortie). */
    static void arctique(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 21);
        if (variant == 1) f.floes(w, h);
        else if (variant == 3) f.polaire(w, h);
        else if (variant == MANETTE) f.polairePad(w, h);
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

    /** Sakura : 0 mont et branche en fleurs, 1 vagues et pétales (manette), 3 rivière et pont (fond), 4 eau de la rivière (manette assortie). */
    static void sakura(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 8);
        if (variant == 1) f.waves(w, h);
        else if (variant == 3) f.riviere(w, h);
        else if (variant == MANETTE) f.rivierePad(w, h);
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

    /** Écarlate : 0 flammes pliées, 1 éventail plié et soleil levant, 3 nuit japonaise (fond), 4 lune et torii (manette assortie). */
    static void ecarlate(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 13);
        if (variant == 1) f.fan(w, h);
        else if (variant == 3) f.japon(w, h);
        else if (variant == MANETTE) f.japonPad(w, h);
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

    /** Codec : 0 radar plié, 1 onde de transmission, 2 circuit en rubans, 3 base de nuit (fond), 4 écran radar de la base (manette assortie). */
    static void codec(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 31);
        if (variant == 1) f.wave(w, h);
        else if (variant == 2) f.ribbons(w, h);
        else if (variant == 3) f.base(w, h);
        else if (variant == MANETTE) f.basePad(w, h);
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

    // ------------------------------------------------------------ Fonds d'écran « nuit » (variante 3)

    /** Variante 3 de chaque thème : le fond d'écran du haut en grande scène nocturne (sans texte). */
    static final int FOND = 3;

    /**
     * Variante 4 : la façade de manette assortie au fond « nuit » (comme le soleil d'Ambre l'est à la ville).
     * Même univers, autre image : un motif plutôt qu'une scène, calme sous la croix et les boutons.
     */
    static final int MANETTE = 4;

    private void poly(int color, float... xy) {
        path.reset();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
        path.close();
        p.setColor(color);
        c.drawPath(path, p);
    }

    private void disc(float cx, float cy, float r, int color) {
        p.setColor(color);
        c.drawCircle(cx, cy, r, p);
    }

    /** Étoile : petit losange étiré. */
    private void star(float x, float y, float s, int alpha) {
        poly(a(sh[4], alpha), x, y - s * 2, x + s, y, x, y + s * 2, x - s, y);
    }

    /** Rectangle en dégradé vertical. */
    private void vgrad(float x0, float y0, float x1, float y1, int c0, int c1) {
        p.setShader(new LinearGradient(x0, y0, x0, y1, c0, c1, Shader.TileMode.CLAMP));
        c.drawRect(x0, y0, x1, y1, p);
        p.setShader(null);
    }

    private void arcLine(float cx, float cy, float r, float start, float sweep, float wpx, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(wpx);
        p.setColor(color);
        c.drawArc(new RectF(cx - r, cy - r, cx + r, cy + r), start, sweep, false, p);
        p.setStyle(Paint.Style.FILL);
    }

    // ---- Codec : base militaire de nuit

    /** Repères de la base (tour radar, bout de grue, tour de garde), partagés avec BackdropMotion (balises, balayage). */
    static float[] basePts(float w, float h) {
        float H0 = Math.min(h, w * 0.9f), ground = h * 0.88f;
        return new float[] {w * 0.9f, ground - H0 * 0.6f, w * 0.5f, ground - H0 * 0.46f, w * 0.04f, ground - H0 * 0.3f};
    }

    /**
     * Base de nuit sous la vision nocturne : règle de réglage de fréquence en haut, anneaux octogonaux du
     * radar autour de la tour, conteneurs et portique de grue à droite, tour de garde à gauche, clôture,
     * et l'onde radio qui court au pied de la scène. Le centre reste bas et sombre.
     */
    private void base(float w, float h) {
        float u = Math.max(w, h), H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f;
        float ground = h * 0.88f, lw = Math.max(1f, u / 1100f);
        float[] pt = basePts(w, h);
        int dk = 0xFF070C09, dk2 = 0xFF0D150F;
        // Règle de réglage : graduations en haut, de plus en plus nettes vers la droite.
        float tx0 = w * 0.42f;
        for (int i = 0; tx0 + i * q * 0.28f < w; i++) {
            float x = tx0 + i * q * 0.28f, fade = (x - tx0) / (w - tx0);
            line(x, 0, x, (i % 5 == 0) ? q * 0.38f : q * 0.2f, lw, a(sh[3], Math.round(0x38 * fade)));
        }
        // Anneaux octogonaux et rayons autour du radar.
        float rcx = pt[0], rcy = pt[1] + q * 1.2f;
        for (int ring = 1; ring <= 4; ring++) {
            float r = H0 * 0.13f * ring;
            for (int s2 = 0; s2 < 16; s2++) {
                double t0 = Math.PI * 2 * s2 / 16, t1 = t0 + Math.PI / 8;
                line(rcx + (float) Math.cos(t0) * r, rcy + (float) Math.sin(t0) * r,
                        rcx + (float) Math.cos(t1) * r, rcy + (float) Math.sin(t1) * r, lw, a(sh[2], 0x24 - ring * 3));
            }
        }
        for (int s2 = 0; s2 < 8; s2++) {
            double t0 = Math.PI * 2 * s2 / 8 + Math.PI / 8;
            line(rcx, rcy, rcx + (float) Math.cos(t0) * H0 * 0.52f, rcy + (float) Math.sin(t0) * H0 * 0.52f, lw, a(sh[2], 0x0E));
        }
        // Entrepôts lointains.
        float bx = -q;
        while (bx < w + q) {
            float bw = q * (1.5f + 2f * rnd());
            float edge = Math.min(1f, Math.abs(bx / w - 0.4f) * 2.2f);
            float bh = H0 * (0.03f + 0.06f * rnd()) * (0.6f + 0.8f * edge);
            box(bx, ground - bh, bx + bw, ground + 1, a(0xFF0C150F, 0xC0));
            line(bx, ground - bh, bx + bw, ground - bh, lw, a(sh[2], 0x20));
            bx += bw * (0.8f + 0.3f * rnd());
        }
        // Conteneurs empilés : surtout à droite, une pile ou deux au bord gauche.
        float cw = q * 2.3f, chh = q * 0.95f;
        for (float x = -cw * 0.5f; x < w; x += cw * 1.04f) {
            float fx = x / w;
            int n;
            float lum;
            if (fx > 0.55f) {
                n = 1 + Math.round(rnd() * (0.5f + 3.2f * Math.min(1f, (fx - 0.5f) * 3f)));
                lum = 0.8f;
            } else if (fx < 0.14f) {
                n = 1 + Math.round(rnd() * 2f);
                lum = 0.7f;
            } else {
                n = rnd() < 0.5f ? 1 : 0;
                lum = 0.35f;
            }
            for (int j = 0; j < n; j++) {
                float y1 = ground - j * chh, y0 = y1 - chh;
                box(x, y0, x + cw, y1, a((j & 1) == 0 ? dk : dk2, 0xF2));
                box(x + cw * 0.5f, y0, x + cw, y1, a(0xFF111B14, 0x80));
                for (int r2 = 1; r2 < 9; r2++) line(x + cw * r2 / 9f, y0 + chh * 0.1f, x + cw * r2 / 9f, y1 - chh * 0.1f, lw, a(sh[2], Math.round(0x0E * lum)));
                line(x, y0, x + cw, y0, lw, a(sh[3], Math.round(0x50 * lum)));
                line(x + cw, y0, x + cw, y1, lw, a(sh[3], Math.round(0x34 * lum)));
            }
        }
        // Portique de grue : deux jambes, flèche qui avance vers le centre, câble et crochet.
        float gx0 = w * 0.64f, gx1 = gx0 + q * 3f, gy = pt[3], bw2 = Math.max(lw * 2.5f, q * 0.2f);
        box(gx0, gy, gx0 + bw2, ground, a(dk, 0xF4));
        box(gx1, gy, gx1 + bw2, ground, a(dk, 0xF4));
        box(pt[2], gy - bw2, gx1 + q * 1.5f, gy + bw2, a(dk, 0xF4));
        int zig = 12;
        for (int i = 0; i < zig; i++) {
            float xa = pt[2] + (gx1 - pt[2]) * i / zig, xb = pt[2] + (gx1 - pt[2]) * (i + 1) / zig;
            line(xa, (i & 1) == 0 ? gy - bw2 : gy + bw2, xb, (i & 1) == 0 ? gy + bw2 : gy - bw2, lw, a(sh[2], 0x28));
        }
        line(gx0, gy, gx0, ground, lw, a(sh[3], 0x40));
        line(gx1 + bw2, gy, gx1 + bw2, ground, lw, a(sh[3], 0x40));
        line(pt[2], gy - bw2, gx1 + q * 1.5f, gy - bw2, lw, a(sh[3], 0x50));
        line(pt[2] + q, gy + bw2, pt[2] + q, gy + q * 2.2f, lw, a(sh[2], 0x38));
        box(pt[2] + q - q * 0.2f, gy + q * 2.2f, pt[2] + q + q * 0.2f, gy + q * 2.6f, a(sh[2], 0x40));
        // Tour radar : mât en treillis, parabole, antenne.
        float topY = pt[1] + q * 1.5f;
        for (int sd = -1; sd <= 1; sd += 2) line(rcx + sd * q * 0.9f, ground, rcx + sd * q * 0.2f, topY, Math.max(lw * 2.5f, q * 0.14f), a(dk, 0xF6));
        int seg = 9;
        for (int i = 0; i < seg; i++) {
            float ya = ground - (ground - topY) * i / seg, yb = ground - (ground - topY) * (i + 1) / seg;
            float xa = q * (0.9f - 0.7f * i / seg), xb = q * (0.9f - 0.7f * (i + 1) / seg);
            line(rcx - xa, ya, rcx + xb, yb, lw, a(sh[2], 0x30));
            line(rcx + xa, ya, rcx - xb, yb, lw, a(sh[2], 0x30));
        }
        float dcy = topY - q * 0.2f;
        arcLine(rcx, dcy, q * 1.7f, 205, 130, Math.max(lw * 3f, q * 0.16f), a(dk, 0xF6));
        arcLine(rcx, dcy, q * 1.7f, 205, 130, lw * 1.4f, a(sh[3], 0x70));
        line(rcx, topY, rcx, dcy - q * 1.0f, lw * 2f, a(dk, 0xF6));
        line(rcx, dcy - q * 1.0f, rcx - q * 0.5f, dcy - q * 2.0f, lw * 1.5f, a(sh[3], 0x60));
        // Tour de garde au bord gauche : cabine sur pilotis, projecteur.
        float gtx = pt[4], gty = pt[5];
        for (int sd = -1; sd <= 1; sd += 2) line(gtx + sd * q * 0.8f, ground, gtx + sd * q * 0.6f, gty + q, Math.max(lw * 2f, q * 0.12f), a(dk, 0xF6));
        box(gtx - q * 1.1f, gty, gtx + q * 1.1f, gty + q, a(dk, 0xF8));
        poly(a(dk, 0xF8), gtx - q * 1.4f, gty, gtx, gty - q * 0.6f, gtx + q * 1.4f, gty);
        line(gtx - q * 1.1f, gty + q * 0.55f, gtx + q * 1.1f, gty + q * 0.55f, lw, a(sh[4], 0x58));
        line(gtx - q * 1.4f, gty, gtx, gty - q * 0.6f, lw, a(sh[3], 0x50));
        tri(gtx + q * 1.1f, gty + q * 0.5f, gtx + q * 16f, gty - q * 3.2f, gtx + q * 16f, gty + q * 0.2f, a(sh[4], 0x08));
        // Clôture le long du sol.
        for (float x = 0; x < w; x += q * 1.4f) line(x, ground, x, ground - q * 0.8f, lw, a(sh[2], 0x24));
        line(0, ground - q * 0.8f, w, ground - q * 0.8f, lw, a(sh[2], 0x20));
        // Sol : quadrillage en perspective et onde radio.
        box(0, ground, w, h, a(0xFF060907, 0xF6));
        line(0, ground, w, ground, lw * 1.5f, a(sh[3], 0x50));
        for (int i = 1; i <= 6; i++) {
            float y = ground + (h - ground) * (float) Math.pow(i / 6.0, 1.8);
            line(0, y, w, y, lw, a(sh[2], 0x12));
        }
        for (int j = -8; j <= 8; j++) line(w * 0.5f + j * q * 0.3f, ground, w * 0.5f + j * w * 0.12f, h, lw, a(sh[2], 0x0C));
        float wy = ground + (h - ground) * 0.5f, px = 0, py = wy;
        for (int i = 1; i <= 200; i++) {
            float x = w * i / 200f, t = x / w;
            float amp = q * 0.5f * (0.12f + 0.88f * t * t);
            float y = wy + amp * ((float) Math.sin(x / q * 2.2f) * 0.7f + (float) Math.sin(x / q * 5.3f + 1f) * 0.45f);
            line(px, py, x, y, lw * 1.2f, a(sh[3], Math.round(0x14 + 0x34 * t)));
            px = x;
            py = y;
        }
        for (int i = 0; i < 40; i++) star(w * rnd(), ground * 0.8f * rnd(), u * (0.001f + 0.0012f * rnd()), 0x18 + Math.round(rnd() * 0x28));
    }

    // ---- Arctique : nuit polaire

    /**
     * Nuit polaire : trois rideaux d'aurore en colonnes, crêtes facettées, falaises de glacier aux bords
     * (grandes à droite), mer sombre et banquise en plaques. Le centre et la gauche restent calmes.
     */
    private void polaire(float w, float h) {
        float u = Math.max(w, h), H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f;
        float ground = h * 0.8f, lw = Math.max(1f, u / 1100f);
        for (int i = 0; i < 50; i++) star(w * rnd(), ground * 0.75f * rnd(), u * (0.001f + 0.0014f * rnd()), 0x28 + Math.round(rnd() * 0x50));
        // Aurore : rideaux en colonnes, vives sur leur bord bas, de plus en plus faibles vers la gauche.
        int[] cols = {sh[2], 0xFF6CFFC0, 0xFF9C8CFF};
        int n = 110;
        for (int j = 0; j < 3; j++) {
            float base = ground * (0.2f + 0.13f * j), amp = ground * 0.07f, hb = ground * (0.2f - 0.03f * j);
            for (int i = 0; i < n; i++) {
                float x0 = Math.round(w * i / n), x1 = Math.round(w * (i + 1) / n), t = (x0 + x1) / 2 / w;
                float y = base + (float) Math.sin(t * Math.PI * (1.3f + 0.5f * j) + j * 1.7) * amp + (float) Math.sin(t * 9 + j) * amp * 0.2f;
                int al = Math.round((0x40 - 0x0E * j) * (0.2f + 0.8f * t));
                vgrad(x0, y - hb, x1, y, a(cols[j], 0), a(cols[j], al));
                vgrad(x0, y, x1, y + hb * 0.35f, a(cols[j], al), a(cols[j], 0));
            }
        }
        // Crêtes lointaines : chaque sommet en deux faces, calotte de neige.
        int pk = 16;
        float hw = w / (pk - 1) * 1.0f;
        for (int i = 0; i < pk; i++) {
            float xi = w * i / (pk - 1), edge = 0.35f + 0.65f * Math.min(1f, Math.abs(xi / w - 0.35f) * 1.8f);
            float hi = H0 * (0.05f + 0.13f * rnd()) * edge;
            poly(a(0xFF0A131E, 0xF2), xi - hw, ground, xi, ground - hi, xi, ground);
            poly(a(0xFF122336, 0xF2), xi, ground - hi, xi + hw, ground, xi, ground);
            poly(a(sh[4], 0x28), xi - hw * 0.2f, ground - hi * 0.8f, xi, ground - hi, xi + hw * 0.16f, ground - hi * 0.84f);
            line(xi, ground - hi, xi + hw, ground, lw, a(fold, 0x22));
        }
        // Falaises de glacier : pans de glace aux sommets inclinés, deux tons, liserés de lumière, crevasses.
        for (int side = 0; side < 2; side++) {
            float xs = side == 0 ? w * 0.6f : -q, xe = side == 0 ? w + q : w * 0.13f;
            float x0 = xs, hp = 0;
            int idx = 0;
            while (x0 < xe) {
                float x1 = x0 + q * (0.4f + 1.1f * rnd());
                float t = Math.max(0f, Math.min(1f, (x1 - xs) / (xe - xs)));
                float env = side == 0 ? 0.08f + 0.36f * (float) Math.pow(t, 1.2) : 0.06f + 0.16f * (1f - t) * (1f - t);
                float hn = H0 * env * (0.55f + 0.65f * rnd());
                if (idx == 0) hp = hn;
                hn = Math.max(hp - q * 1.4f, Math.min(hp + q * 1.4f, hn));
                int dark = (idx & 1) == 0 ? 0xFF0C1824 : 0xFF16293D;
                poly(a(dark, 0xF4), x0, ground + 1, x0, ground - hp, x1, ground - hn, x1, ground + 1);
                poly(a(sh[4], 0x16), x0, ground - hp, x1, ground - hn, x1, ground - hn + (hn > hp ? q * 0.5f : q * 0.35f), x0, ground - hp + q * 0.4f);
                line(x0, ground - hp, x1, ground - hn, lw * 1.4f, a(fold, 0x5A));
                line(x1, ground - hn, x1, ground, lw, a(sh[4], 0x22));
                if (rnd() < 0.6f) line(x0 + (x1 - x0) * 0.5f, ground - (hp + hn) * 0.4f, x0 + (x1 - x0) * 0.4f, ground - (hp + hn) * 0.1f, lw, a(sh[4], 0x20));
                hp = hn;
                x0 = x1;
                idx++;
            }
        }
        // Mer sombre et reflet d'aurore.
        vgrad(0, ground, w, h, a(0xFF0C1926, 0xF6), a(0xFF050A10, 0xFA));
        for (int i = 0; i < 12; i++) vgrad(w * (0.4f + 0.05f * i), ground, w * (0.45f + 0.05f * i) + 1, h, a(sh[2], Math.round(0x1C * i / 11f)), a(sh[2], 0));
        line(0, ground, w, ground, lw * 1.4f, a(fold, 0x48));
        // Banquise : plaques à deux faces, plus grandes au premier plan, rares au centre.
        for (int row = 0; row < 5; row++) {
            float t = row / 4f, y = ground + (h - ground) * (0.14f + 0.78f * t), s = q * (0.8f + 2.4f * t);
            for (float x = -s; x < w + s; x += s * (2.4f + rnd())) {
                float fx = x / w;
                if (rnd() > 0.28f + 0.72f * fx) continue;
                float lum = 0.5f + 0.5f * fx;
                poly(a(0xFF070F18, 0xF4), x - s, y, x - s * 0.6f, y + s * 0.16f, x + s * 0.4f, y + s * 0.18f, x + s, y,
                        x + s, y + s * 0.12f, x + s * 0.4f, y + s * 0.3f, x - s * 0.6f, y + s * 0.28f, x - s, y + s * 0.12f);
                poly(a(0xFF14263A, 0xF4), x - s, y, x - s * 0.5f, y - s * 0.22f, x, y - s * 0.2f, x, y + s * 0.17f, x - s * 0.6f, y + s * 0.16f);
                poly(a(0xFF223A54, 0xF4), x, y - s * 0.2f, x + s * 0.6f, y - s * 0.2f, x + s, y, x + s * 0.4f, y + s * 0.18f, x, y + s * 0.17f);
                line(x - s * 0.5f, y - s * 0.22f, x + s * 0.6f, y - s * 0.2f, lw, a(fold, Math.round(0x50 * lum)));
                line(x + s * 0.6f, y - s * 0.2f, x + s, y, lw, a(sh[4], Math.round(0x38 * lum)));
            }
        }
    }

    // ---- Écarlate : nuit japonaise

    /** Hauteur de colline (t de 0 à 1 en travers de l'écran) : basse au centre, un grand mont à droite. */
    static float hillH(float t, float H0) {
        float g = (t - 0.84f) / 0.17f;
        float g2 = (t - 0.04f) / 0.1f;
        return H0 * (0.05f + 0.025f * (float) Math.sin(t * 9f) + 0.30f * (float) Math.exp(-g * g) + 0.12f * (float) Math.exp(-g2 * g2));
    }

    /** Repères du torii (x, hauteur de la poutre, portée), partagés avec BackdropMotion (lanternes suspendues). */
    static float[] japonPts(float w, float h) {
        float H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f, ground = h * 0.9f;
        float tx = w * 0.72f;
        return new float[] {tx, ground - hillH(0.72f, H0) + q * 0.1f - q * 3.6f + q * 0.14f, q * 3.8f};
    }

    private void pin(float x, float y, float hgt, float lw) {
        float ww = hgt * 0.22f;
        box(x - lw * 1.2f, y - hgt * 0.35f, x + lw * 1.2f, y + 1, a(0xFF0A0405, 0xF8));
        for (int i = 0; i < 4; i++) {
            float yy = y - hgt * (0.28f + 0.2f * i), wd = ww * (1f - 0.22f * i);
            poly(a(0xFF0A0405, 0xF8), x - wd, yy, x, yy - hgt * 0.3f, x + wd, yy);
            line(x, yy - hgt * 0.3f, x + wd, yy, lw, a(sh[3], 0x30));
        }
    }

    /**
     * Nuit japonaise : lune rouge laquée en facettes et ses nervures d'éventail, nuages en écailles,
     * collines sombres, pagode à cinq toits et torii sur le grand mont à droite, pins et lanterne de
     * pierre au bord gauche. Le centre reste bas et sombre.
     */
    private void japon(float w, float h) {
        float u = Math.max(w, h), H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f;
        float ground = h * 0.9f, lw = Math.max(1f, u / 1100f);
        float mx = w * 0.56f, my = Math.min(h * 0.25f, ground - H0 * 0.62f), mr = H0 * 0.12f;
        for (int i = 0; i < 28; i++) star(w * rnd(), ground * 0.5f * rnd(), u * (0.001f + 0.0012f * rnd()), 0x20 + Math.round(rnd() * 0x40));
        // Nervures d'éventail derrière la lune.
        for (int i = 0; i < 26; i++) {
            double ang = Math.PI * 2 * i / 26;
            line(mx + (float) Math.cos(ang) * mr * 1.3f, my + (float) Math.sin(ang) * mr * 1.3f,
                    mx + (float) Math.cos(ang) * u, my + (float) Math.sin(ang) * u, lw, a(fold, 0x0A));
        }
        // Lune : halos et disque à vingt facettes.
        disc(mx, my, mr * 1.6f, a(sh[2], 0x0C));
        disc(mx, my, mr * 1.3f, a(sh[2], 0x14));
        int fc = 20;
        for (int i = 0; i < fc; i++) {
            double t0 = Math.PI * 2 * i / fc, t1 = Math.PI * 2 * (i + 1) / fc;
            boolean left = Math.cos((t0 + t1) / 2) < 0;
            tri(mx, my, mx + (float) Math.cos(t0) * mr, my + (float) Math.sin(t0) * mr,
                    mx + (float) Math.cos(t1) * mr, my + (float) Math.sin(t1) * mr, a(sh[left ? 1 : 2], (i & 1) == 0 ? 0x4A : 0x3E));
        }
        arcLine(mx, my, mr, 0, 360, lw * 1.4f, a(fold, 0x40));
        // Nuages en écailles : rangées d'arcs, de plus en plus visibles vers la droite.
        for (int j = 0; j < 4; j++) {
            float r = q * (0.9f + 0.25f * j), y = my + mr * (0.5f + 0.75f * j);
            for (float x = w * (0.3f + 0.05f * j) + (j & 1) * r; x < w + r; x += r * 2) {
                arcLine(x, y, r, 180, 180, lw, a(fold, Math.round(0x1C * (x / w))));
                arcLine(x, y, r * 0.66f, 180, 180, lw, a(fold, Math.round(0x12 * (x / w))));
            }
        }
        // Collines : lointaines, puis proches avec le grand mont.
        int steps = 64;
        for (int layer = 0; layer < 2; layer++) {
            path.reset();
            path.moveTo(0, h);
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                float hh = layer == 0 ? H0 * (0.12f + 0.05f * (float) Math.sin(t * 7f + 1f) + 0.12f * t) : hillH(t, H0);
                path.lineTo(w * t, ground - hh - (layer == 0 ? 0 : 0));
            }
            path.lineTo(w, h);
            path.close();
            p.setColor(layer == 0 ? a(0xFF1D0B0C, 0xC8) : a(0xFF0A0506, 0xFA));
            c.drawPath(path, p);
            float px = 0, py = 0;
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                float hh = layer == 0 ? H0 * (0.12f + 0.05f * (float) Math.sin(t * 7f + 1f) + 0.12f * t) : hillH(t, H0);
                float x = w * t, y = ground - hh;
                if (i > 0) line(px, py, x, y, lw * 1.3f, a(layer == 0 ? sh[1] : sh[3], layer == 0 ? 0x18 : 0x3A));
                px = x;
                py = y;
            }
        }
        // Pagode sur le mont.
        float pgx = w * 0.88f, y = ground - hillH(0.88f, H0) + q * 0.2f, bw = q * 2.6f;
        for (int i = 0; i < 5; i++) {
            float bwi = bw * (1f - 0.15f * i), bh = q * 0.7f;
            box(pgx - bwi / 2, y - bh, pgx, y, a(0xFF1A0707, 0xF8));
            box(pgx, y - bh, pgx + bwi / 2, y, a(0xFF260B0A, 0xF8));
            box(pgx - bwi * 0.12f, y - bh * 0.7f, pgx + bwi * 0.12f, y - bh * 0.3f, a(sh[3], 0x48));
            float yb = y - bh, ew = bwi * 0.5f + q * 0.75f;
            poly(a(0xFF120505, 0xFA), pgx - ew - q * 0.3f, yb - q * 0.2f, pgx - ew * 0.7f, yb + q * 0.03f,
                    pgx + ew * 0.7f, yb + q * 0.03f, pgx + ew + q * 0.3f, yb - q * 0.2f, pgx + bwi * 0.4f, yb - q * 0.42f, pgx - bwi * 0.4f, yb - q * 0.42f);
            line(pgx - ew - q * 0.3f, yb - q * 0.2f, pgx - ew * 0.7f, yb + q * 0.03f, lw, a(fold, 0x50));
            line(pgx + ew + q * 0.3f, yb - q * 0.2f, pgx + ew * 0.7f, yb + q * 0.03f, lw, a(fold, 0x50));
            line(pgx + bwi * 0.4f, yb - q * 0.42f, pgx + ew + q * 0.3f, yb - q * 0.2f, lw * 1.3f, a(sh[3], 0x78));
            y = yb - q * 0.36f;
        }
        line(pgx, y, pgx, y - q * 1.7f, lw * 1.6f, a(fold, 0x70));
        for (int i = 0; i < 3; i++) line(pgx - q * 0.22f, y - q * (0.5f + 0.4f * i), pgx + q * 0.22f, y - q * (0.5f + 0.4f * i), lw, a(fold, 0x60));
        // Torii.
        float[] tp = japonPts(w, h);
        float tx = tp[0], span = tp[2], yt = ground - hillH(0.72f, H0) + q * 0.1f, ht = q * 3.6f;
        for (int sd = -1; sd <= 1; sd += 2) {
            float xl = tx + sd * span * 0.5f;
            float yb = ground - hillH(xl / w, H0) + q * 0.3f;
            poly(a(sh[0], 0xFA), xl - q * 0.15f, yb, xl + q * 0.15f, yb, xl + q * 0.11f, yt - ht, xl - q * 0.11f, yt - ht);
            line(xl + q * 0.14f, yb, xl + q * 0.1f, yt - ht, lw * 1.2f, a(sh[3], 0x70));
        }
        poly(a(sh[0], 0xFA), tx - span * 0.5f, yt - ht * 0.72f, tx + span * 0.5f, yt - ht * 0.72f, tx + span * 0.5f, yt - ht * 0.72f + q * 0.17f, tx - span * 0.5f, yt - ht * 0.72f + q * 0.17f);
        poly(a(0xFF16080A, 0xFC), tx - span * 0.68f, yt - ht - q * 0.3f, tx - span * 0.3f, yt - ht - q * 0.04f, tx + span * 0.3f, yt - ht - q * 0.04f,
                tx + span * 0.68f, yt - ht - q * 0.3f, tx + span * 0.5f, yt - ht + q * 0.16f, tx - span * 0.5f, yt - ht + q * 0.16f);
        line(tx - span * 0.68f, yt - ht - q * 0.3f, tx - span * 0.3f, yt - ht - q * 0.04f, lw * 1.4f, a(sh[3], 0x80));
        line(tx - span * 0.3f, yt - ht - q * 0.04f, tx + span * 0.3f, yt - ht - q * 0.04f, lw * 1.4f, a(sh[3], 0x80));
        line(tx + span * 0.3f, yt - ht - q * 0.04f, tx + span * 0.68f, yt - ht - q * 0.3f, lw * 1.4f, a(sh[3], 0x80));
        // Pins et lanterne de pierre.
        pin(w * 0.955f, ground - hillH(0.955f, H0) + q * 0.2f, H0 * 0.2f, lw * 1.5f);
        pin(w * 0.8f, ground - hillH(0.8f, H0) + q * 0.2f, H0 * 0.12f, lw);
        pin(w * 0.03f, ground - hillH(0.03f, H0) + q * 0.2f, H0 * 0.34f, lw * 2.4f);
        pin(w * 0.105f, ground - hillH(0.105f, H0) + q * 0.2f, H0 * 0.16f, lw * 1.5f);
        float lx = w * 0.17f, ly = ground - hillH(0.17f, H0) + q * 0.2f;
        box(lx - q * 0.3f, ly - q * 0.5f, lx + q * 0.3f, ly, a(0xFF120607, 0xFA));
        box(lx - q * 0.2f, ly - q * 1.2f, lx + q * 0.2f, ly - q * 0.5f, a(0xFF120607, 0xFA));
        box(lx - q * 0.28f, ly - q * 1.6f, lx + q * 0.28f, ly - q * 1.2f, a(0xFF1A0909, 0xFA));
        box(lx - q * 0.1f, ly - q * 1.5f, lx + q * 0.1f, ly - q * 1.28f, a(sh[3], 0x60));
        poly(a(0xFF120607, 0xFA), lx - q * 0.5f, ly - q * 1.6f, lx, ly - q * 2f, lx + q * 0.5f, ly - q * 1.6f);
    }

    // ---- Sakura : printemps, rivière et pont

    private void arbre(float x, float y, float len, double ang, float wid, int depth, float[] ends, int[] cnt) {
        float ex = x + (float) Math.cos(ang) * len, ey = y + (float) Math.sin(ang) * len;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(wid);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(a(0xFF0B060B, 0xFA));
        c.drawLine(x, y, ex, ey, p);
        p.setStyle(Paint.Style.FILL);
        if (depth <= 0) {
            if (cnt[0] + 1 < ends.length / 2) {
                ends[cnt[0] * 2] = ex;
                ends[cnt[0] * 2 + 1] = ey;
                cnt[0]++;
            }
            return;
        }
        int kids = depth > 2 ? 2 : (rnd() < 0.5f ? 3 : 2);
        for (int i = 0; i < kids; i++) {
            double da = (i - (kids - 1) / 2.0) * (0.55 + 0.25 * rnd()) + (rnd() - 0.5) * 0.2;
            arbre(ex, ey, len * (0.62f + 0.14f * rnd()), ang + da, wid * 0.62f, depth - 1, ends, cnt);
        }
    }

    private void cerisier(float x, float y, float len, double ang, float wid, float q, float lum) {
        float[] ends = new float[120];
        int[] cnt = new int[1];
        arbre(x, y, len, ang, wid, 4, ends, cnt);
        for (int i = 0; i < cnt[0]; i++) {
            float ex = ends[i * 2], ey = ends[i * 2 + 1];
            for (int j = 0; j < 3; j++) {
                float r = q * (0.45f + 0.5f * rnd());
                disc(ex + (rnd() - 0.5f) * q * 1.2f, ey + (rnd() - 0.5f) * q * 1.0f, r, a(sh[2 + (int) (rnd() * 3)], Math.round((0x0A + 0x0E * rnd()) * lum)));
            }
            if (rnd() < 0.7f) flower(ex + (rnd() - 0.5f) * q * 1.4f, ey + (rnd() - 0.5f) * q * 1.2f, q * 0.3f, rnd() * 6.28, Math.round(0x58 * lum));
        }
    }

    /**
     * Nuit de printemps : lune douce, collines lointaines, rivière en écailles (seigaiha) qui reflète la lune,
     * pont en dos d'âne et cerisiers en fleurs aux bords. Le centre et la gauche restent calmes.
     */
    private void riviere(float w, float h) {
        float u = Math.max(w, h), H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f;
        float bank = h * 0.64f, lw = Math.max(1f, u / 1100f);
        float mx = w * 0.7f, my = Math.min(h * 0.2f, bank - H0 * 0.5f), mr = H0 * 0.09f;
        for (int i = 0; i < 30; i++) star(w * rnd(), bank * 0.6f * rnd(), u * (0.001f + 0.0012f * rnd()), 0x20 + Math.round(rnd() * 0x40));
        // Lune et traînées de nuages.
        disc(mx, my, mr * 2.2f, a(sh[2], 0x0A));
        disc(mx, my, mr * 1.5f, a(sh[2], 0x14));
        disc(mx, my, mr, a(sh[4], 0x58));
        disc(mx - mr * 0.25f, my - mr * 0.1f, mr * 0.7f, a(sh[4], 0x22));
        for (int i = 0; i < 3; i++) {
            float yy = my + mr * (-0.5f + 0.7f * i), x0 = mx - mr * (3f + i), x1 = mx + mr * (2.5f + i);
            poly(a(sh[2], 0x10), x0, yy, x1, yy - q * 0.1f, x1 - q, yy + q * 0.12f);
        }
        // Collines.
        int steps = 48;
        for (int layer = 0; layer < 2; layer++) {
            path.reset();
            path.moveTo(0, bank);
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                float hh = H0 * (layer == 0 ? 0.13f + 0.06f * (float) Math.sin(t * 6f + 2f) + 0.05f * t : 0.06f + 0.03f * (float) Math.sin(t * 10f));
                path.lineTo(w * t, bank - hh);
            }
            path.lineTo(w, bank);
            path.close();
            p.setColor(layer == 0 ? a(0xFF1D131C, 0xC8) : a(0xFF120C13, 0xF4));
            c.drawPath(path, p);
        }
        // Berge d'en face : lanternes lointaines.
        box(0, bank - q * 0.25f, w, bank + 1, a(0xFF0C070C, 0xFA));
        for (float x = q; x < w; x += q * (0.7f + rnd())) {
            if (rnd() < 0.5f * (1.2f - x / w)) continue;
            disc(x, bank - q * 0.12f, Math.max(1.2f, q * 0.06f), a(0xFFFFD9A0, Math.round(0x58 * (0.3f + 0.7f * x / w))));
        }
        // Rivière : eau sombre, reflet de la lune, écailles en perspective.
        vgrad(0, bank, w, h, a(0xFF100B16, 0xF6), a(0xFF06040A, 0xFA));
        for (int i = 0; i < 14; i++) {
            float t = i / 14f, y = bank + (h - bank) * (float) Math.pow(t, 1.4) + q * 0.3f, ww = mr * (1.5f - t * 0.7f);
            box(mx - ww, y, mx + ww, y + Math.max(1.5f, q * 0.07f), a(sh[4], Math.round(0x1A * (1f - t * 0.5f))));
        }
        float y = bank + q * 0.5f;
        int row = 0;
        while (y < h + q) {
            float t = (y - bank) / (h - bank), r = q * (0.8f + 1.8f * t);
            for (float x = -r + ((row & 1) == 0 ? 0 : r); x < w + r; x += 2 * r) {
                float k2 = 0.45f + 0.55f * Math.min(1f, x / w);
                arcLine(x, y, r, 180, 180, lw, a(sh[2], Math.round((0x10 + 0x18 * t) * k2)));
                arcLine(x, y, r * 0.66f, 180, 180, lw, a(sh[3], Math.round((0x0C + 0x12 * t) * k2)));
            }
            y += r * 0.5f;
            row++;
        }
        // Pont en dos d'âne : tablier voûté, rambarde, reflet en cercle.
        float cx = w * 0.78f, sp = Math.min(w * 0.17f, H0 * 0.3f), rise = H0 * 0.12f, th = q * 0.4f;
        int n = 28;
        path.reset();
        float[] tx = new float[n + 1], ty = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float u2 = -1f + 2f * i / n;
            tx[i] = cx + u2 * sp;
            ty[i] = bank - q * 0.2f - rise * (1f - u2 * u2);
            if (i == 0) path.moveTo(tx[i], ty[i]);
            else path.lineTo(tx[i], ty[i]);
        }
        for (int i = n; i >= 0; i--) path.lineTo(tx[i], ty[i] + th + (i > 3 && i < n - 3 ? 0f : q * 0.6f));
        path.close();
        p.setColor(a(0xFF0B070C, 0xFC));
        c.drawPath(path, p);
        for (int i = 0; i < n; i++) {
            line(tx[i], ty[i], tx[i + 1], ty[i + 1], lw * 1.4f, a(sh[3], 0x80));
            line(tx[i], ty[i] - q * 0.55f, tx[i + 1], ty[i + 1] - q * 0.55f, lw, a(sh[2], 0x4A));
            if ((i & 1) == 0) line(tx[i], ty[i], tx[i], ty[i] - q * 0.55f, lw, a(sh[2], 0x44));
        }
        arcLine(cx, bank - q * 0.2f, sp, 0, 180, lw * 1.2f, a(sh[2], 0x1C));
        // Cerisiers sur les berges.
        cerisier(w * 0.975f, bank + q * 0.1f, H0 * 0.15f, -Math.PI / 2 - 0.35, q * 0.45f, q, 1f);
        cerisier(w * 0.58f, bank + q * 0.1f, H0 * 0.07f, -Math.PI / 2 + 0.15, q * 0.25f, q * 0.8f, 0.8f);
        cerisier(w * 0.02f, bank + q * 0.1f, H0 * 0.14f, -Math.PI / 2 + 0.32, q * 0.42f, q, 0.9f);
        // Pétales portés par l'eau et par l'air.
        for (int i = 0; i < 16; i++) {
            float x = w * (0.2f + 0.8f * rnd()), yy = bank + (h - bank) * rnd();
            float s2 = q * (0.14f + 0.12f * rnd());
            kite(x, yy, s2 * 2f, s2 * 0.7f, rnd() * 6.28, 2, 4, 0x24 + Math.round(rnd() * 0x18), false);
        }
    }

    // ------------------------------------------------------------ Façades de manette assorties aux fonds « nuit » (variante 4)

    /**
     * Codec, manette de la base : l'écran radar vu de la console. Anneaux en seize pans et rayons qui se lèvent
     * du bas (comme le soleil d'Ambre), faisceau pâle, échos, règle de fréquence en haut, onde radio sur le
     * quadrillage du sol, étoiles et lignes de balayage. Tout au trait, rien de plein sous les touches.
     */
    private void basePad(float w, float h) {
        float u = Math.max(w, h), m = Math.min(w, h), lw = Math.max(1f, u / 900f);
        float cx = w * 0.5f, cy = h * 1.04f;
        // Lueur verte de vision nocturne qui monte du radar.
        p.setShader(new android.graphics.RadialGradient(cx, cy, m * 1.1f, a(sh[2], 0x26), a(sh[2], 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        for (int i = 0; i < 26; i++) star(w * rnd(), h * 0.55f * rnd(), u * (0.0012f + 0.0012f * rnd()), 0x18 + Math.round(rnd() * 0x26));
        // Anneaux à seize pans et rayons.
        for (int ring = 1; ring <= 6; ring++) {
            float r = m * 0.17f * ring;
            for (int s2 = 0; s2 < 16; s2++) {
                double t0 = Math.PI * 2 * s2 / 16, t1 = t0 + Math.PI / 8;
                line(cx + (float) Math.cos(t0) * r, cy + (float) Math.sin(t0) * r,
                        cx + (float) Math.cos(t1) * r, cy + (float) Math.sin(t1) * r, lw, a(sh[3], 0x40 - ring * 5));
            }
        }
        for (int s2 = 0; s2 < 16; s2++) {
            double t = Math.PI * 2 * s2 / 16;
            line(cx, cy, cx + (float) Math.cos(t) * u, cy + (float) Math.sin(t) * u, lw, a(sh[2], 0x14));
        }
        // Faisceau : éventail pâle qui s'éteint en tournant, arête au ton du pli.
        float R = m * 0.86f;
        double lead = -Math.PI * 0.42;
        for (int b = 0; b < 10; b++) {
            double t0 = lead - b * 0.05, t1 = t0 - 0.05;
            tri(cx, cy, cx + (float) Math.cos(t0) * R, cy + (float) Math.sin(t0) * R,
                    cx + (float) Math.cos(t1) * R, cy + (float) Math.sin(t1) * R, a(sh[(b & 1) == 0 ? 3 : 2], Math.round(0x10 * (1f - b / 10f))));
        }
        line(cx, cy, cx + (float) Math.cos(lead) * R, cy + (float) Math.sin(lead) * R, lw * 1.2f, a(sh[4], 0x34));
        for (int i = 0; i < 5; i++) {
            double t = -Math.PI * (0.44 + 0.22 * rnd());
            float d = m * (0.3f + 0.6f * rnd()), s = m * 0.012f;
            float ex = cx + (float) Math.cos(t) * d, ey = cy + (float) Math.sin(t) * d;
            kite(ex, ey + s, s * 2, s * 0.9f, -Math.PI / 2, 2, 4, 0x48, false);
        }
        // Règle de fréquence le long du bord haut, plus nette au milieu.
        float step = m * 0.03f;
        for (int i = 0; i * step < w; i++) {
            float x = i * step, fade = 1f - Math.abs(x / w - 0.5f) * 1.6f;
            if (fade <= 0) continue;
            line(x, 0, x, i % 5 == 0 ? m * 0.045f : m * 0.022f, lw, a(sh[3], Math.round(0x38 * fade)));
        }
        // Sol : horizon, quadrillage en perspective et onde radio, au pied de la façade.
        float ground = h * 0.84f;
        line(0, ground, w, ground, lw, a(sh[3], 0x30));
        for (int i = 1; i <= 4; i++) {
            float y = ground + (h - ground) * (float) Math.pow(i / 4.0, 1.6);
            line(0, y, w, y, lw, a(sh[2], 0x12));
        }
        for (int j = -10; j <= 10; j++) line(cx + j * m * 0.05f, ground, cx + j * w * 0.12f, h, lw, a(sh[2], 0x0E));
        float wy = ground + (h - ground) * 0.45f, px = 0, py = wy, q = m * 0.05f;
        for (int i = 1; i <= 160; i++) {
            float x = w * i / 160f, t = Math.abs(x / w - 0.5f) * 2f;
            float amp = q * 0.7f * (0.15f + 0.85f * (1f - t) * (1f - t));
            float y = wy + amp * ((float) Math.sin(x / q * 0.9f) * 0.75f + (float) Math.sin(x / q * 2.3f + 1f) * 0.3f);
            line(px, py, x, y, lw * 1.2f, a(sh[3], Math.round(0x14 + 0x2C * (1f - t))));
            px = x;
            py = y;
        }
        scan(w, h);
    }

    /**
     * Arctique, manette de la nuit polaire : le ciel en pose longue. Les étoiles tournent autour de la Polaire
     * (arcs concentriques de longueurs variées, glacés ou teintés d'aurore), un voile d'aurore passe derrière,
     * et une ligne basse de crêtes de glace facettées ferme le bas. Fond sombre et uni sous les touches.
     */
    private void polairePad(float w, float h) {
        float u = Math.max(w, h), m = Math.min(w, h), lw = Math.max(1f, u / 900f);
        float cx = w * 0.5f, cy = h * 0.46f;
        // Voile d'aurore en colonnes, qui ondule en travers de la façade.
        int[] cols = {sh[2], 0xFF6CFFC0};
        int n = 90;
        for (int j = 0; j < 2; j++) {
            float base = h * (0.34f + 0.1f * j), amp = h * 0.07f, hb = h * (0.22f - 0.06f * j);
            for (int i = 0; i < n; i++) {
                float x0 = Math.round(w * i / n), x1 = Math.round(w * (i + 1) / n), t = (x0 + x1) / 2 / w;
                float y = base + (float) Math.sin(t * Math.PI * (1.4f + 0.6f * j) + j * 2.1) * amp;
                int al = Math.round((0x2A - 0x0A * j) * (0.3f + 0.7f * (float) Math.sin(Math.PI * t)));
                vgrad(x0, y - hb, x1, y, a(cols[j], 0), a(cols[j], al));
                vgrad(x0, y, x1, y + hb * 0.3f, a(cols[j], al), a(cols[j], 0));
            }
        }
        // Traînées d'étoiles : un arc par étoile, plus long et plus pâle loin du pôle.
        float rmax = (float) Math.hypot(w * 0.5f, h * 0.6f);
        int trails = 110;
        for (int i = 0; i < trails; i++) {
            float r = m * 0.05f + (rmax - m * 0.05f) * (float) Math.sqrt(rnd());
            float start = rnd() * 360, sweep = 18 + 40 * rnd();
            int tone = rnd() < 0.2f ? 0xFF6CFFC0 : (rnd() < 0.5f ? sh[4] : sh[3]);
            arcLine(cx, cy, r, start, sweep, lw * (rnd() < 0.15f ? 1.6f : 1f), a(tone, 0x1C + Math.round(rnd() * 0x2A)));
        }
        // La Polaire : petite étoile à quatre branches, halo.
        disc(cx, cy, m * 0.03f, a(sh[3], 0x18));
        float s = m * 0.012f;
        poly(a(sh[4], 0x90), cx, cy - s * 3, cx + s * 0.6f, cy, cx, cy + s * 3, cx - s * 0.6f, cy);
        poly(a(sh[4], 0x90), cx - s * 3, cy, cx, cy - s * 0.6f, cx + s * 3, cy, cx, cy + s * 0.6f);
        line(cx, cy - s * 3, cx, cy + s * 3, lw, a(fold, 0x80));
        // Crêtes de glace au pied : pics bas pliés en deux faces, arête de lumière.
        float ground = h * 0.97f;
        int pk = Math.max(8, Math.round(w / (m * 0.12f)));
        float hw = w / (pk - 1);
        for (int i = 0; i < pk; i++) {
            float xi = w * i / (pk - 1), edge = 0.5f + 0.5f * Math.abs(xi / w - 0.5f) * 2f;
            float hi = m * (0.04f + 0.08f * rnd()) * edge;
            poly(a(0xFF0A131E, 0xE8), xi - hw, ground, xi, ground - hi, xi, ground);
            poly(a(0xFF122336, 0xE8), xi, ground - hi, xi + hw, ground, xi, ground);
            line(xi, ground - hi, xi + hw, ground, lw, a(fold, 0x30));
        }
        box(0, ground, w, h, a(0xFF0A131E, 0xE8));
        line(0, ground, w, ground, lw, a(fold, 0x28));
    }

    /**
     * Écarlate, manette de la nuit japonaise : la lune rouge en facettes se lève du bas, derrière un petit
     * torii ; nervures d'éventail, nuages en écailles dorées sur les côtés, ligne de collines au pied,
     * étoiles. Le haut au centre (bouton origami) et les côtés bas (croix, boutons) restent calmes.
     */
    private void japonPad(float w, float h) {
        float u = Math.max(w, h), m = Math.min(w, h), lw = Math.max(1f, u / 900f);
        float mx = w * 0.5f, mr = m * 0.36f, my = h - m * 0.1f;
        p.setShader(new android.graphics.RadialGradient(mx, my, m * 1.0f, a(sh[2], 0x24), a(sh[2], 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        for (int i = 0; i < 22; i++) star(w * rnd(), h * 0.5f * rnd(), u * (0.0012f + 0.0012f * rnd()), 0x20 + Math.round(rnd() * 0x30));
        // Nervures d'éventail.
        for (int i = 0; i < 30; i++) {
            double ang = Math.PI * 2 * i / 30;
            line(mx + (float) Math.cos(ang) * mr * 1.3f, my + (float) Math.sin(ang) * mr * 1.3f,
                    mx + (float) Math.cos(ang) * u, my + (float) Math.sin(ang) * u, lw, a(fold, 0x10));
        }
        // Nuages en écailles : rangées d'arcs dorés, plus présents vers les bords.
        float q = m * 0.07f;
        for (int j = 0; j < 4; j++) {
            float r = q * (1f + 0.2f * j), yy = h * (0.2f + 0.12f * j);
            for (float x = (j & 1) * r - r; x < w + r; x += r * 2) {
                float edge = Math.min(1f, Math.abs(x / w - 0.5f) * 2.4f);
                if (edge < 0.15f) continue;
                arcLine(x, yy, r, 180, 180, lw, a(fold, Math.round(0x22 * edge)));
                arcLine(x, yy, r * 0.66f, 180, 180, lw, a(fold, Math.round(0x14 * edge)));
            }
        }
        // Lune : halos et disque à vingt-quatre facettes, en partie sous le bord bas.
        disc(mx, my, mr * 1.5f, a(sh[2], 0x0E));
        disc(mx, my, mr * 1.22f, a(sh[2], 0x14));
        int fc = 24;
        for (int i = 0; i < fc; i++) {
            double t0 = Math.PI * 2 * i / fc, t1 = Math.PI * 2 * (i + 1) / fc;
            boolean left = Math.cos((t0 + t1) / 2) < 0;
            tri(mx, my, mx + (float) Math.cos(t0) * mr, my + (float) Math.sin(t0) * mr,
                    mx + (float) Math.cos(t1) * mr, my + (float) Math.sin(t1) * mr, a(sh[left ? 1 : 2], (i & 1) == 0 ? 0x40 : 0x34));
        }
        arcLine(mx, my, mr, 0, 360, lw * 1.4f, a(fold, 0x40));
        // Torii devant la lune.
        float gy = h - m * 0.03f, ht = mr * 0.95f, span = mr * 0.95f, tq = mr * 0.07f;
        for (int sd = -1; sd <= 1; sd += 2) {
            float xl = mx + sd * span * 0.5f;
            poly(0xF016080A, xl - tq, gy, xl + tq, gy, xl + tq * 0.75f, gy - ht, xl - tq * 0.75f, gy - ht);
            line(xl + tq * 0.9f, gy, xl + tq * 0.7f, gy - ht, lw, a(sh[3], 0x60));
        }
        float by = gy - ht * 0.74f;
        poly(0xF016080A, mx - span * 0.62f, by, mx + span * 0.62f, by, mx + span * 0.62f, by + tq * 1.1f, mx - span * 0.62f, by + tq * 1.1f);
        float ty = gy - ht;
        poly(0xF416080A, mx - span * 0.8f, ty - tq * 2f, mx - span * 0.35f, ty - tq * 0.3f, mx + span * 0.35f, ty - tq * 0.3f,
                mx + span * 0.8f, ty - tq * 2f, mx + span * 0.6f, ty + tq * 1.1f, mx - span * 0.6f, ty + tq * 1.1f);
        line(mx - span * 0.8f, ty - tq * 2f, mx - span * 0.35f, ty - tq * 0.3f, lw * 1.3f, a(sh[3], 0x70));
        line(mx - span * 0.35f, ty - tq * 0.3f, mx + span * 0.35f, ty - tq * 0.3f, lw * 1.3f, a(sh[3], 0x70));
        line(mx + span * 0.35f, ty - tq * 0.3f, mx + span * 0.8f, ty - tq * 2f, lw * 1.3f, a(sh[3], 0x70));
        // Collines au pied : courbe sombre et son liseré.
        int steps = 48;
        path.reset();
        path.moveTo(0, h);
        float[] hy = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float g = (t - 0.5f) / 0.3f;
            hy[i] = h - m * (0.1f + 0.04f * (float) Math.sin(t * 8f + 0.5f) + 0.1f * (1f - (float) Math.exp(-g * g)));
            path.lineTo(w * t, hy[i]);
        }
        path.lineTo(w, h);
        path.close();
        p.setColor(a(0xFF0A0506, 0xE0));
        c.drawPath(path, p);
        for (int i = 0; i < steps; i++) line(w * i / steps, hy[i], w * (i + 1) / steps, hy[i + 1], lw * 1.2f, a(sh[3], 0x38));
    }

    /**
     * Sakura, manette de la rivière : l'eau vue de près, en écailles seigaiha au trait. Au milieu, le pont en
     * dos d'âne et son reflet ferment un cercle (pont de lune), la lune pâle brille dedans et se reflète en
     * traits ; des nuées de cerisier pendent du bord haut et des pétales dérivent. Rien de plein sous les touches.
     */
    private void rivierePad(float w, float h) {
        float u = Math.max(w, h), m = Math.min(w, h), lw = Math.max(1f, u / 900f);
        float cx = w * 0.5f;
        vgrad(0, 0, w, h, a(0xFF100B16, 0x00), a(0xFF06040A, 0x70));
        // Écailles d'eau, un peu plus grandes vers le bas.
        float y = m * 0.1f;
        int row = 0;
        while (y < h + m * 0.2f) {
            float t = y / h, r = m * (0.09f + 0.05f * t);
            for (float x = -r + ((row & 1) == 0 ? 0 : r); x < w + r; x += 2 * r) {
                arcLine(x, y, r, 180, 180, lw, a(sh[2], Math.round(0x12 + 0x10 * t)));
                arcLine(x, y, r * 0.66f, 180, 180, lw, a(sh[3], Math.round(0x0C + 0x0A * t)));
            }
            y += r * 0.5f;
            row++;
        }
        // Pont de lune : l'arche (tablier sombre, liseré, rambarde à barreaux) et son reflet en tirets.
        float by = h * 0.6f, R = m * 0.3f, th = m * 0.03f;
        disc(cx, by, R * 0.98f, a(0xFF06040A, 0x2C));
        // La lune dans le cercle et son reflet en traits.
        float mr = R * 0.32f, my = by - R * 0.45f;
        disc(cx, my, mr * 1.8f, a(sh[2], 0x10));
        disc(cx, my, mr, a(sh[4], 0x50));
        disc(cx - mr * 0.25f, my - mr * 0.1f, mr * 0.7f, a(sh[4], 0x20));
        for (int i = 0; i < 9; i++) {
            float t = i / 8f, yy = by + R * (0.12f + 0.75f * t), ww = mr * (1.1f - 0.6f * t) * (0.8f + 0.4f * rnd());
            box(cx - ww, yy, cx + ww, yy + Math.max(1.5f, m * 0.007f), a(sh[4], Math.round(0x30 * (1f - 0.6f * t))));
        }
        int n = 36;
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI + Math.PI * i / n, t1 = Math.PI + Math.PI * (i + 1) / n;
            float x0 = cx + (float) Math.cos(t0) * R, y0 = by + (float) Math.sin(t0) * R;
            float x1 = cx + (float) Math.cos(t1) * R, y1 = by + (float) Math.sin(t1) * R;
            float ix0 = cx + (float) Math.cos(t0) * (R - th), iy0 = by + (float) Math.sin(t0) * (R - th);
            float ix1 = cx + (float) Math.cos(t1) * (R - th), iy1 = by + (float) Math.sin(t1) * (R - th);
            poly(a(0xFF0B070C, 0xE0), x0, y0, x1, y1, ix1, iy1, ix0, iy0);
            line(x0, y0, x1, y1, lw * 1.4f, a(sh[3], 0x70));
            float rr = R + m * 0.05f;
            line(cx + (float) Math.cos(t0) * rr, by + (float) Math.sin(t0) * rr, cx + (float) Math.cos(t1) * rr, by + (float) Math.sin(t1) * rr, lw, a(sh[2], 0x48));
            if ((i & 1) == 0) line(x0, y0, cx + (float) Math.cos(t0) * rr, by + (float) Math.sin(t0) * rr, lw, a(sh[2], 0x40));
            // Reflet : moitié basse en tirets qui tremblent.
            if ((i & 1) == 0) {
                float d = (rnd() - 0.5f) * m * 0.01f;
                line(x0 + d, by * 2 - y0, x1 + d, by * 2 - y1, lw * 1.2f, a(sh[3], 0x30));
            }
        }
        line(cx - R - m * 0.08f, by, cx + R + m * 0.08f, by, lw, a(sh[3], 0x28));
        // Nuées de cerisier qui pendent du bord haut, de part et d'autre du bouton origami.
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 14; i++) {
                float t = rnd(), x = w * (side == 0 ? 0.1f + 0.28f * t : 0.62f + 0.28f * t);
                float yy = m * (0.02f + 0.13f * rnd() * (float) Math.sin(Math.PI * t));
                disc(x, yy, m * (0.035f + 0.04f * rnd()), a(sh[2 + (int) (rnd() * 3)], Math.round(0x0A + 0x0C * rnd())));
            }
            for (int i = 0; i < 5; i++) {
                float t = 0.15f + 0.7f * rnd(), x = w * (side == 0 ? 0.1f + 0.28f * t : 0.62f + 0.28f * t);
                flower(x, m * (0.03f + 0.11f * rnd() * (float) Math.sin(Math.PI * t)), m * (0.024f + 0.016f * rnd()), rnd() * 6.28, 0x34 + Math.round(rnd() * 0x14));
            }
        }
        // Pétales portés par l'eau.
        for (int i = 0; i < 18; i++) {
            float x = w * rnd(), yy = h * (0.25f + 0.75f * rnd());
            float s2 = m * (0.012f + 0.01f * rnd());
            kite(x, yy, s2 * 2f, s2 * 0.7f, rnd() * 6.28, 2, 4, 0x26 + Math.round(rnd() * 0x18), false);
        }
    }
}
