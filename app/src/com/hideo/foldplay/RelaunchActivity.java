package com.hideo.foldplay;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Passerelle de « Redémarrer le jeu » pour les moteurs à processus séparé (PS3, 360). Le moteur
 * lance cette activité (processus principal, fond noir opaque : l'accueil ne se voit jamais), puis
 * meurt. Une fois son processus parti, on relance le jeu dans un processus neuf et on s'efface.
 */
public final class RelaunchActivity extends Activity {
    static final String EXTRA_AGAIN = "origami.again";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean done;

    /** Lance la passerelle depuis un moteur ; l'appelant termine ensuite son propre processus. */
    static void from(Activity engine) {
        engine.startActivity(new Intent(engine, RelaunchActivity.class)
                .putExtra(EXTRA_AGAIN, new Intent(engine.getIntent())));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF000000));
        final Intent again = getIntent().getParcelableExtra(EXTRA_AGAIN);
        if (again == null) {
            finish();
            return;
        }
        // Laisse au moteur le temps de mourir : sinon le système réutiliserait son processus.
        handler.postDelayed(new Runnable() {
            public void run() {
                if (done) return;
                done = true;
                try {
                    startActivity(again);
                } catch (Exception ignored) {
                    // Le jeu reste lançable depuis la bibliothèque.
                }
                finish();
                overridePendingTransition(0, 0);
            }
        }, 900);
    }

    @Override
    protected void onDestroy() {
        done = true;
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
