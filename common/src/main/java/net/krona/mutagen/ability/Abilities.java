package net.krona.mutagen.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.krona.mutagen.strain.Stage;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Active ability types that datapacks can use to build strains.
 * <p>
 * All abilities share {@code "from_stage"}, {@code "cooldown"} in ticks, {@code "cost"} ({@code hunger} and
 * {@code health}), {@code "apex"}, and the translation key {@code "name"}.
 */
public final class Abilities {
    private static final Codec<Ability.Cost> COST_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.floatRange(0.0F, 20.0F).optionalFieldOf("hunger", 0.0F).forGetter(Ability.Cost::hunger),
            Codec.floatRange(0.0F, 20.0F).optionalFieldOf("health", 0.0F).forGetter(Ability.Cost::health)
    ).apply(instance, Ability.Cost::new));

    public static final AbilityType<EffectBurst> EFFECT_BURST =
            AbilityType.register("effect_burst", EffectBurst.CODEC);
    public static final AbilityType<Leap> LEAP = AbilityType.register("leap", Leap.CODEC);
    public static final AbilityType<Shockwave> SHOCKWAVE = AbilityType.register("shockwave", Shockwave.CODEC);
    public static final AbilityType<Volley> VOLLEY = AbilityType.register("volley", Volley.CODEC);
    public static final AbilityType<Rally> RALLY = AbilityType.register("rally", Rally.CODEC);
    public static final AbilityType<Reveal> REVEAL = AbilityType.register("reveal", Reveal.CODEC);
    public static final AbilityType<Blink> BLINK = AbilityType.register("blink", Blink.CODEC);
    public static final AbilityType<Hex> HEX = AbilityType.register("hex", Hex.CODEC);
    public static final AbilityType<Fireball> FIREBALL = AbilityType.register("fireball", Fireball.CODEC);
    public static final AbilityType<Vortex> VORTEX = AbilityType.register("vortex", Vortex.CODEC);
    public static final AbilityType<Lunge> LUNGE = AbilityType.register("lunge", Lunge.CODEC);

    /** The {@code "type"} field determines how to read the remaining ability fields. */
    private static final MapCodec<Ability> RAW = AbilityType.CODEC.dispatchMap("type", Ability::type, AbilityType::codec);
    public static final Codec<Ability> CODEC = RecordCodecBuilder.<Conditioned>create(instance -> instance.group(
            RAW.forGetter(Conditioned::delegate),
            AbilityRequirement.CODEC.optionalFieldOf("requires", AbilityRequirement.ANY).forGetter(Conditioned::requirement)
    ).apply(instance, Conditioned::new)).xmap(value -> (Ability)value,
            value -> value instanceof Conditioned conditioned ? conditioned : new Conditioned(value, value.requirement()));

    private record Conditioned(Ability delegate, AbilityRequirement requirement) implements Ability {
        public AbilityType<?> type() { return delegate.type(); }
        public Stage minStage() { return delegate.minStage(); }
        public Component name() { return delegate.name(); }
        public int cooldown() { return delegate.cooldown(); }
        public Ability.Cost cost() { return delegate.cost(); }
        public boolean apex() { return delegate.apex(); }
        public boolean activate(ServerPlayer player, Stage stage) { return delegate.activate(player, stage); }
    }

    private Abilities() {
    }

    private static <T extends Ability> RecordCodecBuilder<T, Stage> stage(Function<T, Stage> getter) {
        return Stage.CODEC.fieldOf("from_stage").forGetter(getter);
    }

    private static <T extends Ability> RecordCodecBuilder<T, String> nameField(Function<T, String> getter) {
        return Codec.STRING.fieldOf("name").forGetter(getter);
    }

    private static <T extends Ability> RecordCodecBuilder<T, Integer> cooldownField(Function<T, Integer> getter) {
        return Codec.intRange(0, 72000).optionalFieldOf("cooldown", 200).forGetter(getter);
    }

    private static <T extends Ability> RecordCodecBuilder<T, Ability.Cost> costField(
            Function<T, Ability.Cost> getter) {
        return COST_CODEC.optionalFieldOf("cost", Ability.Cost.FREE).forGetter(getter);
    }

    private static <T extends Ability> RecordCodecBuilder<T, Boolean> apexField(Function<T, Boolean> getter) {
        return Codec.BOOL.optionalFieldOf("apex", false).forGetter(getter);
    }

    /** Apply effects to the player, such as a burst of regeneration, night vision, or resistance. */
    public record EffectBurst(Stage minStage, String nameKey, int cooldown, Ability.Cost cost, boolean apex,
                              List<Holder<MobEffect>> effects, int duration, int amplifier) implements Ability {
        static final MapCodec<EffectBurst> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(EffectBurst::minStage),
                nameField(EffectBurst::nameKey),
                cooldownField(EffectBurst::cooldown),
                costField(EffectBurst::cost),
                apexField(EffectBurst::apex),
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().listOf().fieldOf("effects")
                        .forGetter(EffectBurst::effects),
                Codec.intRange(1, 72000).optionalFieldOf("duration", 200).forGetter(EffectBurst::duration),
                Codec.intRange(0, 4).optionalFieldOf("amplifier", 0).forGetter(EffectBurst::amplifier)
        ).apply(instance, EffectBurst::new));

        @Override
        public AbilityType<?> type() {
            return EFFECT_BURST;
        }

        @Override
        public Component name() {
            return Component.translatable(nameKey);
        }

        @Override
        public boolean activate(ServerPlayer player, Stage stage) {
            for (Holder<MobEffect> effect : effects) {
                player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true, true));
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW,
                    SoundSource.PLAYERS, 0.7F, 1.4F);
            return true;
        }
    }

    /** Dash in the direction the player is looking. */
    public record Leap(Stage minStage, String nameKey, int cooldown, Ability.Cost cost, boolean apex,
                       double power, double lift, List<Holder<MobEffect>> effects, int duration,
                       int amplifier) implements Ability {
        static final MapCodec<Leap> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Leap::minStage),
                nameField(Leap::nameKey),
                cooldownField(Leap::cooldown),
                costField(Leap::cost),
                apexField(Leap::apex),
                Codec.doubleRange(0.1D, 4.0D).optionalFieldOf("power", 1.2D).forGetter(Leap::power),
                Codec.doubleRange(0.0D, 2.0D).optionalFieldOf("lift", 0.35D).forGetter(Leap::lift),
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().listOf().optionalFieldOf("effects", List.of())
                        .forGetter(Leap::effects),
                Codec.intRange(1, 72000).optionalFieldOf("duration", 100).forGetter(Leap::duration),
                Codec.intRange(0, 4).optionalFieldOf("amplifier", 0).forGetter(Leap::amplifier)
        ).apply(instance, Leap::new));

        @Override
        public AbilityType<?> type() {
            return LEAP;
        }

        @Override
        public Component name() {
            return Component.translatable(nameKey);
        }

        @Override
        public boolean activate(ServerPlayer player, Stage stage) {
            Vec3 look = player.getLookAngle().normalize();
            player.setDeltaMovement(look.x * power, (player.isInWater() ? look.y * power : Math.max(look.y * power, 0.0D)) + lift, look.z * power);
            // Without this, the server treats the movement as invalid and teleports the player back.
            player.hurtMarked = true;
            for (Holder<MobEffect> effect : effects) {
                player.addEffect(new MobEffectInstance(effect, duration, amplifier, false, true, true));
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP,
                    SoundSource.PLAYERS, 0.8F, 1.1F);
            return true;
        }
    }

    /** Damage and knock back nearby targets, sometimes at the cost of the player's health. */
    public record Shockwave(Stage minStage, String nameKey, int cooldown, Ability.Cost cost, boolean apex,
                            double radius, float damage, double knockback, int ignite) implements Ability {
        static final MapCodec<Shockwave> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Shockwave::minStage),
                nameField(Shockwave::nameKey),
                cooldownField(Shockwave::cooldown),
                costField(Shockwave::cost),
                apexField(Shockwave::apex),
                Codec.doubleRange(1.0D, 16.0D).optionalFieldOf("radius", 4.0D).forGetter(Shockwave::radius),
                Codec.floatRange(0.0F, 40.0F).optionalFieldOf("damage", 4.0F).forGetter(Shockwave::damage),
                Codec.doubleRange(0.0D, 4.0D).optionalFieldOf("knockback", 1.0D).forGetter(Shockwave::knockback),
                Codec.intRange(0, 30).optionalFieldOf("ignite", 0).forGetter(Shockwave::ignite)
        ).apply(instance, Shockwave::new));

        @Override
        public AbilityType<?> type() {
            return SHOCKWAVE;
        }

        @Override
        public Component name() {
            return Component.translatable(nameKey);
        }

        @Override
        public boolean activate(ServerPlayer player, Stage stage) {
            ServerLevel level = player.serverLevel();
            AABB box = player.getBoundingBox().inflate(radius);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box,
                    entity -> AbilityTargets.valid(player, entity, radius));

            if (targets.isEmpty()) {
                // A shockwave with no targets spends neither resources nor cooldown. The creeper ability
                // costs three hearts.
                player.displayClientMessage(Component.translatable("mutagen.ability.no_target"), true);
                return false;
            }

            for (LivingEntity target : targets) {
                if (damage > 0.0F) {
                    target.hurt(player.damageSources().playerAttack(player), damage);
                }
                if (knockback > 0.0D) {
                    double dx = target.getX() - player.getX();
                    double dz = target.getZ() - player.getZ();
                    target.knockback(knockback, -dx, -dz);
                }
                if (ignite > 0) {
                    target.igniteForSeconds(ignite);
                }
            }

            level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.5D, player.getZ(),
                    8, radius * 0.3D, 0.2D, radius * 0.3D, 0.0D);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 0.9F, 1.3F);
            return true;
        }
    }

    /** Fire a spread of arrows that players cannot pick up. */
    public record Volley(Stage minStage, String nameKey, int cooldown, Ability.Cost cost, boolean apex,
                         int arrows, float spread, float velocity, double damage) implements Ability {
        static final MapCodec<Volley> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Volley::minStage),
                nameField(Volley::nameKey),
                cooldownField(Volley::cooldown),
                costField(Volley::cost),
                apexField(Volley::apex),
                Codec.intRange(1, 16).optionalFieldOf("arrows", 3).forGetter(Volley::arrows),
                Codec.floatRange(0.0F, 45.0F).optionalFieldOf("spread", 8.0F).forGetter(Volley::spread),
                Codec.floatRange(0.5F, 5.0F).optionalFieldOf("velocity", 2.6F).forGetter(Volley::velocity),
                Codec.doubleRange(0.5D, 20.0D).optionalFieldOf("damage", 2.0D).forGetter(Volley::damage)
        ).apply(instance, Volley::new));

        @Override
        public AbilityType<?> type() {
            return VOLLEY;
        }

        @Override
        public Component name() {
            return Component.translatable(nameKey);
        }

        @Override
        public boolean activate(ServerPlayer player, Stage stage) {
            ServerLevel level = player.serverLevel();
            float middle = (arrows - 1) / 2.0F;

            for (int index = 0; index < arrows; index++) {
                Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
                arrow.setBaseDamage(damage);
                // Prevent pickup so the volley cannot replenish the player's arrows.
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.shoot(player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z,
                        velocity, 0.0F);
                // Spread the arrows around the player's look direction.
                float angle = (index - middle) * spread;
                Vec3 motion = arrow.getDeltaMovement().yRot((float) Math.toRadians(-angle));
                arrow.setDeltaMovement(motion);
                level.addFreshEntity(arrow);
            }

            level.playSound(null, player.blockPosition(), SoundEvents.SKELETON_SHOOT,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        }
    }

    /** Call nearby creatures of the same kind to attack whoever hit the player. */
    public record Rally(Stage minStage, String nameKey, int cooldown, Ability.Cost cost, boolean apex,
                        Optional<TagKey<EntityType<?>>> kin, List<EntityType<?>> entities, double radius,
                        int duration) implements Ability {
        static final MapCodec<Rally> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Rally::minStage),
                nameField(Rally::nameKey),
                cooldownField(Rally::cooldown),
                costField(Rally::cost),
                apexField(Rally::apex),
                TagKey.hashedCodec(Registries.ENTITY_TYPE).optionalFieldOf("kin").forGetter(Rally::kin),
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf().optionalFieldOf("entities", List.of())
                        .forGetter(Rally::entities),
                Codec.doubleRange(4.0D, 64.0D).optionalFieldOf("radius", 16.0D).forGetter(Rally::radius),
                Codec.intRange(20, 12000).optionalFieldOf("duration", 600).forGetter(Rally::duration)
        ).apply(instance, Rally::new));

        @Override
        public AbilityType<?> type() {
            return RALLY;
        }

        @Override
        public Component name() {
            return Component.translatable(nameKey);
        }

        @Override
        public boolean activate(ServerPlayer player, Stage stage) {
            LivingEntity hurtBy = player.getLastHurtByMob();
            final LivingEntity enemy = hurtBy != null ? hurtBy : player.getLastHurtMob();
            if (enemy == null || !AbilityTargets.valid(player, enemy, radius)) {
                player.displayClientMessage(Component.translatable("mutagen.ability.no_target"), true);
                return false;
            }

            ServerLevel level = player.serverLevel();
            AABB box = player.getBoundingBox().inflate(radius);
            List<Mob> kinsmen = level.getEntitiesOfClass(Mob.class, box, mob -> mob != enemy
                    && mob.isAlive() && player.distanceToSqr(mob) <= radius * radius
                    && player.hasLineOfSight(mob)
                    && (!(mob instanceof net.minecraft.world.entity.TamableAnimal pet)
                        || !pet.isTame() || pet.isOwnedBy(player))
                    && !mob.isAlliedTo(enemy)
                    && answers(mob));
            if (kinsmen.isEmpty()) {
                player.displayClientMessage(Component.translatable("mutagen.ability.no_kin"), true);
                return false;
            }

            for (Mob kinsman : kinsmen) {
                kinsman.setTarget(enemy);
                kinsman.setLastHurtByMob(enemy);
                // Neutral mobs like wolves, bees, and zombified piglins attack only when angry.
                // Brain-driven mobs like piglins also need an attack target in memory.
                if (kinsman instanceof NeutralMob neutral) {
                    neutral.setPersistentAngerTarget(enemy.getUUID());
                    neutral.startPersistentAngerTimer();
                }
                Brain<?> brain = kinsman.getBrain();
                if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
                    brain.setMemory(MemoryModuleType.ATTACK_TARGET, enemy);
                }
                if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED)) {
                    brain.setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, enemy.getUUID(), duration);
                }
                kinsman.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 0, false, false, false));
            }

            level.playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_AMBIENT,
                    SoundSource.PLAYERS, 1.2F, 0.6F);
            player.displayClientMessage(Component.translatable("mutagen.ability.rallied", kinsmen.size()), true);
            return true;
        }

        private boolean answers(Mob mob) {
            if (kin.isEmpty() && entities.isEmpty()) {
                return true;
            }
            return entities.contains(mob.getType()) || (kin.isPresent() && mob.getType().is(kin.get()));
        }
    }

    /** Make nearby living entities glow through walls. */
    public record Reveal(Stage minStage, String nameKey, int cooldown, Ability.Cost cost, boolean apex,
                         double radius, int duration) implements Ability {
        static final MapCodec<Reveal> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Reveal::minStage),
                nameField(Reveal::nameKey),
                cooldownField(Reveal::cooldown),
                costField(Reveal::cost),
                apexField(Reveal::apex),
                Codec.doubleRange(4.0D, 64.0D).optionalFieldOf("radius", 24.0D).forGetter(Reveal::radius),
                Codec.intRange(20, 12000).optionalFieldOf("duration", 300).forGetter(Reveal::duration)
        ).apply(instance, Reveal::new));

        @Override
        public AbilityType<?> type() {
            return REVEAL;
        }

        @Override
        public Component name() {
            return Component.translatable(nameKey);
        }

        @Override
        public boolean activate(ServerPlayer player, Stage stage) {
            ServerLevel level = player.serverLevel();
            AABB box = player.getBoundingBox().inflate(radius);
            List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box,
                    entity -> entity != player && EntitySelector.NO_SPECTATORS.test(entity));

            if (found.isEmpty()) {
                // Sensing nothing costs no resources and starts no cooldown.
                player.displayClientMessage(Component.translatable("mutagen.ability.no_target"), true);
                return false;
            }

            for (LivingEntity entity : found) {
                entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, false, false));
            }

            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.PLAYERS, 0.5F, 1.8F);
            player.displayClientMessage(Component.translatable("mutagen.ability.revealed", found.size()), true);
            return true;
        }
    }

    /** Find the first living target along the player's line of sight that a wall does not obstruct. */
    @Nullable
    static LivingEntity lookTarget(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult wall = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
                entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator()
                        && entity.isPickable(),
                eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity living && AbilityTargets.valid(player, living, range) ? living : null;
    }

    /** Draw particles from the player's eyes to the target to show where the attack came from. */
    static void trail(ServerLevel level, Vec3 from, Vec3 to, ParticleType<?> particle) {
        if (!(particle instanceof SimpleParticleType simple)) {
            return;
        }
        Vec3 step = to.subtract(from);
        int points = Math.max(2, (int) (step.length() * 2.0D));
        for (int i = 0; i <= points; i++) {
            Vec3 at = from.add(step.scale(i / (double) points));
            level.sendParticles(simple, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Teleport along the player's line of sight, up to {@code range} and clear of walls. */
    public record Blink(Stage minStage, String nameKey, int cooldown, Ability.Cost cost, boolean apex,
                        double range, List<Holder<MobEffect>> effects, int duration) implements Ability {
        static final MapCodec<Blink> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Blink::minStage),
                nameField(Blink::nameKey),
                cooldownField(Blink::cooldown),
                costField(Blink::cost),
                apexField(Blink::apex),
                Codec.doubleRange(2.0D, 64.0D).optionalFieldOf("range", 16.0D).forGetter(Blink::range),
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().listOf().optionalFieldOf("effects", List.of())
                        .forGetter(Blink::effects),
                Codec.intRange(1, 72000).optionalFieldOf("duration", 60).forGetter(Blink::duration)
        ).apply(instance, Blink::new));

        @Override
        public AbilityType<?> type() {
            return BLINK;
        }

        @Override
        public Component name() {
            return Component.translatable(nameKey);
        }

        @Override
        public boolean activate(ServerPlayer player, Stage stage) {
            ServerLevel level = player.serverLevel();
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0F);
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

            Vec3 aim;
            if (hit.getType() == HitResult.Type.MISS) {
                aim = eye.add(look.scale(range));
            } else if (hit.getDirection() == Direction.UP) {
                // When aiming at the floor, land on the targeted block.
                aim = hit.getLocation();
            } else {
                // When aiming at a wall or ceiling, land just in front of it.
                aim = hit.getLocation().subtract(look.scale(0.6D));
            }

            Vec3 destination = safeSpot(player, aim);
            if (destination == null || destination.distanceToSqr(player.position()) < 4.0D) {
                player.displayClientMessage(Component.translatable("mutagen.ability.no_room"), true);
                return false;
            }

            Vec3 from = player.position();
            level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0D, from.z, 32, 0.3D, 0.8D, 0.3D, 0.2D);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                    1.0F, 1.0F);

            player.teleportTo(destination.x, destination.y, destination.z);
            player.resetFallDistance();
            for (Holder<MobEffect> effect : effects) {
                player.addEffect(new MobEffectInstance(effect, duration, 0, false, true, true));
            }

            level.sendParticles(ParticleTypes.REVERSE_PORTAL, destination.x, destination.y + 1.0D, destination.z,
                    32, 0.3D, 0.8D, 0.3D, 0.05D);
            level.playSound(null, destination.x, destination.y, destination.z, SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.PLAYERS, 1.0F, 1.2F);
            return true;
        }

        /** Find the closest position that fits the player at, above, or slightly below the target point. */
        @Nullable
        private static Vec3 safeSpot(ServerPlayer player, Vec3 aim) {
            ServerLevel level = player.serverLevel();
            double y = Math.floor(aim.y);
            for (int dy : new int[]{0, 1, -1, 2, 3}) {
                Vec3 feet = new Vec3(aim.x, y + dy, aim.z);
                AABB body = player.getBoundingBox().move(feet.subtract(player.position()));
                if (level.noCollision(player, body) && !level.containsAnyLiquid(body)
                        && level.isInWorldBounds(BlockPos.containing(feet))) {
                    return feet;
                }
            }
            return null;
        }
    }

    /**
     * Ranged attack used for the guardian beam, spider web, or enderman stare. Hits the target in sight or
     * every nearby target when {@code radius} is set. Magic damage is credited to the player.
     */
    public record Hex(Stage minStage, String nameKey, int cooldown, Ability.Cost cost, boolean apex,
                      double range, double radius, float damage, List<Holder<MobEffect>> effects, int duration,
                      int amplifier, ParticleType<?> particle, Optional<SoundEvent> sound) implements Ability {
        static final MapCodec<Hex> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Hex::minStage),
                nameField(Hex::nameKey),
                cooldownField(Hex::cooldown),
                costField(Hex::cost),
                apexField(Hex::apex),
                Codec.doubleRange(1.0D, 64.0D).optionalFieldOf("range", 16.0D).forGetter(Hex::range),
                Codec.doubleRange(0.0D, 48.0D).optionalFieldOf("radius", 0.0D).forGetter(Hex::radius),
                Codec.floatRange(0.0F, 40.0F).optionalFieldOf("damage", 0.0F).forGetter(Hex::damage),
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().listOf().optionalFieldOf("effects", List.of())
                        .forGetter(Hex::effects),
                Codec.intRange(1, 72000).optionalFieldOf("duration", 100).forGetter(Hex::duration),
                Codec.intRange(0, 4).optionalFieldOf("amplifier", 0).forGetter(Hex::amplifier),
                BuiltInRegistries.PARTICLE_TYPE.byNameCodec().optionalFieldOf("particle", ParticleTypes.CRIT)
                        .forGetter(Hex::particle),
                BuiltInRegistries.SOUND_EVENT.byNameCodec().optionalFieldOf("sound").forGetter(Hex::sound)
        ).apply(instance, Hex::new));

        @Override
        public AbilityType<?> type() {
            return HEX;
        }

        @Override
        public Component name() {
            return Component.translatable(nameKey);
        }

        @Override
        public boolean activate(ServerPlayer player, Stage stage) {
            ServerLevel level = player.serverLevel();
            List<LivingEntity> targets = new ArrayList<>();
            if (radius > 0.0D) {
                targets.addAll(level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                        entity -> AbilityTargets.valid(player, entity, radius)));
            } else {
                LivingEntity target = lookTarget(player, range);
                if (target != null) {
                    targets.add(target);
                }
            }
            if (targets.isEmpty()) {
                player.displayClientMessage(Component.translatable("mutagen.ability.no_target"), true);
                return false;
            }

            Vec3 eye = player.getEyePosition();
            for (LivingEntity target : targets) {
                if (radius <= 0.0D) {
                    trail(level, eye, target.getEyePosition(), particle);
                } else if (particle instanceof SimpleParticleType simple) {
                    level.sendParticles(simple, target.getX(), target.getY(0.5D), target.getZ(), 8,
                            0.3D, 0.4D, 0.3D, 0.02D);
                }
                if (damage > 0.0F) {
                    target.hurt(player.damageSources().indirectMagic(player, player), damage);
                }
                for (Holder<MobEffect> effect : effects) {
                    target.addEffect(new MobEffectInstance(effect, duration, amplifier), player);
                }
            }

            level.playSound(null, player.blockPosition(), sound.orElse(SoundEvents.EVOKER_CAST_SPELL),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        }
    }

    /** Fire one blaze fireball or a spread. They set targets on fire like vanilla fireballs. */
    public record Fireball(Stage minStage, String nameKey, int cooldown, Ability.Cost cost, boolean apex,
                           int count, float spread) implements Ability {
        static final MapCodec<Fireball> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Fireball::minStage),
                nameField(Fireball::nameKey),
                cooldownField(Fireball::cooldown),
                costField(Fireball::cost),
                apexField(Fireball::apex),
                Codec.intRange(1, 12).optionalFieldOf("count", 1).forGetter(Fireball::count),
                Codec.floatRange(0.0F, 45.0F).optionalFieldOf("spread", 6.0F).forGetter(Fireball::spread)
        ).apply(instance, Fireball::new));

        @Override
        public AbilityType<?> type() {
            return FIREBALL;
        }

        @Override
        public Component name() {
            return Component.translatable(nameKey);
        }

        @Override
        public boolean activate(ServerPlayer player, Stage stage) {
            ServerLevel level = player.serverLevel();
            Vec3 look = player.getViewVector(1.0F);
            Vec3 muzzle = player.getEyePosition().add(look.scale(1.0D));
            float middle = (count - 1) / 2.0F;
            for (int index = 0; index < count; index++) {
                float angle = (index - middle) * spread;
                Vec3 direction = look.yRot((float) Math.toRadians(-angle));
                SmallFireball fireball = new SmallFireball(level, player, direction);
                fireball.setPos(muzzle.x, muzzle.y - 0.1D, muzzle.z);
                level.addFreshEntity(fireball);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
            return true;
        }
    }

    /** Pull nearby entities toward the player and deal water damage. */
    public record Vortex(Stage minStage, String nameKey, int cooldown, Ability.Cost cost, boolean apex,
                         double radius, double pull, float damage, int duration) implements Ability {
        static final MapCodec<Vortex> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Vortex::minStage),
                nameField(Vortex::nameKey),
                cooldownField(Vortex::cooldown),
                costField(Vortex::cost),
                apexField(Vortex::apex),
                Codec.doubleRange(1.0D, 24.0D).optionalFieldOf("radius", 6.0D).forGetter(Vortex::radius),
                Codec.doubleRange(0.1D, 3.0D).optionalFieldOf("pull", 0.8D).forGetter(Vortex::pull),
                Codec.floatRange(0.0F, 40.0F).optionalFieldOf("damage", 2.0F).forGetter(Vortex::damage),
                Codec.intRange(0, 1200).optionalFieldOf("duration", 60).forGetter(Vortex::duration)
        ).apply(instance, Vortex::new));

        @Override
        public AbilityType<?> type() {
            return VORTEX;
        }

        @Override
        public Component name() {
            return Component.translatable(nameKey);
        }

        @Override
        public boolean activate(ServerPlayer player, Stage stage) {
            ServerLevel level = player.serverLevel();
            List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(radius),
                    entity -> AbilityTargets.valid(player, entity, radius));
            if (caught.isEmpty()) {
                player.displayClientMessage(Component.translatable("mutagen.ability.no_target"), true);
                return false;
            }

            for (LivingEntity entity : caught) {
                Vec3 toward = player.position().subtract(entity.position());
                Vec3 flat = new Vec3(toward.x, 0.0D, toward.z);
                if (flat.lengthSqr() > 1.0E-4D) {
                    flat = flat.normalize().scale(pull);
                }
                entity.setDeltaMovement(entity.getDeltaMovement().add(flat.x, 0.25D, flat.z));
                entity.hurtMarked = true;
                if (damage > 0.0F) {
                    entity.hurt(player.damageSources().indirectMagic(player, player), damage);
                }
                if (duration > 0) {
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1), player);
                }
            }

            for (int i = 0; i < 24; i++) {
                double angle = i * Math.PI * 2.0D / 24.0D;
                level.sendParticles(ParticleTypes.SPLASH, player.getX() + Math.cos(angle) * radius * 0.6D,
                        player.getY() + 0.3D, player.getZ() + Math.sin(angle) * radius * 0.6D, 4,
                        0.2D, 0.1D, 0.2D, 0.1D);
            }
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, player.getX(), player.getY() + 0.5D, player.getZ(),
                    40, radius * 0.3D, 0.5D, radius * 0.3D, 0.1D);
            level.playSound(null, player.blockPosition(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE,
                    SoundSource.PLAYERS, 1.2F, 0.8F);
            return true;
        }
    }

    /** Lunge at the target in sight and bite it immediately. */
    public record Lunge(Stage minStage, String nameKey, int cooldown, Ability.Cost cost, boolean apex,
                        double range, double power, float damage) implements Ability {
        static final MapCodec<Lunge> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                stage(Lunge::minStage),
                nameField(Lunge::nameKey),
                cooldownField(Lunge::cooldown),
                costField(Lunge::cost),
                apexField(Lunge::apex),
                Codec.doubleRange(2.0D, 32.0D).optionalFieldOf("range", 10.0D).forGetter(Lunge::range),
                Codec.doubleRange(0.2D, 4.0D).optionalFieldOf("power", 1.4D).forGetter(Lunge::power),
                Codec.floatRange(0.0F, 40.0F).optionalFieldOf("damage", 4.0F).forGetter(Lunge::damage)
        ).apply(instance, Lunge::new));

        @Override
        public AbilityType<?> type() {
            return LUNGE;
        }

        @Override
        public Component name() {
            return Component.translatable(nameKey);
        }

        @Override
        public boolean activate(ServerPlayer player, Stage stage) {
            LivingEntity target = lookTarget(player, range);
            if (target == null) {
                player.displayClientMessage(Component.translatable("mutagen.ability.no_target"), true);
                return false;
            }
            Vec3 toward = target.position().subtract(player.position());
            Vec3 push = toward.lengthSqr() > 1.0E-4D ? toward.normalize().scale(power) : Vec3.ZERO;
            player.setDeltaMovement(push.x, Math.max(push.y, 0.0D) + 0.3D, push.z);
            player.hurtMarked = true;
            player.resetFallDistance();
            if (damage > 0.0F) {
                LungeAttack.start(player, target, damage);
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.WOLF_GROWL, SoundSource.PLAYERS,
                    1.0F, 1.0F);
            return true;
        }
    }
}
