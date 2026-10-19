package com.hideo.foldplay;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.List;

/**
 * Navigation à la manette dans n'importe quel écran : un cadre Codec passe d'un élément touchable
 * à son voisin dans la direction demandée (croix, stick), A le touche, B revient en arrière,
 * L1 / R1 changent d'onglet là où l'écran en a. Un menu codec ouvert garde la main.
 * Pas de focus Android : son cadre de secours débordait et il ne voit pas les lignes faites main.
 */
final class PadNav {
    /** Touches propres à un écran (onglets…) ; vrai si l'écran l'a prise. */
    interface Hooks {
        boolean onKey(String k);
    }

    private final Activity activity;
    private final float dp;
    private final GradientDrawable frame = new GradientDrawable();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Pads pads;
    private Hooks hooks;
    private View current;
    private int lastIndex;
    private boolean shown;
    private String held, hat, stick;
    private boolean l2Held, r2Held;

    PadNav(Activity a) {
        activity = a;
        dp = a.getResources().getDisplayMetrics().density;
        frame.setColor((Theme.CODEC & 0x00FFFFFF) | 0x22000000);
        frame.setStroke(Math.round(3 * dp), Theme.CODEC);
        frame.setCornerRadius(14 * dp);
        View decor = a.getWindow().getDecorView();
        ViewTreeObserver.OnScrollChangedListener scroll = new ViewTreeObserver.OnScrollChangedListener() {
            public void onScrollChanged() { place(); }
        };
        decor.getViewTreeObserver().addOnScrollChangedListener(scroll);
        decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() { place(); }
        });
    }

    PadNav hooks(Hooks h) {
        hooks = h;
        return this;
    }

    /** Touche de manette : vrai si elle a servi à naviguer. */
    boolean dispatchKey(KeyEvent e) {
        InputDevice d = e.getDevice();
        if (!Pads.isPad(d)) {
            // Un doigt sur l'écran : le cadre s'efface jusqu'au prochain usage de la manette.
            return false;
        }
        if (pads == null) pads = new Pads(activity);
        String k = Pads.key(pads.profile(d), e.getKeyCode());
        if (k == null && e.getKeyCode() == KeyEvent.KEYCODE_BACK) k = "B";
        if (k == null) return true;
        boolean dir = k.equals("UP") || k.equals("DOWN") || k.equals("LEFT") || k.equals("RIGHT");
        if (dir) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) hold(k);
            else if (e.getAction() == KeyEvent.ACTION_UP && k.equals(held)) hold(null);
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) key(k);
        return true;
    }

    boolean dispatchMotion(MotionEvent e) {
        if (!Pads.isPad(e.getDevice()) || e.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        float hx = e.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y);
        String h = hx < -0.5f ? "LEFT" : hx > 0.5f ? "RIGHT" : hy < -0.5f ? "UP" : hy > 0.5f ? "DOWN" : null;
        float x = e.getAxisValue(MotionEvent.AXIS_X);
        float y = e.getAxisValue(MotionEvent.AXIS_Y);
        float ax = Math.abs(x), ay = Math.abs(y);
        String st = null;
        if (Math.max(ax, ay) > (stick != null ? 0.35f : 0.6f)) st = ax > ay ? (x > 0 ? "RIGHT" : "LEFT") : (y > 0 ? "DOWN" : "UP");
        hat = h;
        stick = st;
        String want = h != null ? h : st;
        if (want == null ? held != null : !want.equals(held)) hold(want);
        float lt = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE));
        float rt = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS));
        boolean l2 = lt > 0.6f, r2 = rt > 0.6f;
        if (l2 && !l2Held) key("L2");
        if (r2 && !r2Held) key("R2");
        l2Held = l2;
        r2Held = r2;
        return true;
    }

    private final Runnable repeat = new Runnable() {
        public void run() {
            if (held == null) return;
            key(held);
            handler.postDelayed(this, 110);
        }
    };

    private void hold(String dir) {
        handler.removeCallbacks(repeat);
        held = dir;
        if (dir == null) return;
        key(dir);
        handler.postDelayed(repeat, 380);
    }

    /** Une touche de la manette Origami. */
    void key(String k) {
        CodecMenu open = CodecMenu.find(CodecMenu.hostFor(activity));
        if (open != null && open.key(k)) return;
        if (hooks != null && hooks.onKey(k)) return;
        if (k.equals("B")) {
            activity.onBackPressed();
            return;
        }
        List<View> all = targets();
        if (all.isEmpty()) return;
        if (current == null || !all.contains(current)) {
            current = all.get(Math.max(0, Math.min(all.size() - 1, lastIndex)));
            if (k.equals("A") || !shown) {
                show();
                return;
            }
        }
        if (k.equals("A") || k.equals("START")) {
            if (!shown) {
                show();
                return;
            }
            lastIndex = all.indexOf(current);
            current.performClick();
            return;
        }
        if (k.equals("X") || k.equals("Y")) {
            if (current.isLongClickable()) current.performLongClick();
            return;
        }
        if (k.equals("L2") || k.equals("R2")) {
            // Saut de page dans une longue liste.
            for (int i = 0; i < 5; i++) step(k.equals("L2") ? "UP" : "DOWN", targets());
            show();
            return;
        }
        if (!shown) {
            show();
            return;
        }
        step(k, all);
        show();
    }

    private void step(String dir, List<View> all) {
        if (current == null) return;
        View next = neighbour(current, dir, all);
        if (next != null) {
            current = next;
            lastIndex = all.indexOf(next);
        }
    }

    /** Oublie l'élément choisi (l'écran a été reconstruit) en gardant son rang. */
    void reset() {
        current = null;
        place();
    }

    private void show() {
        shown = true;
        if (current != null) {
            Rect r = new Rect(0, 0, current.getWidth(), current.getHeight());
            current.requestRectangleOnScreen(r, false);
        }
        place();
    }

    private void place() {
        View decor = activity.getWindow().getDecorView();
        decor.getOverlay().remove(frame);
        if (!shown || current == null || !current.isShown() || current.getWindowToken() == null) return;
        int[] at = new int[2];
        current.getLocationInWindow(at);
        int pad = Math.round(2 * dp);
        frame.setBounds(at[0] - pad, at[1] - pad, at[0] + current.getWidth() + pad, at[1] + current.getHeight() + pad);
        decor.getOverlay().add(frame);
    }

    /** Éléments touchables visibles, le plus extérieur seulement quand ils s'emboîtent. */
    private List<View> targets() {
        List<View> out = new ArrayList<View>();
        View content = activity.findViewById(android.R.id.content);
        if (content != null) collect(content, out);
        return out;
    }

    private static void collect(View v, List<View> out) {
        if (v.getVisibility() != View.VISIBLE || !v.isEnabled()) return;
        if ((v.isClickable() || v.isLongClickable()) && v.getWidth() > 0 && v.getHeight() > 0 && v.isShown()) {
            out.add(v);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out);
        }
    }

    /**
     * Le voisin le plus proche dans la direction : d'abord ceux qui sont en face (qui se
     * recouvrent sur l'autre axe), sinon le plus proche en tenant compte du décalage.
     */
    private static View neighbour(View from, String dir, List<View> all) {
        Rect a = rect(from);
        View best = null;
        double bestScore = Double.MAX_VALUE;
        for (View v : all) {
            if (v == from) continue;
            Rect b = rect(v);
            float main, cross;
            boolean facing;
            if (dir.equals("UP") || dir.equals("DOWN")) {
                main = dir.equals("UP") ? a.top - b.bottom : b.top - a.bottom;
                if ((dir.equals("UP") ? b.centerY() >= a.centerY() : b.centerY() <= a.centerY())) continue;
                facing = b.left < a.right && b.right > a.left;
                cross = facing ? 0 : Math.min(Math.abs(b.left - a.right), Math.abs(a.left - b.right));
            } else {
                main = dir.equals("LEFT") ? a.left - b.right : b.left - a.right;
                if ((dir.equals("LEFT") ? b.centerX() >= a.centerX() : b.centerX() <= a.centerX())) continue;
                facing = b.top < a.bottom && b.bottom > a.top;
                cross = facing ? 0 : Math.min(Math.abs(b.top - a.bottom), Math.abs(a.top - b.bottom));
            }
            double score = Math.max(0, main) + cross * 3 + (facing ? 0 : 100000);
            if (score < bestScore) {
                bestScore = score;
                best = v;
            }
        }
        return best;
    }

    private static Rect rect(View v) {
        int[] at = new int[2];
        v.getLocationInWindow(at);
        return new Rect(at[0], at[1], at[0] + v.getWidth(), at[1] + v.getHeight());
    }
}
