package com.wildfire.physics;

/** Round the outer cage corners while keeping the shoulder and knee connections in place. */
public final class BodyCage {
    private BodyCage() {}

    public static BodyDeformation.Point round(BodyDeformation.Part part,double x,double y,double z) {
        double sign=part==BodyDeformation.Part.LEFT_LEG?1:-1;
        double bx=part==BodyDeformation.Part.TORSO?x:x+sign*2;
        double by=part==BodyDeformation.Part.TORSO?y:12+y;
        double weight=BodyDeformation.smooth((by-1)/2)*(1-BodyDeformation.smooth((by-15)/4));
        double a=Math.max(0,Math.abs(bx)-3.4),b=Math.max(0,Math.abs(z)-1.4);
        if(weight==0 || a==0 || b==0) return new BodyDeformation.Point(x,y,z);
        // This radial projection maps all nested inflated cubes to nested rounded rectangles.
        double scale=Math.max(a,b)/Math.hypot(a,b);
        return new BodyDeformation.Point(x+Math.signum(bx)*a*(scale-1)*weight,y,
                z+Math.signum(z)*b*(scale-1)*weight);
    }
}
