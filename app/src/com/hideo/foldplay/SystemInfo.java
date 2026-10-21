package com.hideo.foldplay;

import java.util.Locale;

/** Une console reconnue : extensions de fichiers, alias de dossier, cœur RetroArch par défaut. */
final class SystemInfo {
    final String id;
    final String name;
    final String shortName;
    final String core;
    final int color;
    final String[] exts;
    final String[] folderAliases;

    private SystemInfo(String id, String name, String shortName, String core, int color,
                       String[] exts, String[] folderAliases) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.core = core;
        this.color = color;
        this.exts = exts;
        this.folderAliases = folderAliases;
    }

    private static String[] a(String... s) {
        return s;
    }

    /** Extensions qui ne disent pas la console : le dossier ou le contenu du fichier tranche. */
    static final String[] AMBIGUOUS = {"iso", "chd", "cue", "bin", "zip", "7z", "rvz", "m3u", "cso", "img", "pbp"};

    // Ordre chronologique par constructeur ; core = cœur libretro quand il en existe un sur Android.
    static final SystemInfo[] ALL = {
        new SystemInfo("nes", "Nintendo NES", "NES", "fceumm", 0xFFE4572E,
            a("nes", "fds", "unf"), a("nes", "famicom", "fc")),
        new SystemInfo("snes", "Super Nintendo", "SNES", "snes9x", 0xFF7B5EA7,
            a("sfc", "smc"), a("snes", "sfc", "superfamicom", "supernintendo")),
        new SystemInfo("n64", "Nintendo 64", "N64", "mupen64plus_next_gles3", 0xFF2E7D32,
            a("n64", "z64", "v64"), a("n64", "nintendo64")),
        new SystemInfo("gc", "GameCube", "GC", "dolphin", 0xFF6A4C93,
            a("gcm", "gcz", "ciso"), a("gc", "gamecube", "ngc")),
        new SystemInfo("wii", "Wii", "WII", "dolphin", 0xFF8EA4B8,
            a("wbfs", "wad"), a("wii")),
        new SystemInfo("switch", "Nintendo Switch", "NSW", null, 0xFFD62839,
            a("nsp", "xci", "nsz", "xcz"), a("switch", "nsw", "nintendoswitch")),
        new SystemInfo("gb", "Game Boy", "GB", "gambatte", 0xFF8BAC0F,
            a("gb"), a("gb", "gameboy")),
        new SystemInfo("gbc", "Game Boy Color", "GBC", "gambatte", 0xFF2A9D8F,
            a("gbc"), a("gbc", "gameboycolor")),
        new SystemInfo("gba", "Game Boy Advance", "GBA", "mgba", 0xFF4361EE,
            a("gba"), a("gba", "gameboyadvance")),
        new SystemInfo("nds", "Nintendo DS", "NDS", "melonds", 0xFF6C757D,
            a("nds"), a("nds", "ds")),
        new SystemInfo("3ds", "Nintendo 3DS", "3DS", "citra", 0xFFC44536,
            a("3ds", "cci", "cxi", "cia", "3dsx", "z3dsx"), a("3ds", "n3ds", "nintendo3ds")),
        new SystemInfo("psx", "PlayStation", "PS1", "swanstation", 0xFF5A5A66,
            a(), a("psx", "ps1", "playstation", "playstation1")),
        new SystemInfo("ps2", "PlayStation 2", "PS2", null, 0xFF1F4E9C,
            a("ps2"), a("ps2", "playstation2")),
        new SystemInfo("psp", "PSP", "PSP", "ppsspp", 0xFF00496E,
            a(), a("psp")),
        // PS3 : moteur à part (ARMSX3, Ps3Activity), pas un cœur libretro. Images .iso ou dossiers
        // de jeu (avec PS3_GAME), reconnus par LibraryScanner.
        new SystemInfo("ps3", "PlayStation 3", "PS3", null, 0xFF3A3F4B,
            a(), a("ps3", "playstation3")),
        // Xbox 360 : moteur à part (Xenia, X360Activity). Images .iso (XGD), .xex, .zar.
        new SystemInfo("x360", "Xbox 360", "360", null, 0xFF107C10,
            a("xex", "zar"), a("x360", "xbox360", "360")),
        new SystemInfo("vita", "PS Vita", "VITA", null, 0xFF2B59C3,
            a("vpk"), a("vita", "psvita", "psv")),
        new SystemInfo("md", "Mega Drive", "MD", "genesis_plus_gx", 0xFF1D3557,
            a("md", "gen", "smd"), a("md", "megadrive", "genesis")),
        new SystemInfo("sms", "Master System", "SMS", "genesis_plus_gx", 0xFFC1121F,
            a("sms"), a("sms", "mastersystem")),
        new SystemInfo("gg", "Game Gear", "GG", "genesis_plus_gx", 0xFF264653,
            a("gg"), a("gg", "gamegear")),
        new SystemInfo("saturn", "Saturn", "SAT", "yabasanshiro", 0xFF3D405B,
            a(), a("saturn", "segasaturn", "ss")),
        new SystemInfo("dc", "Dreamcast", "DC", "flycast", 0xFFF28C28,
            a("gdi", "cdi"), a("dc", "dreamcast")),
        new SystemInfo("pce", "PC Engine", "PCE", "mednafen_pce_fast", 0xFFF77F00,
            a("pce"), a("pce", "pcengine", "tg16", "turbografx16")),
        // Les jeux d'arcade sont des .zip : seul le dossier (arcade/, fbneo/, neogeo/…) les reconnaît.
        new SystemInfo("arcade", "Arcade", "ARC", "fbneo", 0xFFB5179E,
            a(), a("arcade", "fbneo", "fba", "fbalpha", "mame", "neogeo", "cps1", "cps2", "cps3")),
    };

    // Catégories d'applications du XMB (0.60.5) : hors de ALL, aucun fichier n'y est rangé.
    static final SystemInfo ANDROID = new SystemInfo("android", "Jeux mobiles", "MOBILE", null, 0xFF1E9E8A, a(), a());
    static final SystemInfo REMOTE = new SystemInfo("remote", "Remote Play", "REMOTE", null, 0xFF6A5ACD, a(), a());
    /** Catégorie « Paramètres » du XMB (Tsuru seulement) : Thème, Manette, Réglages. */
    static final SystemInfo SETTINGS = new SystemInfo("settings", "Paramètres", "Paramètres", null, 0xFF5B6475, a(), a());
    /** Entrées d'action (« Actualiser la bibliothèque ») glissées dans une catégorie. */
    static final SystemInfo ACTION = new SystemInfo("action", "Bibliothèque", "MAJ", null, 0xFF3A4150, a(), a());

    /**
     * Consoles « récentes » (PS3 et après) : le profil Performance est leur défaut (décision de BOSS).
     * ATTENTION : toute future console (ps4, xboxone, ps5, xseries, switch2…) doit être ajoutée ici.
     */
    private static final java.util.Set<String> MODERN = new java.util.HashSet<String>(java.util.Arrays.asList(
            "ps3", "x360", "ps4", "xboxone", "ps5", "xseries", "switch", "switch2", "wiiu"));

    static boolean isModern(String systemId) {
        return systemId != null && MODERN.contains(systemId);
    }

    static boolean isModern(SystemInfo s) {
        return s != null && isModern(s.id);
    }

    static int order(SystemInfo s) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i] == s) return i;
        return ALL.length;
    }

    static SystemInfo byId(String id) {
        for (SystemInfo s : ALL) if (s.id.equals(id)) return s;
        if (ANDROID.id.equals(id)) return ANDROID;
        if (REMOTE.id.equals(id)) return REMOTE;
        if (SETTINGS.id.equals(id)) return SETTINGS;
        return null;
    }

    /** Système déduit de l'extension, ou du nom de dossier pour les archives (.zip, .7z). */
    /**
     * Système déduit en trois temps : extension propre à une console, sinon le dossier
     * (« ps2/ », « gamecube/ »… y compris un dossier parent), sinon le contenu du disque.
     * Le dossier l'emporte toujours sur une extension ambiguë (une .iso dans « ps2/ » est PS2).
     */
    static SystemInfo detect(String fileName, SystemInfo folderHint, DiscProbe.Source probe) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) return null;
        String ext = lower.substring(dot + 1);
        boolean ambiguous = false;
        for (String e : AMBIGUOUS) if (e.equals(ext)) ambiguous = true;
        if (!ambiguous) {
            for (SystemInfo s : ALL) {
                for (String e : s.exts) if (e.equals(ext)) return s;
            }
            return null;
        }
        if (folderHint != null) return folderHint;
        if (probe != null && (ext.equals("iso") || ext.equals("rvz") || ext.equals("cso") || ext.equals("img")
                || ext.equals("pbp"))) {
            String id = DiscProbe.identify(probe, ext);
            if (id != null) return byId(id);
        }
        // Sans indice : les images CD les plus courantes sont des jeux PlayStation.
        if (ext.equals("cue") || ext.equals("chd") || ext.equals("m3u")) return byId("psx");
        if (ext.equals("cso") || ext.equals("pbp")) return byId("psp");
        return null;
    }

    static SystemInfo fromFolder(String folderName) {
        if (folderName == null) return null;
        String f = folderName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (SystemInfo s : ALL) {
            for (String alias : s.folderAliases) if (alias.equals(f)) return s;
        }
        return null;
    }
}
