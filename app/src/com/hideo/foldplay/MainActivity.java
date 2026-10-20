package com.hideo.foldplay;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Origami : un lanceur de jeux rétro qui change de forme avec le Galaxy Fold.
 * - Écran externe (ou téléphone classique) : liste compacte.
 * - Fold ouvert à plat : barre des consoles + grille de cartouches.
 * - Mode Flex (plié à moitié, charnière horizontale) : le jeu en vedette en haut,
 *   un carrousel et une manette de navigation en bas.
 */
public class MainActivity extends Activity implements SensorEventListener, ColorSheet.Host {
    private static final int REQ_TREE = 7;
    private static final int REQ_COVER = 8;
    /** Jeu dont on choisit la jaquette à la main (image du téléphone). */
    private Game coverFor;
    /** Sensor.TYPE_HINGE_ANGLE (API 30), écrit en dur pour rester compatible dès Android 8. */
    private static final int TYPE_HINGE_ANGLE = 36;
    private static final float FLEX_MIN_ANGLE = 60f;
    private static final float FLEX_MAX_ANGLE = 150f;

    enum Posture { COVER, OPEN, FLEX }

    private Store store;
    private Launcher launcher;
    private CoverLoader covers;
    private SensorManager sensors;
    private Sensor hinge;
    private float hingeAngle = -1;

    private FrameLayout root;
    private FrameLayout menuStage;
    private final List<Game> library = new ArrayList<Game>();
    private final List<Game> shown = new ArrayList<Game>();
    /** Catégories d'applications du XMB (0.60.5) : jeux Android installés, clients Remote Play. */
    private final List<Game> mobile = new ArrayList<Game>();
    private final List<Game> remote = new ArrayList<Game>();
    private final Game refreshItem = MobileApps.refreshItem();
    private String appsSig = "";
    private String filter = "all";
    private int selected;
    private Posture posture;
    /**
     * Pliant (BOSS 19/10) : l'écran externe ouvre le lanceur Washi, et déplier l'appareil
     * pendant qu'Origami est à l'écran passe en mode Tsuru. coverTsuru / openTsuru sont des choix
     * du moment, pas des réglages : replier ou déplier les remet à zéro.
     */
    private boolean wasCover, coverTsuru, openTsuru, resumed;
    private boolean scanning;
    private boolean splashDone;

    private TextView postureTag;
    private ConsoleEmblemView heroEmblem;
    private LinearLayout heroSheet;
    private TextView sheetTitle;
    private TextView sheetMeta;
    private TextView sheetSummary;
    private TextView sheetSource;
    private GameInfo gameInfo;
    private CoverFetcher coverFetcher;
    private TextView statusLine;
    private TextView heroSystem;
    private TextView heroTitle;
    private TextView heroMeta;
    private ScrollView carouselScroll;
    private HorizontalScrollView catScroll;
    private LinearLayout cats;
    private LinearLayout carousel;

    /** Intention d'un jeu à relancer (« Redémarrer la console ») une fois son moteur arrêté. */
    static final String EXTRA_RESTART = "origami.restart";

