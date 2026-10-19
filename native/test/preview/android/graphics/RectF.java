package android.graphics;
public class RectF { public float left,top,right,bottom; public RectF(){} public RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
 public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;} public void set(Rect r){left=r.left;top=r.top;right=r.right;bottom=r.bottom;} public float width(){return right-left;} public float height(){return bottom-top;} public float centerX(){return (left+right)/2;} }
