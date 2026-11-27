package net.krona.mutagen.mixin;

import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.body.Bodies;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.strain.BodyShape;
import net.krona.mutagen.mutation.Mutation;
import net.krona.mutagen.mutation.Restrictions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Store and save mutation state on the vanilla player entity on both loaders.
 */
@Mixin(Player.class)
public abstract class PlayerMixin implements MutagenPlayer {
    @Unique
    private final MutagenData mutagen$data = new MutagenData();

    @Override
    public MutagenData mutagen$getData() {
        return mutagen$data;
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void mutagen$creatureTravel(net.minecraft.world.phys.Vec3 input, CallbackInfo ci) {
        net.krona.mutagen.mutation.CreatureLocomotion.beforeTravel((Player)(Object)this, input);
        if (net.krona.mutagen.mutation.CreatureLocomotion.swim((Player)(Object)this, input)) ci.cancel();
    }

    /**
     * Cancel damage reduced to zero before hit effects, sound, and knockback. Otherwise falling with light
     * bones would still shake the camera.
     */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void mutagen$immune(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide && amount > 0.0F
                && Mutation.modifyIncomingDamage(self, source, amount) <= 0.0F) {
            cir.setReturnValue(false);
        }
    }

    /** Apply the player's weaknesses and resistances here because Player handles damage separately. */
    @ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float mutagen$modifyDamage(float amount, DamageSource source) {
        return Mutation.modifyIncomingDamage((Player) (Object) this, source, amount);
    }

    /** Apply melee contact traits, such as thorns and venom, between players. */
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void mutagen$melee(DamageSource source, float amount, CallbackInfo ci) {
        Mutation.onDamaged((Player) (Object) this, source);
    }

    /**
     * Return items blocked by hoarding to the inventory. Death and {@code dropAround} still drop items, as
     * does overflow when the inventory is full.
     */
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void mutagen$hoard(ItemStack stack, boolean dropAround, boolean includeThrowerName,
                               CallbackInfoReturnable<ItemEntity> cir) {
        Player self = (Player) (Object) this;
        if (self.level().isClientSide || dropAround || stack.isEmpty() || !self.isAlive()
                || !Mutation.hoards(self, stack)) {
            return;
        }
        ItemStack kept = stack.copy();
        self.getInventory().add(kept);
        if (kept.isEmpty()) {
            Restrictions.tell(self, "mutagen.restriction.hoard");
            cir.setReturnValue(null);
            return;
        }
        stack.setCount(kept.getCount());
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void mutagen$save(CompoundTag tag, CallbackInfo ci) {
        tag.put(MutagenData.ROOT_TAG, mutagen$data.save());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void mutagen$load(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(MutagenData.ROOT_TAG, Tag.TAG_COMPOUND)) {
            mutagen$data.load(tag.getCompound(MutagenData.ROOT_TAG));
        }
    }

    /**
     * Interpolate hitbox width, height, and eye height separately (0.3). Account for the vanilla scale
     * attribute, which is applied after this method.
     */
    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void mutagen$bodyDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (mutagen$data == null || mutagen$data.isEmpty()) {
            return;
        }
        BodyShape shape = BodyShape.of(mutagen$data.primary());
        if (shape.isHuman()) {
            return;
        }
        EntityDimensions current = cir.getReturnValue();
        if (!Bodies.ownsDimensions()) {
            // Pehkui scales eye height with the hitbox. Set the creature-specific eye position here before
            // Pehkui applies its scale.
            cir.setReturnValue(current.withEyeHeight(current.eyeHeight() * shape.eyeFactor() / shape.heightFactor()));
            return;
        }
        Player self = (Player) (Object) this;
        float scale = self.getScale();
        if (scale <= 0.0F) {
            return;
        }
        var gene = mutagen$data.primary();
        float override = gene == null || !gene.strainId().getNamespace().equals("mutagen") ? 0.0F
                : net.krona.mutagen.strain.TransformationProgress.poseOverride(gene.strainId().getPath(), gene.progress());
        cir.setReturnValue(shape.posed(cir.getReturnValue(), scale, override));
    }
}
