package com.hideo.foldplay;

import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/** Transmet tout à la fenêtre d'origine : on n'en change que ce qu'il faut (TouchSound). */
class WindowCallbackProxy implements Window.Callback {
    final Window.Callback base;

    WindowCallbackProxy(Window.Callback base) {
        this.base = base;
    }

    public boolean dispatchKeyEvent(KeyEvent e) { return base.dispatchKeyEvent(e); }
    public boolean dispatchKeyShortcutEvent(KeyEvent e) { return base.dispatchKeyShortcutEvent(e); }
    public boolean dispatchTouchEvent(MotionEvent e) { return base.dispatchTouchEvent(e); }
    public boolean dispatchTrackballEvent(MotionEvent e) { return base.dispatchTrackballEvent(e); }
    public boolean dispatchGenericMotionEvent(MotionEvent e) { return base.dispatchGenericMotionEvent(e); }
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent e) { return base.dispatchPopulateAccessibilityEvent(e); }
    public View onCreatePanelView(int f) { return base.onCreatePanelView(f); }
    public boolean onCreatePanelMenu(int f, Menu m) { return base.onCreatePanelMenu(f, m); }
    public boolean onPreparePanel(int f, View v, Menu m) { return base.onPreparePanel(f, v, m); }
    public boolean onMenuOpened(int f, Menu m) { return base.onMenuOpened(f, m); }
    public boolean onMenuItemSelected(int f, MenuItem i) { return base.onMenuItemSelected(f, i); }
    public void onWindowAttributesChanged(WindowManager.LayoutParams a) { base.onWindowAttributesChanged(a); }
    public void onContentChanged() { base.onContentChanged(); }
    public void onWindowFocusChanged(boolean f) { base.onWindowFocusChanged(f); }
    public void onAttachedToWindow() { base.onAttachedToWindow(); }
    public void onDetachedFromWindow() { base.onDetachedFromWindow(); }
    public void onPanelClosed(int f, Menu m) { base.onPanelClosed(f, m); }
    public boolean onSearchRequested() { return base.onSearchRequested(); }
    public boolean onSearchRequested(SearchEvent e) { return base.onSearchRequested(e); }
    public ActionMode onWindowStartingActionMode(ActionMode.Callback c) { return base.onWindowStartingActionMode(c); }
    public ActionMode onWindowStartingActionMode(ActionMode.Callback c, int t) { return base.onWindowStartingActionMode(c, t); }
    public void onActionModeStarted(ActionMode m) { base.onActionModeStarted(m); }
    public void onActionModeFinished(ActionMode m) { base.onActionModeFinished(m); }
}
