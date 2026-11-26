package net.krona.mutagen.client.body;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Позы частей модели как матрицы и кватернионы.
 * <p>
 * Часть модели поворачивается углами Эйлера в порядке ZYX ({@link ModelPart#translateAndRotate}).
 * Превращению нужно складывать повороты и плавно переходить от одного к другому, а это делается
 * кватернионами; обратно в углы кватернион раскладывает {@link #euler}. Свой расклад, а не JOML:
 * {@code getEulerAnglesZYX} в версии, что идёт с игрой, возвращает неверный угол вокруг X.
 */
@Environment(EnvType.CLIENT)
public final class Poses {
    private Poses() {
    }

    /** Поворот части так, как его применяет сама игра. */
    public static Quaternionf rotation(ModelPart part) {
        return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
    }

    /** Разложение поворота на углы для {@link ModelPart}: {@code [x, y, z]}. */
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

    /** Добавляет к матрице (в блоках) собственное смещение, поворот и масштаб части. */
    public static Matrix4f apply(Matrix4f matrix, ModelPart part) {
        return matrix.translate(part.x / 16.0F, part.y / 16.0F, part.z / 16.0F)
                .rotate(rotation(part))
                .scale(part.xScale, part.yScale, part.zScale);
    }

    /** Точка поворота в пикселях модели. */
    public static Vector3f pivot(Matrix4f matrix) {
        return matrix.getTranslation(new Vector3f()).mul(16.0F);
    }

    public static Quaternionf rotation(Matrix4f matrix) {
        return matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
    }

    /** Масштаб матрицы. Позы существ масштабируются равномерно, поэтому хватает одной оси. */
    public static float scale(Matrix4f matrix) {
        return matrix.getScale(new Vector3f()).x;
    }

    /** Ставит часть в позу, заданную матрицей в блоках, с добавочным масштабом. */
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
