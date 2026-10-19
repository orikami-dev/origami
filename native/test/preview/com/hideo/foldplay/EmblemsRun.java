package com.hideo.foldplay;
import android.graphics.*;
public class EmblemsRun { public static void run(String out) throws Exception {
 String[] packs={"trait","givre","braise","petale","phosphore"}; String[] themes={"codec","arctique","ecarlate","sakura","ambre"};
 int[] bgs={0xFF0A0F0C,0xFF0B1016,0xFF120C0D,0xFF120E12,0xFF110F0A};
 String[] ids={"nes","snes","n64","gc","switch","gba","nds","psx","ps2","ps3","x360","psp","md","dc","arcade"};
 int W0=110,H0=110,pad=10; int W=pad+ids.length*(W0+pad), H=pad+packs.length*(H0+pad);
 java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(W,H,2); java.awt.Graphics2D G=img.createGraphics();
 for(int r=0;r<packs.length;r++){ G.setColor(new java.awt.Color(bgs[r])); G.fillRect(0,pad/2+r*(H0+pad),W,H0+pad);
  Theme.THEME_ID=themes[r]; Theme.CODEC=new int[]{0xFF4CFFA0,0xFF5CD6FF,0xFFFF5A4E,0xFFFF8AC8,0xFFFFC14D}[r]; Icons.PACK=packs[r];
  for(int i=0;i<ids.length;i++){ Bitmap bm=Bitmap.createBitmap(W0,H0,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
   ConsoleEmblemView v=new ConsoleEmblemView(new android.content.Context()); v.layout(0,0,W0,H0); v.setSystem(SystemInfo.byId(ids[i])); v.onDraw(c);
   G.drawImage(bm.img,pad+i*(W0+pad),pad+r*(H0+pad),null);} }
 javax.imageio.ImageIO.write(img,"png",new java.io.File(out)); } }
