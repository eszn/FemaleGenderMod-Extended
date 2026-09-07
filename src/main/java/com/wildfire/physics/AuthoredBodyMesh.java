package com.wildfire.physics;

import com.google.gson.JsonParser;
import com.wildfire.main.entitydata.BodySettings;
import java.io.Reader;
import java.util.*;
import org.joml.Vector3f;

/** Authored soft surfaces with a rounded trunk and continuous thigh attachments. */
public final class AuthoredBodyMesh {
    public record Vertex(Vector3f point, Vector3f normal) {}
    public record Triangle(String group, BodyDeformation.Part uvPart, Vertex a, Vertex b, Vertex c) {}
    public record Motion(float leftX,float leftY,float rightX,float rightY,float buttLeft,float buttRight,float depth) {
        public static final Motion STILL=new Motion(0,0,0,0,0,0,0);
    }
    public record Adjustment(float x,float y,float z,float cleavage) { public static final Adjustment NONE=new Adjustment(0,0,0,0); }
    private record Panel(String group,BodyDeformation.Part rig,Vector3f[] points,Vector3f normal) {}
    private final String character;
    private final Map<BodyDeformation.Part,List<Triangle>> parts=new EnumMap<>(BodyDeformation.Part.class);
    private final Map<BodyDeformation.Part,List<Triangle>> refined=new EnumMap<>(BodyDeformation.Part.class);
    private AuthoredBodyMesh(String character) {
        this.character=character;
        for(var part:BodyDeformation.Part.values()) parts.put(part,new ArrayList<>());
    }
    public String character() { return character; }
    public List<Triangle> sourceTriangles(BodyDeformation.Part part) { return Collections.unmodifiableList(parts.get(part)); }
    public List<Triangle> triangles(BodyDeformation.Part part) { return Collections.unmodifiableList(refined.get(part)); }
    public static AuthoredBodyMesh read(Reader reader) {
        var json=JsonParser.parseReader(reader).getAsJsonObject();
        var result=new AuthoredBodyMesh(json.get("character").getAsString());
        var panels=new ArrayList<Panel>();
        for(var element:json.getAsJsonArray("quads")) {
            var q=element.getAsJsonObject(); var points=new Vector3f[4];
            for(int i=0;i<4;i++) {
                var p=q.getAsJsonArray("points").get(3-i).getAsJsonArray();
                points[i]=new Vector3f(p.get(0).getAsFloat(),24-p.get(1).getAsFloat(),p.get(2).getAsFloat());
                if(!points[i].isFinite()) throw new IllegalArgumentException("Non-finite imported vertex");
            }
            var normal=new Vector3f(points[1]).sub(points[0]).cross(new Vector3f(points[2]).sub(points[0])).normalize();
            if(!normal.isFinite()) continue;
            var rig=switch(q.get("rig").getAsString()) { case "left_leg"->BodyDeformation.Part.LEFT_LEG; case "right_leg"->BodyDeformation.Part.RIGHT_LEG; default->BodyDeformation.Part.TORSO; };
            panels.add(new Panel(q.get("group").getAsString(),rig,points,normal));
        }
        // Smooth lighting across adjacent authored panels without moving or subdividing any position.
        for(var panel:panels) {
            var vertices=new ArrayList<Vertex>();
            for(var point:panel.points) {
                var n=new Vector3f();
                boolean soft=panel.group.equals("breast") || panel.group.equals("buttock");
                for(var neighbor:panels) {
                    if(!neighbor.group.equals(panel.group) || neighbor.normal.dot(panel.normal)<.15f) continue;
                    float distance=Float.MAX_VALUE;
                    for(var p:neighbor.points) distance=Math.min(distance,p.distanceSquared(point));
                    float radius=soft?.81f:.0064f;
                    if(distance<radius) n.fma((1-distance/radius)*(1-distance/radius),neighbor.normal);
                }
                vertices.add(new Vertex(point,n.lengthSquared()>0?n.normalize():new Vector3f(panel.normal)));
            }
            // Clip each original triangle, preserving its surface even for non-planar authored quads.
            for(var triangle:List.of(List.of(vertices.get(0),vertices.get(1),vertices.get(2)),List.of(vertices.get(0),vertices.get(2),vertices.get(3)))) {
                if(panel.rig!=BodyDeformation.Part.TORSO) result.add(panel.rig,panel.rig,panel.group,triangle);
                else {
                    // Texture seams follow the vanilla atlas; both sides retain the authored hip bone.
                    result.add(panel.rig,BodyDeformation.Part.TORSO,panel.group,clip(triangle,1,12,false));
                    var lower=clip(triangle,1,12,true);
                    result.add(panel.rig,BodyDeformation.Part.LEFT_LEG,panel.group,clip(lower,0,0,true));
                    result.add(panel.rig,BodyDeformation.Part.RIGHT_LEG,panel.group,clip(lower,0,0,false));
                }
            }
        }
        result.refineCore();
        if(json.has("smooth_surfaces")) {
            for(var surfaces:result.refined.values()) surfaces.removeIf(t->t.group.equals("breast") || t.group.equals("buttock"));
            for(var element:json.getAsJsonArray("smooth_surfaces")) {
                var surface=element.getAsJsonObject();String group=surface.get("group").getAsString();
                var vertices=new ArrayList<Vertex>();
                for(var value:surface.getAsJsonArray("vertices")) {
                    var v=value.getAsJsonArray();var p=new Vector3f(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat());
                    var n=new Vector3f(v.get(3).getAsFloat(),v.get(4).getAsFloat(),v.get(5).getAsFloat()).normalize();
                    if(!p.isFinite() || !n.isFinite()) throw new IllegalArgumentException("Invalid cleaned surface");
                    vertices.add(new Vertex(p,n));
                }
                for(var face:surface.getAsJsonArray("faces")) {
                    var ids=face.getAsJsonArray();var triangle=List.of(vertices.get(ids.get(0).getAsInt()),vertices.get(ids.get(1).getAsInt()),vertices.get(ids.get(2).getAsInt()));
                    result.addRefined(BodyDeformation.Part.TORSO,group,clip(triangle,1,12,false));
                    var lower=clip(triangle,1,12,true);
                    result.addRefined(BodyDeformation.Part.LEFT_LEG,group,clip(lower,0,0,true));
                    result.addRefined(BodyDeformation.Part.RIGHT_LEG,group,clip(lower,0,0,false));
                }
            }
        }
        return result;
    }
    private void addRefined(BodyDeformation.Part uvPart,String group,List<Vertex> polygon) {
        for(int i=1;i+1<polygon.size();i++) {
            var a=polygon.get(0);var b=polygon.get(i);var c=polygon.get(i+1);
            if(new Vector3f(b.point).sub(a.point).cross(new Vector3f(c.point).sub(a.point)).lengthSquared()>1e-12)
                refined.get(BodyDeformation.Part.TORSO).add(new Triangle(group,uvPart,a,b,c));
        }
    }
    private void refineCore() {
        // Keep the authored breast/buttock panels. Replace overlapping box panels only in the
        // trunk and legs, following Jenny's shoulder, waist, hip, knee and ankle proportions.
        for(var part:BodyDeformation.Part.values()) {
            var surfaces=new ArrayList<Triangle>(); refined.put(part,surfaces);
            for(var t:parts.get(part)) if(t.group.equals("breast") || t.group.equals("buttock")) surfaces.add(t);
            float start=part==BodyDeformation.Part.TORSO?0:12;
            for(int row=0;row<24;row++) for(int column=0;column<48;column++) {
                float y=start+row*.5f,a=(float)(column*Math.PI/24),b=(float)((column+1)*Math.PI/24);
                var q=new Vertex[]{coreVertex(part,y,a),coreVertex(part,y+.5f,a),coreVertex(part,y+.5f,b),coreVertex(part,y,b)};
                if(part==BodyDeformation.Part.RIGHT_LEG) q=new Vertex[]{q[3],q[2],q[1],q[0]};
                String group=part==BodyDeformation.Part.TORSO?"body":"thigh";
                surfaces.add(new Triangle(group,part,q[0],q[1],q[2])); surfaces.add(new Triangle(group,part,q[0],q[2],q[3]));
            }
            if(part!=BodyDeformation.Part.TORSO) for(int column=0;column<48;column++) {
                var center=new Vertex(new Vector3f(part==BodyDeformation.Part.LEFT_LEG?1.8f:-1.8f,24,0),new Vector3f(0,1,0));
                var a=new Vertex(corePoint(part,24,(float)(column*Math.PI/24)),new Vector3f(0,1,0));
                var b=new Vertex(corePoint(part,24,(float)((column+1)*Math.PI/24)),new Vector3f(0,1,0));
                surfaces.add(part==BodyDeformation.Part.LEFT_LEG?new Triangle("thigh",part,center,b,a):new Triangle("thigh",part,center,a,b));
            }
        }
    }
    private static float profile(float y,float[] ys,float[] values) {
        if(y<=ys[0]) return values[0];
        for(int i=1;i<ys.length;i++) if(y<=ys[i]) {
            float h=ys[i]-ys[i-1],t=(y-ys[i-1])/h;
            float secant=(values[i]-values[i-1])/h;
            float before=i==1?secant:(values[i-1]-values[i-2])/(ys[i-1]-ys[i-2]);
            float after=i==ys.length-1?secant:(values[i+1]-values[i])/(ys[i+1]-ys[i]);
            float m0=before*secant<=0?0:2*before*secant/(before+secant);
            float m1=after*secant<=0?0:2*after*secant/(after+secant);
            return (2*t*t*t-3*t*t+1)*values[i-1]+(t*t*t-2*t*t+t)*h*m0+(-2*t*t*t+3*t*t)*values[i]+(t*t*t-t*t)*h*m1;
        }
        return values[values.length-1];
    }
    public static Vector3f corePoint(BodyDeformation.Part part,float y,float angle) {
        float s=(float)Math.sin(angle),c=(float)Math.cos(angle);
        float hip=profile(y,new float[]{0,2,5,6,8,10,12,14,17,24},new float[]{3.95f,3.4f,2.67f,2.67f,3.5f,4.4f,4.7f,4.4f,3.65f,3.4f});
        if(part==BodyDeformation.Part.TORSO) return new Vector3f(hip*s,y,-1.8f*c);
        float blend=smooth((y-12)/1.7f);
        float center=profile(y,new float[]{12,14,17,24},new float[]{2.35f,2.25f,1.9f,1.8f});
        float radius=profile(y,new float[]{12,14,17,24},new float[]{2.35f,2.15f,1.7f,1.6f});
        float depth=profile(y,new float[]{12,14,17,24},new float[]{1.8f,1.8f,1.75f,1.7f});
        float x=(1-blend)*hip*Math.max(0,s)+blend*(center+radius*s);
        return new Vector3f(part==BodyDeformation.Part.LEFT_LEG?x:-x,y,-depth*c);
    }
    private static Vertex coreVertex(BodyDeformation.Part part,float y,float angle) {
        var du=corePoint(part,y,angle+.001f).sub(corePoint(part,y,angle-.001f));
        var dv=corePoint(part,y+.001f,angle).sub(corePoint(part,y-.001f,angle));
        var normal=dv.cross(du).normalize(); if(part==BodyDeformation.Part.RIGHT_LEG) normal.negate();
        return new Vertex(corePoint(part,y,angle),normal);
    }
    private void add(BodyDeformation.Part part,BodyDeformation.Part uvPart,String group,List<Vertex> polygon) {
        for(int i=1;i+1<polygon.size();i++) {
            var a=polygon.get(0); var b=polygon.get(i); var c=polygon.get(i+1);
            if(new Vector3f(b.point).sub(a.point).cross(new Vector3f(c.point).sub(a.point)).lengthSquared()>1e-12)
                parts.get(part).add(new Triangle(group,uvPart,a,b,c));
        }
    }
    private static List<Vertex> clip(List<Vertex> input,int axis,float boundary,boolean above) {
        var out=new ArrayList<Vertex>(); if(input.isEmpty()) return out;
        // A surface lying exactly in the cut plane belongs to one side, not both.
        if(above && input.stream().allMatch(v->Math.abs(v.point.get(axis)-boundary)<.000001f)) return out;
        var a=input.getLast(); float da=(a.point.get(axis)-boundary)*(above?1:-1);
        for(var b:input) {
            float db=(b.point.get(axis)-boundary)*(above?1:-1);
            if((da>=0)!=(db>=0)) {
                float t=da/(da-db);
                out.add(new Vertex(new Vector3f(a.point).lerp(b.point,t),new Vector3f(a.normal).lerp(b.normal,t).normalize()));
            }
            if(db>=0) out.add(b); a=b; da=db;
        }
        return out;
    }
    private static float smooth(float value) { float t=Math.clamp(value,0,1); return t*t*(3-2*t); }
    public static float legWeight(float y) { return smooth((y-12)/3); }
    private static float bell(float y,float center,float radius) { return 1-smooth(Math.abs(y-center)/radius); }
    public static Vector3f deform(Vector3f source,String group,BodySettings settings,float bust,Motion motion) {
        return deform(source,group,settings,bust,motion,Adjustment.NONE);
    }
    public static Vector3f deform(Vector3f source,String group,BodySettings settings,float bust,Motion motion,Adjustment adjustment) {
        var p=new Vector3f(source); float x=p.x,y=p.y,z=p.z;
        // At the Jenny preset, sizing leaves authored soft surfaces and the rounded core unchanged.
        float hip=bell(y,12,5),waist=bell(y,7.4f,4),thigh=bell(y,16,4);
        p.x*=1+.28f*(settings.hips()-.5f)*hip-.22f*(settings.waist()-.5f)*waist+.22f*(settings.thighs()-.5f)*thigh;
        float rear=smooth((z-1.4f)/2.2f)*bell(y,12.3f,5);
        p.z+=(z-1.4f)*.8f*(settings.buttocks()-.5f)*rear;
        p.y+=(x>=0?motion.buttLeft:motion.buttRight)*rear;
        p.z+=motion.depth*rear;
        if(group.equals("breast")) {
            float ratio=Math.max(0,bust)/.8f,scale=(float)Math.sqrt(ratio),anchor=x>=0?2.1f:-2.1f;
            float distance=Math.max(0,-z-1.75f),transition=1.6f,t=Math.clamp(distance/transition,0,1),sizing=smooth(t);
            p.x=anchor+(p.x-anchor)*(1+.35f*(scale-1)*sizing);
            p.y=2.7f+(p.y-2.7f)*(1+(scale-1)*sizing);
            // Integrate the smooth attachment weight: projection stays monotone even
            // for small sizes, and rear/internal vertices cannot grow through the back.
            float extension=distance<transition?transition*(t*t*t-.5f*t*t*t*t):distance-transition*.5f;
            p.z-=(ratio-1)*extension;
            float weight=smooth((-z-1.65f)/3.5f);
            p.x+=(x>=0?motion.leftX:motion.rightX)*weight;
            p.y+=(x>=0?motion.leftY:motion.rightY)*weight;
            float angle=(x>=0?-1:1)*adjustment.cleavage*(float)Math.PI*weight;
            float dx=p.x-anchor,dz=p.z+1.75f;
            p.x=anchor+dx*(float)Math.cos(angle)+dz*(float)Math.sin(angle)-(x>=0?1:-1)*adjustment.x*weight;
            p.z=-1.75f-dx*(float)Math.sin(angle)+dz*(float)Math.cos(angle)+adjustment.z*weight;
            p.y-=adjustment.y*weight;
        }
        return p;
    }
    public static Vector3f normal(Vertex vertex,String group,BodySettings settings,float bust,Motion motion) {
        return normal(vertex,group,settings,bust,motion,Adjustment.NONE);
    }
    public static Vector3f normal(Vertex vertex,String group,BodySettings settings,float bust,Motion motion,Adjustment adjustment) {
        var n=vertex.normal; var u=new Vector3f(Math.abs(n.y)<.9f?new Vector3f(0,1,0):new Vector3f(1,0,0)).cross(n).normalize().mul(.01f);
        var v=new Vector3f(n).cross(u);
        var center=deform(vertex.point,group,settings,bust,motion,adjustment);
        var du=deform(new Vector3f(vertex.point).add(u),group,settings,bust,motion,adjustment).sub(center);
        var dv=deform(new Vector3f(vertex.point).add(v),group,settings,bust,motion,adjustment).sub(center);
        return du.cross(dv).normalize();
    }
}
