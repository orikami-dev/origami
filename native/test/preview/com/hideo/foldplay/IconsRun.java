package com.hideo.foldplay;
import android.graphics.*;
public class IconsRun { public static void run(String out) throws Exception {
 String[] packs={"trait","givre","braise","petale","phosphore"}; int[] acc={0xFF4CFFA0,0xFF5CD6FF,0xFFFF5A4E,0xFFFF8AC8,0xFFFFC14D};
 int[] bgs={0xFF0A0F0C,0xFF0B1016,0xFF120C0D,0xFF120E12,0xFF110F0A};
 String[] names={"grid","flex","refresh","palette","pads","settings","apps","star","clock","library","share"};
 int S=64,pad=16; int W=pad+names.length*(S+pad), H=pad+packs.length*(S+pad);
 java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(W,H,java.awt.image.BufferedImage.TYPE_INT_ARGB);
 java.awt.Graphics2D G=img.createGraphics();
 for(int r=0;r<packs.length;r++){ G.setColor(new java.awt.Color(bgs[r])); G.fillRect(0,pad/2+r*(S+pad),W,S+pad);
  Icons.PACK=packs[r];
  for(int i=0;i<names.length;i++){ Bitmap bm=Bitmap.createBitmap(S,S,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
   Icons ic=new Icons(names[i],0xFFF1F1F1,S).mark(acc[r]); ic.setBounds(0,0,S,S); ic.draw(c);
   G.drawImage(bm.img,pad+i*(S+pad),pad+r*(S+pad),null);} }
 javax.imageio.ImageIO.write(img,"png",new java.io.File(out)); } }
