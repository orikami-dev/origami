package com.hideo.foldplay;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Charge les jaquettes (image du même nom que le jeu) en arrière-plan, avec un petit cache. */
final class CoverLoader {
    private static final int TARGET_PX = 360;

    private final ContentResolver cr;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(64);

    private android.content.pm.PackageManager pm;

    CoverLoader(ContentResolver cr) {
        this.cr = cr;
    }

    /** Pour les icônes des applications (jaquettes « app: »). */
    CoverLoader(ContentResolver cr, android.content.pm.PackageManager pm) {
        this(cr);
        this.pm = pm;
    }

    /** Une vue qui sait afficher une jaquette. */
    interface Target {
        void setCover(Bitmap b);

        void setTag(Object tag);

        Object getTag();
    }

    void bind(CartridgeView view, Game g) {
        bind(view, g.coverUri);
    }

    void bind(final Target view, final String uri) {
        view.setTag(uri);
        if (uri == null) {
            view.setCover(null);
            return;
        }
        Bitmap hit = cache.get(uri);
        view.setCover(hit);
        if (hit != null) return;
        pool.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bm = decode(uri);
                if (bm == null) return;
                cache.put(uri, bm);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (uri.equals(view.getTag())) view.setCover(bm);
                    }
                });
            }
        });
    }

    private Bitmap decode(String uri) {
        if (uri.startsWith("app:")) return pm == null ? null : MobileApps.icon(pm, uri, 192);
        try {
            Uri u = Uri.parse(uri);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = cr.openInputStream(u);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int sample = 1;
            while (o.outWidth / (sample * 2) >= TARGET_PX && o.outHeight / (sample * 2) >= TARGET_PX) {
                sample *= 2;
            }
            BitmapFactory.Options real = new BitmapFactory.Options();
            real.inSampleSize = sample;
            in = cr.openInputStream(u);
            Bitmap bm = BitmapFactory.decodeStream(in, null, real);
            in.close();
            return bm;
        } catch (Exception e) {
            return null;
        }
    }
}
