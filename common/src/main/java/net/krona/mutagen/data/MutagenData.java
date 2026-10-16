package net.krona.mutagen.data;

import io.netty.buffer.ByteBuf;
import net.krona.mutagen.strain.Stage;
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
 * A player's mutation state. Lives on the server; a copy is sent to the client for rendering.
 * <p>
 * The data format has been versioned since the mod's first version: the world converter in 2.0 will rely on this field.
 */
public final class MutagenData {
    public static final int FORMAT_VERSION = 1;
    public static final String ROOT_TAG = "Mutagen";

    private final List<Gene> genes = new ArrayList<>();
    private boolean dirty = true;

    public List<Gene> genes() {
        return genes;
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

    /** The primary strain — the one with the highest progress. */
    @Nullable
    public Gene primary() {
        Gene best = null;
        for (Gene gene : genes) {
            if (best == null || gene.progress() > best.progress()) {
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
     * Humanity: 100 minus the progress of the strongest strain.
     * In 0.4 this will become a weighted sum of all genes.
     */
    public float humanity() {
        Gene primary = primary();
        return primary == null ? 100.0F : 100.0F - primary.progress();
    }

    /**
     * Flag "the state changed substantially": a gene appeared or left, the fixation changed.
     * Such changes go to the client immediately. Smooth progress movement doesn't set the flag —
     * the network sends it in batches, see {@link net.krona.mutagen.network.MutagenNetwork}.
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
            if (gene != null) {
                genes.add(gene);
            }
        }
        markDirty();
    }

    /** A copy of the genes for sending to the client and comparing with what has already been sent. */
    public List<Gene> snapshot() {
        List<Gene> copy = new ArrayList<>(genes.size());
        for (Gene gene : genes) {
            copy.add(gene.copy());
        }
        return copy;
    }

    /** Client: accept the state received from the server. */
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

    /** Progress of one strain in the player's body. */
    public static final class Gene {
        /** Remainders below this are a computation error, not progress. */
        private static final float EDGE = 0.001F;

        /** Compact network encoding: without NBT string keys. */
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

        /** Strain progress, 0–100. */
        public float progress() {
            return progress;
        }

        public void setProgress(float value) {
            // Absorption goes in steps of 1/30%, and the fractional error left progress at 99.99999:
            // the full form never arrived. Progress that comes close to the edge snaps exactly to the edge.
            float clamped = Math.max(0.0F, Math.min(100.0F, value));
            this.progress = clamped > 100.0F - EDGE ? 100.0F : clamped;
        }

        /** Absorption reserve: injected but not yet processed by the body. */
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

        /** Stabilizer: progress no longer decays on its own. */
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
