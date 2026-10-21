package com.hideo.foldplay;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.PixelCopy;
import android.view.SurfaceView;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Touches CAPTURE et VIDÉO de la manette (0.66.21), communes à toutes les consoles.
 * Capture : l'image du jeu (la surface du moteur, sans la manette), dans Images/Origami.
 * Vidéo : l'écran entier, enregistré par RecordService dans Films/Origami ; un appui démarre, un autre arrête.
 */
final class ScreenCapture {
    private ScreenCapture() {
    }

    /** Code de la demande d'autorisation d'enregistrer l'écran (onActivityResult des écrans de jeu). */
    static final int REQUEST = 0x5C4E;

    static String stamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
    }

    /** Nom de fichier lisible : titre du jeu sans caractères interdits, puis la date. */
    static String name(String title, String ext) {
        String t = title == null ? "Origami" : title.replaceAll("[\\\\/:*?\"<>|]", " ").trim();
        if (t.length() > 60) t = t.substring(0, 60).trim();
        return (t.isEmpty() ? "Origami" : t) + " " + stamp() + ext;
    }

    static void shot(final Activity a, SurfaceView screen, final String title) {
        if (screen == null || screen.getWidth() <= 0 || screen.getHolder().getSurface() == null
                || !screen.getHolder().getSurface().isValid()) {
            I18n.toast(a, "Pas d'image à capturer pour l'instant.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        final Bitmap bmp = Bitmap.createBitmap(screen.getWidth(), screen.getHeight(), Bitmap.Config.ARGB_8888);
        PixelCopy.request(screen, bmp, new PixelCopy.OnPixelCopyFinishedListener() {
            public void onPixelCopyFinished(int result) {
                if (result != PixelCopy.SUCCESS) {
                    DebugLog.event("[capture] PixelCopy refusé : " + result);
                    I18n.toast(a, "La capture n'a pas pu lire l'image du jeu.", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                new Thread(new Runnable() {
                    public void run() {
                        final boolean ok = save(a, bmp, name(title, ".png"));
                        bmp.recycle();
                        a.runOnUiThread(new Runnable() {
                            public void run() {
                                I18n.toast(a, ok ? "Capture enregistrée dans Images/Origami." : "La capture n'a pas pu être enregistrée.",
                                        android.widget.Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }, "origami-capture").start();
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private static boolean save(Activity a, Bitmap bmp, String file) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, file);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Origami");
                Uri uri = a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return false;
                OutputStream out = a.getContentResolver().openOutputStream(uri);
                try {
                    return bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                } finally {
                    out.close();
                }
            }
            java.io.File dir = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Origami");
            dir.mkdirs();
            OutputStream out = new java.io.FileOutputStream(new java.io.File(dir, file));
            try {
                return bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            } finally {
                out.close();
            }
        } catch (Exception e) {
            DebugLog.event("[capture] enregistrement impossible : " + e);
            return false;
        }
    }

    /** Démarre ou arrête l'enregistrement vidéo. Le premier démarrage passe par l'autorisation d'Android. */
    static void video(Activity a, String title) {
        if (RecordService.running(a)) {
            RecordService.stop(a);
            return;
        }
        pendingTitle = title;
        MediaProjectionManager pm = (MediaProjectionManager) a.getSystemService(Activity.MEDIA_PROJECTION_SERVICE);
        try {
            a.startActivityForResult(pm.createScreenCaptureIntent(), REQUEST);
        } catch (Exception e) {
            I18n.toast(a, "Ce téléphone ne permet pas d'enregistrer l'écran.", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private static String pendingTitle;

    /** À appeler depuis onActivityResult ; vrai si la réponse était pour nous. */
    static boolean onResult(Activity a, int request, int result, Intent data) {
        if (request != REQUEST) return false;
        if (result != Activity.RESULT_OK || data == null) {
            I18n.toast(a, "Enregistrement annulé.", android.widget.Toast.LENGTH_SHORT).show();
            return true;
        }
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        RecordService.start(a, result, data, dm.widthPixels, dm.heightPixels, dm.densityDpi, name(pendingTitle, ".mp4"));
        return true;
    }

    /** L'écran du jeu se ferme : l'enregistrement s'arrête avec lui. */
    static void gameClosed(Activity a) {
        if (RecordService.running(a)) RecordService.stop(a);
    }
}
