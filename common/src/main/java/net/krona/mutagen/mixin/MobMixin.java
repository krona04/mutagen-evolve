package net.krona.mutagen.mixin;

import net.krona.mutagen.item.SyringeItem;
import net.krona.mutagen.mutation.Mutation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Modify mob origin tracking and targeting.
 * <p>
 * Tag spawner mobs so their samples have lower quality. A vanilla entity tag survives chunk reloads.
 * <p>
 * Mobs that recognize the player as their own species skip them when selecting a target.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void mutagen$markSpawnerOrigin(ServerLevelAccessor level, DifficultyInstance difficulty,
                                                MobSpawnType spawnType, @Nullable SpawnGroupData groupData,
                                                CallbackInfoReturnable<SpawnGroupData> cir) {
        if (MobSpawnType.isSpawner(spawnType)) {
            ((Mob) (Object) this).addTag(SyringeItem.SPAWNER_TAG);
        }
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void mutagen$ignoreKin(@Nullable LivingEntity target, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (target instanceof Player player && !player.level().isClientSide
                && Mutation.pacified(player, self.getType())) {
            ci.cancel();
        }
    }
}
