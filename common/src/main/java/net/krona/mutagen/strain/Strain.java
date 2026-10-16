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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Strain description: which creature the genome is taken from, how it changes the body and what the host pays for it.
 * <p>
 * Since 0.2 strains live in datapacks: {@code data/<namespace>/mutagen/strains/<name>.json},
 * the strain id is {@code <namespace>:<name>}. The format is described in {@code docs/DESIGN.md}.
 */
public final class Strain {
    private static final int DEFAULT_COLOR = 0x7FAE6B;

    /** Color is written as a number or a {@code "#RRGGBB"} string. */
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
            Traits.CODEC.listOf().optionalFieldOf("traits", List.of()).forGetter(Strain::traits)
    ).apply(instance, Strain::new));

    private final ResourceLocation id;
    private final EntityType<?> entityType;
    private final int color;
    private final float targetScale;
    private final int difficulty;
    private final Synthesis synthesis;
    private final Body body;
    private final List<Trait> traits;
    /** Traits active at each stage: computed once, queried every second. */
    private final List<List<Trait>> byStage;
    /** Each bone's place in the infection queue: computed once, queried every frame. */
    private final int[] spreadIndex;

    public Strain(ResourceLocation id, EntityType<?> entityType, int color, float targetScale, int difficulty,
                  Synthesis synthesis, Body body, List<Trait> traits) {
        this.id = id;
        this.entityType = entityType;
        this.color = color & 0xFFFFFF;
        this.targetScale = targetScale;
        this.difficulty = difficulty;
        this.synthesis = synthesis;
        this.body = body;
        this.traits = List.copyOf(traits);

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

    /** Color for the skin overlay, particles and HUD. */
    public int color() {
        return color;
    }

    /**
     * Overall scale of the host's model. The body shape is set by the creature's model itself, so for strains
     * with a model it stays 1; it's only needed for strains without a model, which change just the skin.
     */
    public float targetScale() {
        return targetScale;
    }

    /** Species complexity: the higher, the slower the sequencing. */
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
     * Hitbox and eyes in full form — the same as the creature's own in game, unless the strain specifies its own.
     * This way a transformed player takes up exactly as much space as the mob would.
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
     * Creature model layer for the hybrid stages: {@code "minecraft:zombie"} or {@code "minecraft:zombie#main"}.
     * Defaults to the creature's own id — that's how the layers of almost all vanilla mobs are named.
     */
    public String modelLayer() {
        return body.modelLayer().orElseGet(() -> BuiltInRegistries.ENTITY_TYPE.getKey(entityType) + "#main");
    }

    public List<Trait> traits() {
        return traits;
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
     * Strain name. If a strain from a third-party datapack has no translation, the name is built from the creature's name —
     * the player will see "Strain: Witch" rather than a raw key.
     */
    public Component displayName() {
        String key = translationKey();
        if (Language.getInstance().has(key)) {
            return Component.translatable(key);
        }
        return Component.translatable("mutagen.strain.generic", entityType.getDescription());
    }

    /**
     * Synthesis cost: which catalyst is needed, how much of it is used per run and how many ticks the work takes.
     * Complex species cost more — this is part of the 0.2.1 balance.
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

    /** The player bone the creature part attaches to. */
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
     * A creature part and the player bone that turns into it. {@code part} is the part's path in the creature model
     * ({@code "head"}, {@code "body/tail"}), {@code bone} is the player bone; if the part is named like the bone,
     * the bone can be omitted.
     * <p>
     * A regular part is a transformation target: the player's bone smoothly takes on its size and position.
     * {@code grow} — a part humans don't have (a creeper's hind legs, a tail): it grows out of the body
     * in its own place and moves together with {@code bone}. {@code scale} — a correction to the part's size.
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
     * Where the infected skin colors come from: {@code texture} — from the creature's own texture (if its layout
     * matches the human one, as with the zombie, the skeleton and the creeper's head), {@code color} — from the strain color.
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
     * Bone pose towards the full form: a rotation in degrees added to its movement. The bone's shape
     * is set by the creature's model, and here only the posture: a zombie's outstretched arms, a lowered head.
     */
    public record Morph(Bone bone, Vector3f rotation) {
        public static final Codec<Morph> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Bone.CODEC.fieldOf("bone").forGetter(Morph::bone),
                ExtraCodecs.VECTOR3F.optionalFieldOf("rotation", new Vector3f()).forGetter(Morph::rotation)
        ).apply(instance, Morph::new));
    }

    /**
     * Strain body: how the infection spreads through it, which creature parts the player's bones turn into,
     * which bones retract, how the pose changes and what dimensions the host has in full form.
     */
    public record Body(Optional<String> modelLayer, Optional<List<Part>> parts, List<Bone> absorb,
                       Optional<Float> width, Optional<Float> height, Optional<Float> eyeHeight, float reach,
                       List<Bone> spread, SkinSource skin, List<Morph> morph) {
        public static final Body DEFAULT = new Body(Optional.empty(), Optional.empty(), List.of(), Optional.empty(),
                Optional.empty(), Optional.empty(), 0.0F, List.of(), SkinSource.COLOR, List.of());

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
                Morph.CODEC.listOf().optionalFieldOf("morph", List.of()).forGetter(Body::morph)
        ).apply(instance, Body::new));
    }

    @Override
    public String toString() {
        return "Strain[" + id + " <- " + BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString()
                .toLowerCase(Locale.ROOT) + "]";
    }
}
