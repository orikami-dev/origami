package com.hideo.foldplay;
import java.awt.*; import java.awt.geom.*; import java.awt.image.BufferedImage;
/** Aperçu des manettes Flex par console (ConsolePad + PadDesign), en portrait et en paysage. */
public class FlexRun {
 static final String[][] SYS={{"nes","NES"},{"snes","Super Nintendo"},{"n64","Nintendo 64"},{"gc","GameCube"},{"wii","Wii"},{"gb","Game Boy"},{"gbc","Game Boy Color"},{"gba","Game Boy Advance"},{"nds","Nintendo DS"},{"3ds","Nintendo 3DS"},{"switch","Switch"},{"psx","PlayStation"},{"ps2","PlayStation 2"},{"ps3","PlayStation 3"},{"psp","PSP"},{"vita","PS Vita"},{"x360","Xbox 360"},{"sms","Master System"},{"md","Mega Drive"},{"gg","Game Gear"},{"saturn","Saturn"},{"dc","Dreamcast"},{"pce","PC Engine"},{"arcade","Arcade"}};
 static final Color BG=new Color(0x0E0F13), PANEL=new Color(0x181A21), STEEL=new Color(0x3A3F4B), ACC=new Color(0x4CFFA0), TXT=new Color(0xE8EAEE), MUT=new Color(0x8A90A0);
 public static void main(String[] a) throws Exception { run(a[0], a.length > 1 && a[1].equals("land")); }
 public static void run(String out, boolean land) throws Exception {
  float cw=land?62:55, ch=land?50:24; int px=land?5:7; int cols=4; int tw=(int)(cw*px)+20, th=(int)(ch*px)+44;
  int rows=(SYS.length+cols-1)/cols; BufferedImage img=new BufferedImage(cols*tw,rows*th,BufferedImage.TYPE_INT_RGB);
  Graphics2D g=img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  g.setColor(new Color(0x08090C)); g.fillRect(0,0,img.getWidth(),img.getHeight());
  for(int i=0;i<SYS.length;i++){ int ox=(i%cols)*tw+10, oy=(i/cols)*th+30;
   g.setColor(TXT); g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,15)); g.drawString(SYS[i][1],ox,oy-10);
   g.setColor(land?new Color(0x1B2430):PANEL); g.fill(new RoundRectangle2D.Float(ox,oy,cw*px,ch*px,18,18));
   if(land){ g.setColor(MUT); g.setFont(new Font(Font.SANS_SERIF,Font.ITALIC,12)); g.drawString("jeu en plein écran",ox+cw*px/2-50,oy+ch*px/2-40);}
   ControlLayout l=ConsolePad.build(SYS[i][0]); PadDesign.Out o=new PadDesign.Out(l.controls);
   ConsolePad.place(l,o,cw,ch,land); float[][] b=o.done();
   if(b==null){ g.setColor(Color.RED); g.drawString("PLACEMENT MANQUANT",ox+10,oy+30); continue; }
   for(int k=0;k<b.length;k++) draw(g,l.controls.get(k),ox+b[k][0]*px,oy+b[k][1]*px,b[k][2]*px,b[k][3]*px,land);
  }
  javax.imageio.ImageIO.write(img,"png",new java.io.File(out)); }
 static void draw(Graphics2D g, ControlLayout.Control c, float x,float y,float w,float h, boolean land){
  Composite old=g.getComposite(); if(land) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.75f));
  String t=c.type, cap=c.cap; float cx=x+w/2, cy=y+h/2, r=Math.min(w,h)/2;
  boolean round=t.equals("A")||t.equals("B")||t.equals("X")||t.equals("Y")||(cap!=null&&cap.startsWith("@c"))||((t.equals("L")||t.equals("R")||t.equals("L2")||t.equals("R2"))&&Math.abs(w-h)<0.5f);
  if(t.equals("DPAD")){ float k=Math.min(w,h)/3; g.setColor(STEEL); g.fill(new RoundRectangle2D.Float(x+k,y,k,3*k,6,6)); g.fill(new RoundRectangle2D.Float(x,y+k,3*k,k,6,6)); }
  else if(t.startsWith("STICK")){ g.setColor(new Color(0x23262E)); g.fill(new Ellipse2D.Float(cx-r,cy-r,2*r,2*r)); g.setColor(STEEL); g.setStroke(new BasicStroke(2)); g.draw(new Ellipse2D.Float(cx-r+1,cy-r+1,2*r-2,2*r-2)); float kn=r*0.42f; g.fill(new Ellipse2D.Float(cx-kn,cy-kn,2*kn,2*kn)); text(g,t.endsWith("L")?"L":"R",cx,cy,kn*0.8f,MUT);}
  else if(t.equals("HOME")){ g.setColor(BG); g.fill(new Ellipse2D.Float(cx-r,cy-r,2*r,2*r)); g.setColor(ACC); g.setStroke(new BasicStroke(2)); g.draw(new Ellipse2D.Float(cx-r,cy-r,2*r,2*r)); Path2D p=new Path2D.Float(); p.moveTo(cx-r*0.5,cy+r*0.2); p.lineTo(cx,cy-r*0.5); p.lineTo(cx+r*0.55,cy+r*0.15); p.lineTo(cx,cy+r*0.05); p.closePath(); g.fill(p);}
  else if(round){ boolean pri=t.equals("A"); float rr=r*0.85f; g.setColor(pri?ACC:STEEL); g.fill(new Ellipse2D.Float(cx-rr,cy-rr,2*rr,2*rr)); Color ink=pri?BG:TXT; String s=cap!=null?cap:t; if(s.startsWith("@")) glyph(g,s,cx,cy,rr*0.46f,ink); else text(g,s,cx,cy,rr*(s.length()>1?0.7f:0.9f),ink);}
  else if(ControlLayout.pill(t)){ g.setColor(STEEL.darker()); g.fill(new RoundRectangle2D.Float(x,y,w,h,h,h)); String s=cap!=null?(cap.equals("@-")?"−":cap.equals("@+")?"+":cap):(t.equals("FAST")?"≫ ×2":t.equals("QSAVE")?"SAVE":t.equals("QLOAD")?"LOAD":t); text(g,s,cx,cy,Math.min(h*0.42f,w/(s.length()*0.75f)),MUT);}
  else { g.setColor(STEEL); g.fill(new RoundRectangle2D.Float(x,y,w,h,14,14)); String s=cap!=null?cap:(t.equals("L")?"L1":t.equals("R")?"R1":t); text(g,s,cx,cy,Math.min(h*0.5f,18),TXT);}
  g.setComposite(old);}
 static void text(Graphics2D g,String s,float cx,float cy,float size,Color col){ g.setColor(col); g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,Math.max(6,Math.round(size)))); FontMetrics fm=g.getFontMetrics(); g.drawString(s,cx-fm.stringWidth(s)/2f,cy+(fm.getAscent()-fm.getDescent())/2f);}
 static void glyph(Graphics2D g,String s,float cx,float cy,float a,Color col){ g.setColor(col); g.setStroke(new BasicStroke(Math.max(1.5f,a*0.24f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
  if(s.equals("@x")){ g.draw(new Line2D.Float(cx-a*0.8f,cy-a*0.8f,cx+a*0.8f,cy+a*0.8f)); g.draw(new Line2D.Float(cx+a*0.8f,cy-a*0.8f,cx-a*0.8f,cy+a*0.8f)); }
  else if(s.equals("@o")) g.draw(new Ellipse2D.Float(cx-a*0.85f,cy-a*0.85f,a*1.7f,a*1.7f));
  else if(s.equals("@s")) g.draw(new Rectangle2D.Float(cx-a*0.75f,cy-a*0.75f,a*1.5f,a*1.5f));
  else if(s.equals("@t")){ Path2D p=new Path2D.Float(); p.moveTo(cx,cy-a*0.9f); p.lineTo(cx+a*0.95f,cy+a*0.7f); p.lineTo(cx-a*0.95f,cy+a*0.7f); p.closePath(); g.draw(p);}
  else { int d=s.equals("@cu")?0:s.equals("@cr")?1:s.equals("@cd")?2:3; float b=a*0.75f; Path2D p=new Path2D.Float();
   if(d==0){p.moveTo(cx,cy-b);p.lineTo(cx+b,cy+b*0.7f);p.lineTo(cx-b,cy+b*0.7f);} else if(d==1){p.moveTo(cx+b,cy);p.lineTo(cx-b*0.7f,cy+b);p.lineTo(cx-b*0.7f,cy-b);} else if(d==2){p.moveTo(cx,cy+b);p.lineTo(cx-b,cy-b*0.7f);p.lineTo(cx+b,cy-b*0.7f);} else {p.moveTo(cx-b,cy);p.lineTo(cx+b*0.7f,cy-b);p.lineTo(cx+b*0.7f,cy+b);} p.closePath(); g.fill(p);} }
}
