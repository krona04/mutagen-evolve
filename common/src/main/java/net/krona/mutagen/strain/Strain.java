package net.krona.mutagen.strain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.krona.mutagen.registry.MutagenItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.krona.mutagen.ability.Abilities;
import net.krona.mutagen.ability.Ability;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * A strain defines its source creature, body changes, and the cost to the player.
 * <p>
 * Since 0.2, datapacks define strains in {@code data/<namespace>/mutagen/strains/<name>.json} under ID
 * {@code <namespace>:<name>}. See {@code docs/DESIGN.md} for the format.
 */
public final class Strain {
    private static final int DEFAULT_COLOR = 0x7FAE6B;

    /** Write a color as a number or a {@code "#RRGGBB"} string. */
    private static final Codec<Integer> COLOR_CODEC = Codec.withAlternative(Codec.INT,
            Codec.STRING.comapFlatMap(Strain::parseColor, color -> String.format("#%06X", color)));

    public static final Codec<Strain> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Strain::id),
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(Strain::entityType),
            COLOR_CODEC.optionalFieldOf("color", DEFAULT_COLOR).forGetter(Strain::color),
            Codec.floatRange(0.1F, 16.0F).optionalFieldOf("scale", 1.0F).forGetter(Strain::targetScale),
            Codec.intRange(1, 10).optionalFieldOf("difficulty", 1).forGetter(Strain::difficulty),
            Synthesis.CODEC.optionalFieldOf("synthesis", Synthesis.DEFAULT).forGetter(Strain::synthesis),
            Body.CODEC.optionalFieldOf("body", Body.DEFAULT).forGetter(Strain::body),
            Traits.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(Strain::traits),
            Abilities.CODEC.listOf().optionalFieldOf("abilities", List.of()).forGetter(Strain::abilities),
            Vision.CODEC.optionalFieldOf("vision").forGetter(Strain::vision)
    ).apply(instance, Strain::new));

    private final ResourceLocation id;
    private final EntityType<?> entityType;
    private final int color;
    private final float targetScale;
    private final int difficulty;
    private final Synthesis synthesis;
    private final Body body;
    private final List<Trait> traits;
    private final List<Ability> abilities;
    private final Optional<Vision> vision;
    /** Precompute the active traits for each stage because they are queried every second. */
    private final List<List<Trait>> byStage;
    /** Precompute each bone's place in the infection order because it is queried every frame. */
    private final int[] spreadIndex;

    public Strain(ResourceLocation id, EntityType<?> entityType, int color, float targetScale, int difficulty,
                  Synthesis synthesis, Body body, List<Trait> traits, List<Ability> abilities,
                  Optional<Vision> vision) {
        this.id = id;
        this.vision = vision;
        this.entityType = entityType;
        this.color = color & 0xFFFFFF;
        this.targetScale = targetScale;
        this.difficulty = difficulty;
        this.synthesis = synthesis;
        this.body = body;
        this.traits = List.copyOf(traits);
        this.abilities = List.copyOf(abilities);

        List<List<Trait>> stages = new ArrayList<>();
        for (Stage stage : Stage.values()) {
            List<Trait> active = new ArrayList<>();
            for (Trait trait : this.traits) {
                if (stage.atLeast(trait.minStage())) {
                    active.add(trait);
                }
            }
            stages.add(List.copyOf(active));
        }
        this.byStage = List.copyOf(stages);

        List<Bone> order = Infection.order(body);
        this.spreadIndex = new int[Bone.values().length];
        for (int i = 0; i < order.size(); i++) {
            spreadIndex[order.get(i).ordinal()] = i;
        }
    }

    private static DataResult<Integer> parseColor(String value) {
        String hex = value.startsWith("#") ? value.substring(1) : value;
        try {
            return DataResult.success(Integer.parseInt(hex, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Bad color: " + value);
        }
    }

    public ResourceLocation id() {
        return id;
    }

    public EntityType<?> entityType() {
        return entityType;
    }

    /** Color for the skin overlay, particles, and UI. */
    public int color() {
        return color;
    }

    /**
     * Overall model scale. The creature model already supplies its form, so keep scale at 1 for strains
     * with a model. Skin-only strains can use this value.
     */
    public float targetScale() {
        return targetScale;
    }

    /** Species difficulty. Higher values slow sequencing. */
    public int difficulty() {
        return difficulty;
    }

    public Synthesis synthesis() {
        return synthesis;
    }

    public Body body() {
        return body;
    }

    /**
     * In full form, use the creature's hitbox and eye height unless the strain overrides them.
     */
    public float targetWidth() {
        return body.width().orElseGet(() -> entityType.getDimensions().width());
    }

    public float targetHeight() {
        return body.height().orElseGet(() -> entityType.getDimensions().height());
    }

    public float targetEyeHeight() {
        return body.eyeHeight().orElseGet(() -> entityType.getDimensions().eyeHeight());
    }

    /**
     * Creature model layer for hybrid stages, such as {@code "minecraft:zombie"} or {@code
     * "minecraft:zombie#main"}. Defaults to the entity ID used by most vanilla layers.
     */
    public String modelLayer() {
        return body.modelLayer().orElseGet(() -> BuiltInRegistries.ENTITY_TYPE.getKey(entityType) + "#main");
    }

    /** Active abilities in the index order used by the client. */
    public List<Ability> abilities() {
        return abilities;
    }

    /** The strain's apex ability, if present. */
    public java.util.Optional<Ability> apex() {
        return abilities.stream().filter(Ability::apex).findFirst();
    }

    public List<Trait> traits() {
        return traits;
    }

    /**
     * Find custom timing by the ID path for built-in strains and use {@code "custom"} for others. A
     * datapack strain such as {@code my_pack:zombie} does not share the built-in zombie's timing.
     */
    public String profile() {
        return id.getNamespace().equals("mutagen") ? id.getPath() : "custom";
    }

    public int spreadIndex(Bone bone) {
        return spreadIndex[bone.ordinal()];
    }

    public List<Trait> activeTraits(Stage stage) {
        return byStage.get(stage.ordinal());
    }

    public String translationKey() {
        return "mutagen.strain." + id.getNamespace() + "." + id.getPath();
    }

    /**
     * Display name of the strain. If a datapack provides no translation, use the creature name so the
     * player sees a label such as Strain: Witch instead of a raw key.
     */
    public Component displayName() {
        String key = translationKey();
        if (Language.getInstance().has(key)) {
            return Component.translatable(key);
        }
        return Component.translatable("mutagen.strain.generic", entityType.getDescription());
    }

    /**
     * Synthesis catalyst, quantity per run, and processing time in ticks. More difficult species cost more
     * in 0.2.1.
     */
    public record Synthesis(Optional<Item> catalystItem, int catalystCount, int time) {
        public static final Synthesis DEFAULT = new Synthesis(Optional.empty(), 1, 300);

        public static final Codec<Synthesis> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("catalyst").forGetter(Synthesis::catalystItem),
                Codec.intRange(1, 64).optionalFieldOf("catalyst_count", 1).forGetter(Synthesis::catalystCount),
                Codec.intRange(20, 72000).optionalFieldOf("time", 300).forGetter(Synthesis::time)
        ).apply(instance, Synthesis::new));

        public Item catalyst() {
            return catalystItem.orElseGet(MutagenItems.CATALYST);
        }
    }

    /** Player bone that the creature part attaches to. */
    public enum Bone {
        HEAD("head"), BODY("body"), RIGHT_ARM("right_arm"), LEFT_ARM("left_arm"),
        RIGHT_LEG("right_leg"), LEFT_LEG("left_leg");

        public static final Codec<Bone> CODEC = Codec.STRING.comapFlatMap(Bone::parse, Bone::key);

        private final String key;

        Bone(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        static DataResult<Bone> parse(String name) {
            for (Bone bone : values()) {
                if (bone.key.equals(name)) {
                    return DataResult.success(bone);
                }
            }
            return DataResult.error(() -> "Unknown bone: " + name);
        }

        static Optional<Bone> byKey(String name) {
            return parse(name).result();
        }
    }

    /**
     * Creature part and its corresponding player bone. {@code part} is a path such as {@code "head"} or
     * {@code "body/tail"}. Omit {@code bone} if the part has the same name as the bone.
     * <p>
     * A regular part supplies the target dimensions and position for the bone. A {@code grow} part, such as
     * a tail or creeper hind leg, has no human equivalent and grows from its attachment {@code bone}.
     * {@code scale} adjusts part size.
     */
    public record Part(String part, Bone bone, boolean grow, Vector3f scale) {
        private static final Vector3f ONE = new Vector3f(1.0F, 1.0F, 1.0F);

        private static final Codec<Part> RAW = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("part").forGetter(Part::part),
                Bone.CODEC.optionalFieldOf("bone").forGetter(part -> Optional.ofNullable(part.bone())),
                Codec.BOOL.optionalFieldOf("grow", false).forGetter(Part::grow),
                ExtraCodecs.VECTOR3F.optionalFieldOf("scale", ONE).forGetter(Part::scale)
        ).apply(instance, (name, bone, grow, scale) -> new Part(name, bone.orElse(null), grow, scale)));

        public static final Codec<Part> CODEC = RAW.validate(part -> part.bone() != null
                ? DataResult.success(part)
                : DataResult.error(() -> "Part '" + part.part() + "' needs a \"bone\""));

        public Part(String part, @Nullable Bone bone, boolean grow, Vector3f scale) {
            this.part = part;
            this.bone = bone != null ? bone : Bone.byKey(part.substring(part.lastIndexOf('/') + 1)).orElse(null);
            this.grow = grow;
            this.scale = scale;
        }

        public Part(String part, Bone bone) {
            this(part, bone, false, ONE);
        }
    }

    /**
     * Source of infected skin color. Use {@code texture} to sample compatible creature UVs, such as zombie
     * and skeleton UVs or a creeper head. Use {@code color} for the strain's tint.
     */
    public enum SkinSource {
        COLOR("color"), TEXTURE("texture");

        public static final Codec<SkinSource> CODEC = Codec.STRING.comapFlatMap(name -> {
            for (SkinSource source : values()) {
                if (source.key.equals(name)) {
                    return DataResult.success(source);
                }
            }
            return DataResult.error(() -> "Unknown skin source: " + name);
        }, SkinSource::key);

        private final String key;

        SkinSource(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /**
     * Rotation in degrees added to a bone's animation by full form. The creature model supplies its shape,
     * while this controls posture, such as outstretched zombie arms or a lowered head.
     */
    public record Morph(Bone bone, Vector3f rotation) {
        public static final Codec<Morph> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Bone.CODEC.fieldOf("bone").forGetter(Morph::bone),
                ExtraCodecs.VECTOR3F.optionalFieldOf("rotation", new Vector3f()).forGetter(Morph::rotation)
        ).apply(instance, Morph::new));
    }

    /**
     * Strain body settings for infection order, bone targets, retracting parts, posture, and full-form
     * dimensions.
     */
    /**
     * Extra creature render layer for details such as drowned clothing or glowing spider eyes. {@code
     * model_layer} selects the layer and defaults to the main one. Set {@code emissive} to make it glow in
     * the dark.
     */
    public record Overlay(Optional<String> modelLayer, ResourceLocation texture, boolean emissive) {
        public static final Codec<Overlay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("model_layer").forGetter(Overlay::modelLayer),
                ResourceLocation.CODEC.fieldOf("texture").forGetter(Overlay::texture),
                Codec.BOOL.optionalFieldOf("emissive", false).forGetter(Overlay::emissive)
        ).apply(instance, Overlay::new));
    }

    public record Body(Optional<String> modelLayer, Optional<List<Part>> parts, List<Bone> absorb,
                       Optional<Float> width, Optional<Float> height, Optional<Float> eyeHeight, float reach,
                       List<Bone> spread, SkinSource skin, List<Morph> morph, List<Overlay> overlays) {
        public static final Body DEFAULT = new Body(Optional.empty(), Optional.empty(), List.of(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0.0F, List.of(), SkinSource.COLOR, List.of(), List.of());

        public static final Codec<Body> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("model_layer").forGetter(Body::modelLayer),
                Part.CODEC.listOf().optionalFieldOf("parts").forGetter(Body::parts),
                Bone.CODEC.listOf().optionalFieldOf("absorb", List.of()).forGetter(Body::absorb),
                Codec.floatRange(0.1F, 8.0F).optionalFieldOf("width").forGetter(Body::width),
                Codec.floatRange(0.1F, 8.0F).optionalFieldOf("height").forGetter(Body::height),
                Codec.floatRange(0.05F, 8.0F).optionalFieldOf("eye_height").forGetter(Body::eyeHeight),
                Codec.floatRange(-3.0F, 8.0F).optionalFieldOf("reach", 0.0F).forGetter(Body::reach),
                Bone.CODEC.listOf().optionalFieldOf("spread", List.of()).forGetter(Body::spread),
                SkinSource.CODEC.optionalFieldOf("skin", SkinSource.COLOR).forGetter(Body::skin),
                Morph.CODEC.listOf().optionalFieldOf("morph", List.of()).forGetter(Body::morph),
                Overlay.CODEC.listOf().optionalFieldOf("overlays", List.of()).forGetter(Body::overlays)
        ).apply(instance, Body::new));
    }

    /** Creature vision screen effect from the specified stage. Enabled through {@code mobVision}. */
    public Optional<Vision> vision() {
        return vision;
    }

    /**
     * The {@code shader} is a post-processing chain ({@code shaders/post/*.json}), like vanilla creeper or
     * spider spectator vision. {@code from_stage} defines when it begins. Its strength reaches maximum in
     * full form.
     */
    public record Vision(ResourceLocation shader, Stage minStage) {
        public static final Codec<Vision> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("shader").forGetter(Vision::shader),
                Stage.CODEC.optionalFieldOf("from_stage", Stage.MANIFESTATION).forGetter(Vision::minStage)
        ).apply(instance, Vision::new));

        public float strength(float progress) {
            int threshold = minStage.threshold();
            if (minStage == Stage.FULL) {
                return progress >= threshold ? 1.0F : 0.0F;
            }
            return Mth.clamp((progress - threshold) / (100.0F - threshold), 0.0F, 1.0F);
        }
    }

    @Override
    public String toString() {
        return "Strain[" + id + " <- " + BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString()
                .toLowerCase(Locale.ROOT) + "]";
    }
}
