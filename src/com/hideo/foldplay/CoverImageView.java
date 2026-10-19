package com.hideo.foldplay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

/** Jaquette en vedette : image entière, coins arrondis, ombre portée. */
final class CoverImageView extends ImageView implements CoverLoader.Target {
    CoverImageView(Context ctx) {
        super(ctx);
        setScaleType(ScaleType.FIT_CENTER);
        setAdjustViewBounds(true);
        final float radius = 10 * ctx.getResources().getDisplayMetrics().density;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                if (getDrawable() == null) return;
                // Arrondit le contour de l'image affichée, pas celui de toute la zone.
                android.graphics.RectF b = new android.graphics.RectF(getDrawable().getBounds());
                getImageMatrix().mapRect(b);
                outline.setRoundRect(Math.round(b.left), Math.round(b.top), Math.round(b.right),
                        Math.round(b.bottom), radius);
            }
        });
        setClipToOutline(true);
        setElevation(8 * ctx.getResources().getDisplayMetrics().density);
    }

    @Override
    public void setCover(Bitmap b) {
        setImageBitmap(b);
        invalidateOutline();
    }
}
