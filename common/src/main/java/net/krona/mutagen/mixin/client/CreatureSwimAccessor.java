package net.krona.mutagen.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface CreatureSwimAccessor {
    @Accessor("swimAmount") void mutagen$setSwim(float value);
    @Accessor("swimAmountO") void mutagen$setSwimOld(float value);
}
