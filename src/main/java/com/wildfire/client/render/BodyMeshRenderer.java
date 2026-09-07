package com.wildfire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.main.entitydata.BodySettings;
import com.wildfire.physics.BodyDeformation;
import com.wildfire.physics.BodyCage;
import com.wildfire.physics.BreastSurface;
import com.wildfire.physics.SurfaceResolution;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/** Subdivide original UV faces and evaluate smooth surface normals without changing the skeleton. */
public final class BodyMeshRenderer {
    private record Vertex(float x,float y,float z,float u,float v) {}
    private record Face(Vertex[] grid,int columns,int rows,Vector3f du,Vector3f dv,Vector3f min,Vector3f max) {}
    private record State(BodyDeformation.Part part,int detail,BodySettings settings,boolean breasts,
            float bust,float leftX,float leftY,float rightX,float rightY,float offsetX,float offsetY,float offsetZ,float cleavage,
            BodySettings.BreastShape shape,double left,double right,double depth) {}
    private record Mesh(State state,float[][] faces) {}
    private static final Map<ModelPart.Cube,Face[][]> CACHE=new WeakHashMap<>();
    // A resting player reuses geometry; animated transforms and lighting are applied at submission time.
    // Weak entity ownership releases profiles when they leave the client world.
    private static final Map<net.minecraft.world.entity.LivingEntity,Map<ModelPart.Cube,Mesh>> MESHES=new WeakHashMap<>();
    public static boolean compatible(ModelPart.Cube cube,BodyDeformation.Part part) {
        float width=part==BodyDeformation.Part.TORSO?8:4;
        return Math.abs(cube.maxX-cube.minX-width)<.01 && Math.abs(cube.maxY-cube.minY-12)<.01
                && Math.abs(cube.maxZ-cube.minZ-4)<.01 && Math.abs(cube.minY)<.01;
    }
    private static Vertex sample(ModelPart.Vertex[] q,float u,float v) {
        float a=(1-u)*(1-v),b=u*(1-v),c=u*v,d=(1-u)*v;
        return new Vertex(q[0].pos.x*a+q[1].pos.x*b+q[2].pos.x*c+q[3].pos.x*d,
                q[0].pos.y*a+q[1].pos.y*b+q[2].pos.y*c+q[3].pos.y*d,
                q[0].pos.z*a+q[1].pos.z*b+q[2].pos.z*c+q[3].pos.z*d,
                q[0].u*a+q[1].u*b+q[2].u*c+q[3].u*d,q[0].v*a+q[1].v*b+q[2].v*c+q[3].v*d);
    }
    private static float[] axis(Vector3f from,Vector3f to,boolean leg,double spacing,boolean chest) {
        return Math.abs(to.y-from.y)>.01?SurfaceResolution.axis(from.y,to.y,true,leg,spacing,chest)
                :Math.abs(to.x-from.x)>.01?SurfaceResolution.axis(from.x,to.x,false,leg,spacing)
                :SurfaceResolution.axis(from.z,to.z,false,leg,spacing);
    }
    private static Face[] bake(ModelPart.Cube cube,BodyDeformation.Part part,int detail) {
        Face[] faces=new Face[cube.polygons.length];
        for(int i=0;i<faces.length;i++) {
            var q=cube.polygons[i].vertices;
            var du=new Vector3f(q[1].pos).sub(q[0].pos);
            var dv=new Vector3f(q[3].pos).sub(q[0].pos);
            boolean leg=part!=BodyDeformation.Part.TORSO;
            boolean chest=!leg && (q[0].pos.z<0 || q[1].pos.z<0 || q[2].pos.z<0 || q[3].pos.z<0);
            double spacing=SurfaceResolution.spacing(detail,Math.abs(du.z)<.01 && Math.abs(dv.z)<.01 && q[0].pos.z>1);
            if(chest) spacing=detail==0?.1:detail==1?.35:.8;
            if(Math.abs(du.y)<.01 && Math.abs(dv.y)<.01 && (leg?q[0].pos.y>=7:q[0].pos.y<=3)) spacing=32;
            float[] us=axis(q[0].pos,q[1].pos,leg,spacing,chest),vs=axis(q[0].pos,q[3].pos,leg,spacing,chest);
            int columns=us.length-1,rows=vs.length-1;
            Vertex[] grid=new Vertex[(columns+1)*(rows+1)];
            for(int row=0;row<=rows;row++) for(int col=0;col<=columns;col++)
                grid[row*(columns+1)+col]=sample(q,us[col],vs[row]);
            var min=new Vector3f(q[0].pos); var max=new Vector3f(q[0].pos);
            for(var vertex:q) { min.min(vertex.pos); max.max(vertex.pos); }
            faces[i]=new Face(grid,columns,rows,du.normalize().mul(.03f),dv.normalize().mul(.03f),min,max);
        }
        return faces;
    }
    private static BodyDeformation.Point neighbor(Face face,Vertex v,Vector3f delta,int sign,BodyDeformation.Part part,
            BodySettings settings,double left,double right,double depth,BodyRenderContext context) {
        return point(part,Math.clamp(v.x+sign*delta.x,face.min.x,face.max.x),
                Math.clamp(v.y+sign*delta.y,face.min.y,face.max.y),
                Math.clamp(v.z+sign*delta.z,face.min.z,face.max.z),settings,left,right,depth,context);
    }
    private static BodyDeformation.Point point(BodyDeformation.Part part,double x,double y,double z,BodySettings settings,
                                               double left,double right,double depth,BodyRenderContext context) {
        double bounce=(part==BodyDeformation.Part.TORSO?x>0:part==BodyDeformation.Part.LEFT_LEG)?left:right;
        if(settings.hasBodyShape() || context.breasts && part==BodyDeformation.Part.TORSO) {
            var cage=BodyCage.round(part,x,y,z);
            x=cage.x(); y=cage.y(); z=cage.z();
        }
        if(part==BodyDeformation.Part.TORSO && context.breasts) {
            var b=context.config.getBreasts();
            var p=BreastSurface.deform(x,y,z,context.bust,context.config.getBodySettings().shape(),
                    context.leftX,context.leftY,context.rightX,context.rightY,b.getXOffset(),b.getYOffset(),b.getZOffset(),b.getCleavage());
            x=p.x(); y=p.y(); z=p.z();
        }
        var p=BodyDeformation.deform(part,x,y,z,settings,bounce,depth);
        // Vanilla leg pivots are +/-1.9, while the torso ends at +/-4. Join at the hip,
        // then return to the original leg coordinates before the knee.
        double seam=settings.hasBodyShape() && part!=BodyDeformation.Part.TORSO
                ? (part==BodyDeformation.Part.LEFT_LEG?.1:-.1)*(1-BodyDeformation.smooth(y/7)):0;
        return new BodyDeformation.Point(p.x()+seam,p.y(),p.z());
    }
    public static void render(ModelPart.Cube cube,BodyDeformation.Part part,BodyRenderContext context,
                              PoseStack.Pose pose,VertexConsumer consumer,int light,int overlay,int color) {
        if(AuthoredModel.get()!=null && context.config.getBodySettings().shape()==BodySettings.BreastShape.NATURAL) {
            DirectBodyRenderer.render(cube,part,context,pose,consumer,light,overlay,color); return;
        }
        var settings=context.config.getGender()==com.wildfire.main.Gender.FEMALE?context.config.getBodySettings():BodySettings.NONE;
        var physics=context.config.getBodyPhysics();
        double left=physics.vertical(true,context.partialTick),right=physics.vertical(false,context.partialTick),depth=physics.depth(context.partialTick);
        Vector3f normal=new Vector3f();
        var viewer=net.minecraft.client.Minecraft.getInstance().player;
        int detail=SurfaceResolution.detail(viewer==null?0:viewer.distanceToSqr(context.entity));
        Face[][] levels=CACHE.computeIfAbsent(cube,key->new Face[3][]);
        if(levels[detail]==null) levels[detail]=bake(cube,part,detail);
        var breasts=context.config.getBreasts();
        var state=new State(part,detail,settings,context.breasts,context.bust,context.leftX,context.leftY,context.rightX,context.rightY,
                breasts.getXOffset(),breasts.getYOffset(),breasts.getZOffset(),breasts.getCleavage(),context.config.getBodySettings().shape(),left,right,depth);
        var cache=MESHES.computeIfAbsent(context.entity,key->new IdentityHashMap<>());
        var cached=cache.get(cube);
        if(cached==null || !state.equals(cached.state)) {
            cached=new Mesh(state,deformFaces(levels[detail],part,settings,left,right,depth,context)); cache.put(cube,cached);
        }
        for(int f=0;f<levels[detail].length;f++) {
            var face=levels[detail][f]; var mesh=cached.faces[f];
            // Coplanar internal hip caps can appear as a dark raster line through the joined skin.
            // Keep them for moving/disconnected poses; the resting external envelope closes the joint.
            if(AuthoredModel.get()!=null && context.config.getBodySettings().shape()==BodySettings.BreastShape.NATURAL
                    && context.entity.getPose()==net.minecraft.world.entity.Pose.STANDING && context.entity.walkAnimation.speed()<.01
                    && Math.abs(face.max.y-face.min.y)<.001
                    && (part==BodyDeformation.Part.TORSO?face.min.y>=11.9:face.max.y<=.01)) continue;
            for(int row=0;row<face.rows;row++) for(int col=0;col<face.columns;col++) for(int corner=0;corner<4;corner++) {
                int i=(row+(corner>=2?1:0))*(face.columns+1)+col+(corner==1||corner==2?1:0),j=i*6;
                var v=face.grid[i];
                normal.set(mesh[j+3],mesh[j+4],mesh[j+5]); pose.transformNormal(normal,normal);
                consumer.addVertex(pose.pose(),mesh[j],mesh[j+1],mesh[j+2]).setColor(color).setUv(v.u,v.v)
                        .setOverlay(overlay).setLight(light).setNormal(normal.x,normal.y,normal.z);
            }
        }
    }
    private static float[][] deformFaces(Face[] faces,BodyDeformation.Part part,BodySettings settings,
            double left,double right,double depth,BodyRenderContext context) {
        float[][] result=new float[faces.length][];
        Vector3f tangent=new Vector3f(),normal=new Vector3f();
        for(int f=0;f<faces.length;f++) {
            var face=faces[f];
            float[] mesh=new float[face.grid.length*6];
            for(int i=0;i<face.grid.length;i++) {
                var v=face.grid[i];
                var p=point(part,v.x,v.y,v.z,settings,left,right,depth,context);
                var a=neighbor(face,v,face.du,1,part,settings,left,right,depth,context);
                var b=neighbor(face,v,face.du,-1,part,settings,left,right,depth,context);
                var c=neighbor(face,v,face.dv,1,part,settings,left,right,depth,context);
                var d=neighbor(face,v,face.dv,-1,part,settings,left,right,depth,context);
                tangent.set((float)(a.x()-b.x()),(float)(a.y()-b.y()),(float)(a.z()-b.z()));
                normal.set((float)(c.x()-d.x()),(float)(c.y()-d.y()),(float)(c.z()-d.z()));
                tangent.cross(normal,normal).normalize();
                int j=i*6; mesh[j]=(float)p.x()/16; mesh[j+1]=(float)p.y()/16; mesh[j+2]=(float)p.z()/16;
                mesh[j+3]=normal.x; mesh[j+4]=normal.y; mesh[j+5]=normal.z;
            }
            result[f]=mesh;
        }
        return result;
    }
}
