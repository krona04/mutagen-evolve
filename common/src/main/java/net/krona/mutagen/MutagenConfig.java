package net.krona.mutagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prototype config: the few things that already make sense to tweak.
 * The full set of settings from the design document arrives together with the mechanics it describes.
 */
public final class MutagenConfig {
    public static final String SCOPE_WORLD = "world";
    public static final String SCOPE_PLAYER = "player";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MutagenConfig instance;

    /** Multiplier for absorption and decay speed. */
    public float transformationSpeed = 1.0F;
    /** How much progress one serum dose of 100% purity gives. */
    public float doseStrength = 20.0F;
    /** Humanity restrictions: refusing to wear armor and other costs. */
    public boolean humanityPenalties = true;
    /** Undead burning in the sun. */
    public boolean sunlightBurn = true;
    /** Mutation indicator in the HUD. */
    public boolean hud = true;
    /** Full model replacement at the fifth stage. Disable if it conflicts with a skin mod. */
    public boolean fullFormRender = true;
    /** How many genes the body can hold. Chimeras arrive in 0.5. */
    public int maxGenes = 1;
    /** Whose research: {@code "world"} — shared across the world, {@code "player"} — each their own. */
    public String knowledgeScope = SCOPE_WORLD;
    /** Multiplier for research gained per sequencer run. */
    public float researchSpeed = 1.0F;
    /** From which genome purity the synthesizer yields two ampoules instead of one. 101 — never. */
    public float doubleSerumPurity = 85.0F;
    /** Hybrid stages III–IV: creature parts on the player's body. Disable if it conflicts with a model mod. */
    public boolean hybridRender = true;
    /** Body proportions and posture change along with the infection. */
    public boolean bodyMorph = true;
    /** Convulsions on stage transitions and involuntary twitches from stage III. */
    public boolean bodyMotion = true;
    /** If Pehkui is installed, the body changes through it: hitbox, eyes, reach and inertia. */
    public boolean pehkuiIntegration = true;

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
            // config is not critical: run on default values
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
