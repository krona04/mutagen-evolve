package net.krona.mutagen.client.body;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.client.FullFormRenderer;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.strain.Infection;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Текстуры заражения, которые мод рисует сам для каждого носителя.
 * <p>
 * <b>Кожа.</b> Поверх скина игрока ложится текстура в его же раскладке: заражённые пиксели непрозрачны,
 * остальные пусты. Каждая кость заражается в своё окно прогресса ({@link Infection}), пятна растут по
 * личному рисунку игрока ({@link InfectionMask}), а на границе пятна кожа темнее — воспалённый край.
 * Цвет пятна берётся из текстуры самого существа, если её раскладка совпадает с человеческой, иначе —
 * из цвета штамма; пустоты в текстуре существа становятся тёмными полостями. Пятна ложатся только на
 * основной слой: слой одежды с превращающейся кости спадает.
 * <p>
 * <b>Части существа.</b> Текстура существа проявляется так же — островками, начиная с малого, —
 * поэтому часть прорастает сквозь кожу, а не проступает прозрачной плёнкой.
 * <p>
 * Текстуры пересобираются только когда прогресс сдвинулся на полпроцента, и выгружаются, если
 * носитель давно не рисовался.
 */
@Environment(EnvType.CLIENT)
public final class InfectionTextures {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Сколько скина заражение может занять до полной формы. */
    private static final float SKIN_CAP = 0.86F;
    /** Шкура существа появляется сразу редкими островками и к концу превращения кости закрывает её всю. */
    private static final float PART_BASE = 0.04F;
    /** Ширина воспалённого края пятна в единицах маски. */
    private static final float RIM = 0.07F;
    private static final long UNUSED_MS = 30_000L;
    /** Полость в теле существа — например, между рёбрами скелета. */
    private static final int CAVITY = 0x1C1A1A;

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final Map<ResourceLocation, Optional<NativeImage>> SOURCES = new HashMap<>();
    private static long lastCleanup;

    /** Раскладка основного слоя скина игрока 64×64 и где та же часть лежит в текстуре существа. */
    private record Region(Strain.Bone bone, int x0, int y0, int x1, int y1, int sampleDx, int sampleDy) {
    }

    private static final List<Region> REGIONS = List.of(
            new Region(Strain.Bone.HEAD, 0, 0, 32, 16, 0, 0),
            new Region(Strain.Bone.BODY, 16, 16, 40, 32, 0, 0),
            new Region(Strain.Bone.RIGHT_ARM, 40, 16, 56, 32, 0, 0),
            new Region(Strain.Bone.RIGHT_LEG, 0, 16, 16, 32, 0, 0),
            // Левые конечности в текстурах мобов старой раскладки нарисованы на месте правых.
            new Region(Strain.Bone.LEFT_ARM, 32, 48, 48, 64, 8, -32),
            new Region(Strain.Bone.LEFT_LEG, 16, 48, 32, 64, -16, -32));

    private static final class Entry {
        final ResourceLocation id;
        final DynamicTexture texture;
        int key = Integer.MIN_VALUE;
        long lastUsed;

        Entry(ResourceLocation id, int width, int height) {
            this.id = id;
            this.texture = new DynamicTexture(width, height, true);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }

    private InfectionTextures() {
    }

    /** Заражённая кожа игрока или null, если заражение ещё не началось. */
    @Nullable
    public static ResourceLocation skin(AbstractClientPlayer player, Strain strain, MutagenData.Gene gene) {
        if (gene.progress() < Infection.START) {
            return null;
        }
        String name = player.getUUID() + "/skin";
        Entry entry = entry(name, 64, 64);
        int key = ((strain.id().hashCode() * 31) + Math.round(gene.progress() * 2.0F)) * 2 + (slim(player) ? 1 : 0);
        if (entry.key != key) {
            entry.key = key;
            paintSkin(entry.texture.getPixels(), player, strain, gene.progress());
            entry.texture.upload();
        }
        return entry.id;
    }

    /**
     * Шкура части существа на превращающейся кости: островками по фазе кости, целиком — к её концу.
     * Null, если превращение кости ещё не началось.
     */
    @Nullable
    public static ResourceLocation part(AbstractClientPlayer player, Strain strain, int index, float phase) {
        if (phase <= 0.0F) {
            return null;
        }
        ResourceLocation source = FullFormRenderer.textureOf(strain.entityType(), player.level());
        NativeImage mob = source == null ? null : source(source);
        if (mob == null) {
            return null;
        }
        String name = player.getUUID() + "/part_" + index + "_" + mob.getWidth() + "x" + mob.getHeight();
        Entry entry = entry(name, mob.getWidth(), mob.getHeight());
        int key = (strain.id().hashCode() * 31 + index) * 31 + Math.round(phase * 200.0F);
        if (entry.key != key) {
            entry.key = key;
            float threshold = PART_BASE + (1.0F - PART_BASE) * phase;
            paintPart(entry.texture.getPixels(), mob, mask(player, 17 + index), threshold, strain.color());
            entry.texture.upload();
        }
        return entry.id;
    }

