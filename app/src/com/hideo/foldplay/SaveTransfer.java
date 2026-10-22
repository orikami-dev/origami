package com.hideo.foldplay;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Boutons Importer / Exporter des sauvegardes, communs aux Réglages, au menu Maison en jeu et au
 * partage reçu d'une autre app (ImportActivity). Tout passe par les menus codec.
 */
final class SaveTransfer {
    private SaveTransfer() {
    }

    static final int REQ_EXPORT_TREE = 31;

    /** Fichiers prêts à partir vers le dossier choisi (entre le sélecteur et son résultat). */
    private static List<SaveImport.Out> pending;

    static File base(Activity a) {
        File b = a.getExternalFilesDir(null);
        return b != null ? b : a.getFilesDir();
    }

    /** Menu d'export : partager avec une app, ou enregistrer dans un dossier. gameBase = un seul jeu. */
    static void export(final Activity a, final String system, String gameBase, final Runnable back) {
        final List<SaveImport.Out> outs;
        try {
            outs = SaveImport.export(base(a), system, new Store(a), gameBase);
        } catch (IOException e) {
            toast(a, "Export impossible : " + e.getMessage());
            return;
        }
        SystemInfo si = SystemInfo.byId(system);
        String sys = si != null ? si.shortName : system;
        CodecMenu m = new CodecMenu(a).title("Exporter", "SAUVEGARDES · " + sys);
        if (back != null) m.onBack(back);
        if (outs.isEmpty()) {
            m.message("Aucune sauvegarde " + sys + " pour l'instant. Une sauvegarde est rattachée à sa console "
                    + "dès que le jeu a été lancé une fois dans Origami.");
            m.item("OK", null, back);
            m.show(CodecMenu.hostFor(a));
            return;
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < outs.size() && i < 6; i++) names.append(i > 0 ? ", " : "").append(outs.get(i).name);
        if (outs.size() > 6) names.append("… (").append(outs.size()).append(" fichiers)");
        m.message(names.toString());
        m.item("Partager", "Vers une autre app, Quick Share, Drive, un PC…", new Runnable() {
            public void run() { share(a, outs); }
        });
        m.item("Enregistrer dans un dossier", "Dossier du téléphone, carte SD, clé USB", new Runnable() {
            public void run() {
                pending = outs;
                try {
                    a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_EXPORT_TREE);
                } catch (ActivityNotFoundException e) {
                    toast(a, "Sélecteur de dossier introuvable.");
                }
            }
        });
        m.show(CodecMenu.hostFor(a));
    }

    private static void share(Activity a, List<SaveImport.Out> outs) {
        share(a, outs, "Envoyer les sauvegardes");
    }

    static void share(Activity a, List<SaveImport.Out> outs, String title) {
        share(a, outs, title, "application/octet-stream");
    }

    static void share(Activity a, List<SaveImport.Out> outs, String title, String mime) {
        share(a, outs, title, mime, null, null);
    }

    /** Comme share, mais vers une boîte mail : destinataire et objet déjà remplis, fichier en pièce jointe (messagerie par défaut). */
    static void share(Activity a, List<SaveImport.Out> outs, String title, String mime, String to, String subject) {
        File dir = SaveProvider.dir(a);
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();
        dir.mkdirs();
        ArrayList<Uri> uris = new ArrayList<Uri>();
        try {
            for (SaveImport.Out o : outs) {
                OutputStream out = new FileOutputStream(new File(dir, o.name));
                try {
                    out.write(o.data);
                } finally {
                    out.close();
                }
                uris.add(SaveProvider.uri(o.name));
            }
        } catch (IOException e) {
            toast(a, "Préparation impossible : " + e.getMessage());
            return;
        }
        Intent send;
        if (uris.size() == 1) {
            send = new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            send = new Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        send.setType(mime);
        if (to != null && !to.isEmpty()) {
            send.putExtra(Intent.EXTRA_EMAIL, new String[] {to});
            if (subject != null) send.putExtra(Intent.EXTRA_SUBJECT, subject);
        }
        // La permission suit le ClipData jusqu'à l'app choisie.
        android.content.ClipData clip = android.content.ClipData.newRawUri(outs.get(0).name, uris.get(0));
        for (int i = 1; i < uris.size(); i++) clip.addItem(new android.content.ClipData.Item(uris.get(i)));
        send.setClipData(clip);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (to != null && !to.isEmpty()) {
            // Messagerie par défaut de l'utilisateur (Gmail, Outlook, Samsung Mail…) : le sélecteur mailto: ne garde que les apps de mail, la pièce jointe suit.
            send.setSelector(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")));
            try {
                a.startActivity(send);
                return;
            } catch (ActivityNotFoundException e) {
                send.setSelector(null);
            }
        }
        try {
            a.startActivity(Intent.createChooser(send, title));
        } catch (ActivityNotFoundException e) {
            toast(a, "Aucune app pour recevoir ces fichiers.");
        }
    }

    /** À appeler depuis onActivityResult : écrit les fichiers en attente dans le dossier choisi. */
    static boolean onResult(Activity a, int request, int result, Intent data) {
        if (request != REQ_EXPORT_TREE) return false;
        List<SaveImport.Out> outs = pending;
        pending = null;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null || outs == null) return true;
        Uri tree = data.getData();
        Uri parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,
                android.provider.DocumentsContract.getTreeDocumentId(tree));
        int ok = 0;
        for (SaveImport.Out o : outs) {
            try {
                Uri doc = android.provider.DocumentsContract.createDocument(a.getContentResolver(), parent,
                        "application/octet-stream", o.name);
                if (doc == null) continue;
                OutputStream out = a.getContentResolver().openOutputStream(doc);
                try {
                    out.write(o.data);
                } finally {
                    out.close();
                }
                ok++;
            } catch (Exception e) {
                // On continue avec les autres fichiers.
            }
        }
        toast(a, ok + " fichier(s) exporté(s) sur " + outs.size() + ".");
        return true;
    }

    /** Lit des documents reçus (sélecteur ou partage) : noms et contenus. */
    static List<SaveImport.Out> read(Activity a, List<Uri> uris, StringBuilder errors) {
        List<SaveImport.Out> in = new ArrayList<SaveImport.Out>();
        for (Uri u : uris) {
            String name = SettingsActivity.displayName(a, u);
            try {
                InputStream s = a.getContentResolver().openInputStream(u);
                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
                try {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = s.read(buf)) > 0) o.write(buf, 0, n);
                } finally {
                    s.close();
                }
                in.add(new SaveImport.Out(name, o.toByteArray()));
            } catch (Exception e) {
                errors.append(name).append(" : illisible\n");
            }
        }
        return in;
    }

    /** Installe des fichiers pour une console et montre le bilan dans un menu codec. */
    static void install(Activity a, String system, List<SaveImport.Out> files, StringBuilder msg, Runnable done) {
        Store store = new Store(a);
        for (SaveImport.Out f : files) {
            try {
                msg.append(SaveImport.install(base(a), system, f.name, f.data, null));
                if (SaveImport.perGame(system)) {
                    String stem = f.name.lastIndexOf('.') > 0 ? f.name.substring(0, f.name.lastIndexOf('.')) : f.name;
                    store.setSaveSystem(stem, system);
                }
            } catch (IOException e) {
                msg.append(f.name).append(" : ").append(e.getMessage());
            }
            msg.append('\n');
        }
        new CodecMenu(a).title("Import des sauvegardes", null).message(msg.toString().trim())
                .item("OK", null, done).show(CodecMenu.hostFor(a));
    }

    /** Choix de la console pour des fichiers reçus ; celle devinée d'après le format est en tête. */
    static void chooseSystem(final Activity a, final List<SaveImport.Out> files, final StringBuilder errors,
                             final Runnable done) {
        String guess = files.isEmpty() ? null : SaveImport.guess(files.get(0).name, files.get(0).data);
        CodecMenu m = new CodecMenu(a).title("Importer", files.size() + " FICHIER(S) · QUELLE CONSOLE ?");
        if (done != null) m.onBack(done);
        List<String> order = new ArrayList<String>();
        if (guess != null) order.add(guess);
        for (String s : SaveImport.SYSTEMS) if (!order.contains(s)) order.add(s);
        for (final String id : order) {
            SystemInfo si = SystemInfo.byId(id);
            String label = (si != null ? si.name : id) + (id.equals(guess) ? "  · détecté" : "");
            m.item(label, SaveImport.formats(id), new Runnable() {
                public void run() { install(a, id, files, errors, done); }
            });
        }
        m.show(CodecMenu.hostFor(a));
    }

    static void toast(Activity a, String s) {
        I18n.toast(a, s, Toast.LENGTH_LONG).show();
    }
}
