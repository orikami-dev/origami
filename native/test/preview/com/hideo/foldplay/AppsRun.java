package com.hideo.foldplay;
import android.graphics.*;
public class AppsRun { public static void main(String[] a) throws Exception {
 String[] packs={"trait","givre","braise","petale","phosphore"}; String[] themes={"codec","arctique","ecarlate","sakura","ambre"};
 String[] names={"Codec · Trait","Arctique · Givre","Écarlate · Braise","Sakura · Pétale","Ambre · Phosphore"};
 int[] bgs={0xFF0A0F0C,0xFF0B1016,0xFF120C0D,0xFF120E12,0xFF110F0A};
 int[] acc={0xFF4CFFA0,0xFF5CD6FF,0xFFFF5A4E,0xFFFF8AC8,0xFFFFC14D};
 String[] ids={"ps3","x360","arcade","android","remote"};
 int W0=130,H0=130,pad=12,left=170; int W=left+pad+(ids.length+1)*(W0+pad), H=pad+packs.length*(H0+pad);
 java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(W,H,2); java.awt.Graphics2D G=img.createGraphics();
 G.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
 for(int r=0;r<packs.length;r++){ G.setColor(new java.awt.Color(bgs[r])); G.fillRect(0,pad/2+r*(H0+pad),W,H0+pad);
  Theme.THEME_ID=themes[r]; Theme.CODEC=acc[r]; Icons.PACK=packs[r];
  G.setColor(new java.awt.Color(acc[r])); G.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 15));
  G.drawString(names[r], pad, pad+r*(H0+pad)+H0/2+5);
  for(int i=0;i<ids.length;i++){ Bitmap bm=Bitmap.createBitmap(W0,H0,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
   ConsoleEmblemView v=new ConsoleEmblemView(new android.content.Context()); v.layout(0,0,W0,H0); v.setSystem(SystemInfo.byId(ids[i])); v.onDraw(c);
   G.drawImage(bm.img,left+pad+i*(W0+pad),pad+r*(H0+pad),null);}
  // Tuile « Actualiser la bibliothèque » telle que dessinée dans la colonne Tous.
  int x=left+pad+ids.length*(W0+pad), y=pad+r*(H0+pad);
  java.awt.Color top=new java.awt.Color(Theme.darken(0xFF3A4150,0.2f)), bot=new java.awt.Color(Theme.darken(0xFF3A4150,0.65f));
  G.setPaint(new java.awt.GradientPaint(0,y+10,top,0,y+H0-10,bot)); G.fillRoundRect(x+22,y+8,W0-44,H0-38,18,18);
  Bitmap bm=Bitmap.createBitmap(W0,H0,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
  Icons ic=Icons.of(new android.content.Context(),"refresh",acc[r],40); ic.setBounds(W0/2-24,(H0-30)/2-24+4,W0/2+24,(H0-30)/2+24+4); ic.draw(c);
  G.drawImage(bm.img,x,y,null);
  G.setColor(java.awt.Color.WHITE); G.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 10));
  G.drawString("Actualiser la", x+30, y+H0-16); G.drawString("bibliothèque", x+30, y+H0-4);
 }
 javax.imageio.ImageIO.write(img,"png",new java.io.File(a[0])); } }
