package net.krona.mutagen.client.body;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Apply after vanilla setupAnim, which otherwise flaps phantom wings even on the ground. */
public final class CreatureRestPose {
    private CreatureRestPose() {}

    public static void phantom(ModelPart root, float flight) {
        ModelPart body = root.getChild("body");
        for (boolean left : new boolean[]{true, false}) {
            String side = left ? "left" : "right";
            float sign = left ? 1 : -1;
            ModelPart base = body.getChild(side + "_wing_base");
            ModelPart tip = base.getChild(side + "_wing_tip");
            base.yRot = Mth.lerp(flight, -sign * 1.1F, base.getInitialPose().yRot);
            tip.yRot = Mth.lerp(flight, sign * 2.2F, tip.getInitialPose().yRot);
            base.zRot = Mth.lerp(flight, sign * .08F, base.zRot);
            tip.zRot = Mth.lerp(flight, -sign * .12F, tip.zRot);
        }
        ModelPart tail = body.getChild("tail_base");
        tail.xRot = Mth.lerp(flight, -.06F, tail.xRot);
        ModelPart tip = tail.getChild("tail_tip");
        tip.xRot = Mth.lerp(flight, -.06F, tip.xRot);
    }

    public static void bee(ModelPart bone, float flight, float walk, float stride, float pitch) {
        for (boolean right : new boolean[]{true, false}) {
            ModelPart wing = bone.getChild(right ? "right_wing" : "left_wing");
            wing.yRot = Mth.lerp(flight, right ? -.2618F : .2618F, wing.yRot);
            wing.zRot *= flight;
        }
        bone.y = Mth.lerp(flight, bone.getInitialPose().y, bone.y);
        bone.xRot = Mth.lerp(flight, 0, bone.xRot + pitch * Mth.DEG_TO_RAD);
        bone.zRot *= flight;
        String[] legs = {"front_legs", "middle_legs", "back_legs"};
        for (int i = 0; i < legs.length; i++) {
            ModelPart leg = bone.getChild(legs[i]);
            float step = Mth.cos(walk * .6662F + (i == 1 ? Mth.PI : 0)) * .25F * stride;
            leg.xRot = Mth.lerp(flight, step, leg.xRot);
        }
    }
}
