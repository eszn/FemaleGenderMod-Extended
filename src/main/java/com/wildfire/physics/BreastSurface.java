package com.wildfire.physics;

import com.wildfire.main.entitydata.BodySettings.BreastShape;

/** Continuous chest-wall deformation with a sloping upper pole and broad, rounded lower volume. */
public final class BreastSurface {
    private BreastSurface() {}
    public static double height(double size) { return 3.2+2.2*Math.clamp(size,0,1.2); }
    private static double lobe(double x,double y,double center,double size,BreastShape shape) {
        double radius=1.3+.45*size;
        double d=(x-center)/radius;
        double cross=Math.exp(-d*d);
        double span=height(size)*(.70+.30*cross);
        double top=1.55+height(size)*.12*(1-cross);
        double v=(y-top)/span;
        if(v<=0 || v>=1) return 0;
        double peak=shape==BreastShape.NATURAL?.60:.48;
        double pole;
        if(v<peak) {
            double upper=v/peak;
            pole=shape==BreastShape.NATURAL?BodyDeformation.smooth(upper):Math.sin(upper*Math.PI/2);
        } else {
            double lower=(v-peak)/(1-peak);
            double round=1-Math.pow(lower,4);
            pole=round*round;
        }
        return cross*pole;
    }
    public static BodyDeformation.Point deform(double x,double y,double z,double size,BreastShape shape,
            double leftX,double leftY,double rightX,double rightY,double offsetX,double offsetY,double offsetZ,double cleavage) {
        size=Math.clamp(size,0,1.2);
        if(size==0) return new BodyDeformation.Point(x,y,z);
        // All front and side faces share this field. There is no attached rim or duplicate chest plane.
        double front=BodyDeformation.smooth((2-z)/4);
        double a=lobe(x,y,-1.65,size,shape),b=lobe(x,y,1.65,size,shape);
        double volume=Math.pow(a*a*a*a+b*b*b*b,.25);
        double depth=2.65*size*volume*front;
        double t=(y-1.55)/height(size);
        double envelope=t<=0 || t>=1?0:Math.pow(Math.sin(Math.PI*t),2);
        double mobile=volume*front*BodyDeformation.smooth(Math.abs(x)/.8);
        double side=x<0?-1:1;
        double dx=x<0?leftX:rightX,dy=x<0?leftY:rightY;
        return new BodyDeformation.Point(
                x*(1+.22*size*envelope*front)+dx*mobile-side*offsetX*mobile
                        +side*depth*Math.sin(Math.toRadians(cleavage*100))*BodyDeformation.smooth(Math.abs(x)/.8),
                y+(dy-offsetY)*mobile,z-depth+offsetZ*mobile);
    }
    public static BodyDeformation.Point point(boolean left,double u,double v,double size,BreastShape shape,
            double lateral,double vertical,double inflate) {
        return point(left,u,v,size,shape,lateral,vertical,inflate,0,0,0,0);
    }
    public static BodyDeformation.Point point(boolean left,double u,double v,double size,BreastShape shape,
            double lateral,double vertical,double inflate,double offsetX,double offsetY,double offsetZ,double cleavage) {
        return deform((left?-4:0)+u*4,1.55+v*height(size),-2-inflate,size,shape,
                lateral,vertical,lateral,vertical,offsetX,offsetY,offsetZ,cleavage);
    }
}
