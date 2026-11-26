package net.krona.mutagen.mixin.client;

import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Guardian.class)
public interface GuardianMotionAccessor {
    @Accessor("clientSideTailAnimation") void mutagen$setTail(float value);
    @Accessor("clientSideTailAnimationO") void mutagen$setTailOld(float value);
    @Accessor("clientSideSpikesAnimation") void mutagen$setSpikes(float value);
    @Accessor("clientSideSpikesAnimationO") void mutagen$setSpikesOld(float value);
    @Invoker("setMoving") void mutagen$setMoving(boolean moving);
}
