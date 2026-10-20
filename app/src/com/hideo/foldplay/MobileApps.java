package com.hideo.foldplay;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Catégories d'applications du XMB (0.60.5) : les jeux Android installés (« Jeux mobiles ») et les
 * clients de jeu à distance (« Remote Play »). On lance les applications officielles telles
 * qu'elles sont : rien n'est embarqué ni modifié, aucun logo n'est repris (icône de l'app installée).
 */
final class MobileApps {
    private MobileApps() {
    }

    /** Plateformes de Remote Play (0.62) : {id, nom, client libre prévu dans Origami}. */
    static final String[][] GROUPS = {
        {"ps", "PlayStation", "chiaki-ng"},
        {"xbox", "XBOX", "un client xCloud libre"},
        {"steam", "Steam", "Moonlight"},
    };

    /** Solutions officielles : {plateforme, paquet, nom, texte de la fiche}. */
    static final String[][] OFFICIAL = {
        {"ps", "com.playstation.remoteplay", "PS Remote Play",
            "Joue à distance aux jeux de ta PS5 ou de ta PS4, chez toi ou en déplacement."},
        {"xbox", "com.microsoft.xboxone.smartglass", "Xbox",
            "Joue à distance sur ta Xbox, ou dans le cloud avec le Game Pass."},
        {"xbox", "com.gamepass", "Xbox Game Pass", "Jeux du Game Pass en streaming depuis le cloud."},
        {"steam", "com.valvesoftware.steamlink", "Steam Link", "Joue aux jeux de ton PC Steam sur le téléphone."},
    };

    /** Alternatives reconnues sur le téléphone : {plateforme, paquet ou mot du nom, texte}. */
    static final String[][] ALTERNATIVES = {
        {"ps", "chiaki", "Client libre de Remote Play PlayStation."},
        {"ps", "pxplay", "Client de Remote Play PlayStation."},
        {"ps", "psplay", "Client de Remote Play PlayStation."},
        {"xbox", "xbplay", "Client de Remote Play Xbox."},
        {"xbox", "greenlight", "Client libre de Remote Play Xbox et xCloud."},
        {"xbox", "xcloud", "Client du cloud Xbox."},
        {"steam", "com.limelight", "Moonlight : client libre de streaming depuis ton PC (Sunshine)."},
        {"steam", "moonlight", "Client libre de streaming depuis ton PC (Sunshine)."},
        {"steam", "artemis", "Client libre de streaming depuis ton PC, dérivé de Moonlight."},
        {"steam", "com.parsec.app", "Bureau et jeux de ton PC à distance."},
        {"steam", "com.nvidia.geforcenow", "Jeux PC en streaming depuis le cloud."},
    };

    static String groupName(String id) {
        for (String[] g : GROUPS) if (g[0].equals(id)) return g[1];
        return id;
    }

    /** Client libre qu'Origami intégrera pour cette plateforme. */
    static String builtinName(String id) {
        for (String[] g : GROUPS) if (g[0].equals(id)) return g[2];
        return null;
    }

    /** Clients ajoutés à la main : « section|paquet/activité » → paquet/activité de cette section. */
    private static String customGroup(java.util.Set<String> custom, String component) {
        for (String c : custom) {
            int bar = c.indexOf('|');
            if (bar > 0 && c.substring(bar + 1).equals(component)) return c.substring(0, bar);
        }
        return null;
    }

    private static boolean remotePackage(String pkg) {
        for (String[] r : OFFICIAL) if (r[1].equals(pkg)) return true;
        return false;
    }

    /** Plateforme d'une alternative reconnue (par paquet ou par nom), sinon null. */
    private static String[] alternative(String pkg, String label) {
        String p = pkg.toLowerCase(Locale.ROOT), l = label.toLowerCase(Locale.ROOT);
        for (String[] a : ALTERNATIVES) {
            if (p.equals(a[1]) || (!a[1].contains(".") && (p.contains(a[1]) || l.replace(" ", "").contains(a[1])))) return a;
        }
        return null;
    }

    private static boolean chiaki(String pkg, String label) {
        // chiaki-ng change d'identifiant selon ses versions : on le reconnaît à son nom.
        return pkg.toLowerCase(Locale.ROOT).contains("chiaki") || label.toLowerCase(Locale.ROOT).contains("chiaki");
    }

    private static List<ResolveInfo> launchables(Context ctx) {
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        return ctx.getPackageManager().queryIntentActivities(main, 0);
    }

    private static String uri(ResolveInfo ri) {
        return "app:" + ri.activityInfo.packageName + "/" + ri.activityInfo.name;
    }

    private static void sort(List<Game> out) {
        final Collator col = Collator.getInstance();
        Collections.sort(out, new Comparator<Game>() {
            public int compare(Game x, Game y) {
                return col.compare(x.title, y.title);
            }
        });
    }

