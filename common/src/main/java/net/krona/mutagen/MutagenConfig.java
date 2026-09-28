package net.krona.mutagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Конфиг прототипа: то немногое, что уже имеет смысл крутить.
 * Полный набор настроек из дизайн-документа приходит вместе с механиками, которые он описывает.
 */
public final class MutagenConfig {
    public static final String SCOPE_WORLD = "world";
    public static final String SCOPE_PLAYER = "player";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MutagenConfig instance;

    /** Множитель скорости усвоения и отката. */
    public float transformationSpeed = 1.0F;
    /** Сколько прогресса даёт одна доза сыворотки со стопроцентной чистотой. */
    public float doseStrength = 20.0F;
    /** Ограничения человечности: отказ носить броню и прочая цена. */
    public boolean humanityPenalties = true;
    /** Горение на солнце у нежити. */
    public boolean sunlightBurn = true;
    /** Индикатор мутации в интерфейсе. */
    public boolean hud = true;
    /** Полная подмена модели на пятой стадии. Выключите, если конфликтует с модом на скины. */
    public boolean fullFormRender = true;
    /** Сколько генов может держать тело. Химеры приходят в 0.5. */
    public int maxGenes = 1;
    /** Чья изученность: {@code "world"} — общая на мир, {@code "player"} — у каждого своя. */
    public String knowledgeScope = SCOPE_WORLD;
    /** Множитель прироста изученности за прогон секвенатора. */
    public float researchSpeed = 1.0F;
    /** С какой чистоты генома синтезатор выдаёт две ампулы вместо одной. 101 — никогда. */
    public float doubleSerumPurity = 85.0F;
    /** Гибридные стадии III–IV: части существа на теле игрока. Выключите, если конфликтует с модом на модели. */
    public boolean hybridRender = true;
    /** Пропорции и осанка тела меняются вместе с заражением. */
    public boolean bodyMorph = true;
    /** Судороги на переходе стадий и непроизвольные подёргивания со стадии III. */
    public boolean bodyMotion = true;
    /** Если установлен Pehkui, тело меняется через него: хитбокс, глаза, дальность рук и инерция. */
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
            // конфиг не критичен: работаем на значениях по умолчанию
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
