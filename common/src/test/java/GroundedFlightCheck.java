import net.krona.mutagen.client.body.CreatureKinematics;
import net.krona.mutagen.client.body.CreatureRestPose;
import net.krona.mutagen.mutation.CreatureLocomotion;
import net.minecraft.client.model.BeeModel;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The regressions reported in game: sliding fish, flapping on land, and upside-down flight pitch. */
public final class GroundedFlightCheck {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void near(float a, float b, String message) { check(Math.abs(a-b)<.00001F, message); }

    private static void nativePhantom(ModelPart root, float phase) {
        ModelPart body=root.getChild("body");
        for (String side : new String[]{"left","right"}) {
            float sign=side.equals("left")?1:-1;
            ModelPart base=body.getChild(side+"_wing_base");
            base.zRot=sign*(float)Math.cos(phase)*.28F;
            base.getChild(side+"_wing_tip").zRot=base.zRot;
        }
        body.getChild("tail_base").xRot=(float)Math.cos(phase*2)*.1F;
        body.getChild("tail_base").getChild("tail_tip").xRot=(float)Math.cos(phase*2)*.1F;
    }

    public static void main(String[] args) {
        check(CreatureLocomotion.guardianHop(100,true,false,false,false,1)>.4F,"Full guardian takes off on dry land");
        near(CreatureLocomotion.guardianHop(60,true,false,false,false,1),0,"Human legs do not hop early");
        near(CreatureLocomotion.guardianHop(100,true,true,false,false,1),0,"Swimming never triggers land hop");
        near(CreatureLocomotion.guardianHop(100,false,false,false,false,1),0,"No second hop in mid-air");
        near(CreatureLocomotion.guardianHop(100,true,false,false,false,0),0,"Idle guardian stays at rest");
        near(CreatureLocomotion.guardianHop(100,true,false,true,false,1),0,"Crouching can stop hopping");
        near(CreatureLocomotion.guardianHop(100,true,false,false,true,1),0,"Flight does not launch hops");
        double y=0,velocity=0,max=0; boolean ground=true; int hops=0;
        for (int tick=0;tick<100;tick++) {
            float hop=CreatureLocomotion.guardianHop(100,ground,false,false,false,1);
            if(hop>0) { velocity=hop;ground=false;hops++; }
            y+=velocity; max=Math.max(max,y);velocity=(velocity-.08)*.98;
            if(y<=0) {y=0;velocity=0;ground=true;}
        }
        check(hops>=5&&max>.5&&max<1.5,"Repeated grounded takeoffs produce real ballistic hops");

        var ascent=new CreatureKinematics.State(0);
        var descent=new CreatureKinematics.State(0);
        ascent.advance("phantom",0,0,0,0,false,false,0,0);
        descent.advance("phantom",0,0,0,0,false,false,0,0);
        for(int tick=1;tick<=30;tick++) {
            ascent.advance("phantom",tick,0,.1,.2,false,false,0,80);
            descent.advance("phantom",tick,0,-.1,.2,false,false,0,-80);
        }
        check(ascent.pitch>20&&descent.pitch<-20,"Phantom renderer pitch matches climb/descent");
        // PhantomRenderer rotates before the inverted-Y model transform. Test the rendered nose,
        // not just a numeric pitch sign (the previous check incorrectly assumed head conventions).
        for(var state:new CreatureKinematics.State[]{ascent,descent}) {
            float pitch=state.pitch*(float)Math.PI/180;
            Vector3f full=new Matrix4f().rotateX(pitch).scale(-1,-1,1)
                    .transformDirection(new Vector3f(0,0,-1));
            Vector3f hybrid=new Matrix4f().scale(-1,-1,1).rotateX(-pitch)
                    .transformDirection(new Vector3f(0,0,-1));
            check(full.y*state.pitch>0,"Rendered nose rises when climbing and lowers when diving");
            check(full.distance(hybrid)<.00001,"Hybrid and full phantom have the same flight direction");
        }
        for(int tick=31;tick<=70;tick++) ascent.advance("phantom",tick,0,0,0,false,true,0,0);
        near(ascent.airborne,0,"Grounded phantom stops flight animation");
        check(Math.abs(ascent.pitch)<.01,"Grounded phantom stops diving");

        ModelPart root=PhantomModel.createBodyLayer().bakeRoot();
        ModelPart left=root.getChild("body").getChild("left_wing_base");
        for(int frame=0;frame<120;frame++) {
            nativePhantom(root,frame*.3F);
            CreatureRestPose.phantom(root,0);
            near(left.zRot,.08F,"Ground rest cannot inherit flapping phase");
            near(left.yRot,-1.1F,"Ground wings stay folded");
            near(root.getChild("body").getChild("tail_base").xRot,-.06F,"Ground tail stops flight oscillation");
        }
        nativePhantom(root,1);
        CreatureRestPose.phantom(root,1);
        near(left.yRot,left.getInitialPose().yRot,"Takeoff unfolds wings after resting");
        near(left.zRot,(float)Math.cos(1)*.28F,"Airborne phantom retains vanilla flap");
        for(float blend:new float[]{0,.25F,.5F,.75F,1}) {
            nativePhantom(root,1);
            CreatureRestPose.phantom(root,blend);
            check(Float.isFinite(left.yRot+left.zRot),"Landing/takeoff pose stays finite");
        }
        ModelPart bee=BeeModel.createBodyLayer().bakeRoot().getChild("bone");
        for(int frame=0;frame<60;frame++) {
            bee.y=19+(float)Math.sin(frame);
            bee.xRot=.2F;
            bee.getChild("left_wing").zRot=(float)Math.cos(frame)*3;
            bee.getChild("right_wing").zRot=-bee.getChild("left_wing").zRot;
            CreatureRestPose.bee(bee,0,frame,1,0);
            near(bee.y,bee.getInitialPose().y,"Grounded bee has no hovering bob");
            near(bee.xRot,0,"Grounded bee rests level");
            near(bee.getChild("left_wing").zRot,0,"Grounded moving bee does not flap");
        }
        System.out.println("Ground/flight regression checks passed: "+checks);
    }
}
