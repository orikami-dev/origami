package com.hideo.foldplay;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Import des BIOS (0.60.3) : chaque fichier est reconnu par son nom et sa taille, puis rangé là où
 * son émulateur le cherche dans system/. Les .zip sont ouverts (sauf neogeo.zip, que FBNeo lit tel
 * quel). Origami ne fournit aucun BIOS : ce sont ceux que l'utilisateur tire de ses consoles.
 */
final class BiosImport {
    private BiosImport() {
    }

    /** {nom en minuscules, sous-dossier de system/ ("" = system/), console} */
    private static final String[][] NAMED = {
        {"saturn_bios.bin", "", "Saturn"},
        {"sega_101.bin", "", "Saturn"},
        {"mpr-17933.bin", "", "Saturn"},
        {"dc_boot.bin", "dc", "Dreamcast"},
        {"dc_flash.bin", "dc", "Dreamcast"},
        {"gba_bios.bin", "", "Game Boy Advance"},
        {"gb_bios.bin", "", "Game Boy"},
        {"gbc_bios.bin", "", "Game Boy Color"},
        {"bios7.bin", "", "Nintendo DS"},
        {"bios9.bin", "", "Nintendo DS"},
        {"firmware.bin", "", "Nintendo DS"},
        {"bios_cd_e.bin", "", "Mega-CD"},
        {"bios_cd_u.bin", "", "Mega-CD"},
        {"bios_cd_j.bin", "", "Mega-CD"},
        {"syscard3.pce", "", "PC Engine CD"},
        {"syscard2.pce", "", "PC Engine CD"},
        {"syscard1.pce", "", "PC Engine CD"},
        {"gexpress.pce", "", "PC Engine CD"},
        {"disksys.rom", "", "Famicom Disk System"},
        {"neogeo.zip", "fbneo", "Neo Geo"},
    };

    /** Où ranger ce fichier : {sous-dossier, nom, console}, ou null s'il n'est pas reconnu. */
    static String[] route(String name, long len) {
        String low = name.toLowerCase(Locale.ROOT);
        // Les noms connus d'abord : un BIOS Saturn fait 512 Ko comme celui de la PS1.
        for (String[] e : NAMED) {
            if (low.equals(e[0]) && len > 0 && len <= 16L << 20) {
                // Les noms de fichiers du Mega-CD sont attendus en majuscules (bios_CD_E.bin).
                String out = low.startsWith("bios_cd_") ? "bios_CD_" + low.substring(8, 9).toUpperCase(Locale.ROOT) + ".bin" : low;
                return new String[] {e[1], out, e[2]};
            }
        }
        if (len == 4L << 20 || len == 8L << 20) {
            return new String[] {"pcsx2/bios", low.endsWith(".bin") ? name : name + ".bin", "PlayStation 2"};
        }
        if (len == 512 * 1024) return new String[] {"", name, "PlayStation"};
        return null;
    }

    /** Résultat d'un import : consoles servies, fichiers ignorés. */
    static final class Report {
        final java.util.Map<String, Integer> done = new java.util.LinkedHashMap<String, Integer>();
        int skipped;

        String text() {
            StringBuilder b = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> e : done.entrySet()) {
                if (b.length() > 0) b.append(", ");
                b.append(e.getKey());
                if (e.getValue() > 1) b.append(" ×").append(e.getValue());
            }
            if (b.length() > 0) b.insert(0, "BIOS importé(s) : ");
            if (skipped > 0) b.append(b.length() > 0 ? " · " : "").append(skipped).append(" fichier(s) non reconnu(s)");
            return b.toString();
        }
    }

    /** Importe un fichier (ou chaque fichier d'un .zip) lu dans in. */
    static void add(File system, String name, InputStream in, Report r) throws IOException {
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip") && !name.equalsIgnoreCase("neogeo.zip")) {
            ZipInputStream zip = new ZipInputStream(in);
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String inner = new File(e.getName()).getName();
                if (inner.isEmpty() || inner.startsWith(".")) continue;
                add(system, inner, zip, r);
            }
            return;
        }
        system.mkdirs();
        File tmp = new File(system, name + ".part");
        OutputStream out = new FileOutputStream(tmp);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                // Rien d'aussi gros n'est un BIOS : on arrête de copier.
                if (total > 16L << 20) break;
                out.write(buf, 0, n);
            }
        } finally {
            out.close();
        }
        String[] to = route(name, tmp.length());
        if (to == null) {
            tmp.delete();
            r.skipped++;
            return;
        }
        File dir = to[0].isEmpty() ? system : new File(system, to[0]);
        dir.mkdirs();
        File dest = new File(dir, to[1]);
        dest.delete();
        if (!tmp.renameTo(dest)) {
            tmp.delete();
            r.skipped++;
            return;
        }
        Integer k = r.done.get(to[2]);
        r.done.put(to[2], k == null ? 1 : k + 1);
    }
}
