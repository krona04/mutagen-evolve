import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.krona.mutagen.client.body.*;
import net.minecraft.client.model.geom.ModelPart;


import net.minecraft.core.Direction;
import java.util.EnumSet;

/** Models the shared-builder invalidation that caused the actual NeoForge Not building! crash. */
public class MorphBufferCheck {
    private static final class SwitchingSource  {
        String active;
        int generation, vertices;
        public VertexConsumer getBuffer(String type) {
            if(type!=active) { active=type; generation++; }
            final int lease=generation;
            return new VertexConsumer() {
                void valid() { if(lease!=generation) throw new IllegalStateException("Not building!"); }
                public VertexConsumer addVertex(float x,float y,float z) { valid();vertices++;return this; }
                public VertexConsumer setColor(int r,int g,int b,int a) { valid();return this; }
                public VertexConsumer setUv(float u,float v) { valid();return this; }
                public VertexConsumer setUv1(int u,int v) { valid();return this; }
                public VertexConsumer setUv2(int u,int v) { valid();return this; }
                public VertexConsumer setNormal(float x,float y,float z) { valid();return this; }
            };
        }
    }
    public static void main(String[] args) {
        String base="core";
        String overlay="overlay";
        String fur="fur";
        var source=new SwitchingSource();
        var stale=source.getBuffer(base);
        source.getBuffer(fur);
        boolean reproduced=false;
        try { stale.addVertex(0,0,0); } catch(IllegalStateException expected) { reproduced=true; }
        if(!reproduced) throw new AssertionError("Regression fixture did not reproduce stale builder failure");
        var cube=new ModelPart.Cube(0,0,-2,0,-2,4,12,4,0,0,0,false,64,64,EnumSet.allOf(Direction.class));
        var corners=MorphCuboid.vertices(cube);
        Box box=MorphCuboid.bounds(cube);
        var pose=new PoseStack();
        for(int frame=0;frame<20;frame++) for(int bone=0;bone<6;bone++) {
            ContinuousMorphRenderer.emit(() -> source.getBuffer(base),pose,box,box,corners,bone,0,-1);
            ContinuousMorphRenderer.emit(() -> source.getBuffer(overlay),pose,box,box,corners,-1,0,-1);
            ContinuousMorphRenderer.emit(() -> source.getBuffer(fur),pose,box,box,corners,-1,0,-1);
        }
        if(source.vertices!=20*6*3*24) throw new AssertionError("Missing vertices after material switch");
        System.out.println("PASS: reproduced stale-builder crash; 360 production draws survive interleaved core/overlay/fur switches");
    }
}
