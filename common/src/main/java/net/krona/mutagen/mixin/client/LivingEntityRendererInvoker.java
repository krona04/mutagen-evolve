package net.krona.mutagen.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Expose the creature renderer's scale and offset. The morph needs these to line up the player at 99% with
 * the creature at 100%.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {
    @Invoker("getBob")
    float mutagen$getBob(LivingEntity entity, float partialTick);

    @Invoker("scale")
    void mutagen$scale(LivingEntity entity, PoseStack poseStack, float partialTick);
}
