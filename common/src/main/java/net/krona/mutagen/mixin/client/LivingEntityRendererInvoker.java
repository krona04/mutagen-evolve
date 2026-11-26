package net.krona.mutagen.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Собственный масштаб и сдвиг рендера существа. Фантом, например, рисуется ниже своих координат
 * модели, а игрок — чуть меньше их. Превращению нужно знать это заранее, чтобы на 99% тело стояло
 * ровно там, где на 100% встанет настоящее существо.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {
    @Invoker("getBob")
    float mutagen$getBob(LivingEntity entity, float partialTick);

    @Invoker("scale")
    void mutagen$scale(LivingEntity entity, PoseStack poseStack, float partialTick);
}
