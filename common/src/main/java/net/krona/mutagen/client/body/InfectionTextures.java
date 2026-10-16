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
 * Infection textures the mod draws itself for each host.
 * <p>
 * <b>Skin.</b> A texture in the same layout is laid over the player's skin: infected pixels are opaque,
 * the rest are empty. Each bone becomes infected in its own progress window ({@link Infection}), the blotches grow along
 * the player's personal pattern ({@link InfectionMask}), and at the blotch border the skin is darker — an inflamed edge.
 * The blotch color comes from the creature's own texture if its layout matches the human one, otherwise
 * from the strain color; empty areas in the creature's texture become dark cavities. Blotches are applied only to
 * the base layer: the clothing layer falls off a transforming bone.
 * <p>
 * <b>Creature parts.</b> The creature's texture appears the same way — in islands, starting small —
 * so the part grows through the skin instead of showing through as a translucent film.
 * <p>
 * Textures are rebuilt only when progress has moved by half a percent, and are unloaded if
 * the host hasn't been rendered for a long time.
 */
@Environment(EnvType.CLIENT)
public final class InfectionTextures {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How much of the skin the infection can cover before the full form. */
    private static final float SKIN_CAP = 0.86F;
    /** The creature's hide appears right away as sparse islands and covers the whole bone by the end of its transformation. */
    private static final float PART_BASE = 0.04F;
    /** Width of the blotch's inflamed edge, in mask units. */
    private static final float RIM = 0.07F;
    private static final long UNUSED_MS = 30_000L;
    /** A cavity in the creature's body — for example, between a skeleton's ribs. */
    private static final int CAVITY = 0x1C1A1A;

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final Map<ResourceLocation, Optional<NativeImage>> SOURCES = new HashMap<>();
    private static long lastCleanup;

    /** Layout of the player's 64×64 base skin layer and where the same part lies in the creature's texture. */
    private record Region(Strain.Bone bone, int x0, int y0, int x1, int y1, int sampleDx, int sampleDy) {
    }

    private static final List<Region> REGIONS = List.of(
            new Region(Strain.Bone.HEAD, 0, 0, 32, 16, 0, 0),
            new Region(Strain.Bone.BODY, 16, 16, 40, 32, 0, 0),
            new Region(Strain.Bone.RIGHT_ARM, 40, 16, 56, 32, 0, 0),
            new Region(Strain.Bone.RIGHT_LEG, 0, 16, 16, 32, 0, 0),
            // In old-layout mob textures the left limbs are drawn in place of the right ones.
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

    /** The player's infected skin, or null if the infection hasn't started yet. */
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
     * The creature part's hide on a transforming bone: in islands by the bone's phase, fully by its end.
     * Null if the bone's transformation hasn't started yet.
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
     * Column of the wide (4-pixel) arm unwrap for a column of the slim (3-pixel) one. The side faces of both
     * are 4 pixels, while the front, back, top and bottom of the slim arm are a pixel narrower — they get stretched.
     */
    private static int wideArmX(int x, int y, Region region) {
        int lx = x - region.x0();
        int ly = y - region.y0();
        int wide;
        if (ly < 4) {
            if (lx >= 4 && lx < 7) {
                wide = 4 + (lx - 4) * 4 / 3;          // top
            } else if (lx >= 7 && lx < 10) {
                wide = 8 + (lx - 7) * 4 / 3;          // bottom
            } else {
                wide = lx;
            }
        } else if (lx < 4) {
            wide = lx;                               // outer side
        } else if (lx < 7) {
            wide = 4 + (lx - 4) * 4 / 3;              // front
        } else if (lx < 11) {
            wide = lx + 1;                           // inner side
        } else if (lx < 14) {
            wide = 12 + (lx - 11) * 4 / 3;            // back
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
            // Cavities only on the torso, where the creature has ribs and gaps. Many creatures' limbs are thinner than
            // human ones, and the empty space around them in the texture is not a cavity but simply "nothing".
            boolean used = region.bone() == Strain.Bone.BODY && regionUsed(mob, region);
            for (int y = region.y0(); y < region.y1(); y++) {
                for (int x = region.x0(); x < region.x1(); x++) {
                    float value = mask.at(x, y);
                    if (value >= threshold) {
                        continue;
                    }
                    // Slim arms (Alex) have a different unwrap: arm faces are 3 pixels wide, not 4. The creature's flesh
                    // is taken from the same face of its wide arm, otherwise the seams on Alex's arm would shift.
                    int sampleX = slim && isArm(region) ? wideArmX(x, y, region) : x;
                    int color = fleshColor(mob, sampleX + region.sampleDx(), y + region.sampleDy(), strain.color(),
                            used);
                    color = shade(color, 0.82F + 0.30F * mask.shade(x, y));
                    if (threshold - value < RIM) {
                        color = inflame(color, strain.color());
                    }
                    int abgr = FastColor.ABGR32.color(255, color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF);
                    // Base skin layer only: the clothing layer falls off a bone when it transforms
                    // (BodyMorph), and drawing the blotches twice would mean getting a double skin.
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
     * The creature's flesh color at this skin point: from its texture, if something is drawn there. If the creature's texture
     * has this part but the point is empty — like between a skeleton's ribs — it is a cavity, and it is dark.
     * If the texture has no such part at all (a creeper's arms), the strain color is used.
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

    /** Whether the creature's texture has anything at all in place of this skin part. */
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

    /** Blotch edge: darker and closer to the strain color, like inflamed skin around a wound. */
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

    /** Every few seconds: unload textures of hosts that haven't been seen for a long time. */
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

    /** The resource pack changed: creature textures may have become different. */
    public static void reset() {
        for (Entry entry : ENTRIES.values()) {
            entry.key = Integer.MIN_VALUE;
        }
        SOURCES.values().forEach(image -> image.ifPresent(NativeImage::close));
        SOURCES.clear();
    }
}
