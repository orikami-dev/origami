package com.hideo.foldplay;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

/** Description de la sortie son pour le journal (Bluetooth, USB, haut-parleur : le décalage son/image en dépend). */
final class Ps3Audio {
    static String describe(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            StringBuilder b = new StringBuilder(", son : ");
            AudioDeviceInfo[] outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            boolean any = false;
            for (AudioDeviceInfo d : outs) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE || t == AudioDeviceInfo.TYPE_TELEPHONY) continue;
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLE_HEADSET
                        || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || t == AudioDeviceInfo.TYPE_WIRED_HEADSET
                        || t == AudioDeviceInfo.TYPE_USB_HEADSET || t == AudioDeviceInfo.TYPE_USB_DEVICE
                        || t == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    b.append(any ? "+" : "").append("type").append(t);
                    any = true;
                }
            }
            b.append(", natif ").append(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)).append(" Hz/")
                    .append(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)).append(" images tampon");
            b.append(", volume ").append(am.getStreamVolume(AudioManager.STREAM_MUSIC)).append("/")
                    .append(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            return b.toString();
        } catch (Throwable t) {
            return ", son illisible";
        }
    }
}
