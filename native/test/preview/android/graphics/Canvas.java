package android.graphics;
import java.awt.*; import java.awt.geom.*;
public class Canvas { public Graphics2D g; java.awt.image.BufferedImage base; java.util.ArrayDeque<Object[]> st=new java.util.ArrayDeque<Object[]>();
 public Canvas(Bitmap b){base=b.img; g=b.img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);}
 void use(android.graphics.Paint p){ g.setComposite(p.xfer==null?AlphaComposite.SrcOver:p.xfer.mode==PorterDuff.Mode.CLEAR?AlphaComposite.Clear:AlphaComposite.SrcAtop); if(p.shader!=null) g.setPaint(p.shader.awt); else g.setColor(new java.awt.Color(p.color,true)); g.setStroke(p.dash!=null?new BasicStroke(p.sw,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,10f,p.dash,0f):new BasicStroke(p.sw,p.cap==android.graphics.Paint.Cap.SQUARE?BasicStroke.CAP_SQUARE:BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)); }
 void shape(Shape s, android.graphics.Paint p){ use(p); if(p.style==android.graphics.Paint.Style.FILL) g.fill(s); else g.draw(s);}
 public int save(){ st.push(new Object[]{g.getTransform(),g.getClip()}); return st.size();}
 public int saveLayerAlpha(float l,float t,float r,float b,int a){ java.awt.image.BufferedImage li=new java.awt.image.BufferedImage(base.getWidth(),base.getHeight(),2); Graphics2D lg=li.createGraphics(); lg.setRenderingHints(g.getRenderingHints()); lg.setTransform(g.getTransform()); lg.setClip(g.getClip()); st.push(new Object[]{g.getTransform(),g.getClip(),g,li,a}); g=lg; return st.size();}
 public int saveLayer(float l,float t,float r,float b,android.graphics.Paint p){return saveLayerAlpha(l,t,r,b,255);}
 public void restore(){Object[] o=st.pop(); if(o.length>2){ Graphics2D prev=(Graphics2D)o[2]; AffineTransform tr=prev.getTransform(); Shape cl=prev.getClip(); prev.setTransform(new AffineTransform()); prev.setClip(null); prev.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,((Integer)o[4])/255f)); prev.drawImage((java.awt.image.BufferedImage)o[3],0,0,null); prev.setComposite(AlphaComposite.SrcOver); prev.setTransform(tr); prev.setClip(cl); g=prev;} g.setTransform((AffineTransform)o[0]); g.setClip((Shape)o[1]);}
 public void restoreToCount(int n){while(st.size()>=n) restore();}
 public void translate(float x,float y){g.translate(x,y);} public void scale(float x,float y){g.scale(x,y);} public void rotate(float d){g.rotate(Math.toRadians(d));}
 public boolean clipPath(Path p){g.clip(p.g);return true;}
 public void drawRect(float l,float t,float r,float b,android.graphics.Paint p){shape(new Rectangle2D.Float(l,t,r-l,b-t),p);}
 public void drawCircle(float x,float y,float r,android.graphics.Paint p){shape(new Ellipse2D.Float(x-r,y-r,2*r,2*r),p);}
 public void drawOval(RectF o,android.graphics.Paint p){shape(new Ellipse2D.Float(o.left,o.top,o.width(),o.height()),p);}
 public void drawLine(float a,float b,float c,float d,android.graphics.Paint p){ android.graphics.Paint.Style s=p.style; p.style=android.graphics.Paint.Style.STROKE; shape(new Line2D.Float(a,b,c,d),p); p.style=s;}
 public void drawRoundRect(RectF o,float rx,float ry,android.graphics.Paint p){shape(new RoundRectangle2D.Float(o.left,o.top,o.width(),o.height(),rx*2,ry*2),p);}
 public void drawRect(RectF o,android.graphics.Paint p){drawRect(o.left,o.top,o.right,o.bottom,p);}
 public void drawArc(RectF o,float st,float sw,boolean c,android.graphics.Paint p){shape(new Arc2D.Float(o.left,o.top,o.width(),o.height(),-st,-sw,Arc2D.OPEN),p);}
 public void drawPath(Path pa,android.graphics.Paint p){shape(pa.g,p);}
 public void drawText(String s,float x,float y,android.graphics.Paint p){ use(p); g.setFont(new Font(Font.MONOSPACED,Font.BOLD,Math.round(p.textSize))); float w=g.getFontMetrics().stringWidth(s); if(p.align==android.graphics.Paint.Align.CENTER) x-=w/2; else if(p.align==android.graphics.Paint.Align.RIGHT) x-=w; g.drawString(s,x,y);}
 public void drawBitmap(Bitmap b,float x,float y,android.graphics.Paint p){g.drawImage(b.img,(int)x,(int)y,null);} }
