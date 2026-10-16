package net.krona.mutagen.mixin.client;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The walk phase is stored privately, and the full form needs to copy it from the player exactly:
 * otherwise the creature's legs step out of sync and at a different pace at different FPS.
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
