package com.hideo.foldplay;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

/**
 * Enregistrement vidéo de l'écran (0.66.21), lancé par la touche VIDÉO de la manette (ScreenCapture.video).
 * Service au premier plan (exigé par Android pour MediaProjection), dans le processus principal : il
 * continue pendant que le moteur tourne dans le sien, et s'arrête au second appui ou à la sortie du jeu.
 * L'image seule pour l'instant : le son du jeu demanderait un encodeur à part (AudioPlaybackCapture).
 */
public class RecordService extends Service {
    private static final String STOP = "stop";
    private MediaProjection projection;
    private VirtualDisplay display;
    private MediaRecorder recorder;
    private ParcelFileDescriptor pfd;
    private Uri uri;

    static void start(Context c, int result, Intent data, int w, int h, int dpi, String file) {
        Intent i = new Intent(c, RecordService.class).putExtra("result", result).putExtra("data", data)
                .putExtra("w", w).putExtra("h", h).putExtra("dpi", dpi).putExtra("file", file);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    static void stop(Context c) {
        c.startService(new Intent(c, RecordService.class).setAction(STOP));
    }

    @SuppressWarnings("deprecation")
    static boolean running(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo s : am.getRunningServices(50)) {
            if (s.service.getClassName().equals(RecordService.class.getName()) && s.started) return true;
        }
        return false;
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        if (i == null || STOP.equals(i.getAction())) {
            finish(true);
            return START_NOT_STICKY;
        }
        foreground();
        try {
            begin(i);
            I18n.toast(this, "Enregistrement vidéo lancé. Rappuie sur la touche pour l'arrêter.", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            DebugLog.event("[vidéo] démarrage impossible : " + e);
            I18n.toast(this, "L'enregistrement n'a pas pu démarrer.", android.widget.Toast.LENGTH_SHORT).show();
            finish(false);
        }
        return START_NOT_STICKY;
    }

    private void foreground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel("record", I18n.tr("Enregistrement vidéo"), NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, RecordService.class).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "record") : new Notification.Builder(this);
        Notification n = b.setSmallIcon(R.drawable.ic_foreground)
                .setContentTitle(I18n.tr("Enregistrement en cours"))
                .setContentText(I18n.tr("Touche VIDÉO de la manette ou ici pour arrêter."))
                .setContentIntent(stop)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(7, n);
        }
    }

    private void begin(Intent i) throws Exception {
        int w = i.getIntExtra("w", 1280), h = i.getIntExtra("h", 720), dpi = i.getIntExtra("dpi", 320);
        // Taille paire, plafonnée vers 1080p sur le grand côté : l'encodeur matériel refuse au-delà sur certaines puces.
        float s = Math.min(1f, 1920f / Math.max(w, h));
        w = Math.round(w * s) & ~1;
        h = Math.round(h * s) & ~1;
        String file = i.getStringExtra("file");
        recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Video.Media.DISPLAY_NAME, file);
            v.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Origami");
            v.put(MediaStore.Video.Media.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            pfd = getContentResolver().openFileDescriptor(uri, "w");
            recorder.setOutputFile(pfd.getFileDescriptor());
        } else {
            java.io.File dir = new java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Origami");
            dir.mkdirs();
            recorder.setOutputFile(new java.io.File(dir, file).getPath());
        }
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setVideoSize(w, h);
        recorder.setVideoFrameRate(60);
        recorder.setVideoEncodingBitRate(16_000_000);
        recorder.prepare();
        MediaProjectionManager pm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = pm.getMediaProjection(i.getIntExtra("result", 0), (Intent) i.getParcelableExtra("data"));
        // Android 14 exige un rappel enregistré avant de créer l'écran virtuel.
        projection.registerCallback(new MediaProjection.Callback() {
            public void onStop() { finish(true); }
        }, new android.os.Handler(android.os.Looper.getMainLooper()));
        display = projection.createVirtualDisplay("origami-record", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder.getSurface(), null, null);
        recorder.start();
    }

    private boolean done;

    private void finish(boolean keep) {
        if (done) return;
        done = true;
        boolean ok = false;
        try {
            if (recorder != null) {
                recorder.stop();
                ok = true;
            }
        } catch (Exception e) {
            DebugLog.event("[vidéo] arrêt : " + e);
        }
        if (recorder != null) recorder.release();
        if (display != null) display.release();
        if (projection != null) projection.stop();
        try {
            if (pfd != null) pfd.close();
        } catch (Exception ignored) {
        }
        if (uri != null) {
            if (ok && keep) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Video.Media.IS_PENDING, 0);
                getContentResolver().update(uri, v, null, null);
            } else {
                getContentResolver().delete(uri, null, null);
            }
        }
        if (ok && keep) I18n.toast(this, "Vidéo enregistrée dans Films/Origami.", android.widget.Toast.LENGTH_SHORT).show();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        finish(true);
        super.onDestroy();
    }
}
