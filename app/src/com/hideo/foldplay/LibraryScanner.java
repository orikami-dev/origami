package com.hideo.foldplay;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parcourt un dossier choisi via le sélecteur Android (SAF) et en tire la liste des jeux. */
final class LibraryScanner {
    private static final int MAX_DEPTH = 5;
    private static final int MAX_ENTRIES = 8000;

    private static final String[] COLUMNS = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
    };

    private static final class Dir {
        final String docId;
        final SystemInfo hint;
        final int depth;

        Dir(String docId, SystemInfo hint, int depth) {
            this.docId = docId;
            this.hint = hint;
            this.depth = depth;
        }
    }

    static List<Game> scan(final ContentResolver cr, Uri tree) {
        List<Game> games = new ArrayList<Game>();
        Map<String, String> images = new HashMap<String, String>();
        Deque<Dir> todo = new ArrayDeque<Dir>();
        String rootId = DocumentsContract.getTreeDocumentId(tree);
        todo.push(new Dir(rootId, SystemInfo.fromFolder(lastSegment(rootId)), 0));
        int seen = 0;

        while (!todo.isEmpty() && seen < MAX_ENTRIES) {
            Dir dir = todo.pop();
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dir.docId);
            Cursor c = null;
            try {
                c = cr.query(children, COLUMNS, null, null, null);
                if (c == null) continue;
                while (c.moveToNext() && seen < MAX_ENTRIES) {
                    seen++;
                    String id = c.getString(0);
                    String name = c.getString(1);
                    String mime = c.getString(2);
                    if (name == null || name.startsWith(".")) continue;
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        // Jeu PS3 en dossier : le dossier parent de PS3_GAME est le jeu lui-même.
                        if (name.equalsIgnoreCase("PS3_GAME")) {
                            String parentName = lastSegment(dir.docId);
                            int sl = parentName.lastIndexOf('/');
                            if (sl >= 0) parentName = parentName.substring(sl + 1);
                            // Nom courant des dossiers PS3 : « BLUS30443-[Demon's Souls] ».
                            java.util.regex.Matcher mm = java.util.regex.Pattern
                                    .compile("^[A-Z]{4}\\d{5}\\s*-?\\s*\\[(.+)\\]$").matcher(parentName);
                            if (mm.matches()) parentName = mm.group(1);
                            Game game = new Game(parentName + ".ps3", SystemInfo.byId("ps3"),
                                    DocumentsContract.buildDocumentUriUsingTree(tree, dir.docId).toString(),
                                    realPath(dir.docId), DocumentsContract.Document.MIME_TYPE_DIR);
                            games.add(game);
                            continue;
                        }
                        if (dir.depth < MAX_DEPTH) {
                            // Un dossier « ps2/ » vaut pour tous ses sous-dossiers.
                            SystemInfo h = SystemInfo.fromFolder(name);
                            todo.push(new Dir(id, h != null ? h : dir.hint, dir.depth + 1));
                        }
                        continue;
                    }
                    String uri = DocumentsContract.buildDocumentUriUsingTree(tree, id).toString();
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                            || lower.endsWith(".webp")) {
                        int dot = lower.lastIndexOf('.');
                        images.put(lower.substring(0, dot), uri);
                        continue;
                    }
                    final Uri docUri = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    SystemInfo sys = SystemInfo.detect(name, dir.hint, new DiscProbe.Source() {
                        @Override
                        public android.os.ParcelFileDescriptor open() throws java.io.IOException {
                            return cr.openFileDescriptor(docUri, "r");
                        }
                    });
                    if (sys == null) continue;
                    // Jeu Xbox 360 extrait : « default.xex » prend le nom de son dossier.
                    String shown = name;
                    if (lower.equals("default.xex")) {
                        String parent = lastSegment(dir.docId);
                        int sl = parent.lastIndexOf('/');
                        shown = (sl >= 0 ? parent.substring(sl + 1) : parent) + ".xex";
                    }
                    Game game = new Game(shown, sys, uri, realPath(id), mime);
                    if (!c.isNull(3)) game.size = c.getLong(3);
                    games.add(game);
                }
            } catch (Exception ignored) {
                // Dossier illisible : on passe au suivant.
            } finally {
                if (c != null) c.close();
            }
        }

        for (Game g : games) g.coverUri = images.get(g.baseName);
        Collections.sort(games, new Comparator<Game>() {
            @Override
            public int compare(Game a, Game b) {
                // Rangés par console (dans l'ordre de SystemInfo.ALL), puis par titre.
                int d = SystemInfo.order(a.system) - SystemInfo.order(b.system);
                return d != 0 ? d : a.title.compareToIgnoreCase(b.title);
            }
        });
        return games;
    }

    /**
     * Les documents du stockage externe ont un id "primary:Roms/snes/x.sfc" ou "1A2B-3C4D:Roms/x.gba".
     * RetroArch a besoin d'un vrai chemin : on le reconstruit quand le format est reconnu.
     */
    static String realPath(String docId) {
        int colon = docId.indexOf(':');
        if (colon <= 0) return null;
        String volume = docId.substring(0, colon);
        String rel = docId.substring(colon + 1);
        if (volume.equals("primary")) return "/storage/emulated/0/" + rel;
        if (volume.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) return "/storage/" + volume + "/" + rel;
        return null;
    }

    private static String lastSegment(String docId) {
        int slash = Math.max(docId.lastIndexOf('/'), docId.lastIndexOf(':'));
        return slash >= 0 ? docId.substring(slash + 1) : docId;
    }
}
