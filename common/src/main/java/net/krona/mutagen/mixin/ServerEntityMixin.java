package net.krona.mutagen.mixin;

import net.krona.mutagen.network.MutagenNetwork;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player came into another's view: that one immediately receives their appearance.
 * <p>
 * Before 0.2.1 the state was broadcast only on change, and a fixed player in full form
 * stayed human for everyone who approached them later.
 */
@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "addPairing", at = @At("RETURN"))
    private void mutagen$sendMutation(ServerPlayer watcher, CallbackInfo ci) {
        if (entity instanceof ServerPlayer target) {
            MutagenNetwork.sendTo(watcher, target);
        }
    }
}
