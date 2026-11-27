package net.krona.mutagen.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.krona.mutagen.mutation.AquaticAdaptation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ScreenEffectRenderer.class)
public abstract class AquaticScreenEffectMixin {
    @Shadow
    @Final
    private static ResourceLocation UNDERWATER_LOCATION;

    // NeoForge moves the shader color call from renderWater into renderFluid, so each hook is optional.
    @ModifyArg(method = "renderWater", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V", ordinal = 0, remap = false),
            index = 3, require = 0, expect = 0)
    private static float mutagen$gentleWater(float alpha) {
        return mutagen$waterAlpha(alpha, Minecraft.getInstance());
    }

    @ModifyConstant(method = "renderFluid", constant = @Constant(floatValue = 0.1F, ordinal = 0),
            remap = false, require = 0, expect = 0)
    private static float mutagen$gentleFluid(float alpha, Minecraft minecraft, PoseStack poseStack,
                                             ResourceLocation texture) {
        return UNDERWATER_LOCATION.equals(texture) ? mutagen$waterAlpha(alpha, minecraft) : alpha;
    }

    private static float mutagen$waterAlpha(float alpha, Minecraft minecraft) {
        if (minecraft.player != null && minecraft.getCameraEntity() == minecraft.player
                && AquaticAdaptation.aquatic(minecraft.player)) return alpha * .35F;
        return alpha;
    }
}
