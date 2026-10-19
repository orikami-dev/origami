package com.hideo.foldplay;

import java.util.ArrayList;
import java.util.List;

/**
 * Manette Flex (0.60.3) : la manette virtuelle prend la forme de la console du jeu lancé.
 *
 * Pour chaque console : les touches qu'elle a vraiment, à leur place (croix ou joystick en haut
 * contre le bord, boutons groupés comme sur la vraie manette), et leurs inscriptions (lettres,
 * chiffres, formes géométriques, L1/LB/ZL…).
 *
 * Droits : rien n'est copié. Pas de logo, pas de nom de marque sur les touches, pas les couleurs
 * d'origine des boutons (tout prend la couleur du thème), formes redessinées en traits simples.
 * Les lettres, chiffres et formes de base (croix, rond, carré, triangle) servent seulement à dire
 * quelle touche est laquelle, comme dans tous les émulateurs et les jeux sur PC.
 *
 * Le type de chaque touche est la fonction envoyée au jeu (Libretro.retroId, Ps3Activity,
 * X360Activity) : la touche du bas d'une manette Super Nintendo est donc de type « B », celle d'une
 * manette PlayStation de type « A » (envoyée par position, comme la Croix).
 */
final class ConsolePad {
    private ConsolePad() {
    }

    /** Une touche du groupe de droite : type, position (en diamètres de bouton), taille relative. */
    static final class Key {
        final String type;
        final String cap;
        final float x, y, size;

        Key(String type, String cap, float x, float y, float size) {
            this.type = type;
            this.cap = cap;
            this.x = x;
            this.y = y;
            this.size = size;
        }
    }

    static final class Spec {
        final String family;
        /** Commande du pouce gauche contre le bord (en haut), puis vers le centre (en bas) ; null = aucune. */
        String leftOut = "DPAD", leftIn;
        /** Joystick droit, sous les boutons, vers le centre ; null = aucun. */
        String rightIn;
        final List<Key> face = new ArrayList<Key>();
        /** Gâchettes de chaque côté, de haut en bas : {type, inscription}. */
        final List<String[]> shL = new ArrayList<String[]>(), shR = new ArrayList<String[]>();
        /** Inscriptions de Select et Start ; null = touche absente. */
        String select = "SELECT", start = "START";
        /** Borne d'arcade : un levier au lieu de la croix. */
        boolean lever;
        /** Manette de l'accueil : touches de navigation seulement (pas de sauvegarde rapide ni d'accéléré). */
        boolean nav;

        Spec(String family) {
            this.family = family;
        }

        Spec key(String type, String cap, float x, float y, float size) {
            face.add(new Key(type, cap, x, y, size));
            return this;
        }

        Spec shoulders(String... typeCap) {
            for (int i = 0; i + 3 < typeCap.length + 2 && i + 1 < typeCap.length; i += 2) {
                String[] k = {typeCap[i], typeCap[i + 1]};
                // Les gâchettes gauches commencent par L (L, L2), les droites par R.
                (k[0].startsWith("L") ? shL : shR).add(k);
            }
            return this;
        }

        /** Losange : haut, gauche, droite, bas. */
        Spec diamond(String top, String topCap, String left, String leftCap, String right, String rightCap,
                String bottom, String bottomCap) {
            float d = 0.84f;
            key(top, topCap, 0, -d, 1);
            key(left, leftCap, -d, 0, 1);
            key(right, rightCap, d, 0, 1);
            return key(bottom, bottomCap, 0, d, 1);
        }

        /** Deux boutons en biais (NES, Game Boy…) : celui de gauche plus bas. */
        Spec pair(String left, String leftCap, String right, String rightCap) {
            key(left, leftCap, -0.62f, 0.3f, 1.12f);
            return key(right, rightCap, 0.62f, -0.3f, 1.12f);
        }
    }

    /** Valeur par défaut hors jeu (accueil, éditeur) : une manette moderne neutre. */
    static final String GENERIC = "generic";
    /** Manette réservée à l'écran d'accueil. */
    static final String HOME_PAD = "home";

