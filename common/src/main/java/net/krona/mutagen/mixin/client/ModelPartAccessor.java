package net.krona.mutagen.mixin.client;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/**
 * Expose the actual model cube dimensions so player bones can morph into creature parts.
 */
@Mixin(ModelPart.class)
public interface ModelPartAccessor {
    @Accessor("cubes")
    List<ModelPart.Cube> mutagen$getCubes();

    @Accessor("children")
    Map<String, ModelPart> mutagen$getChildren();
}
