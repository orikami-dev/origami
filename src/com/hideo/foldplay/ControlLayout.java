package com.hideo.foldplay;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Disposition de la manette du bas : quelles touches, où, de quelle taille, et l'image de fond.
 *
 * Les positions sont des fractions de la zone (0..1, coin haut-gauche) pour suivre tous les écrans ;
 * les tailles sont en cases de la grille (une case = {@link #CELL_DP} dp) pour que les boutons
 * gardent leurs proportions. À l'affichage, tout est aimanté sur la grille.
 */
final class ControlLayout {
    static final int CELL_DP = 16;

    /** Types de touches disponibles, dans l'ordre du menu « Ajouter ». */
    static final String[] TYPES = {"DPAD", "STICK_L", "STICK_R", "A", "B", "X", "Y", "L", "R", "L2", "R2",
        "L3", "R3", "SELECT", "START", "HOME", "TOUCHPAD", "FAST", "QSAVE", "QLOAD"};

    /** Nom affiché dans le menu « Ajouter ». */
    static String label(String type) {
        if (type.equals("DPAD")) return "Croix directionnelle";
        if (type.equals("STICK_L")) return "Joystick gauche";
        if (type.equals("STICK_R")) return "Joystick droit";
        if (type.equals("L")) return "L1 (ou L)";
        if (type.equals("R")) return "R1 (ou R)";
        if (type.equals("L3")) return "L3 (clic joystick gauche)";
        if (type.equals("R3")) return "R3 (clic joystick droit)";
        if (type.equals("HOME")) return "Home";
        if (type.equals("TOUCHPAD")) return "Pavé tactile";
        if (type.equals("FAST")) return "Accélérer le jeu";
        if (type.equals("QSAVE")) return "Sauvegarde rapide";
        if (type.equals("QLOAD")) return "Chargement rapide";
        return type;
    }

    static final class Control {
        String type;
        float x;
        float y;
        int w;
        int h;
        /** Couleur propre à cette touche (0 = celle de la disposition). */
        int color;
        /** Inscription de la console (ConsolePad) : lettre, chiffre, « @x » croix… ; null = le type. */
        String cap;

        Control(String type, float x, float y, int w, int h) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        Control copy() {
            Control c = new Control(type, x, y, w, h);
            c.color = color;
            c.cap = cap;
            return c;
        }
    }

    String name;
    final List<Control> controls = new ArrayList<Control>();
    String background;
    float bgX;
    float bgY;
    float bgScale = 1f;
    /** Grille (colonnes × lignes) sur laquelle les tailles ont été choisies ; 0 = inconnue. */
    int refCols;
    int refRows;
    // Style : teinte du fond, des touches, couleur d'accent, opacités (0..100).
    int deckColor = Theme.PANEL0;
    int buttonColor = Theme.STEEL0;
    int accentColor = Theme.CODEC0;
    int deckAlpha = 100;
    int buttonAlpha = 100;
    /** Disposition d'origine jamais retouchée : elle est redessinée pour chaque zone. */
    boolean stock;
    /** Disposition d'origine dont elle est issue : "classic" ou "flex" (divisée). */
    String kind = "classic";
    /** Divisée façon clavier Samsung : deux moitiés sur les bords, fond seulement sous chacune. */
    boolean split;
    /** Disposition divisée : taille des moitiés (%), décalage de chacune en cases, moitiés liées. */
    int splitScale = 100;
    float leftDx, leftDy, rightDx, rightDy;
    boolean splitLinked = true;
    /** Habillage : "codec" (relief, liserés, motif) par défaut, ou "plain" (aplats). */
    String skin = "codec";
    /** Flex : console dont la manette a la forme (ConsolePad) ; null = manette neutre. */
    String family;

    ControlLayout(String name) {
        this.name = name;
    }

    ControlLayout copy(String newName) {
        ControlLayout c = new ControlLayout(newName);
        for (Control k : controls) c.controls.add(k.copy());
        c.background = background;
        c.bgX = bgX;
        c.bgY = bgY;
        c.bgScale = bgScale;
        c.refCols = refCols;
        c.refRows = refRows;
        c.deckColor = deckColor;
        c.buttonColor = buttonColor;
        c.accentColor = accentColor;
        c.deckAlpha = deckAlpha;
        c.buttonAlpha = buttonAlpha;
        c.stock = stock;
        c.kind = kind;
        c.split = split;
        c.splitScale = splitScale;
        c.leftDx = leftDx;
        c.leftDy = leftDy;
        c.rightDx = rightDx;
        c.rightDy = rightDy;
        c.splitLinked = splitLinked;
        c.skin = skin;
        c.family = family;
        return c;
    }

    /** Reprend le style d'une autre disposition : couleurs, opacités, habillage, couleurs des touches. */
    void copyStyle(ControlLayout s) {
        deckColor = s.deckColor;
        buttonColor = s.buttonColor;
        accentColor = s.accentColor;
        deckAlpha = s.deckAlpha;
        buttonAlpha = s.buttonAlpha;
        skin = s.skin;
        for (Control c : controls) {
            c.color = 0;
            for (Control k : s.controls) if (k.type.equals(c.type)) c.color = k.color;
        }
    }

    /** La disposition par défaut : la « 3D » façon manette Xbox. */
    static ControlLayout classic(int cols, int rows) {
        return xbox();
    }

    /**
     * « 3D » : touches placées comme sur une manette Xbox (0.42), sur la grille de référence 43 × 28
     * (téléphone ouvert, en portrait) : joystick gauche en haut et croix en dessous, vers le centre ;
     * A B X Y en haut à droite et joystick droit en dessous, vers le centre ; Maison au milieu,
     * Select et Start de chaque côté. {@link #arrange} l'adapte à toutes les zones et rotations.
     */
    static ControlLayout xbox() {
        int c = 43;
        int r = 28;
        ControlLayout l = new ControlLayout("3D");
        l.refCols = c;
        l.refRows = r;
        l.add("L2", 1, 1, 8, 3, c, r);
        l.add("L", 1, 5, 8, 3, c, r);
        l.add("R2", 34, 1, 8, 3, c, r);
        l.add("R", 34, 5, 8, 3, c, r);
        l.add("QSAVE", 12, 1, 5, 2, c, r);
        l.add("SELECT", 12, 4, 5, 2, c, r);
        l.add("HOME", 19, 1, 6, 6, c, r);
        l.add("FAST", 19, 8, 6, 2, c, r);
        l.add("QLOAD", 26, 1, 5, 2, c, r);
        l.add("START", 26, 4, 5, 2, c, r);
        l.add("STICK_L", 2, 9, 9, 9, c, r);
        l.add("DPAD", 11, 19, 7, 7, c, r);
        l.add("Y", 32, 9, 5, 5, c, r);
        l.add("X", 27, 13, 5, 5, c, r);
        l.add("B", 37, 13, 5, 5, c, r);
        l.add("A", 32, 17, 5, 5, c, r);
        l.add("STICK_R", 21, 19, 7, 7, c, r);
        l.kind = "3d";
        l.stock = true;
        return l;
    }

    /**
     * « 2D » : panneau de borne d'arcade. Levier à gauche (il envoie aussi les directions), quatre
     * boutons ronds à droite en deux rangées décalées façon Vewlix (Y X en haut, B A en bas), sans
     * gâchettes comme sur une vraie borne, Select (pièce) et Start en haut. Placement : PadDesign.
     */
    static ControlLayout arcade() {
        int c = 43;
        int r = 28;
        ControlLayout l = new ControlLayout("2D");
        l.refCols = c;
        l.refRows = r;
        l.add("QSAVE", 1, 1, 5, 2, c, r);
        l.add("SELECT", 7, 1, 5, 2, c, r);
        l.add("HOME", 19, 1, 6, 6, c, r);
        l.add("FAST", 19, 8, 6, 2, c, r);
        l.add("START", 31, 1, 5, 2, c, r);
        l.add("QLOAD", 37, 1, 5, 2, c, r);
        l.add("STICK_L", 3, 11, 10, 10, c, r);
        l.add("Y", 23, 13, 5, 5, c, r);
        l.add("X", 29, 11, 5, 5, c, r);
        l.add("B", 22, 19, 5, 5, c, r);
        l.add("A", 28, 17, 5, 5, c, r);
        l.kind = "2d";
        l.stock = true;
        return l;
    }

    /**
     * « Flex » (0.60.3), la manette phare : elle prend la forme de la manette de la console du jeu
     * (ConsolePad), touches et inscriptions comprises, et se replace à chaque rotation (PadDesign).
     * Hors jeu, une manette neutre.
     */
    static ControlLayout flex() {
        return ConsolePad.build(null);
    }

    /** La Flex de cette console, avec le style (couleurs, fond, habillage) de celle-ci. */
    ControlLayout forSystem(String system) {
        ControlLayout l = ConsolePad.build(system);
        l.name = name;
        l.deckColor = deckColor;
        l.buttonColor = buttonColor;
        l.accentColor = accentColor;
        l.deckAlpha = deckAlpha;
        l.buttonAlpha = buttonAlpha;
        l.background = background;
        l.bgX = bgX;
        l.bgY = bgY;
        l.bgScale = bgScale;
        l.skin = skin;
        return l;
    }

    /**
     * « Mini » : petite manette carrée qui flotte par-dessus le jeu (le jeu passe en plein écran),
     * déplaçable et redimensionnable en jeu. Grille de référence 20 × 20.
     */
    static ControlLayout mini() {
        int c = 20;
        int r = 20;
        ControlLayout l = new ControlLayout("Mini");
        l.refCols = c;
        l.refRows = r;
        l.add("L", 1, 1, 5, 2, c, r);
        l.add("HOME", 8, 1, 4, 4, c, r);
        l.add("R", 14, 1, 5, 2, c, r);
        l.add("DPAD", 1, 8, 8, 8, c, r);
        l.add("Y", 13, 7, 3, 3, c, r);
        l.add("X", 10, 10, 3, 3, c, r);
        l.add("B", 16, 10, 3, 3, c, r);
        l.add("A", 13, 13, 3, 3, c, r);
        l.add("SELECT", 3, 17, 5, 2, c, r);
        l.add("START", 12, 17, 5, 2, c, r);
        l.kind = "mini";
        l.stock = true;
        return l;
    }

    /** Flex : ses deux moitiés se posent sur les bords, par-dessus le jeu ou l'accueil en plein écran. */
    boolean overlaid() {
        return split;
    }

    /** Manette flottante (Mini) : un carré posé sur le jeu en plein écran. */
    boolean floating() {
        return kind.equals("mini");
    }

    /** Les dispositions fournies, dans l'ordre des profils : Flex, 2D, 3D, puis Mini (en chantier). */
    static List<ControlLayout> defaults() {
        List<ControlLayout> out = new ArrayList<ControlLayout>();
        out.add(flex());
        out.add(arcade());
        out.add(xbox());
        out.add(mini());
        return out;
    }

    /** La disposition d'origine de ce genre, pour la redessiner ou la réinitialiser. */
    ControlLayout origin(int cols, int rows) {
        if (kind.equals("flex")) return ConsolePad.build(family);
        if (kind.equals("2d")) return arcade();
        if (kind.equals("mini")) return mini();
        return xbox();
    }

    /** La classique des versions 0.9 à 0.13, pour reconnaître une disposition jamais retouchée. */
    private static ControlLayout legacyClassic(int cols, int rows) {
        ControlLayout l = new ControlLayout("Classique");
        cols = Math.max(cols, 20);
        rows = Math.max(rows, 16);
        l.refCols = cols;
        l.refRows = rows;
        int face = 4;
        int dpad = 9;
        l.add("L", 1, 2, 8, 4, cols, rows);
        l.add("R", cols - 9, 2, 8, 4, cols, rows);
        l.add("DPAD", 1, rows - dpad - 2, dpad, dpad, cols, rows);
        int cx = cols - 7;
        int cy = rows - 8;
        l.add("Y", cx - face / 2, cy - face - 1, face, face, cols, rows);
        l.add("X", cx - face - 2, cy - face / 2, face, face, cols, rows);
        l.add("B", cx + 2, cy - face / 2, face, face, cols, rows);
        l.add("A", cx - face / 2, cy + 1, face, face, cols, rows);
        int mid = cols / 2;
        // Disposition refaite par BOSS : Maison 5×5 entre Select et Start, Accélérer au-dessus.
        l.add("SELECT", mid - 8, rows - 5, 5, 2, cols, rows);
        l.add("HOME", mid - 2, rows - 6, 5, 5, cols, rows);
        l.add("START", mid + 4, rows - 5, 5, 2, cols, rows);
        l.add("FAST", mid - 2, rows - 9, 5, 2, cols, rows);
        return l;
    }

    /**
     * Recale la disposition sur la grille de l'écran courant (l'éditeur le fait avant la première
     * retouche) : mêmes positions qu'à l'affichage, arrondies aux cases.
     */
    void rebase(int cols, int rows) {
        if (refCols <= 0 || refRows <= 0) {
            refCols = cols;
            refRows = rows;
            stock = false;
            return;
        }
        if (refCols == cols && refRows == rows && !stock && !split) return;
        float[][] boxes = arrange(cols, rows);
        for (int i = 0; i < controls.size(); i++) {
            Control c = controls.get(i);
            float[] b = boxes[i];
            boolean square = Math.abs(b[2] - b[3]) < 0.01f;
            c.w = Math.max(2, Math.round(b[2]));
            c.h = square ? c.w : Math.max(2, Math.round(b[3]));
            c.x = (float) Math.round(b[0]) / cols;
            c.y = (float) Math.round(b[1]) / rows;
        }
        refCols = cols;
        refRows = rows;
        stock = false;
        splitScale = 100;
        leftDx = leftDy = rightDx = rightDy = 0;
    }

    /**
     * Place toutes les touches sur une grille cols × rows : [x, y, l, h] en cases (fractionnaires).
     *
     * Sur la grille d'origine, rien ne bouge. Ailleurs, les touches qui se touchent forment un bloc
     * (la croix, le losange A B X Y, Select-Maison-Start…). Chaque bloc reste collé à son bord
     * (gauche, centre ou droite ; haut, milieu ou bas) et tous sont mis à la même échelle, la plus
     * grande possible sans dépasser 1 ni faire se chevaucher deux touches.
     */
    float[][] arrange(int cols, int rows) {
        if (stock) {
            // Manette fournie : placement dessiné (PadDesign), recalé sur la grille centrée.
            float[][] drawn = designed(cols, rows);
            if (drawn != null) return drawn;
            ControlLayout d = origin(cols, rows);
            if (sameKeys(d)) {
                d.splitScale = splitScale;
                d.leftDx = leftDx;
                d.leftDy = leftDy;
                d.rightDx = rightDx;
                d.rightDy = rightDy;
                return d.fit(cols, rows);
            }
        }
        return fit(cols, rows);
    }

    /**
     * Taille réelle de la zone (en cases, fractionnaires) et position de la rangée Maison, fournies
     * par l'affichage ; sans elles, la zone est supposée égale à la grille.
     */
    float viewW, viewH;
    boolean viewBottomCenter;

    /** Placement dessiné, exprimé dans la grille cols × rows centrée dans la zone réelle. */
    private float[][] designed(int cols, int rows) {
        float w = viewW > 0 ? viewW : cols, h = viewH > 0 ? viewH : rows;
        if (!sameKeys(origin(cols, rows))) return null;
        float[][] d = PadDesign.place(this, w, h, viewBottomCenter);
        if (d == null) return null;
        float ox = (w - cols) / 2f, oy = (h - rows) / 2f;
        for (float[] b : d) {
            b[0] -= ox;
            b[1] -= oy;
        }
        return d;
    }

    private boolean sameKeys(ControlLayout d) {
        if (d.controls.size() != controls.size()) return false;
        for (int i = 0; i < controls.size(); i++) {
            if (!d.controls.get(i).type.equals(controls.get(i).type)) return false;
        }
        return true;
    }

    private float[][] fit(int cols, int rows) {
        int n = controls.size();
        float[][] out = new float[n][];
        if (refCols <= 0 || refRows <= 0 || (refCols == cols && refRows == rows && !split)) {
            for (int i = 0; i < n; i++) {
                Control c = controls.get(i);
                float x = Math.max(0, Math.min(Math.max(0, cols - c.w), Math.round(c.x * cols)));
                float y = Math.max(0, Math.min(Math.max(0, rows - c.h), Math.round(c.y * rows)));
                out[i] = new float[] {x, y, c.w, c.h};
            }
            return out;
        }
        float[][] ref = new float[n][];
        for (int i = 0; i < n; i++) {
            Control c = controls.get(i);
            ref[i] = new float[] {c.x * refCols, c.y * refRows, c.w, c.h};
        }
        int[] sides = new int[n];
        if (split) moveHalves(ref, sides);
        // Blocs : touches qui se touchent ou presque (jusqu'à une case d'écart, celui des boutons
        // de la 2D et du losange de la 3D, pour qu'un panneau ne s'éparpille pas en paysage).
        // En disposition divisée, chaque moitié forme un seul bloc : elle bouge d'un tenant.
        int[] group = new int[n];
        for (int i = 0; i < n; i++) group[i] = i;
        int[] firstOfSide = {-1, -1, -1};
        for (int i = 0; i < n && split; i++) {
            if (sides[i] == 0) continue;
            if (firstOfSide[sides[i]] < 0) firstOfSide[sides[i]] = i;
            else group[i] = firstOfSide[sides[i]];
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (near(ref[i], ref[j], 1.5f)) {
                    int gi = root(group, i);
                    int gj = root(group, j);
                    if (gi != gj) group[gj] = gi;
                }
            }
        }
        float[] ax = new float[n];
        float[] ay = new float[n];
        for (int g = 0; g < n; g++) {
            float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (root(group, i) != g) continue;
                l = Math.min(l, ref[i][0]);
                t = Math.min(t, ref[i][1]);
                r = Math.max(r, ref[i][0] + ref[i][2]);
                b = Math.max(b, ref[i][1] + ref[i][3]);
            }
            if (l == Float.MAX_VALUE) continue;
            ax[g] = third((l + r) / 2 / refCols);
            ay[g] = third((t + b) / 2 / refRows);
        }
        float rx = (float) cols / refCols;
        float ry = (float) rows / refRows;
        float lo = Math.min(1f, Math.min(rx, ry));
        float hi = Math.min(1f, Math.max(rx, ry));
        float[][] best = at(lo, ref, group, ax, ay, cols, rows);
        if (hi > lo) {
            float[][] top = at(hi, ref, group, ax, ay, cols, rows);
            if (fits(top, ref, cols, rows)) {
                best = top;
            } else {
                for (int step = 0; step < 14; step++) {
                    float mid = (lo + hi) / 2;
                    float[][] m = at(mid, ref, group, ax, ay, cols, rows);
                    if (fits(m, ref, cols, rows)) {
                        lo = mid;
                        best = m;
                    } else {
                        hi = mid;
                    }
                }
            }
        }
        for (float[] b : best) {
            b[0] = Math.max(0, Math.min(Math.max(0, cols - b[2]), b[0]));
            b[1] = Math.max(0, Math.min(Math.max(0, rows - b[3]), b[1]));
        }
        return best;
    }

    /** Côté d'une touche en disposition divisée : 1 gauche, 2 droite, 0 au centre. */
    int sideOf(Control c) {
        float center = c.x + c.w / 2f / Math.max(1, refCols);
        return center < 1f / 3 ? 1 : center > 2f / 3 ? 2 : 0;
    }

    /**
     * Taille et place des deux moitiés : agrandies depuis leur coin bas extérieur, puis décalées.
     * Une moitié reste dans la zone et ne vient jamais sur les touches du centre ni sur l'autre moitié.
     */
    private void moveHalves(float[][] ref, int[] sides) {
        for (int i = 0; i < ref.length; i++) sides[i] = sideOf(controls.get(i));
        for (int side = 1; side <= 2; side++) {
            float[] bb = bounds(ref, sides, side);
            if (bb == null) continue;
            float l = bb[0], t = bb[1], r = bb[2], b = bb[3];
            // Pas plus grande que la hauteur de la zone, ni que 45 % de sa largeur.
            float s = Math.min(splitScale / 100f, Math.min(refRows / (b - t), refCols * 0.45f / (r - l)));
            float ax = side == 1 ? l : r;
            float dx = side == 1 ? leftDx : rightDx;
            float dy = side == 1 ? leftDy : rightDy;
            float nl = ax + (l - ax) * s + dx;
            float nr = ax + (r - ax) * s + dx;
            float nt = b + (t - b) * s + dy;
            float nb = b + dy;
            if (nl < 0) dx -= nl;
            if (nr > refCols) dx -= nr - refCols;
            if (nt < 0) dy -= nt;
            if (nb > refRows) dy -= nb - refRows;
            for (int i = 0; i < ref.length; i++) {
                if (sides[i] != side) continue;
                ref[i][0] = ax + (ref[i][0] - ax) * s + dx;
                ref[i][1] = b + (ref[i][1] - b) * s + dy;
                ref[i][2] *= s;
                ref[i][3] *= s;
            }
        }
        // Repousse chaque moitié vers son bord si elle touche le centre ou l'autre moitié.
        for (int side = 1; side <= 2; side++) {
            float[] bb = bounds(ref, sides, side);
            if (bb == null) continue;
            float push = 0;
            for (int i = 0; i < ref.length; i++) {
                if (sides[i] == side) continue;
                float[] o = ref[i];
                if (o[1] >= bb[3] + 0.5f || o[1] + o[3] <= bb[1] - 0.5f) continue;
                if (side == 1) push = Math.max(push, bb[2] - (o[0] - 0.5f));
                else push = Math.max(push, (o[0] + o[2] + 0.5f) - bb[0]);
            }
            if (push <= 0) continue;
            float room = side == 1 ? bb[0] : refCols - bb[2];
            push = Math.min(push, Math.max(0, room));
            for (int i = 0; i < ref.length; i++) {
                if (sides[i] == side) ref[i][0] += side == 1 ? -push : push;
            }
        }
    }

    private static float[] bounds(float[][] ref, int[] sides, int side) {
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        for (int i = 0; i < ref.length; i++) {
            if (sides[i] != side) continue;
            l = Math.min(l, ref[i][0]);
            t = Math.min(t, ref[i][1]);
            r = Math.max(r, ref[i][0] + ref[i][2]);
            b = Math.max(b, ref[i][1] + ref[i][3]);
        }
        return l == Float.MAX_VALUE ? null : new float[] {l, t, r, b};
    }

    /** Les touches à l'échelle k, chaque bloc gardant sa distance (mise à l'échelle) à son bord. */
    private float[][] at(float k, float[][] ref, int[] group, float[] ax, float[] ay, int cols, int rows) {
        float[][] out = new float[ref.length][];
        for (int i = 0; i < ref.length; i++) {
            int g = root(group, i);
            float[] r = ref[i];
            out[i] = new float[] {
                ax[g] * cols + (r[0] - ax[g] * refCols) * k,
                ay[g] * rows + (r[1] - ay[g] * refRows) * k,
                r[2] * k,
                r[3] * k,
            };
        }
        return out;
    }

    /** Tout tient dans la grille et aucune touche ne vient coller ou recouvrir une autre (sauf si c'était déjà le cas). */
    private static boolean fits(float[][] b, float[][] ref, int cols, int rows) {
        float eps = 0.05f;
        for (int i = 0; i < b.length; i++) {
            if (b[i][0] < -eps || b[i][1] < -eps || b[i][0] + b[i][2] > cols + eps || b[i][1] + b[i][3] > rows + eps) {
                return false;
            }
            for (int j = i + 1; j < b.length; j++) {
                // Deux touches séparées d'au moins une demi-case le restent (un peu moins toléré).
                if (near(b[i], b[j], 0.4f) && !near(ref[i], ref[j], 0.4f)) return false;
            }
        }
        return true;
    }

    /** Vrai si les deux cases se recouvrent, ou sont à moins de « gap » cases l'une de l'autre. */
    private static boolean near(float[] a, float[] b, float gap) {
        return a[0] < b[0] + b[2] + gap && b[0] < a[0] + a[2] + gap
                && a[1] < b[1] + b[3] + gap && b[1] < a[1] + a[3] + gap;
    }

    private static int root(int[] group, int i) {
        while (group[i] != i) i = group[i];
        return i;
    }

    private static float third(float center) {
        return center < 1f / 3 ? 0f : center > 2f / 3 ? 1f : 0.5f;
    }

    /**
     * Manette superposée sur un écran en paysage : comme sur une manette, chaque pouce a sa colonne
     * contre son bord (gâchettes en haut, croix ou boutons au milieu, joystick en bas), Select,
     * Maison et Start en haut au centre, et une case de marge tout autour pour que rien ne déborde
     * ni ne passe sous les coins arrondis. Les touches gardent leur taille, réduites ensemble
     * seulement si la hauteur manque.
     */
    static ControlLayout wideOverlay(ControlLayout base, int cols, int rows) {
        ControlLayout l = base.copy(base.name);
        float[][] fallback = base.arrange(cols, rows);
        int n = l.controls.size();
        float rc = base.refCols > 0 ? base.refCols : cols;
        float rr = base.refRows > 0 ? base.refRows : rows;
        float[][] ref = new float[n][];
        int[] role = new int[n];
        for (int i = 0; i < n; i++) {
            Control c = l.controls.get(i);
            ref[i] = new float[] {c.x * rc, c.y * rr, c.w, c.h};
            role[i] = wideRole(c.type);
            // Gâchette carrée = bouton rond du panneau d'arcade (2D) : elle reste avec A B X Y.
            if ((role[i] == R_LSH || role[i] == R_RSH) && c.w == c.h) {
                role[i] = ref[i][0] + ref[i][2] / 2 > rc / 2 ? R_FACE : R_DPAD;
            }
        }
        compactTop(ref, role, rc);
        final float m = 1f;
        final float gap = 1f;
        // Hauteur de chaque colonne : gâchettes, puis bloc du milieu, puis joystick.
        float[] need = new float[2];
        float[] width = new float[2];
        for (int side = 0; side < 2; side++) {
            float sh = stackHeight(ref, role, side == 0 ? R_LSH : R_RSH);
            float mid = groupBox(ref, role, side == 0 ? R_DPAD : R_FACE)[3];
            float st = groupBox(ref, role, side == 0 ? R_STL : R_STR)[3];
            int parts = (sh > 0 ? 1 : 0) + (mid > 0 ? 1 : 0) + (st > 0 ? 1 : 0);
            need[side] = sh + mid + st + Math.max(0, parts - 1) * gap;
            width[side] = Math.max(stackWidth(ref, role, side == 0 ? R_LSH : R_RSH),
                    Math.max(groupBox(ref, role, side == 0 ? R_DPAD : R_FACE)[2],
                            groupBox(ref, role, side == 0 ? R_STL : R_STR)[2]));
        }
        float[] top = groupBox(ref, role, R_TOP);
        float k = Math.min(1f, (rows - 2 * m) / Math.max(1f, Math.max(need[0], need[1])));
        float room = cols - 2 * m - 2 * gap;
        float wide = width[0] + width[1] + top[2];
        if (wide * k > room) k = Math.max(0.3f, room / wide);
        float[][] out = new float[n][];
        for (int side = 0; side < 2; side++) {
            int shRole = side == 0 ? R_LSH : R_RSH;
            int midRole = side == 0 ? R_DPAD : R_FACE;
            int stRole = side == 0 ? R_STL : R_STR;
            float colW = width[side] * k;
            float colX = side == 0 ? m : cols - m - colW;
            // Gâchettes empilées du haut, avec une case entre elles.
            float y = m;
            for (int i = 0; i < n; i++) {
                if (role[i] != shRole) continue;
                float w = ref[i][2] * k, h = ref[i][3] * k;
                out[i] = new float[] {side == 0 ? colX : colX + colW - w, y, w, h};
                y += h + gap;
            }
            float midTop = y > m ? y : m;
            // Croix (ou A B X Y) et joystick dans l'ordre du dessin d'origine : sur la 3D, le joystick
            // gauche est au-dessus de la croix, comme sur une manette Xbox. Le second va en bas, le
            // premier se centre dans la place qui reste.
            float[] st = groupBox(ref, role, stRole);
            float[] mid = groupBox(ref, role, midRole);
            boolean stickFirst = st[3] > 0 && mid[3] > 0 && st[1] < mid[1];
            int firstRole = stickFirst ? stRole : midRole;
            int lastRole = stickFirst ? midRole : stRole;
            float[] first = stickFirst ? st : mid;
            float[] last = stickFirst ? mid : st;
            float lastTop = rows - m - last[3] * k;
            if (last[3] > 0) placeGroup(ref, role, lastRole, k, out, colX + (colW - last[2] * k) / 2, lastTop);
            if (first[3] > 0) {
                float bottom = last[3] > 0 ? lastTop - gap : rows - m;
                float my = midTop + Math.max(0, (bottom - midTop - first[3] * k) / 2);
                placeGroup(ref, role, firstRole, k, out, colX + (colW - first[2] * k) / 2, my);
            }
        }
        if (top[3] > 0) placeGroup(ref, role, R_TOP, k, out, (cols - top[2] * k) / 2, m);
        for (int i = 0; i < n; i++) {
            Control c = l.controls.get(i);
            float[] b = out[i] != null ? out[i] : fallback[i];
            boolean square = c.w == c.h;
            int w = Math.max(2, Math.round(b[2]));
            int h = square ? w : Math.max(2, Math.round(b[3]));
            float x = Math.max(0, Math.min(cols - w, Math.round(b[0])));
            float yy = Math.max(0, Math.min(rows - h, Math.round(b[1])));
            c.w = w;
            c.h = h;
            c.x = x / cols;
            c.y = yy / rows;
        }
        l.refCols = cols;
        l.refRows = rows;
        l.stock = false;
        l.split = false;
        l.splitScale = 100;
        l.leftDx = l.leftDy = l.rightDx = l.rightDy = 0;
        return l;
    }

    /**
     * Rangée du haut resserrée : Select, Start, sauvegarde et chargement rapides posés aux coins
     * (comme sur la 2D) viennent se coller de part et d'autre de Maison, au lieu d'occuper toute la
     * largeur entre les deux colonnes.
     */
    private static void compactTop(float[][] ref, int[] role, float rc) {
        float cl = Float.MAX_VALUE, cr = -Float.MAX_VALUE;
        float lr = -Float.MAX_VALUE, rl = Float.MAX_VALUE;
        for (int i = 0; i < ref.length; i++) {
            if (role[i] != R_TOP) continue;
            float c = (ref[i][0] + ref[i][2] / 2) / rc;
            if (c < 1f / 3) {
                lr = Math.max(lr, ref[i][0] + ref[i][2]);
            } else if (c > 2f / 3) {
                rl = Math.min(rl, ref[i][0]);
            } else {
                cl = Math.min(cl, ref[i][0]);
                cr = Math.max(cr, ref[i][0] + ref[i][2]);
            }
        }
        if (cl == Float.MAX_VALUE) return;
        float dl = lr > -Float.MAX_VALUE ? Math.max(0, cl - 1 - lr) : 0;
        float dr = rl < Float.MAX_VALUE ? Math.max(0, rl - (cr + 1)) : 0;
        for (int i = 0; i < ref.length; i++) {
            if (role[i] != R_TOP) continue;
            float c = (ref[i][0] + ref[i][2] / 2) / rc;
            if (c < 1f / 3) ref[i][0] += dl;
            else if (c > 2f / 3) ref[i][0] -= dr;
        }
    }

    private static final int R_OTHER = 0, R_LSH = 1, R_RSH = 2, R_DPAD = 3, R_FACE = 4, R_STL = 5, R_STR = 6, R_TOP = 7;

    private static int wideRole(String t) {
        if (t.equals("L2") || t.equals("L")) return R_LSH;
        if (t.equals("R2") || t.equals("R")) return R_RSH;
        if (t.equals("DPAD")) return R_DPAD;
        if (t.equals("A") || t.equals("B") || t.equals("X") || t.equals("Y")) return R_FACE;
        if (t.equals("STICK_L")) return R_STL;
        if (t.equals("STICK_R")) return R_STR;
        if (t.equals("SELECT") || t.equals("START") || t.equals("HOME") || t.equals("FAST")
                || t.equals("QSAVE") || t.equals("QLOAD")) return R_TOP;
        return R_OTHER;
    }

    /** [x, y, l, h] du groupe dans la grille d'origine ; l et h nuls si le groupe est vide. */
    private static float[] groupBox(float[][] ref, int[] role, int r) {
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, rt = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        for (int i = 0; i < ref.length; i++) {
            if (role[i] != r) continue;
            l = Math.min(l, ref[i][0]);
            t = Math.min(t, ref[i][1]);
            rt = Math.max(rt, ref[i][0] + ref[i][2]);
            b = Math.max(b, ref[i][1] + ref[i][3]);
        }
        return l == Float.MAX_VALUE ? new float[] {0, 0, 0, 0} : new float[] {l, t, rt - l, b - t};
    }

    private static float stackHeight(float[][] ref, int[] role, int r) {
        float h = 0;
        int count = 0;
        for (int i = 0; i < ref.length; i++) {
            if (role[i] != r) continue;
            h += ref[i][3];
            count++;
        }
        return h + Math.max(0, count - 1);
    }

    private static float stackWidth(float[][] ref, int[] role, int r) {
        float w = 0;
        for (int i = 0; i < ref.length; i++) if (role[i] == r) w = Math.max(w, ref[i][2]);
        return w;
    }

    /** Place un groupe à l'échelle k, son coin haut gauche en (x, y), en gardant son agencement. */
    private static void placeGroup(float[][] ref, int[] role, int r, float k, float[][] out, float x, float y) {
        float[] g = groupBox(ref, role, r);
        for (int i = 0; i < ref.length; i++) {
            if (role[i] != r) continue;
            out[i] = new float[] {x + (ref[i][0] - g[0]) * k, y + (ref[i][1] - g[1]) * k, ref[i][2] * k, ref[i][3] * k};
        }
    }

    Control add(String type, int cx, int cy, int w, int h, int cols, int rows) {
        Control c = new Control(type, (float) cx / cols, (float) cy / rows, w, h);
        controls.add(c);
        return c;
    }

    static boolean pill(String type) {
        return type.equals("SELECT") || type.equals("START") || type.equals("FAST") || type.equals("QSAVE")
                || type.equals("QLOAD");
    }

    static boolean shoulder(String type) {
        return type.equals("L") || type.equals("R") || type.equals("L2") || type.equals("R2");
    }

    static int defaultWidth(String type) {
        if (type.equals("DPAD")) return 9;
        if (type.startsWith("STICK")) return 8;
        if (type.equals("TOUCHPAD")) return 12;
        if (shoulder(type)) return 8;
        if (pill(type)) return 5;
        if (type.equals("L3") || type.equals("R3")) return 3;
        return 4;
    }

    static int defaultHeight(String type) {
        if (type.equals("DPAD")) return 9;
        if (type.startsWith("STICK")) return 8;
        if (type.equals("TOUCHPAD")) return 8;
        if (shoulder(type)) return 4;
        if (pill(type)) return 2;
        if (type.equals("L3") || type.equals("R3")) return 3;
        return 4;
    }

    // ------------------------------------------------------------------ JSON

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("name", name);
        JSONArray arr = new JSONArray();
        for (Control c : controls) {
            JSONObject k = new JSONObject();
            k.put("t", c.type);
            k.put("x", (double) c.x);
            k.put("y", (double) c.y);
            k.put("w", c.w);
            k.put("h", c.h);
            if (c.color != 0) k.put("c", c.color);
            if (c.cap != null) k.put("cap", c.cap);
            arr.put(k);
        }
        o.put("controls", arr);
        o.put("rc", refCols);
        o.put("rr", refRows);
        o.put("stock", stock);
        o.put("kind", kind);
        o.put("split", split);
        o.put("splitScale", splitScale);
        o.put("leftDx", (double) leftDx);
        o.put("leftDy", (double) leftDy);
        o.put("rightDx", (double) rightDx);
        o.put("rightDy", (double) rightDy);
        o.put("splitLinked", splitLinked);
        o.put("skin", skin);
        if (family != null) o.put("family", family);

        o.put("deckColor", deckColor);
        o.put("buttonColor", buttonColor);
        o.put("accentColor", accentColor);
        o.put("deckAlpha", deckAlpha);
        o.put("buttonAlpha", buttonAlpha);
        if (background != null) {
            o.put("bg", background);
            o.put("bgX", (double) bgX);
            o.put("bgY", (double) bgY);
            o.put("bgScale", (double) bgScale);
        }
        return o;
    }

    /** Mêmes touches aux mêmes places (pour reconnaître l'ancienne classique jamais retouchée). */
    private boolean matches(ControlLayout d) {
        if (!sameKeys(d)) return false;
        for (int i = 0; i < controls.size(); i++) {
            Control a = controls.get(i);
            Control b = d.controls.get(i);
            if (Math.abs(a.x - b.x) > 1e-4 || Math.abs(a.y - b.y) > 1e-4 || a.w != b.w || a.h != b.h) return false;
        }
        return true;
    }

    static ControlLayout fromJson(JSONObject o) throws JSONException {
        ControlLayout l = new ControlLayout(o.getString("name"));
        JSONArray arr = o.getJSONArray("controls");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject k = arr.getJSONObject(i);
            Control c = new Control(k.getString("t"), (float) k.getDouble("x"), (float) k.getDouble("y"),
                    k.getInt("w"), k.getInt("h"));
            c.color = k.optInt("c", 0);
            c.cap = k.optString("cap", null);
            l.controls.add(c);
        }
        l.refCols = o.optInt("rc", 0);
        l.refRows = o.optInt("rr", 0);
        l.kind = o.optString("kind", "classic");
        l.split = o.optBoolean("split", false);
        l.splitScale = o.optInt("splitScale", 100);
        // « splitIn » : ancien réglage « Écart » (0.16 à 0.18).
        l.leftDx = (float) o.optDouble("leftDx", o.optInt("splitIn", 0));
        l.rightDx = (float) o.optDouble("rightDx", -o.optInt("splitIn", 0));
        l.leftDy = (float) o.optDouble("leftDy", 0);
        l.rightDy = (float) o.optDouble("rightDy", 0);
        l.splitLinked = o.optBoolean("splitLinked", true);
        l.skin = o.optString("skin", "codec");
        l.family = o.optString("family", null);
        if (o.has("stock")) l.stock = o.optBoolean("stock", false);
        else if (l.refCols > 0) l.stock = l.matches(legacyClassic(l.refCols, l.refRows));
        l.deckColor = o.optInt("deckColor", Theme.PANEL0);
        l.buttonColor = o.optInt("buttonColor", Theme.STEEL0);
        l.accentColor = o.optInt("accentColor", Theme.CODEC0);
        l.deckAlpha = o.optInt("deckAlpha", 100);
        l.buttonAlpha = o.optInt("buttonAlpha", 100);
        if (o.has("bg")) {
            l.background = o.getString("bg");
            l.bgX = (float) o.optDouble("bgX", 0);
            l.bgY = (float) o.optDouble("bgY", 0);
            l.bgScale = (float) o.optDouble("bgScale", 1);
        }
        return l;
    }
}
