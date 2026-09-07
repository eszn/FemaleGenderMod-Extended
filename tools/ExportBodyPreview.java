import com.wildfire.physics.*;
import com.wildfire.main.entitydata.BodySettings;
import org.joml.Vector3f;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Export production mesh/deformation/forces; no Minecraft startup or graphics libraries. */
public class ExportBodyPreview {
    static final BodySettings SETTINGS=new BodySettings(.5f,.5f,.5f,.5f,BodySettings.BreastShape.NATURAL,true,.5f);
    record Surface(BodyDeformation.Part part,AuthoredBodyMesh.Triangle triangle) {}
    static void tick(SecondaryMotion.Response response,DampedSpring[] springs) {
        response.first().tick(springs[0]); response.second().tick(springs[1]); response.third().tick(springs[2]);
    }
    static DampedSpring[] springs() { return new DampedSpring[]{new DampedSpring(),new DampedSpring(),new DampedSpring()}; }
    static void vertex(DataOutputStream out,Vector3f p,Vector3f n) throws IOException {
        if(!p.isFinite() || !n.isFinite()) throw new IllegalStateException("Non-finite export");
        out.writeFloat(p.x);out.writeFloat(p.y);out.writeFloat(p.z);out.writeFloat(n.x);out.writeFloat(n.y);out.writeFloat(n.z);
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]),output=root.resolve("build/cpu-preview"); Files.createDirectories(output);
        AuthoredBodyMesh mesh;
        try(var reader=Files.newBufferedReader(root.resolve("local-models/resources/assets/wildfire_gender/body/jenny-mesh.json"))) { mesh=AuthoredBodyMesh.read(reader); }
        var surfaces=new ArrayList<Surface>();
        for(var part:BodyDeformation.Part.values()) for(var triangle:mesh.triangles(part)) surfaces.add(new Surface(part,triangle));
        for(int mode=0;mode<3;mode++) {
            var armor=mode==0?null:com.google.gson.JsonParser.parseString(Files.readString(root.resolve("src/datagen/generated/assets/minecraft/wildfire_gender_data/"+(mode==1?"leather":"diamond")+".json"))).getAsJsonObject();
            double chestSupport=armor==null?0:armor.get("resistance").getAsDouble(),legSupport=mode==0?0:mode==1?.8:1;
            float tightness=armor!=null && armor.has("tightness")?armor.get("tightness").getAsFloat():0;
            var left=springs();var right=springs();var body=springs();var input=new MotionInput();Object world=new Object();
            double peakBreast=0,peakButt=0,walkBreast=0,walkButt=0;float walkPosition=0;
            try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(output.resolve("motion-"+mode+".bin"))))) {
                out.writeInt(160);out.writeInt(surfaces.size());
                for(int frame=0;frame<160;frame++) {
                    boolean walking=frame>=20 && frame<70;
                    float speed=walking?.65f:0;walkPosition+=speed;
                    double x=frame<20?0:frame<70?(frame-20)*.08:4;
                    double y=frame>=95 && frame<=113?Math.max(0,.028*(frame-95)*(113-frame)):0;
                    float yaw=frame<70?0:frame<90?(frame-70)*4.5f:90;
                    var sample=input.update(x,81+y,0,yaw,frame,world);
                    float bust=.8f*(1-.12f*tightness);
                    if(sample.reset() || mode==2) { for(var s:left)s.reset();for(var s:right)s.reset();for(var s:body)s.reset(); }
                    else {
                        tick(SecondaryMotion.breast(sample,bust,.333f,.75f,-1,chestSupport,false,y==0,walkPosition,speed),left);
                        tick(SecondaryMotion.breast(sample,bust,.333f,.75f,1,chestSupport,false,y==0,walkPosition,speed),right);
                        tick(SecondaryMotion.body(sample,SETTINGS,legSupport,false,y==0,walkPosition,speed),body);
                    }
                    var motion=new AuthoredBodyMesh.Motion((float)left[1].position(),(float)left[0].position(),(float)right[1].position(),(float)right[0].position(),
                            (float)body[0].position(),(float)body[1].position(),(float)body[2].position());
                    peakBreast=Math.max(peakBreast,Math.abs(motion.leftY()));peakButt=Math.max(peakButt,Math.abs(motion.buttLeft()));
                    if(walking) {walkBreast=Math.max(walkBreast,Math.abs(motion.leftY()));walkButt=Math.max(walkButt,Math.abs(motion.buttLeft()));}
                    for(var surface:surfaces) for(var v:new AuthoredBodyMesh.Vertex[]{surface.triangle.a(),surface.triangle.b(),surface.triangle.c()}) {
                        var p=AuthoredBodyMesh.deform(v.point(),surface.triangle.group(),SETTINGS,bust,motion);
                        var n=AuthoredBodyMesh.normal(v,surface.triangle.group(),SETTINGS,bust,motion);
                        // A neutral garment envelope visualizes the same normal inflation used by the live pass.
                        if(mode>0) p.fma(surface.triangle.uvPart()==BodyDeformation.Part.TORSO?1:.5f,n);
                        if(surface.part!=BodyDeformation.Part.TORSO) {
                            float phase=surface.part==BodyDeformation.Part.LEFT_LEG?(float)Math.PI:0;
                            float angle=(float)Math.cos(walkPosition*.6662f+phase)*1.4f*speed;
                            float t=AuthoredBodyMesh.legWeight(v.point().y);
                            var posed=new Vector3f(p).sub(0,12,0).rotateX(angle).add(0,12,0);
                            p.lerp(posed,t);n.lerp(new Vector3f(n).rotateX(angle),t).normalize();
                        }
                        vertex(out,p,n);
                    }
                }
            }
            if(mode==0 && (walkBreast<.15 || walkButt<.08)) throw new IllegalStateException("Walking motion remains too small");
            if(mode==2 && (peakBreast!=0 || peakButt!=0)) throw new IllegalStateException("Rigid armor moved");
            if(Math.abs(left[0].position())>.005 || Math.abs(body[0].position())>.005) throw new IllegalStateException("Motion did not settle");
            String report="mode="+mode+"; peak breast="+peakBreast+"; peak butt="+peakButt+"; walking breast="+walkBreast+"; walking butt="+walkButt+"; settled=true";
            Files.writeString(output.resolve("motion-"+mode+".txt"),report);System.out.println(report);
        }
    }
}
