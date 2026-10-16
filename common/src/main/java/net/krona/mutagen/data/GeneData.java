package net.krona.mutagen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Item component: which strain is in the vial and how pure it is.
 * Carried by a sample, a genome and a serum.
 * <p>
 * {@code limit} exists only on a serum: the progress it can bring the host to. It is set by
 * the species research at synthesis time — a weak serum from a poorly researched genome caps at 40%.
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
