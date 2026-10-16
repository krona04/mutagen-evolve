package net.krona.mutagen.strain;

import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The host's body dimensions at the moment: hitbox, eye height, reach bonus and model scale.
 * <p>
 * Everything follows the primary strain's progress: at 0% — human, at 100% — the creature. Computed the same way
 * on the server and the client, because both know the strain and progress.
 */
public record BodyShape(float width, float height, float eyeHeight, float reach, float renderScale) {
    public static final BodyShape HUMAN = new BodyShape(0.6F, 1.8F, 1.62F, 0.0F, 1.0F);

    /** The player's base interaction range: needed by the Pehkui layer, where reach is a multiplier. */
    public static final float BASE_BLOCK_REACH = 4.5F;
    public static final float BASE_ENTITY_REACH = 3.0F;

    public static BodyShape of(Player player) {
        MutagenData data = MutagenPlayer.of(player);
        return data == null ? HUMAN : of(data.primary());
    }

    public static BodyShape of(@Nullable MutagenData.Gene gene) {
        if (gene == null) {
            return HUMAN;
        }
        Strain strain = Strains.get(gene.strainId());
        if (strain == null) {
            return HUMAN;
        }
        float t = Math.max(0.0F, Math.min(1.0F, gene.progress() / 100.0F));
        return new BodyShape(
                lerp(HUMAN.width, strain.targetWidth(), t),
                lerp(HUMAN.height, strain.targetHeight(), t),
                lerp(HUMAN.eyeHeight, strain.targetEyeHeight(), t),
                strain.body().reach() * t,
                lerp(1.0F, strain.targetScale(), t));
    }

    public boolean isHuman() {
        return Math.abs(width - HUMAN.width) < 1.0E-4F && Math.abs(height - HUMAN.height) < 1.0E-4F
                && Math.abs(eyeHeight - HUMAN.eyeHeight) < 1.0E-4F && Math.abs(reach) < 1.0E-4F
                && Math.abs(renderScale - 1.0F) < 1.0E-4F;
    }

    public float widthFactor() {
        return width / HUMAN.width;
    }

    public float heightFactor() {
        return height / HUMAN.height;
    }

    public float eyeFactor() {
        return eyeHeight / HUMAN.eyeHeight;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
