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
    private PowerManager.WakeLock lock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Précompilation", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Origami compile les shaders")
                .setContentText("Le jeu se mettra en pause à la fin.")
                .setOngoing(true)
                .build();
        startForeground(63, n);
        if (lock == null) {
            lock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "origami:precompile");
            lock.acquire(12 * 3600 * 1000L);
        }
        return START_NOT_STICKY;
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
