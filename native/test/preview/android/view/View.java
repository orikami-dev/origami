package android.view;
public class View { int w,h; public View(android.content.Context c){} public int getWidth(){return w;} public int getHeight(){return h;}
 public void layout(int l,int t,int r,int b){w=r-l;h=b-t;} public void invalidate(){} protected void onDraw(android.graphics.Canvas c){} }
