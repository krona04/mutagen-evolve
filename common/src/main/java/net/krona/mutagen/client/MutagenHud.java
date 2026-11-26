package net.krona.mutagen.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.MutagenConfig;
import net.krona.mutagen.data.MutagenData;
import net.krona.mutagen.data.MutagenPlayer;
import net.krona.mutagen.strain.Stage;
import net.krona.mutagen.strain.Strain;
import net.krona.mutagen.strain.Strains;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;
import java.util.Locale;

/**
 * Индикатор мутации: компактная панель со штаммом, стадией, прогрессом, запасом усвоения и способностями.
 * Игрок должен понимать, что с ним происходит, не открывая команд.
 * <p>
 * Панель стоит в углу, который выбран в настройке {@code hudCorner} (по умолчанию верхний левый): внизу
 * слева её закрывал чат. Если в том же углу открыт экран отладки или иконки эффектов, панель уходит
 * от них, а не рисуется поверх.
 */
@Environment(EnvType.CLIENT)
public final class MutagenHud {
    private static final int MARGIN = 4;
    private static final int PADDING = 4;
    private static final int WIDTH = 124;
    private static final int BAR_HEIGHT = 4;
    private static final int ROW = 11;

    private static final int PANEL = 0x90101010;
    private static final int EDGE = 0x40FFFFFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8A8A8A;
    private static final int LOCKED = 0xFF5E5E5E;
    private static final int COOLING = 0xFFC9B87A;

    private MutagenHud() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        renderFlash(graphics, minecraft);
        LockedSlots.renderHotbar(graphics, minecraft);

        if (!MutagenConfig.get().hud) {
            return;
        }
        MutagenData data = MutagenPlayer.of(minecraft.player);
        MutagenData.Gene gene = data.primary();
        if (gene == null) {
            return;
        }
        Strain strain = Strains.get(gene.strainId());
        if (strain == null) {
            return;
        }

        Font font = minecraft.font;
        List<ClientAbilities.Slot> slots = ClientAbilities.slots();
        boolean debug = minecraft.getDebugOverlay().showDebugScreen();
        int height = PADDING * 2 + ROW + BAR_HEIGHT + 3 + slots.size() * ROW + (debug ? ROW : 0);

        String corner = corner(minecraft, debug);
        boolean right = corner.endsWith("right");
        boolean bottom = corner.startsWith("bottom");
        int x = right ? graphics.guiWidth() - WIDTH - MARGIN : MARGIN;
        int y = bottom ? graphics.guiHeight() - height - MARGIN : MARGIN + topOffset(minecraft, right, bottom);

        // Фон и тонкая полоса цвета штамма слева — видно, чей это ген, ещё до того, как прочитано название.
        graphics.fill(x, y, x + WIDTH, y + height, PANEL);
        graphics.renderOutline(x, y, WIDTH, height, EDGE);
        graphics.fill(x, y, x + 2, y + height, 0xFF000000 | strain.color());

        int left = x + PADDING + 2;
        int inner = WIDTH - PADDING * 2 - 2;
        int row = y + PADDING;

        // Название штамма и стадия справа, цветом стадии.
        Stage stage = gene.stage();
        Component numeral = Component.literal(stage.numeral()).withStyle(stage.color());
        int numeralWidth = font.width(numeral);
        String name = font.plainSubstrByWidth(strain.displayName().getString(), inner - numeralWidth - 6);
        graphics.drawString(font, name, left, row, TEXT, true);
        if (gene.locked()) {
            graphics.drawString(font, "■", left + inner - numeralWidth - 9, row, 0xFFFFD24A, true);
        }
        graphics.drawString(font, numeral, left + inner - numeralWidth, row, TEXT, true);
        row += ROW;

