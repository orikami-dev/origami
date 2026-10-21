package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Typeface;

/** Polices embarquées (licence OFL) : Black Ops One pour le logo, Michroma pour les sous-titres. */
final class Fonts {
    private static Typeface logo;
    private static Typeface wide;
    private static String wideTheme;

    private Fonts() {
    }

    static Typeface logo(Context ctx) {
        if (logo == null) logo = load(ctx, "fonts/BlackOpsOne-Regular.ttf", Typeface.DEFAULT_BOLD);
        return logo;
    }

    /** Titres des menus : le pochoir sur Radar, la police du thème en gras ailleurs (0.66.17). */
    static Typeface title(Context ctx) {
        if ("codec".equals(new Store(ctx).theme())) return logo(ctx);
        return Typeface.create(wide(ctx), Typeface.BOLD);
    }

    /** Police des textes du thème en cours (0.66.14) : Michroma pour Radar, une autre par thème. */
    static Typeface wide(Context ctx) {
        String theme = new Store(ctx).theme();
        if (wide == null || !theme.equals(wideTheme)) {
            String file = "Michroma-Regular";
            if ("arctique".equals(theme)) file = "Exo2";
            else if ("ecarlate".equals(theme)) file = "Cinzel";
            else if ("sakura".equals(theme)) file = "Quicksand";
            else if ("ambre".equals(theme)) file = "Marcellus";
            else if ("neon".equals(theme)) file = "Orbitron";
            else if ("jade".equals(theme)) file = "JosefinSans";
            else if ("argent".equals(theme)) file = "Jura";
            wide = load(ctx, "fonts/" + file + ".ttf", Typeface.MONOSPACE);
            wideTheme = theme;
        }
        return wide;
    }

    private static Typeface load(Context ctx, String path, Typeface fallback) {
        try {
            return Typeface.createFromAsset(ctx.getAssets(), path);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
