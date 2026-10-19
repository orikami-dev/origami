package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Typeface;

/** Polices embarquées (licence OFL) : Black Ops One pour le logo, Michroma pour les sous-titres. */
final class Fonts {
    private static Typeface logo;
    private static Typeface wide;

    private Fonts() {
    }

    static Typeface logo(Context ctx) {
        if (logo == null) logo = load(ctx, "fonts/BlackOpsOne-Regular.ttf", Typeface.DEFAULT_BOLD);
        return logo;
    }

    static Typeface wide(Context ctx) {
        if (wide == null) wide = load(ctx, "fonts/Michroma-Regular.ttf", Typeface.MONOSPACE);
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
