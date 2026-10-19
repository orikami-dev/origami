package com.hideo.foldplay;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Origami en lanceur du téléphone (bouton Accueil) et accès aux autres applications. L'entrée
 * « HomeLauncher » du manifeste porte la catégorie HOME ; elle reste désactivée tant que
 * l'utilisateur ne l'a pas demandé, pour qu'Origami n'apparaisse pas d'office dans le choix
 * du lanceur.
 */
final class HomeApps {
    static final int REQ_HOME = 41;

    private HomeApps() {
    }

    private static ComponentName alias(Context ctx) {
        return new ComponentName(ctx, ctx.getPackageName() + ".HomeLauncher");
    }

    /** Vrai si Origami est le lanceur choisi du téléphone. */
    static boolean isDefault(Context ctx) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = ctx.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && ctx.getPackageName().equals(ri.activityInfo.packageName);
    }

    /** Active l'entrée HOME puis demande au système d'en faire le lanceur par défaut. */
    static void askDefault(Activity a) {
        try {
            a.getPackageManager().setComponentEnabledSetting(alias(a),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        } catch (Exception ignored) {
            // Entrée absente : le système ne proposera pas Origami.
        }
        if (Build.VERSION.SDK_INT >= 29) {
            android.app.role.RoleManager rm = a.getSystemService(android.app.role.RoleManager.class);
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)
                    && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                try {
                    a.startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQ_HOME);
                    return;
                } catch (Exception ignored) {
                    // Pas d'écran de rôle : réglages du lanceur ci-dessous.
                }
            }
        }
        try {
            a.startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            a.startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    static final class App {
        final String label;
        final ComponentName component;
        final ResolveInfo info;

        App(String label, ComponentName component, ResolveInfo info) {
            this.label = label;
            this.component = component;
            this.info = info;
        }

        Drawable icon(Context ctx) {
            return info.loadIcon(ctx.getPackageManager());
        }
    }

    /** Applications installées qu'on peut ouvrir, Origami exclu, par ordre alphabétique. */
    static List<App> apps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<App> out = new ArrayList<App>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo == null || ctx.getPackageName().equals(ri.activityInfo.packageName)) continue;
            out.add(new App(String.valueOf(ri.loadLabel(pm)),
                    new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name), ri));
        }
        final Collator col = Collator.getInstance();
        Collections.sort(out, new Comparator<App>() {
            public int compare(App x, App y) {
                return col.compare(x.label, y.label);
            }
        });
        return out;
    }

    static void open(Context ctx, App app) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (Exception e) {
            I18n.toast(ctx, "Impossible d'ouvrir " + app.label + ".", android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