    static Spec of(String system) {
        String id = system == null ? GENERIC : system;
        Spec s = new Spec(id);
        switch (id) {
            case HOME_PAD:
                // Accueil (0.60.6, demande BOSS) : naviguer dans les menus et rien d'autre.
                // Croix, Ⓐ valider, Ⓑ retour, Ⓧ options, Ⓨ favori, L/R catégories, Home.
                s.diamond("Y", "Y", "X", "X", "B", "B", "A", "A").shoulders("L", "L", "R", "R");
                s.select = null;
                s.start = null;
                s.nav = true;
                break;
            case "nes":
                s.pair("B", "B", "A", "A");
                break;
            case "gb":
            case "gbc":
                s.pair("B", "B", "A", "A");
                break;
            case "gba":
                s.pair("B", "B", "A", "A").shoulders("L", "L", "R", "R");
                break;
            case "snes":
                s.diamond("X", "X", "Y", "Y", "A", "A", "B", "B").shoulders("L", "L", "R", "R");
                break;
            case "nds":
                s.diamond("X", "X", "Y", "Y", "A", "A", "B", "B").shoulders("L", "L", "R", "R");
                break;
            case "3ds":
                s.leftOut = "STICK_L";
                s.leftIn = "DPAD";
                s.diamond("X", "X", "Y", "Y", "A", "A", "B", "B")
                        .shoulders("L2", "ZL", "L", "L", "R2", "ZR", "R", "R");
                break;
            case "switch":
            case "wii":
                // Switch, et Wii avec la manette classique : joystick gauche en haut, croix dessous.
                s.leftOut = "STICK_L";
                s.leftIn = "DPAD";
                s.rightIn = "STICK_R";
                s.diamond("X", "X", "Y", "Y", "A", "A", "B", "B")
                        .shoulders("L2", "ZL", "L", "L", "R2", "ZR", "R", "R");
                s.select = "@-";
                s.start = "@+";
                break;
            case "n64":
                // Croix en haut, joystick au centre ; A et B, et les quatre boutons C en losange
                // (types pris par Libretro.n64 : Y haut, X gauche, R2 droite, Select bas).
                s.leftIn = "STICK_L";
                s.key("A", "A", -0.2f, 0.95f, 1.25f)
                        .key("B", "B", -1.15f, 0.25f, 1.1f)
                        .key("Y", "@cu", 0.55f, -0.95f, 0.72f)
                        .key("X", "@cl", -0.05f, -0.35f, 0.72f)
                        .key("R2", "@cr", 1.15f, -0.35f, 0.72f)
                        .key("SELECT", "@cd", 0.55f, 0.25f, 0.72f)
                        .shoulders("L", "L", "L2", "Z", "R", "R");
                s.select = null;
                break;
            case "gc":
                // Gros A au centre, B en bas à gauche, X à droite, Y en haut ; Z au-dessus de R.
                s.leftOut = "STICK_L";
                s.leftIn = "DPAD";
                s.rightIn = "STICK_R";
                s.key("A", "A", 0, 0.1f, 1.45f)
                        .key("B", "B", -1.1f, 0.75f, 0.85f)
                        .key("X", "X", 1.15f, -0.15f, 0.9f)
                        .key("Y", "Y", -0.15f, -1.15f, 0.9f)
                        .shoulders("L2", "L", "R", "Z", "R2", "R");
                s.select = null;
                break;
            case "psx":
            case "ps2":
            case "ps3":
                s.leftIn = "STICK_L";
                s.rightIn = "STICK_R";
                s.diamond("Y", "@t", "X", "@s", "B", "@o", "A", "@x")
                        .shoulders("L2", "L2", "L", "L1", "R2", "R2", "R", "R1");
                break;
            case "psp":
                s.leftIn = "STICK_L";
                s.diamond("Y", "@t", "X", "@s", "B", "@o", "A", "@x").shoulders("L", "L", "R", "R");
                break;
            case "vita":
                s.leftIn = "STICK_L";
                s.rightIn = "STICK_R";
                s.diamond("Y", "@t", "X", "@s", "B", "@o", "A", "@x").shoulders("L", "L", "R", "R");
                break;
            case "x360":
                s.leftOut = "STICK_L";
                s.leftIn = "DPAD";
                s.rightIn = "STICK_R";
                s.diamond("Y", "Y", "X", "X", "B", "B", "A", "A")
                        .shoulders("L2", "LT", "L", "LB", "R2", "RT", "R", "RB");
                s.select = "BACK";
                break;
            case "dc":
                // Joystick en haut, croix dessous ; gâchettes analogiques L et R.
                s.leftOut = "STICK_L";
                s.leftIn = "DPAD";
                s.diamond("Y", "Y", "X", "X", "B", "B", "A", "A").shoulders("L2", "L", "R2", "R");
                s.select = null;
                break;
            case "md":
                // Trois boutons en arc qui remonte vers la droite (Genesis Plus GX : A = Y, B = B, C = A).
                s.key("X", "A", -1.15f, 0.3f, 1.1f)
                        .key("A", "B", 0, 0, 1.1f)
                        .key("B", "C", 1.15f, -0.3f, 1.1f);
                s.select = "MODE";
                break;
            case "saturn":
                // Six boutons : A B C en bas, X Y Z plus petits au-dessus (Yabause : C = R, Z = L).
                s.key("A", "A", -1.1f, 0.55f, 1)
                        .key("B", "B", 0, 0.3f, 1)
                        .key("R", "C", 1.1f, 0.05f, 1)
                        .key("X", "X", -1.0f, -0.55f, 0.78f)
                        .key("Y", "Y", 0.05f, -0.8f, 0.78f)
                        .key("L", "Z", 1.1f, -1.05f, 0.78f)
                        .shoulders("L2", "L", "R2", "R");
                s.select = null;
                break;
            case "sms":
                s.pair("A", "1", "B", "2");
                s.select = null;
                s.start = "PAUSE";
                break;
            case "gg":
                s.pair("A", "1", "B", "2");
                s.select = null;
                break;
            case "pce":
                // II à gauche, I à droite, comme sur la manette d'origine.
                s.pair("A", "II", "B", "I");
                s.start = "RUN";
                break;
            case "arcade":
                // Levier et quatre boutons en deux rangées décalées.
                s.lever = true;
                s.leftOut = "STICK_L";
                s.key("A", "1", -0.75f, 0.55f, 1.1f)
                        .key("B", "2", 0.45f, 0.4f, 1.1f)
                        .key("X", "3", -0.5f, -0.65f, 1.1f)
                        .key("Y", "4", 0.7f, -0.8f, 1.1f);
                s.select = "COIN";
                break;
            default:
                s.leftOut = "STICK_L";
                s.leftIn = "DPAD";
                s.rightIn = "STICK_R";
                s.diamond("Y", "Y", "X", "X", "B", "B", "A", "A")
                        .shoulders("L2", "L2", "L", "L1", "R2", "R2", "R", "R1");
                break;
        }
        return s;
    }

