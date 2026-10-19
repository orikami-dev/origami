package com.hideo.foldplay;

import java.util.List;

/**
 * Placement dessiné des manettes fournies (0.42.5), au pixel près, pour n'importe quel écran.
 *
 * Tout est calculé à partir de la taille réelle de la zone (en cases de 16 dp, fractionnaires,
 * sans arrondi à la grille) et de tailles fixées en dp, comme sur les manettes tactiles des
 * émulateurs du commerce : un bouton garde la même taille physique d'un téléphone à une tablette,
 * et toute la manette est réduite ensemble seulement si la place manque.
 *
 * Règles communes :
 * - chaque pouce a son coin : les commandes sont posées contre les bords bas gauche et bas droit,
 *   là où les pouces tombent naturellement ;
 * - Maison est toujours exactement au milieu (x = largeur / 2), Select et Start, sauvegarde et
 *   chargement rapides en miroir de part et d'autre ;
 * - en portrait (sous le jeu), la rangée Maison est en haut de la manette ; superposée au jeu,
 *   elle passe en bas au centre pour laisser l'image libre.
 */
final class PadDesign {
    // Tailles de référence, en cases de 16 dp.
    private static final float M = 1.25f;        // marge contre les bords (20 dp)
    private static final float G = 0.5f;         // écart entre deux touches (8 dp)
    private static final float STICK = 8f;       // joystick (128 dp)
    private static final float DPAD = 8.5f;      // croix (136 dp)
    private static final float FACE = 3.6f;      // bouton A B X Y (58 dp)
    private static final float SPREAD = 0.84f;   // écart des boutons du losange, en diamètres
    private static final float LEVER = 9f;       // levier d'arcade (144 dp)
    private static final float ARCADE = 4.25f;   // bouton d'arcade (68 dp)
    private static final float SH_W = 5.75f;     // gâchette (92 × 40 dp)
    private static final float SH_H = 2.5f;
    private static final float PILL_W = 4f;      // Select, Start… (64 × 28 dp)
    private static final float PILL_H = 1.75f;
    private static final float HOME = 3.5f;      // Home (56 dp)

    private PadDesign() {
    }

    /**
     * Cases [x, y, l, h] de chaque touche de la disposition (dans l'ordre de ses touches), pour une
     * zone de w × h cases. bottomCenter : rangée Maison en bas (manette superposée au jeu).
     * Renvoie null si la disposition n'est pas une des manettes fournies telle quelle.
     */
    static float[][] place(ControlLayout l, float w, float h, boolean bottomCenter) {
        if (w < 4 || h < 4) return null;
        Out o = new Out(l.controls);
        if (l.kind.equals("mini")) return mini(l, o, w, h);
        if (l.kind.equals("flex")) {
            if (l.split) return null;
            ConsolePad.place(l, o, w, h, bottomCenter);
        } else if (l.kind.equals("2d")) {
            arcade(o, w, h, bottomCenter);
        } else {
            xbox(o, w, h, bottomCenter);
        }
        return o.done();
    }

    // ------------------------------------------------------------------ 3D

    /**
     * Façon manette Xbox : joystick gauche en haut contre le bord et croix en dessous vers le centre ;
     * A B X Y en haut contre le bord droit, joystick droit en dessous vers le centre ; gâchettes
     * empilées dans les coins du haut.
     */
    private static void xbox(Out o, float w, float h, boolean bottom) {
        bottom = rowAtBottom(w, h, bottom, true);
        float s = solve(o, w, h, bottom, true);
        float m = M * s, g = G * s;
        shoulders(o, w, s, "L2", "L", "R2", "R");
        float rS = STICK * s / 2, rD = DPAD * s / 2, rF = faceRadius(FACE * s);
        float[] left = stagger(w, h, m, g, rS, rD, s);
        float[] right = stagger(w, h, m, g, rF, rS, s);
        o.circle("STICK_L", left[0], left[1], rS);
        o.circle("DPAD", left[2], left[3], rD);
        face(o, w - right[0], right[1], FACE * s);
        o.circle("STICK_R", w - right[2], right[3], rS);
        center(o, w, h, s, bottom);
    }

    /**
     * Deux commandes d'un pouce : la première contre le bord, plus haut ; la seconde vers le centre,
     * posée sur le bas. Renvoie les centres (x depuis le bord, y) de l'une puis de l'autre.
     */
    private static float[] stagger(float w, float h, float m, float g, float rOut, float rIn, float s) {
        float d = rOut + rIn + 2 * g;
        float room = w / 2 - g - m - rOut - rIn;
        float dx = Math.max(0, Math.min(0.8f * d, room));
        float dy = (float) Math.sqrt(Math.max(0, d * d - dx * dx));
        float yIn = h - m - rIn;
        float xOut = m + rOut;
        return new float[] {xOut, yIn - dy, xOut + dx, yIn};
    }

