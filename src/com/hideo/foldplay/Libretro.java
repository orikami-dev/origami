package com.hideo.foldplay;

import java.nio.ByteBuffer;

/** Accès au pont natif libfoldplay.so. Un seul cœur à la fois, appelé depuis le fil d'émulation. */
final class Libretro {
    // Touches RetroPad (RETRO_DEVICE_ID_JOYPAD_*).
    static final int B = 0, Y = 1, SELECT = 2, START = 3, UP = 4, DOWN = 5, LEFT = 6, RIGHT = 7,
            A = 8, X = 9, L = 10, R = 11, L2 = 12, R2 = 13, L3 = 14, R3 = 15;
    static final int MEMORY_SAVE_RAM = 0;

    private static Boolean available;

    private Libretro() {
    }

    /** Vrai si le pont natif se charge (appareil arm64). */
    static synchronized boolean available() {
        if (available == null) {
            try {
                System.loadLibrary("foldplay");
                available = Boolean.TRUE;
            } catch (Throwable t) {
                available = Boolean.FALSE;
            }
        }
        return available;
    }

    /** Consoles dont les boutons portent les lettres A, B, X, Y : on les associe par nom. */
    private static final String NINTENDO = " nes snes n64 gc wii switch gb gbc gba nds 3ds ";

    /**
     * Bouton de la manette Origami → touche RetroPad, pour que l'entrée reçue par le jeu ne
     * change pas d'une console à l'autre.
     * Consoles Nintendo : par nom (A envoie A, B envoie B), ce qu'affiche le jeu.
     * Autres consoles (PlayStation, Sega, PC Engine), sans lettres A/B/X/Y : par position, comme
     * RetroArch ; le bouton du bas (A chez nous) donne Croix sur PS1, celui de droite Rond, etc.
     */
    static int retroId(String key, String system) {
        if ("n64".equals(system)) return n64(key);
        boolean byName = system != null && NINTENDO.contains(" " + system + " ");
        if (key.equals("A")) return byName ? A : B;
        if (key.equals("B")) return byName ? B : A;
        if (key.equals("X")) return byName ? X : Y;
        if (key.equals("Y")) return byName ? Y : X;
        if (key.equals("UP")) return UP;
        if (key.equals("DOWN")) return DOWN;
        if (key.equals("LEFT")) return LEFT;
        if (key.equals("RIGHT")) return RIGHT;
        if (key.equals("L")) return L;
        if (key.equals("R")) return R;
        if (key.equals("L2")) return L2;
        if (key.equals("R2")) return R2;
        if (key.equals("SELECT")) return SELECT;
        if (key.equals("START")) return START;
        if (key.equals("L3")) return L3;
        if (key.equals("R3")) return R3;
        return -1;
    }

    /**
     * Nintendo 64 (mupen64plus-next) : A et B gardent leur nom, Z sur L2, les quatre boutons C sur
     * X (gauche), Y (haut), R2 (droite) et Select (bas), en plus du joystick droit.
     */
    private static int n64(String key) {
        if (key.equals("A")) return B;
        if (key.equals("B")) return Y;
        if (key.equals("X")) return L;
        if (key.equals("Y")) return X;
        if (key.equals("R2")) return R;
        if (key.equals("SELECT")) return A;
        if (key.equals("L")) return SELECT;
        if (key.equals("R")) return R2;
        if (key.equals("L2")) return L2;
        if (key.equals("START")) return START;
        if (key.equals("UP")) return UP;
        if (key.equals("DOWN")) return DOWN;
        if (key.equals("LEFT")) return LEFT;
        if (key.equals("RIGHT")) return RIGHT;
        return -1;
    }

    static native boolean nativeOpen(String corePath, String systemDir, String saveDir);

    static native String[] nativeSystemInfo();

    static native void nativeSetVariable(String key, String value);

    static native boolean nativeLoadGame(String path, byte[] data);

    static native double[] nativeAvInfo();

    static native void nativeSetFrameBuffer(ByteBuffer buffer);

    static native int nativeRun();

    static native int nativeFrameWidth();

    static native int nativeFrameHeight();

    static native float nativeAspect();

    static native int nativeDrainAudio(short[] out);

    static native void nativeSetButtons(int mask);

    static native void nativeSetAnalog(int stick, int x, int y);

    /** Joueurs 2 à 4 (ports 1 à 3) : manettes physiques supplémentaires. */
    static native void nativeSetPortButtons(int port, int mask);

    static native void nativeSetPortAnalog(int port, int stick, int x, int y);

    static native void nativeSetPointer(int x, int y, boolean down);

    /** Accéléré : répondu aux cœurs qui demandent GET_FASTFORWARDING (ARMSX2 passe alors en ×2). */
    static native void nativeSetFastForward(boolean on);

    /** Mode débogage : laisse passer les messages DEBUG des cœurs dans logcat. */
    static native void nativeSetDebug(boolean on);

    static native void nativeReset();

    static native byte[] nativeSaveState();

    static native boolean nativeLoadState(byte[] state);

    static native ByteBuffer nativeMemory(int id);

    static native String nativeLastError();

    static native String[] nativeVariables();

    static native boolean nativeCheatReset();

    static native void nativeCheatSet(int index, boolean enabled, String code);

    static native void nativeClose();
}
