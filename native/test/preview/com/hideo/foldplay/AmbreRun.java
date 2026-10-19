package com.hideo.foldplay;
import android.graphics.*;
public class AmbreRun { public static void run(String out) throws Exception {
 int W=520,H=760,cols=3; Theme.THEME_ID="ambre"; Theme.CODEC=0xFFFFC14D;
 java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(W*cols+20*(cols+1),H+40,java.awt.image.BufferedImage.TYPE_INT_ARGB);
 java.awt.Graphics2D G=img.createGraphics(); G.setColor(new java.awt.Color(0xFF050608)); G.fillRect(0,0,img.getWidth(),img.getHeight());
 for(int v=0;v<cols;v++){ Bitmap bm=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
  for(int half=0;half<2;half++){ c.save(); c.translate(0,half*(H/2+6)); float hh=H/2-6; Path cl=new Path(); cl.addRoundRect(new RectF(0,0,W,hh),40,40,Path.Direction.CW); c.clipPath(cl);
   Paint p=new Paint(); p.setColor(half==0?0xFF110F0A:0xFF1D1A12); c.drawRect(0,0,W,hh,p);
   if(half==0){ p.setShader(new RadialGradient(W*0.5f,hh*0.45f,W*0.65f,0x26FFC14D,0x00FFC14D,Shader.TileMode.CLAMP)); c.drawRect(0,0,W,hh,p); p.setShader(null);}
   Facets.ambre(c,W,hh,Theme.CODEC,Theme.fold(),half==0?1f:0.75f,v);
   if(half==0){ p.setShader(null); p.setColor(0x14000000); for(float y=0;y<hh;y+=4) c.drawRect(0,y,W,y+2,p); }
   c.restore(); }
  Crane.Logo lg=new Crane.Logo(); lg.setBounds(30,20,130,120); lg.draw(c);
  G.drawImage(bm.img,20+v*(W+20),20,null);}
 javax.imageio.ImageIO.write(img,"png",new java.io.File(out)); } }
