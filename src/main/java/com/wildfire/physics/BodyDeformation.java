package com.wildfire.physics;

import com.wildfire.main.entitydata.BodySettings;

/** A continuous displacement field in the original skeleton's local pixel coordinates. */
public final class BodyDeformation {
    private BodyDeformation() {}
    public enum Part { TORSO, LEFT_LEG, RIGHT_LEG }
    public record Point(double x, double y, double z) {}
    public static double smooth(double t) { t = Math.clamp(t, 0, 1); return t*t*(3-2*t); }
    private static double bell(double y, double center, double radius) {
        double t = (y-center)/radius;
        return Math.abs(t) >= 1 ? 0 : (1-t*t)*(1-t*t);
    }

    public static Point deform(Part part, double x, double y, double z, BodySettings s, double bounce, double depth) {
        double bodyY = part == Part.TORSO ? y : 12 + y;
        double sign = part == Part.LEFT_LEG ? 1 : -1;
        double bodyX = part == Part.TORSO ? x : x + sign*2;
        // The gluteal volume straddles the pelvis, rather than sitting halfway down each thigh.
        // Two broad ovoid lobes blend into the sacrum above and the upper thigh below.
        double lateral=(Math.abs(bodyX)-1.85)/2.7;
        double extent=Math.sqrt(Math.max(0,1-lateral*lateral));
        double pelvicHeight=extent==0?0:bell(bodyY,11.8,(bodyY<=11.8?3.8:2.9)*extent);
        double lobe = bell(Math.abs(bodyX),1.85,2.7);
        double rearWeight = smooth((z+.2)/2.2)*pelvicHeight*lobe;
        double rear = s.buttocks()*1.65*rearWeight;
        // Pin the shared torso/leg edge during motion, and keep the center cleft stable.
        double moving = rearWeight*smooth(Math.abs(bodyY-12)/1.1)*smooth(Math.abs(bodyX)/.8);
        if (part == Part.TORSO) {
            double waist = bell(y, 7.2, 4.2);
            double hip = smooth((y-8)/4);
            double scale = 1 - .18*s.waist()*waist + .14*s.hips()*hip;
            // Upper chest/shoulders are pinned. Lower torso blends into widened upper legs.
            return new Point(x*scale, y+bounce*moving,
                    z*(1-.08*s.waist()*waist + .06*s.hips()*hip)+rear+depth*moving);
        }
        double height = Math.clamp(y, 0, 12);
        double upper = bell(height, 3.5, 3.5);
        double hip = 1-smooth(height/5);
        double width = .14*s.hips()*hip + .1*s.thighs()*bell(height, 2.8, 2.8);
        // The inner seam remains at x=0 in the neutral pose; knees/boots retain their vanilla shape.
        double outX = bodyX*(1+width)-sign*2;
        double outZ = z*(1+.06*s.hips()*hip+.07*s.thighs()*upper) + rear + depth*moving;
        return new Point(outX, y + bounce*moving, outZ);
    }
}
