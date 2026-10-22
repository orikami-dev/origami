package com.hideo.foldplay;

/**
 * Profil de test (Réglages > Débogage) : toutes les pistes d'optimisation en cours, avec les valeurs que
 * KAMI-WORKS juge les meilleures, ajustées après chaque essai de BOSS. Jamais proposé aux utilisateurs.
 *
 * Essai 1 (0.66.30) : Turnip, tampon son 120 ms avec étirement du temps, 5 fils de compilation de shaders,
 * son / rendu / compilation sur les 2 gros cœurs, SPU et PPU sur les 6 cœurs moyens.
 */
final class Lab {
    static final int AUDIO_MS = 120;
    static final boolean STRETCH = true;
    static final int COMPILER_THREADS = 5;

    private Lab() {
    }

    /** Fils légers mais sensibles au retard : au son, au rendu et à la compilation des shaders. */
    static boolean onPrime(String name) {
        String s = name.toLowerCase(java.util.Locale.ROOT);
        return s.contains("audio") || s.startsWith("rsx") || s.contains("rsx::") || s.contains("vulkan")
                || s.contains("renderthread") || CoreAffinity.compiler(name);
    }
}
