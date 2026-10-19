package android.graphics;
public class Paint { public static final int ANTI_ALIAS_FLAG=1; public enum Style{FILL,STROKE} public enum Cap{ROUND,BUTT,SQUARE} public enum Join{ROUND,MITER} public float[] dash; public Cap cap=Cap.ROUND;
 public int color=0xFF000000; public Style style=Style.FILL; public float sw=1; public Shader shader; public ColorFilter cf;
 public Paint(){} public Paint(int f){}
 public Paint(Paint o){color=o.color;style=o.style;sw=o.sw;shader=o.shader;cap=o.cap;xfer=o.xfer;}
 public Xfermode xfer; public Xfermode setXfermode(Xfermode x){xfer=x;return x;} public Xfermode getXfermode(){return xfer;}
 public enum Align{LEFT,CENTER,RIGHT} public Align align=Align.LEFT; public float textSize=12;
 public void setTextAlign(Align a){align=a;} public void setTextSize(float t){textSize=t;} public float getTextSize(){return textSize;}
 public Typeface setTypeface(Typeface t){return t;}
 public float measureText(String s){java.awt.image.BufferedImage i=new java.awt.image.BufferedImage(1,1,2); java.awt.Graphics2D g=i.createGraphics(); g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED,java.awt.Font.BOLD,Math.round(textSize))); return g.getFontMetrics().stringWidth(s);}
 public void setColor(int c){color=c;} public void setAlpha(int a){color=(color&0xFFFFFF)|(a<<24);} public void setStyle(Style s){style=s;} public void setStrokeWidth(float w){sw=w;} public float getStrokeWidth(){return sw;}
 public void setStrokeCap(Cap c){cap=c;} public PathEffect setPathEffect(PathEffect e){dash=e==null?null:e.d;return e;} public int getColor(){return color;} public void setStrokeJoin(Join j){} public Shader setShader(Shader s){shader=s;return s;} public ColorFilter setColorFilter(ColorFilter f){cf=f;return f;} }
