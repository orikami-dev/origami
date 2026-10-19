package com.hideo.foldplay;
import android.graphics.*;
public class RedRun { public static void run(String out) throws Exception {
 int W=620,H=760,cols=2; Theme.THEME_ID="ecarlate"; Theme.CODEC=0xFFFF5A4E;
 java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(W*cols+20*(cols+1),H+40,java.awt.image.BufferedImage.TYPE_INT_ARGB);
 java.awt.Graphics2D G=img.createGraphics(); G.setColor(new java.awt.Color(0xFF050608)); G.fillRect(0,0,img.getWidth(),img.getHeight());
 for(int v=0;v<cols;v++){ Bitmap bm=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
  for(int half=0;half<2;half++){ c.save(); c.translate(0,half*(H/2+6)); float hh=H/2-6; Path cl=new Path(); cl.addRoundRect(new RectF(0,0,W,hh),40,40,Path.Direction.CW); c.clipPath(cl);
   Paint p=new Paint(); p.setColor(half==0?0xFF120C0D:0xFF1E1416); c.drawRect(0,0,W,hh,p);
   if(half==0){ p.setShader(new RadialGradient(W*0.5f,hh*1.1f,W*0.75f,0x30FF5A4E,0x00FF5A4E,Shader.TileMode.CLAMP)); c.drawRect(0,0,W,hh,p);}
   Facets.ecarlate(c,W,hh,Theme.CODEC,Theme.fold(),half==0?1f:0.75f,v); c.restore(); }
  Crane.Logo lg=new Crane.Logo(); lg.setBounds(30,20,130,120); lg.draw(c);
  G.drawImage(bm.img,20+v*(W+20),20,null);}
 javax.imageio.ImageIO.write(img,"png",new java.io.File(out)); } }
