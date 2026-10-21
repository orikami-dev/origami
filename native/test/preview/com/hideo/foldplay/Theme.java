package com.hideo.foldplay;
import android.graphics.Color;
final class Theme { static String THEME_ID="codec"; static int CODEC=0xFF4CFFA0; static final int CODEC0=0xFF4CFFA0, PANEL0=0xFF181A21, STEEL0=0xFF3A3F4B; static int LOGO=0; static boolean ANIMATE=false; static boolean PAD_OWN;
 static final String[] THEMES={"codec","arctique","ecarlate","sakura","ambre"};
 static final int[] FOLDS={0xFFFFD23F,0xFFEAF9FF,0xFFFFB347,0xFFFFF0F7,0xFFFF7A2E};
 static final int[][] PAL={{0xFF0E0F13,0xFF181A21,0,0xFF4CFFA0},{0xFF0B1016,0xFF141C26,0,0xFF5CD6FF},{0xFF120C0D,0xFF1E1416,0,0xFFFF5A4E},{0xFF120E12,0xFF1E171E,0,0xFFFF8AC8},{0xFF110F0A,0xFF1D1A12,0,0xFFFFC14D}};
 static int themeIndex(String id){return idx(id);} static int idx(String id){for(int i=0;i<5;i++) if(THEMES[i].equals(id)) return i; return 0;}
 static int fold(){return FOLDS[idx(THEME_ID)];} static int logo(int f){return f;}
 static int lighten(int c,float a){return Color.rgb((int)(Color.red(c)+(255-Color.red(c))*a),(int)(Color.green(c)+(255-Color.green(c))*a),(int)(Color.blue(c)+(255-Color.blue(c))*a));}
 static int alpha(int c,int a){return (c&0xFFFFFF)|(a<<24);} static int blend(int a,int b,float t){return Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*t),Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*t),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
 static int darken(int c,float a){return Color.rgb((int)(Color.red(c)*(1-a)),(int)(Color.green(c)*(1-a)),(int)(Color.blue(c)*(1-a)));} }
