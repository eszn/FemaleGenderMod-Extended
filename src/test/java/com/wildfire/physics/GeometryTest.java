package com.wildfire.physics;

import com.wildfire.main.entitydata.BodySettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeometryTest {
    private static final BodySettings MAX=new BodySettings(1,1,1,1,BodySettings.BreastShape.NATURAL,true,1);
    @Test void neutralSettingsPreserveEveryVertex() {
        for(var part:BodyDeformation.Part.values()) for(int y=0;y<=12;y++) for(int x=-4;x<=4;x++) for(int z=-2;z<=2;z++) {
            var p=BodyDeformation.deform(part,x,y,z,BodySettings.NONE,0,0);
            assertEquals(x,p.x()); assertEquals(y,p.y()); assertEquals(z,p.z());
        }
    }
    @Test void shouldersAndKneesRemainPinned() {
        assertEquals(new BodyDeformation.Point(4,0,2),BodyDeformation.deform(BodyDeformation.Part.TORSO,4,0,2,MAX,.5,.5));
        for(var leg:new BodyDeformation.Part[]{BodyDeformation.Part.LEFT_LEG,BodyDeformation.Part.RIGHT_LEG})
            for(int y=7;y<=12;y++) assertEquals(new BodyDeformation.Point(2,y,2),BodyDeformation.deform(leg,2,y,2,MAX,.5,.5));
    }
    @Test void torsoAndLegOuterSeamsMatch() {
        var torso=BodyDeformation.deform(BodyDeformation.Part.TORSO,4,12,2,MAX,0,0);
        var leg=BodyDeformation.deform(BodyDeformation.Part.LEFT_LEG,2,0,2,MAX,.5,.5);
        assertEquals(torso.x(),leg.x()+2,1e-9); assertEquals(torso.z(),leg.z(),1e-9); assertEquals(0,leg.y());
    }
    @Test void legsDoNotCrossInnerSeamAtRest() {
        for(int y=0;y<12;y++) {
            var left=BodyDeformation.deform(BodyDeformation.Part.LEFT_LEG,-2,y,2,MAX,0,0);
            var right=BodyDeformation.deform(BodyDeformation.Part.RIGHT_LEG,2,y,2,MAX,0,0);
            assertEquals(0,left.x()+2,1e-9); assertEquals(0,right.x()-2,1e-9);
        }
    }
    @Test void waistAndHipsAreModerateAndMirrored() {
        assertTrue(BodyDeformation.deform(BodyDeformation.Part.TORSO,4,7.2,2,MAX,0,0).x()>=3.28-1e-9);
        for(double y=0;y<=12;y+=.1) {
            var l=BodyDeformation.deform(BodyDeformation.Part.LEFT_LEG,2,y,2,MAX,0,0);
            var r=BodyDeformation.deform(BodyDeformation.Part.RIGHT_LEG,-2,y,2,MAX,0,0);
            assertEquals(l.x(),-r.x(),1e-9); assertEquals(l.z(),r.z(),1e-9);
            assertTrue(l.x()<3.5); assertTrue(l.z()<3.6);
        }
    }
    @Test void inflatedArmorEnclosesNeutralSkin() {
        for(double y=0;y<=12;y+=.2) for(double x=-2;x<=2;x+=.2) {
            var skin=BodyDeformation.deform(BodyDeformation.Part.LEFT_LEG,x,y,2,MAX,0,0);
            var armor=BodyDeformation.deform(BodyDeformation.Part.LEFT_LEG,x,y,2.5,MAX,0,0);
            assertTrue(armor.z()>skin.z());
        }
    }
    @Test void breastRimIsPinnedAndLargerRangeChangesDepth() {
        for(var shape:BodySettings.BreastShape.values()) {
            for(double u=0;u<=1;u+=.05) {
                var a=BreastSurface.point(true,u,0,1.2,shape,.5,.8,0);
                var b=BreastSurface.point(true,u,0,1.2,shape,0,0,0);
                assertEquals(a,b);
            }
            var normal=BreastSurface.point(true,.5,.5,.8,shape,0,0,0);
            var large=BreastSurface.point(true,.5,.5,1.2,shape,0,0,0);
            assertTrue(large.z()<normal.z()); assertTrue(large.z()>-6.1);
        }
    }
    @Test void roundedSurfaceHasSmoothTipInsteadOfTriangle() {
        var shape=BodySettings.BreastShape.ROUNDED;
        double deepest=0, peak=0;
        for(int i=0;i<=10000;i++) {
            double u=i/10000.0;
            double z=BreastSurface.point(true,u,.5,1,shape,0,0,0).z();
            if(z<deepest) { deepest=z; peak=u; }
        }
        var before=BreastSurface.point(true,peak-.0001,.5,1,shape,0,0,0);
        var after=BreastSurface.point(true,peak+.0001,.5,1,shape,0,0,0);
        assertTrue(Math.abs(before.z()-deepest)<1e-6);
        assertTrue(Math.abs(after.z()-deepest)<1e-6);
    }
    @Test void naturalProfileHasFullerLowerPole() {
        var top=BreastSurface.point(true,.5,.35,1,BodySettings.BreastShape.NATURAL,0,0,0);
        var bottom=BreastSurface.point(true,.5,.65,1,BodySettings.BreastShape.NATURAL,0,0,0);
        assertTrue(bottom.z()<top.z());
    }
    @Test void roundedProfileRemainsFullAwayFromCenter() {
        var p=BreastSurface.point(true,.5,.75,1,BodySettings.BreastShape.ROUNDED,0,0,0);
        var peak=BreastSurface.point(true,.5,.5,1,BodySettings.BreastShape.ROUNDED,0,0,0);
        assertTrue((-p.z()-2)/(-peak.z()-2)>.8,"Contour retains a rounded lower pole instead of a straight cone");
    }
    @Test void existingCustomizationChangesSurfaceButKeepsRimAttached() {
        var shape=BodySettings.BreastShape.NATURAL;
        var original=BreastSurface.point(true,.5,.5,1,shape,0,0,0);
        var edited=BreastSurface.point(true,.5,.5,1,shape,0,0,0,.5,.5,-.5,.1);
        assertNotEquals(original.x(),edited.x()); assertTrue(edited.y()<original.y()); assertTrue(edited.z()<original.z());
        assertEquals(BreastSurface.point(true,0,0,1,shape,0,0,0),BreastSurface.point(true,0,0,1,shape,0,0,0,.5,.5,-.5,.1));
    }
    @Test void buttockProjectionPeaksAtPelvisAndEndsAboveMidThigh() {
        var onlyButt=new BodySettings(0,0,1,0,BodySettings.BreastShape.NATURAL,false,0);
        double peakY=0, peakZ=0;
        for(double bodyY=0;bodyY<=24;bodyY+=.02) {
            var p=bodyY<=12 ? BodyDeformation.deform(BodyDeformation.Part.TORSO,1.85,bodyY,2,onlyButt,0,0)
                    : BodyDeformation.deform(BodyDeformation.Part.LEFT_LEG,-.15,bodyY-12,2,onlyButt,0,0);
            if(p.z()>peakZ) { peakZ=p.z(); peakY=bodyY; }
            if(bodyY>=15) assertEquals(2,p.z(),1e-9,"Buttock shape must not create a bulge halfway down the thigh");
        }
        assertTrue(peakY>10.5 && peakY<12.5,"The apex belongs at the pelvis");
        assertTrue(peakZ>3.5 && peakZ<4.1);
    }
    @Test void rearHasTwoRoundedLobesAndAContinuousPelvisSeam() {
        var center=BodyDeformation.deform(BodyDeformation.Part.TORSO,0,11.8,2,MAX,0,0);
        var lobe=BodyDeformation.deform(BodyDeformation.Part.TORSO,1.85,11.8,2,MAX,0,0);
        var edge=BodyDeformation.deform(BodyDeformation.Part.TORSO,4,11.8,2,MAX,0,0);
        assertTrue(lobe.z()>center.z()+.15); assertTrue(lobe.z()>edge.z()+.5);
        for(double x=0;x<=4;x+=.1) {
            var torso=BodyDeformation.deform(BodyDeformation.Part.TORSO,x,12,2,MAX,-.5,-.2);
            var leg=BodyDeformation.deform(BodyDeformation.Part.LEFT_LEG,x-2,0,2,MAX,.5,.2);
            assertEquals(torso.z(),leg.z(),1e-9); assertEquals(torso.x(),leg.x()+2,1e-9);
            assertEquals(12,torso.y()); assertEquals(0,leg.y());
        }
    }
    @Test void naturalUpperSlopeIsGentleAndChestBridgeDoesNotSplitDuringMotion() {
        var shape=BodySettings.BreastShape.NATURAL;
        var upper=BreastSurface.point(true,.5,.25,1,shape,0,0,0);
        var peak=BreastSurface.point(true,.5,.58,1,shape,0,0,0);
        assertTrue((-upper.z()-2)/(-peak.z()-2)<.5);
        for(double v=0;v<=1;v+=.02) {
            assertEquals(BreastSurface.point(true,1,v,1.2,shape,.5,.5,0,.5,.5,-.5,.1),
                    BreastSurface.point(false,0,v,1.2,shape,-.5,-.5,0,.5,.5,-.5,.1));
        }
        var bridge=BreastSurface.point(true,1,.58,1,shape,0,0,0);
        assertTrue(bridge.z()<-2.5 && bridge.z()>peak.z()+.8);
    }
    @Test void lowerBreastAndGlutealContoursHaveRoundedFootprints() {
        var shape=BodySettings.BreastShape.NATURAL;
        var lowerLobe=BreastSurface.point(true,.5375,.9,1,shape,0,0,0);
        var lowerCenter=BreastSurface.point(true,1,.9,1,shape,0,0,0);
        assertTrue(lowerLobe.z()<-2.5); assertEquals(-2,lowerCenter.z(),1e-9);
        var onlyButt=new BodySettings(0,0,1,0,shape,false,0);
        var lowerRear=BodyDeformation.deform(BodyDeformation.Part.LEFT_LEG,-.15,2.4,2,onlyButt,0,0);
        var lowerCleft=BodyDeformation.deform(BodyDeformation.Part.LEFT_LEG,-2,2.4,2,onlyButt,0,0);
        assertTrue(lowerRear.z()>2.005); assertEquals(2,lowerCleft.z(),1e-9);
    }
    @Test void largerSizesGrowWidthHeightAndDepthPastTheVanillaOutline() {
        double[] small=breastBounds(.25),large=breastBounds(1.2);
        assertTrue(large[0]>4.7 && large[0]>small[0]*1.1,"Large breasts may extend past the chest sides");
        assertTrue(large[1]>small[1]+.5,"Visible lower volume grows with size");
        assertTrue(large[2]>small[2]*2);
        var s=new BodySettings(0,0,.2f,0,BodySettings.BreastShape.NATURAL,false,0);
        var b=new BodySettings(0,0,1,0,s.shape(),false,0);
        assertTrue(BodyDeformation.deform(BodyDeformation.Part.TORSO,4,11.7,2,b,0,0).x()>4.8);
        assertTrue(BodyDeformation.deform(BodyDeformation.Part.TORSO,4,11.7,2,b,0,0).x()
                >BodyDeformation.deform(BodyDeformation.Part.TORSO,4,11.7,2,s,0,0).x()+.6);
    }
    private static double[] breastBounds(double size) {
        double width=0,bottom=0,depth=0;
        for(double x=0;x<=4;x+=.05) for(double y=0;y<=10;y+=.05) {
            var p=BreastSurface.deform(x,y,-2,size,BodySettings.BreastShape.NATURAL,0,0,0,0,0,0,0,0);
            width=Math.max(width,p.x()); depth=Math.max(depth,-2-p.z());
            if(p.z()<-2.1) bottom=Math.max(bottom,p.y());
        }
        return new double[]{width,bottom,depth};
    }
    @Test void rearCenterAndTorsoLegJoinHaveContinuousSurfaceSlopes() {
        double e=.0001;
        for(double y=9;y<=12;y+=.1) {
            double a=BodyDeformation.deform(BodyDeformation.Part.TORSO,-e,y,2,MAX,0,0).z();
            double b=BodyDeformation.deform(BodyDeformation.Part.TORSO,0,y,2,MAX,0,0).z();
            double c=BodyDeformation.deform(BodyDeformation.Part.TORSO,e,y,2,MAX,0,0).z();
            assertTrue(Math.abs((b-a)/e-(c-b)/e)<.003,"The midline must not create a hard normal crease");
        }
        for(double x=.2;x<=4;x+=.2) {
            double top=BodyDeformation.deform(BodyDeformation.Part.TORSO,x,12-e,2,MAX,0,0).z();
            double seam=BodyDeformation.deform(BodyDeformation.Part.TORSO,x,12,2,MAX,0,0).z();
            double bottom=BodyDeformation.deform(BodyDeformation.Part.LEFT_LEG,x-2,e,2,MAX,0,0).z();
            assertTrue(Math.abs((seam-top)/e-(bottom-seam)/e)<.003);
        }
    }
    @Test void continuousChestFieldDoesNotInvertSkinOrGarmentVolume() {
        double e=.0001;
        for(double x=-5;x<=5;x+=.5) for(double y=0;y<=12;y+=.5) for(double z=-3;z<=3;z+=.5) {
            var a=chest(x+e,y,z); var b=chest(x-e,y,z);
            var c=chest(x,y+e,z); var d=chest(x,y-e,z);
            var f=chest(x,y,z+e); var g=chest(x,y,z-e);
            double ax=a.x()-b.x(),ay=a.y()-b.y(),az=a.z()-b.z();
            double bx=c.x()-d.x(),by=c.y()-d.y(),bz=c.z()-d.z();
            double cx=f.x()-g.x(),cy=f.y()-g.y(),cz=f.z()-g.z();
            double determinant=(ax*(by*cz-bz*cy)-ay*(bx*cz-bz*cx)+az*(bx*cy-by*cx))/(8*e*e*e);
            assertTrue(determinant>.05,"Skin and garment layers must preserve their inside/outside order");
        }
    }
    private static BodyDeformation.Point chest(double x,double y,double z) {
        var p=BreastSurface.deform(x,y,z,1.2,BodySettings.BreastShape.NATURAL,0,0,0,0,0,0,0,0);
        return BodyDeformation.deform(BodyDeformation.Part.TORSO,p.x(),p.y(),p.z(),MAX,0,0);
    }
    @Test void roundedCageKeepsConnectionsAndNestedGarmentCorners() {
        var torso=BodyDeformation.Part.TORSO;
        assertEquals(new BodyDeformation.Point(4,0,2),BodyCage.round(torso,4,0,2));
        for(double x=-4;x<=4;x+=.2) {
            var a=BodyCage.round(torso,x,12,2);
            var b=BodyCage.round(BodyDeformation.Part.LEFT_LEG,x-2,0,2);
            assertEquals(a.x(),b.x()+2,1e-9); assertEquals(a.z(),b.z(),1e-9);
        }
        var skin=BodyCage.round(torso,4,6,2);
        for(double inflation=.25;inflation<=1;inflation+=.25) {
            var garment=BodyCage.round(torso,4+inflation,6,2+inflation);
            assertTrue(garment.x()>skin.x() && garment.z()>skin.z());
        }
        double e=.0001;
        var front=BodyCage.round(torso,4-e,6,2);
        var side=BodyCage.round(torso,4,6,2-e);
        double frontSlope=(skin.z()-front.z())/(skin.x()-front.x());
        double sideSlope=(side.z()-skin.z())/(side.x()-skin.x());
        assertEquals(frontSlope,sideSlope,.001,"Front and side tangents agree at the rounded corner");
    }
    @Test void distantMeshesHaveFewerSamplesWithoutDroppingTheirBoundaries() {
        var near=SurfaceResolution.axis(0,12,true,false,.2,true);
        var far=SurfaceResolution.axis(12,0,true,false,.8,true);
        assertTrue(near.length>far.length*2);
        assertEquals(0,far[0],1e-7); assertEquals(1,far[far.length-1],1e-7);
        for(int i=1;i<far.length;i++) assertTrue(far[i]>far[i-1]);
        assertEquals(0,SurfaceResolution.detail(4)); assertEquals(1,SurfaceResolution.detail(100)); assertEquals(2,SurfaceResolution.detail(400));
    }
}
