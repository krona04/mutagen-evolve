import java.util.*;
import net.krona.mutagen.client.body.*;
import net.krona.mutagen.strain.Strain;
import net.minecraft.client.model.geom.*;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

public class MergedGeometryCheck {
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static float volume(Box box) { var s=box.size();return s.x*s.y*s.z; }
    public static void main(String[] args) throws Exception {
        var field=Box.class.getDeclaredField("CACHE");field.setAccessible(true);
        @SuppressWarnings("unchecked") Map<ModelPart,Box> cache=(Map<ModelPart,Box>)field.get(null);
        for(int absorbed:new int[]{5,2}) for(Box whole:new Box[]{
                new Box(-3.5F,-4,-5,3.5F,3,5),new Box(-6,-6,-8,6,6,8),new Box(-3,-2,-8,3,2,8)}) {
            var size=whole.size();
            var cube=new ModelPart.Cube(0,0,whole.minX(),whole.minY(),whole.minZ(),size.x,size.y,size.z,
                    0,0,0,false,64,64,EnumSet.allOf(Direction.class));
            List<Box> cells=new ArrayList<>();
            float total=0,externalArea=0;
            Map<String,MorphCuboid.Vertex> seams=new HashMap<>();
            for(Strain.Bone bone:Strain.Bone.values()) {
                if(absorbed==2&&bone!=Strain.Bone.BODY&&bone!=Strain.Bone.RIGHT_LEG&&bone!=Strain.Bone.LEFT_LEG) continue;
                Box piece=ContinuousMorph.partition(whole,bone,absorbed);
                check(volume(piece)>0,"Human bone collapses at merge endpoint");
                total+=volume(piece);cells.add(piece);
                var surface=MorphCuboid.surface(cube,piece);
                check(surface.vertices().size()==24,"Merge changed core topology");
                for(int face=0;face<6;face++) if(surface.outer(face)) {
                    var normal=surface.vertices().get(face*4);var s=piece.size();
                    externalArea+=Math.abs(normal.nx())>0.5F?s.y*s.z:Math.abs(normal.ny())>0.5F?s.x*s.z:s.x*s.y;
                    for(int k=0;k<4;k++) {
                        var v=surface.vertices().get(face*4+k);
                        String key=Math.round(v.x()*160000)+":"+Math.round(v.y()*160000)+":"+Math.round(v.z()*160000)
                                +":"+v.nx()+":"+v.ny()+":"+v.nz();
                        var old=seams.putIfAbsent(key,v);
                        if(old!=null) check(Math.abs(old.u()-v.u())<0.00001F&&Math.abs(old.v()-v.v())<0.00001F,"Material seam between merged cells");
                    }
                }
                ModelPart part=new ModelPart(List.of(),Map.of());part.setInitialPose(PartPose.ZERO);
                Box human=new Box(-2,0,-2,2,12,2);cache.put(part,human);
                var core=new MorphCuboid.Core(cube,List.of());
                Matrix4f goal=new Matrix4f().translate(0,19/16F,0).scale(0.9375F);
                for(int i=0;i<=1000;i++) {
                    part.resetPose();
                    var posed=ContinuousMorph.reshapeBox(part,core,goal,piece,i/1000F,21+79F*i/1000);
                    check(volume(posed.shape())>0,"Merge loses solid volume");
                    for(var vertex:surface.vertices()) check(MorphCuboid.position(piece,posed.shape(),vertex).isFinite(),"Invalid merge vertex");
                    if(i==1000) check(posed.shape().size().distance(piece.times(0.9375F).size())<0.0001F,"Wrong merge cell endpoint");
                }
            }
            check(Math.abs(total-volume(whole))<0.001F,"Merged cells do not fill creature core");
            check(Math.abs(externalArea-2*(size.x*size.y+size.x*size.z+size.y*size.z))<0.001F,"Duplicate/missing external surface");
            for(int i=0;i<cells.size();i++) for(int j=i+1;j<cells.size();j++) {
                Box a=cells.get(i),b=cells.get(j);
                float overlap=Math.max(0,Math.min(a.maxX(),b.maxX())-Math.max(a.minX(),b.minX()))
                        *Math.max(0,Math.min(a.maxY(),b.maxY())-Math.max(a.minY(),b.minY()))
                        *Math.max(0,Math.min(a.maxZ(),b.maxZ())-Math.max(a.minZ(),b.minZ()));
                check(overlap==0,"Merged solids overlap");
            }
        }
        System.out.println("PASS: 27027 volume-preserving merge poses, complete nonoverlapping core partitions and continuous outer UV seams");
    }
}
