package net.krona.mutagen.client.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krona.mutagen.mixin.client.ModelPartAccessor;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import java.util.*;

/** Fixed six-quad topology, with the original cube's UVs retained for material resampling. */
public final class MorphCuboid {
    public record Vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {}
    public record Surface(Box box, List<Vertex> vertices, Box whole) {
        public boolean outer(int face) {
            Vertex v=vertices.get(face*4);
            float plane, boundary;
            if(Math.abs(v.nx)>0.5F) { plane=v.x*16; boundary=v.nx>0?whole.maxX():whole.minX(); }
            else if(Math.abs(v.ny)>0.5F) { plane=v.y*16; boundary=v.ny>0?whole.maxY():whole.minY(); }
            else { plane=v.z*16; boundary=v.nz>0?whole.maxZ():whole.minZ(); }
            return Math.abs(plane-boundary)<0.0001F;
        }
    }
    public record Core(ModelPart.Cube cube, List<ModelPart> path) {
        public Box box() { return bounds(cube); }
        public Matrix4f relative() {
            Matrix4f matrix = new Matrix4f();
            for (ModelPart part : path) Poses.apply(matrix, part);
            return matrix;
        }
    }
    private static final Map<ModelPart.Cube, List<Vertex>> VERTICES = new WeakHashMap<>();
    private static final Map<ModelPart, Core> CORES = new WeakHashMap<>();
    private MorphCuboid() {}

    public static Box bounds(ModelPart.Cube cube) {
        return new Box(cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ);
    }

    public static Core core(ModelPart root) {
        Core cached = CORES.get(root);
        if (cached != null) return cached;
        List<Core> cubes = new ArrayList<>();
        // The part's own cubes come first. A descendant may be a larger but hidden layer, like an illager's hat
        // inside the head, and the body must not morph towards it.
        for (ModelPart.Cube cube : ((ModelPartAccessor)(Object)root).mutagen$getCubes()) cubes.add(new Core(cube, List.of()));
        if (cubes.isEmpty()) collect(root, List.of(), cubes);
        // Inflated cubes are outer layers (a robe over the body, a hat over the head), often half transparent.
        // Their bounds sit on half pixels; the solid body underneath is on whole pixels.
        List<Core> solid = cubes.stream().filter(c -> !inflated(c.cube())).toList();
        if (!solid.isEmpty()) cubes = new ArrayList<>(solid);
        Core result = cubes.stream().max(Comparator.comparingDouble(c -> {
            var size = c.box().size(); return size.x * size.y * size.z;
        })).orElseThrow();
        CORES.put(root, result);
        return result;
    }

    /**
     * A cube built with a deformation keeps its nominal bounds, but its vertices sit outside them. That is how
     * vanilla draws outer layers: hats, robes, jackets.
     */
    private static boolean inflated(ModelPart.Cube cube) {
        // Compiled vertices are in blocks; cube bounds are in model pixels.
        for (Vertex vertex : vertices(cube)) {
            float x = vertex.x() * 16, y = vertex.y() * 16, z = vertex.z() * 16;
            if (x < cube.minX - 0.01F || x > cube.maxX + 0.01F || y < cube.minY - 0.01F || y > cube.maxY + 0.01F
                    || z < cube.minZ - 0.01F || z > cube.maxZ + 0.01F) {
                return true;
            }
        }
        return false;
    }

    private static void collect(ModelPart part, List<ModelPart> path, List<Core> cubes) {
        ModelPartAccessor access = (ModelPartAccessor)(Object)part;
        for (ModelPart.Cube cube : access.mutagen$getCubes()) cubes.add(new Core(cube, path));
        for (ModelPart child : access.mutagen$getChildren().values()) {
            List<ModelPart> next = new ArrayList<>(path); next.add(child);
            collect(child, List.copyOf(next), cubes);
        }
    }

    public static List<Vertex> vertices(ModelPart.Cube cube) {
        return VERTICES.computeIfAbsent(cube, key -> {
            Capture capture = new Capture();
            key.compile(new PoseStack().last(), capture, 0, 0, -1);
            return List.copyOf(capture.vertices);
        });
    }

