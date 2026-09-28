package net.krona.mutagen.client.body;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.mixin.client.ModelPartAccessor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Габариты части модели в её собственных координатах (в пикселях модели, от точки поворота).
 * Считаются по кубам части и её потомков; повороты потомков не учитываются — для оценки формы
 * конечности этого достаточно.
 */
@Environment(EnvType.CLIENT)
public record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    private static final Map<ModelPart, Box> CACHE = new WeakHashMap<>();

    public static Box of(ModelPart part) {
        return CACHE.computeIfAbsent(part, key -> {
            float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                    -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            collect(key, 0.0F, 0.0F, 0.0F, bounds);
            if (bounds[0] > bounds[3]) {
                return new Box(0, 0, 0, 0, 0, 0);
            }
            return new Box(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
        });
    }

    private static void collect(ModelPart part, float dx, float dy, float dz, float[] bounds) {
        ModelPartAccessor access = (ModelPartAccessor) (Object) part;
        for (ModelPart.Cube cube : access.mutagen$getCubes()) {
            bounds[0] = Math.min(bounds[0], cube.minX + dx);
            bounds[1] = Math.min(bounds[1], cube.minY + dy);
            bounds[2] = Math.min(bounds[2], cube.minZ + dz);
            bounds[3] = Math.max(bounds[3], cube.maxX + dx);
            bounds[4] = Math.max(bounds[4], cube.maxY + dy);
            bounds[5] = Math.max(bounds[5], cube.maxZ + dz);
        }
        for (ModelPart child : access.mutagen$getChildren().values()) {
            PartPose pose = child.getInitialPose();
            collect(child, dx + pose.x, dy + pose.y, dz + pose.z, bounds);
        }
    }

    public Vector3f size() {
        return new Vector3f(maxX - minX, maxY - minY, maxZ - minZ);
    }

    /** Своё смешивание по каждой оси: толщина и длина кости меняются в разное время. */
    public Box lerp(Box to, float tx, float ty, float tz) {
        return new Box(minX + (to.minX - minX) * tx, minY + (to.minY - minY) * ty, minZ + (to.minZ - minZ) * tz,
                maxX + (to.maxX - maxX) * tx, maxY + (to.maxY - maxY) * ty, maxZ + (to.maxZ - maxZ) * tz);
    }

    public Box lerp(Box to, float t) {
        return new Box(minX + (to.minX - minX) * t, minY + (to.minY - minY) * t, minZ + (to.minZ - minZ) * t,
                maxX + (to.maxX - maxX) * t, maxY + (to.maxY - maxY) * t, maxZ + (to.maxZ - maxZ) * t);
    }

    /** Растянуть вокруг центра. */
    public Box scaled(float x, float y, float z) {
        float cx = (minX + maxX) * 0.5F;
        float cy = (minY + maxY) * 0.5F;
        float cz = (minZ + maxZ) * 0.5F;
        float hx = (maxX - minX) * 0.5F * x;
        float hy = (maxY - minY) * 0.5F * y;
        float hz = (maxZ - minZ) * 0.5F * z;
        return new Box(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
    }

    /**
     * Как вписать эту коробку в {@code target}: масштаб по осям и сдвиг в координатах части.
     * Точка {@code v} этой коробки переходит в {@code offset + scale * v}.
     */
    public Fit fit(Box target) {
        Vector3f scale = new Vector3f(ratio(target.maxX - target.minX, maxX - minX),
                ratio(target.maxY - target.minY, maxY - minY),
                ratio(target.maxZ - target.minZ, maxZ - minZ));
        Vector3f offset = new Vector3f(target.minX - scale.x * minX, target.minY - scale.y * minY,
                target.minZ - scale.z * minZ);
        return new Fit(scale, offset);
    }

    private static float ratio(float target, float source) {
        return source < 1.0E-4F ? 1.0F : target / source;
    }

    public record Fit(Vector3f scale, Vector3f offset) {
    }
}
