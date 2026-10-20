package com.hideo.foldplay;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Rangée qui passe à la ligne (0.62.3) : les boutons et pastilles des menus ne défilent plus à
 * l'horizontale, ils se rangent sur plusieurs lignes, plus simples à atteindre au doigt et à la manette.
 */
final class FlowRow extends ViewGroup {
    private final int gap;

    FlowRow(Context c, int gapPx) {
        super(c);
        gap = gapPx;
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int max = MeasureSpec.getSize(ws) - getPaddingLeft() - getPaddingRight();
        if (MeasureSpec.getMode(ws) == MeasureSpec.UNSPECIFIED) max = Integer.MAX_VALUE;
        int x = 0, y = 0, line = 0, widest = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            measureChild(v, MeasureSpec.makeMeasureSpec(Math.max(0, max), MeasureSpec.AT_MOST), hs);
            int cw = v.getMeasuredWidth() + margins(v, true), ch = v.getMeasuredHeight() + margins(v, false);
            if (x > 0 && x + cw > max) {
                y += line + gap;
                x = 0;
                line = 0;
            }
            x += cw + gap;
            line = Math.max(line, ch);
            widest = Math.max(widest, x - gap);
        }
        int w = MeasureSpec.getMode(ws) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(ws)
                : widest + getPaddingLeft() + getPaddingRight();
        setMeasuredDimension(w, y + line + getPaddingTop() + getPaddingBottom());
    }

    private static int margins(View v, boolean horizontal) {
        LayoutParams lp = v.getLayoutParams();
        if (!(lp instanceof MarginLayoutParams)) return 0;
        MarginLayoutParams m = (MarginLayoutParams) lp;
        return horizontal ? m.leftMargin + m.rightMargin : m.topMargin + m.bottomMargin;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int max = r - l - getPaddingRight();
        int x = getPaddingLeft(), y = getPaddingTop(), line = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            MarginLayoutParams m = v.getLayoutParams() instanceof MarginLayoutParams
                    ? (MarginLayoutParams) v.getLayoutParams() : new MarginLayoutParams(0, 0);
            int cw = v.getMeasuredWidth(), ch = v.getMeasuredHeight();
            if (x > getPaddingLeft() && x + cw + m.leftMargin + m.rightMargin > max) {
                y += line + gap;
                x = getPaddingLeft();
                line = 0;
            }
            v.layout(x + m.leftMargin, y + m.topMargin, x + m.leftMargin + cw, y + m.topMargin + ch);
            x += cw + m.leftMargin + m.rightMargin + gap;
            line = Math.max(line, ch + m.topMargin + m.bottomMargin);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }

    @Override
    public LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }
}
