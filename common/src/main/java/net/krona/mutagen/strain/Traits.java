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
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Trait types available to datapack strains. Strengths and their costs ship together.
 * <p>
 * All trait types have {@code "from_stage"} and an optional {@code "description"} translation key to
 * override the default.
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
    public static final TraitType<PermanentEffect> PERMANENT_EFFECT =
            TraitType.register("permanent_effect", PermanentEffect.CODEC);
    public static final TraitType<MobNeutrality> MOB_NEUTRALITY =
            TraitType.register("mob_neutrality", MobNeutrality.CODEC);
    public static final TraitType<WallClimb> WALL_CLIMB = TraitType.register("wall_climb", WallClimb.CODEC);
    public static final TraitType<WebWalker> WEB_WALKER = TraitType.register("web_walker", WebWalker.CODEC);
    public static final TraitType<WaterDamage> WATER_DAMAGE = TraitType.register("water_damage", WaterDamage.CODEC);
    public static final TraitType<DryOut> DRY_OUT = TraitType.register("dry_out", DryOut.CODEC);
    public static final TraitType<OnHitEffect> ON_HIT_EFFECT = TraitType.register("on_hit_effect", OnHitEffect.CODEC);
    public static final TraitType<Thorns> THORNS = TraitType.register("thorns", Thorns.CODEC);
    public static final TraitType<Fear> FEAR = TraitType.register("fear", Fear.CODEC);
    public static final TraitType<DarkEffect> DARK_EFFECT = TraitType.register("dark_effect", DarkEffect.CODEC);
    public static final TraitType<GrowthAura> GROWTH_AURA = TraitType.register("growth_aura", GrowthAura.CODEC);
    public static final TraitType<Homesick> HOMESICK = TraitType.register("homesick", Homesick.CODEC);
    public static final TraitType<Hoard> HOARD = TraitType.register("hoard", Hoard.CODEC);
    public static final TraitType<Flight> FLIGHT = TraitType.register("flight", Flight.CODEC);
    public static final TraitType<WaterEffect> WATER_EFFECT = TraitType.register("water_effect", WaterEffect.CODEC);
    public static final TraitType<AquaticBody> AQUATIC_BODY = TraitType.register("aquatic_body", AquaticBody.CODEC);
    public static final TraitType<WaterDependence> WATER_DEPENDENCE = TraitType.register("water_dependence", WaterDependence.CODEC);

    /** The {@code "type"} field determines how to decode the remaining trait fields. */
    public static final Codec<Trait> CODEC = TraitType.CODEC.dispatch("type", Trait::type, TraitType::codec);

    private Traits() {
    }

    public record AquaticBody(Stage minStage) implements Trait {
        static final MapCodec<AquaticBody> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(AquaticBody::minStage)).apply(instance, AquaticBody::new));
        @Override public TraitType<?> type() { return AQUATIC_BODY; }
        @Override public Component description() { return Component.translatable("mutagen.trait.aquatic_body"); }
    }

    public record WaterDependence(Stage minStage) implements Trait {
        static final MapCodec<WaterDependence> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(WaterDependence::minStage)).apply(instance, WaterDependence::new));
        @Override public TraitType<?> type() { return WATER_DEPENDENCE; }
        @Override public Component description() { return Component.translatable("mutagen.trait.water_dependence"); }
        @Override public boolean weakness() { return true; }
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

    /** Persistent attribute modifier. */
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
            // The attribute defines which direction is beneficial. Less max health is harmful, while less
            // fall damage is beneficial.
            return attribute.value().getStyle(amount > 0.0D) == ChatFormatting.RED;
        }

        @Override
        public void attributes(Stage stage, AttributeSink sink) {
            sink.add(attribute, amount, operation);
        }
    }

    /** Sunlight burn for undead strains. */
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

    /** Regeneration in darkness. */
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

    /** Immunity to a set of effects. */
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

    /** Damage multiplier for a specified type, including immunity and vulnerability. */
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

    /** Panic near cats for the creeper strain. */
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

    /** Prevent wearing armor at higher stages. */
    public record ArmorRejection(Stage minStage, List<EquipmentSlot> slots,
                                 Optional<String> descriptionKey) implements Trait {
        private static final List<EquipmentSlot> ALL_ARMOR =
                List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

        static final MapCodec<ArmorRejection> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(ArmorRejection::minStage),
                EquipmentSlot.CODEC.listOf().optionalFieldOf("slots", ALL_ARMOR).forGetter(ArmorRejection::slots),
                descriptionField(ArmorRejection::descriptionKey)
        ).apply(instance, ArmorRejection::new));

        @Override
        public TraitType<?> type() {
            return ARMOR_REJECTION;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.armor_rejection"));
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
            for (EquipmentSlot slot : slots) {
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

    /** Increase arrow damage when the arrow enters the world. */
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

    // --- Handless traits declare restrictions that Restrictions enforces. ---

    /** Disable the offhand. */
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

    /** Lock the main inventory and leave {@code hotbar} slots accessible. */
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

    /** Prevent use of tools and weapons tagged {@code mutagen:needs_hands}. */
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
     * Disable stations in {@code stations}, which defaults to {@code mutagen:needs_hands}. Enable {@code
     * grid} to disable the inventory crafting grid too.
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

    /** Prevent placing blocks when the body cannot build. */
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

    /** Prevent sleeping in beds. */
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

    /** Allow only foods in the {@code foods} tag. */
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
     * Disable eating and freeze hunger at {@code food_level}. This prevents starvation, but natural healing
     * still requires at least 18 food points.
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

    /**
     * Refresh the effect every second while the trait is active. Leave enough duration to prevent night
     * vision from flickering.
     */
    public record PermanentEffect(Stage minStage, Holder<MobEffect> effect, int amplifier,
                                  Optional<String> descriptionKey) implements Trait {
        static final MapCodec<PermanentEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(PermanentEffect::minStage),
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(PermanentEffect::effect),
                Codec.intRange(0, 4).optionalFieldOf("amplifier", 0).forGetter(PermanentEffect::amplifier),
                descriptionField(PermanentEffect::descriptionKey)
        ).apply(instance, PermanentEffect::new));

        @Override
        public TraitType<?> type() {
            return PERMANENT_EFFECT;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.permanent_effect",
                    Component.translatable(effect.value().getDescriptionId())));
        }

        @Override
        public void tick(ServerPlayer player, Stage stage) {
            player.addEffect(new MobEffectInstance(effect, 400, amplifier, true, false, true));
        }
    }

    /**
     * The creature species stops attacking once it recognizes the player at the required stage.
     */
    public record MobNeutrality(Stage minStage, Optional<TagKey<EntityType<?>>> kin, List<EntityType<?>> entities,
                                Optional<String> descriptionKey) implements Trait {
        static final MapCodec<MobNeutrality> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(MobNeutrality::minStage),
                TagKey.hashedCodec(Registries.ENTITY_TYPE).optionalFieldOf("kin").forGetter(MobNeutrality::kin),
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf().optionalFieldOf("entities", List.of())
                        .forGetter(MobNeutrality::entities),
                descriptionField(MobNeutrality::descriptionKey)
        ).apply(instance, MobNeutrality::new));

        @Override
        public TraitType<?> type() {
            return MOB_NEUTRALITY;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.mob_neutrality"));
        }

        @Override
        public boolean pacifies(EntityType<?> kind, Stage stage) {
            if (entities.contains(kind)) {
                return true;
            }
            return kin.isPresent() && kind.is(kin.get());
        }
    }

    /** Climb like a spider by pressing against a wall. Crouch to hold position. */
    public record WallClimb(Stage minStage, Optional<String> descriptionKey) implements Trait {
        static final MapCodec<WallClimb> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(WallClimb::minStage),
                descriptionField(WallClimb::descriptionKey)
        ).apply(instance, WallClimb::new));

        @Override
        public TraitType<?> type() {
            return WALL_CLIMB;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.wall_climb"));
        }

        @Override
        public boolean climbs(Stage stage) {
            return true;
        }
    }

    /** Move through cobwebs without getting stuck. */
    public record WebWalker(Stage minStage, Optional<String> descriptionKey) implements Trait {
        static final MapCodec<WebWalker> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(WebWalker::minStage),
                descriptionField(WebWalker::descriptionKey)
        ).apply(instance, WebWalker::new));

        @Override
        public TraitType<?> type() {
            return WEB_WALKER;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.web_walker"));
        }

        @Override
        public boolean walksThroughWebs(Stage stage) {
            return true;
        }
    }

    /** Take damage each second in water and, if {@code rain} is enabled, in rain. */
    public record WaterDamage(Stage minStage, float amount, boolean rain,
                              Optional<String> descriptionKey) implements Trait {
        static final MapCodec<WaterDamage> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(WaterDamage::minStage),
                Codec.floatRange(0.5F, 20.0F).optionalFieldOf("amount", 1.0F).forGetter(WaterDamage::amount),
                Codec.BOOL.optionalFieldOf("rain", true).forGetter(WaterDamage::rain),
                descriptionField(WaterDamage::descriptionKey)
        ).apply(instance, WaterDamage::new));

        @Override
        public TraitType<?> type() {
            return WATER_DAMAGE;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable(
                    rain ? "mutagen.trait.water_damage.rain" : "mutagen.trait.water_damage"));
        }

        @Override
        public boolean weakness() {
            return true;
        }

        @Override
        public void tick(ServerPlayer player, Stage stage) {
            boolean wet = rain ? player.isInWaterRainOrBubble() : player.isInWaterOrBubble();
            if (wet) {
                player.hurt(player.damageSources().drown(), amount);
            }
        }
    }

    /**
     * Dry out on land after {@code grace} seconds. Apply weakness first and start dealing damage after
     * twice that time. Water or rain resets the timer.
     */
    public record DryOut(Stage minStage, int grace, float amount, Optional<String> descriptionKey) implements Trait {
        /**
         * Track consecutive dry seconds and the last check. Reconnecting resets the timer. So does a break
         * in the trait caused by a strain or stage change.
         */
        private static final Map<UUID, long[]> DRY = new ConcurrentHashMap<>();

        static final MapCodec<DryOut> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(DryOut::minStage),
                Codec.intRange(5, 3600).optionalFieldOf("grace", 90).forGetter(DryOut::grace),
                Codec.floatRange(0.5F, 20.0F).optionalFieldOf("amount", 1.0F).forGetter(DryOut::amount),
                descriptionField(DryOut::descriptionKey)
        ).apply(instance, DryOut::new));

        @Override
        public TraitType<?> type() {
            return DRY_OUT;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.dry_out", grace));
        }

        @Override
        public boolean weakness() {
            return true;
        }

        @Override
        public void tick(ServerPlayer player, Stage stage) {
            UUID id = player.getUUID();
            if (player.isInWaterRainOrBubble()) {
                DRY.remove(id);
                return;
            }
            long now = player.serverLevel().getGameTime();
            long[] entry = DRY.computeIfAbsent(id, key -> new long[]{0L, now});
            if (now - entry[1] > 40L) {
                entry[0] = 0L;
            }
            entry[0]++;
            entry[1] = now;
            long dry = entry[0];
            if (dry < grace) {
                return;
            }
            if (dry == grace) {
                player.displayClientMessage(Component.translatable("mutagen.message.drying"), true);
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 0, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, 0, true, false, true));
            if (dry >= grace * 2) {
                player.hurt(player.damageSources().dryOut(), amount);
            }
        }
    }

    /**
     * Melee applies an effect such as venom, wither, or slowness. With {@code self_damage}, each hit also
     * costs health without killing the player.
     */
    public record OnHitEffect(Stage minStage, Holder<MobEffect> effect, int duration, int amplifier,
                              float selfDamage, Optional<String> descriptionKey) implements Trait {
        static final MapCodec<OnHitEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(OnHitEffect::minStage),
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(OnHitEffect::effect),
                Codec.intRange(1, 1200).optionalFieldOf("duration", 100).forGetter(OnHitEffect::duration),
                Codec.intRange(0, 4).optionalFieldOf("amplifier", 0).forGetter(OnHitEffect::amplifier),
                Codec.floatRange(0.0F, 20.0F).optionalFieldOf("self_damage", 0.0F).forGetter(OnHitEffect::selfDamage),
                descriptionField(OnHitEffect::descriptionKey)
        ).apply(instance, OnHitEffect::new));

        @Override
        public TraitType<?> type() {
            return ON_HIT_EFFECT;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.on_hit_effect",
                    Component.translatable(effect.value().getDescriptionId())));
        }

        @Override
        public void onMeleeHit(ServerPlayer player, LivingEntity target, Stage stage) {
            target.addEffect(new MobEffectInstance(effect, duration, amplifier), player);
            if (selfDamage > 0.0F && !player.getAbilities().instabuild) {
                // Avoid hurt because it would also grant half a second of invulnerability.
                player.setHealth(Math.max(1.0F, player.getHealth() - selfDamage));
            }
        }
    }

    /** Thorns deal damage back to melee attackers. */
    public record Thorns(Stage minStage, float damage, Optional<String> descriptionKey) implements Trait {
        static final MapCodec<Thorns> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Thorns::minStage),
                Codec.floatRange(0.5F, 20.0F).optionalFieldOf("damage", 2.0F).forGetter(Thorns::damage),
                descriptionField(Thorns::descriptionKey)
        ).apply(instance, Thorns::new));

        @Override
        public TraitType<?> type() {
            return THORNS;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.thorns"));
        }

        @Override
        public void onMeleeHurt(ServerPlayer player, LivingEntity attacker, Stage stage) {
            attacker.hurt(player.damageSources().thorns(player), damage);
        }
    }

    /** Nearby species on the list cause weakness and slowness. */
    public record Fear(Stage minStage, List<EntityType<?>> entities, double radius,
                       Optional<String> descriptionKey) implements Trait {
        static final MapCodec<Fear> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Fear::minStage),
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf().fieldOf("entities").forGetter(Fear::entities),
                Codec.doubleRange(1.0D, 32.0D).optionalFieldOf("radius", 8.0D).forGetter(Fear::radius),
                descriptionField(Fear::descriptionKey)
        ).apply(instance, Fear::new));

        @Override
        public TraitType<?> type() {
            return FEAR;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.fear"));
        }

        @Override
        public boolean weakness() {
            return true;
        }

        @Override
        public void tick(ServerPlayer player, Stage stage) {
            AABB box = player.getBoundingBox().inflate(radius);
            List<Entity> near = player.serverLevel().getEntities(player, box,
                    entity -> entity.isAlive() && entities.contains(entity.getType()));
            if (near.isEmpty()) {
                return;
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, 1, true, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 45, 0, true, false, true));
        }
    }

    /** Apply an effect only in darkness, such as phantom invisibility or a nocturnal predator's strength. */
    public record DarkEffect(Stage minStage, Holder<MobEffect> effect, int amplifier, int maxLight,
                             Optional<String> descriptionKey) implements Trait {
        static final MapCodec<DarkEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(DarkEffect::minStage),
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(DarkEffect::effect),
                Codec.intRange(0, 4).optionalFieldOf("amplifier", 0).forGetter(DarkEffect::amplifier),
                Codec.intRange(0, 15).optionalFieldOf("max_light", 7).forGetter(DarkEffect::maxLight),
                descriptionField(DarkEffect::descriptionKey)
        ).apply(instance, DarkEffect::new));

        @Override
        public TraitType<?> type() {
            return DARK_EFFECT;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.dark_effect",
                    Component.translatable(effect.value().getDescriptionId())));
        }

        @Override
        public void tick(ServerPlayer player, Stage stage) {
            if (player.serverLevel().getMaxLocalRawBrightness(player.blockPosition()) > maxLight) {
                return;
            }
            player.addEffect(new MobEffectInstance(effect, 45, amplifier, true, false, true));
        }
    }

    /**
     * Accelerate nearby crop and sapling growth. Leave grass and flowers alone so the effect does not
     * behave like bone meal on the ground.
     */
    public record GrowthAura(Stage minStage, int radius, int tries, Optional<String> descriptionKey) implements Trait {
        static final MapCodec<GrowthAura> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(GrowthAura::minStage),
                Codec.intRange(1, 12).optionalFieldOf("radius", 5).forGetter(GrowthAura::radius),
                Codec.intRange(1, 64).optionalFieldOf("tries", 6).forGetter(GrowthAura::tries),
                descriptionField(GrowthAura::descriptionKey)
        ).apply(instance, GrowthAura::new));

        @Override
        public TraitType<?> type() {
            return GROWTH_AURA;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.growth_aura"));
        }

        @Override
        public void tick(ServerPlayer player, Stage stage) {
            ServerLevel level = player.serverLevel();
            RandomSource random = level.getRandom();
            BlockPos origin = player.blockPosition();
            for (int i = 0; i < tries; i++) {
                BlockPos pos = origin.offset(random.nextInt(radius * 2 + 1) - radius,
                        random.nextInt(5) - 2, random.nextInt(radius * 2 + 1) - radius);
                BlockState state = level.getBlockState(pos);
                if (!state.is(BlockTags.CROPS) && !state.is(BlockTags.SAPLINGS)) {
                    continue;
                }
                if (state.getBlock() instanceof BonemealableBlock plant
                        && plant.isValidBonemealTarget(level, pos, state)
                        && plant.isBonemealSuccess(level, random, pos, state)) {
                    plant.performBonemeal(level, random, pos, state);
                    level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, 0);
                }
            }
        }
    }

    /** Weaken the player outside the strain's home dimension. */
    public record Homesick(Stage minStage, ResourceKey<Level> dimension, List<Holder<MobEffect>> effects,
                           Optional<String> descriptionKey) implements Trait {
        static final MapCodec<Homesick> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Homesick::minStage),
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Homesick::dimension),
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().listOf().fieldOf("effects")
                        .forGetter(Homesick::effects),
                descriptionField(Homesick::descriptionKey)
        ).apply(instance, Homesick::new));

        @Override
        public TraitType<?> type() {
            return HOMESICK;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.homesick"));
        }

        @Override
        public boolean weakness() {
            return true;
        }

        @Override
        public void tick(ServerPlayer player, Stage stage) {
            if (player.level().dimension() == dimension) {
                return;
            }
            for (Holder<MobEffect> effect : effects) {
                player.addEffect(new MobEffectInstance(effect, 45, 0, true, false, true));
            }
        }
    }

    /** Block dropping tagged items. They can still go into chests and drop on death. */
    public record Hoard(Stage minStage, TagKey<Item> items, Optional<String> descriptionKey) implements Trait {
        static final MapCodec<Hoard> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Hoard::minStage),
                TagKey.hashedCodec(Registries.ITEM).fieldOf("items").forGetter(Hoard::items),
                descriptionField(Hoard::descriptionKey)
        ).apply(instance, Hoard::new));

        @Override
        public TraitType<?> type() {
            return HOARD;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.hoard"));
        }

        @Override
        public boolean weakness() {
            return true;
        }

        @Override
        public boolean hoards(ItemStack stack, Stage stage) {
            return stack.is(items);
        }
    }

    /**
     * Double-jump to fly as in creative mode. Flight consumes hunger faster than sprinting and requires at
     * least {@code min_food}. Without {@code rain}, water and rain prevent flight. {@link
     * net.krona.mutagen.mutation.Flight} grants and revokes permission.
     */
    public record Flight(Stage minStage, float speed, float exhaustion, int minFood, boolean rain,
                         Optional<String> descriptionKey) implements Trait {
        static final MapCodec<Flight> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Flight::minStage),
                Codec.floatRange(0.01F, 0.3F).optionalFieldOf("speed", 0.05F).forGetter(Flight::speed),
                Codec.floatRange(0.0F, 4.0F).optionalFieldOf("exhaustion", 0.3F).forGetter(Flight::exhaustion),
                Codec.intRange(0, 20).optionalFieldOf("min_food", 6).forGetter(Flight::minFood),
                Codec.BOOL.optionalFieldOf("rain", true).forGetter(Flight::rain),
                descriptionField(Flight::descriptionKey)
        ).apply(instance, Flight::new));

        @Override
        public TraitType<?> type() {
            return FLIGHT;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable(rain ? "mutagen.trait.flight"
                    : "mutagen.trait.flight.dry"));
        }
    }

    /** Apply an effect while the player is in water, such as dolphin speed or underwater vision. */
    public record WaterEffect(Stage minStage, Holder<MobEffect> effect, int amplifier,
                              Optional<String> descriptionKey) implements Trait {
        static final MapCodec<WaterEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(WaterEffect::minStage),
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(WaterEffect::effect),
                Codec.intRange(0, 4).optionalFieldOf("amplifier", 0).forGetter(WaterEffect::amplifier),
                descriptionField(WaterEffect::descriptionKey)
        ).apply(instance, WaterEffect::new));

        @Override
        public TraitType<?> type() {
            return WATER_EFFECT;
        }

        @Override
        public Component description() {
            return describe(descriptionKey, Component.translatable("mutagen.trait.water_effect",
                    Component.translatable(effect.value().getDescriptionId())));
        }

        @Override
        public void tick(ServerPlayer player, Stage stage) {
            if (player.isInWater()) {
                // Keep more than one second of effect duration to prevent flickering between checks.
                player.addEffect(new MobEffectInstance(effect, 45, amplifier, true, false, true));
            }
        }
    }
}
