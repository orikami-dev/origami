package android.graphics;
public class Color { public static int red(int c){return (c>>16)&255;} public static int green(int c){return (c>>8)&255;} public static int blue(int c){return c&255;} public static int alpha(int c){return (c>>>24);}
 public static int rgb(int r,int g,int b){return 0xFF000000|(r<<16)|(g<<8)|b;} }
