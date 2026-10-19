package com.hideo.foldplay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Reçoit des sauvegardes partagées par une autre app (« Partager » > Origami) : un ou plusieurs
 * fichiers, puis choix de la console et conversion comme l'import des Réglages.
 */
public final class ImportActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        I18n.attach(this);
        Theme.sync(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Theme.BG);
        setContentView(root);
        Theme.fullscreen(this);

        Intent i = getIntent();
        List<Uri> uris = new ArrayList<Uri>();
        if (Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())) {
            ArrayList<Uri> list = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) uris.addAll(list);
        } else {
            Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u == null) u = i.getData();
            if (u != null) uris.add(u);
        }
        StringBuilder errors = new StringBuilder();
        List<SaveImport.Out> files = SaveTransfer.read(this, uris, errors);
        Runnable close = new Runnable() {
            public void run() { finish(); }
        };
        if (files.isEmpty()) {
            new CodecMenu(this).title("Importer", "SAUVEGARDES").message("Aucun fichier lisible reçu.\n" + errors)
                    .item("OK", null, close).onBack(close).show(root);
            return;
        }
        SaveTransfer.chooseSystem(this, files, errors, close);
    }

    @Override
    public void onBackPressed() {
        CodecMenu open = CodecMenu.find(CodecMenu.hostFor(this));
        if (open != null) open.key("B");
        else super.onBackPressed();
    }

    private PadNav nav;

    /** La manette pilote aussi cet écran : cadre Codec, A pour toucher, B pour revenir. */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (nav == null) nav = new PadNav(this);
        return nav.dispatchKey(e) || super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent e) {
        if (nav == null) nav = new PadNav(this);
        return nav.dispatchMotion(e) || super.dispatchGenericMotionEvent(e);
    }
}
