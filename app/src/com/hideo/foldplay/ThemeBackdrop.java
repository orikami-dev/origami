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
 * - Ambre : métropole japonaise la nuit (tour en treillis rouge et blanc, Fuji, gratte-ciel de verre, passerelles ; ville Art déco gardée en réserve, AMBRE_VILLE_DECO ; ancien terminal, AMBRE_VILLE) ;
 * - Néon : ruelle de Tokyo la nuit sous la pluie, enseignes sans texte, reflets sur le bitume mouillé ;
 * - Jade : temple de montagne dans la brume, bambouseraie et lanternes ;
 * - Argent : pleine lune presque monochrome, grues en origami au-dessus d'une mer de nuages ;
 * - Variantes nuit (CODEC_BASE, ARCTIQUE_POLAIRE, ECARLATE_JAPON, SAKURA_RIVIERE), éteintes tant qu'elles ne sont pas validées ;
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
        if (id.equals("arctique")) {
            if (ARCTIQUE_POLAIRE) BackdropMotion.polaire(c, w, h, u, accent, t);
            else BackdropMotion.snow(c, w, h, u, accent, t);
        } else if (id.equals("ecarlate")) {
            if (ECARLATE_JAPON) BackdropMotion.japon(c, w, h, u, accent, t);
            else BackdropMotion.embers(c, w, h, u, accent, t);
        } else if (id.equals("sakura")) {
            if (SAKURA_RIVIERE) BackdropMotion.riviere(c, w, h, u, accent, t);
            else BackdropMotion.petals(c, w, h, u, accent, t);
        }
        else if (id.equals("ambre")) {
            if (!AMBRE_VILLE) BackdropMotion.terminal(c, w, h, u, accent, t);
            else if (AMBRE_VILLE_DECO) BackdropMotion.ville(c, w, h, u, accent, t);
            else BackdropMotion.tokyo(c, w, h, u, accent, t);
        }
        else if (id.equals("neon")) BackdropMotion.ruelle(c, w, h, u, accent, fold, t);
        else if (id.equals("jade")) BackdropMotion.temple(c, w, h, u, accent, fold, t);
        else if (id.equals("argent")) BackdropMotion.lune(c, w, h, u, accent, fold, t);
        else if (CODEC_BASE) BackdropMotion.base(c, w, h, u, accent, t);
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
        if (id.equals("arctique")) {
            if (ARCTIQUE_POLAIRE) polaire(c, w, h, u);
            else ice(c, w, h, u);
        } else if (id.equals("ecarlate")) {
            if (ECARLATE_JAPON) japon(c, w, h, u);
            else ember(c, w, h, u);
        } else if (id.equals("sakura")) {
            if (SAKURA_RIVIERE) riviere(c, w, h, u);
            else sakura(c, w, h, u);
        }
        else if (id.equals("ambre")) {
            if (!AMBRE_VILLE) terminal(c, w, h, u);
            else if (AMBRE_VILLE_DECO) ville(c, w, h, u);
            else tokyo(c, w, h, u);
        }
        else if (id.equals("neon")) ruelle(c, w, h, u);
        else if (id.equals("jade")) temple(c, w, h, u);
        else if (id.equals("argent")) lune(c, w, h, u);
        else if (CODEC_BASE) base(c, w, h, u);
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

    /** Fond d'Ambre : ville la nuit (BOSS) ; false rend l'ancien vieux terminal au soleil. */
    static final boolean AMBRE_VILLE = true;

    /**
     * Variante de réserve, éteinte : la ville Art déco la nuit (gratte-ciel à gradins, éventail doré, ville() ci-dessous,
     * Facets variante 3, BackdropMotion.ville). Mettre true pour la remettre à la place de la métropole japonaise.
     */
    static final boolean AMBRE_VILLE_DECO = false;

    /** Fond actif d'Ambre : métropole japonaise la nuit (tour en treillis rouge et blanc, Fuji, passerelles, enseignes sans texte). */
    private void tokyo(Canvas c, float w, float h, float u) {
        p.setShader(new LinearGradient(0, 0, 0, h * 0.6f, 0xCC030201, 0x00030201, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.6f, p);
        p.setShader(new LinearGradient(0, h * 0.4f, 0, h * 0.9f, a(accent, 0), a(accent, 0x3C), Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.4f, w, h * 0.9f, p);
        p.setShader(new RadialGradient(w * 0.68f, h * 0.88f, u * 0.6f, a(accent, 0x44), a(accent, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        Facets.ambre(c, w, h, accent, fold, 1f, Facets.AMBRE_TOKYO);
        vignette(c, w, h, u);
    }

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

    /**
     * Nouveaux fonds « nuit » (comme AMBRE_VILLE) : false tant que BOSS n'a pas validé, les anciens restent.
     * Codec : base militaire ; Arctique : nuit polaire ; Écarlate : nuit japonaise ; Sakura : rivière et pont.
     */
    static boolean CODEC_BASE = true;
    static boolean ARCTIQUE_POLAIRE = true;
    static boolean ECARLATE_JAPON = true;
    static boolean SAKURA_RIVIERE = true;

    /** Ciel commun : haut assombri, brume de couleur sur la bande d'horizon, lueur ponctuelle, vignette. */
    private void ciel(Canvas c, float w, float h, float u, float horizon, int band, float gx, float gy, float gr, int glow) {
        p.setShader(new LinearGradient(0, 0, 0, h * 0.6f, 0xCC030201, 0x00030201, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.6f, p);
        p.setShader(new LinearGradient(0, horizon - h * 0.4f, 0, horizon, a(accent, 0), a(accent, band), Shader.TileMode.CLAMP));
        c.drawRect(0, horizon - h * 0.4f, w, horizon, p);
        p.setShader(new RadialGradient(gx, gy, gr, a(accent, glow), a(accent, 0), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    private void vignette(Canvas c, float w, float h, float u) {
        p.setShader(new RadialGradient(w * 0.5f, h * 0.5f, u * 0.8f, new int[] {0, 0, 0x70000000},
                new float[] {0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
    }

    private void base(Canvas c, float w, float h, float u) {
        ciel(c, w, h, u, h * 0.88f, 0x2C, w * 0.88f, h * 0.88f, u * 0.55f, 0x30);
        p.setColor(a(accent, 0x07));
        c.drawRect(0, 0, w, h, p);
        Facets.codec(c, w, h, accent, fold, 1f, Facets.FOND);
        p.setColor(0x16000000);
        float step = Math.max(2f, u / 400f);
        for (float y = 0; y < h; y += step * 2) c.drawRect(0, y, w, y + step, p);
        vignette(c, w, h, u);
    }

    private void polaire(Canvas c, float w, float h, float u) {
        ciel(c, w, h, u, h * 0.8f, 0x1C, w * 0.7f, h * 0.8f, u * 0.5f, 0x20);
        Facets.arctique(c, w, h, accent, fold, 1f, Facets.FOND);
        vignette(c, w, h, u);
    }

    private void japon(Canvas c, float w, float h, float u) {
        ciel(c, w, h, u, h * 0.9f, 0x30, w * 0.74f, h * 0.3f, u * 0.55f, 0x2A);
        Facets.ecarlate(c, w, h, accent, fold, 1f, Facets.FOND);
        vignette(c, w, h, u);
    }

    private void riviere(Canvas c, float w, float h, float u) {
        ciel(c, w, h, u, h * 0.64f, 0x26, w * 0.7f, h * 0.2f, u * 0.55f, 0x26);
        Facets.sakura(c, w, h, accent, fold, 1f, Facets.FOND);
        vignette(c, w, h, u);
    }

    // ------------------------------------------------------------------ Néon, Jade, Argent (0.66)

    /** Néon : ruelle de Tokyo sous la pluie, lueur magenta au bout de la rue. */
    private void ruelle(Canvas c, float w, float h, float u) {
        ciel(c, w, h, u, h * 0.5f, 0x16, w * 0.56f, h * 0.48f, u * 0.4f, 0x18);
        Facets.neon(c, w, h, accent, fold, 1f, Facets.FOND);
        vignette(c, w, h, u);
    }

    /** Jade : temple de montagne dans la brume, bambous aux bords. */
    private void temple(Canvas c, float w, float h, float u) {
        ciel(c, w, h, u, h * 0.62f, 0x1C, w * 0.78f, h * 0.3f, u * 0.5f, 0x1E);
        Facets.jade(c, w, h, accent, fold, 1f, Facets.FOND);
        vignette(c, w, h, u);
    }

    /** Argent : pleine lune et grues au-dessus d'une mer de nuages. */
    private void lune(Canvas c, float w, float h, float u) {
        ciel(c, w, h, u, h * 0.64f, 0x14, w * 0.72f, h * 0.27f, u * 0.5f, 0x1A);
        Facets.argent(c, w, h, accent, fold, 1f, Facets.FOND);
        vignette(c, w, h, u);
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
