package net.krona.mutagen.mixin.client;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;

/** Copy the player's sampled fluid state without ticking or pushing the render-only creature. */
@Mixin(Entity.class)
public interface CreatureFluidAccessor {
    @Accessor("wasTouchingWater") void mutagen$setTouchingWater(boolean value);
    @Accessor("wasEyeInWater") void mutagen$setEyeInWater(boolean value);
    @Accessor("fluidHeight") Object2DoubleMap<TagKey<Fluid>> mutagen$getFluidHeight();
    @Accessor("fluidOnEyes") Set<TagKey<Fluid>> mutagen$getFluidOnEyes();
}
