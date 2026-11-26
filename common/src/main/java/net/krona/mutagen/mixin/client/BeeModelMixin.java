package net.krona.mutagen.mixin.client;

import net.krona.mutagen.client.body.CreatureMotion;
import net.krona.mutagen.client.body.CreatureRestPose;
import net.minecraft.client.model.BeeModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeeModel.class)
public abstract class BeeModelMixin {
    @Shadow @Final private ModelPart bone;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/animal/Bee;FFFFF)V", at = @At("RETURN"))
    private void mutagen$rest(Bee bee, float walk, float stride, float age, float yaw, float pitch, CallbackInfo ci) {
        var state = CreatureMotion.forDummy(bee);
        if (state == null) return;
        float partial = Mth.clamp(age - bee.tickCount, 0, 1);
        CreatureRestPose.bee(bone, Mth.lerp(partial, state.airborneOld, state.airborne), walk, stride,
                Mth.lerp(partial, state.pitchOld, state.pitch));
    }
}
