package com.hideo.foldplay;

import android.content.Context;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Vibration des touches de la manette, réglée dans l'éditeur : 0 coupée, 1 légère, 2 moyenne,
 * 3 forte. Passe par le vibreur (et non le retour tactile du système, que One UI peut couper).
 */
final class Haptics {
    private static final long[] MS = {0, 8, 14, 22};
    private static final int[] AMP = {0, 70, 150, 255};

    private Haptics() {
    }

    static void tap(View v) {
        int level = new Store(v.getContext()).hapticLevel();
        if (level <= 0) return;
        level = Math.min(3, level);
        Vibrator vib = (Vibrator) v.getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vib == null || !vib.hasVibrator()) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            return;
        }
        try {
            int amp = vib.hasAmplitudeControl() ? AMP[level] : VibrationEffect.DEFAULT_AMPLITUDE;
            vib.vibrate(VibrationEffect.createOneShot(MS[level], amp));
        } catch (RuntimeException e) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }
}
