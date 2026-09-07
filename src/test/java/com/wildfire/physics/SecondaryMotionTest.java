package com.wildfire.physics;

import com.wildfire.main.entitydata.BodySettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecondaryMotionTest {
    private static final BodySettings SETTINGS=new BodySettings(.5f,.5f,.5f,.5f,BodySettings.BreastShape.NATURAL,true,.5f);
    private static double[] walking(double chestSupport,double legSupport) {
        var breast=new DampedSpring();var butt=new DampedSpring();
        double peakBreast=0,peakButt=0;
        var sample=new MotionInput.Sample(0,0,0,0,false);
        for(int tick=0;tick<180;tick++) {
            float speed=tick<60?.65f:0,position=Math.min(tick+1,60)*.65f;
            SecondaryMotion.breast(sample,.8f,.333f,.75f,-1,chestSupport,false,true,position,speed).first().tick(breast);
            SecondaryMotion.body(sample,SETTINGS,legSupport,false,true,position,speed).first().tick(butt);
            peakBreast=Math.max(peakBreast,Math.abs(breast.position()));peakButt=Math.max(peakButt,Math.abs(butt.position()));
        }
        assertEquals(0,breast.position(),1e-8);assertEquals(0,butt.position(),1e-8);
        return new double[]{peakBreast,peakButt};
    }
    @Test void walkingRemainsVisibleSettlesAndRespectsArmorSupport() {
        var free=walking(0,0);var leather=walking(.3,.8);var rigid=walking(1,1);
        assertTrue(free[0]>.15 && free[0]<.5);assertTrue(free[1]>.08 && free[1]<.4);
        assertTrue(leather[0]>0 && leather[0]<free[0]*.7);
        assertTrue(leather[1]>0 && leather[1]<free[1]*.2);
        assertEquals(0,rigid[0]);assertEquals(0,rigid[1]);
    }
    @Test void turningMovesOppositePelvicSidesAndWaterReducesExcitation() {
        var sample=new MotionInput.Sample(0,0,0,4.5,false);
        var dry=SecondaryMotion.body(sample,SETTINGS,0,false,true,0,0);
        var wet=SecondaryMotion.body(sample,SETTINGS,0,true,true,0,0);
        var left=new DampedSpring();var right=new DampedSpring();var water=new DampedSpring();
        dry.first().tick(left);dry.second().tick(right);wet.first().tick(water);
        assertTrue(left.position()>0);assertEquals(-left.position(),right.position(),1e-9);
        assertTrue(water.position()>0 && water.position()<left.position());
    }
}
