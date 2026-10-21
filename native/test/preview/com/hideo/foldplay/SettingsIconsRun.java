package com.hideo.foldplay;
import android.graphics.*;
import java.awt.Font; import java.awt.Graphics2D; import java.awt.RenderingHints;
/**
 * Aperçu des entrées de la catégorie « Paramètres » du XMB (0.66) : une ligne par thème, une colonne par entrée,
 * chaque tuile comme CartridgeView.drawTile (écrin du pack, pictogramme). java SettingsIconsRun fichier.png
 */
public class SettingsIconsRun {
 static final String[] TH={"codec","arctique","ecarlate","sakura","ambre","neon","jade","argent"};
 static final String[] NM={"Codec","Arctique","Écarlate","Sakura","Ambre","Néon","Jade","Argent"};
 static final int[] ACC={0xFF4CFFA0,0xFF5CD6FF,0xFFFF5A4E,0xFFFF8AC8,0xFFFFC14D,0xFFFF4FD8,0xFF5BD6A2,0xFFC8D0DC};
 static final int[] BG={0xFF0E0F13,0xFF0B1016,0xFF120C0D,0xFF120E12,0xFF110F0A,0xFF0D0A13,0xFF0A100E,0xFF0C0D10};
 static final String[] IDS={"sec:library","sec:emulators","theme","sec:display","sec:sound","pads","sec:a11y","sec:monitoring","sec:updates"};
 static final String[] LAB={"Bibliothèque","Émulateurs","Thème","Affichage","Son","Manette","Accessibilité","Monitoring","Mises à jour, À propos"};
 public static void main(String[] a) throws Exception {
  int T=140, g=14, lw=150, top=40; int W=lw+IDS.length*(T+g)+g+48, H=top+TH.length*(T+g)+g;
  java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(W,H,java.awt.image.BufferedImage.TYPE_INT_RGB);
  Graphics2D G=img.createGraphics(); G.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  G.setColor(new java.awt.Color(0x08090C)); G.fillRect(0,0,W,H);
  G.setFont(new Font(Font.SANS_SERIF,Font.BOLD,15)); G.setColor(new java.awt.Color(0xE8EAEE));
  for(int i=0;i<IDS.length;i++) G.drawString(LAB[i],lw+i*(T+g)+4,top-12);
  for(int r=0;r<TH.length;r++){ Theme.THEME_ID=TH[r]; Theme.CODEC=ACC[r]; Icons.PACK="auto"; int y=top+r*(T+g);
   G.setColor(new java.awt.Color(BG[r])); G.fillRect(0,y-g/2,W,T+g);
   G.setColor(new java.awt.Color(0xE8EAEE)); G.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18)); G.drawString(NM[r],14,y+T/2-4);
   G.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,13)); G.setColor(new java.awt.Color(0x8A8F9C)); G.drawString(Icons.packName(Icons.pack()),14,y+T/2+16);
   for(int i=0;i<IDS.length;i++){ Bitmap bm=Bitmap.createBitmap(T,T,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); RectF rr=new RectF(4,4,T-4,T-4); float rad=(T-8)*0.18f;
    p.setShader(new LinearGradient(0,4,0,T-4,Theme.darken(0xFF5B6475,0.2f),Theme.darken(0xFF5B6475,0.65f),Shader.TileMode.CLAMP)); c.drawRoundRect(rr,rad,rad,p); p.setShader(null);
    float side=(T-8)*0.56f, cx=T/2f, cy=T/2f;
    Icons.frame(c,Icons.pack(),cx,cy,side*1.12f,ACC[r]); side*=0.72f;
    Icons ic=new Icons(Icons.forSetting(IDS[i]),ACC[r],Math.round(side)); ic.setBounds(Math.round(cx-side/2),Math.round(cy-side/2),Math.round(cx+side/2),Math.round(cy+side/2)); ic.draw(c);
    G.drawImage(bm.img,lw+i*(T+g),y,null); } }
  javax.imageio.ImageIO.write(img,"png",new java.io.File(a[0])); } }
