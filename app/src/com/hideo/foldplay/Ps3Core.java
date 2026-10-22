package com.hideo.foldplay;

import java.io.File;

/**
 * Le moteur PS3 : ARMSX3 (RPCS3 pour Android, GPL-2.0-only), chargé par le pont
 * libfoldplay_ps3.so. Seulement dans le processus « :ps3 » (Ps3Activity) : jamais dans le même
 * programme que le cœur PS2 (GPL-3).
 */
final class Ps3Core {
    /** Événements de surface attendus par le cœur. */
    static final int SURFACE_CREATED = 0, SURFACE_CHANGED = 1, SURFACE_DESTROYED = 2;

    /** Résultats de démarrage (game_boot_result de RPCS3, dans l'ordre). */
    static final String[] BOOT_RESULTS = {
        "NoErrors", "GenericError", "NothingToBoot", "WrongDiscLocation", "InvalidFileOrFolder",
        "InvalidBDvdFolder", "InstallFailed", "DecryptionError", "FileCreationError", "FirmwareMissing",
        "FirmwareVersion", "UnsupportedDiscType", "SavestateCorrupted", "SavestateVersionUnsupported",
        "StillRunning", "AlreadyAdded", "CurrentlyRestricted", "DatabaseConfigMissing",
    };
    static final int BOOT_OK = 0, BOOT_ALREADY_ADDED = 15;

    /** États (system_state de RPCS3). */
    static final int STOPPED = 0, LOADING = 1, STOPPING = 2, RUNNING = 3, PAUSED = 4, FROZEN = 5, READY = 6, STARTING = 7;

    // Touches de la manette PS3 (pad_types.h) : premier mot, puis second mot.
    static final int D1_SELECT = 0x01, D1_L3 = 0x02, D1_R3 = 0x04, D1_START = 0x08, D1_UP = 0x10,
            D1_RIGHT = 0x20, D1_DOWN = 0x40, D1_LEFT = 0x80, D1_PS = 0x100;
    static final int D2_L2 = 0x01, D2_R2 = 0x02, D2_L1 = 0x04, D2_R1 = 0x08, D2_TRIANGLE = 0x10,
            D2_CIRCLE = 0x20, D2_CROSS = 0x40, D2_SQUARE = 0x80;

    private static boolean loaded;

    private Ps3Core() {
    }

    /** Le cœur est livré dans l'APK (ou installé à côté) : sinon pas de PS3 intégrée. */
    static File coreFile(android.content.Context ctx) {
        return new File(ctx.getApplicationInfo().nativeLibraryDir, "libarmsx3-core.so");
    }

    static boolean present(android.content.Context ctx) {
        return coreFile(ctx).isFile() && new File(ctx.getApplicationInfo().nativeLibraryDir, "libfoldplay_ps3.so").isFile();
    }

    static synchronized boolean load(android.content.Context ctx) {
        if (loaded) return true;
        try {
            System.loadLibrary("foldplay_ps3");
        } catch (Throwable t) {
            return false;
        }
        loaded = nativeOpen(coreFile(ctx).getPath());
        return loaded;
    }

    /** Dossier racine du cœur (config, dev_flash, dev_hdd0, cache), avec la barre finale. */
    static String root(android.content.Context ctx) {
        File base = ctx.getExternalFilesDir(null);
        File d = new File(base != null ? base : ctx.getFilesDir(), "ps3");
        d.mkdirs();
        return d.getPath() + "/";
    }

    static boolean firmwareInstalled(android.content.Context ctx) {
        return new File(root(ctx) + "config/dev_flash/vsh/module/vsh.self").isFile();
    }

    static String bootName(int r) {
        return r >= 0 && r < BOOT_RESULTS.length ? BOOT_RESULTS[r] : "GenericError";
    }

    /** Valeur de stick PS3 : 0 à 255, 128 au centre. */
    static int stick(float v) {
        return Math.max(0, Math.min(255, Math.round(128.5f + v * (v < 0 ? 128f : 127f))));
    }

    static native boolean nativeOpen(String corePath);
    static native boolean nativeSetDriver(String dir, String lib, String hooks);
    static native String nativeLastError();
    static native boolean nativeInitialize(String root, String user, String soc);
    static native int nativeBoot(String path);
    static native int nativeState();
    static native void nativeKill();
    static native void nativePause();
    static native void nativeResume();
    static native void nativeBackground(boolean background);
    static native void nativeHomeMenu();
    static native String nativeTitle();
    static native String nativeTitleId();
    static native long nativeFramePeriodNs();
    static native boolean nativeSurface(android.view.Surface surface, int event);
    static native void nativeSurfaceSize(int width, int height);
    static native boolean nativePad(int port, int d1, int d2, int lx, int ly, int rx, int ry);
    static native boolean nativeInstallFw(int fd, long progressId);
    static native boolean nativeInstallPkg(int fd, long progressId);
    static native boolean nativeSettingsSet(String path, String value);
    static native String nativeSettingsGet(String path);
    static native boolean nativeSaveState(int slot);
    static native boolean nativeLoadState(int slot);
    static native boolean nativeHasState(int slot);
    static native String nativeVersion();
}
