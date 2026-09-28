package net.krona.mutagen.client.body;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * План превращения для штамма: какая кость игрока в какую часть существа переходит, что у существа
 * вырастает сверх человеческого и какие кости втягиваются.
 * <p>
 * Модель существа запекается из её собственного ванильного слоя, поэтому годится любой моб.
 * Если в штамме нет списка частей, кости сопоставляются по именам: {@code head}, {@code body},
 * {@code right_arm} и так далее — так устроены все человекоподобные мобы.
 */
@Environment(EnvType.CLIENT)
public final class MorphPlan {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Optional<ModelPart>> ROOTS = new HashMap<>();
    private static final Map<Strain, MorphPlan> PLANS = new WeakHashMap<>();

    /** Кость игрока → часть существа, которой она становится. */
    public record Target(int index, Strain.Part part, ModelPart model, Box box, PartPose pose) {
    }

    private final Target[] targets = new Target[Strain.Bone.values().length];
    private final List<Target> grown = new ArrayList<>();
    private final Set<Strain.Bone> absorbed = EnumSet.noneOf(Strain.Bone.class);

    private MorphPlan() {
    }

    public static MorphPlan of(Strain strain) {
        return PLANS.computeIfAbsent(strain, MorphPlan::build);
    }

    private static MorphPlan build(Strain strain) {
        MorphPlan plan = new MorphPlan();
        plan.absorbed.addAll(strain.body().absorb());
        ModelPart root = root(strain);
        if (root == null) {
            return plan;
        }
        List<Strain.Part> parts = strain.body().parts().orElseGet(() -> {
            List<Strain.Part> auto = new ArrayList<>();
            for (Strain.Bone bone : Strain.Bone.values()) {
                if (root.hasChild(bone.key())) {
                    auto.add(new Strain.Part(bone.key(), bone));
                }
            }
            return auto;
        });
        for (int i = 0; i < parts.size(); i++) {
            Strain.Part part = parts.get(i);
            ModelPart model = find(root, part.part());
            if (model == null) {
                LOGGER.warn("[Mutagen] Strain {}: no part '{}' in model {}", strain.id(), part.part(),
                        strain.modelLayer());
                continue;
            }
            Box box = Box.of(model).scaled(part.scale().x(), part.scale().y(), part.scale().z());
            Target target = new Target(i, part, model, box, model.getInitialPose());
            if (part.grow()) {
                plan.grown.add(target);
            } else if (!plan.absorbed.contains(part.bone())) {
                plan.targets[part.bone().ordinal()] = target;
            }
        }
        return plan;
    }

    @Nullable
    public Target target(Strain.Bone bone) {
        return targets[bone.ordinal()];
    }

    public List<Target> grown() {
        return grown;
    }

    public boolean absorbed(Strain.Bone bone) {
        return absorbed.contains(bone);
    }

    /** Часть по пути вида {@code "head"} или {@code "body/tail"}. */
    @Nullable
    private static ModelPart find(ModelPart root, String path) {
        ModelPart current = root;
        for (String name : path.split("/")) {
            if (!current.hasChild(name)) {
                return null;
            }
            current = current.getChild(name);
        }
        return current;
    }

    /**
     * Корень модели существа. Запекается один раз: геометрия слоёв задаётся кодом и не меняется
     * при перезагрузке ресурсов. Если слоя нет, штамм меняет только кожу, но не форму.
     */
    @Nullable
    private static ModelPart root(Strain strain) {
        String layer = strain.modelLayer();
        return ROOTS.computeIfAbsent(layer, key -> {
            try {
                int split = key.indexOf('#');
                ResourceLocation model = ResourceLocation.parse(split < 0 ? key : key.substring(0, split));
                String name = split < 0 ? "main" : key.substring(split + 1);
                return Optional.of(Minecraft.getInstance().getEntityModels()
                        .bakeLayer(new ModelLayerLocation(model, name)));
            } catch (Exception e) {
                LOGGER.warn("[Mutagen] No model layer {} for strain {}: the body will change skin, not shape",
                        key, strain.id());
                return Optional.empty();
            }
        }).orElse(null);
    }
}
