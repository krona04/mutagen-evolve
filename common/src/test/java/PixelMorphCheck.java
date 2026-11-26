import net.krona.mutagen.client.body.*;

public class PixelMorphCheck {
    public static void main(String[] args) {
        int human=0x4273B2, creature=0xE09955;
        var mask=new InfectionMask(123456789);
        var same=new InfectionMask(123456789);
        var other=new InfectionMask(987654321);
        int middleChanged=0, differentSeeds=0;
        for(int y=0;y<64;y++) for(int x=0;x<64;x++) {
            float u=(x+0.5F)/64, v=(y+0.5F)/64;
            boolean alreadyChanged=false;
            for(int step=0;step<=100;step++) {
                float phase=step/100F;
                int colour=PixelMorph.colour(human,creature,mask,u,v,phase);
                if(colour!=human&&colour!=creature) throw new AssertionError("Global colour blending returned");
                if(alreadyChanged&&colour!=creature) throw new AssertionError("Pixel infection reversed without progress reversal");
                alreadyChanged=colour==creature;
                if(step==0&&colour!=human || step==100&&colour!=creature) throw new AssertionError("Wrong material endpoint");
                if(colour!=PixelMorph.colour(human,creature,same,u,v,phase)) throw new AssertionError("Mask changes between frames");
                if(colour!=PixelMorph.colour(human,creature,mask,(x+0.1F)/64,(y+0.9F)/64,phase))
                    throw new AssertionError("Pattern depends on atlas subpixel sampling");
            }
            int middle=PixelMorph.colour(human,creature,mask,u,v,0.5F);
            if(middle==creature) middleChanged++;
            if(middle!=PixelMorph.colour(human,creature,other,u,v,0.5F)) differentSeeds++;
        }
        if(middleChanged<500||middleChanged>3596) throw new AssertionError("Texture changes all at once");
        if(differentSeeds==0) throw new AssertionError("Players share an identical infection layout");
        System.out.println("PASS: 413696 texel transitions; pixel selection, progressive patches, stable seed and atlas-independent pattern");
    }
}
