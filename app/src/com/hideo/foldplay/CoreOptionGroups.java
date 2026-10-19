package com.hideo.foldplay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Range les options d'un cœur libretro par thème (les cœurs les donnent en vrac) : on lit la clé et
 * l'intitulé et on cherche des mots repères. Les thèmes gardent toujours le même ordre.
 */
final class CoreOptionGroups {
    private CoreOptionGroups() {
    }

    static final String[][] GROUPS = {
        {"Image et rendu", "Résolution, moteur graphique, filtres, format",
                "resolution", "render", "gpu", "upscal", "internal", "scale", "filter", "texture", "aspect",
                "widescreen", "shader", "msaa", "aa", "anisotrop", "efb", "vsync", "palette", "color", "colour",
                "crop", "overscan", "border", "video", "graphics", "gfx", "rdp", "rsp", "screen", "display",
                "interlac", "dither", "bilinear", "mipmap", "wide", "fov", "pgxp", "bloom", "fog", "blend",
                "brightness", "gamma", "ntsc", "lcd", "ghost", "layout", "hd", "fps"},
        {"Performances", "Accélérations, sauts d'images, cadence",
                "frameskip", "frame skip", "skip", "speed", "overclock", "underclock", "clock", "cycle", "thread",
                "async", "fast", "jit", "dynarec", "recompil", "cache", "turbo", "boost", "latency", "lag",
                "run-ahead", "runahead", "cpu", "timing"},
        {"Son", "Volume, qualité, canaux",
                "audio", "sound", "volume", "reverb", "interpol", "sample", "dsp", "spu", "apu", "music", "fm",
                "channel", "stereo", "mono", "resampl", "cdda", "xa"},
        {"Commandes", "Manettes, sticks, pistolets, vibrations",
                "input", "pad", "analog", "joypad", "joystick", "mouse", "gun", "controller", "rumble", "dpad",
                "d-pad", "button", "touch", "pointer", "turbo", "deadzone", "sensitiv", "multitap", "keyboard",
                "stick", "trigger", "port"},
        {"Système et console", "BIOS, région, langue, mémoire",
                "bios", "region", "language", "lang", "memory", "ram", "card", "mcd", "save", "sram", "rtc",
                "clock", "date", "time", "system", "model", "console", "hle", "lle", "boot", "firmware", "disc",
                "cd", "expansion", "pak", "dongle", "dip", "neogeo mode"},
    };

    static final String OTHER = "Autres réglages";

    static String group(String key, String spec) {
        int semi = spec.indexOf(';');
        String label = semi > 0 ? spec.substring(0, semi) : "";
        // L'intitulé passe d'abord : « Internal Resolution » parle mieux que la clé.
        String text = (label + " " + key.replace('_', ' ').replace('-', ' ')).toLowerCase(Locale.ROOT);
        for (String[] g : GROUPS) {
            for (int i = 2; i < g.length; i++) {
                if (matches(text, g[i])) return g[0];
            }
        }
        return OTHER;
    }

    /** Un mot repère court ne compte que s'il est isolé (« aa », « hd », « cd », « fm »). */
    private static boolean matches(String text, String word) {
        if (word.length() > 3) return text.contains(word);
        return (" " + text.replaceAll("[^a-z0-9]+", " ") + " ").contains(" " + word + " ");
    }

    static String detail(String group) {
        for (String[] g : GROUPS) if (g[0].equals(group)) return g[1];
        return "Le reste, tel que le cœur le donne";
    }

    /** Thème → index des options (par pas de 3 dans vars), dans l'ordre fixe des thèmes. */
    static Map<String, List<Integer>> split(String[] vars) {
        Map<String, List<Integer>> out = new LinkedHashMap<String, List<Integer>>();
        for (String[] g : GROUPS) out.put(g[0], new ArrayList<Integer>());
        out.put(OTHER, new ArrayList<Integer>());
        for (int i = 0; i + 2 < vars.length; i += 3) {
            if (vars[i] == null || vars[i].isEmpty()) continue;
            out.get(group(vars[i], vars[i + 1] == null ? "" : vars[i + 1])).add(i / 3);
        }
        java.util.Iterator<Map.Entry<String, List<Integer>>> it = out.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue().isEmpty()) it.remove();
        return out;
    }

    /** Intitulé lisible d'une option. */
    static String label(String key, String spec) {
        int semi = spec.indexOf(';');
        return semi > 0 ? spec.substring(0, semi).trim() : key;
    }
}
