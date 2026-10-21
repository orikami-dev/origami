package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.io.InputStream;

/**
 * La moitié basse : fond, touches d'une {@link ControlLayout}, et en mode édition la grille grise
 * sur laquelle les touches s'aimantent (déplacement, poignée de taille, pincement).
 */
final class ControlDeckView extends ViewGroup {
    interface EditListener {
        /** La touche choisie a changé (null = aucune). */
        void onPicked(ControlLayout.Control c);

        /** La disposition a été modifiée par un geste. */
        void onChanged();
    }

    private static final int MOVE = 1, RESIZE = 2, BG = 3, PINCH = 4, HALF = 5;
    // Disposition divisée : moitié saisie (1 gauche, 2 droite), décalages au début du geste, échelle affichée.
    private int halfSide;
    private int startScaleSplit = 100;
    private float startLx, startLy, startRx, startRy, halfK = 1f;

    private final float dp;
    private final float cell;
    private final Paint grid = new Paint();
    private final Paint gridMajor = new Paint();
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private ControlLayout layout;
    private Store source;
    private ControlView.Listener keys;
    private boolean haptics = true;
    private boolean editing;
    private ControlLayout.Control picked;
    private EditListener editListener;
    private Bitmap background;
    private String backgroundLoaded;
    private int cols;
    private int rows;

    // Geste en cours.
    private int mode;
    private float downX, downY, offX, offY;
    private int startW, startH;
    /** Coin saisi pour redimensionner : -1 gauche / haut, +1 droite / bas ; et la case de départ. */
    private int cornerX, cornerY, startCX, startCY;
    private float startBgX, startBgY, startScale, startDist;
    private int lastCellX = -1, lastCellY = -1;

    ControlDeckView(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        cell = ControlLayout.CELL_DP * dp;
        grid.setColor(0x33A0A4AE);
        grid.setStrokeWidth(Math.max(1f, dp * 0.6f));
        gridMajor.setColor(0x66A0A4AE);
        gridMajor.setStrokeWidth(Math.max(1f, dp));
        setWillNotDraw(false);
        // Une « pilule » aux coins arrondis, qui découpe aussi l'image de fond.
        setBackground(Theme.pill(Theme.PANEL, Theme.pillR(ctx, 28 * dp)));
        setClipToOutline(true);
    }

    /** Sans disposition fixée, la vue prend la disposition active une fois sa taille connue. */
    void setSource(Store store) {
        this.source = store;
    }

    void setKeyListener(ControlView.Listener l, boolean haptics) {
        this.keys = l;
        this.haptics = haptics;
        for (int i = 0; i < getChildCount(); i++) ((ControlView) getChildAt(i)).setListener(l, haptics);
    }

    void setEditListener(EditListener l) {
        this.editListener = l;
    }

    void setEditing(boolean on) {
        editing = on;
        if (!on) picked = null;
        refreshChildren();
        invalidate();
    }

    ControlLayout layout() {
        return layout;
    }

    int columns() {
        return cols;
    }

    int rows() {
        return rows;
    }

    ControlLayout.Control picked() {
        return picked;
    }

    /** Console du jeu en cours : la manette Flex en prend la forme (ConsolePad). */
    private String system;

    void setSystem(String system) {
        this.system = system;
    }

    /** Consoles montrées tour à tour par l'aperçu de la Flex, avec leur nom. */
    static final String[][] TOUR = {
        {"psx", "PlayStation"}, {"snes", "Super Nintendo"}, {"x360", "Xbox 360"}, {"n64", "Nintendo 64"},
        {"ps2", "PlayStation 2"}, {"gc", "GameCube"}, {"md", "Mega Drive"}, {"gba", "Game Boy Advance"},
        {"ps3", "PlayStation 3"}, {"switch", "Switch"}, {"saturn", "Saturn"}, {"nes", "NES"},
        {"psp", "PSP"}, {"dc", "Dreamcast"}, {"nds", "Nintendo DS"}, {"pce", "PC Engine"},
        {"wii", "Wii"}, {"sms", "Master System"}, {"vita", "PS Vita"}, {"3ds", "Nintendo 3DS"},
        {"gb", "Game Boy"}, {"gg", "Game Gear"}, {"gbc", "Game Boy Color"}, {"arcade", "Arcade"},
    };
    private Runnable tour;

