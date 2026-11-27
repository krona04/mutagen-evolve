package net.krona.mutagen.client;

import com.mojang.blaze3d.shaders.Uniform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.mixin.client.GameRendererInvoker;
import net.krona.mutagen.mixin.client.PostChainAccessor;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creature vision uses screen effects like vanilla creeper, spider, and enderman spectator views. Enable it
 * with {@code mobVision}. It applies only while the camera is on the player, leaving spectator effects to
 * vanilla.
 * <p>
 * Vision strength follows the transformation. Load the full effect and interpolate each shader parameter
 * from neutral to its configured value. The effect is subtle at stage II and reaches full intensity in full
 * form. Abrupt progress changes from commands or doses blend over about one second.
 */
@Environment(EnvType.CLIENT)
public final class MobVision {
    /** Remember effects that failed to load so they do not generate errors every tick. */
    private static final Set<ResourceLocation> BROKEN = new HashSet<>();
    /** Maximum change in vision strength per tick. A complete transition takes one second. */
    private static final float STEP = 0.05F;
    /** Remove the effect below this threshold to avoid rendering an imperceptible effect. */
    private static final float VISIBLE = 0.01F;

    /**
     * Neutral values that leave the image unchanged. Parameters omitted here retain their effect-file
     * values.
     */
    private static final Map<String, float[]> NEUTRAL = Map.ofEntries(
            Map.entry("RedMatrix", new float[]{1.0F, 0.0F, 0.0F}),
            Map.entry("GreenMatrix", new float[]{0.0F, 1.0F, 0.0F}),
            Map.entry("BlueMatrix", new float[]{0.0F, 0.0F, 1.0F}),
            Map.entry("ColorScale", new float[]{1.0F, 1.0F, 1.0F}),
            Map.entry("Offset", new float[]{0.0F, 0.0F, 0.0F}),
            Map.entry("Saturation", new float[]{1.0F}),
            Map.entry("MosaicSize", new float[]{1.0F}),
            Map.entry("InverseAmount", new float[]{0.0F}),
            Map.entry("Radius", new float[]{0.0F}),
            Map.entry("InScale", new float[]{1.0F, 1.0F}),
            Map.entry("InOffset", new float[]{0.0F, 0.0F}),
            Map.entry("InRotation", new float[]{0.0F}),
            Map.entry("Scissor", new float[]{0.0F, 0.0F, 1.0F, 1.0F}),
            Map.entry("Vignette", new float[]{0.0F, 0.0F, 1.0F, 1.0F}),
            Map.entry("ColorModulate", new float[]{1.0F, 1.0F, 1.0F, 1.0F}));
    /** Number of color levels per channel needed to hide visible banding. */
    private static final float SMOOTH_RESOLUTION = 256.0F;

    /** A shader uniform and its full-strength value from the effect file. */
    private record Knob(Uniform uniform, String name, float[] full) {
    }

    @Nullable
    private static ResourceLocation active;
    @Nullable
    private static PostChain chain;
    private static final List<Knob> KNOBS = new ArrayList<>();
    /** Displayed vision strength approaches the target by at most {@link #STEP} per tick. */
    private static float shown;

    private MobVision() {
    }

    public static void tick(Minecraft minecraft) {
        GameRenderer renderer = minecraft.gameRenderer;
        if (minecraft.player == null || minecraft.getCameraEntity() != minecraft.player) {
            // Let vanilla manage the effect while the camera is on another entity.
            forget();
            shown = 0.0F;
            return;
        }

        Strain.Vision vision = vision(minecraft);
        float wanted = vision == null ? 0.0F : strength(minecraft, vision);
        shown += Mth.clamp(wanted - shown, -STEP, STEP);
        boolean ours = chain != null && renderer.currentEffect() == chain;

        if (shown <= VISIBLE) {
            if (ours) {
                renderer.shutdownEffect();
            }
            forget();
            return;
        }
        ResourceLocation shader = vision != null ? vision.shader() : active;
        if (shader == null) {
            return;
        }
        if (!ours || !shader.equals(active)) {
            if (!load(renderer, shader)) {
                return;
            }
        }
        apply(shown);
    }

