package net.krona.mutagen.strain;

import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Current body dimensions including the hitbox, eye height, reach bonus, and model scale.
 * <p>
 * Interpolate from human at 0% to creature at 100% using the primary strain's progress. Client and server
 * use the same inputs.
 */
public record BodyShape(float width, float height, float eyeHeight, float reach, float renderScale) {
    public static final BodyShape HUMAN = new BodyShape(0.6F, 1.8F, 1.62F, 0.0F, 1.0F);

    /** Base player interaction range. The Pehkui scaler needs it because Pehkui uses a multiplier. */
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
        return transition(strain.profile(), gene.progress(), new BodyShape(strain.targetWidth(),
                strain.targetHeight(), strain.targetEyeHeight(), strain.body().reach(), strain.targetScale()));
    }

    public static BodyShape transition(String strain, float progress, BodyShape target) {
        float t = TransformationProgress.dimensions(strain, progress);
        float width = lerp(HUMAN.width, target.width, t);
        float height = lerp(HUMAN.height, target.height, t);
        float eye = lerp(HUMAN.eyeHeight, target.eyeHeight, t);
        if (TransformationProgress.compact(strain)) {
            // The preparatory crouch already changes the visible height, before fusion begins.
            float prepare = TransformationProgress.compactPrepare(progress);
            float fuse = TransformationProgress.compactFuse(progress);
            float folded = switch (strain) {
                case "phantom" -> 0.65F;
                case "bee" -> 0.79F;
                default -> 0.80F;
            };
            width = lerp(HUMAN.width, target.width, fuse);
            height = lerp(lerp(HUMAN.height, HUMAN.height * folded, prepare), target.height, fuse);
            eye = lerp(lerp(HUMAN.eyeHeight, HUMAN.eyeHeight * folded, prepare), target.eyeHeight, fuse);
        }
        return new BodyShape(
                width, height, eye,
                target.reach * Math.max(0.0F, Math.min(1.0F, progress / 100.0F)),
                lerp(1.0F, target.renderScale, t));
    }

    public net.minecraft.world.entity.EntityDimensions posed(net.minecraft.world.entity.EntityDimensions vanilla,
                                                             float scale, float override) {
        float width = lerp(vanilla.width() * widthFactor() / scale, this.width, override);
        float height = lerp(vanilla.height() * heightFactor() / scale, this.height, override);
        float eye = lerp(vanilla.eyeHeight() * eyeFactor() / scale, eyeHeight, override);
        return vanilla.scale(width / vanilla.width(), height / vanilla.height()).withEyeHeight(eye);
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
