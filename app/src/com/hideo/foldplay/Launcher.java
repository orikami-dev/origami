package com.hideo.foldplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.StrictMode;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lance un jeu : dans le moteur intégré (cœurs libretro téléchargés sur l'appareil) quand la console
 * en a un, sinon dans un émulateur déjà installé. Origami n'embarque aucun jeu.
 *
 * On ne passe plus par le sélecteur Android générique (qui propose n'importe quelle app capable
 * d'ouvrir « un fichier », comme Wallet ou Google) : on détecte nous-mêmes les émulateurs
 * installés qui savent ouvrir cette console, on propose une seule fenêtre si plusieurs
 * conviennent, et le choix est mémorisé. Ensuite, le lancement est direct.
 */
final class Launcher {
    static final String BUILTIN = "builtin:";
    private static final String RETROARCH_ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture";

    /** Émulateurs connus : paquet → consoles. Ceux qui ne savent pas ouvrir un fichier sont écartés à la détection. */
    private static final String[][] KNOWN = {
        {"com.retroarch.aarch64", "*"},
        {"com.retroarch", "*"},
        {"com.swordfish.lemuroid", "nes snes gb gbc gba n64 md sms gg pce psx psp nds"},
        {"com.fastemulator.gba", "gba"},
        {"com.fastemulator.gbafree", "gba"},
        {"com.fastemulator.gbc", "gb gbc"},
        {"com.fastemulator.gbcfree", "gb gbc"},
        {"it.dbtecno.pizzaboygba", "gba"},
        {"it.dbtecno.pizzaboygbapro", "gba"},
        {"it.dbtecno.pizzaboy", "gb gbc"},
        {"it.dbtecno.pizzaboypro", "gb gbc"},
        {"com.johnemulators.johngba", "gba"},
        {"com.johnemulators.johngbalite", "gba"},
        {"com.explusalpha.GbaEmu", "gba"},
        {"com.explusalpha.GbcEmu", "gb gbc"},
        {"com.explusalpha.NesEmu", "nes"},
        {"com.explusalpha.Snes9xPlus", "snes"},
        {"com.explusalpha.MdEmu", "md sms gg"},
        {"com.nostalgiaemulators.nes", "nes"},
        {"com.nostalgiaemulators.nesfull", "nes"},
        {"com.dsemu.drastic", "nds"},
        {"me.magnum.melonds", "nds"},
        {"com.github.stenzek.duckstation", "psx"},
        {"org.ppsspp.ppsspp", "psp"},
        {"org.ppsspp.ppssppgold", "psp"},
        {"org.mupen64plusae.v3.fzurita", "n64"},
        {"org.mupen64plusae.v3.fzurita.pro", "n64"},
        {"xyz.aethersx2.android", "ps2"},
        {"xyz.aethersx2.custom", "ps2"},
        {"come.nanodata.armsx2", "ps2"},
        {"org.dolphinemu.dolphinemu", "gc wii"},
        {"org.dolphinemu.mmjr", "gc wii"},
        {"org.azahar_emu.azahar", "3ds"},
        {"io.github.lime3ds.android", "3ds"},
        {"org.citra.emu", "3ds"},
        {"org.vita3k.emulator", "vita"},
        {"dev.eden.eden_emulator", "switch"},
        {"org.citron.citron_emu", "switch"},
        {"org.sudachi.sudachi_emu", "switch"},
        {"org.kenjinx.android", "switch"},
        {"org.yuzu.yuzu_emu", "switch"},
        {"com.flycast.emulator", "dc"},
        {"io.recompiled.redream", "dc"},
        {"org.uoyabause.android", "saturn"},
        {"org.uoyabause.uranus.pro", "saturn"},
    };

    /** Mots qui trahissent un émulateur dans un nom de paquet inconnu de notre liste. */
    private static final String[] EMULATOR_HINTS = {
        "emu", "retroarch", "sx2", "dolphin", "citra", "azahar", "lime3ds", "vita3k", "eden",
        "citron", "sudachi", "yuzu", "kenji", "ryujinx", "ppsspp", "drastic", "melonds", "flycast",
        "redream", "yaba", "duckstation", "mupen", "pizzaboy", "fastemulator", "explusalpha", "skyline",
    };

    /** Un émulateur capable d'ouvrir ce jeu, avec l'intention exacte qui le lance. */
    static final class Candidate {
        final String key;
        final String label;
        final Drawable icon;
        final Intent intent;

        Candidate(String key, String label, Drawable icon, Intent intent) {
            this.key = key;
            this.label = label;
            this.icon = icon;
            this.intent = intent;
        }
    }

    /** Vrai si ce paquet est un émulateur connu (écarté des jeux mobiles). */
    static boolean isEmulator(String pkg) {
        for (String[] k : KNOWN) if (k[0].equals(pkg)) return true;
        for (Emulators.Entry e : Emulators.ALL) if (pkg.equals(e.play)) return true;
        return false;
    }

    private final Activity activity;
    private final Store store;
    private final PackageManager pm;

    Launcher(Activity activity, Store store) {
        this.activity = activity;
        this.store = store;
        this.pm = activity.getPackageManager();
        // Beaucoup d'émulateurs n'ouvrent que des chemins « file:// ». Android refuse de les
        // transmettre par défaut ; comme les autres lanceurs, on lève ce garde-fou pour nous.
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build());
    }

    void launch(Game g) {
        List<Candidate> all = detect(g);
        String saved = store.emulatorFor(g.system);
        if (saved != null) {
            for (Candidate c : all) {
                if (c.key.equals(saved)) {
                    start(c, g);
                    return;
                }
            }
            store.setEmulator(g.system.id, (String) null);
        }
        if (!all.isEmpty() && all.get(0).key.startsWith(BUILTIN)) {
            // Pas encore de choix : le moteur intégré passe d'office, on peut changer depuis Maison.
            store.setEmulator(g.system.id, all.get(0).key);
            start(all.get(0), g);
        } else if (all.size() == 1) {
            store.setEmulator(g.system.id, all.get(0).key);
            start(all.get(0), g);
        } else if (all.isEmpty()) {
            noEmulator(g);
        } else {
            choose(g, all);
        }
    }

    /** Force le choix de l'émulateur pour cette console (depuis les options). */
    void pick(Game g) {
        store.setEmulator(g.system.id, (String) null);
        List<Candidate> all = detect(g);
        if (all.isEmpty()) noEmulator(g);
        else choose(g, all);
    }

    String emulatorLabel(Game g) {
        return emulatorLabel(g.system);
    }

    String emulatorLabel(SystemInfo system) {
        String saved = store.emulatorFor(system);
        if (saved == null) return null;
        if (saved.startsWith(BUILTIN)) return "Intégré (" + saved.substring(BUILTIN.length()) + ")";
        if (saved.startsWith("retroarch:")) return "RetroArch (" + system.core + ")";
        String pkg = saved.contains(":") ? saved.substring(saved.indexOf(':') + 1) : saved;
        int slash = pkg.indexOf('/');
        if (slash > 0) pkg = pkg.substring(0, slash);
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ détection

    List<Candidate> detect(Game g) {
        List<Candidate> out = new ArrayList<Candidate>();
        Set<String> seen = new HashSet<String>();
        String self = activity.getPackageName();

        // En premier : le moteur intégré, quand la console a un cœur libretro branché.
        String core = CoreManager.coreFor(g.system);
        if (core != null) core = new CoreManager(activity).coreToRun(g.system);
        if (core != null && Libretro.available()) {
            Intent i = new Intent(activity, GameActivity.class)
                    .putExtra(GameActivity.EXTRA_URI, g.uri)
                    .putExtra(GameActivity.EXTRA_PATH, g.path)
                    .putExtra(GameActivity.EXTRA_NAME, g.fileName)
                    .putExtra(GameActivity.EXTRA_TITLE, g.title)
                    .putExtra(GameActivity.EXTRA_SYSTEM, g.system.id)
                    .putExtra(GameActivity.EXTRA_CORE, core);
            out.add(new Candidate(BUILTIN + core, "Origami intégré (" + core + ")", icon(self), i));
        }

        // PS3 : moteur intégré à part (ARMSX3), dans son propre processus.
        if (g.system.id.equals("ps3") && Ps3Core.present(activity)) {
            Intent i = new Intent(activity, Ps3Activity.class)
                    .putExtra(Ps3Activity.EXTRA_PATH, g.path)
                    .putExtra(Ps3Activity.EXTRA_TITLE, g.title);
            out.add(new Candidate(BUILTIN + "armsx3", "Origami intégré (armsx3)", icon(self), i));
        }

        // Xbox 360 : moteur intégré à part (Xenia), dans son propre processus.
        if (g.system.id.equals("x360") && X360Activity.present(activity)) {
            Intent i = new Intent(activity, X360Activity.class)
                    .putExtra(X360Activity.EXTRA_PATH, g.path)
                    .putExtra(X360Activity.EXTRA_TITLE, g.title);
            out.add(new Candidate(BUILTIN + "xenia", "Origami intégré (xenia)", icon(self), i));
        }

        for (String[] k : KNOWN) {
            String pkg = k[0];
            if (!installed(pkg)) continue;
            if (!k[1].equals("*") && !(" " + k[1] + " ").contains(" " + g.system.id + " ")) continue;
            Intent i = intentFor(pkg, g);
            if (i == null) continue;
            String key = (pkg.startsWith("com.retroarch") ? "retroarch:" : "pkg:") + pkg;
            out.add(new Candidate(key, label(pkg), icon(pkg), i));
            seen.add(pkg);
        }

        // Émulateurs inconnus de notre liste qui acceptent un fichier quelconque : on ne garde que
        // ceux dont le nom de paquet ressemble à un émulateur (et pas Wallet, Google, etc.).
        Intent generic = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(g.uri), "application/octet-stream");
        for (ResolveInfo ri : pm.queryIntentActivities(generic, PackageManager.MATCH_DEFAULT_ONLY)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(self) || seen.contains(pkg) || !looksLikeEmulator(pkg)) continue;
            Intent i = new Intent(generic).setComponent(new ComponentName(pkg, ri.activityInfo.name))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            out.add(new Candidate("pkg:" + pkg, label(pkg), icon(pkg), i));
            seen.add(pkg);
        }

        // Émulateurs inconnus de notre liste mais qui déclarent l'extension du jeu.
        if (g.path != null) {
            Intent probe = new Intent(Intent.ACTION_VIEW, Uri.fromFile(new File(g.path)));
            for (ResolveInfo ri : pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(self) || seen.contains(pkg)) continue;
                Intent i = new Intent(probe).setComponent(new ComponentName(pkg, ri.activityInfo.name));
                out.add(new Candidate("pkg:" + pkg, label(pkg), icon(pkg), i));
                seen.add(pkg);
            }
        }
        return out;
    }

    /** L'intention qui lance ce jeu dans ce paquet, ou null s'il ne sait pas l'ouvrir. */
    private Intent intentFor(String pkg, Game g) {
        if (pkg.startsWith("com.retroarch")) return retroArchIntent(pkg, g);
        if (pkg.equals("com.dsemu.drastic") && g.path != null) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setComponent(new ComponentName(pkg, "com.dsemu.drastic.DraSticActivity"));
            i.putExtra("GAMEPATH", g.path);
            return resolves(i) ? i : null;
        }
        if (pkg.startsWith("xyz.aethersx2")) {
            // AetherSX2 et ses dérivés (NetherSX2) démarrent un jeu via EmulationActivity + bootPath.
            // Ils lisent les jeux par le stockage Android (SAF) : un chemin /storage/… les faisait
            // planter. On passe l'URI du document, avec la permission de lecture accordée par le
            // ClipData (une permission ne suit pas un simple extra), comme le fait ES-DE.
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(pkg, "xyz.aethersx2.android.EmulationActivity"));
            Uri doc = Uri.parse(g.uri);
            i.putExtra("bootPath", doc.toString());
            i.setClipData(android.content.ClipData.newRawUri("jeu", doc));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (resolves(i)) return i;
        }
        if (pkg.equals("com.github.stenzek.duckstation")) {
            Intent i = new Intent();
            i.setComponent(new ComponentName(pkg, "com.github.stenzek.duckstation.EmulationActivity"));
            i.putExtra("bootPath", g.path != null ? g.path : g.uri);
            if (resolves(i)) return i;
        }
        // Cas général : on essaie le fichier tel que l'app le déclare, du plus précis au plus large.
        List<Intent> variants = new ArrayList<Intent>();
        String mime = g.mime == null || g.mime.isEmpty() ? "application/octet-stream" : g.mime;
        variants.add(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(g.uri), mime));
        if (g.path != null) variants.add(new Intent(Intent.ACTION_VIEW, Uri.fromFile(new File(g.path))));
        variants.add(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(g.uri), "*/*"));
        for (Intent v : variants) {
            v.setPackage(pkg);
            v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            List<ResolveInfo> r = pm.queryIntentActivities(v, 0);
            if (!r.isEmpty()) {
                v.setComponent(new ComponentName(pkg, r.get(0).activityInfo.name));
                return v;
            }
        }
        return null;
    }

    private Intent retroArchIntent(String pkg, Game g) {
        if (g.path == null || g.system.core == null) return null;
        String ext = "/storage/emulated/0/Android/data/" + pkg + "/files";
        Intent i = new Intent();
        i.setComponent(new ComponentName(pkg, RETROARCH_ACTIVITY));
        i.putExtra("ROM", g.path);
        i.putExtra("LIBRETRO", "/data/data/" + pkg + "/cores/" + g.system.core + "_libretro_android.so");
        i.putExtra("CONFIGFILE", ext + "/retroarch.cfg");
        i.putExtra("IME", Settings.Secure.getString(activity.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD));
        i.putExtra("DATADIR", "/data/data/" + pkg);
        i.putExtra("SDCARD", "/storage/emulated/0");
        i.putExtra("EXTERNAL", ext);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return resolves(i) ? i : null;
    }

    // ------------------------------------------------------------------ fenêtres

    /** Un menu codec, avec les émulateurs qui conviennent et leur icône. */
    private void choose(final Game g, final List<Candidate> all) {
        CodecMenu m = new CodecMenu(activity).title("Émulateur " + g.system.shortName, g.title);
        for (final Candidate c : all) {
            m.item(c.icon, c.label, null, new Runnable() {
                public void run() {
                    store.setEmulator(g.system.id, c.key);
                    start(c, g);
                }
            });
        }
        m.show(CodecMenu.hostFor(activity));
    }

    private void noEmulator(final Game g) {
        CodecMenu m = new CodecMenu(activity).title("Pas encore pris en charge", g.title)
                .message("Origami ne fait pas encore tourner la " + g.system.name + ". Elle arrivera avec un "
                        + "nouveau cœur ; en attendant, tu peux ouvrir le jeu avec une autre app.");
        m.item("Ouvrir avec une autre app…", null, new Runnable() {
                    public void run() {
                        Intent v = new Intent(Intent.ACTION_VIEW)
                                .setDataAndType(Uri.parse(g.uri), "application/octet-stream")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try {
                            activity.startActivity(Intent.createChooser(v, "Ouvrir « " + g.title + " » avec"));
                        } catch (ActivityNotFoundException e) {
                            toast("Aucune app ne sait ouvrir ce fichier.");
                        }
                    }
                })
                .item("Plus tard", null, null)
                .show(CodecMenu.hostFor(activity));
    }

    private void start(Candidate c, Game g) {
        store.addRecent(g);
        UiSound.play(activity, UiSound.LAUNCH);
        try {
            activity.startActivity(c.intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            store.setEmulator(g.system.id, (String) null);
            toast(c.label + " refuse d'ouvrir ce jeu. Choisis un autre émulateur.");
        }
    }

    // ------------------------------------------------------------------ outils

    private boolean installed(String pkg) {
        try {
            pm.getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private static boolean looksLikeEmulator(String pkg) {
        String p = pkg.toLowerCase(java.util.Locale.ROOT);
        for (String h : EMULATOR_HINTS) if (p.contains(h)) return true;
        return false;
    }

    private boolean resolves(Intent i) {
        return !pm.queryIntentActivities(i, 0).isEmpty();
    }

    private String label(String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private Drawable icon(String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void toast(String msg) {
        I18n.toast(activity, msg, Toast.LENGTH_LONG).show();
    }
}
