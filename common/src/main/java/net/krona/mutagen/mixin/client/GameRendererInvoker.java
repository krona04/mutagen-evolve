package net.krona.mutagen.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Загрузка эффекта экрана — тот же путь, что у ванили для взгляда крипера в режиме зрителя. */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
    @Invoker("loadEffect")
    void mutagen$loadEffect(ResourceLocation location);

    @Accessor("effectActive")
    boolean mutagen$isEffectActive();
}
