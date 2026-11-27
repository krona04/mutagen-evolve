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
 * Infection textures generated per player.
 * <p>
 * <b>Skin.</b> Overlay the player skin using the same UV layout, with opaque infected pixels and
 * transparent unaffected ones. Each bone has its own infection window ({@link Infection}), and the player's
 * pattern ({@link InfectionMask}) adds darker patch edges. Sample the creature texture when its layout
 * matches, otherwise use the strain color. Gaps in the creature texture become dark cavities. Draw only on
 * the base layer because clothing falls off the transforming bone.
 * <p>
 * <b>Creature parts.</b> Reveal the texture in small patches so the part appears to grow through the skin.
 * <p>
 * Rebuild textures only after progress changes by half a percentage point. Evict textures for players who
 * have not rendered recently.
 */
@Environment(EnvType.CLIENT)
public final class InfectionTextures {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Maximum infection coverage of the skin before full form. */
    private static final float SKIN_CAP = 0.86F;
    /** Reveal small patches first and cover the entire bone by the end of its transformation. */
    private static final float PART_BASE = 0.04F;
    /** Width of the inflamed patch edge in mask units. */
    private static final float RIM = 0.07F;
    private static final long UNUSED_MS = 30_000L;
    /** A cavity in a creature's body, such as the gap between skeleton ribs. */
    private static final int CAVITY = 0x1C1A1A;

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final Map<ResourceLocation, Optional<NativeImage>> SOURCES = new HashMap<>();
    private static long lastCleanup;

    /** The 64×64 player base-layer layout and corresponding region in the creature texture. */
    private record Region(Strain.Bone bone, int x0, int y0, int x1, int y1, int sampleDx, int sampleDy) {
    }

