package com.hideo.foldplay;
import android.graphics.*;
/** Aperçu : pour chaque thème, le fond de l'écran du haut et celui de la manette, côte à côte. */
public class PairRun {
 public static void main(String[] a) throws Exception {
  String[] th={"codec","arctique","ecarlate","sakura","ambre"}; String[] nm={"Codec","Arctique","Écarlate","Sakura","Ambre"};
  int[] acc={0xFF4CFFA0,0xFF5CD6FF,0xFFFF5A4E,0xFFFF8AC8,0xFFFFC14D}; int[] bg={0xFF0E0F13,0xFF0B1016,0xFF120C0D,0xFF120E12,0xFF110F0A}; int[] pn={0xFF181A21,0xFF141C26,0xFF1E1416,0xFF1E171E,0xFF1D1A12};
  int W=420,H=300,PW=420,PH=220,gap=16,lab=28; int tw=W+PW+gap*3; int th1=lab+Math.max(H,PH)+gap;
  java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(tw,th1*th.length,2); java.awt.Graphics2D G=img.createGraphics();
  G.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  G.setColor(new java.awt.Color(0x08090C)); G.fillRect(0,0,img.getWidth(),img.getHeight());
  for(int i=0;i<th.length;i++){ Theme.THEME_ID=th[i]; Theme.CODEC=acc[i]; int y=i*th1;
   G.setColor(new java.awt.Color(0xE8EAEE)); G.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF,java.awt.Font.BOLD,16)); G.drawString(nm[i]+" · écran du haut",gap,y+20); G.drawString("manette",gap*2+W,y+20);
   Bitmap top=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(top); c.g.setColor(new java.awt.Color(bg[i])); c.g.fillRect(0,0,W,H);
   if(i==0) Facets.codec(c,W,H,acc[i],Theme.fold(),1f,0); else if(i==1) Facets.arctique(c,W,H,acc[i],Theme.fold(),1f,0); else if(i==2) Facets.ecarlate(c,W,H,acc[i],Theme.fold(),1f,0); else if(i==3) Facets.sakura(c,W,H,acc[i],Theme.fold(),1f,0); else Facets.ambre(c,W,H,acc[i],Theme.fold(),1f,Facets.AMBRE);
   Bitmap pad=Bitmap.createBitmap(PW,PH,Bitmap.Config.ARGB_8888); Canvas d=new Canvas(pad); d.g.setColor(new java.awt.Color(pn[i])); d.g.fillRect(0,0,PW,PH);
   if(i==0) Facets.codec(d,PW,PH,acc[i],Theme.fold(),0.75f,1); else if(i==1) Facets.arctique(d,PW,PH,acc[i],Theme.fold(),0.75f,1); else if(i==2) Facets.ecarlate(d,PW,PH,acc[i],Theme.fold(),0.75f,1); else if(i==3) Facets.sakura(d,PW,PH,acc[i],Theme.fold(),0.75f,1); else Facets.ambre(d,PW,PH,acc[i],Theme.fold(),0.75f,0);
   G.drawImage(top.img,gap,y+lab,null); G.drawImage(pad.img,gap*2+W,y+lab,null); }
  javax.imageio.ImageIO.write(img,"png",new java.io.File(a[0])); } }
