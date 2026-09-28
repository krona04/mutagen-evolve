package net.krona.mutagen.strain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.krona.mutagen.MutagenConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.krona.mutagen.mutation.Restrictions;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Типы черт, из которых собираются штаммы в датапаках. Каждая сильная сторона здесь соседствует
 * со своей ценой: правило мода — способность и слабость приходят в одной версии.
 * <p>
 * Общие поля у всех типов: {@code "from_stage"} — стадия, с которой черта работает, и
 * необязательное {@code "description"} — ключ перевода, если стандартное описание не подходит.
 */
public final class Traits {
    public static final TraitType<Attr> ATTRIBUTE = TraitType.register("attribute", Attr.CODEC);
    public static final TraitType<SunBurn> SUN_BURN = TraitType.register("sun_burn", SunBurn.CODEC);
    public static final TraitType<DarknessRegeneration> DARK_REGENERATION =
            TraitType.register("dark_regeneration", DarknessRegeneration.CODEC);
    public static final TraitType<EffectImmunity> EFFECT_IMMUNITY =
            TraitType.register("effect_immunity", EffectImmunity.CODEC);
    public static final TraitType<DamageFactor> DAMAGE_FACTOR = TraitType.register("damage_factor", DamageFactor.CODEC);
    public static final TraitType<CatFear> CAT_FEAR = TraitType.register("cat_fear", CatFear.CODEC);
    public static final TraitType<ArmorRejection> ARMOR_REJECTION =
            TraitType.register("armor_rejection", ArmorRejection.CODEC);
    public static final TraitType<ArrowPower> ARROW_POWER = TraitType.register("arrow_power", ArrowPower.CODEC);
    public static final TraitType<NoOffhand> NO_OFFHAND = TraitType.register("no_offhand", NoOffhand.CODEC);
    public static final TraitType<InventoryLimit> INVENTORY_LIMIT =
            TraitType.register("inventory_limit", InventoryLimit.CODEC);
    public static final TraitType<NoTools> NO_TOOLS = TraitType.register("no_tools", NoTools.CODEC);
    public static final TraitType<NoCrafting> NO_CRAFTING = TraitType.register("no_crafting", NoCrafting.CODEC);
    public static final TraitType<NoBuilding> NO_BUILDING = TraitType.register("no_building", NoBuilding.CODEC);
    public static final TraitType<NoSleep> NO_SLEEP = TraitType.register("no_sleep", NoSleep.CODEC);
    public static final TraitType<Diet> DIET = TraitType.register("diet", Diet.CODEC);
    public static final TraitType<NoEating> NO_EATING = TraitType.register("no_eating", NoEating.CODEC);

    /** Любая черта: поле {@code "type"} выбирает формат остальных полей. */
    public static final Codec<Trait> CODEC = TraitType.CODEC.dispatch("type", Trait::type, TraitType::codec);

    private Traits() {
    }

    private static <T extends Trait> RecordCodecBuilder<T, Stage> stage(Function<T, Stage> getter) {
        return Stage.CODEC.fieldOf("from_stage").forGetter(getter);
    }

    private static <T extends Trait> RecordCodecBuilder<T, Optional<String>> descriptionField(
            Function<T, Optional<String>> getter) {
        return Codec.STRING.optionalFieldOf("description").forGetter(getter);
    }

    private static Component describe(Optional<String> key, Component fallback) {
        return key.<Component>map(Component::translatable).orElse(fallback);
    }

