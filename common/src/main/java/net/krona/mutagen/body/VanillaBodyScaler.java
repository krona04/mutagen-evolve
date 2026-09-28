package net.krona.mutagen.body;

import net.krona.mutagen.strain.BodyShape;
import net.krona.mutagen.strain.Trait;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Тело без сторонних модов. Модель растёт через атрибут {@code minecraft:generic.scale}, дальность рук —
 * через атрибуты дальности взаимодействия, а ширину, высоту и глаза хитбокса отдельно от масштаба
 * задаёт миксин размеров игрока ({@code PlayerMixin}).
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
