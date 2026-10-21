package com.hideo.foldplay;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Import de sauvegardes venues d'autres émulateurs, converties au format du cœur intégré de la console :
 * - cartouches (NES à GBA, Mega Drive, PC Engine) : .sav/.srm brut → saves/<jeu>.srm ;
 * - PS1 : carte mémoire (.mcr, .mcd, .mc, DexDrive .gme, PSP .vmp) → saves/<jeu>.srm (carte 1) ;
 * - N64 : .eep, .mpk, .sra, .fla rangés à leur place dans le .srm unique de mupen64plus ;
 * - PS2 : carte mémoire PCSX2/AetherSX2/NetherSX2 (.ps2, .mcd, .mc2) → system/pcsx2/memcards/Mcd001.ps2 ;
 * - PSP : .zip de dossiers SAVEDATA → saves/PSP/SAVEDATA ;
 * - Dreamcast : VMU de 128 Ko → saves/vmu_save_A1.bin.
 * L'ancien fichier remplacé est gardé en .bak.
 */
final class SaveImport {
    private SaveImport() {
    }

    /** Consoles gérées, dans l'ordre du menu. */
    static final String[] SYSTEMS = {"ps2", "psx", "psp", "n64", "gba", "gb", "gbc", "nes", "snes", "md", "sms", "gg",
            "pce", "dc"};

    /** Ce que la console accepte, pour l'afficher sous son nom. */
    static String formats(String system) {
        switch (system) {
            case "ps2": return "Carte PCSX2/AetherSX2 (.ps2, .mcd) · devient la carte 1";
            case "psx": return "Carte mémoire .mcr, .mcd, .mc, .gme (DexDrive), .vmp (PSP)";
            case "psp": return ".zip des dossiers SAVEDATA (PPSSPP)";
            case "n64": return ".eep, .mpk, .sra, .fla (Project64, mupen64plus) ou .srm";
            case "dc": return "VMU de 128 Ko (.bin, .vmu) · devient la VMU A1";
            default: return "Sauvegarde de cartouche .sav ou .srm";
        }
    }

    /** Vrai si la sauvegarde va dans un fichier propre au jeu (il faut donc savoir lequel). */
    static boolean perGame(String system) {
        return !system.equals("ps2") && !system.equals("psp") && !system.equals("dc");
    }

    /**
     * Installe la sauvegarde. gameBase = nom du jeu sans extension (pour les consoles par jeu) ;
     * null = celui du fichier importé. Rend un message pour l'utilisateur.
     */
    static String install(File base, String system, String fileName, byte[] data, String gameBase) throws IOException {
        String lower = fileName.toLowerCase(Locale.ROOT);
        String ext = lower.lastIndexOf('.') > 0 ? lower.substring(lower.lastIndexOf('.') + 1) : "";
        String stem = fileName.lastIndexOf('.') > 0 ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        String game = gameBase != null ? gameBase : stem;
        File saves = new File(base, "saves");
        File system_ = new File(base, "system");
        switch (system) {
            case "ps2": {
                if (!isPs2Card(data.length)) {
                    throw new IOException("ce n'est pas une carte mémoire PS2 au format fichier (8 Mo et plus). "
                            + "Les sauvegardes isolées (.max, .psu) doivent d'abord être copiées dans une carte, "
                            + "par exemple avec mymc sur PC");
                }
                write(new File(system_, "pcsx2/memcards/Mcd001.ps2"), data);
                return "Carte PS2 installée en carte 1. Relance le jeu.";
            }
            case "psx": {
                byte[] card = ps1Card(data, ext);
                write(new File(saves, game + ".srm"), card);
                return "Carte mémoire PS1 installée pour « " + game + " ».";
            }
            case "psp": {
                int n = unzipSavedata(data, new File(saves, "PSP/SAVEDATA"));
                if (n == 0) throw new IOException("aucun dossier de sauvegarde PSP dans ce .zip");
                return n + " sauvegarde(s) PSP installée(s).";
            }
            case "n64": {
                File srm = new File(saves, game + ".srm");
                write(srm, n64(srm, data, ext));
                return "Sauvegarde N64 (" + ext + ") installée pour « " + game + " ».";
            }
            case "dc": {
                if (data.length != 128 * 1024) throw new IOException("une VMU fait 128 Ko");
                write(new File(saves, "vmu_save_A1.bin"), data);
                return "VMU installée en A1. Relance le jeu.";
            }
            default: {
                if (data.length == 0 || data.length > 2 * 1024 * 1024) throw new IOException("taille de sauvegarde inattendue");
                write(new File(saves, game + ".srm"), data);
                return "Sauvegarde installée pour « " + game + " ».";
            }
        }
    }

