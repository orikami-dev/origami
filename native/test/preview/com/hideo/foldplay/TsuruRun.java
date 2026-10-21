package com.hideo.foldplay;
import android.graphics.*;
import java.awt.BasicStroke; import java.awt.Font; import java.awt.FontMetrics; import java.awt.Graphics2D; import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D; import java.awt.geom.Path2D; import java.awt.geom.RoundRectangle2D;
/**
 * Aperçu « mode Tsuru » : fond d'écran du haut (ThemeBackdrop, fonds nuit) et, dessous, la manette d'accueil
 * avec la façade du thème (PadSkins, comme ControlDeckView.drawSkin) et ses touches par-dessus.
 * java TsuruRun dossier [old] : écrit <thème>-pad-new.png pour chaque thème et la planche pads-new.png
 * (Ambre en référence). Avec « old », les anciennes façades (drapeaux de PadSkins éteints), fichiers -pad-old.
 */
public class TsuruRun {
 static final String[] TH={"codec","arctique","ecarlate","sakura","ambre"};
 static final String[] NM={"Codec","Arctique","Écarlate","Sakura","Ambre"};
 static final int W=1100, TOP=640, PAD=600, GAP=14; static final float DP=1.6f;

 static java.awt.image.BufferedImage shot(int i){
  Theme.THEME_ID=TH[i]; int[] pal=PAL[i]; Theme.CODEC=pal[3];
  Bitmap bm=Bitmap.createBitmap(W,TOP+GAP+PAD,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
  Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(pal[0]); c.drawRect(0,0,W,TOP+GAP+PAD,p);
  ThemeBackdrop tb=new ThemeBackdrop(TH[i],pal[0],pal[3],28*DP,Theme.fold()).squareTop(true); tb.setBounds(0,0,W,TOP); tb.draw(c);
  // Manette : panneau, façade du thème, liseré d'accent, bord fin (ControlDeckView.drawSkin).
  c.save(); c.translate(0,TOP+GAP); RectF box=new RectF(0,0,W,PAD); float rad=28*DP;
  Path cl=new Path(); cl.addRoundRect(box,rad,rad,Path.Direction.CW); c.clipPath(cl);
  p.setColor(pal[1]); c.drawRect(box,p);
  p.setShader(new LinearGradient(0,0,0,PAD,0x18FFFFFF,0x22000000,Shader.TileMode.CLAMP)); c.drawRect(box,p); p.setShader(null);
  PadSkins.draw(c,box,TH[i],pal[3],DP);
  int rgb=pal[3]&0xFFFFFF;
  p.setShader(new LinearGradient(0,0,W,0,new int[]{rgb,rgb|(0x40<<24),rgb},null,Shader.TileMode.CLAMP)); c.drawRect(rad*0.6f,0,W-rad*0.6f,7*DP,p);
  p.setShader(new LinearGradient(0,0,W,0,new int[]{rgb,rgb|(0xCC<<24),rgb},null,Shader.TileMode.CLAMP)); c.drawRect(rad*0.6f,0,W-rad*0.6f,1.5f*DP,p); p.setShader(null);
  c.restore();
  keys(c.g,0,TOP+GAP,W,PAD,pal);
  return bm.img; }

 /** Touches de la manette d'accueil (L, R, origami, croix, ABXY), aux couleurs du thème. */
 static void keys(Graphics2D g,float ox,float oy,float w,float h,int[] pal){
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  java.awt.Color steel=new java.awt.Color(pal[4]), acc=new java.awt.Color(pal[3]), ink=new java.awt.Color(0xE8EAEE), bg=new java.awt.Color(pal[0]);
  float m=14*DP, pw=60*DP, ph=26*DP;
  g.setColor(steel); g.fill(new RoundRectangle2D.Float(ox+m,oy+m+4*DP,pw,ph,ph*0.5f,ph*0.5f)); text(g,"L",ox+m+pw/2,oy+m+4*DP+ph/2,13*DP,ink);
  g.setColor(steel); g.fill(new RoundRectangle2D.Float(ox+w-m-pw,oy+m+4*DP,pw,ph,ph*0.5f,ph*0.5f)); text(g,"R",ox+w-m-pw/2,oy+m+4*DP+ph/2,13*DP,ink);
  float hx=ox+w/2, hy=oy+m+4*DP+ph/2+6*DP, hr=19*DP;
  g.setColor(bg); g.fill(new Ellipse2D.Float(hx-hr,hy-hr,2*hr,2*hr)); g.setColor(acc); g.setStroke(new BasicStroke(2*DP)); g.draw(new Ellipse2D.Float(hx-hr,hy-hr,2*hr,2*hr));
  Path2D t=new Path2D.Float(); t.moveTo(hx-hr*0.5,hy+hr*0.2); t.lineTo(hx,hy-hr*0.5); t.lineTo(hx+hr*0.55,hy+hr*0.15); t.lineTo(hx,hy+hr*0.05); t.closePath(); g.fill(t);
  float k=40*DP, dx=ox+m*2+k*1.5f, dy=oy+h*0.62f;
  g.setColor(steel); g.fill(new RoundRectangle2D.Float(dx-k/2,dy-k*1.5f,k,3*k,8*DP,8*DP)); g.fill(new RoundRectangle2D.Float(dx-k*1.5f,dy-k/2,3*k,k,8*DP,8*DP));
  float r=23*DP, s=46*DP, bx=ox+w-m*2-s-r, by=oy+h*0.6f;
  String[] L={"Y","X","B","A"}; float[][] P={{0,-1},{-1,0},{1,0},{0,1}};
  for(int i=0;i<4;i++){ float cx=bx+P[i][0]*s, cy=by+P[i][1]*s; boolean a=i==3; g.setColor(a?acc:steel); g.fill(new Ellipse2D.Float(cx-r,cy-r,2*r,2*r)); text(g,L[i],cx,cy,r*0.85f,a?bg:ink); } }

 static void text(Graphics2D g,String s,float cx,float cy,float size,java.awt.Color col){ g.setColor(col); g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,Math.max(6,Math.round(size)))); FontMetrics fm=g.getFontMetrics(); g.drawString(s,cx-fm.stringWidth(s)/2f,cy+(fm.getAscent()-fm.getDescent())/2f);}

 static final int[][] PAL={{0xFF0E0F13,0xFF181A21,0,0xFF4CFFA0,0xFF343946},{0xFF0B1016,0xFF141C26,0,0xFF5CD6FF,0xFF34414F},{0xFF120C0D,0xFF1E1416,0,0xFFFF5A4E,0xFF46353A},{0xFF120E12,0xFF1E171E,0,0xFFFF8AC8,0xFF443848},{0xFF110F0A,0xFF1D1A12,0,0xFFFFC14D,0xFF45402F}};

 public static void main(String[] a) throws Exception { String d=a[0]; boolean old=a.length>1&&a[1].equals("old");
  ThemeBackdrop.CODEC_BASE=true; ThemeBackdrop.ARCTIQUE_POLAIRE=true; ThemeBackdrop.ECARLATE_JAPON=true; ThemeBackdrop.SAKURA_RIVIERE=true;
  PadSkins.CODEC_PAD_BASE=!old; PadSkins.ARCTIQUE_PAD_POLAIRE=!old; PadSkins.ECARLATE_PAD_JAPON=!old; PadSkins.SAKURA_PAD_RIVIERE=!old;
  String suf=old?"-pad-old.png":"-pad-new.png";
  java.awt.image.BufferedImage[] im=new java.awt.image.BufferedImage[TH.length];
  for(int i=0;i<TH.length;i++){ im[i]=shot(i); if(i<4) javax.imageio.ImageIO.write(im[i],"png",new java.io.File(d+"/"+TH[i]+suf)); }
  // Planche : les cinq thèmes côte à côte, réduits de moitié, Ambre (référence) au bout.
  int sw=W/2, shh=(TOP+GAP+PAD)/2, g=20, lab=34;
  java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(g+TH.length*(sw+g),lab+shh+g,java.awt.image.BufferedImage.TYPE_INT_RGB);
  Graphics2D G=sheet.createGraphics(); G.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC); G.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  G.setColor(new java.awt.Color(0x08090C)); G.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
  for(int i=0;i<TH.length;i++){ int x=g+i*(sw+g); G.setColor(new java.awt.Color(0xE8EAEE)); G.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18)); G.drawString(NM[i]+(i==4?" (référence)":""),x,24); G.drawImage(im[i],x,lab,sw,shh,null); }
  javax.imageio.ImageIO.write(sheet,"png",new java.io.File(d+(old?"/pads-old.png":"/pads-new.png"))); } }
