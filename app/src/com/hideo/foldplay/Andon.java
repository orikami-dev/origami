package com.hideo.foldplay;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Andon (0.65.18, la lanterne de papier) : le fond autour de l'écran du jeu prend les couleurs de l'image, façon éclairage
 * d'ambiance derrière un téléviseur. L'image est lue en tout petit (PixelCopy), lissée dans le temps,
 * puis étirée en flou sur toute la scène, assombrie pour ne pas gêner. Modes : éteinte, thème (fond aux
 * couleurs du thème), image (suit l'image), mélange (image teintée par le thème), douce (suit l'image
 * lentement).
 */
final class Andon extends View {
    static final String[] KEYS = {"off", "theme", "image", "mix", "soft"};
    static final String[] LABELS = {"Éteinte", "Thème", "Image", "Image et thème", "Douce"};
    static final String[] HELP = {"Fond noir autour du jeu", "Les couleurs du thème, sans suivre le jeu",
            "Suit les couleurs de l'image en direct", "L'image, teintée par la couleur du thème",
            "Suit l'image lentement, sans à-coups"};

    private static final int W = 8, H = 5;
    private final SurfaceView source;
    private final Store store;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Bitmap grab = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
    private final Bitmap small = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    private final int[] cur = new int[W * H * 3];
    private final int[] px = new int[W * H];
    private String mode;
    private boolean running, busy, primed;

    private Andon(android.content.Context c, SurfaceView source, Store store) {
        super(c);
        this.source = source;
        this.store = store;
        mode = store.pills() ? store.glowMode() : "off";
    }

    /** Pose la lanterne sous tout le reste de la scène (premier enfant), et la démarre. */
    static Andon attach(FrameLayout stage, SurfaceView source, Store store) {
        Andon l = new Andon(stage.getContext(), source, store);
        stage.addView(l, 0, new FrameLayout.LayoutParams(-1, -1));
        l.start();
        return l;
    }

    void refresh() {
        mode = store.pills() ? store.glowMode() : "off";
        primed = false;
        invalidate();
    }

    void start() {
        if (running) return;
        running = true;
        main.post(tick);
    }

    void stop() {
        running = false;
        main.removeCallbacks(tick);
    }

    private boolean follows() {
        return mode.equals("image") || mode.equals("mix") || mode.equals("soft");
    }

    private final Runnable tick = new Runnable() {
        public void run() {
            if (!running) return;
            if (follows() && !busy && source.getHolder().getSurface() != null
                    && source.getHolder().getSurface().isValid() && source.getWidth() > 0) {
                busy = true;
                try {
                    PixelCopy.request(source, grab, new PixelCopy.OnPixelCopyFinishedListener() {
                        public void onPixelCopyFinished(int result) {
                            busy = false;
                            if (result == PixelCopy.SUCCESS) blend();
                        }
                    }, main);
                } catch (Throwable e) {
                    busy = false;
                }
            }
            main.postDelayed(this, mode.equals("soft") ? 250 : 120);
        }
    };

    /** Réduit la capture, la mêle à l'état précédent (lissage) et redessine. */
    private void blend() {
        Bitmap s = Bitmap.createScaledBitmap(grab, W, H, true);
        s.getPixels(px, 0, W, 0, 0, W, H);
        s.recycle();
        // Part de la nouvelle image à chaque lecture : vive en mode Image, lente en mode Douce.
        int k = !primed ? 256 : mode.equals("soft") ? 26 : 90;
        int tr = Color.red(Theme.CODEC), tg = Color.green(Theme.CODEC), tb = Color.blue(Theme.CODEC);
        boolean mix = mode.equals("mix");
        for (int i = 0; i < px.length; i++) {
            int r = Color.red(px[i]), g = Color.green(px[i]), b = Color.blue(px[i]);
            if (mix) {
                r = (r * 3 + tr * 2) / 5;
                g = (g * 3 + tg * 2) / 5;
                b = (b * 3 + tb * 2) / 5;
            }
            cur[i * 3] += (r - cur[i * 3]) * k / 256;
            cur[i * 3 + 1] += (g - cur[i * 3 + 1]) * k / 256;
            cur[i * 3 + 2] += (b - cur[i * 3 + 2]) * k / 256;
            // Assombrie de moitié : une lumière d'ambiance, pas un second écran.
            px[i] = Color.rgb(cur[i * 3] * 55 / 100, cur[i * 3 + 1] * 55 / 100, cur[i * 3 + 2] * 55 / 100);
        }
        primed = true;
        small.setPixels(px, 0, W, 0, 0, W, H);
        invalidate();
    }

