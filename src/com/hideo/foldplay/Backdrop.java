package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Illustrations de fond de l'accueil, une par jeu, gardées dans files/backdrops.
 * Un jeu sans illustration laisse un fichier « .none » pour ne pas redemander avant une semaine.
 */
final class Backdrop {
    interface Callback {
        void onBackdrop(Game g, Bitmap b);
    }

    private static final long RETRY_MS = 7L * 24 * 3600 * 1000;

    private final File dir;
    private final CoverFetcher fetcher;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(6);

    private final android.content.ContentResolver cr;

    Backdrop(Context ctx, CoverFetcher fetcher) {
        cr = ctx.getContentResolver();
        dir = new File(ctx.getFilesDir(), "backdrops2");
        Backdrop.delete(new File(ctx.getFilesDir(), "backdrops"));
        this.fetcher = fetcher;
    }

    private File file(Game g, String ext) {
        return new File(new File(dir, g.system.id), Integer.toHexString(g.uri.hashCode()) + ext);
    }

    Bitmap cached(Game g) {
        return cache.get(g.uri);
    }

    /** Charge (ou télécharge si permis) l'illustration ; le rappel arrive sur le fil principal, même sans image. */
    void load(final Game g, final boolean download, final Callback cb) {
        Bitmap hit = cache.get(g.uri);
        if (hit != null) {
            cb.onBackdrop(g, hit);
            return;
        }
        worker.execute(new Runnable() {
            public void run() {
                Bitmap bm = null;
                File img = file(g, ".img");
                File none = file(g, ".none");
                try {
                    if (!img.exists() && download
                            && (!none.exists() || System.currentTimeMillis() - none.lastModified() > RETRY_MS)) {
                        byte[] b = fetcher.artwork(g);
                        img.getParentFile().mkdirs();
                        if (b != null) {
                            FileOutputStream out = new FileOutputStream(img);
                            out.write(b);
                            out.close();
                            none.delete();
                        } else {
                            new FileOutputStream(none).close();
                        }
                    }
                    if (img.exists()) bm = decode(img);
                    // Une illustration doit être large (16:10 ou plus) : le reste gênerait la lecture.
                    if (bm != null && bm.getWidth() < bm.getHeight() * 1.5f) {
                        bm = null;
                        img.delete();
                        new FileOutputStream(none).close();
                    }
                    // Sinon : la jaquette, très floutée et assombrie, ne garde que ses couleurs.
                    if (bm == null && g.coverUri != null) bm = wash(cr, g.coverUri);
                } catch (Exception ignored) {
                    // Pas de réseau : le fond reste sombre.
                }
                if (bm != null) cache.put(g.uri, bm);
                final Bitmap out = bm;
                main.post(new Runnable() {
                    public void run() {
                        cb.onBackdrop(g, out);
                    }
                });
            }
        });
    }

    /** Jaquette réduite à 12 px puis agrandie avec lissage : un lavis de ses couleurs, sans détail. */
    private static Bitmap wash(android.content.ContentResolver cr, String uri) {
        try {
            java.io.InputStream in = cr.openInputStream(android.net.Uri.parse(uri));
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 8;
            Bitmap src = BitmapFactory.decodeStream(in, null, o);
            in.close();
            if (src == null) return null;
            Bitmap tiny = Bitmap.createScaledBitmap(src, 12, Math.max(1, 12 * src.getHeight() / Math.max(1, src.getWidth())), true);
            Bitmap mid = Bitmap.createScaledBitmap(tiny, 48, Math.max(1, 48 * tiny.getHeight() / tiny.getWidth()), true);
            Bitmap big = Bitmap.createScaledBitmap(mid, 480, Math.max(1, 480 * mid.getHeight() / mid.getWidth()), true);
            Bitmap out = big.copy(Bitmap.Config.ARGB_8888, true);
            android.graphics.Canvas c = new android.graphics.Canvas(out);
            c.drawColor(0x99000000);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap decode(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 960) sample *= 2;
        BitmapFactory.Options real = new BitmapFactory.Options();
        real.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), real);
    }

    void clear() {
        cache.evictAll();
        delete(dir);
    }

    static void delete(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) delete(k);
        f.delete();
    }
}
