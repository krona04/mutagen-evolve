package net.krona.mutagen.mixin.client;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/**
 * Geometry of a model part. The transformation needs the actual cube sizes so the player's bone can
 * smoothly take the shape of the creature's part, and vanilla keeps them private.
 */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("cubes")
    List<ModelPart.Cube> mutagen$getCubes();

    @Accessor("children")
    Map<String, ModelPart> mutagen$getChildren();
}
