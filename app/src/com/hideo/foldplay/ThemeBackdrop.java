package com.hideo.foldplay;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Fond assorti à chaque thème, dessiné (aucune image, aucun texte) et toujours très sombre pour
 * que la lecture passe avant tout :
 * - Codec : radar plié (anneaux octogonaux, faisceau en éventail, échos), lignes de balayage ;
 * - Arctique : glaciers, aurore et cristaux facettés, pliés comme la grue (Facets) ;
 * - Écarlate : flammes, escarbilles et chevrons d'alerte pliés (Facets) ;
 * - Sakura : mont, branche et fleurs de cerisier en papier plié (Facets) ;
 * - Ambre : ville Art déco la nuit (gratte-ciel à gradins, liserés d'or, éventail ; ancien terminal gardé, AMBRE_VILLE) ;
 * - Couleurs de la manette : le radar plié à la teinte de la manette.
 */
final class ThemeBackdrop extends Drawable {
    private final String id;
    private final int bg;
    private final int accent;
    private final float radius;
    private final int fold;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();

    ThemeBackdrop(String id, int bg, int accent, float radius) {
        this(id, bg, accent, radius, Theme.fold());
    }

    ThemeBackdrop(String id, int bg, int accent, float radius, int fold) {
        this.fold = fold;
        this.id = id;
        this.bg = bg;
        this.accent = accent;
        this.radius = radius;
    }

    /** Coins du haut droits (Tsuru : l'écran touche la barre d'état), ceux du bas restent arrondis. */
    private boolean squareTop;

    ThemeBackdrop squareTop(boolean on) {
        squareTop = on;
        return this;
    }

    private void addRound() {
        if (squareTop) {
            clip.addRoundRect(box, new float[] {0, 0, 0, 0, radius, radius, radius, radius}, Path.Direction.CW);
        } else {
            clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        }
    }

    private static int a(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    // ------------------------------------------------------------------ animation (0.62.2)

    /** Durée d'un tour de boucle : tout ce qui bouge revient exactement à sa place. */
    private static final long PERIOD = 24000;
    private final boolean animated = Theme.ANIMATE;
    private android.graphics.Bitmap still;
    private final Runnable tick = new Runnable() {
        public void run() {
            invalidateSelf();
        }
    };

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (!visible) {
            unscheduleSelf(tick);
            if (still != null) {
                still.recycle();
                still = null;
            }
        } else if (animated) {
            invalidateSelf();
        }
        return changed;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (!animated) {
            drawStill(c);
            return;
        }
        int w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        // Le décor fixe est dessiné une fois dans une image ; seul ce qui bouge est redessiné.
        if (still == null || still.getWidth() != w || still.getHeight() != h) {
            if (still != null) still.recycle();
            still = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
            Canvas sc = new Canvas(still);
            sc.translate(-b.left, -b.top);
            Facets.MOVING = true;
            drawStill(sc);
            Facets.MOVING = false;
        }
        c.drawBitmap(still, b.left, b.top, null);
        float t = (android.os.SystemClock.uptimeMillis() % PERIOD) / (float) PERIOD;
        c.save();
        if (radius > 0) {
            box.set(b);
            clip.reset();
            addRound();
            c.clipPath(clip);
        }
        c.translate(b.left, b.top);
        float u = Math.max(w, h);
        if (id.equals("arctique")) BackdropMotion.snow(c, w, h, u, accent, t);
        else if (id.equals("ecarlate")) BackdropMotion.embers(c, w, h, u, accent, t);
        else if (id.equals("sakura")) BackdropMotion.petals(c, w, h, u, accent, t);
        else if (id.equals("ambre")) {
            if (AMBRE_VILLE) BackdropMotion.ville(c, w, h, u, accent, t);
            else BackdropMotion.terminal(c, w, h, u, accent, t);
        }
        else BackdropMotion.radar(c, w, h, u, accent, t);
        c.restore();
        if (isVisible()) scheduleSelf(tick, android.os.SystemClock.uptimeMillis() + 33);
    }

    private void drawStill(Canvas c) {
        Rect b = getBounds();
        float w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        c.save();
        box.set(b);
        if (radius > 0) {
            clip.reset();
            addRound();
            c.clipPath(clip);
        }
        c.translate(b.left, b.top);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(bg);
        c.drawRect(0, 0, w, h, p);
        float u = Math.max(w, h);
        if (id.equals("arctique")) ice(c, w, h, u);
        else if (id.equals("ecarlate")) ember(c, w, h, u);
        else if (id.equals("sakura")) sakura(c, w, h, u);
        else if (id.equals("ambre")) {
            if (AMBRE_VILLE) ville(c, w, h, u);
            else terminal(c, w, h, u);
        }
        else radar(c, w, h, u);
        p.setShader(null);
        c.restore();
    }

    private void radar(Canvas c, float w, float h, float u) {
        // Lueur dans le coin bas droit, puis le radar plié (choisi par BOSS le 19/10).
        p.setShader(new RadialGradient(w * 0.82f, h * 0.78f, u * 0.6f, a(accent, 0x30), a(accent, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Facets.codec(c, w, h, accent, fold, 1f, 0);
    }

    private void ice(Canvas c, float w, float h, float u) {
        // Froid venu d'en haut, puis le décor plié.
        p.setShader(new LinearGradient(0, 0, 0, h * 0.5f, a(accent, 0x1C), a(accent, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.5f, p);
        p.setShader(null);
        Facets.arctique(c, w, h, accent, fold, 1f, u / 1000f);
        p.setShader(new LinearGradient(0, h * 0.35f, 0, h, 0x00000000, 0x30000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    private void ember(Canvas c, float w, float h, float u) {
        // Lueur de braise montant du bas, puis le décor plié.
        p.setShader(new RadialGradient(w * 0.5f, h * 1.1f, u * 0.75f, a(accent, 0x30), a(accent, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Facets.ecarlate(c, w, h, accent, fold, 1f, u / 1000f);
    }

    private void sakura(Canvas c, float w, float h, float u) {
        p.setShader(new RadialGradient(w * 0.15f, h * 0.1f, u * 0.6f, a(accent, 0x1E), a(accent, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Facets.sakura(c, w, h, accent, fold, 1f, u / 1000f);
    }

    /** Fond d'Ambre : ville Art déco la nuit (BOSS) ; false rend l'ancien vieux terminal au soleil. */
    static final boolean AMBRE_VILLE = true;

    private void ville(Canvas c, float w, float h, float u) {
        // Ciel : haut presque noir, puis brume ambrée qui monte de l'horizon, plus forte côté tours (droite).
        p.setShader(new LinearGradient(0, 0, 0, h * 0.6f, 0xCC030201, 0x00030201, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.6f, p);
        p.setShader(new LinearGradient(0, h * 0.5f, 0, h * 0.9f, a(accent, 0), a(accent, 0x34), Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.5f, w, h * 0.9f, p);
        p.setShader(new RadialGradient(w * 0.84f, h * 0.9f, u * 0.55f, a(accent, 0x46), a(accent, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Facets.ambre(c, w, h, accent, fold, 1f, Facets.AMBRE_FOND);
        // Vignette douce : les bords se ferment, le centre reste lisible.
        p.setShader(new RadialGradient(w * 0.5f, h * 0.5f, u * 0.8f, new int[] {0, 0, 0x70000000},
                new float[] {0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    private void terminal(Canvas c, float w, float h, float u) {
        p.setShader(new RadialGradient(w * 0.5f, h * 0.45f, u * 0.65f, a(accent, 0x26), a(accent, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Facets.ambre(c, w, h, accent, fold, 1f, Facets.AMBRE);
        p.setColor(0x14000000);
        float step = Math.max(2f, u / 400f);
        for (float y = 0; y < h; y += step * 2) c.drawRect(0, y, w, y + step, p);
        // Vignette d'écran cathodique.
        p.setShader(new RadialGradient(w * 0.5f, h * 0.5f, u * 0.75f, new int[] {0, 0, 0x88000000},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    @Override
    public void getOutline(android.graphics.Outline o) {
        Rect b = getBounds();
        if (squareTop) o.setRoundRect(b.left, Math.round(b.top - radius), b.right, b.bottom, radius);
        else o.setRoundRect(b, radius);
    }
}
