package com.hideo.foldplay;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Locale;

/**
 * Identifiant de titre d'un jeu Xbox 360 (0.62), pour trouver sa jaquette : la base libretro n'en
 * a presque pas pour cette console. Lu dans l'en-tête de default.xex (image .iso XDVDFS, dossier
 * de jeu ou .xex seul), ou dans l'en-tête d'un conteneur STFS (« Games on Demand », LIVE, CON),
 * qui porte aussi une vignette PNG utilisée hors connexion.
 */
final class X360Icon {
    private X360Icon() {
    }

    private static final int SECTOR = 2048;
    /** Partitions de jeu (XGD3, XGD2, XGD1, image « xiso ») : volume XDVDFS à +0x10000. */
    private static final long[] XGD = {0x2080000L, 0xFD90000L, 0x18300000L, 0L};

    /** Ce qu'on a pu lire : identifiant de titre (8 chiffres hexadécimaux) et vignette éventuelle. */
    static final class Info {
        String titleId;
        byte[] thumb;
    }

    static Info read(ContentResolver cr, Game g) {
        try {
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(g.mime)) {
                Uri xex = child(cr, Uri.parse(g.uri), "default.xex");
                return xex == null ? null : fromFile(cr, xex);
            }
            return fromFile(cr, Uri.parse(g.uri));
        } catch (Exception e) {
            return null;
        }
    }

    /** Image de la jaquette du catalogue Xbox pour ce titre (grand format). */
    static String[] boxartUrls(String titleId) {
        String base = "http://download.xbox.com/content/images/66acd000-77fe-1000-9115-d802" + titleId + "/1033/";
        return new String[] {base + "boxartlg.jpg", base + "boxartsm.jpg"};
    }

    private static Info fromFile(ContentResolver cr, Uri uri) throws Exception {
        ParcelFileDescriptor pfd = cr.openFileDescriptor(uri, "r");
        if (pfd == null) return null;
        FileInputStream fin = new FileInputStream(pfd.getFileDescriptor());
        FileChannel ch = fin.getChannel();
        try {
            ByteBuffer head = read(ch, 0, 4, ByteOrder.BIG_ENDIAN);
            String magic = head.remaining() == 4 ? new String(new byte[] {head.get(0), head.get(1), head.get(2), head.get(3)}, "ISO-8859-1") : "";
            if (magic.equals("XEX2")) return xex(ch, 0);
            if (magic.equals("LIVE") || magic.equals("PIRS") || magic.equals("CON ")) return stfs(ch);
            for (long base : XGD) {
                ByteBuffer v = read(ch, base + 0x10000L, 28, ByteOrder.LITTLE_ENDIAN);
                if (v.remaining() < 28) continue;
                byte[] m = new byte[20];
                v.get(m);
                if (!new String(m, "ISO-8859-1").equals("MICROSOFT*XBOX*MEDIA")) continue;
                long root = v.getInt(20) & 0xFFFFFFFFL, size = v.getInt(24) & 0xFFFFFFFFL;
                long[] f = find(ch, base, root, size, "default.xex");
                return f == null ? null : xex(ch, base + f[0] * SECTOR);
            }
            return null;
        } finally {
            fin.close();
            pfd.close();
        }
    }

    /** En-tête XEX2 : l'en-tête optionnel 0x00040006 (informations d'exécution) porte le titre à +0xC. */
    private static Info xex(FileChannel ch, long at) throws Exception {
        ByteBuffer h = read(ch, at, 24, ByteOrder.BIG_ENDIAN);
        if (h.remaining() < 24 || h.getInt(0) != 0x58455832) return null;
        int count = h.getInt(20);
        if (count <= 0 || count > 64) return null;
        ByteBuffer opt = read(ch, at + 24, count * 8, ByteOrder.BIG_ENDIAN);
        for (int i = 0; i + 8 <= opt.limit(); i += 8) {
            if (opt.getInt(i) != 0x00040006) continue;
            ByteBuffer exec = read(ch, at + (opt.getInt(i + 4) & 0xFFFFFFFFL), 16, ByteOrder.BIG_ENDIAN);
            if (exec.remaining() < 16) return null;
            Info out = new Info();
            out.titleId = String.format(Locale.ROOT, "%08X", exec.getInt(12));
            return out.titleId.equals("00000000") ? null : out;
        }
        return null;
    }

    /** Conteneur STFS : titre à 0x360, vignette (taille à 0x1712, image à 0x171A). */
    private static Info stfs(FileChannel ch) throws Exception {
        ByteBuffer b = read(ch, 0x360, 4, ByteOrder.BIG_ENDIAN);
        if (b.remaining() < 4) return null;
        Info out = new Info();
        out.titleId = String.format(Locale.ROOT, "%08X", b.getInt(0));
        ByteBuffer s = read(ch, 0x1712, 4, ByteOrder.BIG_ENDIAN);
        int size = s.remaining() == 4 ? s.getInt(0) : 0;
        if (size > 8 && size <= 0x4000) {
            ByteBuffer img = read(ch, 0x171A, size, ByteOrder.BIG_ENDIAN);
            byte[] png = new byte[img.remaining()];
            img.get(png);
            if (png.length > 8 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G') out.thumb = png;
        }
        return out;
    }

    /**
     * Cherche un fichier dans un répertoire XDVDFS : entrées {gauche, droite (u16), secteur, taille
     * (u32), attributs, longueur du nom, nom}, alignées sur 4 octets, 0xFF en bourrage. Renvoie {secteur, taille}.
     */
    private static long[] find(FileChannel ch, long base, long sector, long size, String name) throws Exception {
        if (size <= 0 || size > (1 << 20)) return null;
        ByteBuffer d = read(ch, base + sector * SECTOR, (int) size, ByteOrder.LITTLE_ENDIAN);
        int pos = 0;
        while (pos + 14 <= d.limit()) {
            if ((d.getShort(pos) & 0xFFFF) == 0xFFFF) {
                pos = (pos / SECTOR + 1) * SECTOR;
                continue;
            }
            int len = d.get(pos + 13) & 0xFF;
            if (len == 0 || pos + 14 + len > d.limit()) break;
            byte[] raw = new byte[len];
            for (int i = 0; i < len; i++) raw[i] = d.get(pos + 14 + i);
            if (new String(raw, "ISO-8859-1").equalsIgnoreCase(name)) {
                return new long[] {d.getInt(pos + 4) & 0xFFFFFFFFL, d.getInt(pos + 8) & 0xFFFFFFFFL};
            }
            pos += (14 + len + 3) & ~3;
        }
        return null;
    }

    private static Uri child(ContentResolver cr, Uri parent, String name) {
        String id = DocumentsContract.getDocumentId(parent);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, id);
        Cursor c = cr.query(children, new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
        if (c == null) return null;
        try {
            while (c.moveToNext()) {
                if (name.equalsIgnoreCase(c.getString(1))) return DocumentsContract.buildDocumentUriUsingTree(parent, c.getString(0));
            }
        } finally {
            c.close();
        }
        return null;
    }

    private static ByteBuffer read(FileChannel ch, long offset, int len, ByteOrder order) throws Exception {
        ByteBuffer b = ByteBuffer.allocate(Math.max(0, len)).order(order);
        while (b.hasRemaining()) {
            if (ch.read(b, offset + b.position()) <= 0) break;
        }
        b.flip();
        return b;
    }
}
