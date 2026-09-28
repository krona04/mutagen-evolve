package net.krona.mutagen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Компонент предмета: какой штамм лежит в пробирке и насколько он чист.
 * Носят образец, геном и сыворотка.
 * <p>
 * {@code limit} есть только у сыворотки: до какого прогресса она способна довести. Его задаёт
 * изученность вида в момент синтеза — слабая сыворотка из плохо изученного генома упирается в 40%.
 */
public record GeneData(ResourceLocation strain, float quality, int limit) {
    public static final int NO_LIMIT = 100;

    public static final Codec<GeneData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("strain").forGetter(GeneData::strain),
            Codec.FLOAT.fieldOf("quality").forGetter(GeneData::quality),
            Codec.intRange(0, 100).optionalFieldOf("limit", NO_LIMIT).forGetter(GeneData::limit)
    ).apply(instance, GeneData::new));

    public static final StreamCodec<ByteBuf, GeneData> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, GeneData::strain,
            ByteBufCodecs.FLOAT, GeneData::quality,
            ByteBufCodecs.VAR_INT, GeneData::limit,
            GeneData::new
    );

    public GeneData(ResourceLocation strain, float quality) {
        this(strain, quality, NO_LIMIT);
    }

    public GeneData withQuality(float newQuality) {
        return new GeneData(strain, Math.max(0.0F, Math.min(100.0F, newQuality)), limit);
    }

    public GeneData withLimit(int newLimit) {
        return new GeneData(strain, quality, newLimit);
    }
}
