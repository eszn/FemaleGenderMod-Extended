package com.wildfire.physics;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PhysicsTest {
    @Test void impactDecaysToRest() {
        DampedSpring spring=new DampedSpring();
        spring.tick(500,4,.7,.8);
        assertTrue(spring.position()>0);
        for(int i=0;i<100;i++) spring.tick(0,4,.7,.8);
        assertEquals(0,spring.position(),1e-9);
        assertEquals(0,spring.velocity(),1e-8);
    }
    @Test void millionsOfExtremeSubstepsStayFiniteAndConstrained() {
        var random=new Random(125);
        var spring=new DampedSpring();
        for(int i=0;i<200_000;i++) {
            spring.tick(random.nextGaussian()*10_000,.5+random.nextDouble()*11.5,random.nextDouble()*2,.01+random.nextDouble());
            assertTrue(Double.isFinite(spring.position())); assertTrue(Math.abs(spring.position())<=1.01);
            assertTrue(Double.isFinite(spring.velocity()));
        }
    }
    @Test void renderSamplingDoesNotAdvanceTheSolver() {
        var a=new DampedSpring(); var b=new DampedSpring();
        for(int tick=0;tick<100;tick++) {
            a.tick(Math.sin(tick)*100,4,.7,.8); b.tick(Math.sin(tick)*100,4,.7,.8);
            for(int frame=0;frame<240;frame++) a.sample(frame/240d);
        }
        assertEquals(a.position(),b.position()); assertEquals(a.velocity(),b.velocity());
    }
    @Test void invalidInputsAndRigidSupportResetMotion() {
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            var spring=new DampedSpring(); spring.tick(500,4,.7,.8); spring.tick(invalid,4,.7,.8);
            assertEquals(0,spring.position()); assertEquals(0,spring.previous());
        }
        var spring=new DampedSpring(); spring.tick(500,4,.7,.8); spring.tick(500,4,.7,0);
        assertEquals(0,spring.position());
    }
    @Test void interpolationCannotOvershootTickEndpoints() {
        var spring=new DampedSpring(); spring.tick(300,4,.7,.8);
        assertEquals(spring.previous(),spring.sample(-10)); assertEquals(spring.position(),spring.sample(10));
    }
    @Test void constantSpeedDoesNotProduceContinuousAcceleration() {
        var input=new MotionInput(); var world=new Object();
        input.update(0,0,0,0,0,world); input.update(.1,0,0,0,1,world);
        var sample=input.update(.2,0,0,0,2,world);
        assertEquals(0,sample.lateral(),1e-10); assertFalse(sample.reset());
    }
    @Test void teleportWorldChangeAndTickGapsReset() {
        var input=new MotionInput(); var world=new Object();
        assertTrue(input.update(0,0,0,0,0,world).reset());
        assertTrue(input.update(100,0,0,0,1,world).reset());
        assertTrue(input.update(100,0,0,0,3,world).reset());
        assertTrue(input.update(100,0,0,0,4,new Object()).reset());
    }
    @Test void localAxesRotateWithPlayerAndYawWrapHasNoSpike() {
        var input=new MotionInput(); var world=new Object();
        input.update(0,0,0,179,0,world);
        var sample=input.update(.1,0,0,-179,1,world);
        assertEquals(2,sample.turn(),1e-9); assertTrue(sample.lateral()<0);
    }
}