    public static org.joml.Vector3f position(Box target, Box shape, Vertex corner) {
        return new org.joml.Vector3f(
                shape.minX()+(shape.maxX()-shape.minX())*(corner.x*16-target.minX())/(target.maxX()-target.minX()),
                shape.minY()+(shape.maxY()-shape.minY())*(corner.y*16-target.minY())/(target.maxY()-target.minY()),
                shape.minZ()+(shape.maxZ()-shape.minZ())*(corner.z*16-target.minZ())/(target.maxZ()-target.minZ()));
    }

    /** Match a target corner to the source cube's same face/corner, including mirrored skin UVs. */
    public static Vertex match(ModelPart.Cube source, ModelPart.Cube target, Vertex corner) {
        return match(source, bounds(target), corner);
    }

    public static Vertex match(ModelPart.Cube source, Box target, Vertex corner) {
        Box a = bounds(source), b = target;
        float x = (corner.x * 16 - b.minX()) / (b.maxX() - b.minX());
        float y = (corner.y * 16 - b.minY()) / (b.maxY() - b.minY());
        float z = (corner.z * 16 - b.minZ()) / (b.maxZ() - b.minZ());
        return vertices(source).stream().min(Comparator.comparingDouble(v -> {
            float dx = (v.x * 16 - a.minX()) / (a.maxX() - a.minX()) - x;
            float dy = (v.y * 16 - a.minY()) / (a.maxY() - a.minY()) - y;
            float dz = (v.z * 16 - a.minZ()) / (a.maxZ() - a.minZ()) - z;
            return dx * dx + dy * dy + dz * dz
                    + 10 * (Math.abs(v.nx - corner.nx) + Math.abs(v.ny - corner.ny) + Math.abs(v.nz - corner.nz));
        })).orElseThrow();
    }

    /** Cut a cuboid into a nonzero subvolume while retaining continuous outer-face texture coordinates. */
    public static Surface surface(ModelPart.Cube cube, Box piece) {
        Box whole=bounds(cube);
        List<Vertex> source=vertices(cube), result=new ArrayList<>(24);
        for(int face=0;face<6;face++) {
            Vertex a=source.get(face*4), b=source.get(face*4+1), d=source.get(face*4+3);
            var origin=new org.joml.Vector3f(a.x,a.y,a.z);
            var du=new org.joml.Vector3f(b.x-a.x,b.y-a.y,b.z-a.z);
            var dv=new org.joml.Vector3f(d.x-a.x,d.y-a.y,d.z-a.z);
            for(int k=0;k<4;k++) {
                Vertex corner=source.get(face*4+k);
                var position=position(whole,piece,corner).div(16);
                var delta=new org.joml.Vector3f(position).sub(origin);
                float u=delta.dot(du)/du.lengthSquared(), v=delta.dot(dv)/dv.lengthSquared();
                result.add(new Vertex(position.x,position.y,position.z,
                        a.u+(b.u-a.u)*u+(d.u-a.u)*v,a.v+(b.v-a.v)*u+(d.v-a.v)*v,corner.nx,corner.ny,corner.nz));
            }
        }
        return new Surface(piece,List.copyOf(result),whole);
    }

    public static Surface rotate(Surface surface, org.joml.Quaternionf rotation) {
        List<Vertex> result=new ArrayList<>(surface.vertices.size());
        for(Vertex vertex:surface.vertices) {
            var point=rotation.transform(new org.joml.Vector3f(vertex.x,vertex.y,vertex.z));
            var normal=rotation.transform(new org.joml.Vector3f(vertex.nx,vertex.ny,vertex.nz));
            result.add(new Vertex(point.x,point.y,point.z,vertex.u,vertex.v,normal.x,normal.y,normal.z));
        }
        return new Surface(surface.box.rotated(rotation),List.copyOf(result),surface.whole.rotated(rotation));
    }

    private static final class Capture implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        float x, y, z, u, v;
        public VertexConsumer addVertex(float x, float y, float z) { this.x=x; this.y=y; this.z=z; return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        public VertexConsumer setUv(float u,float v) { this.u=u; this.v=v; return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) {
            vertices.add(new Vertex(this.x,this.y,this.z,u,v,x,y,z)); return this;
        }
    }
}