    /** Carte PS2 au format fichier : 8 Mo de données + ECC (8 650 752 octets), ou ses multiples. */
    static boolean isPs2Card(long len) {
        long unit = 8650752L;
        return len >= unit && len % unit == 0 && len <= unit * 8;
    }

    /** Carte PS1 brute de 128 Ko, en retirant l'en-tête des formats DexDrive et PSP. */
    static byte[] ps1Card(byte[] data, String ext) throws IOException {
        final int size = 128 * 1024;
        int offset = 0;
        if (data.length == size) offset = 0;
        else if (data.length == size + 3904) offset = 3904;  // .gme (DexDrive)
        else if (data.length == size + 0x80) offset = 0x80;   // .vmp (PSP/Vita)
        else throw new IOException("une carte mémoire PS1 fait 128 Ko (fichier de " + data.length + " octets)");
        byte[] out = new byte[size];
        System.arraycopy(data, offset, out, 0, size);
        if (out[0] != 'M' || out[1] != 'C') throw new IOException("ce fichier n'a pas l'en-tête d'une carte PS1");
        return out;
    }

    /** Disposition du .srm de mupen64plus : EEPROM, 4 Controller Paks, SRAM, FlashRAM. */
    private static final int N64_EEP = 0, N64_MPK = 0x800, N64_SRA = 0x20800, N64_FLA = 0x28800, N64_SIZE = 0x48800;

    static byte[] n64(File existing, byte[] data, String ext) throws IOException {
        if (ext.equals("srm")) {
            if (data.length != N64_SIZE) throw new IOException("ce .srm ne vient pas de mupen64plus");
            return data;
        }
        byte[] srm = new byte[N64_SIZE];
        java.util.Arrays.fill(srm, (byte) 0xFF);
        if (existing.isFile() && existing.length() == N64_SIZE) {
            InputStream in = new FileInputStream(existing);
            try {
                int n = 0, r;
                while (n < N64_SIZE && (r = in.read(srm, n, N64_SIZE - n)) > 0) n += r;
            } finally {
                in.close();
            }
        }
        int off, max;
        if (ext.equals("eep")) { off = N64_EEP; max = 0x800; }
        else if (ext.equals("mpk")) { off = N64_MPK; max = 0x20000; }
        else if (ext.equals("sra")) { off = N64_SRA; max = 0x8000; }
        else if (ext.equals("fla")) { off = N64_FLA; max = 0x20000; }
        else throw new IOException("extension N64 inconnue (.eep, .mpk, .sra, .fla)");
        if (data.length > max) throw new IOException("fichier ." + ext + " trop gros");
        System.arraycopy(data, 0, srm, off, data.length);
        return srm;
    }

