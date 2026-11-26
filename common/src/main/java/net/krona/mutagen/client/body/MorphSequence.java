package net.krona.mutagen.client.body;

import net.krona.mutagen.strain.Infection;
import net.krona.mutagen.strain.TransformationProgress;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.model.PlayerModel;

/** Anatomy-specific timelines; unknown datapack strains retain the original spread order. */
public final class MorphSequence {
    private MorphSequence() {}

    public static float window(float t, float start, float end) {
        float x = Math.max(0, Math.min(1, (t - start) / (end - start)));
        return x * x * (3 - 2 * x);
    }

    public static float bone(String strain, Strain.Bone bone, float p) {
        boolean arm = bone == Strain.Bone.RIGHT_ARM || bone == Strain.Bone.LEFT_ARM;
        boolean leg = bone == Strain.Bone.RIGHT_LEG || bone == Strain.Bone.LEFT_LEG;
        return switch (strain) {
            case "zombie" -> window(p, arm ? 0.08F : bone == Strain.Bone.HEAD ? 0.22F : 0.34F, 1);
            case "drowned" -> window(p, bone == Strain.Bone.HEAD ? 0.08F : arm ? 0.18F : 0.32F, 1);
            case "skeleton" -> window(p, arm || leg ? 0.04F : 0.25F, 1);
            case "piglin" -> window(p, bone == Strain.Bone.HEAD ? 0.04F : arm ? 0.22F : 0.36F, 1);
            case "enderman" -> window(p, arm || leg ? 0.04F : bone == Strain.Bone.BODY ? 0.24F : 0.38F, 1);
            case "creeper" -> window(p, bone == Strain.Bone.BODY ? 0.12F : arm ? 0.32F : 0.22F, 1);
            case "wolf" -> window(p, bone == Strain.Bone.HEAD ? 0.12F : arm ? 0.30F : 0.24F, 1);
            case "phantom" -> window(p, arm ? 0.06F : leg ? 0.58F : 0.30F, 1);
            case "bee" -> bone == Strain.Bone.BODY ? TransformationProgress.beeBody(21 + 79 * p)
                    : TransformationProgress.beeRetract(21 + 79 * p);
            case "guardian" -> window(p, bone == Strain.Bone.BODY ? 0.30F : 0.44F, 1);
            case "blaze" -> window(p, bone == Strain.Bone.HEAD ? 0.04F : bone == Strain.Bone.BODY ? 0.48F : 0.28F, 1);
            default -> p;
        };
    }

    public static boolean supports(Strain strain) {
        if (!strain.id().getNamespace().equals("mutagen")) return false;
        return switch (strain.id().getPath()) {
            case "zombie", "drowned", "skeleton", "piglin", "enderman", "creeper", "wolf",
                    "phantom", "bee", "guardian", "blaze" -> true;
            default -> false;
        };
    }

    public static float phase(Strain strain, Strain.Bone bone, float progress) {
        if (supports(strain)) return bone(strain.id().getPath(), bone, Infection.overall(progress));
        return Math.min(1, Infection.overall(progress) * 0.85F
                + Infection.bonePhase(strain, bone, progress) * 0.15F);
    }

    /** A short anticipatory posture; fades away before the final mob animation takes ownership. */
    public static void prepare(PlayerModel<?> model, Strain strain, float progress) {
        if (!supports(strain)) return;
        float p = Infection.overall(progress);
        float tension = window(p, 0.05F, 0.35F) * (1 - window(p, 0.60F, 1));
        switch (strain.id().getPath()) {
            case "zombie", "drowned" -> {
                model.rightArm.xRot -= 0.65F * tension;
                model.leftArm.xRot -= 0.65F * tension;
                model.head.xRot += 0.12F * tension;
            }
            case "skeleton" -> {
                model.rightArm.zRot += 0.12F * tension;
                model.leftArm.zRot -= 0.12F * tension;
            }
            case "piglin" -> model.head.xRot -= 0.15F * tension;
            case "enderman" -> {
                model.head.xRot += 0.18F * tension;
                model.rightArm.zRot += 0.08F * tension;
                model.leftArm.zRot -= 0.08F * tension;
            }
            case "wolf", "creeper" -> model.body.xRot += 0.35F * tension;
            case "phantom" -> {
                model.rightArm.zRot += 0.65F * tension;
                model.leftArm.zRot -= 0.65F * tension;
                model.body.xRot += 0.25F * tension;
            }
            case "guardian" -> {
                model.rightArm.zRot -= 0.25F * tension;
                model.leftArm.zRot += 0.25F * tension;
                model.head.xRot += 0.15F * tension;
            }
            case "blaze" -> {
                model.rightArm.zRot += 0.30F * tension;
                model.leftArm.zRot -= 0.30F * tension;
            }
            default -> {}
        }
    }

    public static float growth(Strain strain, MorphPlan.Target target, float progress) {
        if (SpiderMorph.supports(strain)) return SpiderMorph.growth(target, progress);
        if (!supports(strain)) return Infection.bonePhase(strain, target.part().bone(), progress);
        return growth(strain.id().getPath(), target.part().part(), target.index(), Infection.overall(progress));
    }

    public static float growth(String strain, String part, int index, float p) {
        return switch (strain) {
            case "bee" -> window(p, part.contains("wing") ? 0.20F : 0.55F, part.contains("wing") ? 0.70F : 0.95F);
            case "guardian" -> window(p, 0.40F, 0.90F);
            case "phantom" -> window(p, 0.40F, 0.90F);
            case "wolf" -> window(p, part.contains("tail") ? 0.08F : 0.32F, 0.76F);
            case "blaze" -> window(p, 0.08F + (index % 3) * 0.06F, 0.68F);
            default -> window(p, 0.18F, 0.90F);
        };
    }
}