    /** Toggle and save the setting when the key is pressed. */
    public static void toggle(Minecraft minecraft) {
        MutagenConfig config = MutagenConfig.get();
        config.mobVision = !config.mobVision;
        MutagenConfig.save();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(config.mobVision
                    ? "mutagen.message.vision_on" : "mutagen.message.vision_off").withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static boolean load(GameRenderer renderer, ResourceLocation shader) {
        forget();
        if (BROKEN.contains(shader)) {
            return false;
        }
        ((GameRendererInvoker) renderer).mutagen$loadEffect(shader);
        PostChain loaded = renderer.currentEffect();
        if (loaded == null || !((GameRendererInvoker) renderer).mutagen$isEffectActive()) {
            // Vanilla logs the error and leaves a closed chain that must be cleared.
            renderer.shutdownEffect();
            BROKEN.add(shader);
            return false;
        }
        active = shader;
        chain = loaded;
        // Capture each full-strength value from the loaded shader, including effect-file overrides and
        // program defaults.
        for (PostPass pass : ((PostChainAccessor) loaded).mutagen$getPasses()) {
            EffectInstance effect = pass.getEffect();
            for (String name : NEUTRAL.keySet()) {
                capture(effect, name);
            }
            capture(effect, "Resolution");
        }
        return true;
    }

    private static void capture(EffectInstance effect, String name) {
        Uniform uniform = effect.getUniform(name);
        if (uniform == null) {
            return;
        }
        FloatBuffer buffer = uniform.getFloatBuffer();
        if (buffer == null) {
            return;
        }
        float[] full = new float[uniform.getCount()];
        for (int i = 0; i < full.length; i++) {
            full[i] = buffer.get(i);
        }
        KNOBS.add(new Knob(uniform, name, full));
    }

    /** Interpolate each parameter from neutral to full strength. */
    private static void apply(float strength) {
        float k = strength * strength * (3.0F - 2.0F * strength);
        for (Knob knob : KNOBS) {
            float[] values = new float[knob.full().length];
            if (knob.name().equals("Resolution")) {
                // Interpolate the color step size to make posterization appear gradually.
                float from = 1.0F / SMOOTH_RESOLUTION;
                float to = 1.0F / Math.max(1.0F, knob.full()[0]);
                values[0] = 1.0F / Mth.lerp(k, from, to);
            } else {
                float[] neutral = NEUTRAL.get(knob.name());
                for (int i = 0; i < values.length; i++) {
                    float base = i < neutral.length ? neutral[i] : knob.full()[i];
                    values[i] = Mth.lerp(k, base, knob.full()[i]);
                }
            }
            knob.uniform().set(values);
        }
    }

    private static void forget() {
        active = null;
        chain = null;
        KNOBS.clear();
    }

    @Nullable
    private static Strain.Vision vision(Minecraft minecraft) {
        if (minecraft.player != null && net.krona.mutagen.mutation.AquaticAdaptation.aquatic(minecraft.player)
                && net.krona.mutagen.mutation.AquaticAdaptation.wet(minecraft.player)) return null;
        if (!MutagenConfig.get().mobVision || minecraft.player == null) {
            return null;
        }
        MutagenData.Gene gene = MutagenPlayer.of(minecraft.player).primary();
        Strain strain = gene == null ? null : Strains.get(gene.strainId());
        return strain == null ? null : strain.vision().orElse(null);
    }

    /** Vision strength at the player's current mutation progress. */
    private static float strength(Minecraft minecraft, Strain.Vision vision) {
        MutagenData.Gene gene = MutagenPlayer.of(minecraft.player).primary();
        if (gene == null) {
            return 0.0F;
        }
        return vision.strength(gene.progress());
    }
}
