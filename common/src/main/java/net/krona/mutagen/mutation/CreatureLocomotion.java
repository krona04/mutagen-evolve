package net.krona.mutagen.mutation;

import net.krona.mutagen.data.MutagenPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Physical land movement: the player, camera and collision box hop together. */
public final class CreatureLocomotion {
    private CreatureLocomotion() {}

    public static float guardianHop(float progress, boolean ground, boolean water, boolean crouching,
                                    boolean flying, double inputSquared) {
        if (progress < 71 || !ground || water || crouching || flying || inputSquared < 0.0001) return 0;
        float weight = Math.max(0, Math.min(1, (progress - 71) / 29));
        return .22F + .20F * weight;
    }

    public static void beforeTravel(Player player, Vec3 input) {
        if (!player.isAlive() || player.isSpectator() || player.isCreative() || player.isPassenger()
                || player.onClimbable() || player.isInLava()) return;
        var data = MutagenPlayer.of(player);
        var gene = data == null ? null : data.primary();
        if (gene == null || !gene.strainId().toString().equals("mutagen:guardian")) return;
        float hop = guardianHop(gene.progress(), player.onGround(), player.isInWaterOrBubble(),
                player.isShiftKeyDown(), player.getAbilities().flying, input.horizontalDistanceSqr());
        if (hop == 0) return;
        Vec3 velocity = player.getDeltaMovement();
        player.setDeltaMovement(velocity.x, Math.max(velocity.y, hop), velocity.z);
        player.setOnGround(false);
        player.hasImpulse = true;
    }

    public static boolean swim(Player player, Vec3 input) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || player.isPassenger()
                || player.getAbilities().flying || !player.isInWater()
                || !player.isControlledByLocalInstance() || !AquaticAdaptation.aquatic(player)) return false;
        var gene = MutagenPlayer.of(player).primary();
        float multiplier = (float)(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) / .1);
        float acceleration = AquaticAdaptation.swimAcceleration(gene.progress(), multiplier);
        if (player.isSwimming()) {
            double pitch = Math.toRadians(player.getXRot());
            input = new Vec3(input.x, input.y - input.z * Math.sin(pitch), input.z * Math.cos(pitch));
        }
        player.moveRelative(acceleration, input);
        player.move(net.minecraft.world.entity.MoverType.SELF, player.getDeltaMovement());
        // Neutral buoyancy: space/sneak, currents and look-directed swimming control depth.
        player.setDeltaMovement(player.getDeltaMovement().scale(.85));
        player.calculateEntityAnimation(false);
        return true;
    }
}