    /** Постоянный модификатор атрибута. */
    public record Attr(Stage minStage, Holder<Attribute> attribute, double amount,
                       AttributeModifier.Operation operation, Optional<String> descriptionKey) implements Trait {
        static final MapCodec<Attr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Attr::minStage),
                BuiltInRegistries.ATTRIBUTE.holderByNameCodec().fieldOf("attribute").forGetter(Attr::attribute),
                Codec.DOUBLE.fieldOf("amount").forGetter(Attr::amount),
                AttributeModifier.Operation.CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE)
                        .forGetter(Attr::operation),
                descriptionField(Attr::descriptionKey)
        ).apply(instance, Attr::new));

        @Override
        public TraitType<?> type() {
            return ATTRIBUTE;
        }

        @Override
        public Component description() {
            String value = operation == AttributeModifier.Operation.ADD_VALUE
                    ? String.format("%+.1f", amount)
                    : String.format("%+.0f%%", amount * 100.0D);
            return describe(descriptionKey, Component.translatable("mutagen.trait.attribute", value,
                    Component.translatable(attribute.value().getDescriptionId())));
        }

        @Override
        public boolean weakness() {
            // Атрибут сам знает, хорошо ли ему расти: меньше здоровья — плохо, меньше урона от падения — хорошо.
            return attribute.value().getStyle(amount > 0.0D) == ChatFormatting.RED;
        }

        @Override
        public void attributes(Stage stage, AttributeSink sink) {
            sink.add(attribute, amount, operation);
        }
    }

    /** Горение на солнце: цена всей нежити. */
    public record SunBurn(Stage minStage) implements Trait {
        static final MapCodec<SunBurn> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(SunBurn::minStage)
        ).apply(instance, SunBurn::new));

        @Override
        public TraitType<?> type() {
            return SUN_BURN;
        }

        @Override
        public Component description() {
            return Component.translatable("mutagen.trait.sun_burn");
        }

        @Override
        public boolean weakness() {
            return true;
        }

        @Override
        public void tick(ServerPlayer player, Stage stage) {
            if (!MutagenConfig.get().sunlightBurn) {
                return;
            }
            ServerLevel level = player.serverLevel();
            if (!level.isDay() || level.isRaining() || player.isInWaterOrRain()) {
                return;
            }
            BlockPos pos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
            if (!level.canSeeSky(pos)) {
                return;
            }
            if (!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                return;
            }
            player.igniteForSeconds(4.0F);
        }
    }

    /** Регенерация в темноте. */
    public record DarknessRegeneration(Stage minStage, int amplifier, int maxLight) implements Trait {
        static final MapCodec<DarknessRegeneration> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(DarknessRegeneration::minStage),
                Codec.intRange(0, 4).optionalFieldOf("amplifier", 0).forGetter(DarknessRegeneration::amplifier),
                Codec.intRange(0, 15).optionalFieldOf("max_light", 7).forGetter(DarknessRegeneration::maxLight)
        ).apply(instance, DarknessRegeneration::new));

        @Override
        public TraitType<?> type() {
            return DARK_REGENERATION;
        }

        @Override
        public Component description() {
            return Component.translatable("mutagen.trait.dark_regeneration");
        }

        @Override
        public void tick(ServerPlayer player, Stage stage) {
            BlockPos pos = player.blockPosition();
            if (player.serverLevel().getMaxLocalRawBrightness(pos) > maxLight) {
                return;
            }
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 45, amplifier, true, false, false));
        }
    }

    /** Иммунитет к набору эффектов. */
    public record EffectImmunity(Stage minStage, List<Holder<MobEffect>> effects,
                                 Optional<String> descriptionKey) implements Trait {
        static final MapCodec<EffectImmunity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(EffectImmunity::minStage),
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().listOf().fieldOf("effects")
                        .forGetter(EffectImmunity::effects),
                descriptionField(EffectImmunity::descriptionKey)
        ).apply(instance, EffectImmunity::new));

        @Override
        public TraitType<?> type() {
            return EFFECT_IMMUNITY;
        }

        @Override
        public Component description() {
            MutableComponent names = Component.empty();
            for (int i = 0; i < effects.size(); i++) {
                if (i > 0) {
                    names.append(", ");
                }
                names.append(effects.get(i).value().getDisplayName());
            }
            return describe(descriptionKey, Component.translatable("mutagen.trait.effect_immunity", names));
        }

        @Override
        public boolean blocksEffect(Holder<MobEffect> effect, Stage stage) {
            return effects.contains(effect);
        }
    }

    /** Множитель урона от определённого типа: иммунитеты и уязвимости. */
    public record DamageFactor(Stage minStage, TagKey<DamageType> damageType, float factor,
                               Optional<String> descriptionKey) implements Trait {
        static final MapCodec<DamageFactor> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(DamageFactor::minStage),
                TagKey.hashedCodec(Registries.DAMAGE_TYPE).fieldOf("damage_type").forGetter(DamageFactor::damageType),
                Codec.floatRange(0.0F, 100.0F).fieldOf("factor").forGetter(DamageFactor::factor),
                descriptionField(DamageFactor::descriptionKey)
        ).apply(instance, DamageFactor::new));

        @Override
        public TraitType<?> type() {
            return DAMAGE_FACTOR;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.damage_factor",
                    String.format("%.1f", factor), damageType.location().getPath()));
        }

        @Override
        public boolean weakness() {
            return factor > 1.0F;
        }

        @Override
        public float modifyIncomingDamage(DamageSource source, float amount, Stage stage) {
            return source.is(damageType) ? amount * factor : amount;
        }
    }

    /** Паника рядом с котами: цена крипера. */
    public record CatFear(Stage minStage, double radius) implements Trait {
        static final MapCodec<CatFear> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(CatFear::minStage),
                Codec.doubleRange(1.0D, 32.0D).optionalFieldOf("radius", 8.0D).forGetter(CatFear::radius)
        ).apply(instance, CatFear::new));

        @Override
        public TraitType<?> type() {
            return CAT_FEAR;
        }

        @Override
        public Component description() {
            return Component.translatable("mutagen.trait.cat_fear");
        }

        @Override
        public boolean weakness() {
            return true;
        }

        @Override
        public void tick(ServerPlayer player, Stage stage) {
            AABB box = player.getBoundingBox().inflate(radius);
            List<Cat> cats = player.serverLevel().getEntitiesOfClass(Cat.class, box, EntitySelector.NO_SPECTATORS);
            List<Ocelot> ocelots = player.serverLevel().getEntitiesOfClass(Ocelot.class, box, EntitySelector.NO_SPECTATORS);
            if (cats.isEmpty() && ocelots.isEmpty()) {
                return;
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 1, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, 0, true, false, true));
        }
    }

    /** На высоких стадиях тело больше не носит броню. */
    public record ArmorRejection(Stage minStage) implements Trait {
        static final MapCodec<ArmorRejection> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(ArmorRejection::minStage)
        ).apply(instance, ArmorRejection::new));

        @Override
        public TraitType<?> type() {
            return ARMOR_REJECTION;
        }

        @Override
        public Component description() {
            return Component.translatable("mutagen.trait.armor_rejection");
        }

        @Override
        public boolean weakness() {
            return true;
        }

        @Override
        public void tick(ServerPlayer player, Stage stage) {
            if (!MutagenConfig.get().humanityPenalties) {
                return;
            }
            boolean rejected = false;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                player.setItemSlot(slot, ItemStack.EMPTY);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                rejected = true;
            }
            if (rejected) {
                player.displayClientMessage(Component.translatable("mutagen.message.armor_rejected"), true);
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                        SoundSource.PLAYERS, 0.6F, 0.7F);
            }
        }
    }

    /** Стрелы носителя бьют сильнее. Применяется при появлении стрелы в мире. */
    public record ArrowPower(Stage minStage, double factor) implements Trait {
        static final MapCodec<ArrowPower> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(ArrowPower::minStage),
                Codec.doubleRange(0.0D, 10.0D).fieldOf("factor").forGetter(ArrowPower::factor)
        ).apply(instance, ArrowPower::new));

        @Override
        public TraitType<?> type() {
            return ARROW_POWER;
        }

        @Override
        public Component description() {
            return Component.translatable("mutagen.trait.arrow_power");
        }

        @Override
        public boolean weakness() {
            return factor < 1.0D;
        }
    }

    // --- Тело без рук. Сами запреты проверяет Restrictions: черты только говорят, что действует. ---

    /** Вторая рука пропадает: из неё ничего не удержать. */
    public record NoOffhand(Stage minStage) implements Trait {
        static final MapCodec<NoOffhand> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(NoOffhand::minStage)
        ).apply(instance, NoOffhand::new));

        @Override
        public TraitType<?> type() {
            return NO_OFFHAND;
        }

        @Override
        public Component description() {
            return Component.translatable("mutagen.trait.no_offhand");
        }

        @Override
        public boolean weakness() {
            return true;
        }
    }

    /** До рюкзака не дотянуться, а на поясе остаётся {@code hotbar} ячеек. */
    public record InventoryLimit(Stage minStage, int hotbar) implements Trait {
        static final MapCodec<InventoryLimit> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(InventoryLimit::minStage),
                Codec.intRange(1, 9).optionalFieldOf("hotbar", 9).forGetter(InventoryLimit::hotbar)
        ).apply(instance, InventoryLimit::new));

        @Override
        public TraitType<?> type() {
            return INVENTORY_LIMIT;
        }

        @Override
        public Component description() {
            return Component.translatable("mutagen.trait.inventory_limit", hotbar);
        }

        @Override
        public boolean weakness() {
            return true;
        }
    }

    /** Инструменты и оружие держать нечем. Какие именно — тег предметов {@code mutagen:needs_hands}. */
    public record NoTools(Stage minStage) implements Trait {
        static final MapCodec<NoTools> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(NoTools::minStage)
        ).apply(instance, NoTools::new));

        @Override
        public TraitType<?> type() {
            return NO_TOOLS;
        }

        @Override
        public Component description() {
            return Component.translatable("mutagen.trait.no_tools");
        }

        @Override
        public boolean weakness() {
            return true;
        }
    }

    /**
     * Мелкая работа недоступна: станки из тега {@code stations} (по умолчанию {@code mutagen:needs_hands}),
     * а с {@code grid} — и сетка крафта в инвентаре.
     */
    public record NoCrafting(Stage minStage, TagKey<Block> stations, boolean grid,
                             Optional<String> descriptionKey) implements Trait {
        static final MapCodec<NoCrafting> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(NoCrafting::minStage),
                TagKey.hashedCodec(Registries.BLOCK).optionalFieldOf("stations", Restrictions.STATIONS)
                        .forGetter(NoCrafting::stations),
                Codec.BOOL.optionalFieldOf("grid", true).forGetter(NoCrafting::grid),
                descriptionField(NoCrafting::descriptionKey)
        ).apply(instance, NoCrafting::new));

        @Override
        public TraitType<?> type() {
            return NO_CRAFTING;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.no_crafting"));
        }

        @Override
        public boolean weakness() {
            return true;
        }
    }

    /** Строить нечем: блоки не ставятся. */
    public record NoBuilding(Stage minStage) implements Trait {
        static final MapCodec<NoBuilding> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(NoBuilding::minStage)
        ).apply(instance, NoBuilding::new));

        @Override
        public TraitType<?> type() {
            return NO_BUILDING;
        }

        @Override
        public Component description() {
            return Component.translatable("mutagen.trait.no_building");
        }

        @Override
        public boolean weakness() {
            return true;
        }
    }

    /** Тело больше не отдыхает: в кровать не лечь. */
    public record NoSleep(Stage minStage) implements Trait {
        static final MapCodec<NoSleep> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(NoSleep::minStage)
        ).apply(instance, NoSleep::new));

        @Override
        public TraitType<?> type() {
            return NO_SLEEP;
        }

        @Override
        public Component description() {
            return Component.translatable("mutagen.trait.no_sleep");
        }

        @Override
        public boolean weakness() {
            return true;
        }
    }

    /** Тело принимает только еду из тега {@code foods}, остальное не лезет. */
    public record Diet(Stage minStage, TagKey<Item> foods, Optional<String> descriptionKey) implements Trait {
        static final MapCodec<Diet> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Diet::minStage),
                TagKey.hashedCodec(Registries.ITEM).fieldOf("foods").forGetter(Diet::foods),
                descriptionField(Diet::descriptionKey)
        ).apply(instance, Diet::new));

        @Override
        public TraitType<?> type() {
            return DIET;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.diet",
                    foods.location().getPath()));
        }

        @Override
        public boolean weakness() {
            return true;
        }
    }

    /**
     * Нечем переваривать: есть нельзя вовсе, а сытость застывает на {@code food_level}. Голодом не умереть,
     * но и естественного лечения нет, если уровень ниже 18.
     */
    public record NoEating(Stage minStage, int foodLevel) implements Trait {
        static final MapCodec<NoEating> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(NoEating::minStage),
                Codec.intRange(1, 20).optionalFieldOf("food_level", 17).forGetter(NoEating::foodLevel)
        ).apply(instance, NoEating::new));

        @Override
        public TraitType<?> type() {
            return NO_EATING;
        }

        @Override
        public Component description() {
            return Component.translatable("mutagen.trait.no_eating");
        }

        @Override
        public boolean weakness() {
            return true;
        }
    }
}
