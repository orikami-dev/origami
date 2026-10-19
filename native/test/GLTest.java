package com.hideo.foldplay;
import java.nio.*;
public class GLTest { public static void main(String[] a){
 System.load(a[0]+"/libfoldplay.so");
 if(!Libretro.nativeOpen(a[0]+"/libglcore.so","/tmp","/tmp")) throw new RuntimeException(Libretro.nativeLastError());
 if(!Libretro.nativeLoadGame("/tmp/x.bin",new byte[]{1})) throw new RuntimeException("load: "+Libretro.nativeLastError());
 ByteBuffer f=ByteBuffer.allocateDirect(64*32*4).order(ByteOrder.nativeOrder()); Libretro.nativeSetFrameBuffer(f);
 int r=Libretro.nativeRun(); System.out.println("run="+r+" "+Libretro.nativeFrameWidth()+"x"+Libretro.nativeFrameHeight());
 int top=f.getInt(0), bottom=f.getInt((31*64)*4);
 System.out.printf("top=%08x bottom=%08x%n", top, bottom);
 // Octets R, G, B, A en mémoire : rouge = 0xff0000ff en little-endian, bleu = 0xffff0000.
 if(r!=1||top!=0xff0000ff||bottom!=0xffff0000) throw new RuntimeException("image fausse");
 Libretro.nativeClose(); System.out.println("ALL PASSED");
}}
