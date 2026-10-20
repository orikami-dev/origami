package com.hideo.foldplay;

import android.graphics.Color;

/** Palette "codec" : fond nuit, vert de transmission, jaune d'alerte. */
final class Theme {
    // Couleurs d'origine (celles de la manette par défaut).
    static final int BG0 = 0xFF0E0F13;
    static final int PANEL0 = 0xFF181A21;
    static final int PANEL_HI0 = 0xFF232632;
    static final int CODEC0 = 0xFF4CFFA0;
    static final int STEEL0 = 0xFF343946;

    // Couleurs en cours : celles d'origine, ou reprises de la manette si l'option est activée.
    static int BG = BG0;
    static int PANEL = PANEL0;
    static int PANEL_HI = PANEL_HI0;
    static int CODEC = CODEC0;
    static int STEEL = STEEL0;
    /** Vrai : les manettes gardent leurs propres couleurs (thème « Couleurs de la manette »). */
    static boolean PAD_OWN;
    /** Couleur du logo choisie (0 : celle du thème). */
    static int LOGO;
    /** Thème en cours, pour le fond assorti. */
    static String THEME_ID = "codec";
    /** Fonds animés en boucle (0.62.2, Réglages > Affichage). */
    static boolean ANIMATE = true;
    static int TEXT = 0xFFF1F1F1;
    static int MUTED = 0xFF8A8F9C;
    /** Accessibilité (Réglages > Accessibilité) : main qui tient le téléphone, gâchettes à bascule, course des joysticks. */
    static boolean RIGHT_HANDED = true, LATCH = false, STRONG_HAPTICS = false;
    static float STICK_REACH = 1f;
    static final int YELLOW = 0xFFFFD23F;
    static final int RED = 0xFFFF5A5F;

    /** Fond séparant les deux moitiés « pilules » de l'écran. */
    static final int GAP = 0xFF050608;

    private Theme() {
    }

    /** Texte sur une ligne qui défile tout seul quand il est trop long, au lieu d'être coupé. */
    static void marquee(android.widget.TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        t.setMarqueeRepeatLimit(-1);
        t.setHorizontalFadingEdgeEnabled(true);
        t.setFadingEdgeLength(Math.round(12 * t.getResources().getDisplayMetrics().density));
        t.setSelected(true);
    }

