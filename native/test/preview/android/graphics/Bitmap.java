package android.graphics;
public class Bitmap { public enum Config{ARGB_8888} public java.awt.image.BufferedImage img;
 public static Bitmap createBitmap(int w,int h,Config c){Bitmap b=new Bitmap(); b.img=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB); return b;} }
