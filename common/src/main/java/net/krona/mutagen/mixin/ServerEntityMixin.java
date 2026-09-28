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
 * Игрок появился в поле зрения другого: тот сразу получает его облик.
 * <p>
 * До 0.2.1 состояние рассылалось только при изменении, и зафиксированный игрок в полной форме
 * оставался человеком для всех, кто подошёл к нему позже.
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
