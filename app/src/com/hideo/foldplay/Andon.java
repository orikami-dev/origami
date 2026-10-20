package com.hideo.foldplay;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
        mode = store.glowMode();
    }

    /** Pose la lanterne sous tout le reste de la scène (premier enfant), et la démarre. */
    static Andon attach(FrameLayout stage, SurfaceView source, Store store) {
        Andon l = new Andon(stage.getContext(), source, store);
        stage.addView(l, 0, new FrameLayout.LayoutParams(-1, -1));
        l.start();
        return l;
    }

    void refresh() {
        mode = store.glowMode();
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

    @Override
    protected void onDraw(Canvas c) {
        if (mode.equals("off")) return;
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
