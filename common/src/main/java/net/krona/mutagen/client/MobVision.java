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
 * Зрение существа: эффект экрана, как у ванили, когда в режиме зрителя смотришь глазами крипера,
 * паука или эндермена. Включается настройкой {@code mobVision} (по умолчанию выключена) и работает,
 * только пока камера — сам игрок: чужие эффекты (режим зрителя) не трогаются.
 * <p>
 * Зрение меняется вместе с телом, а не включается рывком. Эффект загружается целиком, но каждый
 * параметр его шейдеров стоит между «ничего не меняю» и своим полным значением — по тому, как далеко
 * зашло превращение: на II стадии мир едва тронут цветом, к полной форме он виден глазами существа.
 * Резкий скачок прогресса (доза, команда) растягивается примерно на секунду.
 */
@Environment(EnvType.CLIENT)
public final class MobVision {
    /** Эффекты, которые не загрузились: второй раз их не пробуем, чтобы не сыпать ошибками каждый тик. */
    private static final Set<ResourceLocation> BROKEN = new HashSet<>();
    /** Насколько сила зрения может измениться за тик: полный путь — за секунду. */
    private static final float STEP = 0.05F;
    /** Ниже этой силы эффект снимается совсем: незаметное зрение не стоит кадров. */
    private static final float VISIBLE = 0.01F;

    /**
     * Значения, при которых проход шейдера не меняет картинку. Параметры, которых здесь нет, остаются
     * как в файле эффекта.
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
    /** Сколько уровней цвета в канале — столько, что ступенек не видно. */
    private static final float SMOOTH_RESOLUTION = 256.0F;

    /** Параметр шейдера и его полное значение из файла эффекта. */
    private record Knob(Uniform uniform, String name, float[] full) {
    }

    @Nullable
    private static ResourceLocation active;
    @Nullable
    private static PostChain chain;
    private static final List<Knob> KNOBS = new ArrayList<>();
    /** Сила зрения, которая сейчас на экране: догоняет нужную не быстрее {@link #STEP} за тик. */
    private static float shown;

    private MobVision() {
    }

    public static void tick(Minecraft minecraft) {
        GameRenderer renderer = minecraft.gameRenderer;
        if (minecraft.player == null || minecraft.getCameraEntity() != minecraft.player) {
            // Камера чужая — эффектом распоряжается ваниль.
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

    /** Переключение клавишей: настройка меняется и сохраняется. */
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
            // Ваниль сама пишет причину в лог и оставляет закрытую цепочку — её нужно убрать.
            renderer.shutdownEffect();
            BROKEN.add(shader);
            return false;
        }
        active = shader;
        chain = loaded;
        // Полные значения берутся у самих шейдеров сразу после загрузки: в них уже и то, что задал файл
        // эффекта, и то, что программа берёт по умолчанию.
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

    /** Каждый параметр — между нейтральным и полным значением. */
    private static void apply(float strength) {
        float k = strength * strength * (3.0F - 2.0F * strength);
        for (Knob knob : KNOBS) {
            float[] values = new float[knob.full().length];
            if (knob.name().equals("Resolution")) {
                // Уровни цвета: плавно по шагу ступеньки, а не по их числу — иначе постеризация
                // выскакивала бы вся разом в самом конце.
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

    /** Нужная сила зрения: 0 на стадии, с которой оно начинается, 1 в полной форме. */
    private static float strength(Minecraft minecraft, Strain.Vision vision) {
        MutagenData.Gene gene = MutagenPlayer.of(minecraft.player).primary();
        if (gene == null) {
            return 0.0F;
        }
        float from = vision.minStage().threshold();
        return Mth.clamp((gene.progress() - from) / Math.max(1.0F, 100.0F - from), 0.0F, 1.0F);
    }
}
