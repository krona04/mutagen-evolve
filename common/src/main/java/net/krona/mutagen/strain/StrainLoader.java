package net.krona.mutagen.strain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.krona.mutagen.Mutagen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads strains from datapacks: {@code data/<namespace>/mutagen/strains/<name>.json}.
 * <p>
 * Runs on server start and on every {@code /reload}. A broken file doesn't break loading:
 * it is skipped, and the reason is logged and shown to operators in chat.
 */
public final class StrainLoader extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = Mutagen.MOD_ID + "/strains";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();

    private static volatile Result pending;

    public StrainLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        List<Strain> strains = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            ResourceLocation id = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                errors.add(id + ": not a JSON object");
                continue;
            }
            // The strain id is the file path; the field in the JSON itself isn't needed and is overwritten.
            JsonObject json = entry.getValue().getAsJsonObject().deepCopy();
            json.addProperty("id", id.toString());

            DataResult<Strain> result = Strain.CODEC.parse(JsonOps.INSTANCE, json);
            result.resultOrPartial(message -> errors.add(id + ": " + message)).ifPresent(strain -> {
                if (result.error().isEmpty()) {
                    strains.add(strain);
                }
            });
        }

        for (String error : errors) {
            LOGGER.error("[Mutagen] Skipped strain {}", error);
        }
        Strains.replace(strains);
        LOGGER.info("[Mutagen] Loaded {} strains ({} skipped)", Strains.count(), errors.size());
        pending = new Result(Strains.count(), List.copyOf(errors));
    }

    /**
     * Result of the last load, if nobody has taken it yet. The server takes it in its tick,
     * broadcasts the new strains to players and tells operators how it went.
     */
    public static Result consumePending() {
        Result result = pending;
        pending = null;
        return result;
    }

    public record Result(int loaded, List<String> errors) {
    }
}