    private static boolean slim(AbstractClientPlayer player) {
        return player.getSkin().model() == PlayerSkin.Model.SLIM;
    }

    private static boolean isArm(Region region) {
        return region.bone() == Strain.Bone.RIGHT_ARM || region.bone() == Strain.Bone.LEFT_ARM;
    }

    /**
     * Столбец широкой (4 пикселя) развёртки руки для столбца узкой (3 пикселя). Боковые грани у обеих
     * по 4 пикселя, а передняя, задняя, верх и низ у узкой руки на пиксель уже — они растягиваются.
     */
    private static int wideArmX(int x, int y, Region region) {
        int lx = x - region.x0();
        int ly = y - region.y0();
        int wide;
        if (ly < 4) {
            if (lx >= 4 && lx < 7) {
                wide = 4 + (lx - 4) * 4 / 3;          // верх
            } else if (lx >= 7 && lx < 10) {
                wide = 8 + (lx - 7) * 4 / 3;          // низ
            } else {
                wide = lx;
            }
        } else if (lx < 4) {
            wide = lx;                               // внешний бок
        } else if (lx < 7) {
            wide = 4 + (lx - 4) * 4 / 3;              // перед
        } else if (lx < 11) {
            wide = lx + 1;                           // внутренний бок
        } else if (lx < 14) {
            wide = 12 + (lx - 11) * 4 / 3;            // спина
        } else {
            wide = lx;
        }
        return region.x0() + wide;
    }

    private static void paintSkin(NativeImage image, AbstractClientPlayer player, Strain strain, float progress) {
        boolean slim = slim(player);
        image.fillRect(0, 0, image.getWidth(), image.getHeight(), 0);
        InfectionMask mask = mask(player, 0);
        NativeImage mob = null;
        if (strain.body().skin() == Strain.SkinSource.TEXTURE) {
            ResourceLocation source = FullFormRenderer.textureOf(strain.entityType(), player.level());
            mob = source == null ? null : source(source);
        }

        for (Region region : REGIONS) {
            float threshold = Infection.bonePhase(strain, region.bone(), progress) * SKIN_CAP;
            if (threshold <= 0.0F) {
                continue;
            }
            // Полости — только на туловище, где у существа рёбра и просветы. Конечности у многих существ тоньше
            // человеческих, и пустое место вокруг них в текстуре — не полость, а просто «ничего».
            boolean used = region.bone() == Strain.Bone.BODY && regionUsed(mob, region);
            for (int y = region.y0(); y < region.y1(); y++) {
                for (int x = region.x0(); x < region.x1(); x++) {
                    float value = mask.at(x, y);
                    if (value >= threshold) {
                        continue;
                    }
                    // У узких рук (Алекс) развёртка другая: грани руки шириной 3, а не 4 пикселя. Плоть существа
                    // берётся из той же грани его широкой руки, иначе на руке Алекс швы съезжали бы.
                    int sampleX = slim && isArm(region) ? wideArmX(x, y, region) : x;
                    int color = fleshColor(mob, sampleX + region.sampleDx(), y + region.sampleDy(), strain.color(),
                            used);
                    color = shade(color, 0.82F + 0.30F * mask.shade(x, y));
                    if (threshold - value < RIM) {
                        color = inflame(color, strain.color());
                    }
                    int abgr = FastColor.ABGR32.color(255, color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF);
                    // Только основной слой кожи: слой одежды спадает с кости, когда она превращается
                    // (BodyMorph), а рисовать пятна дважды — значит получить двойную кожу.
                    image.setPixelRGBA(x, y, abgr);
                }
            }
        }
    }

