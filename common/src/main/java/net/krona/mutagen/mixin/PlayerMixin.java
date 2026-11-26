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
 * Состояние мутации живёт прямо на игроке и сохраняется вместе с ним.
 * Один и тот же код работает на обеих платформах, потому что цель миксина — ванильный класс.
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
     * Полный иммунитет: удар, сведённый чертами к нулю, не случается вовсе — без вспышки, звука
     * и отброса. Иначе «лёгкие кости» всё равно дёргали бы камеру при каждом падении.
     */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void mutagen$immune(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide && amount > 0.0F
                && Mutation.modifyIncomingDamage(self, source, amount) <= 0.0F) {
            cir.setReturnValue(false);
        }
    }

    /** Слабости и сопротивления носителя. Игрок считает урон сам, мимо версии живой сущности. */
    @ModifyVariable(method = "actuallyHurt", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float mutagen$modifyDamage(float amount, DamageSource source) {
        return Mutation.modifyIncomingDamage((Player) (Object) this, source, amount);
    }

    /** Удар вплотную по носителю или носителем по другому игроку: шипы, яд. */
    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void mutagen$melee(DamageSource source, float amount, CallbackInfo ci) {
        Mutation.onDamaged((Player) (Object) this, source);
    }

    /**
     * Жадность: то, что тело не отдаёт, возвращается в инвентарь вместо того, чтобы упасть на землю.
     * Смерть и рассыпание вещей ({@code dropAround}) не останавливаются, а если места нет —
     * остаток всё-таки падает: жадность не должна съедать предметы.
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
     * Настоящие габариты (0.3): ширина, высота и глаза хитбокса идут к размерам существа отдельно друг
     * от друга, а не одним общим масштабом. Ванильный атрибут масштаба применяется к результату после,
     * поэтому здесь он заранее вынесен за скобки — итог совпадает с формой тела ровно.
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
            // Хитбокс меняет Pehkui, и глаза у него идут строго за высотой хитбокса. У существа они
            // посажены иначе (у крипера ниже), поэтому высота глаз поправляется здесь — Pehkui затем
            // масштабирует уже поправленное значение.
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
