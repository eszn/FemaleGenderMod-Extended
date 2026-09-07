package com.wildfire.physics;

import com.wildfire.main.entitydata.BodySettings;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.joml.Vector3f;

class AuthoredBodyMeshTest {
    private static final BodySettings JENNY=new BodySettings(.5f,.5f,.5f,.5f,BodySettings.BreastShape.NATURAL,true,.5f);
    private static AuthoredBodyMesh mesh() throws Exception {
        var path=Path.of("local-models/resources/assets/wildfire_gender/body/jenny-mesh.json");
        assumeTrue(Files.exists(path),"User-supplied mesh is intentionally local");
        try(var reader=Files.newBufferedReader(path)) { return AuthoredBodyMesh.read(reader); }
    }
    @Test void presetPreservesEveryImportedVertexAndSmoothNormals() throws Exception {
        var mesh=mesh(); int count=0;
        for(var part:BodyDeformation.Part.values()) for(var triangle:mesh.sourceTriangles(part)) {
            for(var v:new AuthoredBodyMesh.Vertex[]{triangle.a(),triangle.b(),triangle.c()}) {
                var p=AuthoredBodyMesh.deform(v.point(),triangle.group(),JENNY,.8f,AuthoredBodyMesh.Motion.STILL);
                assertTrue(p.distance(v.point())<.000003,"Preset must preserve authored positions");
                var n=AuthoredBodyMesh.normal(v,triangle.group(),JENNY,.8f,AuthoredBodyMesh.Motion.STILL);
                assertTrue(n.isFinite()); assertEquals(1,n.length(),.0001); count++;
                if(triangle.group().equals("buttock")) assertEquals(BodyDeformation.Part.TORSO,part,"The entire pelvis must follow its authored body bone when walking");
            }
        }
        assertTrue(count>3000);
    }
    @Test void jointClippingPreservesOriginalSurfaceArea() throws Exception {
        var mesh=mesh(); double actual=0,expected=0;
        for(var part:BodyDeformation.Part.values()) for(var t:mesh.sourceTriangles(part))
            actual+=new Vector3f(t.b().point()).sub(t.a().point()).cross(new Vector3f(t.c().point()).sub(t.a().point())).length()/2;
        try(var reader=Files.newBufferedReader(Path.of("local-models/resources/assets/wildfire_gender/body/jenny-mesh.json"))) {
            for(var value:com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("quads")) {
                var points=value.getAsJsonObject().getAsJsonArray("points"); var p=new Vector3f[4];
                for(int i=0;i<4;i++) { var q=points.get(i).getAsJsonArray(); p[i]=new Vector3f(q.get(0).getAsFloat(),q.get(1).getAsFloat(),q.get(2).getAsFloat()); }
                // Renderer reverses the winding, using the equivalent diagonal 1--3.
                expected+=new Vector3f(p[2]).sub(p[3]).cross(new Vector3f(p[1]).sub(p[3])).length()/2;
                expected+=new Vector3f(p[1]).sub(p[3]).cross(new Vector3f(p[0]).sub(p[3])).length()/2;
            }
        }
        assertEquals(expected,actual,.01,"Rig partitioning must not replace or discard the source surface");
    }
    @Test void sizingChangesWidthAndProjectionWithoutMovingChestAttachment() {
        var p=new Vector3f(4.8f,5,-5);
        var small=AuthoredBodyMesh.deform(p,"breast",JENNY,.3f,AuthoredBodyMesh.Motion.STILL);
        var large=AuthoredBodyMesh.deform(p,"breast",JENNY,1.2f,AuthoredBodyMesh.Motion.STILL);
        assertTrue(large.x>small.x); assertTrue(large.z<small.z); assertTrue(large.x>4);
        var root=new Vector3f(2.1f,2.7f,-1.75f);
        assertTrue(root.distance(AuthoredBodyMesh.deform(root,"breast",JENNY,1.2f,AuthoredBodyMesh.Motion.STILL))<.00001);
    }
    @Test void physicsIsFeatheredAtTheAttachmentAndMovesTheAuthoredTip() {
        var motion=new AuthoredBodyMesh.Motion(.2f,.3f,-.2f,-.3f,.25f,-.25f,.1f);
        var root=new Vector3f(2.1f,2.7f,-1.65f); var tip=new Vector3f(3,5,-6);
        assertEquals(0,root.distance(AuthoredBodyMesh.deform(root,"breast",JENNY,.8f,motion)),.00001);
        assertTrue(tip.distance(AuthoredBodyMesh.deform(tip,"breast",JENNY,.8f,motion))>.2);
        assertTrue(new Vector3f(3,12,4).distance(AuthoredBodyMesh.deform(new Vector3f(3,12,4),"buttock",JENNY,.8f,motion))>.2);
    }
    @Test void positionControlsKeepTheRootPinnedAndNormalsFinite() {
        var adjustment=new AuthoredBodyMesh.Adjustment(.5f,.7f,-.6f,.1f);
        var root=new Vector3f(2.1f,2.7f,-1.65f);
        assertEquals(0,root.distance(AuthoredBodyMesh.deform(root,"breast",JENNY,.8f,AuthoredBodyMesh.Motion.STILL,adjustment)),.00001);
        var tip=new AuthoredBodyMesh.Vertex(new Vector3f(3,5,-6),new Vector3f(0,0,-1));
        assertTrue(tip.point().distance(AuthoredBodyMesh.deform(tip.point(),"breast",JENNY,.8f,AuthoredBodyMesh.Motion.STILL,adjustment))>.5);
        assertTrue(AuthoredBodyMesh.normal(tip,"breast",JENNY,.8f,AuthoredBodyMesh.Motion.STILL,adjustment).isFinite());
    }
    @Test void roundedCoreHasMatchingHipBoundariesAndSeparatedLegs() {
        for(int i=0;i<=48;i++) {
            float angle=(float)(Math.PI*i/48);
            assertTrue(AuthoredBodyMesh.corePoint(BodyDeformation.Part.TORSO,12,angle).distance(AuthoredBodyMesh.corePoint(BodyDeformation.Part.LEFT_LEG,12,angle))<.00001);
            assertTrue(AuthoredBodyMesh.corePoint(BodyDeformation.Part.TORSO,12,-angle).distance(AuthoredBodyMesh.corePoint(BodyDeformation.Part.RIGHT_LEG,12,angle))<.00001);
        }
        for(float y=14;y<=24;y+=.25f) for(int i=0;i<96;i++) {
            float angle=(float)(i*Math.PI/48);
            assertTrue(AuthoredBodyMesh.corePoint(BodyDeformation.Part.LEFT_LEG,y,angle).x>0);
            assertTrue(AuthoredBodyMesh.corePoint(BodyDeformation.Part.RIGHT_LEG,y,angle).x<0);
        }
        var flank=AuthoredBodyMesh.corePoint(BodyDeformation.Part.TORSO,8,(float)Math.PI/4);
        assertTrue(flank.z< -1.2f && flank.x>2.4f,"Flank is a curved section, not a flat diagonal panel");
    }
    @Test void refinedCoreHasFiniteNormalsAndLoadsCleanedSoftSurfaces() throws Exception {
        var mesh=mesh();
        var json=com.google.gson.JsonParser.parseString(Files.readString(Path.of("local-models/resources/assets/wildfire_gender/body/jenny-mesh.json"))).getAsJsonObject();
        for(var part:BodyDeformation.Part.values()) {
            if(!json.has("smooth_surfaces")) for(var t:mesh.sourceTriangles(part)) if(t.group().equals("breast") || t.group().equals("buttock")) assertTrue(mesh.triangles(part).contains(t));
            for(var t:mesh.triangles(part)) for(var v:new AuthoredBodyMesh.Vertex[]{t.a(),t.b(),t.c()}) {
                assertTrue(v.point().isFinite()); assertTrue(v.normal().isFinite()); assertEquals(1,v.normal().length(),.001);
            }
        }
        if(json.has("smooth_surfaces")) {
            for(String group:new String[]{"breast","buttock"}) {
                var triangles=mesh.triangles(BodyDeformation.Part.TORSO).stream().filter(t->t.group().equals(group)).toList();
                assertTrue(triangles.size()>1000,"Cleaned boundary must actually load");
                assertTrue(triangles.stream().noneMatch(t->mesh.sourceTriangles(BodyDeformation.Part.TORSO).contains(t)),"Overlapping original panels must not also render");
                for(var t:triangles) {
                    var n=new Vector3f(t.b().point()).sub(t.a().point()).cross(new Vector3f(t.c().point()).sub(t.a().point()));
                    assertTrue(n.dot(t.a().normal())>-.00001,"Cleaned face winding must agree with lighting normals");
                }
            }
        }
    }
    @Test void legMotionStartsAtHipWithoutMovingSharedBoundary() {
        assertEquals(0,AuthoredBodyMesh.legWeight(12));assertEquals(1,AuthoredBodyMesh.legWeight(15));
        assertTrue(AuthoredBodyMesh.legWeight(13)>.2);assertTrue(AuthoredBodyMesh.legWeight(14)>.7);
    }
    @Test void increasingBustCannotGrowTheAttachmentThroughTheBackOrFoldSmallProfiles() {
        for(float size:new float[]{.05f,.3f,.8f,1.2f}) {
            for(float z:new float[]{-1.65f,0,1.1f}) {
                var root=new Vector3f(1.5f,4,z);
                assertEquals(0,root.distance(AuthoredBodyMesh.deform(root,"breast",JENNY,size,AuthoredBodyMesh.Motion.STILL)),.000001);
            }
            float previous=Float.NEGATIVE_INFINITY;
            for(float z=-6.3f;z<=-1.65f;z+=.025f) {
                float projected=AuthoredBodyMesh.deform(new Vector3f(3,5,z),"breast",JENNY,size,AuthoredBodyMesh.Motion.STILL).z;
                assertTrue(projected>previous,"Projection must stay ordered at small and large sizes");previous=projected;
            }
        }
    }
}
