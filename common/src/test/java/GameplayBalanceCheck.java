import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.krona.mutagen.ability.*;
import net.krona.mutagen.strain.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import java.nio.file.*;

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
}