    /** La manette Flex de cette console, sur la grille de référence 43 × 28 (placée par PadDesign). */
    static ControlLayout build(String system) {
        Spec s = of(system);
        int c = 43, r = 28;
        ControlLayout l = new ControlLayout("Flex");
        l.refCols = c;
        l.refRows = r;
        l.family = s.family;
        for (String[] k : s.shL) l.add(k[0], 1, 1, 6, 3, c, r).cap = k[1];
        for (String[] k : s.shR) l.add(k[0], 36, 1, 6, 3, c, r).cap = k[1];
        if (s.leftOut != null) l.add(s.leftOut, 2, 9, 8, 8, c, r);
        if (s.leftIn != null) l.add(s.leftIn, 10, 18, 8, 8, c, r);
        for (Key k : s.face) {
            int d = Math.max(2, Math.round(4 * k.size));
            l.add(k.type, 32 + Math.round(k.x * 4), 14 + Math.round(k.y * 4), d, d, c, r).cap = k.cap;
        }
        if (s.rightIn != null) l.add(s.rightIn, 24, 18, 7, 7, c, r);
        if (s.select != null) l.add("SELECT", 12, 4, 5, 2, c, r).cap = s.select;
        l.add("HOME", 19, 1, 6, 6, c, r);
        if (s.start != null) l.add("START", 26, 4, 5, 2, c, r).cap = s.start;
        if (!s.nav) {
            l.add("QSAVE", 12, 1, 5, 2, c, r);
            l.add("FAST", 19, 8, 6, 2, c, r);
            l.add("QLOAD", 26, 1, 5, 2, c, r);
        }
        l.kind = "flex";
        l.stock = true;
        return l;
    }

    // ------------------------------------------------------------------ placement

    // Mêmes tailles que PadDesign (cases de 16 dp).
    private static final float M = 1.25f, G = 0.5f, STICK = 8f, DPAD = 8.5f, LEVER = 9f, FACE = 3.6f;
    private static final float SH_W = 5.75f, SH_H = 2.5f, PILL_W = 4f, PILL_H = 1.75f, HOME = 3.5f;

    /** Rayon du groupe de boutons (à l'échelle 1), depuis son centre. */
    private static float faceRadius(Spec s) {
        float r = 0;
        for (Key k : s.face) {
            r = Math.max(r, (float) Math.hypot(k.x, k.y) * FACE + k.size * FACE / 2);
        }
        return r;
    }

    private static float size(String type, Spec s) {
        if (type == null) return 0;
        if (type.equals("DPAD")) return DPAD;
        if (type.equals("STICK_L") && s.lever) return LEVER;
        return STICK;
    }

