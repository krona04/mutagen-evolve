package net.krona.mutagen.mixin.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.krona.mutagen.mutation.AquaticAdaptation;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public abstract class AquaticFogMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private static void mutagen$aquaticFog(Camera camera, FogRenderer.FogMode mode, float distance,
                                          boolean thick, float partial, CallbackInfo ci) {
        if (!(camera.getEntity() instanceof Player player) || !AquaticAdaptation.aquatic(player)
                || player.isSpectator() || player.hasEffect(MobEffects.BLINDNESS)
                || player.hasEffect(MobEffects.DARKNESS)) return;
        if (camera.getFluidInCamera() == FogType.WATER) {
            // Retain underwater depth, with a clear foreground and soft distant haze.
            float end = Math.min(distance, 96);
            RenderSystem.setShaderFogStart(Math.min(10, end * .15F));
            RenderSystem.setShaderFogEnd(end);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
        } else if (camera.getFluidInCamera() == FogType.NONE && mode == FogRenderer.FogMode.FOG_TERRAIN
                && AquaticAdaptation.landVision(player)) {
            float end = Math.min(distance, 32);
            RenderSystem.setShaderFogStart(Math.min(6, end * .2F));
            RenderSystem.setShaderFogEnd(end);
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
        }
    }

    // Preserve vanilla colors: biome water tint underwater and local atmosphere on land.
}
