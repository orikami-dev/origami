package com.hideo.foldplay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/** Réponse d'Android à une session d'installation : ouvre la confirmation demandée, sinon note le résultat. */
public final class InstallStatusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    ctx.startActivity(confirm);
                } catch (Exception e) {
                    DebugLog.event("[maj] confirmation impossible à ouvrir", e);
                }
            }
            return;
        }
        DebugLog.event("[maj] installation : statut " + status + " "
                + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
    }
}
