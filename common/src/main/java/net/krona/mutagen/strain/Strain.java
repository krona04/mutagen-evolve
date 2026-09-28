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
 * Описание штамма: от какого существа взят геном, как он меняет тело и чем за это платит носитель.
 * <p>
 * С 0.2 штаммы живут в датапаках: {@code data/<namespace>/mutagen/strains/<name>.json},
 * id штамма — {@code <namespace>:<name>}. Формат описан в {@code docs/DESIGN.md}.
 */
public final class Strain {
    private static final int DEFAULT_COLOR = 0x7FAE6B;

    /** Цвет пишется числом или строкой {@code "#RRGGBB"}. */
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
    /** Черты, активные на каждой стадии: считаются один раз, а спрашиваются каждую секунду. */
    private final List<List<Trait>> byStage;
    /** Место каждой кости в очереди заражения: считается один раз, спрашивается каждый кадр. */
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

    /** Цвет для оверлея кожи, частиц и интерфейса. */
    public int color() {
        return color;
    }

    /**
     * Общий масштаб модели носителя. Форму тела задаёт сама модель существа, поэтому для штаммов
     * с моделью он остаётся 1; нужен только штаммам без модели, которые меняют лишь кожу.
     */
    public float targetScale() {
        return targetScale;
    }

    /** Сложность вида: чем выше, тем медленнее секвенирование. */
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
     * Хитбокс и глаза в полной форме — те же, что у самого существа в игре, если штамм не задал своих.
     * Так превращённый игрок занимает ровно столько места, сколько занимал бы моб.
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
     * Слой модели существа для гибридных стадий: {@code "minecraft:zombie"} или {@code "minecraft:zombie#main"}.
     * По умолчанию — id самого существа, так названы слои почти всех ванильных мобов.
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
     * Имя штамма. Если у штамма из чужого датапака нет перевода, имя собирается из имени существа —
     * игрок увидит «Штамм: Ведьма», а не сырой ключ.
     */
    public Component displayName() {
        String key = translationKey();
        if (Language.getInstance().has(key)) {
            return Component.translatable(key);
        }
        return Component.translatable("mutagen.strain.generic", entityType.getDescription());
    }

    /**
     * Цена синтеза: какой катализатор нужен, сколько его уходит за прогон и сколько тиков идёт работа.
     * Сложные виды стоят дороже — это часть баланса 0.2.1.
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

    /** Кость игрока, на которую садится часть существа. */
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
     * Часть существа и кость игрока, которая в неё превращается. {@code part} — путь части в модели существа
     * ({@code "head"}, {@code "body/tail"}), {@code bone} — кость игрока; если часть называется как кость,
     * кость можно не писать.
     * <p>
     * Обычная часть — цель превращения: кость игрока плавно принимает её размеры и положение.
     * {@code grow} — часть, которой у человека нет (задние ноги крипера, хвост): она вырастает из тела
     * на своём месте и двигается вместе с {@code bone}. {@code scale} — поправка к размеру части.
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
     * Откуда берутся цвета заражённой кожи: {@code texture} — из текстуры самого существа (если её раскладка
     * совпадает с человеческой, как у зомби, скелета и головы крипера), {@code color} — из цвета штамма.
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
     * Поза кости к полной форме: поворот в градусах, который добавляется к её движению. Форму кости
     * задаёт модель существа, а здесь — только осанка: вытянутые руки зомби, опущенная голова.
     */
    public record Morph(Bone bone, Vector3f rotation) {
        public static final Codec<Morph> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Bone.CODEC.fieldOf("bone").forGetter(Morph::bone),
                ExtraCodecs.VECTOR3F.optionalFieldOf("rotation", new Vector3f()).forGetter(Morph::rotation)
        ).apply(instance, Morph::new));
    }

    /**
     * Тело штамма: как по нему идёт заражение, в какие части существа превращаются кости игрока,
     * какие кости втягиваются, как меняется поза и какие у носителя габариты в полной форме.
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
