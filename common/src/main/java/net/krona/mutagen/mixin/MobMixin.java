package net.krona.mutagen.mixin;

import net.krona.mutagen.item.SyringeItem;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob origin: from a spawner means a "depleted line", and its sample is worse.
 * The mark is a regular entity tag, so it survives chunk reloads without its own data format.
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
}
