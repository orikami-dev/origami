package com.hideo.foldplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.KeyEvent;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Manettes physiques (Bluetooth ou USB) : détection, joueur attribué, réglages propres à chacune.
 *
 * Mappage automatique : Android traduit déjà les manettes courantes (Xbox, PlayStation, 8BitDo…)
 * en touches standard ; on y ajoute la détection des manettes Nintendo (A à droite) et la lecture
 * des axes (sticks, croix analogique, gâchettes). Chaque manette garde ses réglages, retrouvés
 * par son identifiant Android (descriptor).
 */
final class Pads {
    /** Fonctions qu'on peut réattribuer, dans l'ordre de l'assistant « Réattribuer les touches ». */
    static final String[] FUNCTIONS = {"A", "B", "X", "Y", "L", "R", "L2", "R2", "L3", "R3", "SELECT", "START", "HOME"};

    static final class Profile {
        final String id;
        String name;
        int player = 1;
        boolean nintendo;
        int deadzone = 15;
        /** keycode Android → fonction Origami, quand on a réattribué une touche. */
        final Map<Integer, String> remap = new HashMap<Integer, String>();

        Profile(String id) {
            this.id = id;
        }
    }

    private final SharedPreferences prefs;
    private final InputManager input;

    Pads(Context ctx) {
        prefs = ctx.getSharedPreferences("foldplay_pads", Context.MODE_PRIVATE);
        input = (InputManager) ctx.getSystemService(Context.INPUT_SERVICE);
    }

    InputManager input() {
        return input;
    }

    static boolean isPad(InputDevice d) {
        if (d == null || d.isVirtual()) return false;
        int s = d.getSources();
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    /** Manettes branchées en ce moment. */
    List<InputDevice> connected() {
        List<InputDevice> out = new ArrayList<InputDevice>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice d = InputDevice.getDevice(id);
            if (isPad(d)) out.add(d);
        }
        return out;
    }

    boolean any() {
        return !connected().isEmpty();
    }

    /** Réglages de la manette ; une nouvelle manette prend le premier joueur libre. */
    Profile profile(InputDevice d) {
        String id = d.getDescriptor();
        Profile p = load(id);
        if (p != null) {
            p.name = d.getName();
            return p;
        }
        p = new Profile(id);
        p.name = d.getName();
        p.nintendo = d.getVendorId() == 0x057e;
        boolean[] taken = new boolean[5];
        for (InputDevice o : connected()) {
            if (o.getDescriptor().equals(id)) continue;
            Profile q = load(o.getDescriptor());
            if (q != null && q.player >= 1 && q.player <= 4) taken[q.player] = true;
        }
        for (int i = 1; i <= 4; i++) {
            if (!taken[i]) {
                p.player = i;
                break;
            }
        }
        save(p);
        return p;
    }

    /** Toutes les manettes déjà vues (pour les réglages), connectées ou non. */
    List<Profile> known() {
        List<Profile> out = new ArrayList<Profile>();
        for (String k : prefs.getAll().keySet()) {
            if (!k.startsWith("pad:")) continue;
            Profile p = load(k.substring(4));
            if (p != null) out.add(p);
        }
        return out;
    }

    Profile load(String id) {
        String json = prefs.getString("pad:" + id, null);
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            Profile p = new Profile(id);
            p.name = o.optString("name", "Manette");
            p.player = o.optInt("player", 1);
            p.nintendo = o.optBoolean("nintendo", false);
            p.deadzone = o.optInt("deadzone", 15);
            JSONObject m = o.optJSONObject("remap");
            if (m != null) {
                Iterator<String> it = m.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    p.remap.put(Integer.parseInt(k), m.getString(k));
                }
            }
            return p;
        } catch (JSONException | NumberFormatException e) {
            return null;
        }
    }

    void save(Profile p) {
        try {
            JSONObject o = new JSONObject();
            o.put("name", p.name);
            o.put("player", p.player);
            o.put("nintendo", p.nintendo);
            o.put("deadzone", p.deadzone);
            JSONObject m = new JSONObject();
            for (Map.Entry<Integer, String> e : p.remap.entrySet()) m.put(String.valueOf(e.getKey()), e.getValue());
            o.put("remap", m);
            prefs.edit().putString("pad:" + p.id, o.toString()).apply();
        } catch (JSONException ignored) {
            // Rien à enregistrer.
        }
    }

    void forget(Profile p) {
        prefs.edit().remove("pad:" + p.id).apply();
    }

    /** Touche physique → fonction Origami (A, B, UP…), ou null si elle ne sert à rien. */
    static String key(Profile p, int code) {
        if (p != null) {
            String r = p.remap.get(code);
            if (r != null) return r;
        }
        String k = standard(code);
        if (k != null && p != null && p.nintendo) {
            // Manette Nintendo : Android range ses touches par position, on revient aux noms écrits dessus.
            if (k.equals("A")) k = "B";
            else if (k.equals("B")) k = "A";
            else if (k.equals("X")) k = "Y";
            else if (k.equals("Y")) k = "X";
        }
        return k;
    }

    static String standard(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "L";
            case KeyEvent.KEYCODE_BUTTON_R1: return "R";
            case KeyEvent.KEYCODE_BUTTON_L2: return "L2";
            case KeyEvent.KEYCODE_BUTTON_R2: return "R2";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "SELECT";
            case KeyEvent.KEYCODE_BUTTON_START: return "START";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "HOME";
            case KeyEvent.KEYCODE_DPAD_UP: return "UP";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "DOWN";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "RIGHT";
            default: return null;
        }
    }
}
