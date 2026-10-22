package com.hideo.foldplay;

/**
 * Profil de test (Réglages > Débogage) : toutes les pistes d'optimisation en cours, avec les valeurs que
 * KAMI-WORKS juge les meilleures, ajustées après chaque essai de BOSS. Jamais proposé aux utilisateurs.
 *
 * Essai 1 (0.66.30) : Turnip, tampon son 120 ms avec étirement du temps, 5 fils de compilation de shaders,
 * son / rendu / compilation sur les 2 gros cœurs, SPU et PPU sur les 6 cœurs moyens.
 *
 * Essai 2 (0.66.32) : plus de retard du son mais il saccade avec les pics d'image (BOSS, essai 1) : tampon 150 ms
 * et étirement du temps déclenché plus tôt (seuil 90 % au lieu de 75 %).
 *
 * Essai 2 bis : compilation des shaders lente sur MGS4 (BOSS) : les fils de compilation (RSX.W, ps3-compile) ne sont plus
 * épinglés sur les 2 gros cœurs mais libres sur les 8, avec 6 fils au lieu de 5.
 */
final class Lab {
    static final int AUDIO_MS = 150;
    static final int STRETCH_THRESHOLD = 90;
    static final boolean STRETCH = true;
    static final int COMPILER_THREADS = 6;
    static final String SLEEP_TIMERS = "All Timers";

    private Lab() {
    }

    /** Fils légers mais sensibles au retard : le son et le rendu. Les fils de shaders restent libres (tous les cœurs), sinon la compilation se limite à 2 cœurs. */
    static boolean onPrime(String name) {
        String s = name.toLowerCase(java.util.Locale.ROOT);
        if (s.startsWith("rsx.w") || s.contains("compil")) return false;
        return s.contains("audio") || s.startsWith("rsx") || s.contains("rsx::") || s.contains("vulkan")
                || s.contains("renderthread");
    }
}
