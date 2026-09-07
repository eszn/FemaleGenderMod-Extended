package com.wildfire.physics;

import com.wildfire.main.entitydata.BodySettings;

/** Shared force calculations for the live entity adapters and offline mesh previews. */
public final class SecondaryMotion {
    private SecondaryMotion() {}
    public record Axis(double acceleration,double frequency,double damping,double limit) {
        public void tick(DampedSpring spring) { spring.tick(acceleration,frequency,damping,limit); }
    }
    public record Response(Axis first,Axis second,Axis third) {}
    public static Response body(MotionInput.Sample motion,BodySettings settings,double support,
                                boolean water,boolean grounded,float walkPosition,float walkSpeed) {
        double gain=settings.motion()*settings.buttocks()*(1-support)*(water?.35:1);
        double frequency=3.8+support*2, damping=.52+support*.65, limit=Math.min(.9,1.6*gain);
        double gait=grounded?Math.sin(walkPosition*2)*Math.min(walkSpeed,1)*650:0;
        return new Response(new Axis((motion.vertical()*28+motion.turn()*55+gait)*gain,frequency,damping,limit),
                new Axis((motion.vertical()*28-motion.turn()*55-gait)*gain,frequency,damping,limit),
                new Axis(-motion.forward()*20*gain,frequency+.7,damping,limit*.6));
    }
    public static Response breast(MotionInput.Sample motion,float target,float bounce,float floppiness,int side,
                                  double support,boolean water,boolean grounded,float walkPosition,float walkSpeed) {
        double gain=Math.clamp(bounce*3,0,1.5)*(1-support)*(water?.35:1);
        double frequency=4.8-1.2*Math.clamp(target,0,1.2)+support*3;
        double damping=.52+.16*(1-floppiness)+support*.65,limit=(.18+.65*target)*gain;
        double gait=grounded?Math.sin(walkPosition*2+side*.12)*Math.min(walkSpeed,1)*220:0;
        return new Response(new Axis((motion.vertical()*16+gait)*gain,frequency,damping,limit),
                new Axis((-motion.lateral()*12-motion.turn()*18)*gain,frequency+.8,damping+.1,limit*.5),
                new Axis(motion.turn()*.7*gain,frequency+1,damping+.1,4*gain));
    }
}
