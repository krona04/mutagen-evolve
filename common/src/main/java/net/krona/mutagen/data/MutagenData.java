package net.krona.mutagen.data;

import io.netty.buffer.ByteBuf;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strains;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side player mutation state, mirrored to clients for rendering.
 * <p>
 * Version the data from the first release so the 2.0 world converter can use this field.
 */
public final class MutagenData {
    public static final int FORMAT_VERSION = 1;
    public static final String ROOT_TAG = "Mutagen";

    private final List<Gene> genes = new ArrayList<>();
    private boolean dirty = true;

    public List<Gene> genes() {
        return genes;
    }

    /** Keep strains from disabled datapacks in the save without counting them toward the slot limit. */
    public long activeGeneCount() {
        return genes.stream().filter(gene -> Strains.get(gene.strainId()) != null).count();
    }

    @Nullable
    public Gene gene(ResourceLocation strainId) {
        for (Gene gene : genes) {
            if (gene.strainId().equals(strainId)) {
                return gene;
            }
        }
        return null;
    }

    /** The primary strain is the registered strain with the highest progress. */
    @Nullable
    public Gene primary() {
        Gene best = null;
        for (Gene gene : genes) {
            if (Strains.get(gene.strainId()) != null && (best == null || gene.progress() > best.progress())) {
                best = gene;
            }
        }
        return best;
    }

    public Gene getOrCreate(ResourceLocation strainId) {
        Gene existing = gene(strainId);
        if (existing != null) {
            return existing;
        }
        Gene gene = new Gene(strainId);
        genes.add(gene);
        markDirty();
        return gene;
    }

    public boolean remove(ResourceLocation strainId) {
        boolean removed = genes.removeIf(gene -> gene.strainId().equals(strainId));
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public void clear() {
        if (!genes.isEmpty()) {
            genes.clear();
            markDirty();
        }
    }

    public boolean isEmpty() {
        return genes.isEmpty();
    }

    /**
     * Humanity is 100 minus the highest strain progress. Version 0.4 will use a weighted sum of all genes.
     */
    public float humanity() {
        Gene primary = primary();
        return primary == null ? 100.0F : 100.0F - primary.progress();
    }

    /**
     * Mark changes such as adding, removing, or locking a gene for immediate sync. Gradual progress is sent
     * in increments by {@link net.krona.mutagen.network.MutagenNetwork}.
     */
    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", FORMAT_VERSION);
        ListTag list = new ListTag();
        for (Gene gene : genes) {
            list.add(gene.save());
        }
        tag.put("Genes", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        genes.clear();
        int version = tag.contains("Version", Tag.TAG_INT) ? tag.getInt("Version") : FORMAT_VERSION;
        ListTag list = tag.getList("Genes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Gene gene = Gene.load(list.getCompound(i), version);
            if (gene != null && (gene.progress() > 0.0F || gene.pending() > 0.0F)) {
                genes.add(gene);
            }
        }
        markDirty();
    }

    /** Copy the genes for client sync and comparison with the previous snapshot. */
    public List<Gene> snapshot() {
        List<Gene> copy = new ArrayList<>(genes.size());
        for (Gene gene : genes) {
            copy.add(gene.copy());
        }
        return copy;
    }

    /** Apply mutation state received from the server. */
    public void applySnapshot(List<Gene> snapshot) {
        genes.clear();
        for (Gene gene : snapshot) {
            genes.add(gene.copy());
        }
    }

    public void copyFrom(MutagenData other) {
        genes.clear();
        for (Gene gene : other.genes) {
            genes.add(gene.copy());
        }
        markDirty();
    }

    /** Progress of a single strain in the player. */
    public static final class Gene {
        /** Treat values below this threshold as rounding error. */
        private static final float EDGE = 0.001F;

        /** Compact network representation without NBT string keys. */
        public static final StreamCodec<ByteBuf, Gene> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Gene::strainId,
                ByteBufCodecs.FLOAT, Gene::progress,
                ByteBufCodecs.FLOAT, Gene::pending,
                ByteBufCodecs.BOOL, Gene::locked,
                Gene::of);

        private final ResourceLocation strainId;
        private float progress;
        private float pending;
        private boolean locked;

        public Gene(ResourceLocation strainId) {
            this.strainId = strainId;
        }

        private static Gene of(ResourceLocation strainId, float progress, float pending, boolean locked) {
            Gene gene = new Gene(strainId);
            gene.progress = progress;
            gene.pending = pending;
            gene.locked = locked;
            return gene;
        }

        public ResourceLocation strainId() {
            return strainId;
        }

        /** Strain progress from 0 to 100. */
        public float progress() {
            return progress;
        }

        public void setProgress(float value) {
            // Absorption advances in steps of 1/30%, which can leave progress at 99.99999 and prevent full
            // form. Snap progress near the upper bound to 100.
            float clamped = Math.max(0.0F, Math.min(100.0F, value));
            this.progress = clamped > 100.0F - EDGE ? 100.0F : clamped;
        }

        /** Injected serum that the body has yet to absorb. */
        public float pending() {
            return pending;
        }

        public void setPending(float value) {
            float clamped = Math.max(0.0F, Math.min(100.0F, value));
            this.pending = clamped < EDGE ? 0.0F : clamped;
        }

        public void addPending(float value) {
            setPending(pending + value);
        }

        /** A stabilizer prevents progress from decaying on its own. */
        public boolean locked() {
            return locked;
        }

        public void setLocked(boolean locked) {
            this.locked = locked;
        }

        public Stage stage() {
            return Stage.of(progress);
        }

        public Gene copy() {
            Gene copy = new Gene(strainId);
            copy.progress = progress;
            copy.pending = pending;
            copy.locked = locked;
            return copy;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", strainId.toString());
            tag.putFloat("Progress", progress);
            tag.putFloat("Pending", pending);
            tag.putBoolean("Locked", locked);
            return tag;
        }

        @Nullable
        public static Gene load(CompoundTag tag, int version) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("Id"));
            if (id == null) {
                return null;
            }
            Gene gene = new Gene(id);
            gene.progress = tag.getFloat("Progress");
            gene.pending = tag.getFloat("Pending");
            gene.locked = tag.getBoolean("Locked");
            return gene;
        }
    }
}
