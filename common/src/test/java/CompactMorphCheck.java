import java.util.*;
import java.nio.file.*;
import net.krona.mutagen.client.body.*;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CompactMorphCheck {
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static Object field(ModelPart part,String name) throws Exception {
        var field=ModelPart.class.getDeclaredField(name);field.setAccessible(true);return field.get(part);
    }
    @SuppressWarnings("unchecked")
    static void collect(ModelPart part,List<ModelPart> path,List<MorphCuboid.Core> result) throws Exception {
        for(var cube:(List<ModelPart.Cube>)field(part,"cubes")) result.add(new MorphCuboid.Core(cube,path));
        for(var child:((Map<String,ModelPart>)field(part,"children")).values()) {
            var next=new ArrayList<>(path);next.add(child);collect(child,List.copyOf(next),result);
        }
    }
    static MorphCuboid.Core core(ModelPart part) throws Exception {
        var result=new ArrayList<MorphCuboid.Core>();collect(part,List.of(),result);
        return result.stream().max(Comparator.comparingDouble(c->{var s=c.box().size();return s.x*s.y*s.z;})).orElseThrow();
    }
    public static void main(String[] args) throws Exception {
        var cacheField=Box.class.getDeclaredField("CACHE");cacheField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<ModelPart,Box> cache=(Map<ModelPart,Box>)cacheField.get(null);
        var previews=new ArrayList<Object>();
        for(String strain:List.of("bee","guardian","phantom")) {
            ModelPart playerRoot=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,false),64,64).bakeRoot();
            PlayerModel<?> player=new PlayerModel<>(playerRoot,false);
            for(Strain.Bone bone:Strain.Bone.values()) {
                ModelPart part=BodyMorph.bone(player,bone);cache.put(part,core(part).box());
            }
            ModelPart mob=switch(strain) {
                case "bee"->BeeModel.createBodyLayer().bakeRoot();
                case "guardian"->GuardianModel.createBodyLayer().bakeRoot();
                default->PhantomModel.createBodyLayer().bakeRoot();
            };
            ModelPart carrier=strain.equals("bee")?mob.getChild("bone").getChild("body")
                    :mob.getChild(strain.equals("guardian")?"head":"body");
            Matrix4f bodyGoal=new Matrix4f().scale(0.9375F);
            if(strain.equals("bee")) Poses.apply(bodyGoal,mob.getChild("bone"));
            Poses.apply(bodyGoal,carrier);
            var bodyCore=core(carrier);
            Vector3f[][] previous=new Vector3f[6][24];
            for(int i=0;i<=1000;i++) {
                float progress=21+79F*i/1000;
                playerRoot.getAllParts().forEach(ModelPart::resetPose);
                var frame=new CompactMorph.Frame(player,strain,progress);
                var poses=new EnumMap<Strain.Bone,WolfMorph.Pose>(Strain.Bone.class);
                var faces=new ArrayList<Object>();
                for(Strain.Bone bone:Strain.Bone.values()) {
                    boolean merged=!strain.equals("phantom") || bone==Strain.Bone.BODY||bone==Strain.Bone.RIGHT_LEG||bone==Strain.Bone.LEFT_LEG;
                    ModelPart target=carrier;
                    Matrix4f goal=new Matrix4f(bodyGoal);
                    if(strain.equals("phantom")&&!merged) {
                        target=carrier.getChild(switch(bone) {
                            case HEAD->"head";case RIGHT_ARM->"right_wing_base";default->"left_wing_base";
                        });
                        Poses.apply(goal,target);
                    }
                    var core=core(target);
                    Box piece=ContinuousMorph.partition(core.box(),bone,strain.equals("phantom")?2:5);
                    var raw=MorphCuboid.surface(core.cube(),piece);
                    Quaternionf alignment=CompactMorph.alignment(strain,bone,core.box());
                    var surface=MorphCuboid.rotate(raw,new Quaternionf(alignment).conjugate());
                    var part=BodyMorph.bone(player,bone);
                    var posed=frame.reshape(bone,part,core,goal,surface.box(),bodyCore,bodyGoal);
                    poses.put(bone,posed);
                    var size=posed.shape().size();
                    check(size.x>0&&size.y>0&&size.z>0,"Collapsed compact anatomy");
                    for(int k=0;k<24;k++) {
                        var point=Poses.rotation(part).transform(MorphCuboid.position(surface.box(),posed.shape(),surface.vertices().get(k))).add(posed.pivot());
                        check(point.isFinite(),"Invalid compact vertex");
                        if(previous[bone.ordinal()][k]!=null) check(previous[bone.ordinal()][k].distance(point)<0.6F,"Compact vertex teleports");
                        previous[bone.ordinal()][k]=point;
                        if(i==1000) {
                            var v=raw.vertices().get(k);
                            Vector3f expected=new Matrix4f(goal).mul(core.relative()).transformPosition(new Vector3f(v.x(),v.y(),v.z())).mul(16);
                            check(expected.distance(point)<0.001F,"Wrong compact endpoint/wing alignment: "+strain+" "+bone);
                        }
                        if(k%4==3 && Set.of(0,300,550,750,900,1000).contains(i)) {
                            var quad=new ArrayList<Object>();
                            for(int q=k-3;q<=k;q++) {var v=previous[bone.ordinal()][q];quad.add(List.of(v.x,v.y,v.z));}
                            faces.add(Map.of("bone",bone.name(),"vertices",quad));
                        }
                    }
                    if(bone==Strain.Bone.HEAD&&CompactMorph.fuse(progress)==0)
                        check(posed.shape().equals(cache.get(part)),"Head flattens before coordinated fusion");
                }
                if(CompactMorph.fuse(progress)==0) {
                    var body=poses.get(Strain.Bone.BODY);var head=poses.get(Strain.Bone.HEAD);
                    var neck=Poses.rotation(player.body).conjugate().transform(new Vector3f(head.pivot()).sub(body.pivot()));
                    check(Math.abs(neck.x)<0.0001F&&Math.abs(neck.y)<0.0001F&&Math.abs(neck.z)<=1.001F,"Disconnected compact neck");
                }
                if(!faces.isEmpty()) previews.add(Map.of("strain",strain,"progress",progress,"faces",faces));
            }
        }
        String export=System.getProperty("mutagen.preview");
        if(export!=null) Files.writeString(Path.of(export),new com.google.gson.Gson().toJson(previews));
        System.out.println("PASS: 18018 actual bee/guardian/phantom core poses, connected necks, late head deformation and aligned wing endpoints");
    }
}
