package net.krona.mutagen.mixin.client;

import net.krona.mutagen.client.body.CreatureMotion;
import net.krona.mutagen.client.body.CreatureRestPose;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PhantomModel.class)
public abstract class PhantomModelMixin {
    @Shadow @Final private ModelPart root;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/monster/Phantom;FFFFF)V", at = @At("RETURN"))
    private void mutagen$rest(Phantom phantom, float walk, float stride, float age, float yaw, float pitch,
                              CallbackInfo ci) {
        var state = CreatureMotion.forDummy(phantom);
        if (state == null) return;
        float partial = Mth.clamp(age - phantom.tickCount, 0, 1);
        CreatureRestPose.phantom(root, Mth.lerp(partial, state.airborneOld, state.airborne));
    }
}
