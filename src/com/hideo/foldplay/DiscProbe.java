package com.hideo.foldplay;

import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;

/**
 * Lit quelques octets d'une image disque pour savoir de quelle console elle vient,
 * sans jamais la charger en entier :
 * - GameCube / Wii : nombre magique de l'en-tête Nintendo (ou type de disque d'un .rvz) ;
 * - ISO 9660 : identifiant système « PSP GAME » ou « PLAYSTATION », puis SYSTEM.CNF
 *   (« BOOT2 » = PS2, « BOOT » = PS1).
 */
final class DiscProbe {
    interface Source {
        ParcelFileDescriptor open() throws IOException;
    }

    private static final int SECTOR = 2048;
    private static final int GC_MAGIC = 0xC2339F3D;
    private static final int WII_MAGIC = 0x5D1C9EA3;

    private DiscProbe() {
    }

    /** Identifiant de console (« ps2 », « psp », « gc »…) ou null si on ne sait pas. */
    static String identify(Source source, String ext) {
        ParcelFileDescriptor pfd = null;
        FileInputStream in = null;
        try {
            pfd = source.open();
            if (pfd == null) return null;
            in = new FileInputStream(pfd.getFileDescriptor());
            return identify(in.getChannel(), ext);
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (in != null) in.close();
                if (pfd != null) pfd.close();
            } catch (IOException ignored) {
                // Rien à faire.
            }
        }
    }

    static String identify(FileChannel ch, String ext) {
        try {
            if (ext.equals("rvz")) return rvz(ch);
            if (ext.equals("cso")) return "psp";
            if (ext.equals("pbp")) return pbp(ch);

            ByteBuffer head = read(ch, 0, 0x20);
            if (head != null) {
                head.order(ByteOrder.BIG_ENDIAN);
                if (head.getInt(0x1C) == GC_MAGIC) return "gc";
                if (head.getInt(0x18) == WII_MAGIC) return "wii";
            }

            // Disque PS3 : le secteur 1 commence par « PlayStation3 ».
            ByteBuffer s1 = read(ch, SECTOR, 16);
            if (s1 != null && ascii(s1, 0, 12).equals("PlayStation3")) return "ps3";

            if (xbox(ch)) return "x360";

            ByteBuffer pvd = read(ch, 16L * SECTOR, SECTOR);
            if (pvd == null || !ascii(pvd, 1, 5).equals("CD001")) return null;
            String system = ascii(pvd, 8, 32).trim();
            if (system.startsWith("PS3")) return "ps3";
            if (system.startsWith("PSP GAME")) return "psp";
            if (!system.startsWith("PLAYSTATION")) return null;

            String cnf = systemCnf(ch, pvd);
            if (cnf != null) return cnf.contains("BOOT2") ? "ps2" : "psx";
            // Pas de SYSTEM.CNF lisible : un DVD (plus de 700 Mo) est forcément PS2.
            return ch.size() > 700L * 1024 * 1024 ? "ps2" : "psx";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * EBOOT.PBP : jeu PSP, ou jeu PS1 du PlayStation Store. PARAM.SFO tranche : CATEGORY « ME »
     * (classique PS1) donne la PS1, tout le reste la PSP.
     */
    private static String pbp(FileChannel ch) throws IOException {
        ByteBuffer h = read(ch, 0, 0x28);
        if (h == null || !ascii(h, 1, 3).equals("PBP")) return null;
        h.order(ByteOrder.LITTLE_ENDIAN);
        long sfo = h.getInt(0x08) & 0xFFFFFFFFL;
        long next = h.getInt(0x0C) & 0xFFFFFFFFL;
        int len = (int) Math.min(Math.max(0, next - sfo), 64 * 1024);
        ByteBuffer p = len > 0x14 ? read(ch, sfo, len) : null;
        if (p == null || !ascii(p, 1, 3).equals("PSF")) return "psp";
        p.order(ByteOrder.LITTLE_ENDIAN);
        int keys = p.getInt(0x08);
        int data = p.getInt(0x0C);
        int count = p.getInt(0x10);
        for (int i = 0; i < count && 0x14 + i * 16 + 16 <= p.limit(); i++) {
            int e = 0x14 + i * 16;
            int k = keys + (p.getShort(e) & 0xFFFF);
            if (!ascii(p, k, 9).equals("CATEGORY\0")) continue;
            String v = ascii(p, data + p.getInt(e + 12), 2);
            return v.equals("ME") ? "psx" : "psp";
        }
        return "psp";
    }

    /** RVZ/WIA : le type de disque suit l'en-tête de 0x48 octets (1 = GameCube, 2 = Wii). */
    private static String rvz(FileChannel ch) throws IOException {
        ByteBuffer b = read(ch, 0, 0x4C);
        if (b == null) return null;
        String magic = ascii(b, 0, 3);
        if (!magic.equals("RVZ") && !magic.equals("WIA")) return null;
        b.order(ByteOrder.BIG_ENDIAN);
        int type = b.getInt(0x48);
        return type == 1 ? "gc" : type == 2 ? "wii" : null;
    }

    /** Cherche SYSTEM.CNF dans le répertoire racine et renvoie ses premières lignes. */
    private static String systemCnf(FileChannel ch, ByteBuffer pvd) throws IOException {
        pvd.order(ByteOrder.LITTLE_ENDIAN);
        int rootLba = pvd.getInt(156 + 2);
        int rootLen = Math.min(pvd.getInt(156 + 10), 64 * 1024);
        ByteBuffer dir = read(ch, (long) rootLba * SECTOR, rootLen);
        if (dir == null) return null;
        dir.order(ByteOrder.LITTLE_ENDIAN);
        int pos = 0;
        while (pos < dir.limit()) {
            int len = dir.get(pos) & 0xFF;
            if (len == 0) {
                // Les entrées ne chevauchent pas deux secteurs : on saute au suivant.
                pos = (pos / SECTOR + 1) * SECTOR;
                continue;
            }
            if (pos + 33 > dir.limit()) break;
            int nameLen = dir.get(pos + 32) & 0xFF;
            String name = ascii(dir, pos + 33, nameLen);
            if (name.startsWith("SYSTEM.CNF")) {
                int lba = dir.getInt(pos + 2);
                ByteBuffer f = read(ch, (long) lba * SECTOR, 256);
                return f == null ? null : ascii(f, 0, f.limit());
            }
            pos += len;
        }
        return null;
    }

    /** Partitions de jeu Xbox (XGD3, XGD2, XGD1, image « xiso ») : volume XDVDFS à +0x10000. */
    private static final long[] XGD = {0x2080000L, 0xFD90000L, 0x18300000L, 0L};

    private static boolean xbox(FileChannel ch) throws IOException {
        for (long base : XGD) {
            ByteBuffer b = read(ch, base + 0x10000L, 20);
            if (b != null && ascii(b, 0, 20).equals("MICROSOFT*XBOX*MEDIA")) return true;
        }
        return false;
    }

    private static ByteBuffer read(FileChannel ch, long offset, int len) throws IOException {
        if (offset + len > ch.size()) return null;
        ByteBuffer b = ByteBuffer.allocate(len);
        ch.position(offset);
        while (b.hasRemaining()) {
            if (ch.read(b) < 0) break;
        }
        b.flip();
        return b.limit() == len ? b : null;
    }

    private static String ascii(ByteBuffer b, int from, int len) {
        if (from + len > b.limit()) return "";
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = b.get(from + i);
        return new String(out, StandardCharsets.US_ASCII);
    }
}
