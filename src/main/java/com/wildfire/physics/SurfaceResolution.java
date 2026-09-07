package com.wildfire.physics;

import java.util.ArrayList;
import java.util.Collections;

/** Keep fine lighting samples on nearby curves without subdividing flat shins and shoulder caps. */
public final class SurfaceResolution {
    private SurfaceResolution() {}
    public static int detail(double distanceSquared) { return distanceSquared<=6.25?0:distanceSquared<=144?1:2; }
    public static double spacing(int detail,boolean rear) { return (detail==0?.1:detail==1?.3:.8)*(rear?1:2); }
    public static float[] axis(double from,double to,boolean vertical,boolean leg,double spacing) {
        return axis(from,to,vertical,leg,spacing,false);
    }
    public static float[] axis(double from,double to,boolean vertical,boolean leg,double spacing,boolean chest) {
        double lo=Math.min(from,to),hi=Math.max(from,to);
        var splits=new ArrayList<Double>(); splits.add(lo);
        if(vertical) for(double cut:leg?new double[]{0,3,7}:chest?new double[]{1.55,9,12}:new double[]{3,8,12}) if(cut>lo && cut<hi) splits.add(cut);
        splits.add(hi); var samples=new ArrayList<Float>(); samples.add((float)((lo-from)/(to-from)));
        for(int i=1;i<splits.size();i++) {
            double a=splits.get(i-1),b=splits.get(i),mid=(a+b)/2;
            boolean flat=vertical && (leg?mid>=7:mid<=(chest?1.55:3));
            double step=vertical && (leg?mid>=3:chest?mid>=9:mid<8)?Math.max(.5,spacing):spacing;
            int count=flat?1:Math.max(1,(int)Math.ceil((b-a)/step));
            for(int n=1;n<=count;n++) samples.add((float)((a+(b-a)*n/count-from)/(to-from)));
        }
        if(from>to) Collections.reverse(samples);
        float[] result=new float[samples.size()]; for(int i=0;i<result.length;i++) result[i]=samples.get(i);
        return result;
    }
}
