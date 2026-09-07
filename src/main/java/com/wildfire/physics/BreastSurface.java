package com.wildfire.physics;

import com.wildfire.main.entitydata.BodySettings.BreastShape;

/** Paired smooth volumes with a shared medial attachment and a separate upper/lower pole profile. */
public final class BreastSurface {
    private BreastSurface() {}
    private static double lobe(double x,double v,double center,BreastShape shape) {
        double lateral=(x-center)/2.15;
        if(Math.abs(lateral)>=1) return 0;
        double extent=Math.sqrt(1-lateral*lateral);
        double t=.5+(v-.5)/extent;
        if(t<=0 || t>=1) return 0;
        double pole;
        if(shape==BreastShape.NATURAL) {
            double peak=.58;
            if(t<=peak) pole=BodyDeformation.smooth(t/peak);
            else {
                double lower=(t-peak)/(1-peak);
                pole=Math.sqrt(Math.max(0,1-lower*lower))*BodyDeformation.smooth((1-lower)/.18);
            }
        } else {
            double round=2*t-1;
            pole=Math.sqrt(Math.max(0,1-round*round))*BodyDeformation.smooth((1-Math.abs(round))/.18);
        }
        return extent*pole*BodyDeformation.smooth((1-lateral*lateral)/.2);
    }
    public static BodyDeformation.Point point(boolean left, double u, double v, double size, BreastShape shape,
                                               double lateral, double vertical, double inflate) {
        return point(left,u,v,size,shape,lateral,vertical,inflate,0,0,0,0);
    }
    public static BodyDeformation.Point point(boolean left, double u, double v, double size, BreastShape shape,
                                               double lateral, double vertical, double inflate,
                                               double offsetX, double offsetY, double offsetZ, double cleavage) {
        double x = (left ? -4 : 0)+u*4;
        // Elliptical attachment footprints give each lower contour its own rounded outline.
        // A smooth union joins overlapping medial surfaces without a gap or double volume.
        double a=lobe(x,v,1.85,shape),b=lobe(x,v,-1.85,shape);
        double dome=Math.pow(a*a*a*a+b*b*b*b,.25);
        double depth=Math.clamp(size,0,1.2)*2.65*dome;
        // The two halves share a shallow chest bridge. Opposite spring phases must not split it.
        double mobile=dome*BodyDeformation.smooth(Math.abs(x)/.85);
        double y=2.1+v*5.7;
        x=x*(1+inflate/4)+lateral*mobile+(left?1:-1)*offsetX*mobile
                +(left?-1:1)*depth*Math.sin(Math.toRadians(cleavage*100))*BodyDeformation.smooth(Math.abs(x)/.85);
        y=5+(y-5)*(1+inflate/6)+(vertical-offsetY)*mobile;
        return new BodyDeformation.Point(x,y,-2-inflate-depth+offsetZ*mobile);
    }
}