    /**
     * Aperçu de la Flex (0.60.4) : la manette passe d'une console à l'autre, son nom dans label.
     * Sans effet (label caché) pour les autres profils.
     */
    void startTour(final ControlLayout base, final android.widget.TextView label) {
        stopTour();
        if (base == null || !base.kind.equals("flex") || base.split) {
            label.setVisibility(GONE);
            setLayout(base);
            return;
        }
        label.setVisibility(VISIBLE);
        tour = new Runnable() {
            int i;

            public void run() {
                String[] s = TOUR[i++ % TOUR.length];
                system = s[0];
                setLayout(base);
                label.setText("MANETTE FLEX · " + s[1].toUpperCase(java.util.Locale.ROOT));
                postDelayed(this, 2200);
            }
        };
        tour.run();
    }

    /** Redessine les touches dont le texte change (FORMAT). */
    void refreshKeys() {
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).invalidate();
    }

    void stopTour() {
        if (tour != null) removeCallbacks(tour);
        tour = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopTour();
        super.onDetachedFromWindow();
    }

    void setLayout(ControlLayout l) {
        // Accueil : sa propre manette de navigation, habillée comme la disposition active.
        if (l != null && ConsolePad.HOME_PAD.equals(system) && !l.split && !ConsolePad.HOME_PAD.equals(l.family)) {
            l = l.forSystem(ConsolePad.HOME_PAD);
        }
        // Flex : la manette de la console du jeu ; retouchée pour une autre console, on repart d'elle.
        else if (l != null && l.kind.equals("flex") && !l.split && system != null
                && (l.stock || !system.equals(l.family)) && !system.equals(l.family)) {
            l = l.forSystem(system);
        }
        layout = l;
        picked = null;
        applyStyle();
        removeAllViews();
        if (l != null) {
            for (ControlLayout.Control c : l.controls) addControlView(c);
        }
        loadBackground();
        requestLayout();
        invalidate();
    }

    void addControl(String type) {
        if (layout == null || layout.split) return;
        ensureBase();
        int w = Math.min(ControlLayout.defaultWidth(type), Math.max(cols, 2));
        int h = Math.min(ControlLayout.defaultHeight(type), Math.max(rows, 2));
        // Au centre de la zone, aligné sur la grille.
        int cx = Math.max(0, (cols - w) / 2);
        int cy = Math.max(0, (rows - h) / 2);
        ControlLayout.Control c = new ControlLayout.Control(type,
                cols == 0 ? 0 : (float) cx / cols, rows == 0 ? 0 : (float) cy / rows, w, h);
        layout.controls.add(c);
        addControlView(c);
        pick(c);
        changed();
    }

    void removePicked() {
        if (layout == null || picked == null || layout.split) return;
        ensureBase();
        for (int i = 0; i < getChildCount(); i++) {
            if (((ControlView) getChildAt(i)).control == picked) {
                removeViewAt(i);
                break;
            }
        }
        layout.controls.remove(picked);
        pick(null);
        changed();
    }

    /** Agrandit (+1) ou rétrécit (-1) la touche choisie d'une case. */
    void resizePicked(int delta) {
        if (picked == null || layout == null || layout.split) return;
        ensureBase();
        int cx = cellX(picked);
        int cy = cellY(picked);
        int w = clamp(picked.w + delta, 2, Math.max(2, cols - cx));
        int h = clamp(picked.h + delta, 2, Math.max(2, rows - cy));
        if (square(picked.type)) w = h = clamp(picked.w + delta, 2, Math.max(2, Math.min(cols - cx, rows - cy)));
        picked.w = w;
        picked.h = h;
        requestLayout();
        changed();
    }

    void reloadBackground() {
        backgroundLoaded = null;
        loadBackground();
        invalidate();
    }

    // ------------------------------------------------------------------ mise en page

    private void addControlView(ControlLayout.Control c) {
        ControlView v = new ControlView(getContext(), c);
        if (layout != null) v.setStyle(layout);
        if (overlay) v.setAlpha(Math.min(v.getAlpha(), 0.7f));
        v.setListener(keys, haptics);
        v.setEditing(editing, c == picked);
        addView(v);
    }

    private void refreshChildren() {
        for (int i = 0; i < getChildCount(); i++) {
            ControlView v = (ControlView) getChildAt(i);
            v.setEditing(editing, v.control == picked);
        }
    }

    private void pick(ControlLayout.Control c) {
        picked = c;
        // La touche choisie passe au premier plan.
        for (int i = 0; i < getChildCount(); i++) {
            ControlView v = (ControlView) getChildAt(i);
            if (v.control == c) {
                v.bringToFront();
                break;
            }
        }
        refreshChildren();
        if (editListener != null) editListener.onPicked(c);
    }

    private void changed() {
        if (editListener != null) editListener.onChanged();
    }

    private static boolean square(String type) {
        return type.equals("DPAD") || type.equals("HOME") || type.equals("A") || type.equals("B")
                || type.equals("X") || type.equals("Y") || type.startsWith("STICK") || type.equals("L3")
                || type.equals("R3");
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private int cellX(ControlLayout.Control c) {
        return clamp(Math.round(c.x * cols), 0, Math.max(0, cols - c.w));
    }

    private int cellY(ControlLayout.Control c) {
        return clamp(Math.round(c.y * rows), 0, Math.max(0, rows - c.h));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cols = Math.max(1, (int) (w / cell));
        rows = Math.max(1, (int) (h / cell));
        if (layout == null && source != null) {
            // Hors de la passe de mise en page en cours : on ajoute les touches juste après.
            post(new Runnable() {
                public void run() {
                    if (layout == null) setLayout(source.currentLayout(cols, rows));
                }
            });
        } else if (followScreen && !editing && (cols != lastCols || rows != lastRows)) {
            post(new Runnable() {
                public void run() { reloadFromSource(); }
            });
        } else {
            loadBackground();
            requestLayout();
        }
        lastCols = cols;
        lastRows = rows;
    }

    private int lastCols, lastRows;

    /** Teinte et opacité du fond, puis des touches. */
    private boolean overlay;

    /** Par-dessus le jeu : moitiés et touches plus transparentes pour voir l'image dessous. */
    /** Opacité (en %) de la manette quand elle est superposée au jeu. */
    private int overlayOpacity = 35;

    void setOverlay(boolean on, int opacity) {
        boolean was = overlay;
        overlay = on;
        overlayOpacity = Math.max(0, Math.min(100, opacity));
        if (was != on && followScreen && !editing && layout != null) {
            reloadFromSource();
            return;
        }
        applyStyle();
        requestLayout();
    }

    /** En jeu : chaque zone (écran, orientation, superposée ou non) reprend sa propre disposition. */
    private boolean followScreen;

    void setFollowScreen(boolean on) {
        followScreen = on;
    }

    /**
     * Seulement les touches, sans fond ni image ni moitiés : manette superposée, ou écran en paysage
     * (le skin complet ne se voit qu'en portrait). L'éditeur montre toujours tout.
     */
    private boolean bare() {
        if (editing) return false;
        android.content.res.Configuration c = getResources().getConfiguration();
        return overlay || c.screenWidthDp > c.screenHeightDp;
    }

    /** Écran large, manette superposée : disposition en colonnes contre les bords. */
    boolean wide() {
        return overlay && cols > rows * 1.2f;
    }

    /**
     * La disposition de cette zone : celle retouchée ici si elle existe, sinon la commune, mise en
     * colonnes sur un écran large superposé.
     */
    void reloadFromSource() {
        if (source == null || cols <= 1 || rows <= 1) return;
        ControlLayout l = source.currentLayout(cols, rows);
        // Manette fournie : PadDesign la place lui-même, en colonnes contre les bords si besoin.
        if (wide() && !l.split && !l.stock && (l.refCols != cols || l.refRows != rows)) l = ControlLayout.wideOverlay(l, cols, rows);
        setLayout(l);
    }

    /**
     * Retouche sur le jeu : la disposition est figée telle qu'affichée sur cette grille, après quoi
     * ajouter, retirer ou déplacer une touche ne bouge plus les autres.
     */
    void pinHere() {
        if (layout == null) return;
        syncView();
        if (layout.split || layout.stock || layout.refCols != cols || layout.refRows != rows) {
            layout.rebase(cols, rows);
            layout.split = false;
        }
        applyStyle();
        requestLayout();
        invalidate();
    }

    void applyStyle() {
        if (layout == null) return;
        int a = Math.round(255 * (overlay ? Math.min(layout.deckAlpha, overlayOpacity) : layout.deckAlpha) / 100f);
        // Divisée : pas de fond d'un bord à l'autre, seulement sous chaque moitié (dessiné dans onDraw).
        // Superposée au jeu : seulement les touches, aucun fond ni moitié qui voilerait l'image.
        setBackground(layout.split || bare() ? null : Theme.pill((Theme.padDeck(layout) & 0x00FFFFFF) | (a << 24), Theme.pillR(getContext(), 28 * dp)));
        halfPaint.setColor(bare() ? 0 : (Theme.padDeck(layout) & 0x00FFFFFF) | (a << 24));
        for (int i = 0; i < getChildCount(); i++) {
            ControlView v = (ControlView) getChildAt(i);
            v.setStyle(layout);
            // Les touches restent un peu plus visibles que le fond pour qu'on les trouve.
            if (overlay) v.setAlpha(Math.min(v.getAlpha(), Math.min(1f, (overlayOpacity + 25) / 100f)));
        }
        invalidate();
    }

    /**
     * L'éditeur travaille sur la grille de l'écran courant : la disposition y est recalée juste
     * avant la première retouche (ouvrir l'éditeur sur l'autre écran ne change rien).
     */
    private void ensureBase() {
        syncView();
        if (layout != null && cols > 1
                && (layout.refCols != cols || layout.refRows != rows || layout.stock)) {
            layout.rebase(cols, rows);
            requestLayout();
        }
    }

    private float[][] boxes() {
        if (layout == null) return new float[0][];
        syncView();
        return layout.arrange(cols, rows);
    }

    /** Taille réelle de la zone, pour le placement dessiné des manettes fournies (PadDesign). */
    private void syncView() {
        if (layout == null || getWidth() <= 0) return;
        layout.viewW = getWidth() / cell;
        layout.viewH = (getHeight() - bottomInset) / cell;
        layout.viewBottomCenter = overlay;
        if (source != null) ConsolePad.ergo = source.ergoTriggers();
    }

    private float[] box(float[][] all, ControlLayout.Control c) {
        int i = layout.controls.indexOf(c);
        return i >= 0 && i < all.length ? all[i] : new float[] {0, 0, c.w, c.h};
    }

    /** Zone du bas prise par la barre de navigation : le fond passe dessous, les touches restent au-dessus. */
    private int bottomInset;

    void setBottomInset(int px) {
        if (px == bottomInset) return;
        bottomInset = px;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), MeasureSpec.getSize(hSpec));
        float[][] all = boxes();
        for (int i = 0; i < getChildCount(); i++) {
            ControlView v = (ControlView) getChildAt(i);
            float[] b = box(all, v.control);
            v.measure(MeasureSpec.makeMeasureSpec(Math.round(b[2] * cell), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.round(b[3] * cell), MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // La grille est centrée : la marge restante se répartit des deux côtés.
        // Calcul en flottants puis un seul arrondi : chaque touche tombe au pixel près.
        float ox = ((r - l) - cols * cell) / 2f;
        float oy = ((b - t) - bottomInset - rows * cell) / 2f;
        float[][] all = boxes();
        for (int i = 0; i < getChildCount(); i++) {
            ControlView v = (ControlView) getChildAt(i);
            float[] bx = box(all, v.control);
            int x = Math.round(ox + bx[0] * cell);
            int y = Math.round(oy + bx[1] * cell);
            v.layout(x, y, x + Math.round(bx[2] * cell), y + Math.round(bx[3] * cell));
            v.setRotation(bx.length > 4 ? bx[4] : 0f);
            v.setBean(bx.length > 7 ? bx[5] : 0f, bx.length > 7 ? bx[6] : 0f, bx.length > 7 ? bx[7] : 0f);
        }
    }

    private float originX() {
        return Math.round((getWidth() - cols * cell) / 2f);
    }

    private float originY() {
        return Math.round((getHeight() - bottomInset - rows * cell) / 2f);
    }

    // ------------------------------------------------------------------ fond et grille

    private void loadBackground() {
        String uri = layout == null ? null : layout.background;
        if (uri == null) {
            background = null;
            backgroundLoaded = null;
            return;
        }
        if (uri.equals(backgroundLoaded) && background != null) return;
        backgroundLoaded = uri;
        background = null;
        int target = Math.max(getWidth(), 720);
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = getContext().getContentResolver().openInputStream(Uri.parse(uri));
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (o.outWidth / (sample * 2) >= target * 2) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            in = getContext().getContentResolver().openInputStream(Uri.parse(uri));
            background = BitmapFactory.decodeStream(in, null, o);
            in.close();
        } catch (Exception e) {
            background = null;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        if (bare() && !editing) return;
        if (background != null && layout != null) {
            // L'image couvre la zone, puis on applique le zoom et le décalage choisis.
            float bw = background.getWidth();
            float bh = background.getHeight();
            float s = Math.max(w / bw, h / bh) * layout.bgScale;
            float dw = bw * s;
            float dh = bh * s;
            float left = (w - dw) / 2 + layout.bgX * w;
            float top = (h - dh) / 2 + layout.bgY * h;
            dst.set(left, top, left + dw, top + dh);
            c.drawBitmap(background, null, dst, bmp);
        }
        if (layout != null && layout.split) drawHalves(c);
        else if (layout != null && !"plain".equals(layout.skin)) {
            half.set(0, 0, w, h);
            drawSkin(c, half, 28 * dp);
        }
        if (editing) {
            float ox = originX();
            float oy = originY();
            for (int i = 0; i <= cols; i++) {
                float x = ox + i * cell;
                c.drawLine(x, oy, x, oy + rows * cell, i % 4 == 0 ? gridMajor : grid);
            }
            for (int j = 0; j <= rows; j++) {
                float y = oy + j * cell;
                c.drawLine(ox, y, ox + cols * cell, y, j % 4 == 0 ? gridMajor : grid);
            }
        }
    }

    private final Paint halfPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint skinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Path skinClip = new android.graphics.Path();

    /**
     * Habillage « codec » d'un panneau : lumière venant du haut, trame de points discrète,
     * liseré d'accent en haut et bord fin, comme une façade d'appareil radio.
     */
    private void drawSkin(Canvas c, RectF box, float radius) {
        int accent = Theme.padAccent(layout);
        c.save();
        skinClip.reset();
        skinClip.addRoundRect(box, radius, radius, android.graphics.Path.Direction.CW);
        c.clipPath(skinClip);
        skinPaint.setStyle(Paint.Style.FILL);
        skinPaint.setShader(new android.graphics.LinearGradient(0, box.top, 0, box.bottom,
                0x18FFFFFF, 0x22000000, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(box, skinPaint);
        skinPaint.setShader(null);
        // Motif gravé propre au thème (circuit, givre, carbone, cerisier, grille…).
        String motif = Theme.PAD_OWN ? "pad" : Theme.THEME_ID;
        PadSkins.draw(c, box, motif, accent, dp);
        if (!PadSkins.replacesDots(motif)) {
            skinPaint.setColor(0x14FFFFFF);
            float step = 10 * dp;
            float dot = 0.9f * dp;
            for (float y = box.top + step / 2; y < box.bottom; y += step) {
                for (float x = box.left + step / 2; x < box.right; x += step) c.drawCircle(x, y, dot, skinPaint);
            }
        }
        // Liseré d'accent en haut, qui s'efface vers les côtés et respire lentement (0.62.5, tous
        // les thèmes) : une fine bande de lumière qui s'allume et s'éteint en 5 secondes, avec un halo.
        float breath = 1f;
        if (Theme.ANIMATE && !editing) {
            double t = (android.os.SystemClock.uptimeMillis() % 5200L) / 5200.0;
            breath = 0.3f + 0.7f * (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * t));
        }
        int rgb = accent & 0x00FFFFFF;
        int core = Math.round(0xCC * breath), glow = Math.round(0x40 * breath);
        skinPaint.setShader(new android.graphics.LinearGradient(box.left, 0, box.right, 0,
                new int[] {rgb, rgb | (glow << 24), rgb}, null, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(box.left + radius * 0.6f, box.top, box.right - radius * 0.6f, box.top + 7 * dp, skinPaint);
        skinPaint.setShader(new android.graphics.LinearGradient(box.left, 0, box.right, 0,
                new int[] {rgb, rgb | (core << 24), rgb}, null, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(box.left + radius * 0.6f, box.top, box.right - radius * 0.6f, box.top + 1.5f * dp, skinPaint);
        skinPaint.setShader(null);
        c.restore();
        if (Theme.ANIMATE && !editing) postInvalidateDelayed(50);
        skinPaint.setStyle(Paint.Style.STROKE);
        skinPaint.setStrokeWidth(dp);
        skinPaint.setColor(0x1FFFFFFF);
        RectF in = new RectF(box.left + dp / 2, box.top + dp / 2, box.right - dp / 2, box.bottom - dp / 2);
        c.drawRoundRect(in, radius, radius, skinPaint);
    }
    private final RectF half = new RectF();

    /** Les deux moitiés du clavier divisé : un panneau arrondi autour des touches de chaque bord. */
    private void drawHalves(Canvas c) {
        float[][] all = boxes();
        float ox = originX();
        float oy = originY();
        for (int side = 0; side < 2; side++) {
            float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
            for (float[] k : all) {
                float center = (k[0] + k[2] / 2) / Math.max(1, cols);
                if (side == 0 ? center >= 1f / 3 : center <= 2f / 3) continue;
                l = Math.min(l, k[0]);
                t = Math.min(t, k[1]);
                r = Math.max(r, k[0] + k[2]);
                b = Math.max(b, k[1] + k[3]);
            }
            if (l == Float.MAX_VALUE) continue;
            float pad = 0.7f * cell;
            half.set(ox + l * cell - pad, oy + t * cell - pad, ox + r * cell + pad, oy + b * cell + pad);
            c.drawRoundRect(half, 26 * dp, 26 * dp, halfPaint);
            if (!"plain".equals(layout.skin)) drawSkin(c, half, 26 * dp);
        }
    }

    // ------------------------------------------------------------------ gestes d'édition

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return editing;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!editing || layout == null) return false;
        float x = e.getX();
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = x;
                downY = y;
                ControlView hit = hitTest(x, y);
                if (hit != null && layout.split && layout.sideOf(hit.control) != 0) {
                    // Touches figées : on déplace toute la moitié (et l'autre en miroir si elles sont liées).
                    pick(hit.control);
                    mode = HALF;
                    halfSide = layout.sideOf(hit.control);
                    startLx = layout.leftDx;
                    startLy = layout.leftDy;
                    startRx = layout.rightDx;
                    startRy = layout.rightDy;
                    float[] b = box(boxes(), hit.control);
                    // Manette fournie (PadDesign) : les décalages sont déjà en cases.
                    halfK = layout.stock ? 1f : Math.max(0.1f, b[2] / Math.max(0.1f, hit.control.w * layout.splitScale / 100f));
                    lastCellX = lastCellY = -1;
                    return true;
                }
                if (hit != null && layout.split) {
                    pick(hit.control);
                    mode = 0;
                    return true;
                }
                if (hit != null) {
                    ensureBase();
                    // Redimensionner seulement par un coin vert d'une touche déjà choisie, et un coin
                    // à la mesure de la touche : sur Select ou Save, 28 dp couvraient presque toute la
                    // touche, et vouloir la déplacer la faisait grossir.
                    boolean wasPicked = hit.control == picked;
                    pick(hit.control);
                    float handle = Math.min(28 * dp, 0.35f * Math.min(hit.getWidth(), hit.getHeight()));
                    // Les quatre coins redimensionnent ; le coin opposé reste en place.
                    boolean nearL = x < hit.getLeft() + handle, nearR = x > hit.getRight() - handle;
                    boolean nearT = y < hit.getTop() + handle, nearB = y > hit.getBottom() - handle;
                    boolean corner = wasPicked && (nearL || nearR) && (nearT || nearB);
                    cornerX = nearL ? -1 : 1;
                    cornerY = nearT ? -1 : 1;
                    startCX = cellX(hit.control);
                    startCY = cellY(hit.control);
                    mode = corner ? RESIZE : MOVE;
                    offX = x - hit.getLeft();
                    offY = y - hit.getTop();
                    startW = hit.control.w;
                    startH = hit.control.h;
                } else {
                    pick(null);
                    mode = layout.background != null ? BG : 0;
                    startBgX = layout.bgX;
                    startBgY = layout.bgY;
                }
                lastCellX = lastCellY = -1;
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2) {
                    startDist = distance(e);
                    startScaleSplit = layout.splitScale;
                    if (picked != null) {
                        startW = picked.w;
                        startH = picked.h;
                    } else {
                        startScale = layout.bgScale;
                    }
                    mode = PINCH;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                drag(e, x, y);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mode != 0) changed();
                mode = 0;
                return true;
            default:
                return true;
        }
    }

    private void drag(MotionEvent e, float x, float y) {
        if (mode == HALF) {
            float dx = (x - downX) / cell / halfK;
            float dy = (y - downY) / cell / halfK;
            if (halfSide == 1 || layout.splitLinked) {
                layout.leftDx = startLx + (halfSide == 1 ? dx : -dx);
                layout.leftDy = startLy + dy;
            }
            if (halfSide == 2 || layout.splitLinked) {
                layout.rightDx = startRx + (halfSide == 2 ? dx : -dx);
                layout.rightDy = startRy + dy;
            }
            requestLayout();
            invalidate();
            return;
        }
        if (mode == MOVE && picked != null) {
            int cx = clamp(Math.round((x - offX - originX()) / cell), 0, Math.max(0, cols - picked.w));
            int cy = clamp(Math.round((y - offY - originY()) / cell), 0, Math.max(0, rows - picked.h));
            if (cx != lastCellX || cy != lastCellY) {
                // Aimant : la touche saute de case en case, avec un petit cran.
                if (lastCellX != -1) tick();
                lastCellX = cx;
                lastCellY = cy;
                picked.x = (float) cx / cols;
                picked.y = (float) cy / rows;
                requestLayout();
            }
        } else if (mode == RESIZE && picked != null) {
            // Place disponible du côté du coin saisi (le coin opposé ne bouge pas).
            int roomX = cornerX > 0 ? cols - startCX : startCX + startW;
            int roomY = cornerY > 0 ? rows - startCY : startCY + startH;
            float dx = cornerX * (x - downX) / cell;
            float dy = cornerY * (y - downY) / cell;
            int w = clamp(startW + Math.round(dx), 2, Math.max(2, roomX));
            int h = clamp(startH + Math.round(dy), 2, Math.max(2, roomY));
            if (square(picked.type)) {
                // Touche carrée : la diagonale du geste, pas le plus grand des deux côtés (qui la
                // faisait grossir au moindre tremblement).
                int d = Math.round((dx + dy) / 2f);
                w = h = clamp(startW + d, 2, Math.max(2, Math.min(roomX, roomY)));
            }
            int nx = cornerX > 0 ? startCX : startCX + startW - w;
            int ny = cornerY > 0 ? startCY : startCY + startH - h;
            picked.x = (float) nx / cols;
            picked.y = (float) ny / rows;
            setSize(w, h);
        } else if (mode == PINCH && e.getPointerCount() >= 2 && startDist > 0) {
            float f = distance(e) / startDist;
            if (layout.split) {
                // Disposition divisée : pincer change la taille des deux moitiés.
                layout.splitScale = Math.max(60, Math.min(130, Math.round(startScaleSplit * f)));
                requestLayout();
                invalidate();
            } else if (picked != null) {
                int cx = cellX(picked);
                int cy = cellY(picked);
                int w = clamp(Math.round(startW * f), 2, Math.max(2, cols - cx));
                int h = clamp(Math.round(startH * f), 2, Math.max(2, rows - cy));
                if (square(picked.type)) w = h = Math.min(w, h);
                setSize(w, h);
            } else if (layout.background != null) {
                layout.bgScale = Math.max(1f, Math.min(4f, startScale * f));
                invalidate();
            }
        } else if (mode == BG) {
            layout.bgX = startBgX + (x - downX) / getWidth();
            layout.bgY = startBgY + (y - downY) / getHeight();
            invalidate();
        }
    }

    private void setSize(int w, int h) {
        if (w == picked.w && h == picked.h) return;
        picked.w = w;
        picked.h = h;
        tick();
        requestLayout();
    }

    private void tick() {
        if (haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private ControlView hitTest(float x, float y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View v = getChildAt(i);
            if (x >= v.getLeft() && x < v.getRight() && y >= v.getTop() && y < v.getBottom()) {
                return (ControlView) v;
            }
        }
        return null;
    }

    private static float distance(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