    /** Rectangle aux coins très arrondis : les deux moitiés de l'écran Flex. */
    static android.graphics.drawable.GradientDrawable pill(int color, float radiusPx) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    /**
     * Fond entre les pilules (0.62.5) : plus de noir franc, un fondu de la couleur du thème,
     * plus marqué en haut et en bas, presque noir au milieu.
     */
    static android.graphics.drawable.Drawable gap() {
        int edge = blend(GAP, CODEC, 0.26f), mid = blend(GAP, CODEC, 0.10f);
        return new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[] {edge, mid, edge});
    }

    /** L'accent du thème avec une transparence donnée (0x00 à 0xFF). */
    static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /**
     * Les icônes vectorielles sont dessinées en deux tons : blanc cassé et vert Codec.
     * Ce filtre garde le blanc et remplace le vert par l'accent du thème en cours.
     * Il s'appuie sur le rouge, très différent entre les deux tons (0xF1 contre 0x4C).
     */
    static android.graphics.ColorFilter accentFilter() {
        float lo = 0x4C, hi = 0xF1, k = 1f / (hi - lo);
        float[] a = {Color.red(CODEC), Color.green(CODEC), Color.blue(CODEC)};
        float w = 0xF1;
        float[] m = new float[20];
        for (int c = 0; c < 3; c++) {
            // sortie = accent + (blanc - accent) * (r - lo) / (hi - lo)
            m[c * 5] = (w - a[c]) * k;
            m[c * 5 + 4] = a[c] - (w - a[c]) * k * lo;
        }
        m[18] = 1f;
        return new android.graphics.ColorMatrixColorFilter(m);
    }

    static int lighten(int color, float amount) {
        return Color.rgb(
                (int) (Color.red(color) + (255 - Color.red(color)) * amount),
                (int) (Color.green(color) + (255 - Color.green(color)) * amount),
                (int) (Color.blue(color) + (255 - Color.blue(color)) * amount));
    }

    /** Texte lisible (sombre ou clair) sur un fond de cette couleur. */
    static int readableOn(int color) {
        double l = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color);
        return l > 150 ? BG : TEXT;
    }

    static int darken(int color, float amount) {
        return Color.rgb(
                (int) (Color.red(color) * (1 - amount)),
                (int) (Color.green(color) * (1 - amount)),
                (int) (Color.blue(color) * (1 - amount)));
    }

    /**
     * Plein écran : barres d'état et de navigation masquées, un glissement du bord les rappelle.
     * Toujours en jeu ; ailleurs seulement si l'option est activée (Réglages > Affichage).
     */
    /** Mode droitier (0.65) : la zone se lit en miroir de la disposition de base, navigation et textes
     *  proches côté pouce. Le sens de lecture du texte lui-même ne change pas. */
    /** Vrai pendant l'animation de démarrage : sous les barres, le fond reste uni (0.65). */
    static boolean splashing;

    /** Fond sous les barres d'état et de navigation : uni pendant le démarrage, puis celui du thème. */
    static void barsBackground(android.app.Activity a, boolean splash) {
        splashing = splash;
        android.view.View content = a.getWindow().getDecorView().findViewById(android.R.id.content);
        if (content == null) return;
        if (splash) content.setBackgroundColor(BG);
        else content.setBackground(backdrop(0));
    }

    static void mirror(android.view.View v) {
        if (!RIGHT_HANDED || v == null) return;
        v.setLayoutDirection(android.view.View.LAYOUT_DIRECTION_RTL);
        v.setTextDirection(android.view.View.TEXT_DIRECTION_LTR);
    }

    @SuppressWarnings("deprecation")
    static void fullscreen(android.app.Activity a) {
        android.view.Window w = a.getWindow();
        // La décoration doit exister avant getInsetsController (sinon NullPointerException dans
        // onCreate, avant setContentView : plantage des moteurs PS3 et 360 en 0.62.1).
        w.getDecorView();
        TouchSound.install(a);
        textScale(a);
        boolean game = a instanceof GameActivity || a instanceof Ps3Activity || a instanceof X360Activity;
        // Paysage : plein écran automatique partout, barres masquées (BOSS, 20/10).
        boolean land = a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (!game && !land && !new Store(a).appFullscreen()) {
            // Fenêtre classique : l'app se redimensionne entre la barre d'état et la barre de
            // navigation, rien n'est caché dessous (ni le haut des menus, ni les manettes).
            w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            // Barres d'état (batterie) et de navigation transparentes (0.65) : le fond de l'app passe
            // dessous, le contenu reste entre les deux grâce à une marge posée sur la zone de contenu.
            w.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            w.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                w.setNavigationBarContrastEnforced(false);
                w.setStatusBarContrastEnforced(false);
            }
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.view.WindowManager.LayoutParams lp = w.getAttributes();
                if (lp.layoutInDisplayCutoutMode != android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT) {
                    lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
                    w.setAttributes(lp);
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                w.setDecorFitsSystemWindows(false);
                final android.view.View content = w.getDecorView().findViewById(android.R.id.content);
                if (content != null) {
                    if (!splashing) content.setBackground(backdrop(0));
                    // Le clavier suit le même chemin : le contenu monte avec lui, image par image, sans barre opaque.
                    content.setWindowInsetsAnimationCallback(new android.view.WindowInsetsAnimation.Callback(
                            android.view.WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                        public android.view.WindowInsets onProgress(android.view.WindowInsets wi,
                                java.util.List<android.view.WindowInsetsAnimation> running) {
                            content.dispatchApplyWindowInsets(wi);
                            return wi;
                        }
                    });
                    content.setOnApplyWindowInsetsListener(new android.view.View.OnApplyWindowInsetsListener() {
                        public android.view.WindowInsets onApplyWindowInsets(android.view.View v, android.view.WindowInsets wi) {
                            android.graphics.Insets b = wi.getInsets(android.view.WindowInsets.Type.systemBars()
                                    | android.view.WindowInsets.Type.displayCutout() | android.view.WindowInsets.Type.ime());
                            // La marge va sur la vue racine de l'activité, pas sur la zone de contenu :
                            // le fond de la racine passe ainsi sous les barres, sans raccord.
                            android.view.View top = ((android.view.ViewGroup) v).getChildCount() > 0
                                    ? ((android.view.ViewGroup) v).getChildAt(0) : null;
                            android.graphics.Insets sys = wi.getInsets(android.view.WindowInsets.Type.systemBars()
                                    | android.view.WindowInsets.Type.displayCutout());
                            INSETS[0] = sys.left;
                            INSETS[1] = sys.top;
                            INSETS[2] = sys.right;
                            INSETS[3] = sys.bottom;
                            // Tsuru : haut et bas sans marge, les pastilles passent sous les barres ; seul le clavier remonte le contenu.
                            int bottom = bleed ? (wi.isVisible(android.view.WindowInsets.Type.ime()) ? b.bottom : 0) : b.bottom;
                            int topPad = bleed ? 0 : b.top;
                            if (top != null) top.setPadding(b.left, topPad, b.right, bottom);
                            else v.setPadding(b.left, topPad, b.right, bottom);
                            Runnable hook = insetsHook;
                            if (hook != null) hook.run();
                            return android.view.WindowInsets.CONSUMED;
                        }
                    });
                    content.requestApplyInsets();
                }
                android.view.WindowInsetsController c = w.getInsetsController();
                if (c != null) c.show(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
            } else {
                w.getDecorView().setSystemUiVisibility(0);
            }
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            android.view.WindowManager.LayoutParams lp = w.getAttributes();
            if (lp.layoutInDisplayCutoutMode != android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
                lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                w.setAttributes(lp);
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
            // Plein écran ou jeu : plus de marge de la fenêtre classique.
            android.view.View content = w.getDecorView().findViewById(android.R.id.content);
            if (content != null) {
                content.setOnApplyWindowInsetsListener(null);
                content.setPadding(0, 0, 0, 0);
                // Retour du portrait : la marge posée sur la vue racine pour les barres disparaît aussi.
                if (!game && content instanceof android.view.ViewGroup && ((android.view.ViewGroup) content).getChildCount() > 0) {
                    ((android.view.ViewGroup) content).getChildAt(0).setPadding(0, 0, 0, 0);
                }
                INSETS[0] = INSETS[1] = INSETS[2] = INSETS[3] = 0;
                Runnable hook = insetsHook;
                if (!game && hook != null) hook.run();
            }
            // Avant setContentView, le DecorView n'existe pas encore et getInsetsController plante
            // (PS3 et Xbox 360 en 0.60.6) : getDecorView() le crée au besoin.
            w.getDecorView();
            android.view.WindowInsetsController c = w.getInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            w.getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    /**
     * Interface aux couleurs de la manette : l'accent (titres, logo, sélection, emblèmes) devient
     * celui de la manette, les panneaux et le fond prennent une touche de sa teinte en restant
     * sombres (le texte blanc reste lisible). Renvoie vrai si quelque chose a changé.
     */
    static boolean sync(android.content.Context ctx) {
        Store store = new Store(ctx);
        Icons.PACK = store.syncUi() && "auto".equals(store.iconPack()) ? "trait" : store.iconPack();
        int bg = BG0, panel = PANEL0, hi = PANEL_HI0, accent = CODEC0, steel = STEEL0;
        int[] t = palette(store.theme());
        if (t != null) {
            bg = t[0];
            panel = t[1];
            hi = t[2];
            accent = t[3];
            steel = t[4];
        }
        if (store.syncUi()) {
            ControlLayout l = null;
            for (ControlLayout k : store.layouts()) if (k.name.equals(store.activeLayout())) l = k;
            if (l != null) {
                accent = l.accentColor;
                steel = l.buttonColor;
                bg = blend(BG0, l.deckColor, 0.18f);
                panel = blend(PANEL0, l.deckColor, 0.28f);
                hi = blend(PANEL_HI0, l.deckColor, 0.32f);
                // Toujours assez sombre pour le texte blanc.
                bg = darkEnough(bg, 0.10f);
                panel = darkEnough(panel, 0.16f);
                hi = darkEnough(hi, 0.22f);
            }
        }
        int logo = store.logoColor();
        String id = store.syncUi() ? "pad" : THEMES[themeIndex(store.theme())];
        boolean changed = bg != BG || panel != PANEL || hi != PANEL_HI || accent != CODEC || steel != STEEL
                || PAD_OWN != store.syncUi() || logo != LOGO || !id.equals(THEME_ID);
        PAD_OWN = store.syncUi();
        LOGO = logo;
        THEME_ID = id;
        ANIMATE = store.animatedBackdrop() && !store.reduceMotion();
        // Tenue à une main désactivée (BOSS, 20/10) : disposition de base partout, à réintégrer plus tard.
        RIGHT_HANDED = false;
        LATCH = store.latchTriggers();
        STRONG_HAPTICS = store.strongHaptics();
        STICK_REACH = new float[] {1f, 0.7f, 0.5f}[Math.max(0, Math.min(2, store.stickReach()))];
        TEXT = store.highContrast() ? 0xFFFFFFFF : 0xFFF1F1F1;
        MUTED = store.highContrast() ? 0xFFC9CDD6 : 0xFF8A8F9C;
        BG = bg;
        PANEL = panel;
        PANEL_HI = hi;
        CODEC = accent;
        STEEL = steel;
        return changed;
    }

    // Couleurs d'une manette : les siennes avec « Couleurs de la manette », sinon celles du thème
    // (fond = panneau, touches = acier, accent = accent), pour que tout reste assorti.
    static int padDeck(ControlLayout l) {
        return PAD_OWN ? l.deckColor : PANEL;
    }

    static int padButton(ControlLayout l) {
        return PAD_OWN ? l.buttonColor : STEEL;
    }

    static int padAccent(ControlLayout l) {
        return PAD_OWN ? l.accentColor : CODEC;
    }

    /** Couleur propre d'une touche, gardée seulement avec les couleurs de la manette. */
    static int keyColor(ControlLayout.Control c) {
        return PAD_OWN ? c.color : 0;
    }

    /** Couleur de la grue : celle choisie, sinon l'accent donné (thème ou manette). */
    static int logo(int fallback) {
        return LOGO != 0 ? LOGO : fallback;
    }

    /**
     * Couleur du pli central de la grue : une par thème (jaune d'alerte pour Codec, givre pour
     * Arctique, braise pour Écarlate, blanc rosé pour Sakura, cuivre pour Ambre). Avec une couleur
     * de logo perso ou « Couleurs de la manette », un ton très clair de cette couleur.
     */
    static int fold() {
        if (LOGO != 0) return lighten(LOGO, 0.7f);
        if (PAD_OWN) return lighten(CODEC, 0.7f);
        return FOLDS[themeIndex(THEME_ID)];
    }

    static final int[] FOLDS = {0xFFFFD23F, 0xFFEAF9FF, 0xFFFFB347, 0xFFFFF0F7, 0xFFFF7A2E};

    /** Curseurs, barres de progression et champs Android aux couleurs du thème (au lieu du vert d'origine). */
    static void tint(android.view.View v) {
        android.content.res.ColorStateList acc = android.content.res.ColorStateList.valueOf(CODEC);
        if (v instanceof android.widget.ProgressBar) {
            android.widget.ProgressBar b = (android.widget.ProgressBar) v;
            b.setProgressTintList(acc);
            b.setIndeterminateTintList(acc);
            b.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(alpha(CODEC, 0x40)));
            b.setSecondaryProgressTintList(android.content.res.ColorStateList.valueOf(alpha(CODEC, 0x70)));
        }
        if (v instanceof android.widget.SeekBar) ((android.widget.SeekBar) v).setThumbTintList(acc);
        if (v instanceof android.widget.CompoundButton) ((android.widget.CompoundButton) v).setButtonTintList(acc);
        if (v instanceof android.widget.EditText) {
            android.widget.EditText e = (android.widget.EditText) v;
            e.setBackgroundTintList(acc);
            e.setHighlightColor(alpha(CODEC, 0x55));
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.graphics.drawable.GradientDrawable cur = new android.graphics.drawable.GradientDrawable();
                cur.setColor(CODEC);
                cur.setSize(Math.round(2 * v.getResources().getDisplayMetrics().density), 1);
                e.setTextCursorDrawable(cur);
            }
        }
    }

    /** Affiche une fenêtre Android, ses boutons à l'accent du thème (le style Material les met en turquoise). */
    static android.app.AlertDialog show(android.app.AlertDialog.Builder b) {
        android.app.AlertDialog d = b.show();
        int[] which = {android.content.DialogInterface.BUTTON_POSITIVE, android.content.DialogInterface.BUTTON_NEGATIVE,
            android.content.DialogInterface.BUTTON_NEUTRAL};
        for (int w : which) {
            android.widget.Button btn = d.getButton(w);
            if (btn != null) btn.setTextColor(CODEC);
        }
        return d;
    }

    /** Fond assorti au thème en cours (coins arrondis de radius px). */
    static android.graphics.drawable.Drawable backdrop(float radius) {
        return new ThemeBackdrop(THEME_ID, BG, CODEC, radius);
    }

    /** Idem, avec les coins du haut droits : l'écran du haut touche la barre d'état (Tsuru). */
    static android.graphics.drawable.Drawable backdropFlushTop(float radius) {
        return new ThemeBackdrop(THEME_ID, BG, CODEC, radius).squareTop(true);
    }

    /** Hauteur de la barre d'état et de la barre de navigation du téléphone, même cachées (plein écran). */
    static int statusBarPx(android.content.Context c) {
        return systemBarPx(c, "status_bar_height", 24);
    }

    static int navBarPx(android.content.Context c) {
        return systemBarPx(c, "navigation_bar_height", 20);
    }

    private static int systemBarPx(android.content.Context c, String name, int fallbackDp) {
        int id = c.getResources().getIdentifier(name, "dimen", "android");
        int px = id > 0 ? c.getResources().getDimensionPixelSize(id) : 0;
        return px > 0 ? px : Math.round(fallbackDp * c.getResources().getDisplayMetrics().density);
    }

    /** Hauteur des barres système (gauche, haut, droite, bas) mesurée par la fenêtre classique. */
    static final int[] INSETS = new int[4];
    /** Vrai en Tsuru : le contenu passe sous les barres, c'est lui qui se marge (pas la racine). */
    static boolean bleed;
    /** Appelé après chaque mesure des barres ; l'accueil y recale ses marges. */
    static Runnable insetsHook;

    /** Les cinq thèmes : identifiant, nom, puis fond, panneau, panneau clair, accent, touches. */
    static final String[] THEMES = {"codec", "arctique", "ecarlate", "sakura", "ambre"};
    static final String[] THEME_NAMES = {"Codec", "Arctique", "Écarlate", "Sakura", "Ambre"};
    static final String[] THEME_HELP = {
        "Le vert de transmission sur fond nuit, l'original.",
        "Bleu glacier sur un fond d'encre, froid et net.",
        "Rouge d'alerte sur fond brun très sombre.",
        "Rose de cerisier sur fond prune, plus doux.",
        "Or chaud sur fond sépia, comme un vieux terminal.",
    };
    private static final int[][] PALETTES = {
        {BG0, PANEL0, PANEL_HI0, CODEC0, STEEL0},
        {0xFF0B1016, 0xFF141C26, 0xFF1E2A38, 0xFF5CD6FF, 0xFF34414F},
        {0xFF120C0D, 0xFF1E1416, 0xFF2B1C1F, 0xFFFF5A4E, 0xFF46353A},
        {0xFF120E12, 0xFF1E171E, 0xFF2A202B, 0xFFFF8AC8, 0xFF443848},
        {0xFF110F0A, 0xFF1D1A12, 0xFF29251A, 0xFFFFC14D, 0xFF45402F},
    };

    /**
     * Icône de l'app aux couleurs du thème choisi : une seule entrée de lanceur (activity-alias
     * « IconCodec », « IconArctique »…) est activée à la fois. Le lanceur peut mettre quelques
     * secondes à la redessiner. Avec « Couleurs de la manette », l'icône garde le dernier thème.
     */
    /**
     * Changer d'entrée de lanceur fait redémarrer l'app sur certains lanceurs (One UI) : le thème
     * s'applique donc tout de suite à l'interface, et l'icône seulement quand l'app passe en
     * arrière-plan hors jeu (dernière activité arrêtée) ou se ferme. Rien ne s'affiche alors à l'écran.
     */
    static void queueIcon(android.content.Context ctx, String themeId) {
        new Store(ctx).setPendingIcon(themeId);
        watchBackground(ctx);
    }

    private static boolean watching;
    private static int started;
    static boolean inGame;

    static void watchBackground(android.content.Context ctx) {
        if (watching) return;
        watching = true;
        final android.app.Application app = (android.app.Application) ctx.getApplicationContext();
        app.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(android.app.Activity a, android.os.Bundle b) {
            }

            public void onActivityStarted(android.app.Activity a) {
                started++;
            }

            public void onActivityResumed(android.app.Activity a) {
            }

            public void onActivityPaused(android.app.Activity a) {
            }

            public void onActivityStopped(android.app.Activity a) {
                started = Math.max(0, started - 1);
                // Rotation : l'activité revient aussitôt, on ne touche à rien.
                if (started == 0 && !a.isChangingConfigurations() && !inGame) flushIcon(app);
            }

            public void onActivitySaveInstanceState(android.app.Activity a, android.os.Bundle b) {
            }

            public void onActivityDestroyed(android.app.Activity a) {
            }
        });
    }

    /** Applique l'icône en attente, s'il y en a une. */
    static void flushIcon(android.content.Context ctx) {
        Store st = new Store(ctx);
        String id = st.pendingIcon();
        if (id == null) return;
        st.setPendingIcon(null);
        applyIcon(ctx, id);
    }

    static void applyIcon(android.content.Context ctx, String themeId) {
        android.content.pm.PackageManager pm = ctx.getPackageManager();
        int want = themeIndex(themeId);
        for (int i = 0; i < THEMES.length; i++) {
            String n = THEMES[i];
            android.content.ComponentName c = new android.content.ComponentName(ctx,
                    ctx.getPackageName() + ".Icon" + Character.toUpperCase(n.charAt(0)) + n.substring(1));
            int state = i == want ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            try {
                if (pm.getComponentEnabledSetting(c) != state) {
                    pm.setComponentEnabledSetting(c, state, android.content.pm.PackageManager.DONT_KILL_APP);
                }
            } catch (Exception ignored) {
                // Entrée absente : l'icône reste celle d'origine.
            }
        }
    }

    static int themeIndex(String id) {
        for (int i = 0; i < THEMES.length; i++) if (THEMES[i].equals(id)) return i;
        return 0;
    }

    static int[] palette(String id) {
        return PALETTES[themeIndex(id)];
    }

    static int blend(int a, int b, float t) {
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }

    private static int darkEnough(int c, float maxLuma) {
        float l = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
        return l <= maxLuma ? c : darken(c, 1f - maxLuma / l);
    }

    /**
     * Écran externe : toujours en portrait, sauf si
     * une manette physique est branchée. L'écran interne tourne librement.
     */
    /** Rotation libre partout, écran externe compris (depuis la 0.26, à la demande de BOSS). */
    /**
     * Textes plus petits sur un écran étroit (écran externe d'un pliant, petit téléphone) : 85 %
     * de la taille choisie dans Android, pour que les menus tiennent sans couper (0.62.5).
     * Appelé à chaque changement d'écran ; les vues créées ensuite prennent la nouvelle taille.
     */
    @SuppressWarnings("deprecation")
    static void textScale(android.content.Context ctx) {
        android.content.res.Resources r = ctx.getResources();
        android.content.res.Configuration c = r.getConfiguration();
        android.util.DisplayMetrics dm = r.getDisplayMetrics();
        float k = c.smallestScreenWidthDp < 480 ? 0.85f : c.smallestScreenWidthDp < 600 ? 0.92f : 1f;
        k *= new float[] {1f, 1.15f, 1.3f}[Math.max(0, Math.min(2, new Store(ctx).a11yText()))];
        dm.scaledDensity = dm.density * c.fontScale * k;
    }

    static void orientation(android.app.Activity a) {
        textScale(a);
        int want = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if (a.getRequestedOrientation() != want) a.setRequestedOrientation(want);
    }
}
