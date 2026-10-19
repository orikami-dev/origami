package android.graphics;
public class RadialGradient extends Shader {
 public RadialGradient(float x,float y,float r,int c0,int c1,TileMode m){ this(x,y,r,new int[]{c0,c1},new float[]{0f,1f},m);}
 public RadialGradient(float x,float y,float r,int[] cs,float[] st,TileMode m){ java.awt.Color[] a=new java.awt.Color[cs.length]; for(int i=0;i<cs.length;i++)a[i]=new java.awt.Color(cs[i],true); float[] s=st.clone(); for(int i=1;i<s.length;i++) if(s[i]<=s[i-1]) s[i]=s[i-1]+1e-4f; awt=new java.awt.RadialGradientPaint(x,y,r,s,a);} }
