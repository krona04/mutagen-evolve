package net.krona.mutagen.client.body;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krona.mutagen.client.FullFormRenderer;
import net.krona.mutagen.mixin.client.ModelPartAccessor;
import net.krona.mutagen.strain.Infection;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.*;
import java.util.List;

/** One opaque, continuously deforming core surface. Skin/fur replace individual texels through a persistent infection mask. */
public final class ContinuousMorphRenderer {
    private ContinuousMorphRenderer() {}

    public static void render(PlayerModel<?> model, AbstractClientPlayer player, Strain strain, float progress,
                              BodyMorph.Frame frame, PoseStack pose, MultiBufferSource buffers, int light) {
        MorphPlan plan = MorphPlan.of(strain);
        int count = Strain.Bone.values().length;
        ModelPart.Cube[] source = new ModelPart.Cube[count];
        MorphCuboid.Surface[] destination = new MorphCuboid.Surface[count];
        MorphCuboid.Core[] cores = new MorphCuboid.Core[count];
        for (Strain.Bone bone : Strain.Bone.values()) {
            int i=bone.ordinal();
            cores[i]=MorphCuboid.core(ContinuousMorph.target(strain,plan,bone).model());
            source[i]=((ModelPartAccessor)(Object)BodyMorph.bone(model,bone)).mutagen$getCubes().getFirst();
            destination[i]=ContinuousMorph.surface(strain,bone,cores[i]);
        }
        var atlas=InfectionTextures.continuousMorph(player,strain,progress,source,destination);
        var fur=FullFormRenderer.textureOf(strain.entityType(),player.level());
        if (atlas == null || fur == null) return;
        var coreType=RenderType.entityCutoutNoCull(atlas);
        for (Strain.Bone bone : Strain.Bone.values()) {
            int i=bone.ordinal();
            Box shape=frame.shape(bone);
            if(shape == null) continue;
            var corners=destination[i].vertices();
            Box target=destination[i].box();
            float[] pivot=frame.pivot(bone);
            pose.pushPose();
            pose.translate(pivot[0]/16F,pivot[1]/16F,pivot[2]/16F);
            pose.mulPose(Poses.rotation(BodyMorph.bone(model,bone)));
            emit(() -> buffers.getBuffer(coreType),pose,target,shape,corners,i,light,-1);
            for(int layer=0;!ContinuousMorph.merged(strain,bone) && layer<strain.body().overlays().size();layer++) {
                var targetPart=ContinuousMorph.target(strain,plan,bone);
                if(layer>=targetPart.overlays().size() || targetPart.overlays().get(layer).isEmpty()) continue;
                var overlay=strain.body().overlays().get(layer);
                var overlayCore=MorphCuboid.core(targetPart.overlays().get(layer).get());
                var texture=InfectionTextures.part(player,strain,targetPart.index(),
                        Infection.bonePhase(strain,bone,progress),overlay.texture(),layer+1);
                if(texture==null) continue;
                var type=overlay.emissive()?RenderType.eyes(texture):RenderType.entityTranslucent(texture);
                emit(() -> buffers.getBuffer(type),pose,target,shape,MorphCuboid.rotate(MorphCuboid.surface(overlayCore.cube(),overlayCore.box()),
                        CompactMorph.alignment(ContinuousMorph.profile(strain),bone,cores[i].box()).conjugate()).vertices(),-1,light,
                        -1);
            }
            float features=MorphSequence.window(Infection.overall(progress),0.12F,0.88F);
            if(ContinuousMorph.merged(strain,bone)) features=MorphSequence.window(
                    net.krona.mutagen.strain.TransformationProgress.dimensions(ContinuousMorph.profile(strain),progress),0.45F,1);
            if(!ContinuousMorph.merged(strain,bone) || ContinuousMorph.carrier(strain,bone)) {
                Box envelope=bone==Strain.Bone.BODY?carrierShape(strain,model,frame,cores[i],progress):shape;
                Quaternionf alignment=CompactMorph.alignment(ContinuousMorph.profile(strain),bone,cores[i].box());
                Box.Fit fit=cores[i].box().rotated(new Quaternionf(alignment).conjugate()).fit(envelope);
                pose.pushPose();
                pose.translate(fit.offset().x/16F,fit.offset().y/16F,fit.offset().z/16F);
                pose.scale(fit.scale().x,fit.scale().y,fit.scale().z);
                var planned=ContinuousMorph.target(strain,plan,bone);
                extras(planned.model(),new Matrix4f(),cores[i],features,pose,buffers,fur,light,planned.hide(),alignment);
                pose.popPose();
            }
            pose.popPose();
        }
        List<MorphPlan.Target> grown=plan.grown();
        for(int i=0;i<grown.size();i++) {
            var part=grown.get(i);
            if(part==ContinuousMorph.target(strain,plan,Strain.Bone.BODY)) continue;
            var own=frame.grown(i);
            float t=MorphSequence.growth(strain,part,progress);
            if(own==null||t<=0.001F) continue;
            Strain.Bone parent=part.part().bone();
            Matrix4f parentGoal=frame.goal(parent);
            var current=BodyMorph.bone(model,parent);
            float[] pivot=frame.pivot(parent);
            Matrix4f transform=attachment(cores[parent.ordinal()].box(), parent==Strain.Bone.BODY?carrierShape(strain,model,frame,cores[parent.ordinal()],progress):frame.shape(parent),
                    new Vector3f(pivot[0],pivot[1],pivot[2]),Poses.rotation(current),parentGoal,own,t);
            pose.pushPose();
            pose.mulPose(transform);
            // The transform already includes the part's root pose. Draw only its cubes and descendants.
            drawLocal(part.model(),pose,buffers.getBuffer(RenderType.entityCutoutNoCull(fur)),light);
            pose.popPose();
        }
    }