    // Cadre d'Andon (0.65.31) : un liseré par thème qui enferme la lumière. Tracés mis en cache, refaits si la taille ou le thème change.
    private final Path frameThin = new Path(), frameBold = new Path(), frameFill = new Path();
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int frameW, frameH, frameColor;
    private String frameTheme = "";

    @Override
    protected void onDraw(Canvas c) {
        if (mode.equals("off")) return;
        float d = getResources().getDisplayMetrics().density;
        // La lumière reste à l'intérieur du cadre ; le cadre est sous le jeu et l'interface (premier enfant).
        c.save();
        c.clipRect(8 * d, 8 * d, getWidth() - 8 * d, getHeight() - 8 * d);
        glow(c);
        c.restore();
        drawFrame(c, d);
    }

    private void buildFrame(float d) {
        frameThin.reset();
        frameBold.reset();
        frameFill.reset();
        frameW = getWidth();
        frameH = getHeight();
        frameTheme = Theme.THEME_ID;
        frameColor = Theme.CODEC;
        float i = 4 * d, w = frameW, h = frameH;
        Path corner = new Path(), fill = new Path();
        if (frameTheme.equals("arctique")) {
            frameColor = Theme.lighten(Theme.CODEC, 0.4f);
            float k = 8 * d;
            frameThin.moveTo(i + k, i);
            frameThin.lineTo(w - i - k, i);
            frameThin.lineTo(w - i, i + k);
            frameThin.lineTo(w - i, h - i - k);
            frameThin.lineTo(w - i - k, h - i);
            frameThin.lineTo(i + k, h - i);
            frameThin.lineTo(i, h - i - k);
            frameThin.lineTo(i, i + k);
            frameThin.close();
            corner.moveTo(4 * d, 8 * d);
            corner.lineTo(8 * d, 4 * d);
        } else if (frameTheme.equals("ecarlate")) {
            frameThin.addRect(i, i, w - i, h - i, Path.Direction.CW);
            corner.addArc(new RectF(-5 * d, -5 * d, 5 * d, 5 * d), 0, 90);
            corner.addArc(new RectF(-10 * d, -10 * d, 10 * d, 10 * d), 0, 90);
            corner.moveTo(0, 0);
            corner.lineTo(7 * d, 7 * d);
            corner.moveTo(0, 0);
            corner.lineTo(10 * d, 0);
            corner.moveTo(0, 0);
            corner.lineTo(0, 10 * d);
        } else if (frameTheme.equals("sakura")) {
            frameThin.addRoundRect(new RectF(i, i, w - i, h - i), 9 * d, 9 * d, Path.Direction.CW);
            Path petal = new Path();
            petal.moveTo(0, 0);
            petal.quadTo(3 * d, -2.2f * d, 6 * d, 0);
            petal.quadTo(3 * d, 2.2f * d, 0, 0);
            float[] ang = {15, 45, 75};
            for (int a = 0; a < 3; a++) {
                Matrix pm = new Matrix();
                pm.setRotate(ang[a]);
                pm.postTranslate(3 * d, 3 * d);
                fill.addPath(petal, pm);
            }
        } else if (frameTheme.equals("ambre")) {
            frameThin.addRect(i, i, w - i, h - i, Path.Direction.CW);
            corner.moveTo(0, 10 * d);
            corner.lineTo(3 * d, 10 * d);
            corner.lineTo(3 * d, 6 * d);
            corner.lineTo(6 * d, 6 * d);
            corner.lineTo(6 * d, 3 * d);
            corner.lineTo(10 * d, 3 * d);
            corner.lineTo(10 * d, 0);
        } else if (frameTheme.equals("neon")) {
            // Néon : double tube aux coins arrondis, l'intérieur au cyan du thème.
            frameColor = Theme.lighten(Theme.CODEC, 0.15f);
            frameThin.addRoundRect(new RectF(i, i, w - i, h - i), 7 * d, 7 * d, Path.Direction.CW);
            frameBold.addRoundRect(new RectF(i + 3 * d, i + 3 * d, w - i - 3 * d, h - i - 3 * d), 5 * d, 5 * d, Path.Direction.CW);
            Path drop = new Path();
            drop.moveTo(8 * d, 5 * d);
            drop.quadTo(10.5f * d, 8.5f * d, 10 * d, 10 * d);
            drop.quadTo(8 * d, 12 * d, 6 * d, 10 * d);
            drop.quadTo(5.5f * d, 8.5f * d, 8 * d, 5 * d);
            drop.close();
            fill.addPath(drop);
        } else if (frameTheme.equals("jade")) {
            // Jade : filet simple, nœuds de bambou le long des bords près des coins, petite lanterne dans le coin.
            frameColor = Theme.lighten(Theme.CODEC, 0.1f);
            frameThin.addRect(i, i, w - i, h - i, Path.Direction.CW);
            corner.moveTo(14 * d, -2.5f * d);
            corner.lineTo(14 * d, 2.5f * d);
            corner.moveTo(-2.5f * d, 14 * d);
            corner.lineTo(2.5f * d, 14 * d);
            corner.moveTo(26 * d, -1.5f * d);
            corner.lineTo(26 * d, 1.5f * d);
            corner.moveTo(-1.5f * d, 26 * d);
            corner.lineTo(1.5f * d, 26 * d);
            fill.addOval(new RectF(4 * d, 3.5f * d, 9 * d, 10.5f * d), Path.Direction.CW);
        } else if (frameTheme.equals("argent")) {
            // Argent : filet fin, pli d'origami en biais dans chaque coin, petite pleine lune.
            frameColor = Theme.lighten(Theme.CODEC, 0.35f);
            frameThin.addRect(i, i, w - i, h - i, Path.Direction.CW);
            corner.moveTo(0, 12 * d);
            corner.lineTo(12 * d, 0);
            fill.addCircle(4.2f * d, 4.2f * d, 2 * d, Path.Direction.CW);
        } else { // codec : double filet et équerres
            frameThin.addRect(i, i, w - i, h - i, Path.Direction.CW);
            frameThin.addRect(i + 3 * d, i + 3 * d, w - i - 3 * d, h - i - 3 * d, Path.Direction.CW);
            corner.moveTo(0, 10 * d);
            corner.lineTo(0, 0);
            corner.lineTo(10 * d, 0);
        }
        float[][] cs = {{i, i, 1, 1}, {w - i, i, -1, 1}, {i, h - i, 1, -1}, {w - i, h - i, -1, -1}};
        for (int n = 0; n < 4; n++) {
            Matrix m = new Matrix();
            m.setScale(cs[n][2], cs[n][3]);
            m.postTranslate(cs[n][0], cs[n][1]);
            if (frameTheme.equals("codec") || frameTheme.equals("ambre") || frameTheme.equals("jade")) frameBold.addPath(corner, m);
            else frameThin.addPath(corner, m);
            frameFill.addPath(fill, m);
        }
    }

