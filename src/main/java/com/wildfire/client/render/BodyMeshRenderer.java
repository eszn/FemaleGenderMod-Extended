package com.wildfire.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildfire.main.entitydata.BodySettings;
import com.wildfire.physics.BodyDeformation;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/** Subdivide original UV faces and evaluate smooth surface normals without changing the skeleton. */
public final class BodyMeshRenderer {
    private record Vertex(float x,float y,float z,float u,float v) {}
    private record Face(Vertex[] grid,int columns,int rows,Vector3f du,Vector3f dv) {}
    private static final Map<ModelPart.Cube,Face[]> CACHE=new WeakHashMap<>();
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
    private static Face[] bake(ModelPart.Cube cube) {
        Face[] faces=new Face[cube.polygons.length];
        for(int i=0;i<faces.length;i++) {
            var q=cube.polygons[i].vertices;
            var du=new Vector3f(q[1].pos).sub(q[0].pos);
            var dv=new Vector3f(q[3].pos).sub(q[0].pos);
            // One pixel across, three quarters of a pixel vertically. Tiny cap faces stay inexpensive.
            int columns=Math.max(1,(int)Math.ceil(du.length()/(Math.abs(du.y)>.01?.75:1)));
            int rows=Math.max(1,(int)Math.ceil(dv.length()/(Math.abs(dv.y)>.01?.75:1)));
            Vertex[] grid=new Vertex[(columns+1)*(rows+1)];
            for(int row=0;row<=rows;row++) for(int col=0;col<=columns;col++)
                grid[row*(columns+1)+col]=sample(q,col/(float)columns,row/(float)rows);
            faces[i]=new Face(grid,columns,rows,du.normalize().mul(.002f),dv.normalize().mul(.002f));
        }
        return faces;
    }
    private static BodyDeformation.Point point(BodyDeformation.Part part,double x,double y,double z,BodySettings settings,
                                               double left,double right,double depth) {
        double bounce=(part==BodyDeformation.Part.TORSO?x>0:part==BodyDeformation.Part.LEFT_LEG)?left:right;
        return BodyDeformation.deform(part,x,y,z,settings,bounce,depth);
    }
    public static void render(ModelPart.Cube cube,BodyDeformation.Part part,BodyRenderContext context,
                              PoseStack.Pose pose,VertexConsumer consumer,int light,int overlay,int color) {
        var settings=context.config.getBodySettings(); var physics=context.config.getBodyPhysics();
        double left=physics.vertical(true,context.partialTick),right=physics.vertical(false,context.partialTick),depth=physics.depth(context.partialTick);
        Vector3f tangent=new Vector3f(),normal=new Vector3f();
        for(var face:CACHE.computeIfAbsent(cube,BodyMeshRenderer::bake)) {
            float[] mesh=new float[face.grid.length*6];
            for(int i=0;i<face.grid.length;i++) {
                var v=face.grid[i];
                var p=point(part,v.x,v.y,v.z,settings,left,right,depth);
                var a=point(part,v.x+face.du.x,v.y+face.du.y,v.z+face.du.z,settings,left,right,depth);
                var b=point(part,v.x-face.du.x,v.y-face.du.y,v.z-face.du.z,settings,left,right,depth);
                var c=point(part,v.x+face.dv.x,v.y+face.dv.y,v.z+face.dv.z,settings,left,right,depth);
                var d=point(part,v.x-face.dv.x,v.y-face.dv.y,v.z-face.dv.z,settings,left,right,depth);
                tangent.set((float)(a.x()-b.x()),(float)(a.y()-b.y()),(float)(a.z()-b.z()));
                normal.set((float)(c.x()-d.x()),(float)(c.y()-d.y()),(float)(c.z()-d.z()));
                tangent.cross(normal,normal).normalize(); pose.transformNormal(normal,normal);
                int j=i*6; mesh[j]=(float)p.x()/16; mesh[j+1]=(float)p.y()/16; mesh[j+2]=(float)p.z()/16;
                mesh[j+3]=normal.x; mesh[j+4]=normal.y; mesh[j+5]=normal.z;
            }
            for(int row=0;row<face.rows;row++) for(int col=0;col<face.columns;col++) for(int corner=0;corner<4;corner++) {
                int i=(row+(corner>=2?1:0))*(face.columns+1)+col+(corner==1||corner==2?1:0),j=i*6;
                var v=face.grid[i];
                consumer.addVertex(pose.pose(),mesh[j],mesh[j+1],mesh[j+2]).setColor(color).setUv(v.u,v.v)
                        .setOverlay(overlay).setLight(light).setNormal(mesh[j+3],mesh[j+4],mesh[j+5]);
            }
        }
    }
}