    /** Cases [x, y, l, h] de chaque touche de la manette Flex, pour une zone de w × h cases. */
    static void place(ControlLayout l, PadDesign.Out o, float w, float h, boolean bottom) {
        Spec s = of(l.family);
        if (bottom && solve(s, w, h, true) < 0.85f && solve(s, w, h, false) > solve(s, w, h, true) + 0.02f) bottom = false;
        float k = solve(s, w, h, bottom);
        float m = M * k, g = G * k;
        // Gâchettes empilées dans les coins du haut.
        float sw = SH_W * k, sh = SH_H * k;
        for (int i = 0; i < s.shL.size(); i++) o.box(s.shL.get(i)[0], m, m + i * (sh + g), sw, sh);
        for (int i = 0; i < s.shR.size(); i++) o.box(s.shR.get(i)[0], w - m - sw, m + i * (sh + g), sw, sh);
        float[] left = side(s, w, h, k, size(s.leftOut, s) * k / 2, size(s.leftIn, s) * k / 2);
        float[] right = side(s, w, h, k, faceRadius(s) * k, size(s.rightIn, s) * k / 2);
        if (s.leftOut != null) o.circle(s.leftOut, left[0], left[1], size(s.leftOut, s) * k / 2);
        if (s.leftIn != null) o.circle(s.leftIn, left[2], left[3], size(s.leftIn, s) * k / 2);
        float fx = w - right[0], fy = right[1];
        for (Key key : s.face) o.circle(key.type, fx + key.x * FACE * k, fy + key.y * FACE * k, key.size * FACE * k / 2);
        if (s.rightIn != null) o.circle(s.rightIn, w - right[2], right[3], size(s.rightIn, s) * k / 2);
        center(o, w, h, k, bottom);
    }

    /**
     * Les deux commandes d'un pouce : la première contre le bord, plus haut, la seconde vers le
     * centre, posée sur le bas ; seule, elle se pose contre le bord en bas.
     */
    private static float[] side(Spec s, float w, float h, float k, float rOut, float rIn) {
        float m = M * k, g = G * k;
        if (rIn <= 0) {
            float y = h - m - rOut - Math.max(0, (h - 2 * m - 2 * rOut) * 0.12f);
            return new float[] {m + rOut, y, 0, 0};
        }
        float d = rOut + rIn + 2 * g;
        float room = w / 2 - g - m - rOut - rIn;
        float dx = Math.max(0, Math.min(0.8f * d, room));
        float dy = (float) Math.sqrt(Math.max(0, d * d - dx * dx));
        float yIn = h - m - rIn;
        return new float[] {m + rOut, yIn - dy, m + rOut + dx, yIn};
    }

    /** Maison au milieu, Select et Start de chaque côté, Accélérer et sauvegardes rapides à côté. */
    private static void center(PadDesign.Out o, float w, float h, float s, boolean bottom) {
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

    /** La plus grande échelle (0,45 à 1,15, 1,35 en rétro) à laquelle tout tient sans chevauchement. */
    private static float solve(Spec s, float w, float h, boolean bottom) {
        // Manettes rétro (croix et boutons seulement) : plus de place, touches un peu plus grandes.
        float lo = 0.45f, hi = s.leftIn == null && s.rightIn == null ? 1.35f : 1.15f;
        if (fits(s, w, h, hi, bottom)) return hi;
        for (int i = 0; i < 18; i++) {
            float mid = (lo + hi) / 2;
            if (fits(s, w, h, mid, bottom)) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    private static boolean fits(Spec s, float w, float h, float k, boolean bottom) {
        float m = M * k, g = G * k;
        float ch = (HOME / 2 + G + PILL_W) * k, cH = (HOME + G + PILL_H) * k;
        int stack = Math.max(s.shL.size(), s.shR.size());
        float shBottom = stack == 0 ? 0 : m + stack * SH_H * k + (stack - 1) * g;
        if (stack > 0 && !bottom && m + SH_W * k + g > w / 2 - ch) return false;
        float cTop = bottom ? h - m - cH : m, cBottom = bottom ? h - m : m + cH;
        float[] rOut = {size(s.leftOut, s) * k / 2, faceRadius(s) * k};
        float[] rIn = {size(s.leftIn, s) * k / 2, size(s.rightIn, s) * k / 2};
        for (int i = 0; i < 2; i++) {
            float[] p = side(s, w, h, k, rOut[i], rIn[i]);
            if (rOut[i] > 0) {
                if (p[0] + rOut[i] > w / 2 - g / 2) return false;
                if (p[1] - rOut[i] < shBottom + g) return false;
                if (!clear(p[0], p[1], rOut[i], w, ch, cTop, cBottom, g)) return false;
            }
            if (rIn[i] > 0) {
                if (p[2] + rIn[i] > w / 2 - g / 2) return false;
                if (!clear(p[2], p[3], rIn[i], w, ch, cTop, cBottom, g)) return false;
            }
        }
        return true;
    }

    private static boolean clear(float x, float y, float r, float w, float ch, float cTop, float cBottom, float g) {
        boolean beside = x + r <= w / 2 - ch - g;
        boolean apart = y - r >= cBottom + g || y + r <= cTop - g;
        return beside || apart;
    }
}
