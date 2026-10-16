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
 * The mutation state lives right on the player and is saved together with them.
 * The same code works on both platforms, because the mixin's target is a vanilla class.
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
     * True dimensions (0.3): hitbox width, height and eyes move towards the creature's dimensions independently
     * of each other, not by one shared scale. The vanilla scale attribute is applied to the result afterwards,
     * so here it is factored out in advance — the result matches the body shape exactly.
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
            // Pehkui changes the hitbox, and its eyes strictly follow the hitbox height. The creature's eyes
            // are placed differently (lower for a creeper), so eye height is corrected here — Pehkui then
            // scales the already corrected value.
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
