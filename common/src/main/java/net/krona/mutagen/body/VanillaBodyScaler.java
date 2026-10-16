package net.krona.mutagen.body;

import net.krona.mutagen.strain.BodyShape;
import net.krona.mutagen.strain.Trait;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Body without third-party mods. The model grows via the {@code minecraft:generic.scale} attribute, reach
 * via the interaction range attributes, and the hitbox width, height and eyes are set separately from scale
 * by the player dimensions mixin ({@code PlayerMixin}).
 */
public final class VanillaBodyScaler implements BodyScaler {
    @Override
    public String name() {
        return "vanilla";
    }

    @Override
    public void apply(ServerPlayer player, BodyShape shape, Trait.AttributeSink sink) {
        if (Math.abs(shape.renderScale() - 1.0F) > 1.0E-4F) {
            sink.add(Attributes.SCALE, shape.renderScale() - 1.0F, AttributeModifier.Operation.ADD_VALUE);
        }
        if (Math.abs(shape.reach()) > 1.0E-4F) {
            sink.add(Attributes.BLOCK_INTERACTION_RANGE, shape.reach(), AttributeModifier.Operation.ADD_VALUE);
            sink.add(Attributes.ENTITY_INTERACTION_RANGE, shape.reach(), AttributeModifier.Operation.ADD_VALUE);
        }
    }

    @Override
    public boolean ownsDimensions() {
        return true;
    }
}