    private static void paintPart(NativeImage image, NativeImage mob, InfectionMask mask, float threshold,
                                  int strainColor) {
        image.fillRect(0, 0, image.getWidth(), image.getHeight(), 0);
        for (int y = 0; y < mob.getHeight(); y++) {
            for (int x = 0; x < mob.getWidth(); x++) {
                int pixel = mob.getPixelRGBA(x, y);
                if (FastColor.ABGR32.alpha(pixel) == 0) {
                    continue;
                }
                float value = mask.at(x, y);
                if (value >= threshold) {
                    continue;
                }
                if (threshold - value < RIM) {
                    int rgb = (FastColor.ABGR32.red(pixel) << 16) | (FastColor.ABGR32.green(pixel) << 8)
                            | FastColor.ABGR32.blue(pixel);
                    rgb = inflame(rgb, strainColor);
                    pixel = FastColor.ABGR32.color(FastColor.ABGR32.alpha(pixel), rgb & 0xFF, (rgb >> 8) & 0xFF,
                            (rgb >> 16) & 0xFF);
                }
                image.setPixelRGBA(x, y, pixel);
            }
        }
    }

    /**
     * Цвет плоти существа в этой точке скина: из его текстуры, если там что-то нарисовано. Если эта часть
     * текстуры у существа есть, но в точке пусто — как между рёбрами скелета, — это полость, и она тёмная.
     * Если части в текстуре нет вовсе (руки крипера), берётся цвет штамма.
     */
    private static int fleshColor(@Nullable NativeImage mob, int x, int y, int fallback, boolean regionUsed) {
        if (mob != null) {
            int scale = Math.max(1, mob.getWidth() / 64);
            int mx = x * scale;
            int my = y * scale;
            if (mx >= 0 && my >= 0 && mx < mob.getWidth() && my < mob.getHeight()) {
                int pixel = mob.getPixelRGBA(mx, my);
                if (FastColor.ABGR32.alpha(pixel) > 0) {
                    return (FastColor.ABGR32.red(pixel) << 16) | (FastColor.ABGR32.green(pixel) << 8)
                            | FastColor.ABGR32.blue(pixel);
                }
                if (regionUsed) {
                    return CAVITY;
                }
            }
        }
        return fallback;
    }

    /** Есть ли в текстуре существа хоть что-то на месте этой части скина. */
    private static boolean regionUsed(@Nullable NativeImage mob, Region region) {
        if (mob == null) {
            return false;
        }
        int scale = Math.max(1, mob.getWidth() / 64);
        for (int y = region.y0() + region.sampleDy(); y < region.y1() + region.sampleDy(); y++) {
            for (int x = region.x0() + region.sampleDx(); x < region.x1() + region.sampleDx(); x++) {
                int mx = x * scale;
                int my = y * scale;
                if (mx >= 0 && my >= 0 && mx < mob.getWidth() && my < mob.getHeight()
                        && FastColor.ABGR32.alpha(mob.getPixelRGBA(mx, my)) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Край пятна: темнее и ближе к цвету штамма, как воспалённая кожа вокруг раны. */
    private static int inflame(int rgb, int strainColor) {
        int dark = shade(strainColor, 0.45F);
        return mix(rgb, dark, 0.6F);
    }

    private static int shade(int rgb, float factor) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static InfectionMask mask(AbstractClientPlayer player, int salt) {
        return new InfectionMask(player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits()
                ^ (salt * 0x9E3779B97F4A7C15L));
    }

    private static Entry entry(String name, int width, int height) {
        Entry entry = ENTRIES.computeIfAbsent(name,
                key -> new Entry(Mutagen.id("infection/" + key.toLowerCase(java.util.Locale.ROOT)), width, height));
        entry.lastUsed = System.currentTimeMillis();
        return entry;
    }

    @Nullable
    private static NativeImage source(ResourceLocation location) {
        return SOURCES.computeIfAbsent(location, key -> {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(key);
            if (resource.isEmpty()) {
                return Optional.empty();
            }
            try (InputStream stream = resource.get().open()) {
                return Optional.of(NativeImage.read(stream));
            } catch (Exception e) {
                LOGGER.warn("[Mutagen] Cannot read texture {} for infection", key, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** Раз в несколько секунд: выгрузить текстуры носителей, которых давно не видно. */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < 5_000L) {
            return;
        }
        lastCleanup = now;
        Iterator<Entry> iterator = ENTRIES.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (now - entry.lastUsed > UNUSED_MS) {
                Minecraft.getInstance().getTextureManager().release(entry.id);
                iterator.remove();
            }
        }
    }

    /** Ресурспак сменился: текстуры существ могли стать другими. */
    public static void reset() {
        for (Entry entry : ENTRIES.values()) {
            entry.key = Integer.MIN_VALUE;
        }
        SOURCES.values().forEach(image -> image.ifPresent(NativeImage::close));
        SOURCES.clear();
    }
}
