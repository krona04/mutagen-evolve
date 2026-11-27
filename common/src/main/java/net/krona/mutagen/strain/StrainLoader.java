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
 * Load strains from {@code data/<namespace>/mutagen/strains/<name>.json}.
 * <p>
 * Run on server startup and on {@code /reload}. Skip invalid files and report the reasons in logs and
 * operator chat.
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
            // Derive the strain ID from the file path and overwrite any ID in the JSON.
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
     * Return the latest reload result once. The server consumes it during a tick, sends the registry to
     * clients, and reports the outcome to operators.
     */
    public static Result consumePending() {
        Result result = pending;
        pending = null;
        return result;
    }

    public record Result(int loaded, List<String> errors) {
    }
}
