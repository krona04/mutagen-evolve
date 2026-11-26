package net.krona.mutagen.strain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Пять стадий трансформации. Порог — минимальный прогресс, с которого стадия считается достигнутой.
 */
public enum Stage {
    NONE("none", 0, "", ChatFormatting.GRAY),
    INFECTION("infection", 1, "I", ChatFormatting.DARK_GREEN),
    MANIFESTATION("manifestation", 21, "II", ChatFormatting.GREEN),
    MUTATION("mutation", 41, "III", ChatFormatting.YELLOW),
    DOMINATION("domination", 71, "IV", ChatFormatting.GOLD),
    FULL("full", 100, "V", ChatFormatting.RED);

    /**
     * В датапаке стадия пишется словом ({@code "mutation"}), римской цифрой ({@code "III"})
     * или номером ({@code 3}) — как автору штамма удобнее.
     */
    public static final Codec<Stage> CODEC = Codec.withAlternative(
            Codec.STRING.comapFlatMap(Stage::parse, Stage::key),
            Codec.intRange(0, 5).xmap(index -> values()[index], Enum::ordinal));

    private final String key;
    private final int threshold;
    private final String numeral;
    private final ChatFormatting color;

    Stage(String key, int threshold, String numeral, ChatFormatting color) {
        this.key = key;
        this.threshold = threshold;
        this.numeral = numeral;
        this.color = color;
    }

    public static Stage of(float progress) {
        Stage result = NONE;
        for (Stage stage : values()) {
            if (progress >= stage.threshold) {
                result = stage;
            }
        }
        return result;
    }

    private static DataResult<Stage> parse(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (Stage stage : values()) {
            if (stage.key.equals(normalized) || stage.numeral.toLowerCase(Locale.ROOT).equals(normalized)) {
                return DataResult.success(stage);
            }
        }
        return DataResult.error(() -> "Unknown stage: " + name);
    }

    public String key() {
        return key;
    }

    public int threshold() {
        return threshold;
    }

    public String numeral() {
        return numeral;
    }

    public ChatFormatting color() {
        return color;
    }

    public boolean atLeast(Stage other) {
        return ordinal() >= other.ordinal();
    }

    public Component displayName() {
        return Component.translatable("mutagen.stage." + key);
    }

    public String translationKey() {
        return "mutagen.stage." + key;
    }
}
