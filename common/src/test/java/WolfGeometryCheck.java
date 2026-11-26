import java.util.*;
import net.krona.mutagen.client.body.*;
import net.krona.mutagen.mixin.client.LivingEntityRendererInvoker;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.core.Direction;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class WolfGeometryCheck {
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    @SuppressWarnings("unchecked")
    static List<ModelPart.Cube> cubes(ModelPart part) throws Exception {
        var field=ModelPart.class.getDeclaredField("cubes"); field.setAccessible(true);
        return (List<ModelPart.Cube>)field.get(part);
    }
    static ModelPart.Cube human(Strain.Bone bone, boolean mirror) {
        float x=-2,y=0,z=-2,w=4,h=12,d=4; int u=0,v=16;
        switch(bone) {
            case HEAD -> {x=-4;y=-8;z=-4;w=8;h=8;d=8;u=0;v=0;}
            case BODY -> {x=-4;w=8;u=16;v=16;}
            case RIGHT_ARM -> {x=-3;y=-2;u=40;v=16;}
            case LEFT_ARM -> {x=-1;y=-2;u=32;v=48;}
            case LEFT_LEG -> {u=16;v=48;}
            default -> {}
        }
        return new ModelPart.Cube(u,v,x,y,z,w,h,d,0,0,0,mirror,64,64,EnumSet.allOf(Direction.class));
    }
    public static void main(String[] args) throws Exception {
        ModelPart root=LayerDefinition.create(WolfModel.createMeshDefinition(CubeDeformation.NONE),64,32).bakeRoot();
        WolfModel<?> animator=new WolfModel<>(root);
        float tailAngle=(float)Math.PI*0.65F;
        LivingEntityRendererInvoker angleSource=new LivingEntityRendererInvoker() {
            public float mutagen$getBob(net.minecraft.world.entity.LivingEntity entity,float partial) { return tailAngle; }
            public void mutagen$scale(net.minecraft.world.entity.LivingEntity entity,PoseStack stack,float partial) {}
        };
        for(int age=0;age<=100000;age+=100) {
            float parameter=MorphPlan.animationParameter(angleSource,null,0.5F,age);
            animator.setupAnim(null,0,0,parameter,0,0);
            check(Math.abs(root.getChild("tail").xRot-tailAngle)<0.00001F,"Player age leaked into wolf tail angle");
        }
        var field=Box.class.getDeclaredField("CACHE");field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<ModelPart,Box> bounds=(Map<ModelPart,Box>)field.get(null);
        Box finalBody=null; Matrix4f bodyGoal=null; Quaternionf bodyRotation=null; Vector3f bodyPivot=null;
        String[] names={"wolf","zombie","drowned","skeleton","piglin","enderman","creeper"};
        for(Strain.Bone bone:Strain.Bone.values()) {
            String targetName=switch(bone) {
                case HEAD -> "head"; case BODY -> "body";
                case RIGHT_ARM -> "right_front_leg"; case LEFT_ARM -> "left_front_leg";
                case RIGHT_LEG -> "right_hind_leg"; case LEFT_LEG -> "left_hind_leg";
            };
            ModelPart target=root.getChild(targetName);
            ModelPart leaf=bone==Strain.Bone.HEAD?target.getChild("real_head"):target;
            var cube=cubes(leaf).stream().max(Comparator.comparingDouble(c -> {
                var s=MorphCuboid.bounds(c).size();return s.x*s.y*s.z;
            })).orElseThrow();
            var core=new MorphCuboid.Core(cube,leaf==target?List.of():List.of(leaf));
            Matrix4f goal=Poses.apply(new Matrix4f().scale(0.9375F),target);
            for(boolean mirror:new boolean[]{false,true}) {
                var source=human(bone,mirror);
                var corners=MorphCuboid.vertices(cube);
                check(corners.size()==24,"Core topology changed");
                for(var corner:corners) {
                    var matched=MorphCuboid.match(source,cube,corner);
                    check(matched.nx()==corner.nx()&&matched.ny()==corner.ny()&&matched.nz()==corner.nz(),"Wrong skin face");
                    var a=MorphCuboid.position(MorphCuboid.bounds(cube),MorphCuboid.bounds(source),corner).div(16);
                    check(a.distance(new Vector3f(matched.x(),matched.y(),matched.z()))<0.00001F,"Mirrored UV corner mismatch");
                }
            }
            var source=human(bone,bone==Strain.Bone.LEFT_ARM||bone==Strain.Bone.LEFT_LEG);
            ModelPart part=new ModelPart(List.of(source),Map.of());
            PartPose initial=switch(bone) {
                case HEAD,BODY -> PartPose.ZERO;
                case RIGHT_ARM -> PartPose.offset(-5,2,0); case LEFT_ARM -> PartPose.offset(5,2,0);
                case RIGHT_LEG -> PartPose.offset(-1.9F,12,0); case LEFT_LEG -> PartPose.offset(1.9F,12,0);
            };
            part.setInitialPose(initial);bounds.put(part,MorphCuboid.bounds(source));
            for(String name:names) {
                Vector3f[] previous=new Vector3f[24];
                for(int i=0;i<=1000;i++) {
                    part.resetPose();
                    var posed=ContinuousMorph.reshape(name,bone,part,core,goal,21+79F*i/1000);

                    check(posed.shape().size().x>0&&posed.shape().size().y>0&&posed.shape().size().z>0,"Core disappeared");
                    var corners=MorphCuboid.vertices(cube);
                    for(int k=0;k<24;k++) {
                        var vertex=Poses.rotation(part).transform(MorphCuboid.position(core.box(),posed.shape(),corners.get(k))).add(posed.pivot());
                        check(vertex.isFinite(),"Non-finite vertex");
                        if(previous[k]!=null) check(previous[k].distance(vertex)<0.5F,"Core vertex teleported");
                        previous[k]=vertex;
                    }
                    if(i==0) check(posed.shape().equals(MorphCuboid.bounds(source)),"Source geometry changed at start");
                    if(i==1000) {
                        Matrix4f actualGoal=new Matrix4f(goal).mul(core.relative());
                        check(posed.pivot().distance(Poses.pivot(actualGoal))<0.0001F,"Final mesh pivot mismatch");
                        check(posed.shape().size().distance(core.box().times(Poses.scale(actualGoal)).size())<0.0001F,"Final mesh dimensions mismatch");
                        check(Math.abs(Poses.rotation(part).dot(Poses.rotation(actualGoal)))>0.9999F,"Final mesh rotation mismatch");
                        if(bone==Strain.Bone.BODY&&name.equals("wolf")) {
                            finalBody=posed.shape();bodyGoal=actualGoal;bodyRotation=Poses.rotation(part);bodyPivot=posed.pivot();
                        }
                    }
                }
            }
        }
        Matrix4f tailGoal=Poses.apply(new Matrix4f().scale(0.9375F),root.getChild("tail"));
        var bodyCube=cubes(root.getChild("body")).getFirst();
        Matrix4f attached=ContinuousMorphRenderer.attachment(MorphCuboid.bounds(bodyCube),finalBody,bodyPivot,bodyRotation,bodyGoal,tailGoal,1);
        check(Poses.pivot(attached).distance(Poses.pivot(tailGoal))<0.0001F,"Final tail attachment mismatch");
        check(Math.abs(Poses.rotation(attached).dot(Poses.rotation(tailGoal)))>0.9999F,"Tail rotates twice with pelvis");
        System.out.println("PASS: 42042 fixed-topology mesh poses, mirrored UV correspondence, vanilla wolf tail timing and final tail attachment");
    }
}
