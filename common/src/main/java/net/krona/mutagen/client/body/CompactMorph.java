package net.krona.mutagen.client.body;

import net.krona.mutagen.strain.Infection;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Connected preparatory anatomy followed by a short, coordinated fusion, rather than independent flying cells. */
public final class CompactMorph {
    private CompactMorph() {}
    public static boolean supports(String strain) {
        return strain.equals("bee") || strain.equals("guardian") || strain.equals("phantom");
    }
    public static boolean supports(Strain strain) {
        return strain.id().getNamespace().equals("mutagen") && supports(strain.id().getPath());
    }
    public static float prepare(float progress) { return net.krona.mutagen.strain.TransformationProgress.compactPrepare(progress); }
    public static float fuse(float progress) { return net.krona.mutagen.strain.TransformationProgress.compactFuse(progress); }

    public static Quaternionf alignment(String strain, Strain.Bone bone, Box core) {
        if(!strain.equals("phantom") || bone!=Strain.Bone.RIGHT_ARM && bone!=Strain.Bone.LEFT_ARM) return new Quaternionf();
        if(core.size().x < core.size().y*1.4F) return new Quaternionf();
        return new Quaternionf().rotationZ(Math.abs(core.maxX())>=Math.abs(core.minX())
                ? -(float)Math.PI/2 : (float)Math.PI/2);
    }

    private record Source(Vector3f pivot, Quaternionf rotation, Box box) {}
    public static final class Frame {
        private final Source[] source=new Source[Strain.Bone.values().length];
        private final String strain;
        private final float progress;
        public Frame(PlayerModel<?> model,String strain,float progress) {
            this.strain=strain; this.progress=progress;
            for(Strain.Bone bone:Strain.Bone.values()) {
                ModelPart part=BodyMorph.bone(model,bone);
                source[bone.ordinal()]=new Source(new Vector3f(part.x,part.y,part.z),Poses.rotation(part),Box.of(part));
            }
        }

        public WolfMorph.Pose reshape(Strain.Bone bone,ModelPart part,MorphCuboid.Core core,
                                       Matrix4f rootGoal,Box destination,
                                       MorphCuboid.Core bodyCore,Matrix4f bodyRootGoal) {
            Source own=source[bone.ordinal()], torso=source[Strain.Bone.BODY.ordinal()];
            float prepare=prepare(progress), fuse=fuse(progress);
            Matrix4f bodyGoal=new Matrix4f(bodyRootGoal).mul(bodyCore.relative());
            float lean=strain.equals("phantom")?0.95F:strain.equals("bee")?0.55F:0.15F;
            Quaternionf middleBody=new Quaternionf().rotationX(lean);
            Quaternionf bodyRotation=new Quaternionf(torso.rotation).slerp(middleBody,prepare).slerp(Poses.rotation(bodyGoal),fuse);
            Vector3f bodyPivot=new Vector3f(torso.pivot).lerp(new Vector3f(torso.pivot.x,9,torso.pivot.z),prepare)
                    .lerp(Poses.pivot(bodyGoal),fuse);
            Vector3f relative=new Quaternionf(torso.rotation).conjugate().transform(new Vector3f(own.pivot).sub(torso.pivot));
            Quaternionf local=new Quaternionf(torso.rotation).conjugate().mul(own.rotation);
            Vector3f tucked=new Vector3f(relative);
            Quaternionf tuckedRotation=new Quaternionf(local);
            boolean arm=bone==Strain.Bone.RIGHT_ARM||bone==Strain.Bone.LEFT_ARM;
            boolean leg=bone==Strain.Bone.RIGHT_LEG||bone==Strain.Bone.LEFT_LEG;
            float side=bone==Strain.Bone.RIGHT_ARM||bone==Strain.Bone.RIGHT_LEG?-1:1;
            if(bone==Strain.Bone.HEAD) tucked.set(0,0,-1);
            if(arm) {
                tucked.set(side*4.5F,strain.equals("phantom")?1:3,-1);
                tuckedRotation=strain.equals("phantom")?new Quaternionf().rotationZ(-side*1.15F)
                        :new Quaternionf().rotationXYZ(-0.75F,0,-side*0.20F);
            }
            if(leg) { tucked.set(side*2,10,1.5F); tuckedRotation=new Quaternionf().rotationX(-0.95F); }
            Vector3f pivot=bone==Strain.Bone.BODY?new Vector3f(bodyPivot)
                    :bodyRotation.transform(relative.lerp(tucked,prepare)).add(bodyPivot);
            Quaternionf rotation=bone==Strain.Bone.BODY?new Quaternionf(bodyRotation)
                    :new Quaternionf(bodyRotation).mul(local.slerp(tuckedRotation,prepare));
            Matrix4f goal=new Matrix4f(rootGoal).mul(core.relative());
            Quaternionf align=alignment(strain,bone,core.box());
            Quaternionf finalRotation=Poses.rotation(goal).mul(align);
            if(bone!=Strain.Bone.BODY) pivot.lerp(Poses.pivot(goal),fuse);
            if(bone!=Strain.Bone.BODY) rotation.slerp(finalRotation,fuse);
            Box middle=preparedBox(own.box,bone,strain);
            Box shape=own.box.lerp(middle,prepare).lerp(destination.times(Poses.scale(goal)),fuse);
            Poses.setRotation(part,rotation);
            Box.Fit fit=own.box.fit(shape);
            part.xScale=fit.scale().x; part.yScale=fit.scale().y; part.zScale=fit.scale().z;
            Vector3f placed=BodyMorph.rotate(part,fit.offset()).add(pivot);
            part.setPos(placed.x,placed.y,placed.z);
            return new WolfMorph.Pose(pivot,shape,MorphSequence.window(Infection.overall(progress),0.10F,1));
        }
    }

    private static Box preparedBox(Box original,Strain.Bone bone,String strain) {
        if(bone==Strain.Bone.HEAD) return original;
        if(bone==Strain.Bone.BODY) return original.scaled(1,0.85F,strain.equals("phantom")?1.1F:1.25F);
        if(strain.equals("phantom")&&(bone==Strain.Bone.RIGHT_ARM||bone==Strain.Bone.LEFT_ARM)) return original;
        // Shortening occurs at the distal end: the joint/socket remains at its source position.
        return new Box(original.minX(),original.minY(),original.minZ(),original.maxX(),
                original.minY()+(original.maxY()-original.minY())*0.75F,original.maxZ());
    }

    public static Box envelope(Box original,Box target,float scale,String strain,float progress) {
        return original.lerp(preparedBox(original,Strain.Bone.BODY,strain),prepare(progress))
                .lerp(target.times(scale),fuse(progress));
    }
}