        // Прогресс и запас усвоения, с процентом справа.
        String percent = Math.round(gene.progress()) + "%";
        int percentWidth = font.width(percent);
        int barWidth = inner - percentWidth - 4;
        int barY = row + 1;
        graphics.fill(left, barY, left + barWidth, barY + BAR_HEIGHT, 0xFF2A2A2A);
        int progress = Mth.ceil(barWidth * gene.progress() / 100.0F);
        int pending = Mth.ceil(barWidth * Math.min(100.0F, gene.progress() + gene.pending()) / 100.0F);
        if (pending > progress) {
            graphics.fill(left + progress, barY, left + pending, barY + BAR_HEIGHT, tint(strain.color(), 110));
        }
        graphics.fill(left, barY, left + progress, barY + BAR_HEIGHT, 0xFF000000 | strain.color());
        // Отметки стадий: видно, сколько осталось до следующей.
        for (Stage mark : Stage.values()) {
            if (mark.threshold() > 1 && mark.threshold() < 100) {
                int markX = left + barWidth * mark.threshold() / 100;
                graphics.fill(markX, barY, markX + 1, barY + BAR_HEIGHT, 0x66000000);
            }
        }
        graphics.drawString(font, percent, left + inner - percentWidth, row - 1, MUTED, false);
        row += BAR_HEIGHT + 3;

        renderAbilities(graphics, font, minecraft, gene, slots, left, row, inner, strain.color());
        row += slots.size() * ROW;

