package net.krona.mutagen.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.Locale;

/** Explicit environmental requirements, shared by server authority and client readiness. */
public enum AbilityRequirement {
    ANY, WATER, DRY, AIRBORNE, GROUND, NETHER, DARK;
    public static final Codec<AbilityRequirement> CODEC=Codec.STRING.comapFlatMap(value -> {
        try { return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT))); }
        catch(IllegalArgumentException error) { return DataResult.error(() -> "Unknown ability requirement: "+value); }
    }, value -> value.name().toLowerCase(Locale.ROOT));

    public String key() { return "mutagen.ability.requires."+name().toLowerCase(Locale.ROOT); }
    public boolean test(Player player) {
        return allows(player.isInWater(),player.level().isRainingAt(player.blockPosition()),player.onGround(),
                player.level().dimension().equals(Level.NETHER),player.level().getMaxLocalRawBrightness(player.blockPosition()));
    }
    public boolean allows(boolean water,boolean rain,boolean ground,boolean nether,int light) {
        return switch(this) {
            case ANY -> true; case WATER -> water; case DRY -> !water&&!rain;
            case AIRBORNE -> !ground&&!water; case GROUND -> ground&&!water;
            case NETHER -> nether; case DARK -> light<=7;
        };
    }
}