    // ------------------------------------------------------------------ 2D

    /**
     * Panneau de borne d'arcade : le levier à gauche, quatre boutons ronds à droite en deux rangées
     * décalées et cintrées comme sur un panneau Vewlix (rangée du bas décalée vers la gauche,
     * boutons qui remontent vers la droite). Pas de gâchettes : une borne n'en a pas.
     */
    private static void arcade(Out o, float w, float h, boolean bottom) {
        bottom = rowAtBottom(w, h, bottom, false);
        float s = solve(o, w, h, bottom, false);
        float m = M * s;
        float rL = LEVER * s / 2;
        o.circle("STICK_L", m + rL, h - m - rL, rL);
        float b = ARCADE * s, p = b + 0.9f * s;
        // Rangées : (x, y) des centres, origine au bouton haut gauche.
        float[][] at = {
            {0, 0}, {p, -0.3f * p},            // Y, X
            {-0.2f * p, p}, {0.8f * p, 0.7f * p}, // B, A
        };
        String[] keys = {"Y", "X", "B", "A"};
        // Le panneau se pose contre le bord droit, centré sur la hauteur du levier, sans dépasser en bas.
        float ox = w - m - b / 2 - p;
        float oy = Math.min(h - m - rL - 0.35f * p, h - m - b / 2 - p);
        for (int i = 0; i < 4; i++) o.circle(keys[i], ox + at[i][0], oy + at[i][1], b / 2);
        center(o, w, h, s, bottom);
    }

    // ------------------------------------------------------------------ Mini

    /** Mini : la grille carrée d'origine, simplement mise à la taille exacte du cadre flottant. */
    private static float[][] mini(ControlLayout l, Out o, float w, float h) {
        ControlLayout ref = ControlLayout.mini();
        float sx = w / ref.refCols, sy = h / ref.refRows;
        for (ControlLayout.Control c : ref.controls) {
            float bw = c.w * Math.min(sx, sy), bh = c.h * Math.min(sx, sy);
            float cx = (c.x * ref.refCols + c.w / 2f) * sx;
            float cy = (c.y * ref.refRows + c.h / 2f) * sy;
            o.box(c.type, cx - bw / 2, cy - bh / 2, bw, bh);
        }
        return o.done();
    }

    // ------------------------------------------------------------------ pièces communes

    private static float faceRadius(float b) {
        return SPREAD * b + b / 2;
    }

    /** Losange A B X Y centré en (cx, cy) : Y en haut, A en bas, X à gauche, B à droite. */
    static void face(Out o, float cx, float cy, float b) {
        float d = SPREAD * b;
        o.circle("Y", cx, cy - d, b / 2);
        o.circle("A", cx, cy + d, b / 2);
        o.circle("X", cx - d, cy, b / 2);
        o.circle("B", cx + d, cy, b / 2);
    }

    /** Gâchettes empilées dans les coins du haut, la 2 au-dessus de la 1. */
    private static void shoulders(Out o, float w, float s, String l2, String l1, String r2, String r1) {
        float m = M * s, g = G * s, sw = SH_W * s, sh = SH_H * s;
        o.box(l2, m, m, sw, sh);
        o.box(l1, m, m + sh + g, sw, sh);
        o.box(r2, w - m - sw, m, sw, sh);
        o.box(r1, w - m - sw, m + sh + g, sw, sh);
    }

    /**
     * Rangée du centre : Maison exactement au milieu ; Select et Start de chaque côté, à sa hauteur ;
     * Accélérer, sauvegarde et chargement rapides sur la rangée voisine, dans le même ordre.
     */
    private static void center(Out o, float w, float h, float s, boolean bottom) {
        float m = M * s, g = G * s, hm = HOME * s, pw = PILL_W * s, ph = PILL_H * s;
        float homeY = bottom ? h - m - hm : m;
        float cy = homeY + hm / 2;
        float sideX = hm / 2 + g + pw / 2;
        o.box("HOME", w / 2 - hm / 2, homeY, hm, hm);
        o.box("SELECT", w / 2 - sideX - pw / 2, cy - ph / 2, pw, ph);
        o.box("START", w / 2 + sideX - pw / 2, cy - ph / 2, pw, ph);
        float rowY = bottom ? homeY - g - ph : homeY + hm + g;
        o.box("FAST", w / 2 - pw / 2, rowY, pw, ph);
        o.box("QSAVE", w / 2 - sideX - pw / 2, rowY, pw, ph);
        o.box("QLOAD", w / 2 + sideX - pw / 2, rowY, pw, ph);
    }

