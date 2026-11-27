import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.krona.mutagen.ability.*;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.network.MutagenNetwork.AbilityUsedPayload;
import net.krona.mutagen.network.MutagenNetwork.SyncPayload;
import net.krona.mutagen.strain.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import java.nio.file.*;
import java.util.List;

/** Real registry/codec checks plus regressions for energy, contact targets and environment gates. */
public final class GameplayBalanceCheck {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        var stdout = System.out;
        var stderr = System.err;
        try {
            Bootstrap.bootStrap();
        } finally {
            System.setOut(stdout);
            System.setErr(stderr);
        }
        check(AbilityBudget.food(20, 0, .25F).food() == 19, "Fractional hunger cannot become free");
        check(AbilityBudget.food(20, 3, 2.5F).saturation() == .5F, "Spend saturation first");
        check(AbilityBudget.food(2, .5F, 3) == null, "Reject unaffordable cost");
        check(!AbilityBudget.healthAvailable(1, 1), "Cost cannot consume last HP");
        check(AbilityBudget.healthAfter(0, 2) == 0, "Payment must never revive a player");
        check(AbilityBudget.healthAfter(8, 4) == 4, "Health price ignores combat defenses");
        check(!AbilityTargets.eligible(true,false,false,false,false,10,3,true), "Sphere excludes AABB corners");
        check(!AbilityTargets.eligible(true,false,false,false,false,1,3,false), "Walls block attacks");
        check(!AbilityTargets.eligible(true,false,false,true,false,1,3,true), "Allies excluded");
        check(!AbilityTargets.eligible(true,false,false,false,true,1,3,true), "Owned pets excluded");
        check(!AbilityTargets.eligible(true,false,true,false,false,1,3,true), "Creative players excluded");
        check(!AbilityRequirement.DRY.allows(false,true,true,false,15), "Rain blocks fire skills");
        check(AbilityRequirement.WATER.allows(true,false,false,false,15), "Water unlocks aquatic skills");
        check(!AbilityRequirement.AIRBORNE.allows(true,false,false,false,15), "Swimming is not diving flight");
        check(!AbilityRequirement.DARK.allows(false,false,true,false,8), "Night veil light boundary");
        check(AbilityRequirement.DARK.allows(false,false,true,false,7), "Night veil in darkness");
        checkPlayerSync();
        Path resources=Path.of("common/src/main/resources");
        JsonObject en=JsonParser.parseString(Files.readString(resources.resolve("assets/mutagen/lang/en_us.json"))).getAsJsonObject();
        JsonObject ru=JsonParser.parseString(Files.readString(resources.resolve("assets/mutagen/lang/ru_ru.json"))).getAsJsonObject();
        int strains=0, abilities=0;
        try (var files=Files.list(resources.resolve("data/mutagen/mutagen/strains"))) {
            for (Path file : files.filter(p->p.toString().endsWith(".json")).toList()) {
                String id=file.getFileName().toString().replace(".json", "");
                JsonObject json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                JsonObject identified=json.deepCopy();
                identified.addProperty("id", "mutagen:"+id);
                // Standalone vanilla bootstrap has no mod item registry; validate the known catalyst
                // identifier separately, and use its vanilla equivalent for the rest of the schema.
                JsonObject synthesis=identified.getAsJsonObject("synthesis");
                if (synthesis != null && synthesis.has("catalyst")
                        && synthesis.get("catalyst").getAsString().startsWith("mutagen:")) {
                    check(synthesis.get("catalyst").getAsString().equals("mutagen:catalyst"),id+" known mod catalyst");
                    synthesis.addProperty("catalyst","minecraft:slime_ball");
                }
                Strain parsed=Strain.CODEC.parse(JsonOps.INSTANCE, identified).getOrThrow();
                check(parsed.id().getPath().equals(id), id+" complete strain loads");
                check(en.has("mutagen.role."+id)&&ru.has("mutagen.role."+id), id+" role translations");
                check(json.getAsJsonArray("abilities").size()==3, id+" has three slots");
                int index=0;
                for (JsonElement value : json.getAsJsonArray("abilities")) {
                    Ability a=Abilities.CODEC.parse(JsonOps.INSTANCE,value).getOrThrow();
                    check(a.minStage()==new Stage[]{Stage.MANIFESTATION,Stage.MUTATION,Stage.FULL}[index],id+" unlock stages");
                    check(a.apex()==(index==2),id+" apex flag");
                    JsonElement encoded=Abilities.CODEC.encodeStart(JsonOps.INSTANCE,a).getOrThrow();
                    Ability roundtrip=Abilities.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();
                    check(roundtrip.requirement()==a.requirement(), id+" requirement survives network codec");
                    check(roundtrip.type()==a.type(), id+" ability type survives codec");
                    JsonObject legacy=value.getAsJsonObject().deepCopy();
                    legacy.remove("requires");
                    check(Abilities.CODEC.parse(JsonOps.INSTANCE,legacy).getOrThrow().requirement()==AbilityRequirement.ANY,
                            id+" old datapacks default to any environment");
                    legacy.addProperty("requires","unknown_environment");
                    check(Abilities.CODEC.parse(JsonOps.INSTANCE,legacy).error().isPresent(),id+" invalid environment rejected");
                    String name=value.getAsJsonObject().get("name").getAsString();
                    check(en.has(name)&&ru.has(name),id+" translated ability "+name);
                    String help="mutagen.ability_help."+id+"."+index;
                    check(en.has(help)&&ru.has(help),id+" translated help");
                    check(a.cooldown()>0&&a.cost().hunger()>0,id+" nonfree cooldown and cost");
                    index++; abilities++;
                }
                boolean advantage=false, weakness=false;
                for (JsonElement value : json.getAsJsonArray("traits")) {
                    Trait t=Traits.CODEC.parse(JsonOps.INSTANCE,value).getOrThrow();
                    if(t.weakness()) weakness=true; else advantage=true;
                    String type=value.getAsJsonObject().get("type").getAsString();
                    check(!type.equals("mutagen:no_eating")&&!type.equals("mutagen:inventory_limit"),id+" no free energy or locked backpack");
                }
                check(advantage&&weakness,id+" has advantages and tradeoffs");
                strains++;
            }
        }
        check(strains==12&&abilities==36,"Complete shipped roster");
        System.out.println("Gameplay checks passed: "+checks+" checks, "+strains+" strains, "+abilities+" abilities.");
    }

    private static void checkPlayerSync() {
        ResourceLocation activeId = ResourceLocation.parse("mutagen:active");
        ResourceLocation dormantId = ResourceLocation.parse("mutagen:dormant");
        ResourceLocation emptyId = ResourceLocation.parse("mutagen:empty");
        ResourceLocation pendingId = ResourceLocation.parse("mutagen:pending");
        Strain active = Strain.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                """
                {"id":"mutagen:active","entity":"minecraft:zombie","synthesis":{"catalyst":"minecraft:slime_ball"}}
                """)).getOrThrow();
        MutagenData saved = new MutagenData();
        saved.getOrCreate(activeId).setProgress(25.0F);
        saved.getOrCreate(dormantId).setProgress(100.0F);
        saved.getOrCreate(emptyId).setLocked(true);
        saved.getOrCreate(pendingId).setPending(5.0F);
        for (int i = 0; i < 14; i++) {
            saved.getOrCreate(ResourceLocation.fromNamespaceAndPath("mutagen", "extra_" + i)).setProgress(50.0F);
        }
        MutagenData loaded = new MutagenData();
        loaded.load(saved.save());
        check(loaded.gene(emptyId) == null, "Legacy empty gene no longer occupies a slot");
        check(loaded.gene(pendingId).pending() == 5.0F, "Unabsorbed doses survive loading");
        Strains.replace(List.of(active));
        try {
            check(loaded.activeGeneCount() == 1, "Missing strains do not count toward the gene limit");
            check(loaded.primary().strainId().equals(activeId), "A missing full form cannot mask a loaded strain");
            check(loaded.gene(dormantId).progress() == 100.0F, "Missing strains retain their progress");
        } finally {
            Strains.replace(List.of());
        }

        var dataBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SyncPayload.STREAM_CODEC.encode(dataBuffer, new SyncPayload(1, loaded.snapshot()));
            SyncPayload decoded = SyncPayload.STREAM_CODEC.decode(dataBuffer);
            check(decoded.genes().size() == 17, "More than 16 saved genes survive network encoding");

            AbilityUsedPayload timer = new AbilityUsedPayload(activeId, 2, 3_000_000_200L, 200);
            AbilityUsedPayload.STREAM_CODEC.encode(dataBuffer, timer);
            AbilityUsedPayload restored = AbilityUsedPayload.STREAM_CODEC.decode(dataBuffer);
            check(restored.equals(timer), "Cooldown packet preserves strain and world tick");
            check(restored.remaining(3_000_000_050L) == 150, "Reconnect restores remaining cooldown, not its full duration");
            check(restored.remaining(3_000_000_200L) == 0, "Expired cooldown is ready");
        } finally {
            dataBuffer.release();
        }

        ResourceLocation shader = ResourceLocation.parse("mutagen:shaders/post/test.json");
        Strain.Vision fullOnly = new Strain.Vision(shader, Stage.FULL);
        check(fullOnly.strength(99.0F) == 0.0F, "Full-only vision stays off below full form");
        check(fullOnly.strength(100.0F) == 1.0F, "Full-only vision turns on at full form");
        check(new Strain.Vision(shader, Stage.MANIFESTATION).strength(100.0F) == 1.0F,
                "Existing gradual vision still reaches full intensity");
    }
}
