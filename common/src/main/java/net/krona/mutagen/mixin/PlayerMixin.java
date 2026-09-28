package net.krona.mutagen.mixin;

import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.body.Bodies;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.strain.BodyShape;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
        EntityDimensions base = cir.getReturnValue();
        cir.setReturnValue(base.scale(shape.widthFactor() / scale, shape.heightFactor() / scale)
                .withEyeHeight(base.eyeHeight() * shape.eyeFactor() / scale));
    }
}