    @Override
    protected void onNewIntent(Intent in) {
        super.onNewIntent(in);
        setIntent(in);
        final Intent again = in.getParcelableExtra(EXTRA_RESTART);
        if (again == null) return;
        in.removeExtra(EXTRA_RESTART);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                try {
                    startActivity(again);
                } catch (Exception ignored) {
                    // Le jeu reste lançable depuis la bibliothèque.
                }
            }
        }, 1200);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Icône de thème restée en attente (app tuée avant de passer en arrière-plan) : on surveille.
        Theme.watchBackground(this);
        I18n.attach(this);
        Theme.sync(this);
        Theme.orientation(this);
        DebugLog.sync(this);
        store = new Store(this);
        if (!store.setupDone()) startActivity(new Intent(this, SetupActivity.class));
        Updater.autoCheck(this);
        if (Libretro.available()) store.adoptBuiltinOnce();
        launcher = new Launcher(this, store);
        covers = new CoverLoader(getContentResolver(), getPackageManager());
        coverFetcher = new CoverFetcher(this);
        gameInfo = new GameInfo(this);
        sensors = (SensorManager) getSystemService(SENSOR_SERVICE);
        hinge = sensors == null ? null : sensors.getDefaultSensor(TYPE_HINGE_ANGLE);
        wasCover = getResources().getConfiguration().smallestScreenWidthDp < 600;

        root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);
        Theme.fullscreen(this);

        if (state == null && store.intro()) {
            showSplash();
        } else {
            splashDone = true;
            rebuild();
        }
        String tree = store.treeUri();
        if (tree != null) scan(Uri.parse(tree));
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Theme.orientation(this);
        UiSound.musicStart(this);
        // Couleurs de la manette changées dans l'éditeur : l'interface suit.
        if (Theme.sync(this) && root != null && posture != null) rebuild();
        // Manette changée dans l'éditeur : Mini et Flex font passer l'accueil en plein écran.
        else if (homeCol != null && !store.activeLayout().equals(homeLayoutName)) {
            homeLayoutName = store.activeLayout();
            applyPadHome();
        }
        if (hinge != null) sensors.registerListener(this, hinge, SensorManager.SENSOR_DELAY_UI);
        if (pads == null) pads = new Pads(this);
        pads.input().registerInputDeviceListener(padListener, null);
        if (store.takeRescanRequest()) {
            String tree = store.treeUri();
            if (tree != null) {
                scan(Uri.parse(tree));
            } else {
                library.clear();
                filter = "all";
                selected = 0;
            }
        }
        if (splashDone) rebuild();
        loadApps();
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final String engine = DebugLog.collectExits(app);
                if (engine != null) runOnUiThread(new Runnable() {
                    public void run() {
                        toast("Le moteur " + engine + " s'est arrêté. Rapport enregistré : Réglages > Débogage > Partager le journal.");
                    }
                });
            }
        }, "origami-exits").start();
    }

    /** Relit les jeux Android et les clients Remote Play installés ; l'écran suit s'ils ont changé. */
    private void loadApps() {
        final android.content.Context ctx = getApplicationContext();
        final java.util.Set<String> custom = store.remoteClients();
        new Thread(new Runnable() {
            public void run() {
                final List<Game> m = MobileApps.games(ctx, custom);
                final List<Game> r = MobileApps.remote(ctx, store);
                StringBuilder sig = new StringBuilder();
                for (Game g : m) sig.append(g.uri).append('|');
                sig.append('#');
                for (Game g : r) sig.append(g.uri).append(g.note).append('|');
                final String now = sig.toString();
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (now.equals(appsSig)) return;
                        appsSig = now;
                        mobile.clear();
                        mobile.addAll(m);
                        remote.clear();
                        remote.addAll(r);
                        if (filter.equals("android") && mobile.isEmpty()) filter = "all";
                        if (splashDone && resumed) rebuild();
                    }
                });
            }
        }, "origami-apps").start();
    }

    /**
     * Menu d'une plateforme de Remote Play (0.62) : l'app préférée en tête, les solutions officielles
     * (ouvrir ou télécharger), les alternatives trouvées sur le téléphone, le choix manuel, puis le
     * client libre qu'Origami intégrera (grisé tant qu'il n'est pas prêt).
     */
    private void remoteMenu(final String group) {
        final Runnable self = new Runnable() { public void run() { remoteMenu(group); } };
        String name = MobileApps.groupName(group);
        CodecMenu m = new CodecMenu(this).title(name, "REMOTE PLAY");
        int px = dp(32);
        android.content.pm.PackageManager pm = getPackageManager();
        final MobileApps.Client pref = MobileApps.preferred(this, store, group);
        if (pref != null) {
            m.item(appIcon(pm, pref.uri, px), "Lancer " + pref.label, "Ton app préférée pour " + name, new Runnable() {
                public void run() { MobileApps.open(MainActivity.this, store, pref.game()); }
            });
        }
        boolean anyAlt = false;
        for (final MobileApps.Client c : MobileApps.clients(this, store, group)) {
            if (!c.official) anyAlt = true;
            String kind = c.official ? (c.installed ? "Officielle" : "Officielle · à télécharger sur le Play Store")
                    : c.custom ? "Choisie par toi" : "Alternative détectée sur le téléphone";
            boolean isPref = pref != null && pref.uri.equals(c.uri);
            m.item(c.installed ? appIcon(pm, c.uri, px) : null, (isPref ? "★ " : "") + c.label, kind + " · " + c.note,
                    new Runnable() {
                public void run() {
                    if (c.installed) {
                        store.setRemotePreferred(group, c.uri);
                        appsSig = "";
                        loadApps();
                    }
                    MobileApps.open(MainActivity.this, store, c.game());
                }
            });
        }
        if (!anyAlt) m.item("Aucune alternative détectée", "Aucun client non officiel trouvé sur le téléphone", null);
        m.sub("En choisir une manuellement…", "N'importe quelle app installée, par exemple PXPlay", new Runnable() {
            public void run() { addClientMenu(group); }
        });
        m.soon(null, "Client intégré à Origami", "👷‍♂️🚧 " + MobileApps.builtinName(group)
                + " sera intégré directement dans l'app, avec ta manette Origami");
        m.show(menuHost());
    }

    private android.graphics.drawable.Drawable appIcon(android.content.pm.PackageManager pm, String uri, int px) {
        try {
            android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(uri.substring(4));
            android.graphics.drawable.Drawable d = pm.getActivityIcon(cn);
            d.setBounds(0, 0, px, px);
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * « Ajouter un client… » d'une section de Remote Play : n'importe quelle app installée, pour
     * utiliser un client non officiel (chiaki-ng, Greenlight, …) à la place de celui du constructeur.
     */
    private void addClientMenu(final String group) {
        CodecMenu m = new CodecMenu(this).title("Ajouter un client", "REMOTE PLAY · " + MobileApps.groupName(group).toUpperCase());
        int px = dp(32);
        java.util.Set<String> taken = new java.util.HashSet<String>();
        for (MobileApps.Client c : MobileApps.clients(this, store, group)) if (c.installed) taken.add(c.uri);
        List<HomeApps.App> apps = HomeApps.apps(this);
        // Un client libre déjà installé (chiaki-ng…) passe en tête de liste.
        List<HomeApps.App> first = new ArrayList<HomeApps.App>(), rest = new ArrayList<HomeApps.App>();
        for (HomeApps.App app : apps) {
            String uri = "app:" + app.component.getPackageName() + "/" + app.component.getClassName();
            if (taken.contains(uri)) continue;
            (group.equals("ps") && MobileApps.looksLikeChiaki(app.component.getPackageName(), app.label) ? first : rest).add(app);
        }
        first.addAll(rest);
        for (final HomeApps.App app : first) {
            final String uri = "app:" + app.component.getPackageName() + "/" + app.component.getClassName();
            android.graphics.drawable.Drawable d = app.icon(this);
            if (d != null) d.setBounds(0, 0, px, px);
            m.item(d, app.label, null, new Runnable() {
                public void run() {
                    store.setRemoteClient(MobileApps.customKey(group, uri), true);
                    store.setRemotePreferred(group, uri);
                    toast(app.label + " : app préférée pour " + MobileApps.groupName(group) + ".");
                    appsSig = "";
                    loadApps();
                }
            });
        }
        m.show(menuHost());
    }

    private boolean appFilter() {
        return filter.equals("android") || filter.equals("remote");
    }

    /** Ⓐ sur une entrée : jeu (émulateur), application, client à installer ou action. */
    private void open(Game g) {
        if (g.uri.equals("action:rescan")) {
            if (store.treeUri() == null) {
                pickFolder();
                return;
            }
            if (scanning) return;
            announceScan = true;
            toast("Actualisation de la bibliothèque…");
            scan(Uri.parse(store.treeUri()));
        } else if (g.uri.startsWith("action:remote:")) {
            remoteMenu(g.group);
        } else if (g.uri.startsWith("action:addclient:")) {
            addClientMenu(g.group);
        } else if (g.isApp()) {
            MobileApps.open(this, store, g);
        } else {
            launcher.launch(g);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        UiSound.musicPause();
        if (hinge != null) sensors.unregisterListener(this);
        if (pads != null) pads.input().unregisterInputDeviceListener(padListener);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        Theme.fullscreen(this);
        Theme.orientation(this);
        boolean cover = c.smallestScreenWidthDp < 600;
        if (hinge != null && cover != wasCover) {
            coverTsuru = false;
            // Déplié avec Origami au premier plan (aucune autre app ouverte) : mode jeu direct.
            openTsuru = !cover && resumed && store.tsuruOnOpen();
        }
        wasCover = cover;
        if (splashDone) rebuild();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        hingeAngle = e.values[0];
        if (!splashDone) return;
        if (computePosture() != posture) rebuild();
        else if (postureTag != null) postureTag.setText(postureText());
        // Plié à moitié ou remis à plat en paysage : la manette quitte ou reprend la surimpression.
        if (homeCol != null) {
            Configuration cf = getResources().getConfiguration();
            boolean half = hingeAngle >= FLEX_MIN_ANGLE && hingeAngle <= FLEX_MAX_ANGLE;
            if ((cf.screenWidthDp > cf.screenHeightDp && !half) != homeOverlayLandscape) applyPadHome();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {
    }

    // ---------------------------------------------------------------- posture

    private Posture computePosture() {
        Configuration cf = getResources().getConfiguration();
        boolean halfway = hinge != null && hingeAngle >= FLEX_MIN_ANGLE && hingeAngle <= FLEX_MAX_ANGLE;
        // Écran externe d'un pliant : le lanceur Washi par défaut, Tsuru à la demande ou à la manette.
        if (hinge != null && cf.smallestScreenWidthDp < 600 && !(halfway && hingeHorizontal(cf))) {
            if (coverTsuru) return Posture.FLEX;
            if (store.padFullscreen()) {
                if (pads == null) pads = new Pads(this);
                if (pads.any()) return Posture.FLEX;
            }
            return Posture.COVER;
        }
        if (store.flexView() || openTsuru) return Posture.FLEX;
        // Manette branchée, sur n'importe quel écran : l'accueil console (scène plein écran, carrousel).
        if (store.padFullscreen()) {
            if (pads == null) pads = new Pads(this);
            if (pads.any()) return Posture.FLEX;
        }
        Configuration c = getResources().getConfiguration();
        boolean halfOpen = hinge != null && hingeAngle >= FLEX_MIN_ANGLE && hingeAngle <= FLEX_MAX_ANGLE;
        // Même en vue grille, plier à moitié repasse en Flex dès que la charnière coupe l'écran en haut / bas.
        if (halfOpen && hingeHorizontal(c)) return Posture.FLEX;
        if (c.smallestScreenWidthDp < 600) return Posture.COVER;
        return Posture.OPEN;
    }

    /**
     * Vrai si la charnière est horizontale à l'écran. Les clapets (Z Flip, Razr : écran intérieur
     * allongé, plus de 1,6:1) plient en travers de la longueur, donc horizontalement en portrait ;
     * les livres (Z Fold, Pixel Fold, Magic V, Find N, Mate XT, TriFold : écran presque carré)
     * plient horizontalement quand on les tient en paysage.
     */
    static boolean hingeHorizontal(Configuration c) {
        int w = c.screenWidthDp, h = c.screenHeightDp;
        float aspect = Math.max(w, h) / (float) Math.max(1, Math.min(w, h));
        return aspect > 1.6f ? h > w : w > h;
    }

    private String postureText() {
        StringBuilder sb = new StringBuilder("◢ ");
        if (posture == Posture.COVER) sb.append("WASHI");
        else if (posture == Posture.OPEN) sb.append("WASHI");
        else sb.append("TSURU");
        sb.append(" · ").append(library.size()).append(" jeux");
        if (scanning) sb.append(" · SCAN…");
        if (coverProgress != null) sb.append(" · ").append(coverProgress);
        return sb.toString();
    }

    // ---------------------------------------------------------------- données

    /** Vrai quand l'utilisateur a demandé l'actualisation : on lui dit combien de jeux on a trouvés. */
    private boolean announceScan;

    private void scan(final Uri tree) {
        scanning = true;
        if (postureTag != null) postureTag.setText(postureText());
        updateFlex(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Game> found = LibraryScanner.scan(getContentResolver(), tree);
                coverFetcher.attachCached(found);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanning = false;
                        if (announceScan && !found.isEmpty()) {
                            int diff = found.size() - library.size();
                            toast("Bibliothèque actualisée · " + found.size() + " jeux"
                                    + (diff > 0 ? " (+" + diff + ")" : diff < 0 ? " (" + diff + ")" : ""));
                        }
                        announceScan = false;
                        if (found.isEmpty()) {
                            toast("Aucun jeu reconnu. Range-les par console (snes/, gba/…).");
                        }
                        library.clear();
                        library.addAll(found);
                        filter = "all";
                        selected = 0;
                        if (splashDone) rebuild();
                        fetchCovers();
                    }
                });
            }
        }, "foldplay-scan").start();
    }

    private String coverProgress;

    /** Télécharge en arrière-plan les jaquettes qui manquent, puis rafraîchit l'écran. */
    private void fetchCovers() {
        if (!store.autoCovers() || library.isEmpty()) return;
        coverFetcher.fetchMissing(new ArrayList<Game>(library), new CoverFetcher.Listener() {
            @Override
            public void onProgress(int done, int total) {
                coverProgress = done < total ? "JAQUETTES " + done + "/" + total : null;
                if (postureTag != null) postureTag.setText(postureText());
            }

            @Override
            public void onFinished(int found) {
                coverProgress = null;
                if (found > 0 && splashDone) rebuild();
                else if (postureTag != null) postureTag.setText(postureText());
            }
        });
    }

    private void refilter() {
        shown.clear();
        if (filter.equals("recent")) {
            Map<String, Game> byUri = new HashMap<String, Game>();
            for (Game g : library) byUri.put(g.uri, g);
            for (Game g : mobile) byUri.put(g.uri, g);
            for (Game g : remote) byUri.put(g.uri, g);
            for (String u : store.recents()) {
                Game g = byUri.get(u);
                if (g != null) shown.add(g);
            }
        } else if (filter.equals("android")) {
            shown.addAll(mobile);
        } else if (filter.equals("remote")) {
            shown.addAll(remote);
        } else {
            for (Game g : library) {
                if (filter.equals("all")
                        || (filter.equals("fav") && store.isFavorite(g))
                        || g.system.id.equals(filter)) {
                    shown.add(g);
                }
            }
            if (filter.equals("fav")) {
                for (Game g : mobile) if (store.isFavorite(g)) shown.add(g);
                for (Game g : remote) if (store.isFavorite(g)) shown.add(g);
            }
            // « Tous » se termine par l'entrée qui relit le dossier (ex-bouton de la barre du haut).
            if (filter.equals("all") && !library.isEmpty()) shown.add(refreshItem);
        }
        if (selected >= shown.size()) selected = Math.max(0, shown.size() - 1);
    }

    private List<String> filterKeys() {
        List<String> keys = new ArrayList<String>();
        keys.add("all");
        keys.add("fav");
        keys.add("recent");
        for (SystemInfo s : SystemInfo.ALL) {
            for (Game g : library) {
                if (g.system == s) {
                    keys.add(s.id);
                    break;
                }
            }
        }
        if (!mobile.isEmpty()) keys.add(SystemInfo.ANDROID.id);
        keys.add(SystemInfo.REMOTE.id);
        return keys;
    }

    private String filterLabel(String key) {
        if (key.equals("all")) return "Tous";
        if (key.equals("fav")) return "★ Favoris";
        if (key.equals("recent")) return "Récents";
        SystemInfo s = SystemInfo.byId(key);
        if (s == SystemInfo.ANDROID || s == SystemInfo.REMOTE) return s.name;
        return s == null ? key : s.shortName;
    }

    private void cycleFilter(int dir) {
        List<String> keys = filterKeys();
        int i = keys.indexOf(filter);
        UiSound.play(this, UiSound.MOVE);
        switchFilter(keys.get(((i < 0 ? 0 : i) + dir + keys.size()) % keys.size()), dir);
    }

    // ---------------------------------------------------------------- écrans

    /**
     * Démarrage : seulement la grue qui se déplie et vient vers l'utilisateur, avec en bas le nom
     * de l'app et l'équipe. Un appui passe l'animation.
     */
    private void showSplash() {
        Theme.barsBackground(this, true);
        FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(Theme.BG);
        final OrigamiSplash crane = new OrigamiSplash(this);
        splash.addView(crane, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout foot = vertical();
        foot.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView name = text("ORIGAMI", 15, Theme.TEXT, false);
        name.setTypeface(Fonts.wide(this));
        name.setLetterSpacing(0.35f);
        name.setGravity(Gravity.CENTER);
        foot.addView(name);
        TextView team = text("KAMI-WORKS", 10, Theme.MUTED, false);
        team.setGravity(Gravity.CENTER);
        team.setTypeface(Fonts.wide(this));
        team.setLetterSpacing(0.45f);
        team.setPadding(0, dp(6), 0, 0);
        foot.addView(team);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        fp.bottomMargin = dp(28);
        splash.addView(foot, fp);
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        foot.setAlpha(0f);
        foot.animate().alpha(1f).setStartDelay(500).setDuration(500).start();

        final Runnable finish = new Runnable() {
            @Override
            public void run() {
                if (splashDone) return;
                splashDone = true;
                Theme.barsBackground(MainActivity.this, false);
                rebuild();
                root.setAlpha(0f);
                root.animate().alpha(1f).setDuration(260).start();
            }
        };
        crane.play(new OrigamiSplash.Done() {
            public void run() { finish.run(); }
        });
        splash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                crane.skip();
            }
        });
    }

    private LinearLayout homeStageCol;

    /** Tsuru : l'écran du haut passe sous la barre d'état et la manette sous la barre de navigation. */
    private void applyBleed() {
        int t = Theme.bleed ? Theme.INSETS[1] : 0, b = Theme.bleed ? Theme.INSETS[3] : 0;
        // Portrait : la pastille du haut garde ses coins ronds et commence sous la barre d'état (BOSS, 20/10).
        if (homeStageCol != null) homeStageCol.setPadding(0, 0, 0, 0);
        if (homeDeckView != null) homeDeckView.setBottomInset(b);
        if (homeCol != null && homeDeck != null && homeDeck.getVisibility() == View.VISIBLE) {
            homeCol.setPadding(homeCol.getPaddingLeft(), t, homeCol.getPaddingRight(), 0);
        }
        if (homeStage != null && homeDeck != null && homeDeck.getVisibility() == View.VISIBLE) {
            homeStage.setBackground(Theme.backdrop(dp(28)));
        }
    }

    private void rebuild() {
        Posture before = posture;
        posture = computePosture();
        refilter();
        View outgoing = (before != null && before != posture && splashDone && root.getChildCount() == 1)
                ? root.getChildAt(0) : null;
        if (outgoing == null) root.removeAllViews();
        // Deux pastilles (Tsuru) : entre elles et sous les barres, la couleur de séparation du thème,
        // pas un morceau du fond d'écran.
        root.setBackground(posture == Posture.FLEX ? Theme.gap() : Theme.backdrop(0));
        boolean flush = posture == Posture.FLEX && !store.appFullscreen();
        if (Theme.bleed != flush) {
            Theme.bleed = flush;
            View content = findViewById(android.R.id.content);
            if (content != null) content.requestApplyInsets();
        }
        Theme.insetsHook = new Runnable() {
            public void run() { applyBleed(); }
        };
        homeCol = null;
        homeDeck = null;
        homeDeckView = null;
        homeWrap = null;
        homeMini = null;
        homeHero = null;
        if (backdropView != null) backdropView.removeCallbacks(backdropIdle);
        backdropView = null;
        heroEmblem = null;
        heroSheet = null;
        statusLine = null;
        carousel = null;
        postureTag = null;
        View v;
        if (posture == Posture.FLEX) v = buildFlex();
        else if (posture == Posture.OPEN) v = buildOpen();
        else v = buildCover();
        root.addView(v, new FrameLayout.LayoutParams(-1, -1));
        if (outgoing != null) transition(outgoing, v, posture == Posture.FLEX);
    }

    /** Passage Washi / Tsuru : l'ancien écran s'efface, le nouveau arrive, au rythme du thème. */
    private void transition(final View out, View in, boolean toTsuru) {
        float w = root.getWidth(), h = root.getHeight();
        float dir = toTsuru ? 1f : -1f;
        long ms = 420;
        float inX = 0, inY = 0, inScale = 1f, outX = 0, outY = 0, outScale = 1f;
        android.animation.TimeInterpolator ip = new android.view.animation.DecelerateInterpolator(1.6f);
        String t = Theme.THEME_ID;
        if ("arctique".equals(t)) {          // banquise : glissement vertical, glacé
            inY = h * 0.08f * dir; outY = -h * 0.08f * dir; ms = 480;
        } else if ("ecarlate".equals(t)) {   // éventail : le nouvel écran s'ouvre depuis le centre
            inScale = toTsuru ? 0.88f : 1.12f; outScale = toTsuru ? 1.12f : 0.88f; ms = 460;
            ip = new android.view.animation.OvershootInterpolator(0.8f);
        } else if ("sakura".equals(t)) {     // pétales : dérive latérale douce
            inX = w * 0.10f * dir; outX = -w * 0.10f * dir; ms = 520;
            ip = new android.view.animation.AccelerateDecelerateInterpolator();
        } else if ("ambre".equals(t)) {      // oscilloscope : bref scintillement
            ms = 300; ip = new android.view.animation.CycleInterpolator(2.5f);
        } else {                             // codec : balayage radio, léger zoom
            inScale = 1.04f; ms = 380;
        }
        in.setAlpha(0f);
        in.setTranslationX(inX);
        in.setTranslationY(inY);
        in.setScaleX(inScale);
        in.setScaleY(inScale);
        if ("ambre".equals(t)) {
            in.setTranslationX(0); in.setScaleX(1f); in.setScaleY(1f);
            in.animate().alpha(1f).setDuration(ms + 120).setInterpolator(new android.view.animation.LinearInterpolator()).start();
            out.animate().alpha(0f).setDuration(ms).withEndAction(new Runnable() {
                public void run() { root.removeView(out); }
            }).start();
            return;
        }
        in.animate().alpha(1f).translationX(0).translationY(0).scaleX(1f).scaleY(1f)
                .setDuration(ms).setInterpolator(ip).start();
        out.animate().alpha(0f).translationX(outX).translationY(outY).scaleX(outScale).scaleY(outScale)
                .setDuration(ms * 3 / 4).setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(new Runnable() { public void run() { root.removeView(out); } }).start();
    }

    private View buildCover() {
        LinearLayout col = vertical();
        col.addView(topBar());
        col.addView(filterBar(false), new LinearLayout.LayoutParams(-1, -2));
        if (shown.isEmpty()) {
            col.addView(emptyState(), new LinearLayout.LayoutParams(-1, 0, 1f));
            return col;
        }
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setPadding(dp(8), dp(4), dp(8), dp(8));
        list.setClipToPadding(false);
        final GameAdapter adapter = new GameAdapter(false);
        list.setAdapter(adapter);
        wireClicks(list, adapter);
        list.setSelection(selected);
        col.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        return col;
    }

    private View buildOpen() {
        LinearLayout col = vertical();
        col.addView(topBar());
        LinearLayout row = new LinearLayout(this);
        row.addView(filterBar(true), new LinearLayout.LayoutParams(dp(170), -1));
        if (shown.isEmpty()) {
            row.addView(emptyState(), new LinearLayout.LayoutParams(0, -1, 1f));
        } else {
            GridView grid = new GridView(this);
            grid.setColumnWidth(dp(150));
            grid.setNumColumns(GridView.AUTO_FIT);
            grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            grid.setVerticalSpacing(dp(6));
            grid.setHorizontalSpacing(dp(6));
            grid.setPadding(dp(8), dp(4), dp(16), dp(16));
            grid.setClipToPadding(false);
            final GameAdapter adapter = new GameAdapter(true);
            grid.setAdapter(adapter);
            wireClicks(grid, adapter);
            grid.setSelection(selected);
            row.addView(grid, new LinearLayout.LayoutParams(0, -1, 1f));
        }
        col.addView(row, new LinearLayout.LayoutParams(-1, 0, 1f));
        return col;
    }

    private View buildFlex() {
        LinearLayout col = vertical();
        // Écran externe ou téléphone classique : tout est un peu plus petit.
        boolean narrow = getResources().getConfiguration().screenWidthDp < 520;
        float s = narrow ? 0.78f : 1f;

        // Moitié haute : la scène.
        FrameLayout stage = new FrameLayout(this);
        menuStage = stage;
        // Illustration du jeu survolé, sous un voile qui garde le texte lisible (façon accueil PS5).
        backdropView = new android.widget.ImageView(this);
        backdropView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        backdropView.setAlpha(0f);
        stage.addView(backdropView, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xB3000000 & 0xFF000000 | (Theme.BG & 0xFFFFFF), 0x66000000 | (Theme.BG & 0xFFFFFF),
                        0xE6000000 | (Theme.BG & 0xFFFFFF)}));
        stage.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        backdropGame = null;
        LinearLayout stageCol = vertical();
        homeStageCol = stageCol;
        stageCol.addView(topBar());
        // Façon XMB : les catégories (Tous, Favoris, Récents, consoles) en ligne, ◀ ▶ pour en
        // changer ; les jeux en colonne sous la catégorie choisie, ▲ ▼ ; la fiche du jeu à droite.
        stageCol.addView(categoryRow(s), new LinearLayout.LayoutParams(-1, dp(92 * s)));
        LinearLayout body = new LinearLayout(this);
        body.setPadding(dp(16 * s), 0, dp(20 * s), dp(12));
        LinearLayout info = vertical();
        heroSystem = text("", 12 * s, Theme.CODEC, false);
        heroSystem.setTypeface(Fonts.wide(this));
        heroSystem.setLetterSpacing(0.2f);
        Theme.marquee(heroSystem);
        heroSystem.setPadding(dp(8), 0, 0, dp(4));
        info.addView(heroSystem);
        heroTitle = text("", 22 * s, Theme.TEXT, false);
        heroTitle.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        heroTitle.setMaxLines(3);
        heroTitle.setPadding(dp(8), 0, 0, 0);
        info.addView(heroTitle);
        ScrollView list = new ScrollView(this);
        list.setVerticalScrollBarEnabled(false);
        list.setVerticalFadingEdgeEnabled(true);
        list.setFadingEdgeLength(dp(28));
        carouselScroll = list;
        carousel = vertical();
        carousel.setPadding(0, dp(2), 0, dp(160));
        fillCarousel(s);
        list.addView(carousel);
        info.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        heroMeta = text("", 12 * s, Theme.MUTED, false);
        Theme.marquee(heroMeta);
        heroMeta.setPadding(dp(8), dp(4), 0, 0);
        info.addView(heroMeta);
        body.addView(info, new LinearLayout.LayoutParams(0, -1, 0.44f));

        // À droite : la fiche du jeu choisi (nom, année, taille, résumé), l'emblème de la console sinon.
        FrameLayout art = new FrameLayout(this);
        heroEmblem = new ConsoleEmblemView(this);
        art.addView(heroEmblem, new FrameLayout.LayoutParams(-1, -1));
        heroSheet = vertical();
        sheetTitle = text("", 20 * s, Theme.TEXT, false);
        sheetTitle.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        sheetTitle.setMaxLines(2);
        sheetTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroSheet.addView(sheetTitle);
        sheetMeta = text("", 12 * s, Theme.CODEC, true);
        sheetMeta.setPadding(0, dp(4), 0, dp(8));
        heroSheet.addView(sheetMeta);
        sheetSummary = text("", 13 * s, Theme.MUTED, false);
        sheetSummary.setLineSpacing(0, 1.15f);
        sheetSummary.setMaxLines(14);
        sheetSummary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroSheet.addView(sheetSummary, new LinearLayout.LayoutParams(-1, 0, 1f));
        sheetSource = text("", 10 * s, Theme.MUTED, false);
        sheetSource.setAlpha(0.7f);
        sheetSource.setPadding(0, dp(4), 0, 0);
        heroSheet.addView(sheetSource);
        // Lisible sur n'importe quelle illustration : panneau sombre translucide et texte ombré.
        heroSheet.setBackground(Theme.pill(0xC80B0D10, dp(18)));
        heroSheet.setPadding(dp(14), dp(12), dp(14), dp(12));
        sheetSummary.setTextColor(0xFFD6DAE0);
        for (TextView t : new TextView[] {sheetTitle, sheetMeta, sheetSummary, sheetSource, heroSystem, heroTitle, heroMeta}) {
            t.setShadowLayer(dp(4), 0, dp(1), 0xE6000000);
        }
        art.addView(heroSheet, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, -1, 0.56f);
        ap.setMargins(dp(16 * s), 0, 0, 0);
        body.addView(art, ap);
        // La disposition XMB vaut avec ou sans manette : rien à réorganiser en plein écran.
        homeHero = null;
        homeArt = art;
        homeInfo = info;
        stageCol.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        stage.addView(stageCol, new FrameLayout.LayoutParams(-1, -1));
        if (store.scanlines()) stage.addView(new Scanlines(), new FrameLayout.LayoutParams(-1, -1));
        // Deux « pilules » aux coins arrondis, séparées par un mince filet au niveau de la charnière.
        col.setBackground(Theme.gap());
        col.setPadding(dp(4), 0, dp(4), 0);
        stage.setBackground(Theme.backdrop(dp(28)));
        stage.setClipToOutline(true);
        col.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1f));
        View gap = new View(this);
        col.addView(gap, new LinearLayout.LayoutParams(-1, dp(4)));

        // Moitié basse : la manette, selon la disposition active (modifiable avec l'icône manette).
        FrameLayout deckBox = new FrameLayout(this);
        ControlDeckView deck = new ControlDeckView(this);
        deck.setSource(store);
        deck.setSystem(ConsolePad.HOME_PAD);
        deck.setKeyListener(new ControlView.Listener() {
            @Override
            public void onDown(String key) {
                padKey(key);
            }

            @Override
            public void onUp(String key) {
            }

            @Override
            public void onAxis(String key, float x, float y) {
                if (key.startsWith("STICK")) stickNav(x, y);
            }
        }, store.haptics());
        deckBox.addView(deck, new FrameLayout.LayoutParams(-1, -1));
        // Rien d'écrit sur la manette : elle reste libre pour les touches.
        statusLine = null;
        col.addView(deckBox, new LinearLayout.LayoutParams(-1, 0, 1f));
        homeCol = col;
        homeDeck = deckBox;
        homeDeckView = deck;
        homeGap = gap;
        homeStage = stage;
        // Par-dessus la colonne : la manette Flex en surimpression ou la Mini flottante.
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(col, new FrameLayout.LayoutParams(-1, -1));
        homeWrap = wrap;
        homeLayoutName = store.activeLayout();
        applyPadHome();
        applyBleed();

        updateFlex(false);
        return wrap;
    }

    // ---------------------------------------------------------------- navigation à la manette physique

    /*
     * Les manettes physiques passent par ici avant toute vue : sinon Android déplace son propre focus
     * entre les boutons de la barre, convertit sticks et croix analogique en touches à sa façon et fait
     * de « rond » un Retour. Ici : une touche = une action, et une direction tenue se répète régulièrement.
     */
    private final android.os.Handler navRepeat = new android.os.Handler(android.os.Looper.getMainLooper());
    private String heldDir;
    private final Runnable repeatDir = new Runnable() {
        public void run() {
            if (heldDir == null) return;
            padKey(heldDir);
            navRepeat.postDelayed(this, 110);
        }
    };

    private void holdDir(String dir) {
        if (dir == null ? heldDir == null : dir.equals(heldDir)) return;
        heldDir = dir;
        navRepeat.removeCallbacks(repeatDir);
        if (dir == null) return;
        padKey(dir);
        navRepeat.postDelayed(repeatDir, 380);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        android.view.InputDevice d = e.getDevice();
        if (!splashDone || !Pads.isPad(d)) return super.dispatchKeyEvent(e);
        if (pads == null) pads = new Pads(this);
        String k = Pads.key(pads.profile(d), e.getKeyCode());
        if (k == null && e.getKeyCode() == KeyEvent.KEYCODE_BACK) k = "B";
        if (k == null) return true;
        boolean dir = k.equals("UP") || k.equals("DOWN") || k.equals("LEFT") || k.equals("RIGHT");
        if (dir) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) holdDir(k);
            else if (e.getAction() == KeyEvent.ACTION_UP && k.equals(heldDir)) holdDir(null);
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) padKey(k);
        return true;
    }

    private String hatDir, stickDirHeld;

    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent e) {
        android.view.InputDevice d = e.getDevice();
        if (!splashDone || !Pads.isPad(d) || e.getActionMasked() != android.view.MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(e);
        }
        float hx = e.getAxisValue(android.view.MotionEvent.AXIS_HAT_X);
        float hy = e.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y);
        String h = hx < -0.5f ? "LEFT" : hx > 0.5f ? "RIGHT" : hy < -0.5f ? "UP" : hy > 0.5f ? "DOWN" : null;
        float x = e.getAxisValue(android.view.MotionEvent.AXIS_X);
        float y = e.getAxisValue(android.view.MotionEvent.AXIS_Y);
        String st = null;
        // Stick : une direction franche (60 %), et on la lâche sous 35 % pour éviter les rebonds.
        float ax = Math.abs(x), ay = Math.abs(y);
        if (Math.max(ax, ay) > (stickDirHeld != null ? 0.35f : 0.6f)) {
            st = ax > ay ? (x > 0 ? "RIGHT" : "LEFT") : (y > 0 ? "DOWN" : "UP");
        }
        hatDir = h;
        stickDirHeld = st;
        holdDir(h != null ? h : st);
        // Gâchettes analogiques : saut de 5 jeux.
        float lt = Math.max(e.getAxisValue(android.view.MotionEvent.AXIS_LTRIGGER), e.getAxisValue(android.view.MotionEvent.AXIS_BRAKE));
        float rt = Math.max(e.getAxisValue(android.view.MotionEvent.AXIS_RTRIGGER), e.getAxisValue(android.view.MotionEvent.AXIS_GAS));
        boolean l2 = lt > 0.6f, r2 = rt > 0.6f;
        if (l2 && !l2Held) padKey("L2");
        if (r2 && !r2Held) padKey("R2");
        l2Held = l2;
        r2Held = r2;
        return true;
    }

    private boolean l2Held, r2Held;

    // ---------------------------------------------------------------- options contrôleur

    private static final int[] OPACITIES = {10, 20, 35, 50, 70};

    /** Icône manette de l'accueil : la manette tactile et les manettes physiques, au même endroit. */
    private void controllerOptions() {
        final Runnable self = new Runnable() {
            public void run() { controllerOptions(); }
        };
        if (pads == null) pads = new Pads(this);
        CodecMenu m = new CodecMenu(this).title("Options contrôleur", "MANETTE TACTILE ET MANETTES PHYSIQUES");
        m.item("Modifier la manette tactile", "Éditeur : touches, disposition, couleurs", new Runnable() {
            public void run() { startActivity(new Intent(MainActivity.this, LayoutEditorActivity.class)); }
        });
        // En paysage, la manette est toujours superposée au jeu en plein écran (0.42.3).
        m.sub("Transparence en superposition", store.overlayOpacity() + " %", new Runnable() {
            public void run() {
                CodecMenu r = new CodecMenu(MainActivity.this).title("Transparence", "MANETTE SUPERPOSÉE").onBack(self);
                for (final int o : OPACITIES) {
                    r.radio(o + " %", o <= 20 ? "Presque invisible" : o >= 50 ? "Bien visible" : null,
                            store.overlayOpacity() == o, new Runnable() {
                                public void run() { store.setOverlayOpacity(o); self.run(); }
                            });
                }
                r.show(CodecMenu.hostFor(MainActivity.this));
            }
        });
        m.check("Gâchettes en arc", "Autour des joysticks, comme X et Y d'une GameCube, plus faciles "
                + "à atteindre. Décoche pour les remettre en haut", store.ergoTriggers(), new Runnable() {
            public void run() {
                store.setErgoTriggers(!store.ergoTriggers());
                ConsolePad.ergo = store.ergoTriggers();
                padChanged();
                self.run();
            }
        });
        m.check("Vibrations", "Retour haptique sur les touches tactiles", store.haptics(), new Runnable() {
            public void run() { store.setHaptics(!store.haptics()); self.run(); }
        });
        m.check("Plein écran avec une manette", "Masquée quand une manette est branchée",
                store.padFullscreen(), new Runnable() {
                    public void run() {
                        store.setPadFullscreen(!store.padFullscreen());
                        padChanged();
                        self.run();
                    }
                });
        m.check("Boutons rapides avec une manette", "SAVE, LOAD et ×2 restent à l'écran en jeu",
                store.padQuickButtons(), new Runnable() {
                    public void run() { store.setPadQuickButtons(!store.padQuickButtons()); self.run(); }
                });
        java.util.List<android.view.InputDevice> now = pads.connected();
        for (android.view.InputDevice d : now) {
            Pads.Profile p = pads.profile(d);
            m.sub(p.name, PadView.familyLabel(PadView.family(d)) + " · joueur " + p.player
                    + " · tester et réattribuer les touches", new Runnable() {
                public void run() { openPads(); }
            });
        }
        m.sub(now.isEmpty() ? "Manettes physiques" : "Toutes les manettes", now.isEmpty()
                ? "Aucune branchée · associe-la en Bluetooth" : "Test des touches, joueurs, zone morte", new Runnable() {
            public void run() { openPads(); }
        });
        m.show(CodecMenu.hostFor(this));
    }

    private void openPads() {
        CodecMenu open = CodecMenu.find(CodecMenu.hostFor(this));
        if (open != null) open.dismiss();
        startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_SECTION, "pads"));
    }

    // ---------------------------------------------------------------- manette physique

    private LinearLayout homeCol, homeHero, homeInfo;
    private View homeDeck, homeGap, homeStage, homeArt;
    private ControlDeckView homeDeckView;
    private FrameLayout homeWrap;
    private FloatingPad homeMini;
    private String homeLayoutName;
    private Pads pads;

    /**
     * Manette physique branchée (et plein écran avec manette activé) : la manette tactile s'efface,
     * la scène prend tout l'écran. Elle revient dès que la dernière manette est débranchée.
     */
    private void applyPadHome() {
        if (homeCol == null || homeDeck == null) return;
        if (pads == null) pads = new Pads(this);
        boolean padOnly = store.padFullscreen() && pads.any();
        // Mini (flottante) et Flex (moitiés sur les bords) : l'accueil passe en plein écran aussi,
        // la manette se pose par-dessus au lieu d'avoir sa moitié d'écran.
        ControlLayout act = null;
        String name = store.activeLayout();
        for (ControlLayout l : store.layouts()) if (l.name.equals(name)) act = l;
        boolean mini = !padOnly && act != null && act.floating();
        // Paysage (écran interne à plat ou externe, pas à moitié plié) : comme en jeu, la manette
        // se superpose à l'accueil en plein écran, seulement ses touches, transparentes.
        Configuration cf = getResources().getConfiguration();
        boolean halfOpen = hinge != null && hingeAngle >= FLEX_MIN_ANGLE && hingeAngle <= FLEX_MAX_ANGLE;
        boolean landscape = cf.screenWidthDp > cf.screenHeightDp && !halfOpen;
        homeOverlayLandscape = landscape;
        boolean flex = !padOnly && act != null && !mini && (act.overlaid() || landscape);
        boolean pad = padOnly || mini || flex;
        placeHomeDeck(mini, flex);
        homeDeck.setVisibility(pad ? View.GONE : View.VISIBLE);
        homeGap.setVisibility(pad ? View.GONE : View.VISIBLE);
        int p = pad ? 0 : dp(4);
        // Paysage / plein écran : aucune pastille ; portrait : pastille ronde sous la barre d'état.
        homeCol.setPadding(p, pad || !Theme.bleed ? 0 : Theme.INSETS[1], p, 0);
        if (homeStageCol != null) homeStageCol.setPadding(0, pad && Theme.bleed ? Theme.INSETS[1] : 0, 0, 0);
        homeStage.setBackground(pad ? Theme.backdrop(0) : Theme.backdrop(dp(28)));
        // Plein écran : la fiche du jeu en haut sur l'illustration, le carrousel en bas comme un dock.
        if (homeHero != null) {
            homeHero.setOrientation(pad ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            homeArt.setLayoutParams(pad ? new LinearLayout.LayoutParams(-1, 0, 0.56f)
                    : new LinearLayout.LayoutParams(0, -1, 0.36f));
            LinearLayout.LayoutParams ip = pad ? new LinearLayout.LayoutParams(-1, 0, 0.44f)
                    : new LinearLayout.LayoutParams(0, -1, 0.64f);
            homeInfo.setLayoutParams(ip);
            homeInfo.setPadding(pad ? 0 : dp(16), pad ? dp(12) : 0, 0, 0);
        }
    }

    /**
     * Range la manette de l'accueil : dans sa moitié d'écran, en surimpression sur tout l'écran (Flex),
     * ou dans un petit carré posé en bas à droite, à la place gardée en jeu (Mini).
     */
    private void placeHomeDeck(boolean mini, boolean flex) {
        if (homeDeckView == null || homeWrap == null) return;
        ControlDeckView deck = homeDeckView;
        if (deck.getParent() != null) ((android.view.ViewGroup) deck.getParent()).removeView(deck);
        if (homeMini != null && homeMini.getParent() != null) ((android.view.ViewGroup) homeMini.getParent()).removeView(homeMini);
        if (flex) {
            // Sous la barre du haut, pour que L2 et R2 ne cachent pas ses boutons.
            FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -1);
            fl.topMargin = dp(72);
            homeWrap.addView(deck, fl);
        } else if (mini) {
            // Fenêtre flottante comme en jeu : poignée en haut pour la déplacer, coins pour la taille.
            if (homeMini == null) homeMini = new FloatingPad(this, store);
            homeMini.content().addView(deck, new FrameLayout.LayoutParams(-1, -1));
            homeWrap.addView(homeMini);
            homeMini.place();
        } else {
            ((FrameLayout) homeDeck).addView(deck, new FrameLayout.LayoutParams(-1, -1));
        }
        // Superposée : comme en jeu, la disposition suit la taille de la zone (colonnes contre les
        // bords sur écran large, via ControlDeckView.reloadFromSource).
        deck.setFollowScreen(flex);
        deck.setLayout(store.currentLayout(Math.max(1, deck.columns()), Math.max(1, deck.rows())));
        deck.setOverlay(flex || mini, store.overlayOpacity());
        if (flex) deck.post(new Runnable() { public void run() { if (homeDeckView != null) homeDeckView.reloadFromSource(); } });
    }

    private boolean homeOverlayLandscape;

    // ---------------------------------------------------------------- illustration de fond

    private android.widget.ImageView backdropView;
    private Game backdropGame;
    private Backdrop backdrop;
    private final Runnable backdropIdle = new Runnable() {
        public void run() { loadBackdrop(); }
    };

    /** Le curseur bouge : l'illustration attend qu'il se pose (600 ms) avant de changer. */
    private void scheduleBackdrop() {
        if (backdropView == null) return;
        backdropView.removeCallbacks(backdropIdle);
        if (!store.backdrops() || shown.isEmpty()) {
            backdropView.animate().alpha(0f).setDuration(300).start();
            backdropGame = null;
            return;
        }
        Game g = shown.get(Math.min(selected, shown.size() - 1));
        if (g == backdropGame) return;
        if (g.isApp()) {
            // Pas d'illustration pour une application : la scène garde le fond du thème.
            backdropView.animate().alpha(0f).setDuration(300).start();
            backdropGame = g;
            return;
        }
        if (backdrop == null) backdrop = new Backdrop(this, coverFetcher);
        // Déjà en mémoire : tout de suite, sinon après un court arrêt.
        if (backdrop.cached(g) != null) loadBackdrop();
        else backdropView.postDelayed(backdropIdle, 600);
    }

    private void loadBackdrop() {
        if (backdropView == null || shown.isEmpty()) return;
        final Game g = shown.get(Math.min(selected, shown.size() - 1));
        backdropGame = g;
        backdrop.load(g, store.autoCovers(), new Backdrop.Callback() {
            public void onBackdrop(Game game, final android.graphics.Bitmap b) {
                if (backdropView == null || game != backdropGame) return;
                if (b == null) {
                    backdropView.animate().alpha(0f).setDuration(300).start();
                    return;
                }
                // Fondu : l'ancienne image s'efface, la nouvelle arrive avec un léger zoom.
                backdropView.animate().alpha(0f).setDuration(backdropView.getAlpha() > 0 ? 160 : 0)
                        .withEndAction(new Runnable() {
                            public void run() {
                                backdropView.setImageBitmap(b);
                                backdropView.setScaleX(1.06f);
                                backdropView.setScaleY(1.06f);
                                backdropView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(520)
                                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                            }
                        }).start();
            }
        });
    }

    private final android.hardware.input.InputManager.InputDeviceListener padListener =
            new android.hardware.input.InputManager.InputDeviceListener() {
                public void onInputDeviceAdded(int id) {
                    android.view.InputDevice d = android.view.InputDevice.getDevice(id);
                    if (!Pads.isPad(d)) return;
                    if (pads == null) pads = new Pads(MainActivity.this);
                    Pads.Profile p = pads.profile(d);
                    PadBanner.show(root, "MANETTE CONNECTÉE", p.name + " · joueur " + p.player, true);
                    padChanged();
                }

                public void onInputDeviceRemoved(int id) {
                    if (pads == null) pads = new Pads(MainActivity.this);
                    PadBanner.show(root, "MANETTE DÉCONNECTÉE",
                            pads.any() || !store.padFullscreen() ? null : "Manette tactile de retour", false);
                    padChanged();
                }

                public void onInputDeviceChanged(int id) {
                }
            };

    private void padChanged() {
        if (!splashDone || root == null) return;
        PadBanner.animateLayout(root);
        // Sur un écran qui n'était pas en Flex (téléphone, tablette), la manette change toute la mise en page.
        if (computePosture() != posture) rebuild();
        else if (homeCol != null) applyPadHome();
    }

    private int stickDir = -1;

    /** Hors jeu, un joystick sert de croix : une direction par poussée franche. */
    private void stickNav(float x, float y) {
        int dir = -1;
        if (Math.abs(x) > 0.6f || Math.abs(y) > 0.6f) {
            dir = Math.abs(x) > Math.abs(y) ? (x > 0 ? 1 : 3) : (y > 0 ? 2 : 0);
        }
        if (dir == stickDir) return;
        stickDir = dir;
        if (dir == 0) padKey("UP");
        else if (dir == 1) padKey("RIGHT");
        else if (dir == 2) padKey("DOWN");
        else if (dir == 3) padKey("LEFT");
    }

    /** Touches de la manette hors jeu : naviguer dans la bibliothèque. */
    private void padKey(String key) {
        if (menuKey(key)) return;
        // Barre du haut : on y monte avec ▲ depuis le premier jeu (ou une liste vide).
        if (barFocus >= 0) {
            barKey(key);
            return;
        }
        if (key.equals("UP") && (shown.isEmpty() || selected == 0) && !barButtons.isEmpty()) {
            barFocus = barButtons.size() - 1;
            paintBarFocus();
            haptic();
            status("▲ · barre du haut");
            return;
        }
        // La croix ne fait que naviguer : les consoles se changent avec L1/R1.
        if (key.equals("UP")) step(-1, "▲");
        else if (key.equals("DOWN")) step(1, "▼");
        // Mode Tsuru (XMB) : ◀ ▶ changent de catégorie, ▲ ▼ de jeu.
        else if (key.equals("LEFT")) {
            if (posture == Posture.FLEX) cycleFilter(-1, "◀");
            else step(-1, "◀");
        } else if (key.equals("RIGHT")) {
            if (posture == Posture.FLEX) cycleFilter(1, "▶");
            else step(1, "▶");
        }
        else if (key.equals("A") || key.equals("START")) launchSelected();
        else if (key.equals("B")) back();
        else if (key.equals("X")) {
            if (!shown.isEmpty()) launchOptions(shown.get(selected));
        }
        else if (key.equals("Y")) toggleFavSelected();
        else if (key.equals("L")) cycleFilter(-1, "L");
        else if (key.equals("R")) cycleFilter(1, "R");
        else if (key.equals("L2")) jump(-5, "L2");
        else if (key.equals("R2")) jump(5, "R2");
        else if (key.equals("HOME")) homePressed();
        else if (key.equals("SELECT")) {
            filter = filter.equals("fav") ? "all" : "fav";
            selected = 0;
            rebuild();
            status("SELECT · " + filterLabel(filter));
        }
    }

    // ---------------------------------------------------------------- barre du haut à la manette

    /** Boutons de la barre du haut, de gauche à droite, et celui qui a le cadre (-1 : aucun). */
    private final List<View> barButtons = new ArrayList<View>();
    private int barFocus = -1;

    /** ◀ ▶ passent d'une icône à l'autre, Ⓐ l'ouvre, Ⓑ ou ▼ rendent la main à la bibliothèque. */
    private void barKey(String key) {
        if (key.equals("LEFT") || key.equals("RIGHT")) {
            int next = Math.max(0, Math.min(barButtons.size() - 1, barFocus + (key.equals("LEFT") ? -1 : 1)));
            if (next != barFocus) {
                barFocus = next;
                UiSound.play(this, UiSound.MOVE);
                haptic();
                paintBarFocus();
            }
            status(key.equals("LEFT") ? "◀" : "▶");
        } else if (key.equals("A") || key.equals("START")) {
            if (barFocus < barButtons.size()) barButtons.get(barFocus).performClick();
        } else if (key.equals("B") || key.equals("DOWN")) {
            barFocus = -1;
            paintBarFocus();
            status("▼ · bibliothèque");
        } else if (key.equals("HOME")) {
            homePressed();
        }
    }

    /** Cadre à l'accent du thème autour de l'icône choisie ; les autres gardent leur effet d'appui. */
    private void paintBarFocus() {
        if (barFocus >= barButtons.size()) barFocus = barButtons.size() - 1;
        for (int i = 0; i < barButtons.size(); i++) {
            View b = barButtons.get(i);
            if (i == barFocus) {
                android.graphics.drawable.GradientDrawable f = new android.graphics.drawable.GradientDrawable();
                f.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                f.setColor(Theme.alpha(Theme.CODEC, 0x26));
                f.setStroke(dp(2), Theme.CODEC);
                b.setForeground(f);
            } else {
                b.setForeground(null);
            }
        }
    }

    // ---------------------------------------------------------------- actions de la manette

    /** ◀ ▶ : jeu précédent ou suivant, sans boucler (le début reste le début). */
    private void step(int dir, String key) {
        if (shown.isEmpty()) {
            status(key + " · aucun jeu");
            return;
        }
        int target = selected + dir;
        if (target < 0 || target >= shown.size()) {
            status(key + " · " + (target < 0 ? "début" : "fin") + " de la liste");
            return;
        }
        select(target);
        status(key + " · " + shown.get(selected).title);
    }

    /** L2 / R2 : saut de cinq jeux, arrêté aux extrémités. */
    private void jump(int dir, String key) {
        if (shown.isEmpty()) return;
        select(selected + dir);
        status(key + " · " + shown.get(selected).title);
    }

    private void select(int i) {
        if (shown.isEmpty()) return;
        int before = selected;
        selected = Math.max(0, Math.min(shown.size() - 1, i));
        if (selected != before) UiSound.play(this, UiSound.MOVE);
        haptic();
        updateFlex(true);
    }

    private void cycleFilter(int dir, String key) {
        cycleFilter(dir);
        status(key + " · " + filterLabel(filter) + " (" + shown.size() + ")");
    }

    /** B : retour à « Tous » depuis un filtre. */
    private void back() {
        UiSound.play(this, UiSound.BACK);
        if (!filter.equals("all")) {
            switchFilter("all", -1);
            status("B · retour à Tous");
        } else {
            status("B · déjà sur Tous");
        }
    }

    private Runnable pendingHome;

    /** Maison : un appui ouvre les options d'émulation, deux appuis rapides reviennent au Flex. */
    private void homePressed() {
        if (pendingHome != null) {
            root.removeCallbacks(pendingHome);
            pendingHome = null;
            if (!store.flexView()) {
                store.setFlexView(true);
                rebuild();
            }
            status("⌂⌂ · retour au mode Tsuru");
            return;
        }
        pendingHome = new Runnable() {
            @Override
            public void run() {
                pendingHome = null;
                showEmulationOptions();
            }
        };
        root.postDelayed(pendingHome, 350);
    }

    private void showEmulationOptions() {
        final Game g = shown.isEmpty() ? null : shown.get(selected);
        CodecMenu m = new CodecMenu(this).title("Options", g == null ? "BIBLIOTHÈQUE" : g.system.shortName + " · " + g.title);
        if (g != null) {
            String emu = launcher.emulatorLabel(g);
            m.item("Émulateur " + g.system.shortName, (emu == null ? "À choisir" : emu) + " · changer", new Runnable() {
                public void run() {
                    launcher.pick(g);
                }
            });
        }
        final boolean auto = store.autoCovers();
        m.check("Jaquettes automatiques", "Téléchargées depuis la base libretro", auto, new Runnable() {
            public void run() {
                store.setAutoCovers(!auto);
                if (!auto) fetchCovers();
                showEmulationOptions();
            }
        });
        m.item("Choisir le dossier de jeux", null, new Runnable() { public void run() { pickFolder(); } });
        if (store.treeUri() != null) {
            m.item("Rescanner le dossier", null, new Runnable() { public void run() { scan(Uri.parse(store.treeUri())); } });
        }
        m.item("Personnaliser la manette", "Disposition, teintes, opacité", new Runnable() {
            public void run() {
                startActivity(new Intent(MainActivity.this, LayoutEditorActivity.class));
            }
        });
        m.item("Passer en vue grille", null, new Runnable() {
            public void run() {
                store.setFlexView(false);
                rebuild();
            }
        });
        m.show(menuHost());
        status("⌂ · options");
    }

    /** Le menu s'affiche dans la moitié haute en vue Flex, sur tout l'écran sinon. */
    FrameLayout menuHost() {
        return menuStage != null && menuStage.isAttachedToWindow() ? menuStage : root;
    }

    /** Renvoie vrai si un menu ouvert a pris la touche. */
    private boolean menuKey(String key) {
        CodecMenu m = CodecMenu.find(menuHost());
        if (m == null) m = CodecMenu.find(root);
        if (m == null) m = CodecMenu.find((FrameLayout) findViewById(android.R.id.content));
        return m != null && m.key(key);
    }

    private void status(String msg) {
        if (statusLine != null) statusLine.setText(msg);
    }

    private void updateFlex(boolean animate) {
        if (heroEmblem == null) return;
        if (library.isEmpty() && !appFilter()) {
            heroSystem.setText("BIBLIOTHÈQUE");
            heroTitle.setVisibility(View.VISIBLE);
            heroTitle.setText(scanning ? "Lecture du dossier…" : "Choisis ton dossier de jeux");
            heroMeta.setText("Appuie sur Ⓐ ou l'icône dossier : Origami range tes jeux par console.");
            heroEmblem.setVisibility(View.INVISIBLE);
            heroSheet.setVisibility(View.GONE);
            return;
        }
        if (shown.isEmpty()) {
            heroSystem.setText(filterLabel(filter).toUpperCase());
            heroTitle.setVisibility(View.VISIBLE);
            heroTitle.setText("Rien ici pour l'instant");
            heroMeta.setText("◀ ▶ change de catégorie, Ⓨ ajoute un favori.");
            SystemInfo fs = SystemInfo.byId(filter);
            heroEmblem.setSystem(fs);
            heroEmblem.setVisibility(fs != null ? View.VISIBLE : View.INVISIBLE);
            heroSheet.setVisibility(View.GONE);
            return;
        }
        Game g = shown.get(selected);
        scheduleBackdrop();
        boolean fav = store.isFavorite(g);
        heroEmblem.setVisibility(View.INVISIBLE);
        heroSheet.setVisibility(View.VISIBLE);
        if (g.isApp()) {
            showSheet(g, null);
        } else {
            showSheet(g, gameInfo.cached(g));
            gameInfo.fetch(g, store.autoInfo(), new GameInfo.Callback() {
                @Override
                public void onSheet(Game game, GameInfo.Sheet sheet) {
                    if (heroSheet != null && !shown.isEmpty() && shown.get(selected) == game) showSheet(game, sheet);
                }
            });
        }
        // L'en-tête dit la catégorie affichée, et la console du jeu quand on est dans « Tous ».
        if (g.group != null) heroSystem.setText(filterLabel(filter).toUpperCase() + " › " + MobileApps.groupName(g.group).toUpperCase());
        else heroSystem.setText(filter.equals(g.system.id) || g.system == SystemInfo.ACTION ? filterLabel(filter).toUpperCase()
                : filterLabel(filter).toUpperCase() + " › " + g.system.name.toUpperCase());
        heroTitle.setVisibility(View.GONE);
        String emu = g.isApp() ? null : launcher.emulatorLabel(g);
        String meta = (fav ? "★ Favori · " : "") + (selected + 1) + "/" + shown.size();
        if (emu != null) meta += " · " + emu;
        heroMeta.setText(meta);
        if (animate) {
            heroSheet.setAlpha(0.4f);
            heroSheet.animate().alpha(1f).setDuration(160).start();
        }
        for (int i = 0; i < carousel.getChildCount(); i++) {
            LinearLayout card = (LinearLayout) carousel.getChildAt(i);
            CartridgeView cv = (CartridgeView) card.getChildAt(0);
            TextView name = (TextView) card.getChildAt(1);
            boolean on = i == selected;
            cv.setPicked(on);
            if (on) cv.setGame(g, fav);
            name.setTextColor(on ? Theme.CODEC : 0xFFE4E7EB);
            name.setTypeface(Typeface.create(Typeface.SANS_SERIF, on ? Typeface.BOLD : Typeface.NORMAL));
            name.setShadowLayer(dp(4), 0, dp(1), 0xF0000000);
            // Le jeu choisi grandit, les autres restent en retrait.
            float k = on ? 1.45f : 1f;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) cv.getLayoutParams();
            int w = Math.round(dp(46 * flexScale()) * k), h = Math.round(dp(58 * flexScale()) * k);
            if (lp.width != w) {
                lp.width = w;
                lp.height = h;
                cv.setLayoutParams(lp);
            }
            card.animate().alpha(on ? 1f : 0.55f).setDuration(animate ? 160 : 0).start();
        }
        final View child = carousel.getChildAt(selected);
        if (child != null) {
            carouselScroll.post(new Runnable() {
                @Override
                public void run() {
                    // Le jeu choisi se cale en haut de la colonne, juste sous les catégories.
                    int y = child.getTop() - dp(4);
                    carouselScroll.smoothScrollTo(0, Math.max(0, y));
                }
            });
        }
    }

    /** Colonne des jeux de la catégorie choisie (vue Tsuru). */
    private void fillCarousel(float s) {
        carousel.removeAllViews();
        for (int i = 0; i < shown.size(); i++) {
            final int index = i;
            Game g = shown.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(8), dp(5), dp(8), dp(5));
            CartridgeView cv = new CartridgeView(this);
            cv.setShowTitle(false);
            cv.setKeepAspect(true);
            cv.setGame(g, store.isFavorite(g));
            covers.bind(cv, g);
            card.addView(cv, new LinearLayout.LayoutParams(dp(46 * s), dp(58 * s)));
            TextView name = text(g.title, 14 * s, Theme.TEXT, false);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setPadding(dp(12), 0, 0, 0);
            card.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            card.setFocusable(false);
            card.setDefaultFocusHighlightEnabled(false);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (index == selected) launchSelected();
                    else select(index);
                }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    select(index);
                    launchOptions(shown.get(index));
                    return true;
                }
            });
            carousel.addView(card, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    /**
     * Change de catégorie sans reconstruire l'écran : les icônes glissent et grandissent sur place,
     * la colonne des jeux s'efface d'un côté et revient de l'autre. (Avant 0.42, tout l'écran était
     * refait : la rangée repartait du début puis sautait, d'où l'animation hachée.)
     */
    private void switchFilter(String key, int dir) {
        if (key.equals(filter)) return;
        filter = key;
        selected = 0;
        haptic();
        if (posture != Posture.FLEX || cats == null || carousel == null) {
            rebuild();
            return;
        }
        refilter();
        updateCategories(true);
        final float s = flexScale();
        final int shift = dp(28) * (dir == 0 ? 1 : dir);
        carousel.animate().cancel();
        carousel.animate().alpha(0f).translationX(-shift).setDuration(90).withEndAction(new Runnable() {
            public void run() {
                if (carousel == null) return;
                fillCarousel(s);
                carouselScroll.scrollTo(0, 0);
                carousel.setTranslationX(shift);
                updateFlex(false);
                scheduleBackdrop();
                carousel.animate().alpha(1f).translationX(0f).setDuration(180)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }
        }).start();
    }

    /** Icônes des catégories : la choisie à pleine taille et nommée, les autres réduites. */
    private void updateCategories(final boolean animate) {
        if (cats == null) return;
        View chosen = null;
        for (int i = 0; i < cats.getChildCount(); i++) {
            LinearLayout cell = (LinearLayout) cats.getChildAt(i);
            boolean on = filter.equals(cell.getTag());
            View icon = cell.getChildAt(0);
            View label = cell.getChildAt(1);
            float k = on ? 1f : 0.76f;
            if (icon instanceof android.widget.ImageView
                    && ((android.widget.ImageView) icon).getDrawable() instanceof Icons) {
                ((Icons) ((android.widget.ImageView) icon).getDrawable()).setColor(on ? Theme.CODEC : Theme.TEXT);
            }
            long d = animate ? 200 : 0;
            icon.animate().scaleX(k).scaleY(k).setDuration(d).start();
            label.animate().alpha(on ? 1f : 0f).setDuration(d).start();
            cell.animate().alpha(on ? 1f : 0.55f).setDuration(d).start();
            if (on) chosen = cell;
        }
        if (chosen != null) {
            final View c = chosen;
            catScroll.post(new Runnable() {
                public void run() {
                    int x = Math.max(0, c.getLeft() - dp(16));
                    if (animate) catScroll.smoothScrollTo(x, 0);
                    else catScroll.scrollTo(x, 0);
                }
            });
        }
    }

    private float flexScale() {
        return getResources().getConfiguration().screenWidthDp < 520 ? 0.78f : 1f;
    }

    /** Rangée des catégories façon XMB : icône, et le nom sous celle qui est choisie. */
    private View categoryRow(float s) {
        catScroll = new HorizontalScrollView(this);
        catScroll.setHorizontalScrollBarEnabled(false);
        cats = new LinearLayout(this);
        cats.setPadding(dp(16 * s), dp(6), dp(400), 0);
        final List<String> keys = filterKeys();
        View chosen = null;
        for (final String key : keys) {
            boolean on = key.equals(filter);
            LinearLayout cell = vertical();
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(10), dp(4), dp(10), dp(4));
            View icon;
            SystemInfo sys = SystemInfo.byId(key);
            if (sys != null) {
                ConsoleEmblemView e = new ConsoleEmblemView(this);
                e.setSystem(sys);
                icon = e;
            } else {
                android.widget.ImageView iv = new android.widget.ImageView(this);
                iv.setImageDrawable(Icons.of(this, key.equals("fav") ? "star" : key.equals("recent") ? "clock" : "library",
                        on ? Theme.CODEC : Theme.TEXT, 40 * s));
                icon = iv;
            }
            // Même place pour toutes : la choisie grandit à l'échelle, sans décaler ses voisines.
            int size = Math.round(dp(50 * s));
            cell.addView(icon, new LinearLayout.LayoutParams(size, size));
            icon.setScaleX(on ? 1f : 0.76f);
            icon.setScaleY(on ? 1f : 0.76f);
            cell.setTag(key);
            TextView label = text(filterLabel(key).replace("★ ", ""), 11 * s, on ? Theme.CODEC : Theme.TEXT, false);
            label.setTypeface(Fonts.wide(this));
            label.setLetterSpacing(0.12f);
            Theme.marquee(label);
            label.setPadding(0, dp(4), 0, 0);
            label.setShadowLayer(dp(4), 0, dp(1), 0xE6000000);
            label.setAlpha(on ? 1f : 0f);
            cell.addView(label);
            cell.setAlpha(on ? 1f : 0.55f);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<String> k = filterKeys();
                    switchFilter(key, Integer.signum(k.indexOf(key) - k.indexOf(filter)));
                }
            });
            cats.addView(cell, new LinearLayout.LayoutParams(-2, -1));
            if (on) chosen = cell;
        }
        catScroll.addView(cats);
        final View c = chosen;
        if (c != null) {
            catScroll.post(new Runnable() {
                public void run() { catScroll.scrollTo(Math.max(0, c.getLeft() - dp(16)), 0); }
            });
        }
        return catScroll;
    }

    /** Fiche à gauche : nom, console · année · taille, résumé et sa source. */
    private void showSheet(Game g, GameInfo.Sheet sheet) {
        if (g.isApp()) {
            sheetTitle.setText(g.title);
            sheetMeta.setText(g.system == SystemInfo.ACTION ? "ACTION" : g.system.name);
            sheetSummary.setText(g.note);
            sheetSource.setText("");
            return;
        }
        boolean found = sheet != null && sheet.found;
        sheetTitle.setText(found && sheet.title != null ? sheet.title : g.title);
        StringBuilder meta = new StringBuilder(g.system.shortName);
        if (found && sheet.year > 0) meta.append(" · ").append(sheet.year);
        if (g.size > 0) meta.append(" · ").append(android.text.format.Formatter.formatShortFileSize(this, g.size));
        sheetMeta.setText(meta);
        if (found && sheet.summary != null && !sheet.summary.isEmpty()) {
            sheetSummary.setText(sheet.summary);
            boolean wiki = sheet.source != null && sheet.source.startsWith("Wikipédia");
            sheetSource.setText((sheet.synopsis ? "Synopsis : " : "Présentation : ") + sheet.source
                    + (wiki ? " · CC BY-SA" : ""));
        } else {
            sheetSummary.setText(sheet == null && store.autoInfo() ? "Recherche de la fiche…"
                    : "Pas de fiche trouvée pour ce jeu.");
            sheetSource.setText("");
        }
    }

    private void launchSelected() {
        if (library.isEmpty() && !appFilter()) {
            status("Ⓐ · choix du dossier");
            pickFolder();
            return;
        }
        if (shown.isEmpty()) {
            status("Ⓐ · rien à lancer ici");
            return;
        }
        Game g = shown.get(selected);
        status("Ⓐ · lancement de " + g.title);
        open(g);
    }

    /**
     * Appui long sur un jeu (X à la manette) : profil d'émulation de ce jeu, profil par défaut,
     * favori, puis lancer.
     */
    void launchOptions(final Game g) {
        if (g.uri.startsWith("action:")) {
            open(g);
            return;
        }
        if (g.isApp()) {
            CodecMenu m = new CodecMenu(this).title(g.title, g.system.name.toUpperCase())
                    .item(g.uri.startsWith("play:") ? "Installer" : "Ouvrir", g.note, new Runnable() {
                        public void run() { open(g); }
                    });
            final String key = g.group != null && g.uri.startsWith("app:") ? MobileApps.customKey(g.group, g.uri) : null;
            if (key != null && store.remoteClients().contains(key)) {
                m.item("Retirer de Remote Play", "L'app reste installée sur le téléphone", new Runnable() {
                    public void run() {
                        store.setRemoteClient(key, false);
                        appsSig = "";
                        loadApps();
                    }
                });
            }
            if (g.uri.startsWith("app:")) {
                m.item("Infos sur l'appli", "Stockage, autorisations, désinstallation", new Runnable() {
                    public void run() { MobileApps.details(MainActivity.this, g); }
                });
            }
            m.check("Favori", null, store.isFavorite(g), new Runnable() {
                public void run() {
                    store.toggleFavorite(g);
                    if (filter.equals("fav")) rebuild();
                    else updateFlex(false);
                    launchOptions(g);
                }
            }).show(menuHost());
            return;
        }
        final String id = Store.gameId(g.uri, g.path);
        final String prof = store.gameProfile(id);
        final boolean isDefault = prof.equals(store.defaultProfile());
        new CodecMenu(this).title(g.title, "OPTIONS DE LANCEMENT")
                .item("Lancer", g.system.name + " · profil " + CoreProfiles.name(prof), new Runnable() {
                    public void run() { launcher.launch(g); }
                })
                .item("Choisir la jaquette", "Une image du téléphone, pour toutes les consoles", new Runnable() {
                    public void run() {
                        coverFor = g;
                        try {
                            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQ_COVER);
                        } catch (Exception e) {
                            toast("Aucun sélecteur d'images sur cet appareil.");
                        }
                    }
                })
                .choice("Profil", CoreProfiles.IDS, CoreProfiles.NAMES,
                        CoreProfiles.HELP, CoreProfiles.index(prof), new CodecMenu.Pick() {
                            public void picked(int i) {
                                store.setGameProfile(id, CoreProfiles.IDS[i]);
                                launchOptions(g);
                            }
                        })
                .check("Profil par défaut", isDefault ? "Les autres jeux démarrent aussi en " + CoreProfiles.name(prof)
                        : "Par défaut : " + CoreProfiles.name(store.defaultProfile()) + " · cocher pour prendre "
                        + CoreProfiles.name(prof), isDefault, new Runnable() {
                    public void run() {
                        if (!isDefault) store.setDefaultProfile(prof);
                        launchOptions(g);
                    }
                })
                .check("Favori", null, store.isFavorite(g), new Runnable() {
                    public void run() {
                        store.toggleFavorite(g);
                        if (filter.equals("fav")) rebuild();
                        else updateFlex(false);
                        launchOptions(g);
                    }
                })
                .show(menuHost());
    }

    private void toggleFavSelected() {
        if (shown.isEmpty()) {
            status("Y · aucun jeu");
            return;
        }
        if (shown.get(selected).uri.startsWith("action:")) return;
        boolean now = store.toggleFavorite(shown.get(selected));
        haptic();
        if (filter.equals("fav")) rebuild();
        else updateFlex(false);
        status("Y · " + (now ? "ajouté aux favoris" : "retiré des favoris"));
    }

    // ---------------------------------------------------------------- manette physique

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        String mk = menuKeyName(keyCode);
        if (mk != null && menuKey(mk)) return true;
        ColorSheet sheet = ColorSheet.find(this);
        if (sheet != null && "B".equals(mk)) {
            sheet.close();
            return true;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_L1:
                cycleFilter(-1, "L1");
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                cycleFilter(1, "R1");
                return true;
            default:
                break;
        }
        if (posture != Posture.FLEX) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                step(-1, "◀");
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                step(1, "▶");
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                step(-1, "▲");
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                step(1, "▼");
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                cycleFilter(1, "X");
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
                back();
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                launchSelected();
                return true;
            case KeyEvent.KEYCODE_BUTTON_Y:
                toggleFavSelected();
                return true;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                filter = filter.equals("fav") ? "all" : "fav";
                selected = 0;
                rebuild();
                return true;
            case KeyEvent.KEYCODE_BUTTON_MODE:
                homePressed();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private static String menuKeyName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return "UP";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "DOWN";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "RIGHT";
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER: return "A";
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BACK: return "B";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "HOME";
            default: return null;
        }
    }

    // ---------------------------------------------------------------- barre du haut

    private View topBar() {
        barButtons.clear();
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(10), dp(8), dp(6));
        LinearLayout titles = vertical();
        TextView logo = text("ORIGAMI", 20, Theme.CODEC, false);
        logo.setTypeface(Fonts.logo(this));
        postureTag = text(postureText(), 11, Theme.MUTED, true);
        android.widget.ImageView mark = new android.widget.ImageView(this);
        Crane.Logo crane = new Crane.Logo();
        mark.setImageDrawable(crane);
        // Le logo est un bouton (applications) : un reflet le traverse régulièrement (0.62).
        crane.animate(mark, 5000);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(40), dp(40));
        mlp.rightMargin = dp(10);
        // Le logo ouvre les applications du téléphone (0.60.4, à la place du bouton « Applications »).
        mark.setContentDescription("Applications");
        mark.setBackground(Theme.pill(0x00000000, dp(20)));
        mark.setFocusable(true);
        mark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appsMenu();
            }
        });
        bar.addView(mark, mlp);
        barButtons.add(mark);
        titles.addView(logo);
        titles.addView(postureTag);
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        // L'icône montre la vue vers laquelle on bascule.
        final boolean flexOn = posture == Posture.FLEX;
        final boolean onCover = hinge != null && getResources().getConfiguration().smallestScreenWidthDp < 600;
        bar.addView(iconButton(flexOn ? "grid" : "flex",
                flexOn ? "Passer en mode Washi" : "Passer en mode Tsuru", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Sur l'écran externe, choix du moment ; sur l'écran interne, le réglage par défaut.
                if (onCover) {
                    coverTsuru = !flexOn;
                } else {
                    store.setFlexView(!flexOn);
                    openTsuru = false;
                }
                rebuild();
            }
        }));
        bar.addView(iconButton("palette", "Thème", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                themeMenu();
            }
        }));
        bar.addView(iconButton("pads", "Options contrôleur", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controllerOptions();
            }
        }));
        bar.addView(iconButton("settings", "Réglages", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        }));
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child instanceof android.widget.ImageView && child.isClickable()) barButtons.add(child);
        }
        paintBarFocus();
        return bar;
    }

    /** Les cinq thèmes, puis les couleurs du logo et de la manette (un seul panneau, 0.61). */
    private void themeMenu() {
        CodecMenu m = new CodecMenu(this).title("Thème", "COULEURS DE L'INTERFACE");
        final boolean sync = store.syncUi();
        String cur = store.theme();
        for (int i = 0; i < Theme.THEMES.length; i++) {
            final String id = Theme.THEMES[i];
            m.radio(Theme.THEME_NAMES[i], Theme.THEME_HELP[i], !sync && id.equals(cur), new Runnable() {
                public void run() {
                    store.setTheme(id);
                    UiSound.refresh(MainActivity.this);
                    store.setSyncUi(false);
                    // L'icône du lanceur change seulement une fois l'app quittée : pas de redémarrage.
                    Theme.queueIcon(MainActivity.this, id);
                    if (Theme.sync(MainActivity.this)) rebuild();
                }
            });
        }
        m.sub("Couleurs du logo et de la manette", store.logoColor() == 0 ? "Logo : suit le thème" : "Logo : couleur perso", new Runnable() {
            public void run() {
                CodecMenu open = CodecMenu.find(CodecMenu.hostFor(MainActivity.this));
                if (open != null) open.dismiss();
                ColorSheet.show(MainActivity.this, MainActivity.this, true, sheetHeight());
            }
        });
        // Choix du pack à part : rouvert avec le store de thèmes communautaires (décision BOSS,
        // 0.60.6). D'ici là, les icônes suivent le thème (iconPackMenu reste prêt).
        m.soon(Icons.of(this, "palette", Theme.MUTED, 24), "Pack d'icônes", "👷‍♂️🚧 Arrive avec le store de thèmes.");
        // Partage de thèmes entre joueurs : annoncé, pas encore ouvert.
        m.soon(Icons.of(this, "share", Theme.MUTED, 24), "Thèmes de la communauté", "👷‍♂️🚧 Partager et récupérer des thèmes");
        m.show(CodecMenu.hostFor(this));
    }

    /** Toutes les applications du téléphone, en menu codec (en attendant le lanceur Washi complet). */
    private void appsMenu() {
        final boolean grid = store.appsGrid();
        CodecMenu m = new CodecMenu(this).title("Applications", "TRI · " + HomeApps.SORTS[store.appsSort()].toUpperCase()).grid(grid)
                .headButton(Icons.of(this, grid ? "menu" : "grid", Theme.CODEC, 22),
                        grid ? "Afficher en liste" : "Afficher en grille", new Runnable() {
                    public void run() {
                        store.setAppsGrid(!grid);
                        appsMenu();
                    }
                }).slider(true)
                .headButton2(Icons.of(this, "sort", Theme.CODEC, 22), "Trier les applications", new Runnable() {
                    public void run() {
                        store.setAppsSort((store.appsSort() + 1) % HomeApps.SORTS.length);
                        toast(HomeApps.SORTS[store.appsSort()]);
                        appsMenu();
                    }
                });
        int px = dp(grid ? 48 : 32);
        for (final HomeApps.App app : HomeApps.apps(this, store.appsSort())) {
            android.graphics.drawable.Drawable d = app.icon(this);
            if (d != null) d.setBounds(0, 0, px, px);
            m.item(d, app.label, null, new Runnable() {
                public void run() { HomeApps.open(MainActivity.this, app); }
            });
        }
        m.show(CodecMenu.hostFor(this));
    }

    /** Packs d'icônes : un par thème, ou celui du thème choisi. */
    private void iconPackMenu() {
        CodecMenu m = new CodecMenu(this).title("Pack d'icônes", "BARRE, XMB ET RÉGLAGES");
        String cur = store.iconPack();
        for (int i = 0; i < Icons.PACKS.length; i++) {
            final String id = Icons.PACKS[i];
            String preview = id.equals("auto") ? Icons.pack() : id;
            String keep = Icons.PACK;
            Icons.PACK = preview;
            android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(dp(28), dp(28), android.graphics.Bitmap.Config.ARGB_8888);
            Icons ic = Icons.of(this, "settings", Theme.TEXT, 28).mark(Theme.CODEC);
            ic.setBounds(0, 0, dp(28), dp(28));
            ic.draw(new android.graphics.Canvas(bm));
            Icons.PACK = keep;
            m.item(new android.graphics.drawable.BitmapDrawable(getResources(), bm),
                    (id.equals(cur) ? "● " : "") + Icons.PACK_NAMES[i], Icons.PACK_HELP[i], new Runnable() {
                public void run() {
                    store.setIconPack(id);
                    Theme.sync(MainActivity.this);
                    rebuild();
                }
            });
        }
        m.onBack(new Runnable() {
            public void run() {
                CodecMenu open = CodecMenu.find(CodecMenu.hostFor(MainActivity.this));
                if (open != null) open.dismiss();
                themeMenu();
            }
        });
        m.show(CodecMenu.hostFor(this));
    }

    /** Hauteur du panneau de couleurs : la moitié haute, la manette restant visible dessous. */
    private int sheetHeight() {
        int h = root.getHeight();
        boolean deckBelow = homeDeck != null && homeDeck.getVisibility() == View.VISIBLE;
        return Math.max(dp(320), deckBelow ? h / 2 - dp(12) : Math.round(h * 0.62f));
    }

    @Override
    public ControlDeckView liveDeck() {
        return homeDeckView;
    }

    @Override
    public void restyled() {
        if (Theme.sync(this)) rebuild();
    }

    private View iconButton(String icon, String description, View.OnClickListener click) {
        android.widget.ImageView b = new android.widget.ImageView(this);
        // Dessinées par Icons : elles suivent le pack d'icônes (Thème > Pack d'icônes).
        b.setImageDrawable(Icons.of(this, icon, Theme.TEXT, 24).mark(Theme.CODEC));
        b.setContentDescription(description);
        b.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        android.content.res.TypedArray a = obtainStyledAttributes(
                new int[] {android.R.attr.selectableItemBackgroundBorderless});
        b.setBackground(a.getDrawable(0));
        a.recycle();
        b.setFocusable(false);
        b.setOnClickListener(click);
        b.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                toast(v.getContentDescription().toString());
                return true;
            }
        });
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return b;
    }

    private void showMenu(View anchor) {
        CodecMenu m = new CodecMenu(this).title("Bibliothèque", null);
        if (store.treeUri() != null) {
            m.item("Rescanner le dossier", null, new Runnable() {
                public void run() { scan(Uri.parse(store.treeUri())); }
            });
        }
        m.item("Oublier les émulateurs choisis", "Redemandé au prochain lancement", new Runnable() {
            public void run() {
                store.clearEmulators();
                toast("L'émulateur sera redemandé au prochain lancement.");
            }
        });
        if (store.treeUri() != null) {
            m.item("Oublier ce dossier", "Origami n'y aura plus accès", new Runnable() {
                public void run() {
                    store.setTreeUri(null);
                    library.clear();
                    filter = "all";
                    selected = 0;
                    rebuild();
                }
            });
        }
        m.show(menuHost());
    }

    private void pickFolder() {
        try {
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_TREE);
        } catch (ActivityNotFoundException e) {
            toast("Sélecteur de dossiers indisponible.");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == REQ_COVER) {
            if (result == RESULT_OK && data != null && data.getData() != null && coverFor != null) setCover(coverFor, data.getData());
            coverFor = null;
            return;
        }
        if (request != REQ_TREE || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // L'accès vaut quand même pour cette session.
        }
        store.setTreeUri(tree.toString());
        scan(tree);
    }

    /** Copie l'image choisie dans le cache des jaquettes : elle remplace celle du jeu, même hors ligne. */
    private void setCover(final Game g, final Uri image) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    java.io.File f = coverFetcher.coverFile(g);
                    f.getParentFile().mkdirs();
                    java.io.InputStream in = getContentResolver().openInputStream(image);
                    java.io.OutputStream out = new java.io.FileOutputStream(f);
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0 && total < (16L << 20)) {
                        out.write(buf, 0, n);
                        total += n;
                    }
                    in.close();
                    out.close();
                    // Adresse nouvelle à chaque choix : le cache d'images ne ressert pas l'ancienne.
                    final String uri = Uri.fromFile(f).toString() + "?v=" + System.currentTimeMillis();
                    runOnUiThread(new Runnable() {
                        public void run() {
                            g.coverUri = uri;
                            toast("Jaquette changée.");
                            if (splashDone) rebuild();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        public void run() { toast("Impossible de lire cette image."); }
                    });
                }
            }
        }, "origami-cover").start();
    }

    // ---------------------------------------------------------------- filtres

    private View filterBar(boolean vertical) {
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        chips.setPadding(dp(12), dp(4), dp(12), dp(8));
        for (final String key : filterKeys()) {
            TextView c = chip(filterLabel(key), key.equals(filter), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    filter = key;
                    selected = 0;
                    rebuild();
                }
            });
            if (vertical) {
                SystemInfo s = SystemInfo.byId(key);
                if (s != null) c.setText(s.name);
                c.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(-1, -2);
                vlp.setMargins(0, dp(4), 0, dp(4));
                chips.addView(c, vlp);
            } else {
                chips.addView(c);
            }
        }
        if (vertical) {
            ScrollView sv = new ScrollView(this);
            sv.addView(chips);
            return sv;
        }
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(chips);
        return hsv;
    }

    private View emptyState() {
        if (library.isEmpty()) {
            LinearLayout box = vertical();
            box.setGravity(Gravity.CENTER);
            box.setPadding(dp(24), dp(24), dp(24), dp(24));
            TextView t = text(scanning ? "Lecture du dossier…" : "Choisis le dossier où sont rangés tes jeux.", 16, Theme.TEXT, false);
            t.setGravity(Gravity.CENTER);
            box.addView(t);
            TextView cta = chip("Choisir mon dossier", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickFolder();
                }
            });
            ((LinearLayout.LayoutParams) cta.getLayoutParams()).topMargin = dp(16);
            box.addView(cta);
            return box;
        }
        TextView t = text(filter.equals("fav")
                ? "Pas de favoris. Appui long sur un jeu pour en ajouter."
                : filter.equals("recent") ? "Aucune partie lancée pour l'instant." : "Rien ici pour l'instant.",
                15, Theme.MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(24), dp(24), dp(24), dp(24));
        return t;
    }

    // ---------------------------------------------------------------- liste et grille

    private void wireClicks(AdapterView<?> view, final GameAdapter adapter) {
        view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                selected = position;
                open(shown.get(position));
            }
        });
        view.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
                selected = position;
                haptic();
                launchOptions(shown.get(position));
                return true;
            }
        });
    }

    private final class GameAdapter extends BaseAdapter {
        private final boolean grid;

        GameAdapter(boolean grid) {
            this.grid = grid;
        }

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Game g = shown.get(position);
            boolean fav = store.isFavorite(g);
            if (grid) {
                CartridgeView cv = convert instanceof CartridgeView ? (CartridgeView) convert : new CartridgeView(MainActivity.this);
                cv.setLayoutParams(new AdapterView.LayoutParams(-1, dp(200)));
                cv.setGame(g, fav);
                covers.bind(cv, g);
                return cv;
            }
            LinearLayout row;
            if (convert instanceof LinearLayout) {
                row = (LinearLayout) convert;
            } else {
                row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(4), dp(4), dp(8), dp(4));
                CartridgeView cv = new CartridgeView(MainActivity.this);
                cv.setShowTitle(false);
                row.addView(cv, new LinearLayout.LayoutParams(dp(60), dp(74)));
                LinearLayout texts = vertical();
                texts.setPadding(dp(12), 0, 0, 0);
                TextView title = text("", 16, Theme.TEXT, false);
                title.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
                Theme.marquee(title);
                texts.addView(title);
                texts.addView(text("", 13, Theme.MUTED, false));
                row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
            }
            CartridgeView cv = (CartridgeView) row.getChildAt(0);
            cv.setGame(g, fav);
            covers.bind(cv, g);
            LinearLayout texts = (LinearLayout) row.getChildAt(1);
            ((TextView) texts.getChildAt(0)).setText(g.title);
            ((TextView) texts.getChildAt(1)).setText(g.system.name + (fav ? "  ★" : ""));
            return row;
        }
    }

    // ---------------------------------------------------------------- petits outils

    /** Lignes de balayage façon écran cathodique, par-dessus la scène du mode Flex. */
    private final class Scanlines extends View {
        private final Paint p = new Paint();

        Scanlines() {
            super(MainActivity.this);
            p.setColor(0x10000000);
        }

        @Override
        protected void onDraw(Canvas c) {
            int step = dp(3);
            for (int y = 0; y < getHeight(); y += step) c.drawRect(0, y, getWidth(), y + dp(1), p);
        }
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String s, float sp, int color, boolean mono) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (mono) t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    private TextView chip(String label, boolean active, View.OnClickListener click) {
        TextView c = text(label, 14, active ? Theme.BG : Theme.TEXT, false);
        c.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        c.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(active ? Theme.CODEC : Theme.PANEL_HI);
        c.setBackground(bg);
        c.setFocusable(false);
        if (click != null) c.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(5), dp(4), dp(5), dp(4));
        c.setLayoutParams(lp);
        return c;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(radius);
        g.setColor(color);
        return g;
    }

    /** Onde de pression visible à chaque appui. */
    private Drawable withRipple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(Theme.alpha(Theme.CODEC, 0x66)), content, null);
    }

    /** Petit enfoncement du bouton, pour sentir l'appui même sans vibration. */
    private void pressFx(View v) {
        haptic();
        v.animate().cancel();
        v.setScaleX(0.88f);
        v.setScaleY(0.88f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
    }

    private void haptic() {
        if (store.haptics()) root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void toast(String msg) {
        I18n.toast(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) Theme.fullscreen(this);
    }
}