    /**
     * Superposée sur un écran étroit, la rangée Maison en bas écraserait les pouces : elle remonte
     * en haut dès que cela laisse les touches nettement plus grandes.
     */
    private static boolean rowAtBottom(float w, float h, boolean bottom, boolean xbox) {
        if (!bottom) return false;
        float down = solve(null, w, h, true, xbox);
        return down >= 0.85f || down >= solve(null, w, h, false, xbox) - 0.02f;
    }

    /** Demi-largeur et hauteur de la rangée du centre, à l'échelle 1. */
    private static float centerHalf() {
        return HOME / 2 + G + PILL_W;
    }

    private static float centerHeight() {
        return HOME + G + PILL_H;
    }

    /**
     * La plus grande échelle (entre 0,45 et 1,15) à laquelle tout tient sans chevauchement :
     * gâchettes, rangée du centre, et les deux commandes de chaque pouce.
     */
    private static float solve(Out o, float w, float h, boolean bottom, boolean xbox) {
        float lo = 0.45f, hi = 1.15f;
        if (fits(w, h, hi, bottom, xbox)) return hi;
        for (int i = 0; i < 18; i++) {
            float mid = (lo + hi) / 2;
            if (fits(w, h, mid, bottom, xbox)) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    private static boolean fits(float w, float h, float s, boolean bottom, boolean xbox) {
        float m = M * s, g = G * s;
        float ch = centerHalf() * s, cH = centerHeight() * s;
        // Haut de la zone libre pour les pouces, sous les gâchettes (ou la rangée du centre).
        float shBottom = xbox ? m + 2 * SH_H * s + g : 0;
        if (xbox && m + SH_W * s + g > w / 2 - ch && !bottom) return false;
        float cTop = bottom ? h - m - cH : m, cBottom = bottom ? h - m : m + cH;
        if (xbox) {
            float rS = STICK * s / 2, rD = DPAD * s / 2, rF = faceRadius(FACE * s);
            float[][] sides = {stagger(w, h, m, g, rS, rD, s), stagger(w, h, m, g, rF, rS, s)};
            float[] rOut = {rS, rF}, rIn = {rD, rS};
            for (int k = 0; k < 2; k++) {
                float[] p = sides[k];
                if (p[2] + rIn[k] > w / 2 - g / 2) return false;
                if (p[1] - rOut[k] < shBottom + g) return false;
                if (!clear(p[0], p[1], rOut[k], w, ch, cTop, cBottom, g)) return false;
                if (!clear(p[2], p[3], rIn[k], w, ch, cTop, cBottom, g)) return false;
            }
            return true;
        }
        float rL = LEVER * s / 2;
        float b = ARCADE * s, p = b + 0.9f * s;
        float panelW = 1.2f * p + b, panelH = 1.3f * p + b;
        if (2 * m + 2 * rL + panelW + 2 * g > w) return false;
        if (h - 2 * m < Math.max(2 * rL, panelH)) return false;
        if (!clear(m + rL, h - m - rL, rL, w, ch, cTop, cBottom, g)) return false;
        float px = w - m - panelW, pyTop = h - m - panelH;
        // Le panneau de boutons ne doit pas passer sous la rangée du centre.
        return px > w / 2 + ch + g || pyTop > cBottom + g || h - m < cTop - g;
    }

    /** Un cercle (centre x depuis le bord, y) ne touche pas la rangée du centre. */
    private static boolean clear(float x, float y, float r, float w, float ch, float cTop, float cBottom, float g) {
        boolean beside = x + r <= w / 2 - ch - g;
        boolean apart = y - r >= cBottom + g || y + r <= cTop - g;
        return beside || apart;
    }

    /** Résultat : une case par touche de la disposition, dans son ordre. */
    static final class Out {
        final List<ControlLayout.Control> keys;
        final float[][] boxes;

        Out(List<ControlLayout.Control> keys) {
            this.keys = keys;
            this.boxes = new float[keys.size()][];
        }

        void box(String type, float x, float y, float w, float h) {
            for (int i = 0; i < keys.size(); i++) {
                if (boxes[i] == null && keys.get(i).type.equals(type)) {
                    boxes[i] = new float[] {x, y, w, h};
                    return;
                }
            }
        }

        void circle(String type, float cx, float cy, float r) {
            box(type, cx - r, cy - r, 2 * r, 2 * r);
        }

        /** null si une touche n'a pas trouvé de place : la disposition n'est pas celle d'origine. */
        float[][] done() {
            for (float[] b : boxes) if (b == null) return null;
            return boxes;
        }
    }
}
