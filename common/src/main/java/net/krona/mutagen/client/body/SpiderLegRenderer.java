package net.krona.mutagen.client.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.Mutagen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.Color;
import software.bernie.geckolib.util.GeckoLibUtil;

/** A jointed transitional limb. Progress is sampled directly, so reversal and remote players agree. */
@Environment(EnvType.CLIENT)
public final class SpiderLegRenderer extends GeoObjectRenderer<SpiderLegRenderer.Leg> {
    private static final ResourceLocation TEXTURE = Mutagen.id("textures/entity/spider_limb.png");
    private static final SpiderLegRenderer INSTANCE = new SpiderLegRenderer();
    private final Leg leg = new Leg();

    private SpiderLegRenderer() { super(new Model()); }

    public static float handoff(float progress) {
        return MorphSurface.vanillaSpiderLeg(progress) ? 1.0F : 0.0F;
    }

    public static void render(ModelPart posed, MorphPlan.Target target, float growth, float progress,
                              float age, PoseStack pose, MultiBufferSource buffers, int light) {
        float alpha = 1 - handoff(progress);
        if (alpha <= 0) return;
        INSTANCE.leg.growth = growth;
        INSTANCE.leg.progress = SpiderMorph.phase(progress);
        INSTANCE.leg.alpha = alpha;
        INSTANCE.leg.age = age;
        Box box = target.box();
        boolean right = target.part().part().contains("right");
        pose.pushPose();
        try {
            posed.translateAndRotate(pose);
            // Gecko geometry is authored root-to-tip along +X. ModelPart right legs point along -X.
            pose.translate((right ? box.maxX() : box.minX()) / 16.0F,
                    box.center().y / 16.0F, box.center().z / 16.0F);
            pose.scale((right ? -1 : 1) * box.size().x / 16.0F,
                    -box.size().y / 2.0F, box.size().z / 2.0F);
            RenderType type = RenderType.entityCutoutNoCull(TEXTURE);
            INSTANCE.render(pose, INSTANCE.leg, buffers, type, buffers.getBuffer(type), light, 0);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public Color getRenderColor(Leg animatable, float partialTick, int light) {
        return Color.ofRGBA(1F, 1F, 1F, animatable.alpha);
    }

    @Override
    public void preRender(PoseStack pose, Leg animatable, BakedGeoModel model, MultiBufferSource buffers,
                          VertexConsumer vertices, boolean rerender, float partialTick, int light,
                          int overlay, int colour) {
        // ObjectRenderer's default half-block offset is inappropriate for an attached body part.
        this.objectRenderTranslations.set(pose.last().pose());
    }

    public static final class Leg implements GeoAnimatable {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        float growth, progress, alpha, age;
        @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
        @Override public double getTick(Object object) { return age; }
    }

    private static final class Model extends GeoModel<Leg> {
        @Override public ResourceLocation getModelResource(Leg leg) {
            return Mutagen.id("geo/spider_limb.geo.json");
        }
        @Override public ResourceLocation getTextureResource(Leg leg) { return TEXTURE; }
        @Override public ResourceLocation getAnimationResource(Leg leg) {
            return Mutagen.id("animations/spider_limb.animation.json");
        }

        @Override
        public void handleAnimations(Leg leg, long instance, AnimationState<Leg> state, float partialTick) {
            // No shared playback clock or growing per-player manager cache: overwrite every joint per draw.
            float folded = 1 - SpiderMorph.window(leg.growth, 0.10F, 0.95F);
            float joint = (1 - SpiderMorph.window(leg.progress, 0.65F, 0.96F));
            float breath = (float)Math.sin(leg.age * 0.12F) * 0.025F * joint;
            getBone("upper").ifPresent(b -> b.setRotZ(-0.75F * folded + breath));
            getBone("lower").ifPresent(b -> b.setRotZ(1.65F * folded + 0.28F * joint));
            getBone("tip").ifPresent(b -> b.setRotZ(-0.90F * folded - 0.28F * joint));
        }
    }
}
