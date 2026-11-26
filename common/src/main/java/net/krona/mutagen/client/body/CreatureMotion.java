package net.krona.mutagen.client.body;

import net.krona.mutagen.mixin.client.CreatureFluidAccessor;
import net.krona.mutagen.mixin.client.GuardianMotionAccessor;
import net.krona.mutagen.mixin.client.CreatureSwimAccessor;
import net.krona.mutagen.mixin.client.WalkAnimationStateAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import java.util.Map;
import java.util.WeakHashMap;

/** Prepare vanilla mob models from actual movement, without running mob AI in the renderer. */
public final class CreatureMotion {
    private static final Map<Player, CreatureKinematics.State> STATES = new WeakHashMap<>();
    private static final Map<LivingEntity, CreatureKinematics.State> PROXIES = new WeakHashMap<>();
    private CreatureMotion() {}

    public static CreatureKinematics.State forDummy(LivingEntity dummy) { return PROXIES.get(dummy); }

    public static void apply(Player player, LivingEntity dummy) {
        String strain = BuiltInRegistries.ENTITY_TYPE.getKey(dummy.getType()).getPath();
        CreatureKinematics.State state = STATES.computeIfAbsent(player, key -> new CreatureKinematics.State(player.yBodyRot));
        state.advance(strain, player.tickCount, player.getX() - player.xo, player.getY() - player.yo,
                player.getZ() - player.zo, player.isInWater(), player.onGround(), player.getYRot(), player.getXRot());
        PROXIES.put(dummy, state);
        dummy.setDeltaMovement(player.getDeltaMovement());
        dummy.setOnGround(player.onGround());
        dummy.setSpeed(player.getSpeed());
        dummy.fallDistance = player.fallDistance;
        dummy.setHealth(dummy.getMaxHealth() * Mth.clamp(player.getHealth() / player.getMaxHealth(), 0.001F, 1));
        dummy.swinging = player.swinging;
        dummy.swingingArm = player.swingingArm;
        dummy.swingTime = player.swingTime;
        dummy.attackAnim = player.attackAnim;
        dummy.oAttackAnim = player.oAttackAnim;
        CreatureSwimAccessor swim = (CreatureSwimAccessor) dummy;
        swim.mutagen$setSwim(CreatureKinematics.nonHuman(strain) ? 0 : player.getSwimAmount(1));
        swim.mutagen$setSwimOld(CreatureKinematics.nonHuman(strain) ? 0 : player.getSwimAmount(0));

        CreatureFluidAccessor fluid = (CreatureFluidAccessor) dummy;
        CreatureFluidAccessor source = (CreatureFluidAccessor) player;
        fluid.mutagen$setTouchingWater(player.isInWater());
        fluid.mutagen$setEyeInWater(player.isUnderWater());
        fluid.mutagen$getFluidHeight().clear();
        fluid.mutagen$getFluidHeight().putAll(source.mutagen$getFluidHeight());
        fluid.mutagen$getFluidOnEyes().clear();
        fluid.mutagen$getFluidOnEyes().addAll(source.mutagen$getFluidOnEyes());

        if (CreatureKinematics.nonHuman(strain)) {
            dummy.setPose(Pose.STANDING);
            dummy.setShiftKeyDown(false);
            dummy.setSwimming(false);
            dummy.yBodyRot = state.bodyYaw;
            dummy.yBodyRotO = state.bodyYawOld;
            dummy.yHeadRot = state.bodyYaw + Mth.clamp(Mth.wrapDegrees(player.yHeadRot - state.bodyYaw), -75, 75);
            dummy.yHeadRotO = state.bodyYawOld + Mth.clamp(Mth.wrapDegrees(player.yHeadRotO - state.bodyYawOld), -75, 75);
            // A headless swimmer/bird tilts with its trajectory, rather than every camera movement.
            if (strain.equals("guardian") || strain.equals("phantom") || strain.equals("bee")) {
                dummy.setXRot(state.pitch);
                dummy.xRotO = state.pitchOld;
            }
            WalkAnimationStateAccessor walk = (WalkAnimationStateAccessor)(Object)dummy.walkAnimation;
            walk.mutagen$setPosition(state.walkPosition);
            walk.mutagen$setSpeed(state.walkSpeed);
            walk.mutagen$setSpeedOld(state.walkSpeedOld);
        }
        if (dummy instanceof Guardian guardian) {
            GuardianMotionAccessor animation = (GuardianMotionAccessor) guardian;
            animation.mutagen$setTail(state.tail);
            animation.mutagen$setTailOld(state.tailOld);
            animation.mutagen$setSpikes(state.spikes);
            animation.mutagen$setSpikesOld(state.spikesOld);
            animation.mutagen$setMoving(player.isInWater() && player.getDeltaMovement().lengthSqr() > 0.0001);
        }
    }
}
