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

    /** Sections de Remote Play, par constructeur : {id, nom}. Nintendo n'a pas de jeu à distance. */
    static final String[][] GROUPS = {
        {"ps", "PlayStation"},
        {"xbox", "Xbox"},
        {"pc", "PC et cloud"},
    };

    /** {section, paquet, nom, proposé au Play Store s'il manque (« store ») ou montré seulement s'il est là, texte de la fiche} */
    static final String[][] REMOTE = {
        {"ps", "com.playstation.remoteplay", "PS Remote Play", "store",
            "Joue à distance aux jeux de ta PS5 ou de ta PS4, chez toi ou en déplacement."},
        {"ps", "com.metallic.chiaki", "Chiaki", "",
            "Client libre de Remote Play PlayStation."},
        {"xbox", "com.microsoft.xboxone.smartglass", "Xbox", "store",
            "Joue à distance sur ta Xbox, ou dans le cloud avec le Game Pass."},
        {"xbox", "com.gamepass", "Xbox Game Pass", "",
            "Jeux du Game Pass en streaming depuis le cloud."},
        {"pc", "com.valvesoftware.steamlink", "Steam Link", "store",
            "Joue aux jeux de ton PC Steam sur le téléphone."},
        {"pc", "com.limelight", "Moonlight", "store",
            "Client libre de streaming depuis ton PC (Sunshine ou GeForce Experience)."},
        {"pc", "com.parsec.app", "Parsec", "",
            "Bureau et jeux de ton PC à distance."},
        {"pc", "com.nvidia.geforcenow", "GeForce NOW", "",
            "Jeux PC en streaming depuis le cloud."},
    };

    static String groupName(String id) {
        for (String[] g : GROUPS) if (g[0].equals(id)) return g[1];
        return id;
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
        for (String[] r : REMOTE) if (r[1].equals(pkg)) return true;
        return false;
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
            if (remotePackage(pkg) || chiaki(pkg, label) || Launcher.isEmulator(pkg)
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

    /** Clients installés d'abord, puis ceux du Play Store qui manquent (Ⓐ ouvre leur page). */
    static List<Game> remote(Context ctx, java.util.Set<String> custom) {
        PackageManager pm = ctx.getPackageManager();
        // Par section : clients installés (connus ou ajoutés), clients du Play Store qui manquent,
        // puis « Ajouter un client… » pour mettre l'app de son choix (client non officiel).
        java.util.Map<String, List<Game>> bySection = new java.util.LinkedHashMap<String, List<Game>>();
        for (String[] gr : GROUPS) bySection.put(gr[0], new ArrayList<Game>());
        java.util.Set<String> found = new java.util.HashSet<String>();
        for (ResolveInfo ri : launchables(ctx)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            String label = String.valueOf(ri.loadLabel(pm));
            String note = null, group = null;
            for (String[] r : REMOTE) {
                if (r[1].equals(pkg)) {
                    note = r[4];
                    group = r[0];
                }
            }
            if (note == null && chiaki(pkg, label)) {
                note = "Client libre de Remote Play PlayStation.";
                group = "ps";
            }
            String own = customGroup(custom, uri(ri).substring(4));
            if (own != null) {
                group = own;
                if (note == null) note = "Client ajouté par toi.";
            }
            if (note == null || bySection.get(group) == null || !found.add(pkg)) continue;
            Game g = new Game(label, SystemInfo.REMOTE, uri(ri));
            g.coverUri = g.uri;
            g.group = group;
            g.note = note + " Ⓐ l'ouvre.";
            bySection.get(group).add(g);
        }
        List<Game> out = new ArrayList<Game>();
        for (String[] gr : GROUPS) {
            List<Game> list = bySection.get(gr[0]);
            sort(list);
            out.addAll(list);
            for (String[] r : REMOTE) {
                if (!r[0].equals(gr[0]) || !r[3].equals("store") || found.contains(r[1])) continue;
                Game g = new Game(r[2], SystemInfo.REMOTE, "play:" + r[1]);
                g.group = gr[0];
                g.note = r[4] + " Pas encore installé : Ⓐ ouvre sa page sur le Play Store.";
                out.add(g);
            }
            out.add(addClientItem(gr[0]));
        }
        return out;
    }

    /** Dernière entrée d'une section : y ajouter un client non officiel (n'importe quelle app installée). */
    static Game addClientItem(String group) {
        Game g = new Game("Ajouter un client " + groupName(group) + "…", SystemInfo.ACTION, "action:addclient:" + group);
        g.group = group;
        g.note = "Ajoute l'app de ton choix à cette section, par exemple un client libre à la place de l'app officielle.";
        return g;
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
