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
    private final RectF rc = new RectF();
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

    /** Fond d'écran d'Ambre (réserve) : la ville Art déco la nuit, gardée derrière ThemeBackdrop.AMBRE_VILLE_DECO. */
    static final int AMBRE_FOND = 3;

    /** Ambre : 0 trace d'oscilloscope pliée, 1 soleil Art déco en gradins, 2 cascade de curseurs, 3 ville Art déco. */
    static void ambre(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 47);
        if (variant == 1) f.deco(w, h);
        else if (variant == 2) f.cursors(w, h);
        else if (variant == 3) f.ville(w, h);
        else if (variant == AMBRE_TOKYO) f.tokyo(w, h);
        else if (variant == MANETTE) f.megapolePad(w, h);
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

    // ---- Ambre : métropole japonaise la nuit (fond actif, la ville Art déco reste en variante 3)

    /** Variante 5 d'Ambre : Tokyo la nuit, tour en treillis rouge et blanc, gratte-ciel de verre, mont Fuji lointain. */
    static final int AMBRE_TOKYO = 5;

    private static float hash(int i, int k) {
        int x = i * 374761393 + k * 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        return ((x ^ (x >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float bump(float x, float c0, float wd) {
        float g = (x - c0) / wd;
        return (float) Math.exp(-g * g);
    }

    /**
     * Immeubles de la métropole, partagés avec BackdropMotion (balises, fenêtres, projecteurs) :
     * layer 1 = premier plan, layer 0 = lointain ; chaque ligne {x0, largeur, hauteur, éclat}.
     * Les tours hautes sont aux bords et à droite ; le centre et la gauche restent bas.
     */
    static float[][] tokyoBlocks(float w, float h, int layer) {
        float H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f;
        java.util.ArrayList<float[]> out = new java.util.ArrayList<float[]>();
        float bx = -q * 0.5f;
        int i = 0;
        while (bx < w + q && i < 400) {
            float bw = layer == 1 ? q * (1.3f + 1.5f * hash(i, 1)) : q * (1.0f + 1.4f * hash(i + 500, 1));
            float fx = (bx + bw / 2) / w;
            float hf;
            if (layer == 1) {
                hf = 0.05f + 0.1f * hash(i, 3) + 0.30f * bump(fx, 0.03f, 0.08f) + 0.42f * bump(fx, 0.93f, 0.10f) + 0.16f * bump(fx, 0.84f, 0.06f)
                        + 0.1f * bump(fx, 0.52f, 0.05f) * hash(i, 4);
                if (fx > 0.6f && fx < 0.8f) hf = Math.min(hf, 0.15f);
            } else {
                hf = 0.07f + 0.15f * hash(i + 500, 3) + 0.14f * bump(fx, 0.12f, 0.12f) + 0.2f * bump(fx, 0.88f, 0.14f);
            }
            float lum = 0.35f + 0.65f * Math.min(1f, Math.abs(fx - 0.45f) * 2.2f);
            out.add(new float[] {bx, bw, H0 * hf, lum});
            bx += layer == 1 ? bw + q * (0.12f + 0.35f * hash(i, 2)) : bw * (0.7f + 0.3f * hash(i + 500, 2));
            i++;
        }
        return out.toArray(new float[out.size()][]);
    }

    /** Toit des tours les plus hautes à gauche puis à droite : {xg, yg, xd, yd}, pour les projecteurs. */
    static float[] tokyoRoofs(float w, float h) {
        float[][] b = tokyoBlocks(w, h, 1);
        float ground = h * 0.9f;
        float[] best = {0, ground, 0, ground};
        for (int i = 0; i < b.length; i++) {
            float cx = b[i][0] + b[i][1] / 2, top = ground - b[i][2];
            int s = cx < w * 0.5f ? 0 : 2;
            if (top < best[s + 1]) {
                best[s] = cx;
                best[s + 1] = top;
            }
        }
        return best;
    }

    /** Tour de Tokyo : {x, demi-largeur du pied, demi-largeur du haut du corps, hauteur du corps, y du sommet, y du sol}. */
    static float[] tokyoTower(float w, float h) {
        float H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f, ground = h * 0.9f, ht = H0 * 0.74f;
        return new float[] {w * 0.7f, q * 2.3f, q * 0.36f, ht * 0.72f, ground - ht, ground};
    }

    static float towerHalf(float[] g, float s) {
        return g[2] + (g[1] - g[2]) * (float) Math.pow(1f - s, 1.7);
    }

    /** Immeuble de verre à toit plat : deux faces, reflet en biais, fenêtres allumées, liserés d'ambre, matériel de toit. */
    private void glass(float x0, float base, float bw, float bh, float lum, boolean near, float q, int depth) {
        float lw = Math.max(1f, q / 22f), top = base - bh, x1 = x0 + bw, mid = x0 + bw * 0.5f;
        int dl = near ? 0xFF0B0805 : 0xFF1E160D, dr = near ? 0xFF18120B : 0xFF2A1F12, al = near ? 0xF4 : 0xB4;
        box(x0, top, mid, base + 1, a(dl, al));
        box(mid, top, x1, base + 1, a(dr, al));
        poly(a(sh[3], Math.round((near ? 0x16 : 0x0C) * lum)), x1 - bw * 0.05f, top, x1 - bw * 0.05f, top + bh * 0.3f, mid + bw * 0.05f, top + bh * 0.64f, mid + bw * 0.05f, top + bh * 0.46f);
        float cw = q * (near ? 0.2f : 0.3f), ch = q * (near ? 0.3f : 0.44f);
        int cols = Math.max(2, Math.round(bw / cw));
        float cwa = bw / cols;
        int row = 0;
        for (float wy = top + ch * 0.7f; wy < base - ch * 0.5f; wy += ch, row++) {
            if (near && (row & 3) == 3) line(x0, wy - ch * 0.3f, x1, wy - ch * 0.3f, lw, a(fold, Math.round(0x0E * lum)));
            for (int k2 = 0; k2 < cols; k2++) {
                if (rnd() > (0.3f * lum + 0.05f) * (near ? 1f : 0.7f)) continue;
                int col = rnd() < 0.14f ? 0xFFFFF0D0 : sh[rnd() < 0.3f ? 4 : 3];
                box(x0 + k2 * cwa + cwa * 0.2f, wy, x0 + k2 * cwa + cwa * 0.8f, wy + ch * 0.5f, a(col, Math.round((near ? 0x38 + 0x58 * rnd() : 0x1C + 0x1C * rnd()) * lum)));
            }
        }
        line(x1, top, x1, base, lw * 1.4f, a(sh[3], Math.round((near ? 0x70 : 0x30) * lum)));
        line(x0, top, x1, top, lw * 1.2f, a(fold, Math.round((near ? 0x5A : 0x28) * lum)));
        if (near) {
            float gx = x0 + bw * (0.12f + 0.4f * rnd()), gh = q * 0.26f;
            box(gx, top - gh, gx + bw * 0.28f, top, a(dl, 0xF8));
            float ax = x0 + bw * (0.6f + 0.3f * rnd()), al2 = q * (0.5f + 1.1f * rnd());
            line(ax, top, ax, top - al2, lw * 1.2f, a(0xFF0B0805, 0xF8));
            disc(ax, top - al2, Math.max(1.2f, lw * 1.4f), a(0xFFFF3B3B, Math.round(0x80 * Math.min(1f, lum + 0.3f))));
            if (depth == 0 && bh > q * 7f) glass(x0 + bw * 0.14f, top, bw * 0.72f, bh * 0.1f, lum, true, q, 1);
        }
    }

    /** Enseigne verticale sans texte : caisson sombre, liseré lumineux, barres et pastilles de couleur empilées. */
    private void enseigne(float x, float y0, float wd, float ht, int style, float q) {
        float lw = Math.max(1f, q / 22f);
        int[] cols = {sh[3], 0xFFE0452B, fold, 0xFFFFE2A0, sh[4]};
        int main = cols[style % cols.length];
        glow(x + wd / 2, y0 + ht / 2, ht * 0.7f, main, 0x16);
        box(x, y0, x + wd, y0 + ht, a(0xFF0A0705, 0xF2));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 1.3f);
        p.setColor(a(main, 0xA0));
        c.drawRect(x, y0, x + wd, y0 + ht, p);
        p.setStyle(Paint.Style.FILL);
        float y = y0 + wd * 0.2f;
        int n = 0;
        while (y < y0 + ht - wd * 0.9f) {
            int col = cols[(style + n) % cols.length];
            float seg = wd * (0.5f + 0.7f * hash(style * 13 + n, 5));
            if (n % 3 == 2) {
                disc(x + wd / 2, y + wd * 0.3f, wd * 0.26f, a(col, 0xC8));
                y += wd * 0.75f;
            } else {
                box(x + wd * 0.2f, y, x + wd * 0.8f, y + seg * 0.5f, a(col, 0xB0));
                box(x + wd * 0.2f, y + seg * 0.5f + wd * 0.1f, x + wd * (0.3f + 0.4f * hash(style + n, 6)), y + seg * 0.5f + wd * 0.2f, a(col, 0x80));
                y += seg * 0.5f + wd * 0.42f;
            }
            n++;
        }
    }

    /** Poteau électrique : fût, deux traverses, isolateurs, petit transformateur. Retourne rien ; les fils sont tirés ailleurs. */
    private void poteau(float x, float base, float ht, float lw, boolean transfo) {
        float ww = lw * 2.6f;
        box(x - ww / 2, base - ht, x + ww / 2, base, a(0xFF050302, 0xF8));
        line(x - ht * 0.16f, base - ht * 0.94f, x + ht * 0.16f, base - ht * 0.94f, lw * 1.6f, a(0xFF050302, 0xF8));
        line(x - ht * 0.12f, base - ht * 0.82f, x + ht * 0.12f, base - ht * 0.82f, lw * 1.4f, a(0xFF050302, 0xF8));
        for (int i = -1; i <= 1; i += 2) {
            disc(x + i * ht * 0.15f, base - ht * 0.95f, lw * 1.2f, a(sh[3], 0x60));
            disc(x + i * ht * 0.11f, base - ht * 0.83f, lw * 1.1f, a(sh[3], 0x48));
        }
        line(x + ww * 0.45f, base - ht, x + ww * 0.45f, base, lw * 0.9f, a(sh[3], 0x28));
        if (transfo) {
            box(x + ww * 0.6f, base - ht * 0.74f, x + ww * 0.6f + ht * 0.07f, base - ht * 0.6f, a(0xFF080503, 0xF8));
            line(x + ww * 0.6f, base - ht * 0.74f, x + ww * 0.6f + ht * 0.07f, base - ht * 0.74f, lw, a(sh[3], 0x40));
        }
    }

    /** Fil entre deux points, en chaînette. */
    private void fil(float x0, float y0, float x1, float y1, float sag, float lw) {
        path.reset();
        path.moveTo(x0, y0);
        path.quadTo((x0 + x1) / 2, (y0 + y1) / 2 + sag * 2f, x1, y1);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw);
        p.setColor(a(0xFF050302, 0xD8));
        c.drawPath(path, p);
        p.setStrokeWidth(lw * 0.6f);
        p.setColor(a(sh[3], 0x1C));
        c.save();
        c.translate(0, -lw * 1.1f);
        c.drawPath(path, p);
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    /** Nuage en lentille facettée : moitié haute éclairée par la ville, moitié basse dans l'ombre. */
    private void nuage(float x0, float y, float len, float hc, int alpha) {
        poly(a(0xFF4A3520, alpha), x0, y, x0 + len * 0.28f, y - hc * 0.7f, x0 + len * 0.7f, y - hc * 0.5f, x0 + len, y);
        poly(a(0xFF1C130A, alpha), x0, y, x0 + len, y, x0 + len * 0.62f, y + hc * 0.45f, x0 + len * 0.26f, y + hc * 0.4f);
        poly(a(sh[2], alpha / 3), x0 + len * 0.28f, y - hc * 0.7f, x0 + len * 0.5f, y - hc * 0.6f, x0 + len * 0.42f, y - hc * 0.1f, x0 + len * 0.12f, y - hc * 0.05f);
    }

    /** Point de la courbe de Fuji (quadratique) : côté -1 gauche, +1 droit, t de 0 (pied) à 1 (sommet). */
    private static float[] fuji(float fx, float ground, float wd, float peakY, int side, float t) {
        float x0 = fx + side * wd, y0 = ground, cx = fx + side * wd * 0.5f, cy = ground - (ground - peakY) * 0.1f;
        float u = 1f - t;
        return new float[] {u * u * x0 + 2 * u * t * cx + t * t * fx, u * u * y0 + 2 * u * t * cy + t * t * peakY};
    }

    /**
     * Métropole japonaise la nuit : mont Fuji lointain et ses nuages, deux plans de gratte-ciel de verre à toit plat
     * (fenêtres, reflets, liserés d'ambre, balises), tour de télécommunication en treillis rouge et blanc,
     * passerelles entre les tours, enseignes verticales sans texte, poteaux et fils au premier plan, quai et feux de voitures.
     * Les tours hautes sont aux bords et à droite ; le centre et la gauche, où se posent les listes, restent bas.
     */
    private void tokyo(float w, float h) {
        float u = Math.max(w, h), H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f;
        float ground = h * 0.9f, lw = Math.max(1f, u / 1100f);
        for (int i = 0; i < 26; i++) star(w * rnd(), ground * 0.45f * rnd(), u * (0.001f + 0.0012f * rnd()), 0x18 + Math.round(rnd() * 0x30));
        // Nuages hauts.
        nuage(w * 0.04f, h * 0.2f, w * 0.26f, q * 0.8f, 0x48);
        nuage(w * 0.34f, h * 0.1f, w * 0.2f, q * 0.6f, 0x3C);
        nuage(w * 0.5f, h * 0.3f, w * 0.24f, q * 0.7f, 0x44);
        // Mont Fuji : flancs concaves, neige facettée, très pâle dans la brume.
        float fx = w * 0.3f, fw = Math.min(w * 0.42f, H0 * 0.95f), peak = ground - H0 * 0.4f;
        path.reset();
        path.moveTo(fx - fw, ground);
        for (int i = 1; i <= 16; i++) {
            float[] pt = fuji(fx, ground, fw, peak, -1, i / 16f);
            path.lineTo(pt[0], pt[1]);
        }
        for (int i = 15; i >= 0; i--) {
            float[] pt = fuji(fx, ground, fw, peak, 1, i / 16f);
            path.lineTo(pt[0], pt[1]);
        }
        path.close();
        p.setColor(a(0xFF2A2016, 0xB8));
        c.drawPath(path, p);
        path.reset();
        path.moveTo(fx, peak);
        for (int i = 15; i >= 0; i--) {
            float[] pt = fuji(fx, ground, fw, peak, 1, i / 16f);
            path.lineTo(pt[0], pt[1]);
        }
        path.lineTo(fx, ground);
        path.close();
        p.setColor(a(0xFF3A2C1C, 0x70));
        c.drawPath(path, p);
        float[] cl = fuji(fx, ground, fw, peak, -1, 0.86f), cr = fuji(fx, ground, fw, peak, 1, 0.86f);
        float yc = cl[1];
        poly(a(0xFFC8A878, 0x24), fx, peak, cl[0], cl[1], cl[0] + (fx - cl[0]) * 0.25f, yc + H0 * 0.035f, fx - (fx - cl[0]) * 0.35f, yc - H0 * 0.01f,
                fx - (fx - cl[0]) * 0.05f, yc + H0 * 0.05f, fx + (cr[0] - fx) * 0.18f, yc + H0 * 0.0f, fx + (cr[0] - fx) * 0.45f, yc + H0 * 0.04f, cr[0], cr[1]);
        poly(a(0xFFE8CC9A, 0x24), fx, peak, cr[0], cr[1], fx + (cr[0] - fx) * 0.45f, yc + H0 * 0.04f, fx + (cr[0] - fx) * 0.18f, yc + H0 * 0.0f, fx, yc + H0 * 0.05f);
        line(fx, peak, cr[0], cr[1], lw, a(sh[3], 0x28));
        // Nuage en travers de la pente.
        nuage(fx - fw * 0.6f, peak + H0 * 0.21f, fw * 1.2f, q * 0.5f, 0x44);
        // Ville lointaine, dans la brume.
        float[][] far = tokyoBlocks(w, h, 0);
        for (int i = 0; i < far.length; i++) glass(far[i][0], ground, far[i][1], far[i][2], far[i][3], false, q, 1);
        vgrad(0, ground - H0 * 0.3f, w, ground, a(sh[3], 0), a(sh[3], 0x30));
        // Tour de télécommunication : corps en treillis rouge et blanc, deux plates-formes, antenne rayée.
        float[] tg = tokyoTower(w, h);
        float tcx = tg[0], bodyH = tg[3];
        int red = 0xFFD8432E, white = 0xFFFFE6C0;
        path.reset();
        path.moveTo(tcx - tg[1], ground);
        for (int i = 1; i <= 20; i++) path.lineTo(tcx - towerHalf(tg, i / 20f), ground - bodyH * i / 20f);
        for (int i = 20; i >= 0; i--) path.lineTo(tcx + towerHalf(tg, i / 20f), ground - bodyH * i / 20f);
        path.close();
        p.setColor(a(0xFF100B07, 0xDC));
        c.drawPath(path, p);
        int nb = 14;
        for (int k2 = 0; k2 < nb; k2++) {
            float s0 = k2 / (float) nb, s1 = (k2 + 1) / (float) nb;
            float y0 = ground - bodyH * s0, y1 = ground - bodyH * s1, h0 = towerHalf(tg, s0), h1 = towerHalf(tg, s1);
            boolean isRed = ((k2 / 2) & 1) == 0;
            int col = isRed ? red : white;
            poly(a(col, isRed ? 0x2C : 0x1C), tcx - h0, y0, tcx + h0, y0, tcx + h1, y1, tcx - h1, y1);
            line(tcx - h0, y0, tcx + h1, y1, lw * 1.1f, a(col, isRed ? 0x88 : 0x70));
            line(tcx + h0, y0, tcx - h1, y1, lw * 1.1f, a(col, isRed ? 0x88 : 0x70));
            line(tcx - h1, y1, tcx + h1, y1, lw * 1.3f, a(col, isRed ? 0xA0 : 0x88));
            line(tcx - h0, y0, tcx - h1, y1, lw * 2.2f, a(col, isRed ? 0xB8 : 0x98));
            line(tcx + h0, y0, tcx + h1, y1, lw * 2.2f, a(col, isRed ? 0xB8 : 0x98));
        }
        // Pied ouvert : arche entre les jambes.
        path.reset();
        path.moveTo(tcx - tg[1] * 0.6f, ground);
        path.quadTo(tcx - tg[1] * 0.56f, ground - bodyH * 0.1f, tcx, ground - bodyH * 0.11f);
        path.quadTo(tcx + tg[1] * 0.56f, ground - bodyH * 0.1f, tcx + tg[1] * 0.6f, ground);
        path.close();
        p.setColor(a(0xFF0A0705, 0xE8));
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 1.6f);
        p.setColor(a(red, 0xA0));
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        // Plates-formes d'observation : caisson plus large, bandeau de fenêtres éclairées.
        float[] ds = {0.4f, 0.9f}, dw = {1.75f, 1.9f};
        for (int i = 0; i < 2; i++) {
            float yd = ground - bodyH * ds[i], hw2 = towerHalf(tg, ds[i]) * dw[i], dh = q * (i == 0 ? 0.6f : 0.4f);
            poly(a(0xFF0C0806, 0xF6), tcx - hw2 * 0.8f, yd - dh, tcx + hw2 * 0.8f, yd - dh, tcx + hw2, yd, tcx - hw2, yd);
            box(tcx - hw2, yd, tcx + hw2, yd + dh * 0.5f, a(0xFF0C0806, 0xF6));
            for (float wx = tcx - hw2 * 0.85f; wx < tcx + hw2 * 0.8f; wx += hw2 * 0.2f) box(wx, yd - dh * 0.65f, wx + hw2 * 0.1f, yd - dh * 0.2f, a(0xFFFFE2A0, 0xA8));
            line(tcx - hw2 * 0.8f, yd - dh, tcx + hw2 * 0.8f, yd - dh, lw * 1.4f, a(red, 0xB0));
            line(tcx - hw2, yd, tcx + hw2, yd, lw * 1.4f, a(white, 0x90));
        }
        // Antenne : mât rayé rouge et blanc jusqu'au sommet.
        float aTop = tg[4], aBot = ground - bodyH, nseg = 9;
        for (int i = 0; i < nseg; i++) {
            float ya = aBot - (aBot - aTop) * i / nseg, yb = aBot - (aBot - aTop) * (i + 1) / nseg;
            float wa = q * 0.1f * (1f - 0.55f * i / nseg);
            poly(a((i & 1) == 0 ? red : white, 0xC8), tcx - wa, ya, tcx + wa, ya, tcx + wa * 0.8f, yb, tcx - wa * 0.8f, yb);
        }
        // Immeubles du premier plan.
        float[][] near = tokyoBlocks(w, h, 1);
        for (int i = 0; i < near.length; i++) glass(near[i][0], ground, near[i][1], near[i][2], near[i][3], true, q, 0);
        // Passerelles couvertes entre les tours voisines, et enseignes verticales sur leurs flancs.
        for (int i = 0; i + 1 < near.length; i++) {
            float[] b = near[i], n = near[i + 1];
            float gx0 = b[0] + b[1], gx1 = n[0], mh = Math.min(b[2], n[2]);
            if (gx1 - gx0 > q * 1.6f || mh < H0 * 0.17f) continue;
            if (hash(i, 7) < 0.6f) {
                float y = ground - mh * (0.35f + 0.3f * hash(i, 8));
                box(gx0 - lw, y - q * 0.2f, gx1 + lw, y + q * 0.1f, a(0xFF0B0805, 0xFA));
                for (float wx = gx0; wx < gx1; wx += q * 0.16f) box(wx, y - q * 0.14f, wx + q * 0.1f, y - q * 0.04f, a(0xFFFFE2A0, 0xA0));
                line(gx0, y - q * 0.2f, gx1, y - q * 0.2f, lw * 1.2f, a(fold, 0x80));
                line(gx0, y + q * 0.1f, gx1, y + q * 0.1f, lw, a(sh[3], 0x38));
            }
            if (b[2] > H0 * 0.2f && hash(i, 9) < 0.5f) {
                float sx = b[0] + b[1] - q * 0.2f, sh2 = Math.min(b[2] * 0.4f, q * 5f);
                enseigne(sx, ground - b[2] * (0.5f + 0.25f * hash(i, 10)) - sh2 * 0.5f, q * 0.42f, sh2, i, q);
            }
        }
        // Quai : sol sombre, filet d'or, feux de voitures et reflets.
        box(0, ground, w, h, a(0xFF080604, 0xF8));
        line(0, ground, w, ground, lw * 1.5f, a(fold, 0x50));
        line(0, ground + q * 0.35f, w, ground + q * 0.35f, lw, a(fold, 0x18));
        for (int i = 0; i < 14; i++) {
            float x = w * rnd(), y = ground + q * (0.7f + 1.2f * rnd()), l = q * (1.5f + 4f * rnd());
            line(x, y, x + l, y, lw * 1.6f, a(i % 3 == 0 ? 0xFFE0452B : 0xFFFFE2A0, 0x44));
        }
        for (int i = 0; i < 10; i++) {
            float x = w * (0.45f + 0.55f * rnd());
            line(x, ground + q * 0.4f, x, ground + q * (0.9f + 1.4f * rnd()), lw, a(sh[3], 0x22));
        }
        // Poteaux et fils au premier plan.
        float[] px = {0.045f, 0.3f, 0.57f, 0.86f};
        float[] pb = new float[px.length], ph = new float[px.length];
        for (int i = 0; i < px.length; i++) {
            pb[i] = ground + q * (1.0f + 0.35f * i);
            ph[i] = H0 * (0.27f + 0.025f * i);
            poteau(w * px[i], pb[i], ph[i], lw * (1.2f + 0.2f * i), i == 1 || i == 3);
        }
        for (int i = 0; i + 1 < px.length; i++) {
            float xa = w * px[i], xb = w * px[i + 1];
            fil(xa + ph[i] * 0.16f, pb[i] - ph[i] * 0.94f, xb - ph[i + 1] * 0.16f, pb[i + 1] - ph[i + 1] * 0.94f, q * 0.5f, lw * 1.3f);
            fil(xa + ph[i] * 0.12f, pb[i] - ph[i] * 0.82f, xb - ph[i + 1] * 0.12f, pb[i + 1] - ph[i + 1] * 0.82f, q * 0.55f, lw * 1.3f);
            fil(xa - ph[i] * 0.16f, pb[i] - ph[i] * 0.94f, xb + ph[i + 1] * 0.16f, pb[i + 1] - ph[i + 1] * 0.94f, q * 0.6f, lw * 1.3f);
        }
        fil(-q, pb[0] - ph[0] * 0.94f - q * 0.3f, w * px[0] - ph[0] * 0.16f, pb[0] - ph[0] * 0.94f, q * 0.4f, lw * 1.3f);
        fil(w * px[3] + ph[3] * 0.16f, pb[3] - ph[3] * 0.94f, w + q, pb[3] - ph[3] * 0.94f - q * 0.5f, q * 0.4f, lw * 1.3f);
        // Faisceaux de deux projecteurs posés sur les toits (au repos).
        if (!MOVING) {
            float[] rf = tokyoRoofs(w, h);
            BackdropMotion.searchlights(c, w, h, sh[3], rf, 0.12f);
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

    /** Haut de la tour radar : (x, toit de la cabine, axe de l'antenne, balise), partagé avec BackdropMotion. */
    static float[] towerPts(float w, float h) {
        float[] pt = basePts(w, h);
        float q = Math.min(w, h * 1.2f) * 0.06f, cab = pt[1] + q * 1.5f - q * 0.9f;
        return new float[] {pt[0], cab, cab - q * 0.9f, cab - q * 1.6f};
    }

    /**
     * Base de nuit sous la vision nocturne : règle de réglage de fréquence en haut, tour d'antenne radar (cabine à
     * fenêtres, antenne tournante, balise rouge ; le sonar reste sur la manette), conteneurs et portique de grue à droite, tour de garde à gauche, clôture,
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
        float rcx = pt[0];
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
        // Cabine du radar en haut du mât, antenne à barre sur son pylône, balise rouge (ce qui bouge : BackdropMotion.base).
        float[] tw = towerPts(w, h);
        box(rcx - q * 0.62f, tw[1], rcx + q * 0.62f, topY + q * 0.1f, a(dk, 0xF8));
        poly(a(dk, 0xF8), rcx - q * 0.78f, tw[1], rcx, tw[1] - q * 0.28f, rcx + q * 0.78f, tw[1]);
        line(rcx - q * 0.78f, tw[1], rcx, tw[1] - q * 0.28f, lw, a(sh[3], 0x50));
        line(rcx, tw[1] - q * 0.28f, rcx + q * 0.78f, tw[1], lw, a(sh[3], 0x50));
        for (int i = 0; i < 3; i++) {
            float wx = rcx + (i - 1) * q * 0.34f;
            box(wx - q * 0.08f, tw[1] + q * 0.22f, wx + q * 0.08f, tw[1] + q * 0.46f, a(sh[3], 0x24));
        }
        line(rcx, tw[1] - q * 0.28f, rcx, tw[2], lw * 2.2f, a(dk, 0xF8));
        if (!MOVING) {
            BackdropMotion.antenna(c, rcx, tw[2], q, lw, sh[3], 0.7f);
            BackdropMotion.beacon(c, rcx, tw[3], q, 0xFFFF3B3B, 0.25f);
        }
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

    /** Conifère en silhouette : quatre étages de triangles sur un tronc court. */
    private void conifer(float x, float yb, float hgt, int color) {
        float ww = hgt * 0.2f;
        box(x - ww * 0.12f, yb - hgt * 0.12f, x + ww * 0.12f, yb + 1, color);
        for (int i = 0; i < 4; i++) {
            float yy = yb - hgt * (0.1f + 0.2f * i), wd = ww * (1.35f - 0.27f * i);
            poly(color, x - wd, yy, x, yy - hgt * 0.34f, x + wd, yy);
        }
    }

    /**
     * Torii vermillon de face (le même que la manette) : piliers sur socles, nuki qui dépasse, plaque, linteau et
     * chapeau noir aux bouts relevés. Rend le bas du nuki, où pendent les lanternes.
     */
    private float toriiGate(float cx, float span, float top, float base, float m, float lw) {
        float xl = cx - span / 2, xr = cx + span / 2, pw = m * 0.042f, pt = top + m * 0.06f;
        float ny = top + (base - top) * 0.29f, nh = m * 0.045f, ext = m * 0.09f, th = m * 0.055f, L = span * 0.5f + m * 0.16f;
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? xl : xr;
            poly(a(Theme.darken(VERM, 0.45f), 0xFC), x - pw, base, x, base, x, pt, x - pw * 0.82f, pt);
            poly(a(VERM, 0xFC), x, base, x + pw, base, x + pw * 0.82f, pt, x, pt);
            line(x + pw * 0.75f, base, x + pw * 0.6f, pt, lw, a(Theme.lighten(VERM, 0.45f), 0x70));
            box(x - pw * 1.35f, base - m * 0.02f, x + pw * 1.35f, base + m * 0.04f, a(0xFF0A0505, 0xFC));
            line(x - pw * 1.35f, base - m * 0.02f, x + pw * 1.35f, base - m * 0.02f, lw, a(fold, 0x60));
            box(x - pw * 1.05f, base - m * 0.07f, x + pw * 1.05f, base - m * 0.045f, a(0xFF2A1408, 0xFC));
            line(x - pw * 1.05f, base - m * 0.07f, x + pw * 1.05f, base - m * 0.07f, lw, a(fold, 0x80));
        }
        box(xl - ext, ny, xr + ext, ny + nh, a(VERM, 0xFC));
        box(xl - ext, ny + nh * 0.6f, xr + ext, ny + nh, a(Theme.darken(VERM, 0.35f), 0xFC));
        line(xl - ext, ny, xr + ext, ny, lw * 1.2f, a(Theme.lighten(VERM, 0.45f), 0x80));
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? xl : xr;
            box(x + pw * 0.9f, ny - nh * 0.2f, x + pw * 1.5f, ny + nh * 1.2f, a(0xFF0A0505, 0xF0));
        }
        box(cx - m * 0.035f, top + th, cx + m * 0.035f, ny, a(Theme.darken(VERM, 0.3f), 0xFC));
        box(cx - m * 0.05f, top + th + m * 0.025f, cx + m * 0.05f, ny - m * 0.02f, a(0xFF0E0606, 0xFA));
        line(cx - m * 0.05f, top + th + m * 0.025f, cx + m * 0.05f, top + th + m * 0.025f, lw, a(fold, 0x70));
        box(cx - span * 0.5f - m * 0.08f, top + th * 0.55f, cx + span * 0.5f + m * 0.08f, top + th * 1.15f, a(VERM, 0xFC));
        for (int k = 0; k < 2; k++) {
            path.reset();
            path.moveTo(cx - L - m * 0.04f, top - m * 0.06f);
            path.quadTo(cx - L * 0.6f, top + th * 0.2f, cx, top + th * 0.25f);
            path.quadTo(cx + L * 0.6f, top + th * 0.2f, cx + L + m * 0.04f, top - m * 0.06f);
            if (k == 0) {
                path.lineTo(cx + L * 0.9f, top + th * 0.6f);
                path.quadTo(cx, top + th * 0.9f, cx - L * 0.9f, top + th * 0.6f);
                path.close();
                p.setColor(a(0xFF120607, 0xFC));
            } else {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(lw * 1.4f);
                p.setColor(a(Theme.lighten(VERM, 0.4f), 0x90));
            }
            c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
        }
        return ny + nh;
    }

    /** Lanterne de pierre (tōrō) posée en b, de taille s. */
    private void toro(float x, float b, float s, float lw) {
        int st = a(0xFF0B0707, 0xFA);
        box(x - s * 0.8f, b - s * 0.35f, x + s * 0.8f, b, st);
        box(x - s * 0.22f, b - s * 1.4f, x + s * 0.22f, b - s * 0.35f, st);
        box(x - s * 0.75f, b - s * 1.65f, x + s * 0.75f, b - s * 1.4f, a(0xFF0E0909, 0xFA));
        box(x - s * 0.5f, b - s * 2.35f, x + s * 0.5f, b - s * 1.65f, st);
        box(x - s * 0.3f, b - s * 2.2f, x + s * 0.3f, b - s * 1.8f, a(fold, 0x70));
        poly(st, x - s * 1.05f, b - s * 2.35f, x, b - s * 2.95f, x + s * 1.05f, b - s * 2.35f);
        line(x, b - s * 2.95f, x + s * 1.05f, b - s * 2.35f, lw, a(sh[3], 0x40));
        disc(x, b - s * 3.05f, s * 0.14f, st);
    }

    /** Branches de pin qui entrent par un bord (dir 1 depuis la gauche, -1 depuis la droite). */
    private void pinBranches(float x0, float dir, float h, float m, float lw) {
        for (int k = 0; k < 3; k++) {
            float y = h * (0.05f + 0.07f * k), len = m * (0.34f - 0.07f * k);
            line(x0, y, x0 + dir * len, y + m * 0.04f, lw * 2.4f, a(0xFF0A0405, 0xF8));
            for (int j = 0; j < 5; j++) {
                float px = x0 + dir * len * (0.3f + 0.15f * j), py = y + m * 0.04f * (0.3f + 0.15f * j);
                for (int n = 0; n < 7; n++) {
                    double ang = -Math.PI / 2 + (n - 3) * 0.32;
                    line(px, py, px + (float) Math.cos(ang) * m * 0.035f, py + (float) Math.sin(ang) * m * 0.035f + m * 0.02f, lw * 0.9f, a(0xFF0F0607, 0xF0));
                }
            }
        }
    }

    /** Feuilles d'érable tombées dans un rectangle. */
    private void mapleLeaves(float x0, float x1, float y0, float y1, int n, float m) {
        for (int i = 0; i < n; i++) {
            float x = x0 + (x1 - x0) * rnd(), y = y0 + (y1 - y0) * rnd(), s2 = m * 0.012f;
            for (int k = 0; k < 5; k++) {
                double ang = -Math.PI / 2 + (k - 2) * 0.55;
                float ex = x + (float) Math.cos(ang) * s2 * 2, ey = y + (float) Math.sin(ang) * s2 * 2;
                float nx = -(float) Math.sin(ang) * s2 * 0.5f, nyy = (float) Math.cos(ang) * s2 * 0.5f;
                poly(a((k & 1) == 1 ? sh[2] : fold, 0x60), x, y, (x + ex) / 2 + nx, (y + ey) / 2 + nyy, ex, ey, (x + ex) / 2 - nx, (y + ey) / 2 - nyy);
            }
        }
    }

    /** Hauteur de colline (t de 0 à 1 en travers de l'écran) : basse au centre, un grand mont à droite. */
    static float hillH(float t, float H0) {
        float g = Math.abs(t - 0.88f) / 0.2f, g2 = (t - 0.04f) / 0.1f;
        return H0 * (0.05f + 0.025f * (float) Math.sin(t * 9f) + 0.31f * (float) Math.exp(-g * g * g) + 0.12f * (float) Math.exp(-g2 * g2));
    }

    private static float ridge(float t, float H0) {
        return H0 * (0.12f + 0.05f * (float) Math.sin(t * 7f + 1f) + 0.12f * t);
    }

    /** Forêt dense posée sur la crête lointaine : quatre rangées, pâles au loin. */
    private void foret(float w, float h, float H0, float ground) {
        float[] hgt = {0.07f, 0.085f, 0.1f, 0.12f}, drop = {0f, 0.02f, 0.045f, 0.075f};
        int[] col = {a(0xFF3C1D20, 0xB8), a(0xFF2E1618, 0xE0), a(0xFF251213, 0xF4), a(0xFF170909, 0xFA)};
        for (int r = 0; r < 4; r++) {
            float step = H0 * (0.022f + 0.004f * r);
            path.reset();
            path.moveTo(0, h);
            for (float x = 0; x <= w + step; x += step) path.lineTo(x, ground - ridge(x / w, H0) + H0 * drop[r]);
            path.lineTo(w + step, h);
            path.close();
            p.setColor(col[r]);
            c.drawPath(path, p);
            for (float x = -step + step * 0.5f * (r & 1); x < w + step; x += step)
                conifer(x, ground - ridge(x / w, H0) + H0 * drop[r] + H0 * 0.012f, H0 * hgt[r] * (0.75f + 0.5f * rnd()), col[r]);
        }
    }

    private void pin(float x, float y, float hgt, float lw) {
        float ww = hgt * 0.22f;
        int col = a(0xFF0A0405, 0xF8);
        box(x - lw * 1.2f, y - hgt * 0.35f, x + lw * 1.2f, y + 1, col);
        for (int i = 0; i < 4; i++) {
            float yy = y - hgt * (0.28f + 0.2f * i), wd = ww * (1f - 0.22f * i);
            poly(col, x - wd, yy, x, yy - hgt * 0.3f, x + wd, yy);
            line(x, yy - hgt * 0.3f, x + wd, yy, lw, a(fold, 0x34));
        }
    }

    /**
     * Repères du fond Torii (0.66.23), partagés avec BackdropMotion : x du torii, portée, bas du nuki, échelle,
     * puis x, y et demi-largeur des cinq fenêtres de la pagode.
     */
    static float[] japonPts(float w, float h) {
        float H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f, ground = h * 0.9f;
        float top = ground - hillH(0.5f, H0) + q * 0.1f - q * 3.6f, base = ground - hillH(0.5f, H0) + q * 0.3f;
        float[] r = new float[4 + 15];
        r[0] = w * 0.5f;
        r[1] = q * 3.8f;
        r[2] = top + (base - top) * 0.29f + q * 3.6f * 0.045f;
        r[3] = q * 3.6f;
        float qq = q * 0.82f, bw = qq * 2.6f, y = ground - hillH(0.88f, H0) + qq * 0.1f - qq * 0.28f;
        for (int i = 0; i < 5; i++) {
            float bwi = bw * (1f - 0.15f * i), bh = qq * 0.7f;
            r[4 + i * 3] = w * 0.88f;
            r[5 + i * 3] = y - bh * 0.5f;
            r[6 + i * 3] = bwi * 0.3f;
            y = y - bh - qq * 0.36f;
        }
        return r;
    }

    /**
     * Fond Torii (0.66.23, l'ancien Écarlate dans la DA de la manette) : lune rouge en facettes et nervures
     * d'éventail, nuages dorés en écailles, collines et forêt, pagode vermillon aux fenêtres allumées sur le grand
     * mont, petit torii au centre, pins et lanterne de pierre à gauche, feuilles d'érable devant. Braises, lanternes
     * de papier, fenêtres qui respirent et feuilles qui tombent : BackdropMotion.japon.
     */
    private void japon(float w, float h) {
        float u = Math.max(w, h), H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f;
        float ground = h * 0.9f, lw = Math.max(1f, u / 1100f);
        float mx = w * 0.56f, my = Math.min(h * 0.25f, ground - H0 * 0.62f), mr = H0 * 0.13f;
        vgrad(0, 0, w, h, a(0xFF1E0C0D, 0x80), a(0xFF070304, 0xA0));
        glow(mx, my, H0 * 0.85f, sh[2], 0x1E);
        for (int i = 0; i < 30; i++) star(w * rnd(), ground * 0.5f * rnd(), u * (0.001f + 0.0012f * rnd()), 0x20 + Math.round(rnd() * 0x30));
        for (int i = 0; i < 28; i++) {
            double ang = Math.PI * 2 * i / 28;
            line(mx + (float) Math.cos(ang) * mr * 1.25f, my + (float) Math.sin(ang) * mr * 1.25f,
                    mx + (float) Math.cos(ang) * u, my + (float) Math.sin(ang) * u, lw, a(fold, 0x0C));
        }
        for (int j = 0; j < 4; j++) {
            float r = q * (0.9f + 0.25f * j), y = my + mr * (0.5f + 0.75f * j);
            for (float x = w * (0.3f + 0.05f * j) + (j & 1) * r; x < w + r; x += r * 2) {
                float k = Math.max(0f, Math.min(1f, (x / w - 0.25f) * 1.5f));
                arcLine(x, y, r, 180, 180, lw, a(fold, Math.round(0x26 * k)));
                arcLine(x, y, r * 0.66f, 180, 180, lw, a(fold, Math.round(0x16 * k)));
            }
        }
        disc(mx, my, mr * 1.45f, a(sh[2], 0x0E));
        disc(mx, my, mr * 1.2f, a(sh[2], 0x16));
        for (int i = 0; i < 24; i++) {
            double t0 = Math.PI * 2 * i / 24, t1 = Math.PI * 2 * (i + 1) / 24;
            boolean left = Math.cos((t0 + t1) / 2) < 0;
            tri(mx, my, mx + (float) Math.cos(t0) * mr, my + (float) Math.sin(t0) * mr,
                    mx + (float) Math.cos(t1) * mr, my + (float) Math.sin(t1) * mr, a(sh[left ? 1 : 2], (i & 1) == 0 ? 0x70 : 0x5C));
        }
        arcLine(mx, my, mr, 0, 360, lw * 1.4f, a(fold, 0x44));
        int steps = 64;
        for (int layer = 0; layer < 2; layer++) {
            if (layer == 1) foret(w, h, H0, ground);
            path.reset();
            path.moveTo(0, h);
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps;
                path.lineTo(w * t, ground - (layer == 0 ? ridge(t, H0) : hillH(t, H0)));
            }
            path.lineTo(w, h);
            path.close();
            p.setColor(layer == 0 ? a(0xFF1D0B0C, 0xC8) : a(0xFF0A0506, 0xFA));
            c.drawPath(path, p);
            float px = 0, py = 0;
            for (int i = 0; i <= steps; i++) {
                float t = i / (float) steps, x = w * t, y = ground - (layer == 0 ? ridge(t, H0) : hillH(t, H0));
                if (i > 0) line(px, py, x, y, lw * 1.3f, layer == 0 ? a(sh[1], 0x18) : a(Theme.lighten(VERM, 0.3f), 0x50));
                px = x;
                py = y;
            }
        }
        // Pagode vermillon : murs laqués, toits noirs liserés, fenêtres allumées.
        float qq = q * 0.82f, pgx = w * 0.88f, bw = qq * 2.6f, y = ground - hillH(0.88f, H0) + qq * 0.1f;
        int edge = a(Theme.lighten(VERM, 0.4f), 0x90);
        box(pgx - bw * 0.62f, y - qq * 0.28f, pgx, y + qq * 0.12f, a(0xFF1A0A0B, 0xFC));
        box(pgx, y - qq * 0.28f, pgx + bw * 0.62f, y + qq * 0.12f, a(0xFF260F10, 0xFC));
        line(pgx - bw * 0.62f, y - qq * 0.28f, pgx + bw * 0.62f, y - qq * 0.28f, lw, a(fold, 0x50));
        y -= qq * 0.28f;
        for (int i = 0; i < 5; i++) {
            float bwi = bw * (1f - 0.15f * i), bh = qq * 0.7f;
            box(pgx - bwi / 2, y - bh, pgx, y, a(Theme.darken(VERM, 0.55f), 0xFA));
            box(pgx, y - bh, pgx + bwi / 2, y, a(Theme.darken(VERM, 0.3f), 0xFA));
            box(pgx - bwi * 0.12f, y - bh * 0.7f, pgx + bwi * 0.12f, y - bh * 0.3f, a(fold, 0x70));
            float yb = y - bh, ew = bwi * 0.5f + qq * 0.75f;
            poly(a(0xFF120505, 0xFC), pgx - ew - qq * 0.3f, yb - qq * 0.2f, pgx - ew * 0.7f, yb + qq * 0.03f,
                    pgx + ew * 0.7f, yb + qq * 0.03f, pgx + ew + qq * 0.3f, yb - qq * 0.2f, pgx + bwi * 0.4f, yb - qq * 0.42f, pgx - bwi * 0.4f, yb - qq * 0.42f);
            line(pgx - ew - qq * 0.3f, yb - qq * 0.2f, pgx - ew * 0.7f, yb + qq * 0.03f, lw, edge);
            line(pgx + ew + qq * 0.3f, yb - qq * 0.2f, pgx + ew * 0.7f, yb + qq * 0.03f, lw, edge);
            line(pgx - ew * 0.7f, yb + qq * 0.03f, pgx + ew * 0.7f, yb + qq * 0.03f, lw, a(Theme.lighten(VERM, 0.4f), 0x60));
            y = yb - qq * 0.36f;
        }
        line(pgx, y, pgx, y - qq * 1.7f, lw * 1.6f, a(fold, 0x80));
        for (int i = 0; i < 3; i++) line(pgx - qq * 0.22f, y - qq * (0.5f + 0.4f * i), pgx + qq * 0.22f, y - qq * (0.5f + 0.4f * i), lw, a(fold, 0x70));
        // Petit torii vermillon au centre, sur la colline.
        float base = ground - hillH(0.5f, H0) + q * 0.3f, top = ground - hillH(0.5f, H0) + q * 0.1f - q * 3.6f;
        toriiGate(w * 0.5f, q * 3.8f, top, base, q * 3.6f, lw);
        pin(w * 0.03f, ground - hillH(0.03f, H0) + q * 0.2f, H0 * 0.34f, lw * 2.4f);
        pin(w * 0.105f, ground - hillH(0.105f, H0) + q * 0.2f, H0 * 0.16f, lw * 1.5f);
        toro(w * 0.17f, ground - hillH(0.17f, H0) + q * 0.2f, q * 0.55f, lw);
        mapleLeaves(0, w * 0.7f, h * 0.93f, h * 0.995f, 22, H0);
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

    /** Branche effilée (0.66.23) : trapèze sombre avec un liseré, puis deux ou trois rameaux qui montent un peu. */
    private void branche(float x, float y, float len, double ang, float wid, int depth, java.util.ArrayList<float[]> ends) {
        float ex = x + (float) Math.cos(ang) * len, ey = y + (float) Math.sin(ang) * len;
        float nx = -(float) Math.sin(ang), ny = (float) Math.cos(ang), w2 = wid * 0.62f;
        poly(a(0xFF0B060B, 0xFA), x + nx * wid / 2, y + ny * wid / 2, ex + nx * w2 / 2, ey + ny * w2 / 2,
                ex - nx * w2 / 2, ey - ny * w2 / 2, x - nx * wid / 2, y - ny * wid / 2);
        line(x + nx * wid * 0.35f, y + ny * wid * 0.35f, ex + nx * w2 * 0.35f, ey + ny * w2 * 0.35f, Math.max(1f, wid * 0.08f), a(sh[1], 0x50));
        disc(ex, ey, w2 / 2, a(0xFF0B060B, 0xFA));
        if (depth <= 0) {
            ends.add(new float[] {ex, ey});
            return;
        }
        int kids = depth > 3 ? 2 : (rnd() < 0.5f ? 3 : 2);
        for (int i = 0; i < kids; i++) {
            double na = ang + (i - (kids - 1) / 2.0) * (0.7 + 0.35 * rnd()) + (rnd() - 0.5) * 0.35;
            branche(ex, ey, len * (0.7f + 0.12f * rnd()), na + (-Math.PI / 2 - na) * 0.08 * rnd() + 0.08, w2, depth - 1, ends);
        }
        if (depth <= 2 && rnd() < 0.6f) ends.add(new float[] {ex, ey});
    }

    /** Grand cerisier du premier plan : branches effilées et grappes de fleurs au bout de chaque rameau. */
    private void grandCerisier(float x, float y, float len, double ang, float wid, int depth, float q, float lum) {
        java.util.ArrayList<float[]> ends = new java.util.ArrayList<float[]>();
        branche(x, y, len, ang, wid, depth, ends);
        for (float[] e : ends) {
            for (int j = 0; j < 5; j++) {
                float r = q * (0.3f + 0.45f * rnd());
                disc(e[0] + (rnd() - 0.5f) * q * 1.6f, e[1] + (rnd() - 0.5f) * q * 1.2f, r, a(sh[2 + (int) (rnd() * 3)], Math.round((0x0E + 0x18 * rnd()) * lum)));
            }
            if (rnd() < 0.75f) flower(e[0] + (rnd() - 0.5f) * q * 1.6f, e[1] + (rnd() - 0.5f) * q * 1.2f, q * 0.34f, rnd() * 6.28, Math.round(0x80 * lum));
        }
    }

    /**
     * Nuit de printemps : lune douce, collines lointaines, rivière lisse qui reflète la lune,
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
        java.util.ArrayList<float[]> lan = new java.util.ArrayList<float[]>();
        for (float x = q; x < w; x += q * (0.7f + rnd())) {
            if (rnd() < 0.5f * (1.2f - x / w)) continue;
            int al = Math.round(0x58 * (0.3f + 0.7f * x / w));
            disc(x, bank - q * 0.12f, Math.max(1.2f, q * 0.06f), a(0xFFFFD9A0, al));
            lan.add(new float[] {x, al});
        }
        // Rivière : eau sombre, reflet de la lune, écailles en perspective.
        vgrad(0, bank, w, h, a(0xFF100B16, 0xF6), a(0xFF06040A, 0xFA));
        // Eau (0.66.21) : calme et lisse, plus claire sous la lune. Les ondulations qui glissent, le scintillement
        // du reflet et les pétales qui flottent sont dans BackdropMotion.riviere.
        vgrad(0, bank, w, h, a(0xFF1A1222, 0xF8), a(0xFF07050A, 0xFC));
        p.setShader(new LinearGradient(mx - mr * 3, 0, mx + mr * 3, 0, new int[] {a(sh[3], 0), a(sh[3], 0x1C), a(sh[3], 0)}, null, Shader.TileMode.CLAMP));
        path.reset();
        path.moveTo(mx - mr * 0.8f, bank);
        path.lineTo(mx + mr * 0.8f, bank);
        path.lineTo(mx + mr * 3, h);
        path.lineTo(mx - mr * 3, h);
        path.close();
        c.drawPath(path, p);
        p.setShader(null);
        // Reflets des lanternes de l'autre berge : petits traits tremblés sous chacune.
        for (float[] l : lan) {
            for (int k2 = 0; k2 < 5; k2++) {
                float yy = bank + q * (0.25f + 0.32f * k2), ww = q * (0.12f + 0.05f * k2) * (0.6f + 0.8f * rnd());
                line(l[0] - ww, yy, l[0] + ww, yy, lw * 1.2f, a(0xFFFFD9A0, Math.round(l[1] * 0.7f * (1f - k2 / 5f))));
            }
        }
        // Pont en dos d'âne, posé sur deux îlots de berge : culées sur la terre, piles qui plongent dans l'eau,
        // reflet de l'arche sur la rivière (la ligne d'eau du pont est un peu en avant de la berge lointaine).
        float cx = w * 0.78f, sp = Math.min(w * 0.17f, H0 * 0.3f), rise = H0 * 0.1f, th = q * 0.32f;
        float yw = bank + q * 0.75f, deck0 = yw - q * 0.55f;
        for (int sd = -1; sd <= 1; sd += 2) {
            float xe = cx + sd * sp, rx = q * 2.6f;
            if (sd > 0) xe += q * 0.6f;
            // Îlot : reflet dans l'eau, puis terre sombre bombée.
            rc.set(xe - rx, yw - q * 0.3f, xe + rx, yw + q * 0.9f);
            p.setColor(a(0xFF0B070C, 0x50));
            c.drawOval(rc, p);
            rc.set(xe - rx, yw - q * 0.95f, xe + rx, yw + q * 0.28f);
            p.setColor(a(0xFF0B070C, 0xFC));
            c.drawOval(rc, p);
            arcLine(xe, yw - q * 0.3f, rx, 200, 140, lw, a(sh[2], 0x24));
        }
        int n = 28;
        float[] tx = new float[n + 1], ty = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float u2 = -1f + 2f * i / n;
            tx[i] = cx + u2 * sp;
            ty[i] = deck0 - rise * (1f - u2 * u2);
        }
        // Piles : deux poteaux par appui, du dessous du tablier jusque dans l'eau, avec une ride au pied.
        for (int m = 1; m < 4; m++) {
            int i = m * 7;
            for (int sd = -1; sd <= 1; sd += 2) {
                float x = tx[i] + sd * q * 0.2f, yb = yw + q * 0.12f;
                box(x - lw * 3f, ty[i], x + lw * 3f, yb, a(0xFF160E18, 0xFE));
                line(x - lw * 3f, ty[i], x - lw * 3f, yb, lw, a(sh[2], 0x50));
            }
            float yh = ty[i] + (yw - ty[i]) * 0.5f;
            box(tx[i] - q * 0.2f, yh, tx[i] + q * 0.2f, yh + lw * 3f, a(0xFF160E18, 0xFE));
            rc.set(tx[i] - q * 0.55f, yw + q * 0.02f, tx[i] + q * 0.55f, yw + q * 0.24f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(lw);
            p.setColor(a(sh[3], 0x34));
            c.drawOval(rc, p);
            p.setStyle(Paint.Style.FILL);
        }
        // Reflet de l'arche (écrasé, très discret).
        for (int i = 0; i < n; i++) {
            float y0 = yw + (yw - ty[i]) * 0.55f, y1 = yw + (yw - ty[i + 1]) * 0.55f;
            line(tx[i], y0, tx[i + 1], y1, lw * 1.6f, a(sh[3], 0x1C));
        }
        // Tablier voûté : fin, il laisse voir l'eau et les piles dessous ; culées de pierre aux deux bouts.
        path.reset();
        for (int i = 0; i <= n; i++) {
            if (i == 0) path.moveTo(tx[i], ty[i]);
            else path.lineTo(tx[i], ty[i]);
        }
        for (int i = n; i >= 0; i--) path.lineTo(tx[i], ty[i] + th);
        path.close();
        p.setColor(a(0xFF0B070C, 0xFC));
        c.drawPath(path, p);
        for (int sd = -1; sd <= 1; sd += 2) {
            float xe = cx + sd * sp;
            box(xe - q * 0.34f, ty[sd < 0 ? 0 : n] - q * 0.05f, xe + q * 0.34f, yw - q * 0.2f, a(0xFF120A12, 0xFC));
        }
        for (int i = 0; i < n; i++) {
            line(tx[i], ty[i], tx[i + 1], ty[i + 1], lw * 1.4f, a(sh[3], 0x80));
            line(tx[i], ty[i] - q * 0.55f, tx[i + 1], ty[i + 1] - q * 0.55f, lw, a(sh[2], 0x4A));
            if ((i & 1) == 0) line(tx[i], ty[i], tx[i], ty[i] - q * 0.55f, lw, a(sh[2], 0x44));
        }
        // Cerisiers sur les berges.
        cerisier(w * 0.975f, yw - q * 0.35f, H0 * 0.15f, -Math.PI / 2 - 0.35, q * 0.45f, q, 1f);
        cerisier(cx - sp - q * 0.5f, yw - q * 0.35f, H0 * 0.07f, -Math.PI / 2 + 0.15, q * 0.25f, q * 0.8f, 0.8f);
        // Grand cerisier au premier plan (0.66.23) : tronc au bord gauche, couronne qui s'étend vers la droite,
        // et une branche fleurie qui retombe du coin haut gauche au-dessus de l'eau.
        grandCerisier(w * 0.05f, h * 1.02f, H0 * 0.3f, -Math.PI / 2 + 0.22, q * 1.2f, 5, q * 0.9f, 1f);
        grandCerisier(-q, h * 0.06f, H0 * 0.22f, 0.1, q * 0.5f, 3, q * 0.75f, 0.9f);
        for (int i = 0; i < 20; i++) kite(w * 0.02f + w * 0.25f * rnd(), h * (0.9f + 0.08f * rnd()), q * 0.25f, q * 0.1f, rnd() * 6.28, 2, 4, 0x50, false);
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
     * du bas (comme le soleil d'Ambre), règle de fréquence en haut, onde radio sur le
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
        // Le faisceau et les échos ne sont plus dessinés ici : ils tournent et s'allument dans PadMotion (0.66.21).
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


    // ---- Ambre, manette (0.66.21) : la métropole vue de près

    /** Gratte-ciel de la façade : {x, largeur, hauteur} en fractions de la largeur et de la hauteur, du bord gauche au bord droit. */
    private static final float[][] MEGA_NEAR = {{-0.02f, 0.13f, 0.86f, 1f}, {0.1f, 0.1f, 0.58f, 0.8f}, {0.19f, 0.08f, 0.34f, 0.6f},
        {0.73f, 0.08f, 0.36f, 0.6f}, {0.8f, 0.1f, 0.62f, 0.8f}, {0.9f, 0.13f, 0.9f, 1f}};

    /** Repères partagés avec PadMotion : toits des six tours proches (x, y) puis plate-forme de la tour (x, y, demi-largeur, hauteur). */
    static float[] megapolePts(float w, float h) {
        float m = Math.min(w, h), q = m * 0.06f, ground = h * 0.92f, top = -h * 0.25f, hb = m * 0.38f, ht = m * 0.05f, yd = h * 0.3f;
        float[] out = new float[16];
        for (int i = 0; i < 6; i++) {
            out[i * 2] = w * (MEGA_NEAR[i][0] + MEGA_NEAR[i][1] * 0.5f);
            out[i * 2 + 1] = ground - h * MEGA_NEAR[i][2];
        }
        float sPos = (ground - yd) / (ground - top);
        out[12] = w * 0.5f;
        out[13] = yd;
        out[14] = (ht + (hb - ht) * (float) Math.pow(1f - sPos, 1.7)) * 1.9f;
        out[15] = q * 0.7f;
        return out;
    }

    /**
     * Ambre, manette : le pied de la tour rouge et blanche au milieu (treillis, arche, plate-forme), des tours
     * de verre sur les côtés avec leurs enseignes verticales, des fils en travers, la rue et ses reflets.
     * Les feux d'avion, les projecteurs et les fenêtres qui s'allument sont dans PadMotion.
     */
    private void megapolePad(float w, float h) {
        float u = Math.max(w, h), m = Math.min(w, h), lw = Math.max(1f, u / 900f), q = m * 0.06f, ground = h * 0.92f;
        vgrad(0, 0, w, h, a(0xFF1C140A, 0x60), a(0xFF060403, 0x90));
        glow(w * 0.5f, ground, m * 0.9f, sh[2], 0x22);
        for (int i = 0; i < 22; i++) star(w * rnd(), h * 0.4f * rnd(), u * (0.001f + 0.0012f * rnd()), 0x18 + Math.round(rnd() * 0x30));
        nuage(w * 0.3f, h * 0.14f, w * 0.18f, q * 0.6f, 0x40);
        nuage(w * 0.52f, h * 0.24f, w * 0.2f, q * 0.55f, 0x38);
        // Tour : corps en treillis rouge et blanc qui sort par le haut.
        float tcx = w * 0.5f, top = -h * 0.25f, hb = m * 0.38f, ht = m * 0.05f, bodyH = ground - top;
        float[] tg = {tcx, hb, ht};
        int red = 0xFFD8432E, white = 0xFFFFE6C0;
        path.reset();
        path.moveTo(tcx - hb, ground);
        for (int i = 1; i <= 20; i++) path.lineTo(tcx - towerHalf(tg, i / 20f), ground - bodyH * i / 20f);
        for (int i = 20; i >= 0; i--) path.lineTo(tcx + towerHalf(tg, i / 20f), ground - bodyH * i / 20f);
        path.close();
        p.setColor(a(0xFF100B07, 0xE0));
        c.drawPath(path, p);
        int nb = 12;
        for (int k2 = 0; k2 < nb; k2++) {
            float s0 = k2 / (float) nb, s1 = (k2 + 1) / (float) nb;
            float y0 = ground - bodyH * s0, y1 = ground - bodyH * s1, h0 = towerHalf(tg, s0), h1 = towerHalf(tg, s1);
            boolean isRed = ((k2 / 2) & 1) == 0;
            int col = isRed ? red : white;
            poly(a(col, isRed ? 0x2C : 0x1C), tcx - h0, y0, tcx + h0, y0, tcx + h1, y1, tcx - h1, y1);
            line(tcx - h0, y0, tcx + h1, y1, lw * 1.1f, a(col, isRed ? 0x88 : 0x70));
            line(tcx + h0, y0, tcx - h1, y1, lw * 1.1f, a(col, isRed ? 0x88 : 0x70));
            if (k2 < 5) {
                // Grandes travées du bas : croix intermédiaires.
                float ym = (y0 + y1) / 2, hm = towerHalf(tg, (s0 + s1) / 2);
                line(tcx - h0, y0, tcx - hm * 0.2f, ym, lw, a(col, 0x50));
                line(tcx + h0, y0, tcx + hm * 0.2f, ym, lw, a(col, 0x50));
                line(tcx - h0 * 0.5f, y0, tcx - h1 * 0.5f, y1, lw * 0.9f, a(col, 0x40));
                line(tcx + h0 * 0.5f, y0, tcx + h1 * 0.5f, y1, lw * 0.9f, a(col, 0x40));
            }
            line(tcx - h1, y1, tcx + h1, y1, lw * 1.3f, a(col, isRed ? 0xA0 : 0x88));
            line(tcx - h0, y0, tcx - h1, y1, lw * 2.4f, a(col, isRed ? 0xB8 : 0x98));
            line(tcx + h0, y0, tcx + h1, y1, lw * 2.4f, a(col, isRed ? 0xB8 : 0x98));
        }
        // Arche entre les jambes.
        path.reset();
        path.moveTo(tcx - hb * 0.62f, ground);
        path.quadTo(tcx - hb * 0.58f, ground - h * 0.2f, tcx, ground - h * 0.22f);
        path.quadTo(tcx + hb * 0.58f, ground - h * 0.2f, tcx + hb * 0.62f, ground);
        path.close();
        p.setColor(a(0xFF0A0705, 0xEC));
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 1.8f);
        p.setColor(a(red, 0xA0));
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        // Plate-forme d'observation.
        float[] mp = megapolePts(w, h);
        float yd = mp[13], dw = mp[14], dh = mp[15];
        poly(a(0xFF0C0806, 0xF6), tcx - dw * 0.8f, yd - dh, tcx + dw * 0.8f, yd - dh, tcx + dw, yd, tcx - dw, yd);
        box(tcx - dw, yd, tcx + dw, yd + dh * 0.45f, a(0xFF0C0806, 0xF6));
        line(tcx - dw * 0.8f, yd - dh, tcx + dw * 0.8f, yd - dh, lw * 1.4f, a(red, 0xB0));
        line(tcx - dw, yd, tcx + dw, yd, lw * 1.4f, a(white, 0x90));
        // Tours de verre : lointaines (hauteur en fraction de h), puis proches.
        float[][] far = {{0f, .16f, .62f}, {.1f, .12f, .5f}, {.2f, .1f, .4f}, {.7f, .1f, .42f}, {.79f, .12f, .52f}, {.88f, .14f, .66f}};
        for (float[] b : far) glass(w * b[0], ground, w * b[1], h * b[2], 0.6f, false, q, 1);
        for (float[] b : MEGA_NEAR) glass(w * b[0], ground, w * b[1], h * b[2], b[3], true, q, 1);
        // Enseignes verticales sans texte sur les flancs proches.
        enseigne(w * 0.112f, h * 0.36f, q * 0.5f, h * 0.3f, 1, q);
        enseigne(w * 0.205f, h * 0.62f, q * 0.45f, h * 0.2f, 2, q);
        enseigne(w * 0.86f, h * 0.3f, q * 0.5f, h * 0.34f, 3, q);
        enseigne(w * 0.755f, h * 0.6f, q * 0.45f, h * 0.22f, 0, q);
        // Fils en travers.
        fil(0, h * 0.16f, w, h * 0.22f, h * 0.05f, lw);
        fil(0, h * 0.19f, w, h * 0.27f, h * 0.05f, lw);
        fil(0, h * 0.48f, w * 0.32f, h * 0.44f, h * 0.03f, lw);
        fil(w * 0.68f, h * 0.44f, w, h * 0.5f, h * 0.03f, lw);
        // Rue : sol sombre, filet d'or et reflets verticaux des enseignes.
        box(0, ground, w, h, a(0xFF080604, 0xF8));
        line(0, ground, w, ground, lw * 1.5f, a(fold, 0x50));
        float[] rx = {0.125f, 0.22f, 0.875f, 0.77f};
        int[] rc2 = {0xFFE0452B, fold, 0xFFFFE2A0, sh[3]};
        for (int i = 0; i < 4; i++) vgrad(w * rx[i] - q * 0.25f, ground, w * rx[i] + q * 0.25f, h, a(rc2[i], 0x30), a(rc2[i], 0));
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

    /** Vermillon des torii. */
    private static final int VERM = 0xFFD8442E;

    /** Repères de la manette Torii, partagés avec PadMotion : {piliers gauche et droit, haut, nuki, bas du nuki, socle, demi-largeur du pilier}. */
    static float[] toriiPadPts(float w, float h) {
        float m = Math.min(w, h);
        return new float[] {w * 0.31f, w * 0.69f, h * 0.13f, h * 0.34f, h * 0.34f + m * 0.045f, h * 0.86f, m * 0.042f};
    }

    /**
     * Torii, manette (0.66.21) : le torii vermillon vu du chemin, de près. Chapeau noir aux bouts relevés, linteau
     * et plaque au centre, nuki qui dépasse, piliers sur socles ; derrière, la lune rouge en facettes et ses
     * nervures d'éventail, nuages dorés sur les côtés, forêt de conifères ; chemin dallé, lanternes de pierre,
     * branches de pin aux coins du haut, feuilles d'érable. Lanternes de papier, braises et feuilles qui tombent : PadMotion.
     */
    private void japonPad(float w, float h) {
        float u = Math.max(w, h), m = Math.min(w, h), lw = Math.max(1f, u / 900f);
        float[] T = toriiPadPts(w, h);
        float xl = T[0], xr = T[1], top = T[2], ny = T[3], base = T[5], pw = T[6];
        float mx = w * 0.5f, my = h * 0.53f, mr = m * 0.25f;
        vgrad(0, 0, w, h, a(0xFF1E0C0D, 0x60), a(0xFF070304, 0x90));
        glow(mx, my, m * 0.9f, sh[2], 0x22);
        for (int i = 0; i < 24; i++) star(w * rnd(), h * 0.45f * rnd(), u * (0.0011f + 0.0012f * rnd()), 0x20 + Math.round(rnd() * 0x30));
        for (int i = 0; i < 30; i++) {
            double ang = Math.PI * 2 * i / 30;
            line(mx + (float) Math.cos(ang) * mr * 1.25f, my + (float) Math.sin(ang) * mr * 1.25f,
                    mx + (float) Math.cos(ang) * u, my + (float) Math.sin(ang) * u, lw, a(fold, 0x0E));
        }
        float q = m * 0.07f;
        for (int j = 0; j < 3; j++) {
            float r = q * (1f + 0.2f * j), yy = h * (0.2f + 0.11f * j);
            for (float x = (j & 1) * r - r; x < w + r; x += r * 2) {
                float edge = Math.min(1f, Math.abs(x / w - 0.5f) * 2.6f);
                if (edge < 0.35f) continue;
                arcLine(x, yy, r, 180, 180, lw, a(fold, Math.round(0x24 * edge)));
                arcLine(x, yy, r * 0.66f, 180, 180, lw, a(fold, Math.round(0x16 * edge)));
            }
        }
        disc(mx, my, mr * 1.45f, a(sh[2], 0x0E));
        disc(mx, my, mr * 1.2f, a(sh[2], 0x16));
        for (int i = 0; i < 24; i++) {
            double t0 = Math.PI * 2 * i / 24, t1 = Math.PI * 2 * (i + 1) / 24;
            boolean left = Math.cos((t0 + t1) / 2) < 0;
            tri(mx, my, mx + (float) Math.cos(t0) * mr, my + (float) Math.sin(t0) * mr,
                    mx + (float) Math.cos(t1) * mr, my + (float) Math.sin(t1) * mr, a(sh[left ? 1 : 2], (i & 1) == 0 ? 0x70 : 0x5C));
        }
        arcLine(mx, my, mr, 0, 360, lw * 1.4f, a(fold, 0x44));
        // Forêt : deux rangs de conifères.
        for (int r = 0; r < 2; r++) {
            float step = m * (0.05f + 0.012f * r), fb = h * (0.72f + 0.06f * r);
            int col = r == 1 ? a(0xFF170909, 0xF6) : a(0xFF2E1618, 0xE0);
            box(0, fb, w, h, col);
            for (float x = -step + (r == 1 ? step * 0.5f : 0); x < w + step; x += step) conifer(x, fb, m * (0.12f + 0.06f * r) * (0.7f + 0.6f * rnd()), col);
        }
        // Chemin dallé vers le torii.
        float gy = h * 0.86f;
        poly(a(0xFF1F1112, 0xF4), w * 0.36f, h, w * 0.64f, h, w * 0.55f, gy, w * 0.45f, gy);
        for (int i = 1; i < 6; i++) {
            float t = i / 6f, y = gy + (h - gy) * t * t, hw = w * (0.05f + 0.09f * t * t);
            line(w * 0.5f - hw, y, w * 0.5f + hw, y, lw, a(sh[3], 0x30));
        }
        line(w * 0.5f, gy, w * 0.5f, h, lw, a(sh[3], 0x18));
        // Piliers : face éclairée à droite, ombre à gauche, socle noir et bague de cuivre.
        float pt = top + m * 0.06f;
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? xl : xr;
            poly(a(Theme.darken(VERM, 0.45f), 0xFC), x - pw, base, x, base, x, pt, x - pw * 0.82f, pt);
            poly(a(VERM, 0xFC), x, base, x + pw, base, x + pw * 0.82f, pt, x, pt);
            line(x + pw * 0.75f, base, x + pw * 0.6f, pt, lw, a(Theme.lighten(VERM, 0.45f), 0x70));
            box(x - pw * 1.35f, base - m * 0.02f, x + pw * 1.35f, base + m * 0.04f, a(0xFF0A0505, 0xFC));
            line(x - pw * 1.35f, base - m * 0.02f, x + pw * 1.35f, base - m * 0.02f, lw, a(fold, 0x60));
            box(x - pw * 1.05f, base - m * 0.07f, x + pw * 1.05f, base - m * 0.045f, a(0xFF2A1408, 0xFC));
            line(x - pw * 1.05f, base - m * 0.07f, x + pw * 1.05f, base - m * 0.07f, lw, a(fold, 0x80));
        }
        // Nuki et ses coins.
        float nh = m * 0.045f, ext = m * 0.09f;
        box(xl - ext, ny, xr + ext, ny + nh, a(VERM, 0xFC));
        box(xl - ext, ny + nh * 0.6f, xr + ext, ny + nh, a(Theme.darken(VERM, 0.35f), 0xFC));
        line(xl - ext, ny, xr + ext, ny, lw * 1.2f, a(Theme.lighten(VERM, 0.45f), 0x80));
        for (int i = 0; i < 2; i++) {
            float x = i == 0 ? xl : xr;
            box(x + pw * 0.9f, ny - nh * 0.2f, x + pw * 1.5f, ny + nh * 1.2f, a(0xFF0A0505, 0xF0));
        }
        // Plaque centrale.
        float th = m * 0.055f;
        box(w * 0.5f - m * 0.035f, top + th, w * 0.5f + m * 0.035f, ny, a(Theme.darken(VERM, 0.3f), 0xFC));
        box(w * 0.5f - m * 0.05f, top + th + m * 0.025f, w * 0.5f + m * 0.05f, ny - m * 0.02f, a(0xFF0E0606, 0xFA));
        line(w * 0.5f - m * 0.05f, top + th + m * 0.025f, w * 0.5f + m * 0.05f, top + th + m * 0.025f, lw, a(fold, 0x70));
        // Linteau rouge puis chapeau noir aux bouts relevés.
        float span = xr - xl, L = span * 0.5f + m * 0.16f, cx = w * 0.5f;
        box(cx - span * 0.5f - m * 0.08f, top + th * 0.55f, cx + span * 0.5f + m * 0.08f, top + th * 1.15f, a(VERM, 0xFC));
        path.reset();
        path.moveTo(cx - L - m * 0.04f, top - m * 0.06f);
        path.quadTo(cx - L * 0.6f, top + th * 0.2f, cx, top + th * 0.25f);
        path.quadTo(cx + L * 0.6f, top + th * 0.2f, cx + L + m * 0.04f, top - m * 0.06f);
        path.lineTo(cx + L * 0.9f, top + th * 0.6f);
        path.quadTo(cx, top + th * 0.9f, cx - L * 0.9f, top + th * 0.6f);
        path.close();
        p.setColor(a(0xFF120607, 0xFC));
        c.drawPath(path, p);
        path.reset();
        path.moveTo(cx - L - m * 0.04f, top - m * 0.06f);
        path.quadTo(cx - L * 0.6f, top + th * 0.2f, cx, top + th * 0.25f);
        path.quadTo(cx + L * 0.6f, top + th * 0.2f, cx + L + m * 0.04f, top - m * 0.06f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(lw * 1.4f);
        p.setColor(a(Theme.lighten(VERM, 0.4f), 0x90));
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        // Lanternes de pierre aux bords bas.
        for (int i = 0; i < 2; i++) {
            float x = w * (i == 0 ? 0.1f : 0.9f), b = h * 0.95f, s2 = m * 0.06f;
            int st = a(0xFF0B0707, 0xFA);
            box(x - s2 * 0.8f, b - s2 * 0.35f, x + s2 * 0.8f, b, st);
            box(x - s2 * 0.22f, b - s2 * 1.4f, x + s2 * 0.22f, b - s2 * 0.35f, st);
            box(x - s2 * 0.75f, b - s2 * 1.65f, x + s2 * 0.75f, b - s2 * 1.4f, a(0xFF0E0909, 0xFA));
            box(x - s2 * 0.5f, b - s2 * 2.35f, x + s2 * 0.5f, b - s2 * 1.65f, st);
            box(x - s2 * 0.3f, b - s2 * 2.2f, x + s2 * 0.3f, b - s2 * 1.8f, a(fold, 0x70));
            poly(st, x - s2 * 1.05f, b - s2 * 2.35f, x, b - s2 * 2.95f, x + s2 * 1.05f, b - s2 * 2.35f);
            line(x, b - s2 * 2.95f, x + s2 * 1.05f, b - s2 * 2.35f, lw, a(sh[3], 0x40));
            disc(x, b - s2 * 3.05f, s2 * 0.14f, st);
        }
        // Branches de pin aux coins du haut.
        for (int sd = 0; sd < 2; sd++) {
            float x0 = sd == 0 ? 0 : w, dir = sd == 0 ? 1 : -1;
            for (int k2 = 0; k2 < 3; k2++) {
                float y = h * (0.06f + 0.08f * k2), len = m * (0.32f - 0.06f * k2);
                line(x0, y, x0 + dir * len, y + m * 0.04f, lw * 2.4f, a(0xFF0A0405, 0xF8));
                for (int j = 0; j < 5; j++) {
                    float px = x0 + dir * len * (0.3f + 0.15f * j), py = y + m * 0.04f * (0.3f + 0.15f * j);
                    for (int n = 0; n < 7; n++) {
                        double ang = -Math.PI / 2 + (n - 3) * 0.32;
                        line(px, py, px + (float) Math.cos(ang) * m * 0.035f, py + (float) Math.sin(ang) * m * 0.035f + m * 0.02f, lw * 0.9f, a(0xFF0F0607, 0xF0));
                    }
                }
            }
        }
        // Feuilles d'érable au sol.
        for (int i = 0; i < 14; i++) {
            float x = w * (0.25f + 0.5f * rnd()), y = h * (0.88f + 0.1f * rnd()), s2 = m * 0.012f;
            for (int k2 = 0; k2 < 5; k2++) {
                double ang = -Math.PI / 2 + (k2 - 2) * 0.55;
                float ex = x + (float) Math.cos(ang) * s2 * 2, ey = y + (float) Math.sin(ang) * s2 * 2;
                float nx = -(float) Math.sin(ang) * s2 * 0.5f, nyy = (float) Math.cos(ang) * s2 * 0.5f;
                poly(a((k2 & 1) == 1 ? sh[2] : fold, 0x60), x, y, (x + ex) / 2 + nx, (y + ey) / 2 + nyy, ex, ey, (x + ex) / 2 - nx, (y + ey) / 2 - nyy);
            }
        }
    }

    /**
     * Hanami, manette (0.66.23) : l'eau de près. Le pont de lune et son reflet ferment un cercle autour de la lune
     * pâle, deux branches de cerisier pendent des coins du haut. Ondulations et pétales : PadMotion.
     */
    private void rivierePad(float w, float h) {
        float u = Math.max(w, h), m = Math.min(w, h), lw = Math.max(1f, u / 900f), q = m * 0.07f;
        vgrad(0, 0, w, h, a(0xFF1A1222, 0xF0), a(0xFF07050A, 0xFC));
        float mx = w * 0.5f, my = h * 0.5f, mr = m * 0.13f;
        glow(mx, my, m * 0.5f, sh[3], 0x1A);
        disc(mx, my - m * 0.05f, mr, a(sh[4], 0x40));
        disc(mx - mr * 0.25f, my - m * 0.05f - mr * 0.1f, mr * 0.7f, a(sh[4], 0x18));
        float R = m * 0.3f, rr = R + q * 0.22f;
        arcLine(mx, my, R, 185.4f, 169.2f, q * 0.45f, a(0xFF0B070C, 0xFA));
        arcLine(mx, my, rr, 185.4f, 169.2f, lw * 1.4f, a(sh[3], 0x90));
        for (int i = 0; i <= 12; i++) {
            double ang = Math.PI * (1.06 + 0.88 * i / 12);
            float x = mx + (float) Math.cos(ang) * rr, y = my + (float) Math.sin(ang) * rr;
            line(x, y, x, y - q * 0.5f, lw, a(sh[2], 0x50));
        }
        // Reflet de l'arche en tirets.
        p.setPathEffect(new android.graphics.DashPathEffect(new float[] {q * 0.4f, q * 0.25f}, 0));
        arcLine(mx, my, rr, 9f, 162f, lw * 1.4f, a(sh[3], 0x30));
        p.setPathEffect(null);
        line(0, my, w, my, lw, a(sh[3], 0x30));
        grandCerisier(-q * 0.5f, q * 0.3f, m * 0.28f, 0.18, q * 0.5f, 3, q * 0.7f, 1f);
        grandCerisier(w + q * 0.5f, q * 0.3f, m * 0.28f, Math.PI - 0.18, q * 0.5f, 3, q * 0.7f, 1f);
    }

    // ------------------------------------------------------------ Néon, Jade, Argent (0.66)

    /** Néon : 3 ruelle de Tokyo sous la pluie (fond), 4 bitume mouillé et reflets (manette assortie). */
    static void neon(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 59);
        if (variant == MANETTE) f.ruellePad(w, h);
        else f.ruelle(w, h);
    }

    /** Jade : 3 temple de montagne dans la brume (fond), 4 bambouseraie et lanternes (manette assortie). */
    static void jade(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 67);
        if (variant == MANETTE) f.bambouPad(w, h);
        else f.temple(w, h);
    }

    /** Argent : 3 pleine lune et grues sur la mer de nuages (fond), 4 grue dépliée sous la lune (manette assortie). */
    static void argent(Canvas c, float w, float h, int accent, int fold, float k, int variant) {
        Facets f = new Facets(c, accent, fold, k, 73);
        if (variant == MANETTE) f.plisPad(w, h);
        else f.lune(w, h);
    }

    /** Trait au néon : halo large et pâle, puis tube clair au centre. */
    private void tube(float x1, float y1, float x2, float y2, float lw, int col, int alpha) {
        line(x1, y1, x2, y2, lw * 7f, a(col, alpha / 9));
        line(x1, y1, x2, y2, lw * 3.2f, a(col, alpha / 4));
        line(x1, y1, x2, y2, lw * 1.3f, a(Theme.lighten(col, 0.5f), alpha));
    }

    private void tubeArc(float cx, float cy, float r, float start, float sweep, float lw, int col, int alpha) {
        arcLine(cx, cy, r, start, sweep, lw * 7f, a(col, alpha / 9));
        arcLine(cx, cy, r, start, sweep, lw * 3.2f, a(col, alpha / 4));
        arcLine(cx, cy, r, start, sweep, lw * 1.3f, a(Theme.lighten(col, 0.5f), alpha));
    }

    private void tubeRect(float x0, float y0, float x1, float y1, float rad, float lw, int col, int alpha) {
        RectF r = new RectF(x0, y0, x1, y1);
        p.setStyle(Paint.Style.STROKE);
        float[] wd = {lw * 7f, lw * 3.2f, lw * 1.3f};
        int[] al = {alpha / 9, alpha / 4, alpha};
        for (int i = 0; i < 3; i++) {
            p.setStrokeWidth(wd[i]);
            p.setColor(a(i == 2 ? Theme.lighten(col, 0.5f) : col, al[i]));
            c.drawRoundRect(r, rad, rad, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void glow(float cx, float cy, float r, int col, int alpha) {
        p.setShader(new android.graphics.RadialGradient(cx, cy, Math.max(1f, r), a(col, alpha), a(col, 0), Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
    }

    // ---- Néon : ruelle de Tokyo la nuit, sous la pluie

    /**
     * Enseignes de la ruelle, partagées avec BackdropMotion (scintillement) : pour chacune x0, y0, x1, y1,
     * couleur (0 magenta, 1 cyan), motif, profondeur. Enseignes en drapeau, face au regard, sans aucun texte.
     */
    static float[] neonSigns(float w, float h) {
        // côté (0 gauche, 1 droite), profondeur, haut (fraction de h, au premier plan), largeur et hauteur (unités m), couleur, motif
        float[][] t = {
            {1, 0.06f, 0.08f, 3.0f, 8.6f, 0, 0}, {1, 0.30f, 0.16f, 2.6f, 6.8f, 1, 1}, {1, 0.47f, 0.03f, 2.4f, 7.6f, 0, 2},
            {1, 0.60f, 0.30f, 2.2f, 4.6f, 1, 3}, {1, 0.69f, 0.10f, 2.0f, 6.0f, 0, 4},
            {0, 0.14f, 0.24f, 2.5f, 5.6f, 1, 3}, {0, 0.40f, 0.10f, 2.3f, 6.6f, 0, 1}, {0, 0.61f, 0.30f, 2.0f, 4.4f, 1, 0},
        };
        float vx = w * 0.56f, vy = h * 0.5f, m = Math.min(w * 0.9f, h) * 0.05f;
        float[] out = new float[t.length * 7];
        for (int i = 0; i < t.length; i++) {
            float s = t[i][1], sc = 1f - s, y0n = h * t[i][2];
            float wx = t[i][0] == 0 ? vx * s : w + (vx - w) * s;
            float y0 = y0n + (vy - y0n) * s, sw = m * t[i][3] * sc, shh = m * t[i][4] * sc, gap = m * 0.25f * sc;
            float x0 = t[i][0] == 0 ? wx + gap : wx - gap - sw;
            out[i * 7] = x0;
            out[i * 7 + 1] = y0;
            out[i * 7 + 2] = x0 + sw;
            out[i * 7 + 3] = y0 + shh;
            out[i * 7 + 4] = t[i][5];
            out[i * 7 + 5] = t[i][6];
            out[i * 7 + 6] = s;
        }
        return out;
    }

    /** Motif lumineux d'une enseigne (jamais de lettres) : anneau, chevrons, zigzag, losanges, ondes. */
    private void signMotif(float x0, float y0, float x1, float y1, int motif, float lw, int col, int col2) {
        float cw = x1 - x0, ch = y1 - y0, cx = (x0 + x1) / 2, ins = cw * 0.2f;
        if (motif == 0) {
            float r = cw * 0.28f;
            tubeArc(cx, y0 + ch * 0.28f, r, 0, 360, lw, col2, 0xC0);
            disc(cx, y0 + ch * 0.28f, r * 0.3f, a(Theme.lighten(col2, 0.5f), 0xA0));
            for (int i = 0; i < 3; i++) {
                float yy = y0 + ch * (0.58f + 0.12f * i);
                tube(x0 + ins, yy, x1 - ins, yy, lw, col, 0xB0);
            }
        } else if (motif == 1) {
            int n = 4;
            for (int i = 0; i < n; i++) {
                float yy = y0 + ch * (0.16f + 0.2f * i), d = cw * 0.22f;
                tube(x0 + ins, yy, cx, yy + d, lw, i % 2 == 0 ? col : col2, 0xB8);
                tube(cx, yy + d, x1 - ins, yy, lw, i % 2 == 0 ? col : col2, 0xB8);
            }
        } else if (motif == 2) {
            int n = 7;
            float px0 = cx, py0 = y0 + ch * 0.1f;
            for (int i = 1; i <= n; i++) {
                float yy = y0 + ch * (0.1f + 0.8f * i / n), xx = (i & 1) == 1 ? x0 + ins : x1 - ins;
                tube(px0, py0, xx, yy, lw, col, 0xB8);
                px0 = xx;
                py0 = yy;
            }
            disc(cx, y0 + ch * 0.06f, cw * 0.06f, a(Theme.lighten(col2, 0.4f), 0xB0));
        } else if (motif == 3) {
            for (int i = 0; i < 2; i++) {
                float my = y0 + ch * (0.3f + 0.4f * i), d = Math.min(cw * 0.32f, ch * 0.18f);
                tube(cx, my - d, cx + d, my, lw, i == 0 ? col : col2, 0xC0);
                tube(cx + d, my, cx, my + d, lw, i == 0 ? col : col2, 0xC0);
                tube(cx, my + d, cx - d, my, lw, i == 0 ? col : col2, 0xC0);
                tube(cx - d, my, cx, my - d, lw, i == 0 ? col : col2, 0xC0);
            }
        } else {
            for (int j = 0; j < 4; j++) {
                float yy = y0 + ch * (0.18f + 0.2f * j), amp = ch * 0.03f;
                float px0 = x0 + ins, py0 = yy;
                for (int i = 1; i <= 8; i++) {
                    float xx = x0 + ins + (cw - 2 * ins) * i / 8f, y2 = yy + ((i & 1) == 1 ? -amp : amp);
                    tube(px0, py0, xx, y2, lw * 0.9f, j % 2 == 0 ? col2 : col, 0xA8);
                    px0 = xx;
                    py0 = y2;
                }
            }
        }
    }

    /**
     * Ruelle de Tokyo la nuit, sous la pluie, en perspective : murs serrés de part et d'autre, enseignes en
     * drapeau magenta et cyan (motifs sans texte), fils tendus au-dessus de la rue, ville lointaine qui rougeoie
     * au bout, et le bitume mouillé où chaque enseigne se reflète en traits brisés. Le centre reste sombre.
     */
    private void ruelle(float w, float h) {
        float u = Math.max(w, h), m = Math.min(w * 0.9f, h) * 0.05f, lw = Math.max(1f, u / 1100f);
        float vx = w * 0.56f, vy = h * 0.5f, gN = h * 0.95f, sEnd = 0.8f;
        int mag = sh[2], cyan = fold;
        float xL = vx * sEnd, xR = w + (vx - w) * sEnd, gF = gN + (vy - gN) * sEnd;
        // Ville au bout de la ruelle : lueur, tours lointaines aux fenêtres allumées.
        glow(vx, gF, (xR - xL) * 1.6f + m * 4, mag, 0x40);
        glow(vx + (xR - xL) * 0.3f, gF - m * 2, (xR - xL) * 0.9f + m * 2, cyan, 0x1C);
        for (int i = 0; i < 26; i++) star(w * rnd(), vy * 0.8f * rnd(), u * (0.0009f + 0.001f * rnd()), 0x14 + Math.round(rnd() * 0x24));
        float bx = xL - m;
        while (bx < xR + m) {
            float bw = m * (0.5f + 0.9f * rnd()), bh = (gF - vy * 0.55f) * (0.3f + 0.7f * rnd());
            box(bx, gF - bh, bx + bw, gF + 1, a(0xFF0B0812, 0xF4));
            for (int j = 0; j < 14; j++) {
                if (rnd() < 0.55f) continue;
                float wy = gF - bh + m * 0.3f + rnd() * (bh - m * 0.5f), wx = bx + m * 0.1f + rnd() * Math.max(1f, bw - m * 0.25f);
                box(wx, wy, wx + m * 0.08f, wy + m * 0.1f, a(rnd() < 0.5f ? sh[3] : cyan, 0x30 + Math.round(rnd() * 0x30)));
            }
            if (rnd() < 0.3f) line(bx + bw * 0.5f, gF - bh, bx + bw * 0.5f, gF - bh - m * 0.8f, lw, a(mag, 0x50));
            bx += bw * (0.75f + 0.3f * rnd());
        }
        // Murs : immeubles en enfilade, toits à hauteurs variées, deux tons par côté.
        float[] br = {0f, 0.22f, 0.4f, 0.53f, 0.63f, 0.72f, sEnd};
        float[][] roofs = {{-0.25f, -0.04f, 0.1f, -0.02f, 0.16f, 0.06f}, {-0.42f, -0.14f, 0.02f, -0.08f, 0.12f, 0.0f}};
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < br.length - 1; i++) {
                float sa = br[i], sb = br[i + 1], y0n = h * roofs[side][i];
                float xa = side == 0 ? vx * sa : w + (vx - w) * sa, xb = side == 0 ? vx * sb : w + (vx - w) * sb;
                float ra = y0n + (vy - y0n) * sa, rb = y0n + (vy - y0n) * sb;
                float ga = gN + (vy - gN) * sa, gb = gN + (vy - gN) * sb;
                int col = side == 0 ? ((i & 1) == 0 ? 0xFF0E0A14 : 0xFF130D1B) : ((i & 1) == 0 ? 0xFF140E1D : 0xFF1A1226);
                poly(a(col, 0xFC), xa, ra, xb, rb, xb, gb, xa, ga);
                // Façade du bout de l'immeuble suivant, plus haut : un pan face au regard.
                if (i + 1 < br.length - 1 && roofs[side][i + 1] < roofs[side][i]) {
                    float y1n = h * roofs[side][i + 1], rb2 = y1n + (vy - y1n) * sb;
                    float fw = (side == 0 ? 1 : -1) * m * 1.2f * (1f - sb);
                    poly(a(side == 0 ? 0xFF181020 : 0xFF1E1529, 0xFC), xb, rb2, xb + fw, rb2, xb + fw, rb, xb, rb);
                    line(xb, rb2, xb + fw, rb2, lw, a(mag, 0x40));
                }
                line(xa, ra, xb, rb, lw * 1.2f, a(side == 0 ? cyan : mag, 0x48));
                line(xb, rb, xb, gb, lw, a(0xFF000000, 0x80));
                // Fenêtres en perspective : la plupart éteintes, quelques-unes tièdes ou teintées.
                int floors = Math.max(3, Math.round((h * 0.9f - Math.max(0, y0n)) / (m * 2.2f)));
                for (int f2 = 0; f2 < floors; f2++) {
                    float yA = Math.max(y0n, 0) + m * 1.2f + f2 * m * 2.2f, yB = yA + m * 1.0f;
                    if (yB > gN - m * 3f) break;
                    for (int cc = 0; cc < 3; cc++) {
                        float s1 = sa + (sb - sa) * (0.14f + 0.3f * cc), s2 = s1 + (sb - sa) * 0.16f;
                        float x1 = side == 0 ? vx * s1 : w + (vx - w) * s1, x2 = side == 0 ? vx * s2 : w + (vx - w) * s2;
                        float lit = rnd();
                        int wc = lit < 0.08f ? 0xFFFFD9A8 : lit < 0.11f ? mag : lit < 0.14f ? cyan : 0xFF05040A;
                        int al = lit < 0.14f ? 0x26 + Math.round(rnd() * 0x22) : 0x70;
                        poly(a(wc, al), x1, yA + (vy - yA) * s1, x2, yA + (vy - yA) * s2, x2, yB + (vy - yB) * s2, x1, yB + (vy - yB) * s1);
                    }
                }
                // Bande lumineuse le long du mur, au-dessus d'une échoppe.
                if ((i + side) % 2 == 0 && i < 5) {
                    float yn = h * (0.66f + 0.05f * side), s1 = sa + (sb - sa) * 0.1f, s2 = sb - (sb - sa) * 0.1f;
                    float x1 = side == 0 ? vx * s1 : w + (vx - w) * s1, x2 = side == 0 ? vx * s2 : w + (vx - w) * s2;
                    tube(x1, yn + (vy - yn) * s1, x2, yn + (vy - yn) * s2, lw * (1.4f - s1), (i & 1) == 0 ? cyan : mag, 0x90);
                    float yd = h * 0.84f;
                    poly(a((i & 1) == 0 ? cyan : mag, 0x10), x1, yn + (vy - yn) * s1, x2, yn + (vy - yn) * s2, x2, yd + (vy - yd) * s2, x1, yd + (vy - yd) * s1);
                }
            }
        }
        // Tuyaux et climatiseurs accrochés aux murs.
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 3; i++) {
                float s = 0.1f + 0.22f * i + 0.05f * side, sc = 1f - s;
                float x = side == 0 ? vx * s : w + (vx - w) * s, yn = h * (0.4f + 0.1f * i);
                float y = yn + (vy - yn) * s, bw = m * 1.1f * sc, bh = m * 0.7f * sc, dx = side == 0 ? 0 : -bw;
                box(x + dx, y, x + dx + bw, y + bh, a(0xFF1C1626, 0xF0));
                line(x + dx, y, x + dx + bw, y, lw, a(cyan, 0x30));
                arcLine(x + dx + bw * 0.5f, y + bh * 0.5f, bh * 0.32f, 0, 360, lw, a(0xFF000000, 0x90));
            }
        }
        // Fils tendus au-dessus de la ruelle.
        for (int i = 0; i < 5; i++) {
            float s = 0.05f + 0.15f * i, y1n = h * (0.06f + 0.05f * i), y2n = h * (0.02f + 0.07f * (4 - i) / 4f);
            float x1 = vx * s, x2 = w + (vx - w) * s, y1 = y1n + (vy - y1n) * s, y2 = y2n + (vy - y2n) * s;
            float sag = (x2 - x1) * 0.08f;
            path.reset();
            path.moveTo(x1, y1);
            path.quadTo((x1 + x2) / 2, (y1 + y2) / 2 + sag * 2, x2, y2);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(lw * (1.6f - s));
            p.setColor(a(0xFF050308, 0xE0));
            c.drawPath(path, p);
            p.setStyle(Paint.Style.FILL);
        }
        // Enseignes : halo sur le mur, plaque sombre, tube de contour, motif.
        float[] sg = neonSigns(w, h);
        for (int i = 0; i < sg.length / 7; i++) {
            float x0 = sg[i * 7], y0 = sg[i * 7 + 1], x1 = sg[i * 7 + 2], y1 = sg[i * 7 + 3], s = sg[i * 7 + 6];
            int col = sg[i * 7 + 4] == 0 ? mag : cyan, col2 = sg[i * 7 + 4] == 0 ? cyan : mag;
            float sc = 1f - s, slw = lw * (0.6f + 1.2f * sc);
            glow((x0 + x1) / 2, (y0 + y1) / 2, (y1 - y0) * 0.9f, col, 0x2C);
            boolean left = x0 < vx;
            float wx = left ? x0 - m * 0.25f * sc : x1 + m * 0.25f * sc;
            line(wx, y0 + (y1 - y0) * 0.1f, left ? x0 : x1, y0 + (y1 - y0) * 0.1f, slw * 1.5f, a(0xFF050308, 0xE0));
            line(wx, y1 - (y1 - y0) * 0.1f, left ? x0 : x1, y1 - (y1 - y0) * 0.1f, slw * 1.5f, a(0xFF050308, 0xE0));
            RectF r = new RectF(x0, y0, x1, y1);
            p.setColor(a(0xFF100A16, 0xF4));
            c.drawRoundRect(r, m * 0.2f * sc, m * 0.2f * sc, p);
            tubeRect(x0 + m * 0.12f * sc, y0 + m * 0.12f * sc, x1 - m * 0.12f * sc, y1 - m * 0.12f * sc, m * 0.18f * sc, slw, col, 0xD0);
            signMotif(x0 + m * 0.12f * sc, y0 + m * 0.12f * sc, x1 - m * 0.12f * sc, y1 - m * 0.12f * sc, (int) sg[i * 7 + 5], slw, col, col2);
        }
        // Bitume mouillé.
        path.reset();
        path.moveTo(0, gN);
        path.lineTo(xL, gF);
        path.lineTo(xR, gF);
        path.lineTo(w, gN);
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.close();
        p.setShader(new LinearGradient(0, gF, 0, h, a(0xFF120C1A, 0xFC), a(0xFF07050B, 0xFE), Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);
        line(0, gN, xL, gF, lw * 1.2f, a(cyan, 0x40));
        line(w, gN, xR, gF, lw * 1.2f, a(mag, 0x48));
        // Reflets : la ville lointaine au milieu, puis chaque enseigne, en traits brisés par les flaques.
        for (int i = 0; i < 22; i++) {
            float t = i / 22f, y = gF + (h - gF) * t * t + lw, ww = (xR - xL) * (0.35f + 0.5f * t) * (0.6f + 0.4f * rnd());
            box(vx - ww / 2 + (rnd() - 0.5f) * m, y, vx + ww / 2 + (rnd() - 0.5f) * m, y + Math.max(lw, m * 0.06f * (0.3f + t)), a(mag, Math.round(0x22 * (1f - 0.7f * t))));
        }
        for (int i = 0; i < sg.length / 7; i++) {
            float x0 = sg[i * 7], y0 = sg[i * 7 + 1], x1 = sg[i * 7 + 2], y1 = sg[i * 7 + 3], s = sg[i * 7 + 6];
            int col = sg[i * 7 + 4] == 0 ? mag : cyan;
            float gs = gN + (vy - gN) * s, len = Math.min(h - gs, (gs - y0) * 0.9f), cx = (x0 + x1) / 2, ww = (x1 - x0);
            int n = Math.max(6, Math.round(len / (m * 0.22f)));
            for (int j = 0; j < n; j++) {
                float t = j / (float) n, y = gs + len * t;
                if (y > h) break;
                float dw = ww * (0.3f + 0.6f * rnd()) * (1f - 0.3f * t), dx = (rnd() - 0.5f) * ww * 0.4f;
                box(cx + dx - dw / 2, y, cx + dx + dw / 2, y + Math.max(lw, len / n * 0.45f), a(col, Math.round(0x40 * (1f - t) * (0.5f + 0.5f * rnd()))));
            }
        }
        // Flaques : ellipses au liseré clair qui renvoient le ciel.
        for (int i = 0; i < 9; i++) {
            float t = 0.2f + 0.8f * rnd(), y = gF + (h - gF) * t, x = w * (0.08f + 0.84f * rnd());
            float rw = m * (1.2f + 3.5f * t), rh = rw * 0.12f;
            p.setShader(new android.graphics.RadialGradient(x, y, rw, a(i % 2 == 0 ? cyan : mag, 0x16), a(i % 2 == 0 ? cyan : mag, 0), Shader.TileMode.CLAMP));
            c.drawOval(new RectF(x - rw, y - rh, x + rw, y + rh), p);
            p.setShader(null);
            arcLine(x, y + rh * 6f, rw * 0.7f, 250, 40, lw, a(Theme.lighten(cyan, 0.3f), 0x24));
        }
        // Pluie fixe (le fond animé la fait tomber).
        if (!MOVING) {
            for (int i = 0; i < 160; i++) {
                float x = w * rnd(), y = h * rnd(), len = m * (0.6f + 1.2f * rnd());
                line(x, y, x - len * 0.18f, y + len, lw * 0.8f, a(rnd() < 0.3f ? cyan : 0xFFD8D0FF, 0x10 + Math.round(rnd() * 0x18)));
            }
        }
    }

    /**
     * Néon, manette : le bitume mouillé vu de plus près. Reflets d'enseignes en colonnes de traits brisés,
     * une enseigne ronde qui se reflète au milieu dans une flaque, ronds de pluie, filet de néon en haut.
     */
    private void ruellePad(float w, float h) {
        float u = Math.max(w, h), m = Math.min(w, h), lw = Math.max(1f, u / 900f);
        int mag = sh[2], cyan = fold;
        vgrad(0, 0, w, h, a(0xFF120C1A, 0x60), a(0xFF050308, 0x90));
        glow(w * 0.5f, h * 0.55f, m * 0.8f, mag, 0x1E);
        // Colonnes de reflets.
        float[] xs = {0.1f, 0.27f, 0.73f, 0.9f};
        for (int i = 0; i < xs.length; i++) {
            float cx = w * xs[i], ww = m * (0.07f + 0.05f * rnd());
            int col = i % 2 == 0 ? mag : cyan;
            vgrad(cx - ww * 0.5f, 0, cx + ww * 0.5f, h, a(col, 0x14), a(col, 0x02));
            int n = Math.round(h / (m * 0.06f));
            for (int j = 0; j < n; j++) {
                float t = j / (float) n, y = h * (0.02f + 0.96f * t);
                if (rnd() < 0.45f) continue;
                float dw = ww * (0.5f + 0.9f * rnd()), dx = (rnd() - 0.5f) * ww * 0.4f;
                box(cx + dx - dw / 2, y, cx + dx + dw / 2, y + Math.max(lw, m * 0.008f), a(col, Math.round((0x14 + 0x18 * rnd()) * (1f - 0.5f * t))));
            }
        }
        // L'enseigne ronde, reflétée : anneau et losange, tremblés en tirets.
        float cx = w * 0.5f, cy = h * 0.56f, R = m * 0.26f;
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            if (rnd() < 0.12f) continue;
            double t0 = Math.PI * 2 * i / seg, t1 = Math.PI * 2 * (i + 0.94) / seg;
            float d = (rnd() - 0.5f) * m * 0.012f;
            tube(cx + (float) Math.cos(t0) * R + d, cy + (float) Math.sin(t0) * R * 0.92f, cx + (float) Math.cos(t1) * R + d, cy + (float) Math.sin(t1) * R * 0.92f, lw, cyan, 0x90);
        }
        float dd = R * 0.5f;
        float[][] dia = {{0, -1, 1, 0}, {1, 0, 0, 1}, {0, 1, -1, 0}, {-1, 0, 0, -1}};
        for (float[] e : dia) {
            for (int k2 = 0; k2 < 4; k2++) {
                float t0 = k2 / 4f, t1 = (k2 + 0.9f) / 4f, d = (rnd() - 0.5f) * m * 0.01f;
                tube(cx + dd * (e[0] + (e[2] - e[0]) * t0) + d, cy + dd * (e[1] + (e[3] - e[1]) * t0),
                        cx + dd * (e[0] + (e[2] - e[0]) * t1) + d, cy + dd * (e[1] + (e[3] - e[1]) * t1), lw, mag, 0x88);
            }
        }
        for (int i = 0; i < 7; i++) {
            float y = cy + R * (1.15f + 0.22f * i), ww2 = R * (0.9f - 0.1f * i) * (0.7f + 0.5f * rnd());
            box(cx - ww2, y, cx + ww2, y + Math.max(lw, m * 0.01f), a(i % 2 == 0 ? cyan : mag, Math.round(0x30 * (1f - i / 8f))));
        }
        // Les ronds de pluie sont animés dans PadMotion (0.66.21) : chaque goutte qui touche le sol en fait.
        // Pluie fine.
        for (int i = 0; i < 70; i++) {
            float x = w * rnd(), y = h * rnd(), len = m * (0.03f + 0.05f * rnd());
            line(x, y, x - len * 0.2f, y + len, lw * 0.8f, a(0xFFD8D0FF, 0x14 + Math.round(rnd() * 0x14)));
        }
        // Filet de néon le long du bord haut, coupé au milieu pour le bouton origami.
        float in = m * 0.045f;
        tube(in * 2, in, w * 0.4f, in, lw, cyan, 0x90);
        tube(w * 0.6f, in, w - in * 2, in, lw, cyan, 0x90);
        tube(in * 2, h - in, w - in * 2, h - in, lw, mag, 0x60);
    }

    // ---- Jade : temple de montagne dans la brume

    /** Banc de brume : bande pâle qui s'estompe vers le haut et vers le bas. */
    private void brume(float w, float y, float hb, int alpha) {
        vgrad(0, y - hb, w, y, a(0xFFB6D8D0, 0), a(0xFFB6D8D0, alpha));
        vgrad(0, y, w, y + hb * 0.6f, a(0xFFB6D8D0, alpha), a(0xFFB6D8D0, 0));
    }

    /** Bambou : tige à deux faces, nœuds clairs, bouquets de feuilles aux nœuds du haut. */
    private void bambou(float x, float y0, float y1, float wd, float tilt, float node, int shade, int alpha, float lw, boolean leaves) {
        float dx = tilt * (y1 - y0);
        poly(a(0xFF07100C, alpha), x - wd / 2, y1, x, y1, x + dx, y0, x + dx - wd / 2, y0);
        poly(a(Theme.blend(0xFF0C1712, sh[0], 0.4f), alpha), x, y1, x + wd / 2, y1, x + dx + wd / 2, y0, x + dx, y0);
        line(x + wd * 0.35f, y1, x + dx + wd * 0.35f, y0, lw, a(sh[shade], alpha / 5));
        int n = 0;
        for (float y = y1 - node * (0.6f + 0.4f * rnd()); y > y0; y -= node * (0.85f + 0.3f * rnd()), n++) {
            float xx = x + dx * (y1 - y) / (y1 - y0);
            line(xx - wd * 0.62f, y, xx + wd * 0.62f, y, Math.max(lw, wd * 0.12f), a(0xFF050B08, alpha));
            line(xx - wd * 0.5f, y - wd * 0.12f, xx + wd * 0.5f, y - wd * 0.12f, lw, a(sh[shade + 1], alpha / 3));
            if (leaves && rnd() < 0.55f) {
                int nl = 2 + (int) (rnd() * 3);
                double base = rnd() < 0.5f ? Math.PI * 0.15 : Math.PI * 0.85;
                for (int k2 = 0; k2 < nl; k2++) {
                    double ang = base + (rnd() - 0.5) * 0.9 + (base > 1 ? 0.2 : -0.2);
                    float len = wd * (2.6f + 2.2f * rnd());
                    kite(xx, y, len, len * 0.14f, ang, 0, 1, Math.round(alpha * (0.3f + 0.25f * rnd())), false);
                }
            }
        }
    }

    /** Lanterne de papier : halo chaud, corps ovale, côtes et coiffes sombres. */
    private void lanterne(float x, float y, float r, float lw, int alpha) {
        glow(x, y, r * 4.5f, fold, alpha / 3);
        RectF o = new RectF(x - r * 0.8f, y - r, x + r * 0.8f, y + r);
        p.setColor(a(Theme.blend(fold, 0xFFFF6A3C, 0.25f), alpha));
        c.drawOval(o, p);
        p.setColor(a(Theme.lighten(fold, 0.5f), alpha * 2 / 3));
        c.drawOval(new RectF(x - r * 0.35f, y - r * 0.6f, x + r * 0.35f, y + r * 0.6f), p);
        for (int i = -1; i <= 1; i++) line(x - r * 0.75f, y + i * r * 0.45f, x + r * 0.75f, y + i * r * 0.45f, lw * 0.8f, a(0xFF3A1A08, alpha / 2));
        box(x - r * 0.45f, y - r * 1.15f, x + r * 0.45f, y - r * 0.9f, a(0xFF0A0605, 0xF0));
        box(x - r * 0.45f, y + r * 0.9f, x + r * 0.45f, y + r * 1.15f, a(0xFF0A0605, 0xF0));
    }

    /** Abscisse du temple (fraction de la largeur), commune au temple, à l'éperon, à l'escalier et aux lanternes. */
    private static final float TEMPLE_X = 0.7f;

    /** Repères de l'escalier : départ au sol du premier plan, palier, puis pied de la porte (x, y trois fois). */
    private static float[] stairPts(float w, float h) {
        float q = Math.min(w, h * 1.2f) * 0.06f, bw = q * 3.4f, tx = w * TEMPLE_X;
        float yTop = templeY(w, h) + q * 0.5f, ya = h * 0.88f + q * 0.2f;
        return new float[] {tx - bw * 2.3f, ya, tx - bw * 0.9f, ya - (ya - yTop) * 0.55f, tx - bw * 0.02f, yTop};
    }

    /** Point de l'escalier au paramètre t de 0 (au sol) à 1 (à la porte), réparti par volée selon sa hauteur. */
    private static float[] stairAt(float[] sp, float t) {
        float k = t < 0.55f ? t / 0.55f : (t - 0.55f) / 0.45f;
        int o = t < 0.55f ? 0 : 1;
        return new float[] {sp[o * 2] + (sp[o * 2 + 2] - sp[o * 2]) * k, sp[o * 2 + 1] + (sp[o * 2 + 3] - sp[o * 2 + 1]) * k};
    }

    /** Repères des lanternes du temple (x, y, rayon), partagés avec BackdropMotion (halo qui respire). */
    static float[] jadePts(float w, float h) {
        float q = Math.min(w, h * 1.2f) * 0.06f;
        float tx = w * TEMPLE_X, ty = templeY(w, h);
        float[] out = new float[7 * 3];
        // Deux sous l'avant-toit, puis cinq posées sur des poteaux le long de l'escalier.
        out[0] = tx - q * 1.6f; out[1] = ty - q * 0.55f; out[2] = q * 0.22f;
        out[3] = tx + q * 1.6f; out[4] = ty - q * 0.55f; out[5] = q * 0.22f;
        float[] sp = stairPts(w, h);
        for (int i = 0; i < 5; i++) {
            float[] pt = stairAt(sp, (i + 0.6f) / 5.4f);
            out[6 + i * 3] = pt[0] - q * 0.45f;
            out[7 + i * 3] = pt[1] - q * 0.95f;
            out[8 + i * 3] = q * (0.2f - 0.014f * i);
        }
        return out;
    }

    private static float templeY(float w, float h) {
        float H0 = Math.min(h, w * 0.9f);
        return h * 0.82f - H0 * 0.4f;
    }

    /** Volée de marches : bande en biais entre deux points, marches en traits (plus serrées en haut), garde-corps. */
    private void volee(float xa, float ya, float xb, float yb, float hwa, float hwb, float q, float lw) {
        poly(a(0xFF1A2B25, 0xF0), xa - hwa, ya, xa + hwa, ya, xb + hwb, yb, xb - hwb, yb);
        int n = Math.max(6, Math.round((ya - yb) / (q * 0.3f)));
        for (int i = 1; i < n; i++) {
            float t = i / (float) n, x = xa + (xb - xa) * t, y = ya + (yb - ya) * t, hw = hwa + (hwb - hwa) * t;
            line(x - hw, y, x + hw, y, lw, a(sh[4], Math.round(0x40 * (1f - 0.4f * t))));
            line(x - hw, y + lw * 1.6f, x + hw, y + lw * 1.6f, lw, a(0xFF050B09, 0x90));
        }
        line(xa - hwa, ya, xb - hwb, yb, lw * 1.4f, a(sh[3], 0x50));
        line(xa + hwa, ya, xb + hwb, yb, lw * 1.4f, a(sh[2], 0x30));
        // Garde-corps côté haut : main courante et poteaux.
        line(xa - hwa, ya - q * 0.55f, xb - hwb, yb - q * 0.55f, lw, a(sh[3], 0x40));
        for (int i = 0; i <= 6; i++) {
            float t = i / 6f;
            line(xa - hwa + (xb - hwb - xa + hwa) * t, ya + (yb - ya) * t, xa - hwa + (xb - hwb - xa + hwa) * t, ya + (yb - ya) * t - q * 0.55f, lw, a(sh[3], 0x38));
        }
    }

    /** Crêtes de montagne en deux faces (gauche dans l'ombre), de profil donné par une somme de pics. */
    private void monts(float w, float h, float base, float[] peaks, int dark, int light, int alpha, float lw, int edgeAlpha) {
        int steps = 90;
        float[] ys = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps, hh = 0;
            for (int k2 = 0; k2 + 2 < peaks.length; k2 += 3) {
                float g = (t - peaks[k2]) / peaks[k2 + 1];
                hh = Math.max(hh, peaks[k2 + 2] * (float) Math.exp(-Math.abs(g) * 1.6f));
            }
            ys[i] = base - hh * (0.92f + 0.08f * (float) Math.sin(t * 40 + base));
        }
        path.reset();
        path.moveTo(0, h);
        for (int i = 0; i <= steps; i++) path.lineTo(w * i / steps, ys[i]);
        path.lineTo(w, h);
        path.close();
        p.setColor(a(dark, alpha));
        c.drawPath(path, p);
        // Faces éclairées : à droite de chaque crête, un pan plus clair.
        for (int i = 1; i < steps; i++) {
            if (ys[i] < ys[i - 1] && ys[i] <= ys[i + 1]) {
                float x = w * i / steps;
                int r = i + 1;
                while (r < steps && ys[r] >= ys[r - 1]) r++;
                float xr = w * r / steps;
                poly(a(light, alpha / 4), x, ys[i], xr, ys[r], x + (xr - x) * 0.35f, base + (h - base) * 0.2f);
                line(x, ys[i], xr, ys[r], lw, a(sh[3], edgeAlpha));
            }
        }
    }

    /**
     * Temple de montagne dans la brume : trois rangs de monts facettés qui pâlissent dans les bancs de brume,
     * le temple à double toit sur le pic de droite, ses lanternes et l'escalier qui descend, des bambous en
     * premier plan aux deux bords. Le centre reste brumeux et calme.
     */
    private void temple(float w, float h) {
        float u = Math.max(w, h), H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f, lw = Math.max(1f, u / 1100f);
        for (int i = 0; i < 30; i++) star(w * rnd(), h * 0.4f * rnd(), u * (0.0009f + 0.001f * rnd()), 0x16 + Math.round(rnd() * 0x26));
        glow(w * 0.72f, h * 0.24f, H0 * 0.5f, sh[3], 0x18);
        // Lune voilée par la brume : une simple lueur.
        glow(w * 0.34f, h * 0.2f, H0 * 0.16f, sh[4], 0x14);
        // Monts lointains, puis plus proches, chacun posé sur un banc de brume.
        float b1 = h * 0.62f, b2 = h * 0.74f, b3 = h * 0.88f;
        monts(w, h, b1, new float[] {0.12f, 0.1f, H0 * 0.22f, 0.36f, 0.08f, H0 * 0.16f, 0.6f, 0.1f, H0 * 0.2f, 0.92f, 0.08f, H0 * 0.3f}, 0xFF13211B, 0xFF1F3329, 0xC0, lw, 0x18);
        brume(w, b1 - H0 * 0.02f, H0 * 0.16f, 0x2C);
        float ty = templeY(w, h);
        monts(w, h, b2, new float[] {0.04f, 0.1f, H0 * 0.26f, 0.3f, 0.12f, H0 * 0.1f, 0.7f, 0.12f, b2 - ty + H0 * 0.01f, 0.97f, 0.05f, H0 * 0.22f}, 0xFF0C1712, 0xFF15261E, 0xE8, lw, 0x28);
        // Temple sur le pic : soubassement, piliers aux ouvertures chaudes, double toit aux bords relevés.
        float tx = w * TEMPLE_X, bw = q * 3.4f;
        // Éperon rocheux facetté : sa crête porte la base du temple, son pied touche le sol du premier plan.
        float ry = ty + q * 0.5f, gyy = h * 0.88f + q * 0.6f, rr = gyy - ry;
        float[] lf = {-0.7f, 0f, -1.05f, 0.2f, -1.6f, 0.42f, -2.0f, 0.62f, -2.55f, 0.82f, -3.1f, 1f};
        float[] rf = {0.72f, 0f, 1.05f, 0.18f, 0.95f, 0.4f, 1.35f, 0.62f, 1.2f, 0.8f, 1.9f, 1f};
        path.reset();
        path.moveTo(tx + lf[0] * bw, ry);
        for (int i = 2; i < lf.length; i += 2) path.lineTo(tx + lf[i] * bw, ry + lf[i + 1] * rr);
        for (int i = rf.length - 2; i >= 0; i -= 2) path.lineTo(tx + rf[i] * bw, ry + rf[i + 1] * rr);
        path.close();
        p.setColor(a(0xFF0B1513, 0xFC));
        c.drawPath(path, p);
        // Facettes : pans éclairés à droite, pans sombres à gauche, arêtes fines.
        poly(a(0xFF1A2E28, 0xB0), tx + 0.1f * bw, ry, tx + 0.72f * bw, ry, tx + 1.05f * bw, ry + 0.18f * rr, tx + 0.35f * bw, ry + 0.3f * rr);
        poly(a(0xFF152722, 0x90), tx + 0.35f * bw, ry + 0.3f * rr, tx + 1.05f * bw, ry + 0.18f * rr, tx + 0.95f * bw, ry + 0.4f * rr, tx + 0.2f * bw, ry + 0.55f * rr);
        poly(a(0xFF152722, 0x80), tx + 0.2f * bw, ry + 0.55f * rr, tx + 0.95f * bw, ry + 0.4f * rr, tx + 1.35f * bw, ry + 0.62f * rr, tx + 0.7f * bw, ry + 0.85f * rr);
        poly(a(0xFF060D0B, 0xA0), tx - 0.7f * bw, ry, tx + 0.1f * bw, ry, tx + 0.35f * bw, ry + 0.3f * rr, tx - 1.05f * bw, ry + 0.2f * rr);
        poly(a(0xFF060D0B, 0x90), tx - 1.6f * bw, ry + 0.42f * rr, tx + 0.2f * bw, ry + 0.55f * rr, tx - 0.2f * bw, ry + 0.8f * rr, tx - 2.0f * bw, ry + 0.62f * rr);
        line(tx + 0.72f * bw, ry, tx + 1.05f * bw, ry + 0.18f * rr, lw * 1.2f, a(sh[3], 0x48));
        line(tx + 1.05f * bw, ry + 0.18f * rr, tx + 0.95f * bw, ry + 0.4f * rr, lw, a(sh[3], 0x30));
        line(tx + 0.95f * bw, ry + 0.4f * rr, tx + 1.35f * bw, ry + 0.62f * rr, lw, a(sh[3], 0x30));
        line(tx + 0.35f * bw, ry + 0.3f * rr, tx + 0.2f * bw, ry + 0.55f * rr, lw, a(sh[2], 0x28));
        line(tx - 0.7f * bw, ry, tx - 1.05f * bw, ry + 0.2f * rr, lw, a(sh[2], 0x30));
        line(tx - 1.05f * bw, ry + 0.2f * rr, tx - 1.6f * bw, ry + 0.42f * rr, lw, a(sh[2], 0x28));
        box(tx - bw * 0.62f, ty, tx + bw * 0.62f, ty + q * 0.5f, a(0xFF08100C, 0xFA));
        line(tx - bw * 0.62f, ty, tx + bw * 0.62f, ty, lw, a(sh[3], 0x40));
        for (int tier = 0; tier < 2; tier++) {
            float tw = bw * (1f - 0.32f * tier), yb = ty - tier * q * 1.55f, bh = q * (tier == 0 ? 1.0f : 0.75f);
            box(tx - tw / 2, yb - bh, tx + tw / 2, yb, a(0xFF0A130F, 0xFA));
            int cols = tier == 0 ? 5 : 3;
            for (int i = 0; i < cols; i++) {
                float x0 = tx - tw / 2 + tw * (i + 0.18f) / cols, x1 = tx - tw / 2 + tw * (i + 0.82f) / cols;
                box(x0, yb - bh * 0.82f, x1, yb - bh * 0.1f, a(fold, i == cols / 2 ? 0x58 : 0x28));
            }
            float yr = yb - bh, ew = tw * 0.5f + q * 0.9f, rh = q * 0.55f;
            path.reset();
            path.moveTo(tx - ew - q * 0.35f, yr - rh * 0.45f);
            path.quadTo(tx - ew * 0.75f, yr + q * 0.05f, tx - tw * 0.42f, yr - rh * 0.2f);
            path.lineTo(tx - tw * 0.3f, yr - rh);
            path.lineTo(tx + tw * 0.3f, yr - rh);
            path.lineTo(tx + tw * 0.42f, yr - rh * 0.2f);
            path.quadTo(tx + ew * 0.75f, yr + q * 0.05f, tx + ew + q * 0.35f, yr - rh * 0.45f);
            path.close();
            p.setColor(a(0xFF060D09, 0xFC));
            c.drawPath(path, p);
            line(tx - ew - q * 0.35f, yr - rh * 0.45f, tx - tw * 0.42f, yr - rh * 0.2f, lw * 1.2f, a(sh[3], 0x60));
            line(tx + tw * 0.42f, yr - rh * 0.2f, tx + ew + q * 0.35f, yr - rh * 0.45f, lw * 1.2f, a(sh[3], 0x70));
            line(tx - tw * 0.3f, yr - rh, tx + tw * 0.3f, yr - rh, lw * 1.4f, a(sh[3], 0x80));
            if (tier == 1) {
                line(tx, yr - rh, tx, yr - rh - q * 0.9f, lw * 1.5f, a(fold, 0x70));
                disc(tx, yr - rh - q * 0.95f, q * 0.08f, a(fold, 0x90));
            }
        }
        // Escalier collé au relief : deux volées en lacet, du sol du premier plan jusqu'à la porte du temple.
        float[] sp = stairPts(w, h);
        volee(sp[0], sp[1], sp[2], sp[3], q * 0.62f, q * 0.46f, q, lw);
        volee(sp[2], sp[3], sp[4], sp[5], q * 0.46f, q * 0.4f, q, lw);
        // Dernières marches sur la façade du soubassement, jusqu'au seuil de la porte.
        poly(a(0xFF1A2B25, 0xF4), tx - q * 0.42f, ry, tx + q * 0.42f, ry, tx + q * 0.4f, ty, tx - q * 0.4f, ty);
        for (int i = 1; i < 4; i++) line(tx - q * 0.42f, ry - q * 0.125f * i, tx + q * 0.42f, ry - q * 0.125f * i, lw, a(sh[4], 0x48));
        brume(w, b2 - H0 * 0.04f, H0 * 0.18f, 0x34);
        brume(w, ty + H0 * 0.2f, H0 * 0.1f, 0x22);
        // Lanternes du temple et de l'escalier.
        float[] lp = jadePts(w, h);
        for (int i = 0; i < lp.length / 3; i++) {
            if (i < 2) line(lp[i * 3], lp[i * 3 + 1] - lp[i * 3 + 2] * 1.6f, lp[i * 3], lp[i * 3 + 1] - lp[i * 3 + 2], lw, a(0xFF0A0605, 0xE0));
            else line(lp[i * 3], lp[i * 3 + 1] + lp[i * 3 + 2], lp[i * 3], lp[i * 3 + 1] + q * 0.95f, lw * 1.4f, a(0xFF0A0605, 0xF0));
            lanterne(lp[i * 3], lp[i * 3 + 1], lp[i * 3 + 2], lw, 0xC8);
        }
        // Mont proche, sombre, et sa brume basse.
        monts(w, h, b3, new float[] {0.0f, 0.12f, H0 * 0.2f, 0.45f, 0.2f, H0 * 0.05f, 0.88f, 0.1f, H0 * 0.12f}, 0xFF070E0A, 0xFF0F1B15, 0xF8, lw, 0x24);
        brume(w, b3 + H0 * 0.02f, H0 * 0.12f, 0x1C);
        // Lanterne de pierre au bord gauche.
        float lx = w * 0.2f, ly = b3 - H0 * 0.04f;
        box(lx - q * 0.32f, ly - q * 0.5f, lx + q * 0.32f, ly, a(0xFF09110D, 0xFA));
        box(lx - q * 0.14f, ly - q * 1.3f, lx + q * 0.14f, ly - q * 0.5f, a(0xFF09110D, 0xFA));
        box(lx - q * 0.3f, ly - q * 1.8f, lx + q * 0.3f, ly - q * 1.3f, a(0xFF0C1712, 0xFA));
        box(lx - q * 0.12f, ly - q * 1.7f, lx + q * 0.12f, ly - q * 1.42f, a(fold, 0x80));
        glow(lx, ly - q * 1.56f, q * 1.4f, fold, 0x26);
        poly(a(0xFF09110D, 0xFA), lx - q * 0.6f, ly - q * 1.8f, lx, ly - q * 2.25f, lx + q * 0.6f, ly - q * 1.8f);
        line(lx, ly - q * 2.25f, lx + q * 0.6f, ly - q * 1.8f, lw, a(sh[3], 0x50));
        // Bambous au premier plan : bosquet serré à gauche, quelques tiges au bord droit.
        float node = H0 * 0.11f;
        float span = Math.min(w * 0.2f, q * 4.5f);
        for (int i = 0; i < 9; i++) {
            float x = w * 0.012f + span * (i + 0.6f * rnd()) / 9f * (i < 4 ? 0.8f : 1.1f), wd = q * (0.22f + 0.2f * rnd()) * (i < 4 ? 1.3f : 0.9f);
            bambou(x, -q, h + q, wd, (rnd() - 0.4f) * 0.05f, node, 1, i < 4 ? 0xF4 : 0xC0, lw, true);
        }
        for (int i = 0; i < 4; i++) {
            float x = w * 0.99f - span * 0.55f * (i + 0.5f * rnd()) / 4f, wd = q * (0.24f + 0.18f * rnd());
            bambou(x, -q, h + q, wd, (rnd() - 0.6f) * 0.05f, node, 1, 0xE8, lw, true);
        }
        if (!MOVING) {
            for (int i = 0; i < 14; i++) disc(w * (0.15f + 0.7f * rnd()), h * (0.55f + 0.4f * rnd()), q * 0.035f, a(fold, 0x50 + Math.round(rnd() * 0x40)));
        }
    }

    /** Lanternes de la manette Jade : {x, y, rayon, genre} par lanterne (0 sous l'avant-toit, 1 sur l'escalier, 2 suspendue). Partagé avec PadMotion. */
    static float[] jadePadLanterns(float w, float h) {
        float m = Math.min(w, h), q = m * 0.085f, bw = q * 3.4f, tx = w * 0.5f, ty = h * 0.6f, hwa = q * 0.9f;
        return new float[] {tx - bw * 0.5f, ty - q * 0.45f, q * 0.17f, 0, tx + bw * 0.5f, ty - q * 0.45f, q * 0.17f, 0,
            tx - hwa * 0.95f, h * 0.86f, q * 0.15f, 1, tx + hwa * 0.95f, h * 0.86f, q * 0.15f, 1,
            w * 0.36f, m * 0.2f, m * 0.045f, 2, w * 0.64f, m * 0.2f, m * 0.045f, 2};
    }

    /** Monts facettés de la manette Jade : crête en deux faces posée sur base. */
    private void padMonts(float w, float h, float base, float[] peaks, int dark, int light, int alpha, float lw) {
        monts(w, h, base, peaks, dark, light, alpha, lw, 0x22);
    }

    /**
     * Jade, manette : la bambouseraie de près. Tiges serrées sur les côtés, une trouée au milieu où l'on devine
     * le toit du temple dans la brume, deux lanternes suspendues de part et d'autre du bouton origami.
     */
    private void bambouPad(float w, float h) {
        float u = Math.max(w, h), m = Math.min(w, h), lw = Math.max(1f, u / 900f);
        vgrad(0, 0, w, h, a(0xFF0E1A14, 0x40), a(0xFF050A07, 0x80));
        glow(w * 0.5f, h * 0.5f, m * 0.7f, sh[3], 0x1A);
        // Temple dans la trouée (0.66.21), avec le détail du fond : éperon de roche facetté, soubassement, deux étages
        // de piliers aux ouvertures chaudes et croisillons, double toit à tuiles et coins relevés, escalier.
        float tx = w * 0.5f, q = m * 0.085f, bw = q * 3.4f, ty = h * 0.6f, ry = ty + q * 0.5f, rr = h * 1.02f - ry;
        float[] lf = {-0.7f, 0f, -1.05f, 0.2f, -1.6f, 0.42f, -2.0f, 0.62f, -2.55f, 0.82f, -3.1f, 1f};
        float[] rf = {0.72f, 0f, 1.05f, 0.18f, 0.95f, 0.4f, 1.35f, 0.62f, 1.2f, 0.8f, 1.9f, 1f};
        float[] mb = {0.15f, 0.12f, m * 0.2f, 0.5f, 0.1f, m * 0.26f, 0.85f, 0.12f, m * 0.22f};
        padMonts(w, h, h * 0.5f, mb, 0xFF13211B, 0xFF1F3329, 0xC0, lw);
        brume(w, h * 0.48f, m * 0.14f, 0x2C);
        path.reset();
        path.moveTo(tx + lf[0] * bw, ry);
        for (int i = 2; i < lf.length; i += 2) path.lineTo(tx + lf[i] * bw, ry + lf[i + 1] * rr);
        for (int i = rf.length - 2; i >= 0; i -= 2) path.lineTo(tx + rf[i] * bw, ry + rf[i + 1] * rr);
        path.close();
        p.setColor(a(0xFF0B1513, 0xFC));
        c.drawPath(path, p);
        poly(a(0xFF1A2E28, 0xB0), tx + 0.1f * bw, ry, tx + 0.72f * bw, ry, tx + 1.05f * bw, ry + 0.18f * rr, tx + 0.35f * bw, ry + 0.3f * rr);
        poly(a(0xFF152722, 0x90), tx + 0.35f * bw, ry + 0.3f * rr, tx + 1.05f * bw, ry + 0.18f * rr, tx + 0.95f * bw, ry + 0.4f * rr, tx + 0.2f * bw, ry + 0.55f * rr);
        poly(a(0xFF152722, 0x80), tx + 0.2f * bw, ry + 0.55f * rr, tx + 0.95f * bw, ry + 0.4f * rr, tx + 1.35f * bw, ry + 0.62f * rr, tx + 0.7f * bw, ry + 0.85f * rr);
        poly(a(0xFF060D0B, 0xA0), tx - 0.7f * bw, ry, tx + 0.1f * bw, ry, tx + 0.35f * bw, ry + 0.3f * rr, tx - 1.05f * bw, ry + 0.2f * rr);
        poly(a(0xFF060D0B, 0x90), tx - 1.6f * bw, ry + 0.42f * rr, tx + 0.2f * bw, ry + 0.55f * rr, tx - 0.2f * bw, ry + 0.8f * rr, tx - 2.0f * bw, ry + 0.62f * rr);
        line(tx + 0.72f * bw, ry, tx + 1.05f * bw, ry + 0.18f * rr, lw * 1.2f, a(sh[3], 0x48));
        line(tx + 1.05f * bw, ry + 0.18f * rr, tx + 0.95f * bw, ry + 0.4f * rr, lw, a(sh[3], 0x30));
        line(tx + 0.95f * bw, ry + 0.4f * rr, tx + 1.35f * bw, ry + 0.62f * rr, lw, a(sh[3], 0x30));
        line(tx - 0.7f * bw, ry, tx - 1.05f * bw, ry + 0.2f * rr, lw, a(sh[2], 0x30));
        line(tx - 1.05f * bw, ry + 0.2f * rr, tx - 1.6f * bw, ry + 0.42f * rr, lw, a(sh[2], 0x28));
        box(tx - bw * 0.62f, ty, tx + bw * 0.62f, ty + q * 0.5f, a(0xFF08100C, 0xFA));
        line(tx - bw * 0.62f, ty, tx + bw * 0.62f, ty, lw, a(sh[3], 0x40));
        for (int i = 0; i <= 8; i++) line(tx - bw * 0.6f + bw * 1.2f * i / 8, ty + q * 0.08f, tx - bw * 0.6f + bw * 1.2f * i / 8, ty + q * 0.42f, lw * 0.8f, a(sh[3], 0x18));
        for (int tier = 0; tier < 2; tier++) {
            float tw = bw * (1f - 0.32f * tier), yb = ty - tier * q * 1.55f, bh = q * (tier == 0 ? 1f : 0.75f);
            box(tx - tw / 2, yb - bh, tx + tw / 2, yb, a(0xFF0A130F, 0xFA));
            // Galerie : balustrade à barreaux.
            line(tx - tw / 2 - q * 0.15f, yb - bh * 0.08f, tx + tw / 2 + q * 0.15f, yb - bh * 0.08f, lw * 1.2f, a(sh[3], 0x40));
            for (int i = 0; i <= 10; i++) {
                float x = tx - tw / 2 - q * 0.15f + (tw + q * 0.3f) * i / 10;
                line(x, yb - bh * 0.08f, x, yb - bh * 0.26f, lw * 0.8f, a(sh[3], 0x30));
            }
            int cols = tier == 0 ? 5 : 3;
            for (int i = 0; i < cols; i++) {
                float x0 = tx - tw / 2 + tw * (i + 0.18f) / cols, x1 = tx - tw / 2 + tw * (i + 0.82f) / cols;
                box(x0, yb - bh * 0.82f, x1, yb - bh * 0.28f, a(fold, i == cols / 2 ? 0x60 : 0x2C));
                line((x0 + x1) / 2, yb - bh * 0.82f, (x0 + x1) / 2, yb - bh * 0.28f, lw * 0.7f, a(0xFF0A130F, 0xC0));
                line(x0, yb - bh * 0.55f, x1, yb - bh * 0.55f, lw * 0.7f, a(0xFF0A130F, 0xC0));
                box(x1, yb - bh, x1 + tw * 0.36f / cols, yb, a(0xFF050A08, 0xF0));
            }
            for (int i = 0; i <= cols; i++) {
                float x = tx - tw / 2 + tw * i / cols;
                box(x - q * 0.05f, yb - bh - q * 0.12f, x + q * 0.05f, yb - bh, a(0xFF050A08, 0xF8));
            }
            float yr = yb - bh, ew = tw * 0.5f + q * 0.9f, rh = q * 0.55f;
            path.reset();
            path.moveTo(tx - ew - q * 0.35f, yr - rh * 0.45f);
            path.quadTo(tx - ew * 0.75f, yr + q * 0.05f, tx - tw * 0.42f, yr - rh * 0.2f);
            path.lineTo(tx - tw * 0.3f, yr - rh);
            path.lineTo(tx + tw * 0.3f, yr - rh);
            path.lineTo(tx + tw * 0.42f, yr - rh * 0.2f);
            path.quadTo(tx + ew * 0.75f, yr + q * 0.05f, tx + ew + q * 0.35f, yr - rh * 0.45f);
            path.close();
            p.setColor(a(0xFF060D09, 0xFC));
            c.drawPath(path, p);
            // Tuiles : traits du faîte à l'égout.
            for (int i = -8; i <= 8; i++) {
                float xt = tx + tw * 0.3f * i / 8, xe = tx + (tw * 0.42f + (ew - tw * 0.42f) * Math.abs(i) / 8 * 0.6f) * (i / 8f) * 1.05f;
                line(xt, yr - rh, xe, yr - rh * 0.2f, lw * 0.7f, a(sh[2], 0x22));
            }
            line(tx - ew - q * 0.35f, yr - rh * 0.45f, tx - tw * 0.42f, yr - rh * 0.2f, lw * 1.2f, a(sh[3], 0x60));
            line(tx + tw * 0.42f, yr - rh * 0.2f, tx + ew + q * 0.35f, yr - rh * 0.45f, lw * 1.2f, a(sh[3], 0x70));
            line(tx - tw * 0.3f, yr - rh, tx + tw * 0.3f, yr - rh, lw * 1.4f, a(sh[3], 0x80));
            // Coins relevés : pointe dorée et clochette.
            for (int sd = -1; sd <= 1; sd += 2) {
                float cx2 = tx + sd * (ew + q * 0.35f), cb = tx + sd * (ew + q * 0.2f);
                disc(cx2, yr - rh * 0.45f, q * 0.05f, a(fold, 0x90));
                line(cb, yr - rh * 0.42f, cb, yr - rh * 0.42f + q * 0.3f, lw * 0.7f, a(0xFF0A0605, 0xE0));
                disc(cb, yr - rh * 0.42f + q * 0.33f, q * 0.05f, a(fold, 0x80));
            }
            if (tier == 1) {
                for (int r2 = 0; r2 < 5; r2++) disc(tx, yr - rh - q * (0.2f + 0.17f * r2), q * (0.09f - 0.01f * r2), a(fold, 0x70));
                line(tx, yr - rh, tx, yr - rh - q * 1.05f, lw * 1.5f, a(fold, 0x70));
                disc(tx, yr - rh - q * 1.1f, q * 0.08f, a(fold, 0x90));
            }
        }
        // Escalier droit jusqu'à la porte.
        float hwa = q * 0.9f, hwb = q * 0.42f, ya = h * 1.0f;
        poly(a(0xFF1A2B25, 0xF0), tx - hwa, ya, tx + hwa, ya, tx + hwb, ry, tx - hwb, ry);
        int ns = Math.max(6, Math.round((ya - ry) / (q * 0.22f)));
        for (int i = 1; i < ns; i++) {
            float t = i / (float) ns, y = ya + (ry - ya) * t, hw = hwa + (hwb - hwa) * t;
            line(tx - hw, y, tx + hw, y, lw, a(sh[4], Math.round(0x40 * (1f - 0.4f * t))));
            line(tx - hw, y + lw * 1.6f, tx + hw, y + lw * 1.6f, lw, a(0xFF050B09, 0x90));
        }
        line(tx - hwa, ya, tx - hwb, ry, lw * 1.4f, a(sh[3], 0x50));
        line(tx + hwa, ya, tx + hwb, ry, lw * 1.4f, a(sh[2], 0x30));
        brume(w, ty + q * 1.2f, m * 0.12f, 0x22);
        brume(w, h * 0.95f, m * 0.16f, 0x26);
        // Lanternes : sous l'avant-toit, sur l'escalier, suspendues de part et d'autre du bouton origami.
        float[] lp = jadePadLanterns(w, h);
        for (int i = 0; i < lp.length / 4; i++) {
            float lx = lp[i * 4], ly = lp[i * 4 + 1], lr = lp[i * 4 + 2];
            int kind = (int) lp[i * 4 + 3];
            if (kind == 0) line(lx, ly - lr * 1.8f, lx, ly - lr, lw, a(0xFF0A0605, 0xE0));
            else if (kind == 1) line(lx, ly + lr, lx, ly + q * 1.1f, lw * 1.4f, a(0xFF0A0605, 0xF0));
            else line(lx, 0, lx, ly - lr * 1.1f, lw, a(0xFF0A0605, 0xE0));
            lanterne(lx, ly, lr, lw, 0xC0);
        }
        // Tiges : plus nombreuses et plus nettes vers les bords.
        float node = m * 0.26f;
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < 9; i++) {
                float t = i / 8f, x = side == 0 ? w * (0.02f + 0.33f * t) : w * (0.98f - 0.33f * t);
                x += (rnd() - 0.5f) * m * 0.04f;
                float wd = m * (0.035f + 0.03f * (1f - t)) * (0.8f + 0.4f * rnd());
                int al = Math.round(0x50 + 0xA0 * (1f - t));
                bambou(x, -m * 0.05f, h + m * 0.05f, wd, (rnd() - 0.5f) * 0.04f, node, 1, al, lw, rnd() < 0.6f);
            }
        }
        for (int i = 0; i < 12; i++) disc(w * (0.15f + 0.7f * rnd()), h * (0.3f + 0.65f * rnd()), m * 0.006f, a(fold, 0x50 + Math.round(rnd() * 0x50)));
    }

    // ---- Argent : pleine lune, grues en origami, mer de nuages

    /**
     * Grue en vol, avec les facettes du logo (Crane) : centre (x, y), envergure size, inclinaison rot en
     * radians, battement flap (1 ailes hautes, -0,4 ailes basses). Partagée avec BackdropMotion.
     */
    static void crane(Canvas c, Paint p, Path path, int[] sh, int fold, float x, float y, float size, float rot, float flap, int alpha, boolean silhouette) {
        // Grue en vol de Crane.FLY (0.66.14) : les deux ailes pivotent autour de leur racine selon flap.
        float s = size / 84f, cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        float ang = -(flap - 0.6f) * 0.9f, ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        int a8 = Math.max(0, Math.min(255, alpha)) << 24;
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(Math.max(1f, 1.4f * s));
        for (int i = 0; i < Crane.FLY.length; i++) {
            float[] f = Crane.FLY[i];
            boolean wing = i >= 3 && i <= 6;
            float pvx = i <= 4 ? 50f : 46f, pvy = i <= 4 ? 46f : 55f;
            path.reset();
            for (int k = 1; k + 1 < f.length; k += 2) {
                float px = f[k], py = f[k + 1];
                if (wing) {
                    float dx = px - pvx, dy = py - pvy;
                    px = pvx + dx * ca - dy * sa;
                    py = pvy + dx * sa + dy * ca;
                }
                px -= Crane.FLY_CX;
                py -= Crane.FLY_CY;
                float rx = x + (px * cs - py * sn) * s, ry = y + (px * sn + py * cs) * s;
                if (k == 1) path.moveTo(rx, ry);
                else path.lineTo(rx, ry);
            }
            path.close();
            int col = silhouette ? Theme.blend(0xFF06070A, sh[(int) f[0]], 0.12f) : sh[(int) f[0]];
            if (i == 0 && !silhouette) col = fold;
            p.setStyle(Paint.Style.FILL);
            p.setColor((col & 0x00FFFFFF) | a8);
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setColor((Theme.darken(col | 0xFF000000, 0.45f) & 0x00FFFFFF) | a8);
            c.drawPath(path, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    /** Vol de grues du fond (x, y en fractions, envergure en H0, inclinaison, battement), partagé avec BackdropMotion. */
    static final float[][] FLOCK = {
        {0.22f, 0.42f, 0.2f, -0.18f, 0.9f}, {0.42f, 0.3f, 0.1f, -0.12f, 0.4f}, {0.53f, 0.22f, 0.075f, -0.1f, 1f},
        {0.6f, 0.16f, 0.055f, -0.14f, 0.2f}, {0.12f, 0.2f, 0.07f, -0.08f, 0.6f}, {0.86f, 0.5f, 0.09f, -0.2f, -0.2f},
    };

    /** Lune (x, y, rayon), partagée avec BackdropMotion. */
    static float[] lunePts(float w, float h) {
        float H0 = Math.min(h, w * 0.9f);
        return new float[] {w * 0.72f, Math.min(h * 0.27f, h * 0.62f - H0 * 0.3f), H0 * 0.15f};
    }

    /** Rangée de nuages bombés, éclairés par la lune sur leur bord haut (plus vif sous la lune). */
    private void nuages(float w, float y, float r, float mx, float lw, int body, int alpha, int edge) {
        int n = (int) (w / (r * 1.3f)) + 3;
        float off = -rnd() * r;
        for (int i = 0; i < n; i++) {
            float x = off + i * r * 1.3f + (rnd() - 0.5f) * r * 0.3f, rr = r * (0.75f + 0.5f * rnd()), yy = y + (rnd() - 0.5f) * r * 0.25f;
            RectF o = new RectF(x - rr * 1.25f, yy - rr * 0.7f, x + rr * 1.25f, yy + rr * 0.7f);
            p.setColor(a(body, alpha));
            c.drawOval(o, p);
            float near = 1f - Math.min(1f, Math.abs(x - mx) / (w * 0.35f));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(lw * (1f + near));
            p.setColor(a(sh[4], Math.round(edge * (0.3f + 0.7f * near))));
            c.drawArc(o, 205, 130, false, p);
            p.setStyle(Paint.Style.FILL);
        }
        box(0, y, w, y + r * 2f, a(body, alpha));
    }

    /**
     * Nuit de pleine lune, presque monochrome : grande lune facettée et ses halos, étoiles, pics qui percent
     * une mer de nuages éclairés par-dessus, chemin de lune sur les nuages, et un vol de grues en origami
     * qui monte vers elle. Le centre reste sombre.
     */
    private void lune(float w, float h) {
        float u = Math.max(w, h), H0 = Math.min(h, w * 0.9f), q = Math.min(w, h * 1.2f) * 0.06f, lw = Math.max(1f, u / 1100f);
        float[] lp = lunePts(w, h);
        float mx = lp[0], my = lp[1], mr = lp[2], sea = h * 0.64f;
        glow(mx, my, mr * 6f, sh[3], 0x22);
        glow(mx, my, mr * 1.9f, sh[4], 0x26);
        for (int i = 0; i < 70; i++) star(w * rnd(), sea * rnd(), u * (0.0008f + 0.0012f * rnd()), 0x18 + Math.round(rnd() * 0x40));
        for (int i = 1; i <= 3; i++) arcLine(mx, my, mr * (1.3f + 0.45f * i), 0, 360, lw, a(fold, 0x14 - i * 3));
        // Disque à vingt-huit facettes, gauche dans l'ombre.
        int fc = 28;
        for (int i = 0; i < fc; i++) {
            double t0 = Math.PI * 2 * i / fc, t1 = Math.PI * 2 * (i + 1) / fc;
            boolean left = Math.cos((t0 + t1) / 2) < -0.2;
            tri(mx, my, mx + (float) Math.cos(t0) * mr, my + (float) Math.sin(t0) * mr,
                    mx + (float) Math.cos(t1) * mr, my + (float) Math.sin(t1) * mr, a(sh[left ? 3 : 4], (i & 1) == 0 ? 0x9C : 0x90));
        }
        disc(mx - mr * 0.3f, my - mr * 0.2f, mr * 0.22f, a(sh[2], 0x30));
        disc(mx + mr * 0.25f, my + mr * 0.3f, mr * 0.16f, a(sh[2], 0x28));
        disc(mx + mr * 0.1f, my - mr * 0.45f, mr * 0.1f, a(sh[2], 0x24));
        arcLine(mx, my, mr, 0, 360, lw * 1.4f, a(fold, 0x70));
        // Pics qui percent la mer de nuages.
        float[][] pk = {{0.08f, 0.2f, 0.14f}, {0.2f, 0.09f, 0.1f}, {0.9f, 0.16f, 0.12f}, {0.55f, 0.06f, 0.08f}};
        for (float[] k2 : pk) {
            float px = w * k2[0], ph = H0 * k2[1], pw = H0 * k2[2];
            poly(a(0xFF0B0C10, 0xF4), px - pw, sea + q, px, sea - ph, px, sea + q);
            poly(a(0xFF15171D, 0xF4), px, sea - ph, px + pw, sea + q, px, sea + q);
            poly(a(sh[4], 0x40), px - pw * 0.18f, sea - ph * 0.8f, px, sea - ph, px + pw * 0.2f, sea - ph * 0.78f, px + pw * 0.05f, sea - ph * 0.86f);
            line(px, sea - ph, px + pw, sea + q, lw, a(fold, 0x34));
        }
        // Mer de nuages : rangs de plus en plus gros et proches, liserés de lune.
        float r = q * 0.7f, y = sea;
        int[] bodies = {0xFF14161B, 0xFF111317, 0xFF0E1013, 0xFF0B0C0F, 0xFF08090B};
        for (int row = 0; row < 7; row++) {
            nuages(w, y, r, mx, lw, bodies[Math.min(4, row * 2 / 3)], 0xF6, 0x50 + row * 0x08);
            y += r * 0.75f;
            r *= 1.25f;
        }
        // Chemin de lune : une lueur douce posée sur les nuages, sous la lune.
        p.setShader(new LinearGradient(mx - mr * 2.5f, 0, mx + mr * 2.5f, 0, new int[] {a(sh[4], 0), a(sh[4], 0x10), a(sh[4], 0)}, null, Shader.TileMode.CLAMP));
        c.drawRect(mx - mr * 2.5f, sea, mx + mr * 2.5f, h, p);
        p.setShader(null);
        // 0.66.17 (BOSS) : plus de grues sur le fond Argent ; l'animation vient du ciel (BackdropMotion.lune).
    }

    /**
     * Argent, manette : la grue dépliée. Le patron de pliage de la grue (plis en montagne et en vallée au trait
     * argent) couvre la façade, la pleine lune en marque le centre, des nuages ferment le bas et trois petites
     * grues volent dans le haut.
     */
    private void plisPad(float w, float h) {
        float u = Math.max(w, h), m = Math.min(w, h), lw = Math.max(1f, u / 900f);
        float cx = w * 0.5f, cy = h * 0.5f, S = Math.max(w, h) * 0.62f;
        glow(cx, cy, m * 0.7f, sh[3], 0x22);
        for (int i = 0; i < 40; i++) star(w * rnd(), h * 0.7f * rnd(), u * (0.0012f + 0.0012f * rnd()), 0x18 + Math.round(rnd() * 0x30));
        // Patron de la base de l'oiseau : carré posé sur la pointe, diagonales, médianes et plis à 22,5°.
        float[] T = {cx, cy - S, cx + S, cy, cx, cy + S, cx - S, cy};
        for (int i = 0; i < 4; i++) line(T[i * 2], T[i * 2 + 1], T[(i * 2 + 2) % 8], T[(i * 2 + 3) % 8], lw * 1.2f, a(fold, 0x34));
        line(T[0], T[1], T[4], T[5], lw, a(sh[4], 0x2C));
        line(T[2], T[3], T[6], T[7], lw, a(sh[4], 0x2C));
        for (int i = 0; i < 4; i++) {
            float ax = (T[i * 2] + T[(i * 2 + 2) % 8]) / 2, ay = (T[i * 2 + 1] + T[(i * 2 + 3) % 8]) / 2;
            float bx = (T[(i * 2 + 4) % 8] + T[(i * 2 + 6) % 8]) / 2, by = (T[(i * 2 + 5) % 8] + T[(i * 2 + 7) % 8]) / 2;
            line(ax, ay, bx, by, lw, a(sh[3], 0x22));
        }
        float tg = (float) Math.tan(Math.PI / 8);
        for (int i = 0; i < 4; i++) {
            float ox = T[i * 2], oy = T[i * 2 + 1];
            float dx = cx - ox, dy = cy - oy;
            for (int sd = -1; sd <= 1; sd += 2) {
                float px = ox + dx + sd * (-dy) * tg, py = oy + dy + sd * dx * tg;
                line(ox, oy, ox + (px - ox) * 0.7f, oy + (py - oy) * 0.7f, lw, a(sh[3], 0x26));
                line(ox + (px - ox) * 0.7f, oy + (py - oy) * 0.7f, cx, cy, lw, a(sh[2], 0x18));
            }
        }
        // Pleine lune au centre du patron.
        float mr = m * 0.16f;
        disc(cx, cy, mr * 1.6f, a(sh[3], 0x12));
        int fc = 24;
        for (int i = 0; i < fc; i++) {
            double t0 = Math.PI * 2 * i / fc, t1 = Math.PI * 2 * (i + 1) / fc;
            boolean left = Math.cos((t0 + t1) / 2) < -0.2;
            tri(cx, cy, cx + (float) Math.cos(t0) * mr, cy + (float) Math.sin(t0) * mr, cx + (float) Math.cos(t1) * mr, cy + (float) Math.sin(t1) * mr,
                    a(sh[left ? 3 : 4], (i & 1) == 0 ? 0x5A : 0x4C));
        }
        arcLine(cx, cy, mr, 0, 360, lw * 1.3f, a(fold, 0x60));
        // Nuages au pied.
        float r = m * 0.07f;
        nuages(w, h - m * 0.12f, r, cx, lw, 0xFF14161B, 0xC8, 0x48);
        nuages(w, h - m * 0.03f, r * 1.4f, cx, lw, 0xFF0E1013, 0xE0, 0x40);
    }
}
