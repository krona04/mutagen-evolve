package net.krona.mutagen.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.client.body.InfectionTextures;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.mixin.client.WalkAnimationStateAccessor;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Render the creature model in place of the player model in full form.
 * <p>
 * Cache one dummy per species and copy the player's pose each frame. The vanilla mob renderer supplies
 * layers such as armor, held items, and glowing spider eyes.
 */
@Environment(EnvType.CLIENT)
public final class FullFormRenderer {
    private static final Map<EntityType<?>, LivingEntity> DUMMIES = new HashMap<>();
    private static final Set<EntityType<?>> FAILED = new HashSet<>();
    private static Level cachedLevel;

    private FullFormRenderer() {
    }

    /** Return the full-form dummy, or null to render the regular player. */
    @Nullable
    public static LivingEntity dummyFor(Player player) {
        if (!MutagenConfig.get().fullFormRender) {
            return null;
        }
        MutagenData.Gene gene = MutagenPlayer.of(player).primary();
        if (gene == null || gene.stage() != Stage.FULL) {
            return null;
        }
        Strain strain = Strains.get(gene.strainId());
        if (strain == null) {
            return null;
        }
        return dummy(strain.entityType(), player.level());
    }

    /**
     * Use the creature renderer's texture so hybrid parts match the creature's color.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static ResourceLocation textureOf(EntityType<?> type, Level level) {
        LivingEntity dummy = dummy(type, level);
        if (dummy == null) {
            return null;
        }
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
        return ((EntityRenderer<LivingEntity>) renderer).getTextureLocation(dummy);
    }

    @Nullable
    public static LivingEntity dummy(EntityType<?> type, Level level) {
        if (cachedLevel != level) {
            DUMMIES.clear();
            FAILED.clear();
            cachedLevel = level;
        }
        LivingEntity existing = DUMMIES.get(type);
        if (existing != null) {
            return existing;
        }
        if (FAILED.contains(type)) {
            return null;
        }
        Entity created = type.create(level);
        if (created instanceof LivingEntity living) {
            DUMMIES.put(type, living);
            return living;
        }
        // Cache unsupported entity types to avoid attempting to create them every frame.
        FAILED.add(type);
        return null;
    }

    /** Copy all player state the renderer needs to pose the dummy. */
    public static void copyState(Player player, LivingEntity dummy) {
        dummy.setPos(player.getX(), player.getY(), player.getZ());
        dummy.xo = player.xo;
        dummy.yo = player.yo;
        dummy.zo = player.zo;
        dummy.xOld = player.xOld;
        dummy.yOld = player.yOld;
        dummy.zOld = player.zOld;

        dummy.setYRot(player.getYRot());
        dummy.yRotO = player.yRotO;
        dummy.setXRot(player.getXRot());
        dummy.xRotO = player.xRotO;
        dummy.yBodyRot = player.yBodyRot;
        dummy.yBodyRotO = player.yBodyRotO;
        dummy.yHeadRot = player.yHeadRot;
        dummy.yHeadRotO = player.yHeadRotO;

        dummy.tickCount = player.tickCount;
        dummy.hurtTime = player.hurtTime;
        dummy.hurtDuration = player.hurtDuration;
        dummy.deathTime = 0;

        dummy.setPose(player.getPose());
        dummy.setShiftKeyDown(player.isShiftKeyDown());
        dummy.setSprinting(player.isSprinting());
        dummy.setSwimming(player.isSwimming());
        dummy.setInvisible(player.isInvisible());
        dummy.setRemainingFireTicks(player.getRemainingFireTicks());
        dummy.setGlowingTag(player.isCurrentlyGlowing());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            dummy.setItemSlot(slot, player.getItemBySlot(slot));
        }

        copyWalkAnimation(player, dummy);
        net.krona.mutagen.client.body.CreatureMotion.apply(player, dummy);
    }

    /**
     * Render the creature's arm in first person at full form. Creatures without arms, such as creepers,
     * show no arm.
     *
     * @return true if this method handled the first-person view and the vanilla arm can be skipped
     */
    @SuppressWarnings("unchecked")
    public static boolean renderFullFormHand(Player player, PoseStack poseStack, MultiBufferSource buffer,
                                             int packedLight, boolean rightHand) {
        LivingEntity dummy = dummyFor(player);
        if (dummy == null) {
            return false;
        }
        copyState(player, dummy);

        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)) {
            return true;
        }
        if (!(livingRenderer.getModel() instanceof HumanoidModel<?> humanoid)) {
            // Creatures without arms should show none in first person.
            return true;
        }

        EntityModel<LivingEntity> model = (EntityModel<LivingEntity>) livingRenderer.getModel();
        model.attackTime = 0.0F;
        model.riding = false;
        model.young = false;
        humanoid.crouching = false;
        humanoid.swimAmount = 0.0F;
        model.setupAnim(dummy, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        ModelPart arm = rightHand ? humanoid.rightArm : humanoid.leftArm;
        arm.xRot = 0.0F;
        ResourceLocation texture = ((EntityRenderer<LivingEntity>) renderer).getTextureLocation(dummy);
        arm.render(poseStack, buffer.getBuffer(RenderType.entitySolid(texture)),
                packedLight, OverlayTexture.NO_OVERLAY);
        return true;
    }

    /**
     * At stages II–IV, render the same infection patches on the first-person arm as on the body. Draw any
     * emerging creature arm over them. A separate pass renders the arm in full form.
     */
    public static void tintHand(AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffer,
                                int packedLight, ModelPart arm, ModelPart sleeve, boolean right) {
        MutagenData.Gene gene = MutagenPlayer.of(player).primary();
        if (gene == null) {
            return;
        }
        Stage stage = gene.stage();
        if (!stage.atLeast(Stage.MANIFESTATION) || stage.atLeast(Stage.FULL)) {
            return;
        }
        Strain strain = Strains.get(gene.strainId());
        if (strain == null) {
            return;
        }

        ResourceLocation skin = InfectionTextures.skin(player, strain, gene);
        if (skin != null) {
            VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCullZOffset(skin));
            arm.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
            sleeve.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
        }
        HybridRenderer.renderArm(player, arm, right, poseStack, buffer, packedLight);
    }

    private static void copyWalkAnimation(Player player, LivingEntity dummy) {
        WalkAnimationStateAccessor from = (WalkAnimationStateAccessor) (Object) player.walkAnimation;
        WalkAnimationStateAccessor to = (WalkAnimationStateAccessor) (Object) dummy.walkAnimation;
        to.mutagen$setPosition(from.mutagen$getPosition());
        to.mutagen$setSpeed(from.mutagen$getSpeed());
        to.mutagen$setSpeedOld(from.mutagen$getSpeedOld());
    }
}
