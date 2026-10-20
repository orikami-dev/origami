package com.hideo.foldplay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Après une mise à jour (0.65.13) : Android ferme l'app pour la remplacer et ne la relance pas
 * (lancement en arrière-plan interdit). Une notification permet de la rouvrir d'un geste.
 */
public final class UpdatedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;
        DebugLog.event("[maj] Origami " + Updater.installed(ctx) + " installée");
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 33
                && ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        I18n.init(ctx);
        nm.createNotificationChannel(new NotificationChannel("updates", I18n.tr("Mises à jour"), NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 8, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(ctx, "updates")
                .setSmallIcon(R.drawable.ic_foreground)
                .setContentTitle("Origami " + Updater.installed(ctx) + " " + I18n.tr("installé"))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        nm.cancel(62);
        nm.notify(63, n);
    }
}
