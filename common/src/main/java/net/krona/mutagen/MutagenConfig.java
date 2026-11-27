package net.krona.mutagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prototype configuration. The remaining settings from the design document will arrive with the features
 * they control.
 */
public final class MutagenConfig {
    public static final String SCOPE_WORLD = "world";
    public static final String SCOPE_PLAYER = "player";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MutagenConfig instance;

    /** Multiplier for absorption and decay rates. */
    public float transformationSpeed = 1.0F;
    /** Progress added by a single dose of 100% pure serum. */
    public float doseStrength = 20.0F;
    /** Humanity penalties, including restrictions on wearing armor. */
    public boolean humanityPenalties = true;
    /** Whether undead strains burn in sunlight. */
    public boolean sunlightBurn = true;
    /** Mutation indicator in the HUD. */
    public boolean hud = true;
    /**
     * HUD indicator corner. Options are {@code top_left} (default), {@code top_right}, {@code
     * bottom_right}, and {@code bottom_left}. Chat overlaps the bottom-left corner, so it is no longer the
     * default.
     */
    public String hudCorner = "top_left";
    /** Replace the player model in full form. Disable if a skin mod conflicts with it. */
    public boolean fullFormRender = true;
    /** Number of genes the body can carry. Chimeras are planned for 0.5. */
    public int maxGenes = 1;
    /** Research scope. With {@code "world"}, research is shared across the world. With {@code "player"}, each player has their own research. */
    public String knowledgeScope = SCOPE_WORLD;
    /** Multiplier for research gained per sequencer run. */
    public float researchSpeed = 1.0F;
    /** Minimum genome purity for the synthesizer to produce two vials instead of one. A value of 101 disables the extra vial. */
    public float doubleSerumPurity = 85.0F;
    /** Render creature parts on the player during hybrid stages III–IV. Disable if a model mod conflicts with them. */
    public boolean hybridRender = true;
    /** Change body proportions and posture as the infection progresses. */
    public boolean bodyMorph = true;
    /** Animate convulsions on stage changes and involuntary twitches from stage III. */
    public boolean bodyMotion = true;
    /** Use Pehkui when installed to adjust the hitbox, eye height, reach, and inertia. */
    public boolean pehkuiIntegration = true;
    /**
     * Creature vision from stage IV lets players see through creeper-green tint, a spider's eight eyes, or
     * a blaze's heat vision. Disabled by default. Press N in-game to toggle it.
     */
    public boolean mobVision = false;
    /** Check CurseForge once per launch and announce available updates in chat. */
    public boolean checkForUpdates = true;
    /** On entering a world or server, show a welcome message the first time and a line about supporting the mod on Ko-fi on later visits. */
    public boolean showSupportMessage = true;

    public static MutagenConfig get() {
        if (instance == null) {
            instance = new MutagenConfig();
        }
        return instance;
    }

    public static void load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try {
                instance = GSON.fromJson(Files.readString(path), MutagenConfig.class);
            } catch (Exception e) {
                instance = new MutagenConfig();
            }
        }
        if (instance == null) {
            instance = new MutagenConfig();
        }
        instance.sanitize();
        save();
    }

    public static void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(get()));
        } catch (IOException ignored) {
            // Use defaults if the config cannot be loaded.
        }
    }

    private void sanitize() {
        transformationSpeed = clamp(transformationSpeed, 0.05F, 20.0F);
        doseStrength = clamp(doseStrength, 0.5F, 100.0F);
        maxGenes = (int) clamp(maxGenes, 1, 5);
        researchSpeed = clamp(researchSpeed, 0.05F, 20.0F);
        doubleSerumPurity = clamp(doubleSerumPurity, 0.0F, 101.0F);
        if (!SCOPE_PLAYER.equals(knowledgeScope)) {
            knowledgeScope = SCOPE_WORLD;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Path configPath() {
        return Platform.getConfigFolder().resolve(Mutagen.MOD_ID + ".json");
    }
}
