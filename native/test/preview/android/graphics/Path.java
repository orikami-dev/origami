package android.graphics;
public class Path { public enum Direction{CW} public java.awt.geom.Path2D.Float g=new java.awt.geom.Path2D.Float();
 public void reset(){g.reset();} public void moveTo(float x,float y){g.moveTo(x,y);} public void lineTo(float x,float y){g.lineTo(x,y);} public void close(){g.closePath();}
 public boolean isEmpty(){return g.getCurrentPoint()==null;}
 public void addRoundRect(RectF r,float[] rad,Direction d){addRoundRect(r,rad[4],rad[4],d);}
 public void cubicTo(float a,float b,float c,float d,float e,float f){g.curveTo(a,b,c,d,e,f);}
 public void addRoundRect(RectF r,float rx,float ry,Direction d){g.append(new java.awt.geom.RoundRectangle2D.Float(r.left,r.top,r.width(),r.height(),rx*2,ry*2),false);} }
