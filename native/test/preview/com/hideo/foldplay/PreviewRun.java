package com.hideo.foldplay;
import android.graphics.*;
public class PreviewRun { public static void run(String out) throws Exception {
 String[] ids={"codec","arctique","ecarlate","sakura","ambre"}; int W=420,H=560; int cols=ids.length;
 java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(W*cols+20*(cols+1),H+40,java.awt.image.BufferedImage.TYPE_INT_ARGB);
 java.awt.Graphics2D G=img.createGraphics(); G.setColor(new java.awt.Color(0xFF050608)); G.fillRect(0,0,img.getWidth(),img.getHeight());
 for(int k=0;k<cols;k++){ String id=ids[k]; int i=Theme.idx(id); Theme.THEME_ID=id; Theme.CODEC=Theme.PAL[i][3];
  Bitmap bm=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(bm);
  ThemeBackdrop tb=new ThemeBackdrop(id,Theme.PAL[i][0],Theme.CODEC,40f); tb.setBounds(0,0,W,H/2-6); tb.draw(c);
  Crane.Logo lg=new Crane.Logo(); lg.setBounds(30,20,130,120); lg.draw(c);
  c.save(); c.translate(0,H/2+6); RectF box=new RectF(0,0,W,H/2-6); Paint p=new Paint(); p.setColor(Theme.PAL[i][1]);
  Path cl=new Path(); cl.addRoundRect(box,40,40,Path.Direction.CW); c.clipPath(cl); c.drawRect(0,0,W,H/2,p);
  PadSkins.draw(c,box,id,Theme.CODEC,2.2f); c.restore();
  G.drawImage(bm.img,20+k*(W+20),20,null);}
 javax.imageio.ImageIO.write(img,"png",new java.io.File(out)); } }
