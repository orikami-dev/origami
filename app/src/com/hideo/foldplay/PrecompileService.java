package com.hideo.foldplay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Service de premier plan de la précompilation PS3 (essais) : garde le processus du moteur en vie et le processeur
 * éveillé quand l'écran s'éteint, jusqu'à la fin de la compilation. Tourne dans le processus « :ps3 ».
 */
public final class PrecompileService extends Service {
    private static final String CHANNEL = "precompile";
    private static final String CHANNEL_DONE = "precompile_done";
    static final String ACTION_DONE = "com.hideo.foldplay.PRECOMPILE_DONE";
    static final String ACTION_PROGRESS = "com.hideo.foldplay.PRECOMPILE_PROGRESS";
    private long startedAt;
    private PowerManager.WakeLock lock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DONE.equals(intent.getAction())) {
            finished();
            return START_NOT_STICKY;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Précompilation", NotificationManager.IMPORTANCE_LOW));
        if (intent != null && ACTION_PROGRESS.equals(intent.getAction())) {
            // Progression en direct (0.67), dans la notification seulement : mise à jour sans bruit.
            startForeground(63, ongoing(intent.getStringExtra("text"), -1));
            return START_NOT_STICKY;
        }
        if (startedAt == 0) startedAt = android.os.SystemClock.elapsedRealtime();
        startForeground(63, ongoing("Le téléphone se réveillera à la fin de la compilation.", -1));
        if (lock == null) {
            lock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "origami:precompile");
            lock.acquire(12 * 3600 * 1000L);
        }
        return START_NOT_STICKY;
    }

    private Notification ongoing(String text, int shaderPct) {
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Origami compile les shaders")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOnlyAlertOnce(true)
                .setOngoing(true);
        b.setProgress(100, Math.max(0, Math.min(100, shaderPct)), shaderPct < 0);
        return b.build();
    }

    /** Compilation finie (0.67) : notification visible sur l'écran verrouillé, sonore, et l'écran se rallume quelques secondes. */
    private void finished() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL_DONE, "Compilation terminée", NotificationManager.IMPORTANCE_HIGH);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        android.app.PendingIntent tap = open == null ? null
                : android.app.PendingIntent.getActivity(this, 0, open, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL_DONE)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Compilation des shaders terminée")
                .setContentText("Le jeu est en pause, prêt à être lancé.")
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();
        // Lancé par startForegroundService : il faut passer au premier plan avant de s'arrêter (sinon Android tue l'app).
        startForeground(63, ongoing("Compilation terminée.", -1));
        nm.notify(64, n);
        // Réveille le téléphone : l'écran s'allume le temps de voir la notification.
        PowerManager.WakeLock screen = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "origami:precompile-done");
        screen.acquire(10000L);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (lock != null && lock.isHeld()) lock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
