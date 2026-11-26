package net.krona.mutagen.mixin;

import net.krona.mutagen.mutation.Mutation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Паутина не держит носителя паучьего штамма — как не держит самого паука. Ягодные кусты и
 * рыхлый снег держат по-прежнему: у них своя цена.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void mutagen$walkThroughWebs(BlockState state, Vec3 motionMultiplier, CallbackInfo ci) {
        if (state.is(Blocks.COBWEB) && (Object) this instanceof Player player && Mutation.walksThroughWebs(player)) {
            ci.cancel();
        }
    }
}
