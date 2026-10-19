package com.hideo.foldplay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * ATH façon RTSS pour les moteurs à activité propre (PS3, Xbox 360), avec les mêmes réglages
 * que GameActivity (Réglages > Affichage). Le moteur donne ses images/s par un {@link Source}.
 */
final class HudOverlay {

    /** Ce que le moteur sait mesurer ; une valeur négative veut dire « inconnue ». */
    interface Source {
        /** Images affichées par seconde depuis le dernier appel, ou -1. */
        float fps(float seconds);

        /** Pic du temps d'image en ms depuis le dernier appel, ou -1. */
        float frameMaxMs();

        boolean paused();
    }

    private final Activity activity;
    private final FrameLayout root;
    private final Store store;
    private final Source source;
    private final String core;
    private View screen;
    private TextView hud;
    private long lastAt, lastCpuMs;
    private final android.os.Handler timer = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        public void run() {
            update();
            if (hud != null && hud.getVisibility() == View.VISIBLE) timer.postDelayed(this, store.hudRate());
        }
    };

    HudOverlay(Activity activity, FrameLayout root, Store store, String core, Source source) {
        this.activity = activity;
        this.root = root;
        this.store = store;
        this.core = core;
        this.source = source;
    }

    /** La vue du jeu, pour afficher sa taille. */
    void setScreen(View screen) {
        this.screen = screen;
    }

    private int dp(float v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }

    /** Affiche ou cache l'ATH selon les réglages. */
    void apply() {
        boolean on = store.hud();
        if (on && hud == null) {
            hud = new TextView(activity);
            hud.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE,
                    android.graphics.Typeface.BOLD));
            hud.setTextColor(Color.WHITE);
            hud.setIncludeFontPadding(false);
            root.addView(hud);
        }
        if (hud == null) return;
        boolean line = !"block".equals(store.hudStyle());
        String pos = store.hudPos();
        int gravity = Gravity.TOP | ("center".equals(pos) ? Gravity.CENTER_HORIZONTAL
                : "right".equals(pos) ? Gravity.END : Gravity.START);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, gravity);
        int m = line ? dp(6) : dp(20);
        lp.setMargins(m, m, m, 0);
        hud.setLayoutParams(lp);
        hud.setTextSize(Hud.SIZES[Math.max(0, Math.min(2, store.hudSize()))]);
        hud.setSingleLine(line);
        hud.setMaxLines(line ? 1 : 20);
        hud.setShadowLayer(dp(2), 0, 0, Color.BLACK);
        if (store.hudBackground()) {
            hud.setBackground(Theme.pill(line ? 0x8C000000 : 0x99000000, dp(line ? 6 : 12)));
            hud.setPadding(dp(line ? 8 : 10), dp(line ? 3 : 6), dp(line ? 8 : 10), dp(line ? 3 : 6));
        } else {
            hud.setBackground(null);
            hud.setPadding(dp(2), 0, dp(2), 0);
        }
        timer.removeCallbacks(tick);
        hud.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            hud.bringToFront();
            lastAt = 0;
            timer.post(tick);
        }
    }

    /** À appeler quand la manette ou un menu passe devant : l'ATH reste au-dessus. */
    void front() {
        if (hud != null) hud.bringToFront();
    }

    void stop() {
        timer.removeCallbacks(tick);
    }

    private void update() {
        if (hud == null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        long cpu = android.os.Process.getElapsedCpuTime();
        boolean block = "block".equals(store.hudStyle());
        int label = Hud.labelColor(store.hudColor());
        if (lastAt == 0) {
            lastAt = now;
            lastCpuMs = cpu;
            source.fps(1);
            source.frameMaxMs();
            android.text.SpannableString s = new android.text.SpannableString("ATH  mesure…");
            s.setSpan(new android.text.style.ForegroundColorSpan(label), 0, 3, 0);
            hud.setText(s);
            return;
        }
        float sec = Math.max(0.001f, (now - lastAt) / 1000f);
        Hud.Sample h = new Hud.Sample();
        h.paused = source.paused();
        h.fps = source.fps(sec);
        h.frameMs = h.fps > 0.5f ? 1000f / h.fps : 0;
        float max = source.frameMaxMs();
        h.frameMaxMs = max >= 0 ? max : h.frameMs;
        // Sans compteur d'images émulées, la vitesse se déduit des 60 images/s visées.
        h.speed = h.fps >= 0 ? h.fps / 60f * 100 : -1;
        h.cpu = (cpu - lastCpuMs) / 10f / sec;
        lastAt = now;
        lastCpuMs = cpu;

        java.util.Set<String> items = store.hudItems();
        if (items.contains("mhz")) h.mhz = Hud.cpuMhz();
        Runtime rt = Runtime.getRuntime();
        h.ramMb = (int) ((android.os.Debug.getNativeHeapAllocatedSize() >> 20)
                + ((rt.totalMemory() - rt.freeMemory()) >> 20));
        Intent bat = activity.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bat != null) {
            int level = bat.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = bat.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0) h.battery = level * 100 / Math.max(1, scale);
            h.charging = bat.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0;
            h.tempC = bat.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            android.os.PowerManager pm = (android.os.PowerManager) activity.getSystemService(Activity.POWER_SERVICE);
            h.heat = pm.getCurrentThermalStatus();
        }
        h.core = core;
        if (screen != null) {
            h.width = screen.getWidth();
            h.height = screen.getHeight();
        }
        hud.setText(Hud.compose(h, items, block, label));
    }
}