    private void drawFrame(Canvas c, float d) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        if (frameW != getWidth() || frameH != getHeight() || !frameTheme.equals(Theme.THEME_ID)
                || frameColor == 0) buildFrame(d);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeJoin(Paint.Join.MITER);
        framePaint.setColor(Theme.alpha(frameColor, 0xB0));
        framePaint.setStrokeWidth(1.2f * d);
        c.drawPath(frameThin, framePaint);
        framePaint.setColor(Theme.alpha(frameColor, 0xE0));
        framePaint.setStrokeWidth(2 * d);
        c.drawPath(frameBold, framePaint);
        framePaint.setStyle(Paint.Style.FILL);
        framePaint.setColor(Theme.alpha(frameColor, 0xC0));
        c.drawPath(frameFill, framePaint);
    }

    private void glow(Canvas c) {
        if (mode.equals("theme") || !primed) {
            int a = Theme.darken(Theme.CODEC, 0.72f), b = Theme.darken(Theme.STEEL, 0.8f);
            Shader sh = new android.graphics.LinearGradient(0, 0, getWidth(), getHeight(),
                    new int[] {a, Color.BLACK, b}, null, Shader.TileMode.CLAMP);
            Paint p = new Paint();
            p.setShader(sh);
            c.drawRect(0, 0, getWidth(), getHeight(), p);
            return;
        }
        // Les couleurs sont calées sur l'image réelle du jeu, à l'endroit où elle est : au-delà de ses bords,
        // la couleur du bord se prolonge. Une rotation, un pliage ou un autre format refont donc la lumière,
        // sans couper l'interface qui passe par-dessus (0.65.25).
        int[] me = new int[2], at = new int[2];
        getLocationInWindow(me);
        source.getLocationInWindow(at);
        float gw = source.getWidth(), gh = source.getHeight();
        RectF game = gw > 0 && gh > 0
                ? new RectF(at[0] - me[0], at[1] - me[1], at[0] - me[0] + gw, at[1] - me[1] + gh)
                : new RectF(0, 0, getWidth(), getHeight());
        Matrix m = new Matrix();
        m.setRectToRect(new RectF(0, 0, W, H), game, Matrix.ScaleToFit.FILL);
        BitmapShader sh = new BitmapShader(small, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        sh.setLocalMatrix(m);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setShader(sh);
        c.drawRect(0, 0, getWidth(), getHeight(), p);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }
}
