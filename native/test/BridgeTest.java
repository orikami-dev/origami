package com.hideo.foldplay;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BridgeTest {
    static void check(boolean c, String what) {
        System.out.println((c ? "OK   " : "FAIL ") + what);
        if (!c) failures++;
    }
    static int failures;

    public static void main(String[] a) throws Exception {
        System.load(a[0] + "/libfoldplay.so");
        check(Libretro.nativeOpen(a[0] + "/libtestcore.so", "/tmp/sys", "/tmp/saves"), "open: " + Libretro.nativeLastError());
        check(!Libretro.nativeOpen(a[0] + "/libtestcore.so", "/tmp", "/tmp"), "second open refused");
        String[] info = Libretro.nativeSystemInfo();
        check(info[0].equals("testcore") && info[2].equals("tst|bin") && info[3].equals("0"), "system info " + String.join(",", info));
        check(Libretro.nativeLoadGame("/x/game.tst", new byte[1234]), "load game");
        double[] av = Libretro.nativeAvInfo();
        check(av[0] == 60 && av[1] == 48000 && Math.abs(av[2] - 4.0 / 3) < 1e-6 && av[3] == 320, "av info");
        ByteBuffer small = ByteBuffer.allocateDirect(100 * 4).order(ByteOrder.nativeOrder());
        Libretro.nativeSetFrameBuffer(small);
        check(Libretro.nativeRun() == 2, "small buffer -> 2");
        ByteBuffer fb = ByteBuffer.allocateDirect(320 * 240 * 4).order(ByteOrder.nativeOrder());
        Libretro.nativeSetFrameBuffer(fb);
        check(Libretro.nativeRun() == 1 && Libretro.nativeFrameWidth() == 320 && Libretro.nativeFrameHeight() == 240, "run frame");
        // octets RGBA : pixel 0 bleu, pixel 1 vert
        check((fb.get(0) & 0xFF) == 0 && (fb.get(1) & 0xFF) == 0 && (fb.get(2) & 0xFF) == 255 && (fb.get(3) & 0xFF) == 255, "pixel0 blue RGBA");
        check((fb.get(4) & 0xFF) == 0 && (fb.get(5) & 0xFF) == 255 && (fb.get(6) & 0xFF) == 0, "pixel1 green");
        short[] audio = new short[96000];
        int n = Libretro.nativeDrainAudio(audio);
        check(n == 1600 * 2 / 2 * 2 / 1 / 1 - 0 || n == 3200, "audio drained " + n);
        Libretro.nativeSetButtons(1 << Libretro.A | 1 << Libretro.START);
        Libretro.nativeRun();
        check((fb.get(4) & 0xFF) == 255 && (fb.get(5) & 0xFF) == 0, "A pressed -> red");
        ByteBuffer mem = Libretro.nativeMemory(0);
        check(mem != null && mem.capacity() == 8192 && (mem.get(0) & 0xFF) == ((1 << 8 | 1 << 3) & 0xFF), "sram + mask");
        Libretro.nativeSetVariable("test_speed", "rapide");
        Libretro.nativeRun();
        String[] vars = Libretro.nativeVariables();
        check(vars.length == 3 && vars[0].equals("test_speed") && vars[1].startsWith("Vitesse;") && vars[2].equals("rapide"),
                "variables " + String.join(",", vars));
        check(Libretro.nativeCheatReset(), "cheat reset");
        Libretro.nativeCheatSet(0, true, "ABCD-1234");
        byte[] st = Libretro.nativeSaveState();
        check(st != null && st.length == 4, "save state");
        Libretro.nativeRun(); Libretro.nativeRun();
        check(Libretro.nativeLoadState(st), "load state");
        Libretro.nativeClose();
        check(Libretro.nativeOpen(a[0] + "/libtestcore.so", "/tmp", "/tmp"), "reopen after close");
        check(!Libretro.nativeOpen("/nonexistent.so", "/tmp", "/tmp"), "already open");
        Libretro.nativeClose();
        check(!Libretro.nativeOpen("/nonexistent.so", "/tmp", "/tmp"), "missing core: " + Libretro.nativeLastError());
        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILURES");
    }
}