    /** Extrait les dossiers de sauvegarde PSP (ULUS…/ULES…) où qu'ils soient dans le .zip. */
    static int unzipSavedata(byte[] data, File dest) throws IOException {
        java.util.Set<String> folders = new java.util.HashSet<String>();
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data));
        String root = dest.getCanonicalPath() + File.separator;
        try {
            ZipEntry e;
            byte[] buf = new byte[64 * 1024];
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String[] parts = e.getName().replace('\\', '/').split("/");
                // On garde « <dossier du jeu>/<fichier> », en sautant PSP/SAVEDATA/ s'il est dans le chemin.
                if (parts.length < 2) continue;
                String folder = parts[parts.length - 2];
                if (folder.equalsIgnoreCase("SAVEDATA")) continue;
                File out = new File(new File(dest, folder), parts[parts.length - 1]);
                if (!out.getCanonicalPath().startsWith(root)) continue;
                out.getParentFile().mkdirs();
                OutputStream o = new FileOutputStream(out);
                try {
                    int n;
                    while ((n = zip.read(buf)) > 0) o.write(buf, 0, n);
                } finally {
                    o.close();
                }
                folders.add(folder);
            }
        } finally {
            zip.close();
        }
        return folders.size();
    }

    /** Écrit en gardant l'ancien fichier en .bak. */
    static void write(File f, byte[] data) throws IOException {
        f.getParentFile().mkdirs();
        File tmp = new File(f.getPath() + ".part");
        OutputStream o = new FileOutputStream(tmp);
        try {
            o.write(data);
        } finally {
            o.close();
        }
        if (f.isFile()) {
            File bak = new File(f.getPath() + ".bak");
            bak.delete();
            f.renameTo(bak);
        }
        if (!tmp.renameTo(f)) throw new IOException("écriture impossible : " + f.getName());
    }

    // ------------------------------------------------------------------ export

    /** Un fichier exporté : son nom (au format courant des autres émulateurs) et son contenu. */
    static final class Out {
        final String name;
        final byte[] data;

        Out(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

    /**
     * Les sauvegardes d'une console, converties dans les formats les plus répandus :
     * .ps2 (PCSX2/AetherSX2/NetherSX2), .mcr (PS1), .eep/.mpk/.sra/.fla (N64), .zip SAVEDATA (PSP),
     * .bin (VMU), .sav (cartouches). gameBase non nul = seulement ce jeu.
     */
    static java.util.List<Out> export(File base, String system, Store store, String gameBase) throws IOException {
        java.util.List<Out> out = new java.util.ArrayList<Out>();
        File saves = new File(base, "saves");
        if (system.equals("ps2")) {
            File[] cards = new File(base, "system/pcsx2/memcards").listFiles();
            if (cards != null) for (File f : cards) if (f.getName().endsWith(".ps2")) out.add(new Out(f.getName(), read(f)));
            return out;
        }
        if (system.equals("dc")) {
            File[] vmus = saves.listFiles();
            if (vmus != null) {
                for (File f : vmus) {
                    if (f.getName().startsWith("vmu_save_") && f.getName().endsWith(".bin")) out.add(new Out(f.getName(), read(f)));
                }
            }
            return out;
        }
        if (system.equals("psp")) {
            File dir = new File(saves, "PSP/SAVEDATA");
            if (dir.isDirectory()) out.add(new Out("PSP-SAVEDATA.zip", zipDir(dir, "SAVEDATA")));
            return out;
        }
        File[] all = saves.listFiles();
        if (all == null) return out;
        for (File f : all) {
            String n = f.getName();
            if (!n.endsWith(".srm")) continue;
            String game = n.substring(0, n.length() - 4);
            if (gameBase != null ? !gameBase.equals(game) : !system.equals(store.saveSystem(game))) continue;
            byte[] d = read(f);
            if (system.equals("psx")) {
                out.add(new Out(game + ".mcr", d));
            } else if (system.equals("n64") && d.length == N64_SIZE) {
                part(out, game + ".eep", d, N64_EEP, 0x800);
                for (int k = 0; k < 4; k++) part(out, game + (k == 0 ? "" : "_" + (k + 1)) + ".mpk", d, N64_MPK + k * 0x8000, 0x8000);
                part(out, game + ".sra", d, N64_SRA, 0x8000);
                part(out, game + ".fla", d, N64_FLA, 0x20000);
            } else {
                out.add(new Out(game + ".sav", d));
            }
        }
        return out;
    }

    /** Ajoute un morceau du .srm N64 s'il contient autre chose que du vide (0xFF ou 0). */
    private static void part(java.util.List<Out> out, String name, byte[] d, int off, int len) {
        boolean used = false;
        for (int i = off; i < off + len && !used; i++) used = d[i] != (byte) 0xFF && d[i] != 0;
        if (!used) return;
        // EEPROM de 4 Kbit : 512 octets suffisent si le reste est vide.
        if (name.endsWith(".eep")) {
            boolean small = true;
            for (int i = off + 0x200; i < off + len && small; i++) small = d[i] == (byte) 0xFF || d[i] == 0;
            if (small) len = 0x200;
        }
        byte[] b = new byte[len];
        System.arraycopy(d, off, b, 0, len);
        out.add(new Out(name, b));
    }

    /** Console probable d'un fichier de sauvegarde, ou null s'il faut demander (.sav, .srm…). */
    static String guess(String fileName, byte[] data) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (isPs2Card(data.length)) return "ps2";
        if (n.endsWith(".gme") || n.endsWith(".vmp") || n.endsWith(".mcr")
                || (data.length == 128 * 1024 && data[0] == 'M' && data[1] == 'C')) return "psx";
        if (n.endsWith(".eep") || n.endsWith(".mpk") || n.endsWith(".sra") || n.endsWith(".fla")) return "n64";
        if (n.endsWith(".zip")) return "psp";
        if (n.startsWith("vmu") && data.length == 128 * 1024) return "dc";
        return null;
    }

    private static byte[] read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream((int) Math.max(32, f.length()));
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return o.toByteArray();
        } finally {
            in.close();
        }
    }

    private static byte[] zipDir(File dir, String prefix) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes);
        try {
            addTree(zip, dir, prefix);
        } finally {
            zip.close();
        }
        return bytes.toByteArray();
    }

    private static void addTree(java.util.zip.ZipOutputStream zip, File f, String path) throws IOException {
        File[] kids = f.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            String p = path + "/" + k.getName();
            if (k.isDirectory()) {
                addTree(zip, k, p);
            } else {
                zip.putNextEntry(new ZipEntry(p));
                zip.write(read(k));
                zip.closeEntry();
            }
        }
    }
}