    private static final List<Region> REGIONS = List.of(
            new Region(Strain.Bone.HEAD, 0, 0, 32, 16, 0, 0),
            new Region(Strain.Bone.BODY, 16, 16, 40, 32, 0, 0),
            new Region(Strain.Bone.RIGHT_ARM, 40, 16, 56, 32, 0, 0),
            new Region(Strain.Bone.RIGHT_LEG, 0, 16, 16, 32, 0, 0),
            // Legacy mob textures use the right-limb texture for the left limbs.
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

    /** Infected player skin, or null before infection begins. */
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
     * Skin for a transforming bone. Reveal patches according to its local phase until fully covered. Return
     * null before the bone starts transforming.
     */
    @Nullable
    public static ResourceLocation part(AbstractClientPlayer player, Strain strain, int index, float phase) {
        if (phase <= 0.0F) {
            return null;
        }
        return part(player, strain, index, phase, FullFormRenderer.textureOf(strain.entityType(), player.level()), 0);
    }

    /**
     * Use the same pattern for the extra layer ({@link Strain.Overlay}) and the base creature skin so a
     * drowned's clothing appears alongside its skin.
     */
    @Nullable
    public static ResourceLocation part(AbstractClientPlayer player, Strain strain, int index, float phase,
                                        @Nullable ResourceLocation source, int layer) {
        if (phase <= 0.0F) {
            return null;
        }
        // Once the player surface is retired, use the unmasked original: no residual noise or rim.
        if (MorphSurface.replaced(phase)) return source;
        NativeImage mob = source == null ? null : source(source);
        if (mob == null) {
            return null;
        }
        String name = player.getUUID() + "/part_" + index + "_" + layer + "_" + mob.getWidth() + "x" + mob.getHeight();
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

    /** Replace source-skin texels through the original seeded infection mask on the persistent mesh. */
    public static ResourceLocation continuousMorph(AbstractClientPlayer player, Strain strain, float progress,
                                             net.minecraft.client.model.geom.ModelPart.Cube[] humanCubes,
                                             MorphCuboid.Surface[] wolfCubes) {
        ResourceLocation furLocation = FullFormRenderer.textureOf(strain.entityType(), player.level());
        NativeImage fur = furLocation == null ? null : source(furLocation);
        if (fur == null) return null;
        ResourceLocation skinLocation = player.getSkin().texture();
        NativeImage skin = source(skinLocation);
        if (skin == null) {
            // Downloaded player skins are GPU textures, not resource-pack files. Read once, cache until reload.
            int previous = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
            try {
                Minecraft.getInstance().getTextureManager().getTexture(skinLocation).bind();
                int width = org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
                int height = org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
                if (width > 0 && height > 0) {
                    skin = new NativeImage(width, height, false);
                    skin.downloadTexture(0, false);
                    SOURCES.put(skinLocation, Optional.of(skin));
                }
            } finally {
                com.mojang.blaze3d.platform.GlStateManager._bindTexture(previous);
            }
        }
        if (skin == null) skin = fur;
        final int tile = 48;
        Entry entry = entry(player.getUUID() + "/mesh_" + strain.id().getPath(), tile * 6, tile * 6);
        int clothing = 0;
        for (Strain.Bone bone : Strain.Bone.values()) if (player.isModelPartShown(skinPart(bone))) clothing |= 1 << bone.ordinal();
        int key = ((skinLocation.hashCode() * 31 + furLocation.hashCode()) * 67 + clothing) * 401 + Math.round(progress * 4);
        key = key * 2 + (slim(player) ? 1 : 0);
        if (entry.key == key) return entry.id;
        entry.key = key;
        entry.texture.setFilter(false, false);
        NativeImage output = entry.texture.getPixels();
        InfectionMask infection = mask(player, 0);
        for (int bone = 0; bone < 6; bone++) {
            Strain.Bone humanBone = Strain.Bone.values()[bone];
            float phase = Infection.bonePhase(strain, humanBone, progress);
            float layerX = switch (humanBone) {
                case HEAD -> 32 / 64F;
                case LEFT_ARM -> 16 / 64F;
                case LEFT_LEG -> -16 / 64F;
                default -> 0;
            };
            float layerY = switch (humanBone) {
                case BODY, RIGHT_ARM, RIGHT_LEG -> 16 / 64F;
                default -> 0;
            };
            var corners = wolfCubes[bone].vertices();
            for (int face = 0; face < 6; face++) {
                var t0 = corners.get(face * 4);
                var t1 = corners.get(face * 4 + 1);
                var t3 = corners.get(face * 4 + 3);
                var s0 = MorphCuboid.match(humanCubes[bone], wolfCubes[bone].box(), t0);
                var s1 = MorphCuboid.match(humanCubes[bone], wolfCubes[bone].box(), t1);
                var s3 = MorphCuboid.match(humanCubes[bone], wolfCubes[bone].box(), t3);
                for (int y = 0; y < tile; y++) for (int x = 0; x < tile; x++) {
                    float u = (x + 0.5F) / tile, v = (y + 0.5F) / tile;
                    float su = s0.u() + (s1.u()-s0.u())*u + (s3.u()-s0.u())*v;
                    float sv = s0.v() + (s1.v()-s0.v())*u + (s3.v()-s0.v())*v;
                    int a = sample(skin, su, sv);
                    if ((clothing & (1 << bone)) != 0) {
                        int outer = sample(skin, su + layerX, sv + layerY);
                        float opacity = FastColor.ABGR32.alpha(outer) / 255F;
                        a = FastColor.ABGR32.color(255,
                                Math.round(FastColor.ABGR32.blue(a)*(1-opacity)+FastColor.ABGR32.blue(outer)*opacity),
                                Math.round(FastColor.ABGR32.green(a)*(1-opacity)+FastColor.ABGR32.green(outer)*opacity),
                                Math.round(FastColor.ABGR32.red(a)*(1-opacity)+FastColor.ABGR32.red(outer)*opacity));
                    }
                    int b = sample(fur, t0.u() + (t1.u()-t0.u())*u + (t3.u()-t0.u())*v,
                            t0.v() + (t1.v()-t0.v())*u + (t3.v()-t0.v())*v);
                    int rgbA = (FastColor.ABGR32.red(a)<<16)|(FastColor.ABGR32.green(a)<<8)|FastColor.ABGR32.blue(a);
                    int rgbB = (FastColor.ABGR32.red(b)<<16)|(FastColor.ABGR32.green(b)<<8)|FastColor.ABGR32.blue(b);
                    // Future internal faces merge into the creature; do not duplicate its face/eyes on them.
                    if (!wolfCubes[bone].outer(face)) rgbB = strain.color();
                    int rgb = PixelMorph.colour(rgbA, rgbB, infection, su, sv, phase);
                    output.setPixelRGBA(face*tile+x, bone*tile+y,
                            FastColor.ABGR32.color(255,rgb&255,(rgb>>8)&255,(rgb>>16)&255));
                }
            }
        }
        entry.texture.upload();
        return entry.id;
    }

    private static int sample(NativeImage image, float u, float v) {
        int x = Math.max(0, Math.min(image.getWidth()-1, (int)(u*image.getWidth())));
        int y = Math.max(0, Math.min(image.getHeight()-1, (int)(v*image.getHeight())));
        return image.getPixelRGBA(x,y);
    }

    private static net.minecraft.world.entity.player.PlayerModelPart skinPart(Strain.Bone bone) {
        return switch (bone) {
            case HEAD -> net.minecraft.world.entity.player.PlayerModelPart.HAT;
            case BODY -> net.minecraft.world.entity.player.PlayerModelPart.JACKET;
            case RIGHT_ARM -> net.minecraft.world.entity.player.PlayerModelPart.RIGHT_SLEEVE;
            case LEFT_ARM -> net.minecraft.world.entity.player.PlayerModelPart.LEFT_SLEEVE;
            case RIGHT_LEG -> net.minecraft.world.entity.player.PlayerModelPart.RIGHT_PANTS_LEG;
            case LEFT_LEG -> net.minecraft.world.entity.player.PlayerModelPart.LEFT_PANTS_LEG;
        };
    }

    private static boolean isArm(Region region) {
        return region.bone() == Strain.Bone.RIGHT_ARM || region.bone() == Strain.Bone.LEFT_ARM;
    }

    /**
     * Map a column from a 3-pixel slim arm to a 4-pixel arm. Both side faces are 4 pixels wide. Stretch the
     * slim front, back, top, and bottom by one pixel.
     */
    private static int wideArmX(int x, int y, Region region) {
        int lx = x - region.x0();
        int ly = y - region.y0();
        int wide;
        if (ly < 4) {
            if (lx >= 4 && lx < 7) {
                wide = 4 + (lx - 4) * 4 / 3;          // Top
            } else if (lx >= 7 && lx < 10) {
                wide = 8 + (lx - 7) * 4 / 3;          // Bottom
            } else {
                wide = lx;
            }
        } else if (lx < 4) {
            wide = lx;                               // Outer side
        } else if (lx < 7) {
            wide = 4 + (lx - 4) * 4 / 3;              // Front
        } else if (lx < 11) {
            wide = lx + 1;                           // Inner side
        } else if (lx < 14) {
            wide = 12 + (lx - 11) * 4 / 3;            // Back
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
            // Only torso gaps, such as between exposed ribs, count as cavities. Empty space around a
            // creature's narrower limbs is transparent.
            boolean used = region.bone() == Strain.Bone.BODY && regionUsed(mob, region);
            for (int y = region.y0(); y < region.y1(); y++) {
                for (int x = region.x0(); x < region.x1(); x++) {
                    float value = mask.at(x, y);
                    if (value >= threshold) {
                        continue;
                    }
                    // Slim Alex arms use 3-pixel faces. Sample the matching face of the creature's wider
                    // arm to keep the seams aligned.
                    int sampleX = slim && isArm(region) ? wideArmX(x, y, region) : x;
                    int color = fleshColor(mob, sampleX + region.sampleDx(), y + region.sampleDy(), strain.color(),
                            used);
                    color = shade(color, 0.82F + 0.30F * mask.shade(x, y));
                    if (threshold - value < RIM) {
                        color = inflame(color, strain.color());
                    }
                    int abgr = FastColor.ABGR32.color(255, color & 0xFF, (color >> 8) & 0xFF, (color >> 16) & 0xFF);
                    // Draw only on the base skin. BodyMorph removes the clothing layer as the bone changes.
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
     * Sample the creature's texture for this skin pixel. If the part exists but the pixel is empty, shade
     * it as a cavity, as between skeleton ribs. If the part is absent altogether, as with creeper arms, use
     * the strain color.
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

    /** Whether the creature texture has any pixels in the corresponding region. */
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

    /** Darken and tint the patch edge toward the strain color to suggest inflammation. */
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

    /** Periodically unload textures for players who have not rendered recently. */
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

    /** Reloaded resource packs may change creature textures. */
    public static void reset() {
        for (Entry entry : ENTRIES.values()) {
            entry.key = Integer.MIN_VALUE;
        }
        SOURCES.values().forEach(image -> image.ifPresent(NativeImage::close));
        SOURCES.clear();
    }
}
