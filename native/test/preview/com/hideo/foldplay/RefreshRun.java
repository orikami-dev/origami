package com.hideo.foldplay;
import android.graphics.*;
public class RefreshRun { public static void main(String[] a) throws Exception {
 String[] packs={"trait","givre","braise","petale","phosphore"}; String[] themes={"codec","arctique","ecarlate","sakura","ambre"};
 String[] names={"Codec · Trait","Arctique · Givre","Écarlate · Braise","Sakura · Pétale","Ambre · Phosphore"};
 int[] acc={0xFF4CFFA0,0xFF5CD6FF,0xFFFF5A4E,0xFFFF8AC8,0xFFFFC14D};
 int[] sizes={24,48,96}; int[] bgs={0xFF22272F,0xFFF2F2F2};
 int cell=150,left=170,pad=10; int W=left+sizes.length*2*(cell+pad)+pad, H=40+packs.length*(cell+pad);
 java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(W,H,2); java.awt.Graphics2D G=img.createGraphics();
 G.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
 G.setColor(new java.awt.Color(0xFF0B0D10)); G.fillRect(0,0,W,H);
 G.setColor(java.awt.Color.WHITE); G.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 13));
 int k=0; for(int bg=0;bg<2;bg++) for(int sz:sizes){ G.drawString((bg==0?"tuile sombre ":"fond clair ")+sz+" px", left+pad+k*(cell+pad)+20, 26); k++; }
 for(int r=0;r<packs.length;r++){ Theme.THEME_ID=themes[r]; Theme.CODEC=acc[r]; Icons.PACK=packs[r];
  int y=40+r*(cell+pad);
  G.setColor(new java.awt.Color(acc[r])); G.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 15));
  G.drawString(names[r], pad, y+cell/2+5);
  k=0; for(int bg=0;bg<2;bg++) for(int sz:sizes){ int x=left+pad+k*(cell+pad); k++;
   G.setColor(new java.awt.Color(bgs[bg])); G.fillRoundRect(x,y,cell,cell,24,24);
   Bitmap bm=Bitmap.createBitmap(cell,cell,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
   Icons ic=Icons.of(new android.content.Context(),"refresh",acc[r],24); ic.setBounds(cell/2-sz/2,cell/2-sz/2,cell/2+sz/2,cell/2+sz/2); ic.draw(c);
   G.drawImage(bm.img,x,y,null);} }
 javax.imageio.ImageIO.write(img,"png",new java.io.File(a[0])); } }
