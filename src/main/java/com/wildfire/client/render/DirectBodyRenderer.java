package com.wildfire.client.render;

import com.mojang.blaze3d.vertex.*;
import com.wildfire.physics.AuthoredBodyMesh;
import com.wildfire.physics.BodyDeformation;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/** Submit the original character panels through the player's existing skin and armor render passes. */
public final class DirectBodyRenderer {
    private record State(com.wildfire.main.entitydata.BodySettings settings,float bust,AuthoredBodyMesh.Motion motion,
            AuthoredBodyMesh.Adjustment adjustment,net.minecraft.world.entity.EquipmentSlot armor,boolean leftVisible,boolean rightVisible) {}
    private record Mesh(State state,float[] vertices) {}
    private static final java.util.Map<net.minecraft.world.entity.LivingEntity,java.util.Map<ModelPart.Cube,Mesh>> CACHE=new java.util.WeakHashMap<>();
    private DirectBodyRenderer() {}
    public static void render(ModelPart.Cube cube,BodyDeformation.Part part,BodyRenderContext context,
            PoseStack.Pose pose,VertexConsumer consumer,int light,int overlay,int color) {
        var settings=context.config.getBodySettings();
        var body=context.config.getBodyPhysics();
        var motion=new AuthoredBodyMesh.Motion(context.leftX,context.leftY,context.rightX,context.rightY,
                (float)body.vertical(true,context.partialTick),(float)body.vertical(false,context.partialTick),(float)body.depth(context.partialTick));
        float bust=context.breasts?context.bust:0;
        float pivot=part==BodyDeformation.Part.TORSO?0:part==BodyDeformation.Part.LEFT_LEG?1.9f:-1.9f;
        var binding=context.binding(cube);
        if(part==BodyDeformation.Part.TORSO && binding!=null) {
            var savedPose=new BodyRenderContext.TorsoPose(new org.joml.Matrix4f(pose.pose()),new org.joml.Matrix3f(pose.normal()));
            context.torsoPoses.put(binding.model(),savedPose);
            if(context.primaryTorsoPose==null) context.primaryTorsoPose=savedPose;
        }
        var breasts=context.config.getBreasts();
        var adjustment=new AuthoredBodyMesh.Adjustment(breasts.getXOffset(),breasts.getYOffset(),breasts.getZOffset(),breasts.getCleavage());
        boolean leftVisible=true,rightVisible=true;
        if(binding!=null && binding.outer() && binding.model() instanceof net.minecraft.client.model.PlayerModel<?> player) { leftVisible=player.leftPants.visible; rightVisible=player.rightPants.visible; }
        var state=new State(settings,bust,motion,adjustment,binding==null?null:binding.armor(),leftVisible,rightVisible);
        var cache=CACHE.computeIfAbsent(context.entity,key->new java.util.IdentityHashMap<>());
        var mesh=cache.get(cube);
        if(mesh==null || !mesh.state.equals(state)) { mesh=new Mesh(state,bake(cube,part,binding,state)); cache.put(cube,mesh); }
        var torso=binding==null?context.primaryTorsoPose:context.torsoPoses.getOrDefault(binding.model(),context.primaryTorsoPose);
        var p=new Vector3f(); var n=new Vector3f(); var global=new Vector3f(); var bodyNormal=new Vector3f();
        var data=mesh.vertices;
        for(int i=0;i<data.length;i+=9) {
            p.set(data[i]-pivot/16,data[i+1]-(part==BodyDeformation.Part.TORSO?0:.75f),data[i+2]).mulPosition(pose.pose());
            n.set(data[i+3],data[i+4],data[i+5]); pose.transformNormal(n,n);
            if(part!=BodyDeformation.Part.TORSO && torso!=null && data[i+8]<1) {
                global.set(data[i],data[i+1],data[i+2]).mulPosition(torso.position());
                p.set(global.lerp(p,data[i+8]));
                bodyNormal.set(data[i+3],data[i+4],data[i+5]).mul(torso.normal()).normalize();
                n.set(bodyNormal.lerp(n,data[i+8]).normalize());
            }
            consumer.addVertex(p.x,p.y,p.z).setColor(color).setUv(data[i+6],data[i+7]).setOverlay(overlay).setLight(light).setNormal(n.x,n.y,n.z);
        }
    }
    private static float[] bake(ModelPart.Cube cube,BodyDeformation.Part part,BodyRenderContext.Binding binding,State state) {
        var triangles=AuthoredModel.get().triangles(part);
        float[] data=new float[triangles.size()*4*9]; int i=0;
        float inflation=0;
        for(var polygon:cube.polygons) for(var v:polygon.vertices) inflation=Math.max(inflation,cube.minX-v.pos.x);
        for(var triangle:triangles) {
            if(triangle.group().equals("breast") && state.bust<.01) continue;
            if(binding!=null && binding.armor()==net.minecraft.world.entity.EquipmentSlot.CHEST && triangle.uvPart()!=BodyDeformation.Part.TORSO) continue;
            if(binding!=null && binding.armor()==net.minecraft.world.entity.EquipmentSlot.LEGS && triangle.group().equals("breast")) continue;
            var uvCube=cube;
            if(binding!=null && part==BodyDeformation.Part.TORSO && triangle.uvPart()!=part) {
                boolean left=triangle.uvPart()==BodyDeformation.Part.LEFT_LEG;
                var leg=left?binding.model().leftLeg:binding.model().rightLeg;
                if(binding.outer() && binding.model() instanceof net.minecraft.client.model.PlayerModel<?> player) leg=left?player.leftPants:player.rightPants;
                if(binding.outer() && !leg.visible) continue;
                if((Object)leg instanceof BodyRenderContext.Cubes access && !access.extended$cubes().isEmpty()) uvCube=access.extended$cubes().getFirst();
            }
            for(var vertex:new AuthoredBodyMesh.Vertex[]{triangle.a(),triangle.b(),triangle.c(),triangle.c()}) {
                var p=AuthoredBodyMesh.deform(vertex.point(),triangle.group(),state.settings,state.bust,state.motion,state.adjustment);
                var n=AuthoredBodyMesh.normal(vertex,triangle.group(),state.settings,state.bust,state.motion,state.adjustment);
                // Normal offset keeps jacket, armor, trim and glint geometry on the same surface.
                p.fma(inflation,n).div(16);
                var uv=uv(uvCube,triangle.uvPart(),vertex.point(),vertex.normal(),triangle.uvPart()==BodyDeformation.Part.LEFT_LEG?1.9f:-1.9f);
                data[i++]=p.x; data[i++]=p.y; data[i++]=p.z;
                data[i++]=n.x; data[i++]=n.y; data[i++]=n.z;
                data[i++]=uv[0]; data[i++]=uv[1]; data[i++]=AuthoredBodyMesh.legWeight(vertex.point().y);
            }
        }
        return java.util.Arrays.copyOf(data,i);
    }
    private static float[] uv(ModelPart.Cube cube,BodyDeformation.Part part,Vector3f point,Vector3f normal,float pivot) {
        var p=new Vector3f(point);
        boolean torso=part==BodyDeformation.Part.TORSO;
        float nx=torso?p.x/5:(p.x-(pivot>0?2.5f:-2.5f))/2.5f;
        float nz=p.z/(p.z<0?(torso?6.2f:1.81f):4.9f);
        float radius=Math.max(.0001f,Math.max(Math.abs(nx),Math.abs(nz)));
        p.x=nx/radius*(torso?4:2); p.z=nz/radius*2;
        p.y=Math.clamp(p.y-(part==BodyDeformation.Part.TORSO?0:12),0,12);
        // A continuous wrap uses side textures across curved panels. Lighting normals must not
        // switch a breast's upper slope into the unrelated shoulder/top UV island.
        var direction=Math.abs(nx)>Math.abs(nz)?new Vector3f(Math.signum(nx),0,0):new Vector3f(0,0,Math.signum(nz));
        if(Math.abs(normal.y)>.99f && (point.y<.01 || point.y>23.99)) direction.set(0,normal.y,0);
        ModelPart.Polygon chosen=cube.polygons[0]; float best=-Float.MAX_VALUE;
        for(var face:cube.polygons) {
            float score=face.normal.dot(direction); if(score>best) { best=score; chosen=face; }
        }
        var q=chosen.vertices;
        // Project into the original cube face's UV rectangle, including slim/armor/trim atlases.
        var du=new Vector3f(q[1].pos).sub(q[0].pos); var dv=new Vector3f(q[3].pos).sub(q[0].pos);
        var delta=new Vector3f(p).sub(q[0].pos);
        float u=Math.clamp(delta.dot(du)/du.lengthSquared(),0,1),v=Math.clamp(delta.dot(dv)/dv.lengthSquared(),0,1);
        return new float[]{q[0].u+(q[1].u-q[0].u)*u+(q[3].u-q[0].u)*v,
                q[0].v+(q[1].v-q[0].v)*u+(q[3].v-q[0].v)*v};
    }
}
