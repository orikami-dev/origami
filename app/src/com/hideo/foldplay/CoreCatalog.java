package com.hideo.foldplay;

/**
 * Fiches des cœurs pris en charge par Origami : nom, rôle, licence et code source.
 * Ce sont les seuls émulateurs que l'app propose ; ils tournent tous à l'intérieur de l'app.
 */
final class CoreCatalog {
    private CoreCatalog() {
    }

    static final class Info {
        final String core, name, about, licence, source;
        /** Vrai si la licence interdit l'usage commercial (à remplacer pour une version payante). */
        final boolean nonCommercial;

        Info(String core, String name, String about, String licence, String source, boolean nonCommercial) {
            this.core = core;
            this.name = name;
            this.about = about;
            this.licence = licence;
            this.source = source;
            this.nonCommercial = nonCommercial;
        }
    }

    static final Info[] ALL = {
        new Info("fceumm", "FCEUmm", "NES et Famicom, très compatible.",
                "GPL-2.0", "https://github.com/libretro/libretro-fceumm", false),
        new Info("snes9x", "Snes9x", "Super Nintendo, précis et rapide.",
                "Licence Snes9x (non commerciale)", "https://github.com/libretro/snes9x", true),
        new Info("gambatte", "Gambatte", "Game Boy et Game Boy Color, précis.",
                "GPL-2.0", "https://github.com/libretro/gambatte-libretro", false),
        new Info("mgba", "mGBA", "Game Boy Advance, précis et léger.",
                "MPL-2.0", "https://github.com/libretro/mgba", false),
        new Info("genesis_plus_gx", "Genesis Plus GX", "Mega Drive, Master System et Game Gear.",
                "Licence non commerciale", "https://github.com/libretro/Genesis-Plus-GX", true),
        new Info("mednafen_pce_fast", "Beetle PCE Fast", "PC Engine et TurboGrafx-16, issu de Mednafen.",
                "GPL-2.0", "https://github.com/libretro/beetle-pce-fast-libretro", false),
        new Info("pcsx_rearmed", "PCSX ReARMed", "PlayStation optimisé ARM, fonctionne sans BIOS.",
                "GPL-2.0", "https://github.com/libretro/pcsx_rearmed", false),
        new Info("swanstation", "SwanStation", "PlayStation HD (OpenGL). BIOS PS1 requis.",
                "GPL-3.0", "https://github.com/libretro/swanstation", false),
        new Info("melonds", "melonDS", "Nintendo DS, deux écrans et tactile.",
                "GPL-3.0", "https://github.com/libretro/melonDS", false),
        new Info("yabause", "Yabause", "Sega Saturn.",
                "GPL-2.0", "https://github.com/libretro/yabause", false),
        new Info("mupen64plus_next_gles3", "Mupen64Plus-Next", "Nintendo 64 en OpenGL ES 3.",
                "GPL-2.0", "https://github.com/libretro/mupen64plus-libretro-nx", false),
        new Info("ppsspp", "PPSSPP", "PlayStation Portable, rendu OpenGL ES.",
                "GPL-2.0+", "https://github.com/hrydgard/ppsspp", false),
        new Info("flycast", "Flycast", "Dreamcast, Naomi et Atomiswave. BIOS facultatif.",
                "GPL-2.0", "https://github.com/flyinghead/flycast", false),
        new Info("fbneo", "FinalBurn Neo", "Arcade : Neo Geo, CPS1 à 3 et plus.",
                "Licence non commerciale", "https://github.com/libretro/FBNeo", true),
        new Info("armsx2", "ARMSX2", "PS2 sur ARM64, fork de PCSX2. BIOS requis.",
                "GPL-3.0", "https://github.com/ARMSX2/ARMSX2", false),
        new Info("dolphin", "Dolphin", "GameCube et Wii.",
                "GPL-2.0+", "https://github.com/libretro/dolphin", false),
    };

    /**
     * Versions d'un cœur : le cœur d'origine, puis d'autres émulateurs libretro pour les mêmes
     * consoles (nom de fichier, nom affiché, différence, licence).
     */
    static final String[][] VARIANTS = {
        {"fceumm", "nestopia", "Nestopia UE", "Très précis, un peu plus gourmand", "GPL-2.0"},
        {"fceumm", "mesen", "Mesen", "Le plus précis pour la NES", "GPL-3.0"},
        {"snes9x", "snes9x2010", "Snes9x 2010", "Ancienne version, plus légère", "Licence Snes9x (non commerciale)"},
        {"gambatte", "sameboy", "SameBoy", "Précision maximale Game Boy", "MIT"},
        {"mgba", "gpsp", "gpSP", "Très léger, moins précis", "GPL-2.0"},
        {"mgba", "vba_next", "VBA Next", "Ancien mais rapide", "GPL-2.0"},
        {"genesis_plus_gx", "picodrive", "PicoDrive", "Rapide, gère aussi le 32X", "Licence non commerciale"},
        {"melonds", "desmume", "DeSmuME", "Autre moteur DS", "GPL-2.0"},
        {"yabause", "yabasanshiro", "YabaSanshiro", "Saturn avec rendu OpenGL ES", "GPL-2.0"},
        {"mupen64plus_next_gles3", "parallel_n64", "ParaLLEl N64", "Autre moteur N64", "GPL-2.0"},
        {"fbneo", "mame2003_plus", "MAME 2003-Plus", "Autre moteur arcade", "Licence MAME (non commerciale)"},
    };

    /** Versions proposées pour un cœur (la première est le cœur d'origine). */
    static java.util.List<String[]> variants(String core) {
        java.util.List<String[]> out = new java.util.ArrayList<String[]>();
        Info i = get(core);
        out.add(new String[] {core, core, name(core), "Version conseillée par Origami", i != null ? i.licence : ""});
        for (String[] v : VARIANTS) if (v[0].equals(core)) out.add(v);
        return out;
    }

    static String variantLabel(String core, String variant) {
        for (String[] v : variants(core)) if (v[1].equals(variant)) return v[2];
        return variant;
    }

    static Info get(String core) {
        for (Info i : ALL) if (i.core.equals(core)) return i;
        return null;
    }

    static String name(String core) {
        Info i = get(core);
        return i != null ? i.name : core;
    }
}