    /** Jeux Android installés (déclarés « jeu » par leur éditeur), émulateurs et clients exclus. */
    static List<Game> games(Context ctx, java.util.Set<String> custom) {
        PackageManager pm = ctx.getPackageManager();
        List<Game> out = new ArrayList<Game>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (ResolveInfo ri : launchables(ctx)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || !seen.add(pkg)) continue;
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            if (ai == null || !isGame(ai)) continue;
            String label = String.valueOf(ri.loadLabel(pm));
            if (remotePackage(pkg) || alternative(pkg, label) != null || Launcher.isEmulator(pkg)
                    || customGroup(custom, uri(ri).substring(4)) != null) continue;
            Game g = new Game(label, SystemInfo.ANDROID, uri(ri));
            g.coverUri = g.uri;
            g.note = "Jeu Android installé sur ce téléphone. Ⓐ l'ouvre.";
            out.add(g);
        }
        sort(out);
        return out;
    }

    @SuppressWarnings("deprecation")
    private static boolean isGame(ApplicationInfo ai) {
        if (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME) return true;
        return (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
    }

    /**
     * Colonne Remote Play : une entrée par plateforme (PlayStation, XBOX, Steam). Ⓐ ouvre son menu :
     * solutions officielles, alternatives trouvées sur le téléphone, choix manuel, client intégré à venir.
     */
    static List<Game> remote(Context ctx, Store store) {
        List<Game> out = new ArrayList<Game>();
        for (String[] gr : GROUPS) {
            Game g = new Game(gr[1], SystemInfo.REMOTE, "action:remote:" + gr[0]);
            g.group = gr[0];
            Client pref = preferred(ctx, store, gr[0]);
            g.note = (pref != null ? "App préférée : " + pref.label + ". " : "")
                    + "Ⓐ : applis officielles, alternatives installées sur le téléphone, ou l'app de ton choix.";
            out.add(g);
        }
        return out;
    }

    /** Un client de Remote Play proposé dans le menu d'une plateforme. */
    static final class Client {
        String label, uri, note;
        boolean official, installed, custom;

        Game game() {
            Game g = new Game(label, SystemInfo.REMOTE, uri);
            g.note = note;
            return g;
        }
    }

    /** Officiels d'abord (installés ou à télécharger), puis alternatives installées, puis ajouts manuels. */
    static List<Client> clients(Context ctx, Store store, String group) {
        PackageManager pm = ctx.getPackageManager();
        java.util.Set<String> custom = store.remoteClients();
        List<Client> official = new ArrayList<Client>(), alt = new ArrayList<Client>();
        java.util.Set<String> found = new java.util.HashSet<String>();
        for (ResolveInfo ri : launchables(ctx)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName())) continue;
            String label = String.valueOf(ri.loadLabel(pm));
            Client c = new Client();
            c.label = label;
            c.uri = uri(ri);
            c.installed = true;
            for (String[] r : OFFICIAL) {
                if (r[1].equals(pkg) && r[0].equals(group)) {
                    c.official = true;
                    c.note = r[3];
                }
            }
            if (!c.official) {
                String[] a = alternative(pkg, label);
                if (a != null && a[0].equals(group)) c.note = a[2];
                String own = customGroup(custom, c.uri.substring(4));
                if (group.equals(own)) {
                    c.custom = true;
                    if (c.note == null) c.note = "Ajoutée par toi.";
                }
            }
            if (c.note == null || !found.add(pkg)) continue;
            (c.official ? official : alt).add(c);
        }
        for (String[] r : OFFICIAL) {
            if (!r[0].equals(group) || found.contains(r[1])) continue;
            Client c = new Client();
            c.label = r[2];
            c.uri = "play:" + r[1];
            c.official = true;
            c.note = r[3];
            official.add(c);
        }
        official.addAll(alt);
        return official;
    }

    /** App préférée de la plateforme, si elle est toujours installée. */
    static Client preferred(Context ctx, Store store, String group) {
        String uri = store.remotePreferred(group);
        if (uri == null) return null;
        for (Client c : clients(ctx, store, group)) if (c.installed && c.uri.equals(uri)) return c;
        return null;
    }

    /** Clé enregistrée pour un client ajouté à la main. */
    static String customKey(String group, String uri) {
        return group + "|" + uri.substring(4);
    }

    /** Vrai si le nom ou le paquet évoque Chiaki (proposé en tête du choix). */
    static boolean looksLikeChiaki(String pkg, String label) {
        return chiaki(pkg, label);
    }

    /** Entrée « Actualiser la bibliothèque » de la catégorie Tous. */
    static Game refreshItem() {
        Game g = new Game("Actualiser la bibliothèque", SystemInfo.ACTION, "action:rescan");
        g.note = "Relit ton dossier de jeux et range les nouveautés par console.";
        return g;
    }

    /** Ouvre l'application, ou sa page du Play Store si elle manque. */
    static void open(Activity a, Store store, Game g) {
        try {
            if (g.uri.startsWith("play:")) {
                String pkg = g.uri.substring(5);
                try {
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
                } catch (Exception e) {
                    a.startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
                }
                return;
            }
            ComponentName cn = component(g.uri);
            a.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            store.addRecent(g);
        } catch (Exception e) {
            I18n.toast(a, "Impossible d'ouvrir " + g.title + ".", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** Page « Infos sur l'appli » du système. */
    static void details(Activity a, Game g) {
        try {
            a.startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + component(g.uri).getPackageName())));
        } catch (Exception ignored) {
            // Pas d'écran de détails : rien à faire.
        }
    }

    static ComponentName component(String uri) {
        String s = uri.substring(4);
        int slash = s.indexOf('/');
        return new ComponentName(s.substring(0, slash), s.substring(slash + 1));
    }

    /** Icône de l'application en image carrée (pour CoverLoader). */
    static Bitmap icon(PackageManager pm, String uri, int px) {
        try {
            Drawable d = pm.getActivityIcon(component(uri));
            Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, px, px);
            d.draw(new Canvas(b));
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}
