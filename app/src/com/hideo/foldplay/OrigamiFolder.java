package com.hideo.foldplay;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;

import java.io.File;

/**
 * Dossier Origami à la racine du téléphone (0.66.23) : Origami/GAMES pour les jeux (et GAMES/DLC pour les contenus additionnels), Origami/BIOS pour les BIOS et
 * firmwares. Créé dès que l'accès à tous les fichiers est accordé ; l'assistant propose ensuite GAMES comme dossier
 * de jeux (le sélecteur d'Android s'y ouvre directement, il ne reste qu'à valider).
 */
final class OrigamiFolder {
    private OrigamiFolder() {
    }

    static final String[] SUBDIRS = {"GAMES", "GAMES/DLC", "BIOS"};

    static File root() {
        return new File(Environment.getExternalStorageDirectory(), "Origami");
    }

    static File games() {
        return new File(root(), "GAMES");
    }

    static File bios() {
        return new File(root(), "BIOS");
    }

    /** Accès à tous les fichiers (Android 11 et plus) ; avant, la permission de stockage classique suffit. */
    static boolean allFiles() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    /** Ouvre la page d'Android qui accorde l'accès à tous les fichiers pour Origami. */
    static void askAllFiles(android.app.Activity a) {
        if (Build.VERSION.SDK_INT < 30) {
            a.requestPermissions(new String[] {android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 41);
            return;
        }
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (Exception e) {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    /** Crée Origami/GAMES et Origami/BIOS s'ils manquent. Rend vrai si le dossier des jeux existe. */
    static boolean ensure() {
        if (!allFiles()) return false;
        for (String s : SUBDIRS) {
            File d = new File(root(), s);
            if (!d.isDirectory() && !d.mkdirs()) DebugLog.event("[dossier] création impossible : " + d);
        }
        return games().isDirectory();
    }

    /** Sélecteur de dossiers d'Android ouvert directement sur Origami/GAMES. */
    static Intent pickGames() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Build.VERSION.SDK_INT >= 26) {
            i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Origami/GAMES"));
        }
        return i;
    }

    /** Range les BIOS déposés dans Origami/BIOS (les gros fichiers comme le firmware PS3 restent pour Ps3Activity). */
    static BiosImport.Report importBios(Context ctx) {
        BiosImport.Report r = new BiosImport.Report();
        File[] files = bios().listFiles();
        if (files == null) return r;
        File base = ctx.getExternalFilesDir(null);
        File system = new File(base != null ? base : ctx.getFilesDir(), "system");
        for (File f : files) {
            if (!f.isFile() || f.getName().startsWith(".") || f.length() > (16L << 20)) continue;
            try {
                java.io.InputStream in = new java.io.FileInputStream(f);
                try {
                    BiosImport.add(system, f.getName(), in, r);
                } finally {
                    in.close();
                }
            } catch (java.io.IOException e) {
                r.skipped++;
            }
        }
        return r;
    }
}
