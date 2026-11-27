package net.krona.mutagen.mixin.client;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Copy the private walk phase exactly so full-form feet stay in step across frame rates.
 */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
    @Accessor("position")
    float mutagen$getPosition();

    @Accessor("position")
    void mutagen$setPosition(float position);

    @Accessor("speed")
    float mutagen$getSpeed();

    @Accessor("speed")
    void mutagen$setSpeed(float speed);

    @Accessor("speedOld")
    float mutagen$getSpeedOld();

    @Accessor("speedOld")
    void mutagen$setSpeedOld(float speedOld);
}
