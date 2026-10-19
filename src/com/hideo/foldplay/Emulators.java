package com.hideo.foldplay;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

/**
 * Émulateurs open source conseillés pour les consoles que le moteur intégré ne fait pas encore
 * tourner (rendu 3D). Installation : page Play Store quand elle existe, sinon site ou GitHub.
 */
final class Emulators {
    static final class Entry {
        final String name;
        final String systems;
        final String[] ids;
        /** Identifiant Play Store, ou null si l'émulateur n'y est pas. */
        final String play;
        final String url;

        Entry(String name, String systems, String[] ids, String play, String url) {
            this.name = name;
            this.systems = systems;
            this.ids = ids;
            this.play = play;
            this.url = url;
        }
    }

    static final Entry[] ALL = {
        new Entry("PPSSPP", "PSP", new String[] {"psp"}, "org.ppsspp.ppsspp", "https://www.ppsspp.org/"),
        new Entry("Dolphin", "GameCube et Wii", new String[] {"gc", "wii"}, "org.dolphinemu.dolphinemu",
                "https://dolphin-emu.org/"),
        new Entry("Mupen64Plus FZ", "Nintendo 64", new String[] {"n64"}, "org.mupen64plusae.v3.fzurita",
                "https://github.com/mupen64plus-ae/mupen64plus-ae"),
        new Entry("Azahar", "Nintendo 3DS", new String[] {"3ds"}, "io.github.lime3ds.android", "https://azahar-emu.org/"),
        new Entry("Flycast", "Dreamcast", new String[] {"dc"}, "com.flycast.emulator",
                "https://github.com/flyinghead/flycast"),
        new Entry("ARMSX2", "PlayStation 2", new String[] {"ps2"}, null, "https://armsx2.net/"),
        new Entry("Eden", "Nintendo Switch", new String[] {"switch"}, null, "https://eden-emu.dev/"),
        new Entry("Vita3K", "PS Vita", new String[] {"vita"}, null, "https://vita3k.org/"),
    };

    private Emulators() {
    }

    static Entry forSystem(String systemId) {
        for (Entry e : ALL) for (String id : e.ids) if (id.equals(systemId)) return e;
        return null;
    }

    static boolean installed(Activity a, Entry e) {
        if (e.play == null) return false;
        try {
            a.getPackageManager().getPackageInfo(e.play, 0);
            return true;
        } catch (PackageManager.NameNotFoundException x) {
            return false;
        }
    }

    /** Où on l'installe : « Play Store » ou « site officiel ». */
    static String source(Entry e) {
        return e.play != null ? "Play Store" : "site officiel";
    }

    /** Ouvre la page Play Store (appli, puis navigateur), sinon le site ou le GitHub. */
    static void open(Activity a, Entry e) {
        if (e.play != null) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + e.play))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException ignored) {
                // Pas d'appli Play Store : on passe par le navigateur.
            }
            if (browse(a, "https://play.google.com/store/apps/details?id=" + e.play)) return;
        }
        if (!browse(a, e.url)) I18n.toast(a, e.url, Toast.LENGTH_LONG).show();
    }

    private static boolean browse(Activity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        } catch (ActivityNotFoundException x) {
            return false;
        }
    }
}
