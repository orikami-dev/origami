package com.hideo.foldplay;
import android.graphics.*;
/** Aperçu des fonds d'écran « nuit » : java WallRun dossier [thème] — écrit <thème>-wallpaper-*.png et <thème>-pad.png (sans thème : les quatre premiers ; neon, jade, argent à demander par leur nom). */
public class WallRun {
 static final String[] TH={"codec","arctique","ecarlate","sakura","ambre","neon","jade","argent"};
 static final int[] ACC={0xFF4CFFA0,0xFF5CD6FF,0xFFFF5A4E,0xFFFF8AC8,0xFFFFC14D,0xFFFF4FD8,0xFF3FB58A,0xFFC8D0DC};
 static final int[] BG={0xFF0E0F13,0xFF0B1016,0xFF120C0D,0xFF120E12,0xFF110F0A,0xFF0D0A13,0xFF09110F,0xFF0C0D10};
 static final int[] PN={0xFF181A21,0xFF141C26,0xFF1E1416,0xFF1E171E,0xFF1D1A12,0xFF18121F,0xFF121E1C,0xFF17191E};
 static void shot(String out,int i,int W,int H) throws Exception {
  Theme.THEME_ID=TH[i]; Theme.CODEC=ACC[i];
  Bitmap bm=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
  ThemeBackdrop d=new ThemeBackdrop(TH[i],BG[i],ACC[i],0,Theme.fold()); d.setBounds(0,0,W,H); d.draw(c);
  javax.imageio.ImageIO.write(bm.img,"png",new java.io.File(out)); }
 static void pad(String out,int i) throws Exception {
  Theme.THEME_ID=TH[i]; Theme.CODEC=ACC[i]; int W=1200,H=520;
  Bitmap bm=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm); c.g.setColor(new java.awt.Color(PN[i])); c.g.fillRect(0,0,W,H);
  if(i==0) Facets.codec(c,W,H,ACC[i],Theme.fold(),0.75f,1); else if(i==1) Facets.arctique(c,W,H,ACC[i],Theme.fold(),0.75f,1); else if(i==2) Facets.ecarlate(c,W,H,ACC[i],Theme.fold(),0.75f,1); else if(i==3) Facets.sakura(c,W,H,ACC[i],Theme.fold(),0.75f,1); else if(i==4) Facets.ambre(c,W,H,ACC[i],Theme.fold(),0.75f,1); else if(i==5) Facets.neon(c,W,H,ACC[i],Theme.fold(),0.75f,Facets.MANETTE); else if(i==6) Facets.jade(c,W,H,ACC[i],Theme.fold(),0.75f,Facets.MANETTE); else Facets.argent(c,W,H,ACC[i],Theme.fold(),0.75f,Facets.MANETTE);
  javax.imageio.ImageIO.write(bm.img,"png",new java.io.File(out)); }
 public static void main(String[] a) throws Exception { String d=a[0];
  ThemeBackdrop.CODEC_BASE=true; ThemeBackdrop.ARCTIQUE_POLAIRE=true; ThemeBackdrop.ECARLATE_JAPON=true; ThemeBackdrop.SAKURA_RIVIERE=true;
  for(int i=0;i<TH.length;i++){ if(a.length>1 ? !a[1].equals(TH[i]) : i>=4) continue;
   shot(d+"/"+TH[i]+"-wallpaper-fold.png",i,2448,1848); shot(d+"/"+TH[i]+"-wallpaper-portrait.png",i,1848,1224); shot(d+"/"+TH[i]+"-wallpaper-cover.png",i,904,2316); pad(d+"/"+TH[i]+"-pad.png",i); } } }