        if (debug) {
            graphics.drawString(font, String.format(Locale.ROOT, "%.1f%% +%.1f · human %.0f%%",
                    gene.progress(), gene.pending(), data.humanity()), left, row + 1, MUTED, false);
        }
    }

    /**
     * Способности: клавиша в рамке, название и перезарядка — полосой под строкой и секундами справа.
     * Ещё не открытые показаны тускло со стадией, с которой они придут, — видно, что тело ещё не
     * доросло, а не что их нет вовсе.
     */
    private static void renderAbilities(GuiGraphics graphics, Font font, Minecraft minecraft, MutagenData.Gene gene,
                                        List<ClientAbilities.Slot> slots, int x, int y, int width, int color) {
        int keySlot = 0;
        int line = 0;
        for (ClientAbilities.Slot slot : slots) {
            String key;
            if (slot.ability().apex()) {
                key = MutagenClient.APEX_KEY.getTranslatedKeyMessage().getString();
            } else if (keySlot < MutagenClient.ABILITY_KEYS.length) {
                key = MutagenClient.ABILITY_KEYS[keySlot++].getTranslatedKeyMessage().getString();
            } else {
                continue;
            }
            int row = y + line * ROW;
            line++;

            int seconds = ClientAbilities.cooldownSeconds(slot.index());
            boolean ready = slot.unlocked() && seconds == 0;
            int textColor = !slot.unlocked() ? LOCKED : ready ? TEXT : COOLING;

            // Клавиша в рамке.
            String shortKey = font.plainSubstrByWidth(key.toUpperCase(Locale.ROOT), 18);
            int keyWidth = Math.max(9, font.width(shortKey) + 4);
            graphics.fill(x, row, x + keyWidth, row + 9, ready ? tint(color, 200) : 0xFF303030);
            graphics.drawString(font, shortKey, x + (keyWidth - font.width(shortKey)) / 2 + 1, row + 1,
                    ready ? 0xFF101010 : textColor, false);

            // Справа: секунды перезарядки, звезда апекса или настоящая причина, по которой способность
            // сейчас недоступна, — стадия, изученность генома, среда или нехватка сил.
            String status = seconds > 0 ? seconds + "s"
                    : slot.unlocked() ? (slot.ability().apex() ? "★" : "") : reason(minecraft, gene, slot);
            int statusWidth = font.width(status);
            int nameX = x + keyWidth + 4;
            String name = font.plainSubstrByWidth(slot.ability().name().getString(),
                    width - (nameX - x) - statusWidth - 4);
            graphics.drawString(font, name, nameX, row + 1, textColor, true);
            if (!status.isEmpty()) {
                graphics.drawString(font, status, x + width - statusWidth, row + 1,
                        slot.ability().apex() && ready ? 0xFFFFD24A : MUTED, false);
            }

            float cooldown = ClientAbilities.cooldownFraction(slot.index());
            if (cooldown > 0.0F) {
                int span = width - (nameX - x);
                graphics.fill(nameX, row + 9, nameX + Mth.ceil(span * cooldown), row + 10, tint(color, 170));
            }
        }
    }

    /** Короткая метка причины, по которой способность сейчас недоступна. */
    private static String reason(Minecraft minecraft, MutagenData.Gene gene, ClientAbilities.Slot slot) {
        net.krona.mutagen.ability.Ability ability = slot.ability();
        if (!gene.stage().atLeast(ability.minStage())) {
            return ability.minStage().numeral();
        }
        if (ability.apex() && net.krona.mutagen.knowledge.ClientKnowledge.research(gene.strainId())
                < net.krona.mutagen.knowledge.Research.MASTERED) {
            return "★ " + Math.round(net.krona.mutagen.knowledge.ClientKnowledge.research(gene.strainId())) + "%";
        }
        if (minecraft.player != null && !ability.requirement().test(minecraft.player)) {
            return switch (ability.requirement()) {
                case WATER -> "≈";
                case DRY -> "☂";
                case AIRBORNE -> "↑";
                case GROUND -> "↓";
                case NETHER -> "N";
                case DARK -> "☾";
                case ANY -> "";
            };
        }
        // Остаётся одно: не хватает сытости или здоровья.
        return "♥";
    }

    /**
     * Угол из настройки. Экран отладки занимает верхние углы целиком: пока он открыт, панель уходит вниз
     * в правый угол, где кроме подсказок ничего нет.
     */
    private static String corner(Minecraft minecraft, boolean debug) {
        String corner = MutagenConfig.get().hudCorner == null ? "top_left"
                : MutagenConfig.get().hudCorner.toLowerCase(Locale.ROOT);
        if (!corner.equals("top_right") && !corner.equals("bottom_right") && !corner.equals("bottom_left")) {
            corner = "top_left";
        }
        if (debug && corner.startsWith("top")) {
            return "bottom_right";
        }
        return corner;
    }

    /** Справа сверху ваниль рисует иконки эффектов: панель встаёт под них. */
    private static int topOffset(Minecraft minecraft, boolean right, boolean bottom) {
        if (!right || bottom || minecraft.player == null) {
            return 0;
        }
        boolean beneficial = false;
        boolean harmful = false;
        for (MobEffectInstance effect : minecraft.player.getActiveEffects()) {
            if (!effect.showIcon()) {
                continue;
            }
            if (effect.getEffect().value().isBeneficial()) {
                beneficial = true;
            } else {
                harmful = true;
            }
        }
        return harmful ? 52 : beneficial ? 26 : 0;
    }

    private static int tint(int rgb, int alpha) {
        return FastColor.ARGB32.color(alpha, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }

    private static void renderFlash(GuiGraphics graphics, Minecraft minecraft) {
        float alpha = MutagenClientState.flashAlpha();
        if (alpha <= 0.0F) {
            return;
        }
        int color = MutagenClientState.flashColor();
        int argb = FastColor.ARGB32.color((int) (alpha * 110), (color >> 16) & 0xFF,
                (color >> 8) & 0xFF, color & 0xFF);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), argb);

        Stage stage = MutagenClientState.lastStage();
        if (stage != Stage.NONE && alpha > 0.35F) {
            Component text = Component.translatable(
                    MutagenClientState.lastUp() ? "mutagen.hud.stage_up" : "mutagen.hud.stage_down",
                    stage.displayName());
            int width = minecraft.font.width(text);
            graphics.drawString(minecraft.font, text,
                    (graphics.guiWidth() - width) / 2, graphics.guiHeight() / 2 - 30,
                    0xFFFFFFFF, true);
        }
    }
}
