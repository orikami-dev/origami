package android.graphics;
public class Rect { public int left,top,right,bottom; public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;} public int width(){return right-left;} public int height(){return bottom-top;}
 public float exactCenterX(){return (left+right)/2f;} public float exactCenterY(){return (top+bottom)/2f;} }
