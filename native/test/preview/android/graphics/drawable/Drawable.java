package android.graphics.drawable;
import android.graphics.*;
public abstract class Drawable { Rect b=new Rect(0,0,0,0); public int getIntrinsicWidth(){return -1;} public int getIntrinsicHeight(){return -1;} public Rect getBounds(){return b;} public void setBounds(int l,int t,int r,int bo){b=new Rect(l,t,r,bo);} public void invalidateSelf(){} public boolean setVisible(boolean v,boolean r){return true;} public boolean isVisible(){return true;} public void unscheduleSelf(Runnable r){} public void scheduleSelf(Runnable r,long t){}
 public abstract void draw(Canvas c); public abstract void setAlpha(int a); public abstract void setColorFilter(ColorFilter f); public abstract int getOpacity(); public void getOutline(Outline o){} }
