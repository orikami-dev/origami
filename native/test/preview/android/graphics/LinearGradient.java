package android.graphics;
public class LinearGradient extends Shader { public LinearGradient(float x0,float y0,float x1,float y1,int c0,int c1,TileMode m){ if(x0==x1&&y0==y1)y1+=1; awt=new java.awt.GradientPaint(x0,y0,new java.awt.Color(c0,true),x1,y1,new java.awt.Color(c1,true)); } }
