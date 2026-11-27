package net.krona.mutagen.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.MutagenConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Looks once per launch on CurseForge for a newer file built for the running Minecraft version
 * and loader. Releases, betas and alphas all count; the type is shown to the player. The request runs on a
 * background thread and any failure (offline, blocked, project not found) is silent: the check never delays
 * or breaks the game. Turn it off with {@code checkForUpdates} in the config.
 */
public final class UpdateChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger("mutagen");
    public static final String MOD_NAME = "Mutagen";
    // private static final String SLUG = "mutagen";
    // private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/" + SLUG + "/version";
    private static final String CURSEFORGE_PROJECT = "https://www.curseforge.com/minecraft/mc-mods/mutagen-evolve";
    // CFWidget provides CurseForge's file metadata, including alpha files, without an API key.
    private static final String CURSEFORGE_API = "https://api.cfwidget.com/1706247";
    private static final Pattern NUMBERS = Pattern.compile("\\d+(?:\\.\\d+)*");
    private static final Pattern CLEAN = Pattern.compile("\\d+(?:\\.\\d+)*(?:-(?:alpha|beta|rc|pre)[.\\w]*)?", Pattern.CASE_INSENSITIVE);

    private static volatile Update found;
    private static boolean started;

    private UpdateChecker() {
    }

    /** Stability of a file, as the sites report it. */
    public enum Type {
        RELEASE, BETA, ALPHA;

        /*
        static Type of(String text) {
            if ("beta".equalsIgnoreCase(text)) {
                return BETA;
            }
            return "alpha".equalsIgnoreCase(text) ? ALPHA : RELEASE;
        }
        */

        /** Suffix of the translation key: empty for a release. */
        public String key() {
            return this == RELEASE ? "" : "." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** A published file: its version text, stability and page. */
    record Candidate(String number, Type type, String url) {
    }

    /** A file newer than the installed one. */
    public record Update(String current, String latest, Type type, String url) {
    }

    /** The newer file, or {@code null} while the check is running, failed, or found nothing. */
    public static Update result() {
        return found;
    }

    /** Starts the background check. Safe to call on every side; only the first call does anything. */
    public static synchronized void start() {
        if (started || !MutagenConfig.get().checkForUpdates) {
            return;
        }
        started = true;
        String current;
        try {
            current = Platform.getMod(Mutagen.MOD_ID).getVersion();
        } catch (RuntimeException e) {
            return;
        }
        String minecraft = Platform.getMinecraftVersion();
        String loader = loader();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        /*
        String query = "?loaders=" + encode("[\"" + loader + "\"]") + "&game_versions=" + encode("[\"" + minecraft + "\"]");
        CompletableFuture<List<Candidate>> files = fetch(client, MODRINTH_API + query, current)
                .thenApply(body -> modrinthFiles(body, SLUG));
        */
        CompletableFuture<List<Candidate>> files = fetch(client, CURSEFORGE_API, current)
                .thenApply(body -> curseForgeFiles(body, minecraft, loader));
        files.thenAccept(candidates -> {
            Update update = choose(candidates, current, minecraft);
            if (update != null) {
                found = update;
                LOGGER.info("{} {} ({}) is available (installed: {}): {}", MOD_NAME, update.latest(),
                        update.type().name().toLowerCase(Locale.ROOT), current, update.url());
            }
        });
    }

    /** The response body, or an empty string when the service is unavailable. */
    private static CompletableFuture<String> fetch(HttpClient client, String url, String current) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mutagen/" + current + " (update check)")
                .header("Accept", "application/json")
                .GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> response.statusCode() == 200 ? response.body() : "")
                .exceptionally(error -> {
                    LOGGER.debug("Update check of {} failed: {}", url, error.toString());
                    return "";
                });
    }

    private static String loader() {
        return Platform.isFabric() ? "fabric" : "neoforge";
    }

    static List<Candidate> curseForgeFiles(String body, String minecraft, String loader) {
        JsonElement response = parse(body);
        if (!response.isJsonObject()) {
            return List.of();
        }
        List<Candidate> result = new ArrayList<>();
        for (JsonObject file : objects(response.getAsJsonObject().get("files"))) {
            JsonElement versions = file.get("versions");
            if (!contains(versions, minecraft) || !contains(versions, loader)) {
                continue;
            }
            Type type = switch (string(file, "type")) {
                case "release" -> Type.RELEASE;
                case "beta" -> Type.BETA;
                case "alpha" -> Type.ALPHA;
                case null, default -> null;
            };
            String id = string(file, "id");
            String number = string(file, "name");
            if (numbers(number, minecraft) == null) {
                number = string(file, "display");
            }
            if (type != null && id != null && id.matches("[0-9]+") && number != null) {
                result.add(new Candidate(number, type, CURSEFORGE_PROJECT + "/files/" + id));
            }
        }
        return result;
    }

    private static boolean contains(JsonElement versions, String expected) {
        if (versions == null || !versions.isJsonArray()) {
            return false;
        }
        for (JsonElement version : versions.getAsJsonArray()) {
            if (version.isJsonPrimitive() && expected.equalsIgnoreCase(version.getAsString())) {
                return true;
            }
        }
        return false;
    }

    /*
    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // Files of a Modrinth version list (already filtered by loader and Minecraft version).
    static List<Candidate> modrinthFiles(String body, String slug) {
        List<Candidate> result = new ArrayList<>();
        for (JsonObject version : objects(parse(body))) {
            String number = string(version, "version_number");
            String id = string(version, "id");
            if (number != null) {
                result.add(new Candidate(number, Type.of(string(version, "version_type")),
                        "https://modrinth.com/mod/" + slug + (id != null ? "/version/" + id : "")));
            }
        }
        return result;
    }
    */

    /** The newest candidate, if it is newer than {@code current}.*/
    static Update choose(List<Candidate> candidates, String current, String minecraft) {
        List<Integer> installed = numbers(current, minecraft);
        if (installed == null) {
            return null;
        }
        Candidate best = null;
        List<Integer> bestNumbers = null;
        for (Candidate candidate : candidates) {
            List<Integer> parsed = numbers(candidate.number(), minecraft);
            if (parsed == null) {
                continue;
            }
            int order = compare(parsed, installed);
            // 1.0.0-rc.2 is older than 1.0.0 although the numbers are equal.
            boolean newer = order > 0
                    || (order == 0 && isPreRelease(current) && candidate.type() == Type.RELEASE && !isPreRelease(candidate.number()));
            if (newer && (best == null || compare(parsed, bestNumbers) > 0)) {
                best = candidate;
                bestNumbers = parsed;
            }
        }
        return best == null ? null : new Update(current, shown(best.number(), minecraft), best.type(), best.url());
    }

    /** What to show as the new version: the numbers with a pre-release tag, without file-name decoration. */
    private static String shown(String number, String minecraft) {
        Matcher matcher = CLEAN.matcher(number.replace(minecraft, " ").replaceFirst("(?i)\\.jar$", ""));
        return matcher.find() ? matcher.group() : number;
    }

    private static List<JsonObject> objects(JsonElement root) {
        List<JsonObject> result = new ArrayList<>();
        if (root != null && root.isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    result.add(element.getAsJsonObject());
                }
            }
        }
        return result;
    }

    private static JsonElement parse(String body) {
        try {
            return JsonParser.parseString(body);
        } catch (RuntimeException e) {
            return JsonNull.INSTANCE;
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** The mod's own numbers from a version or file name, or {@code null} when there are none. */
    private static List<Integer> numbers(String version, String minecraft) {
        if (version == null) {
            return null;
        }
        // The Minecraft version often sits next to the mod's own: 1.21.1-0.3, mod-0.3+1.21.1, mod-0.3.jar.
        Matcher matcher = NUMBERS.matcher(version.replace(minecraft, " "));
        if (!matcher.find()) {
            return null;
        }
        List<Integer> result = new ArrayList<>();
        try {
            for (String part : matcher.group().split("\\.")) {
                result.add(Integer.parseInt(part));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return result;
    }

    private static boolean isPreRelease(String version) {
        return version != null && version.matches("(?i).*-(alpha|beta|rc|pre|snapshot).*");
    }

    private static int compare(List<Integer> a, List<Integer> b) {
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            int x = i < a.size() ? a.get(i) : 0;
            int y = i < b.size() ? b.get(i) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }
}
