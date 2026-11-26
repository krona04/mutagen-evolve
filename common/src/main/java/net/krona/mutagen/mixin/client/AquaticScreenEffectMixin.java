package net.krona.mutagen.mixin.client;

import net.krona.mutagen.mutation.AquaticAdaptation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ScreenEffectRenderer.class)
public abstract class AquaticScreenEffectMixin {
    @ModifyArg(method = "renderWater", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V", ordinal = 0), index = 3)
    private static float mutagen$gentleWater(float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.getCameraEntity() == minecraft.player
                && AquaticAdaptation.aquatic(minecraft.player)) return alpha * .35F;
        return alpha;
    }
}
