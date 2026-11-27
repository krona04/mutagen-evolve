package net.krona.mutagen.client.body;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Represent model part poses as matrices and quaternions.
 * <p>
 * Vanilla applies Euler rotations in ZYX order ({@link ModelPart#translateAndRotate}). Use quaternions to
 * compose and interpolate rotations, then convert with {@link #euler}. Minecraft's version of JOML returns
 * an incorrect X angle from {@code getEulerAnglesZYX}.
 */
@Environment(EnvType.CLIENT)
public final class Poses {
    private Poses() {
    }

    /** Rotate a part in the same order as vanilla. */
    public static Quaternionf rotation(ModelPart part) {
        return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
    }

    /** Decompose a rotation into {@code [x, y, z]} angles for {@link ModelPart}. */
    public static Vector3f euler(Quaternionf rotation) {
        Matrix3f m = new Matrix3f().set(rotation);
        float y = (float) Math.asin(Math.max(-1.0F, Math.min(1.0F, -m.m02)));
        float x = (float) Math.atan2(m.m12, m.m22);
        float z = (float) Math.atan2(m.m01, m.m00);
        return new Vector3f(x, y, z);
    }

    public static void setRotation(ModelPart part, Quaternionf rotation) {
        Vector3f angles = euler(rotation);
        part.xRot = angles.x;
        part.yRot = angles.y;
        part.zRot = angles.z;
    }

    /** Append the part's translation in blocks, rotation, and scale to the matrix. */
    public static Matrix4f apply(Matrix4f matrix, ModelPart part) {
        return matrix.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F)
                .rotate(rotation(part))
                .scale(part.xScale, part.yScale, part.zScale);
    }

    /** Pivot in model pixels. */
    public static Vector3f pivot(Matrix4f matrix) {
        return matrix.getTranslation(new Vector3f()).mul(16.0F);
    }

    public static Quaternionf rotation(Matrix4f matrix) {
        return matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
    }

    /** Uniform scale from the pose matrix. One axis suffices. */
    public static float scale(Matrix4f matrix) {
        return matrix.getScale(new Vector3f()).x;
    }

    /** Set the part pose from a matrix in blocks and apply an extra scale. */
    public static void place(ModelPart part, Matrix4f matrix, float extraScale) {
        Vector3f pivot = pivot(matrix);
        part.x = pivot.x;
        part.y = pivot.y;
        part.z = pivot.z;
        setRotation(part, rotation(matrix));
        float scale = scale(matrix) * extraScale;
        part.xScale = scale;
        part.yScale = scale;
        part.zScale = scale;
    }
}
