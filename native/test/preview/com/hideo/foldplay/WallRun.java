package com.hideo.foldplay;
import android.graphics.*;
/** Aperçu du fond d'écran Ambre (ville Art déco) : java WallRun dossier — écrit ambre-wallpaper-*.png. */
public class WallRun {
 static void shot(String out,int W,int H) throws Exception {
  Theme.THEME_ID="ambre"; Theme.CODEC=0xFFFFC14D;
  Bitmap bm=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
  ThemeBackdrop d=new ThemeBackdrop("ambre",0xFF110F0A,0xFFFFC14D,0,Theme.fold()); d.setBounds(0,0,W,H); d.draw(c);
  javax.imageio.ImageIO.write(bm.img,"png",new java.io.File(out)); }
 public static void main(String[] a) throws Exception { String d=a[0];
  shot(d+"/ambre-wallpaper-fold.png",2448,1848); shot(d+"/ambre-wallpaper-portrait.png",1848,1224); shot(d+"/ambre-wallpaper-cover.png",904,2316); } }