    private static Box carrierShape(Strain strain, PlayerModel<?> model, BodyMorph.Frame frame,
                                    MorphCuboid.Core core, float progress) {
        if(!ContinuousMorph.builtIn(strain)) {
            // Корпус, в который влились другие кости, несёт детали на всём своём объёме, а не на своей доле.
            if(ContinuousMorph.sharers(strain,Strain.Bone.BODY).size()<=1) return frame.shape(Strain.Bone.BODY);
            Matrix4f goal=new Matrix4f(frame.goal(Strain.Bone.BODY)).mul(core.relative());
            float t=ContinuousMorph.customPhase(strain,Strain.Bone.BODY,progress);
            return Box.of(model.body).lerp(core.box().times(Poses.scale(goal)),t);
        }
        if(strain.body().absorb().size()<=1) return frame.shape(Strain.Bone.BODY);
        Matrix4f goal=new Matrix4f(frame.goal(Strain.Bone.BODY)).mul(core.relative());
        if(CompactMorph.supports(strain)) return CompactMorph.envelope(Box.of(model.body),core.box(),Poses.scale(goal),ContinuousMorph.profile(strain),progress);
        float t=net.krona.mutagen.strain.TransformationProgress.dimensions(ContinuousMorph.profile(strain),progress);
        return Box.of(model.body).lerp(core.box().times(Poses.scale(goal)),t);
    }

    public static Matrix4f attachment(Box parentBox, Box shape, Vector3f pivot, Quaternionf rotation,
                                      Matrix4f parentGoal, Matrix4f own, float growth) {
        Box.Fit fit=parentBox.times(Poses.scale(parentGoal)).fit(shape);
        Matrix4f relative=parentGoal.invert(new Matrix4f()).mul(own);
        Matrix4f placed=new Matrix4f().translate(pivot.x/16F,pivot.y/16F,pivot.z/16F)
                .rotate(rotation).translate(fit.offset().x/16F,fit.offset().y/16F,fit.offset().z/16F)
                .scale(fit.scale()).scale(Poses.scale(parentGoal)).mul(relative);
        // Position follows the pelvis, without transmitting its nonuniform scale into the tail's orientation.
        return new Matrix4f().translation(placed.getTranslation(new Vector3f()))
                .rotate(new Quaternionf(rotation).mul(Poses.rotation(parentGoal).conjugate()).mul(Poses.rotation(own)))
                .scale(Poses.scale(own)*growth);
    }

    public static void emit(java.util.function.Supplier<com.mojang.blaze3d.vertex.VertexConsumer> buffer, PoseStack pose,
                             Box target, Box shape, List<MorphCuboid.Vertex> corners, int atlasRow, int light, int colour) {
        // getBuffer for another RenderType may end the previous builder. Acquire only for this draw.
        var vertices=buffer.get();
        for(int v=0;v<corners.size();v++) {
            var corner=corners.get(v);
            var position=MorphCuboid.position(target,shape,corner).div(16);
            int face=v/4, k=v%4;
            float u=atlasRow<0?corner.u():(face+(k==1||k==2?1:0))/6F;
            float texV=atlasRow<0?corner.v():(atlasRow+(k>=2?1:0))/6F;
            vertices.addVertex(pose.last(),position).setColor(colour).setUv(u,texV)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(pose.last(),corner.nx(),corner.ny(),corner.nz());
        }
    }

    private static void extras(ModelPart part, Matrix4f path, MorphCuboid.Core core, float growth,
                               PoseStack pose, MultiBufferSource buffers, net.minecraft.resources.ResourceLocation fur, int light, List<ModelPart> hidden, Quaternionf alignment) {
        if(growth<=0.001F || hidden.contains(part)) return;
        var access=(ModelPartAccessor)(Object)part;
        Matrix4f relative=new Matrix4f().rotate(new Quaternionf(alignment).conjugate()).mul(core.relative().invert()).mul(path);
        for(var cube:access.mutagen$getCubes()) {
            if(cube==core.cube()) continue;
            Vector3f center=MorphCuboid.bounds(cube).center().div(16);
            relative.transformPosition(center);
            Box box=core.box().rotated(new Quaternionf(alignment).conjugate());
            center.mul(16);
            center.set(java.lang.Math.clamp(center.x,box.minX(),box.maxX()),java.lang.Math.clamp(center.y,box.minY(),box.maxY()),
                    java.lang.Math.clamp(center.z,box.minZ(),box.maxZ())).div(16);
            relative.invert(new Matrix4f()).transformPosition(center);
            pose.pushPose();
            pose.mulPose(relative);
            pose.translate(center.x,center.y,center.z);
            pose.scale(growth,growth,growth);
            pose.translate(-center.x,-center.y,-center.z);
            cube.compile(pose.last(),buffers.getBuffer(RenderType.entityCutoutNoCull(fur)),light,OverlayTexture.NO_OVERLAY,-1);
            pose.popPose();
        }
        for(var child:access.mutagen$getChildren().values()) extras(child,Poses.apply(new Matrix4f(path),child),core,growth,pose,buffers,fur,light,hidden,alignment);
    }

    private static void drawLocal(ModelPart root, PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vertices, int light) {
        var access=(ModelPartAccessor)(Object)root;
        for(var cube:access.mutagen$getCubes()) cube.compile(pose.last(),vertices,light,OverlayTexture.NO_OVERLAY,-1);
        for(var child:access.mutagen$getChildren().values()) child.render(pose,vertices,light,OverlayTexture.NO_OVERLAY,-1);
    }
}
