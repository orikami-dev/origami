package com.hideo.foldplay;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Petit signal d'appel à l'ouverture des menus : cinq bips carrés alternés, synthétisés ici
 * (aucun son repris d'un jeu existant). Se coupe dans Réglages > Affichage.
 */
final class CodecSound {
    private static final int RATE = 22050;
    private static short[] pcm;

    private CodecSound() {
    }

    static void call(Context ctx) {
        if (!new Store(ctx).menuSound()) return;
        try {
            short[] data = samples();
            final AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(data.length * 2)
                    .build();
            t.write(data, 0, data.length);
            t.setNotificationMarkerPosition(data.length - 1);
            t.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                public void onMarkerReached(AudioTrack track) {
                    track.release();
                }

                public void onPeriodicNotification(AudioTrack track) {
                }
            });
            t.play();
        } catch (Exception ignored) {
            // Pas de son plutôt qu'un plantage.
        }
    }

    private static synchronized short[] samples() {
        if (pcm != null) return pcm;
        float[] tones = {1480f, 1975f, 1480f, 1975f, 2490f};
        int beep = RATE * 38 / 1000;
        int gap = RATE * 22 / 1000;
        short[] out = new short[tones.length * (beep + gap)];
        int p = 0;
        for (float f : tones) {
            for (int i = 0; i < beep; i++) {
                double phase = (i * f / RATE) % 1.0;
                double env = Math.min(1.0, Math.min(i, beep - i) / (RATE * 0.004));
                out[p++] = (short) ((phase < 0.5 ? 1 : -1) * 2600 * env);
            }
            p += gap;
        }
        pcm = out;
        return out;
    }
}
