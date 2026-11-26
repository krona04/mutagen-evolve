package net.krona.mutagen.client.screen;

import dev.architectury.networking.NetworkManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.menu.BioWorkbenchMenu;
import net.krona.mutagen.network.MutagenNetwork;
import net.krona.mutagen.recipe.BioWorkbenchRecipe;
import net.krona.mutagen.registry.MutagenRecipes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Экран верстака биолога. Слева — список всех сборок: что получится и что для этого нужно.
 * Клик по строке раскладывает ингредиенты из инвентаря; то, что уже можно собрать, подсвечено.
 */
@Environment(EnvType.CLIENT)
public class BioWorkbenchScreen extends AbstractContainerScreen<BioWorkbenchMenu> {
    private static final ResourceLocation TEXTURE = Mutagen.id("textures/gui/container/bio_workbench.png");
    private static final int PANEL_WIDTH = 112;
    private static final int ROW = 20;
    private static final int PANEL_PAD = 4;

    private int scroll;

    public BioWorkbenchScreen(BioWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        // Окно и панель рецептов вместе стоят по центру экрана.
        leftPos = (width - imageWidth - PANEL_WIDTH) / 2 + PANEL_WIDTH;
    }

    private List<RecipeHolder<BioWorkbenchRecipe>> recipes() {
        if (minecraft == null || minecraft.level == null) {
            return List.of();
        }
        List<RecipeHolder<BioWorkbenchRecipe>> list = new ArrayList<>(minecraft.level.getRecipeManager()
                .getAllRecipesFor(MutagenRecipes.BIO_WORKBENCH_TYPE.get()));
        list.sort(Comparator.comparing(holder -> holder.value().result().getHoverName().getString()));
        return list;
    }

    private int panelLeft() {
        return leftPos - PANEL_WIDTH;
    }

    private int visibleRows() {
        return (imageHeight - PANEL_PAD * 2) / ROW;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        int px = panelLeft();
        int right = leftPos - 2;
        graphics.fill(px, topPos, right, topPos + imageHeight, 0xFF555555);
        graphics.fill(px + 1, topPos + 1, right - 1, topPos + imageHeight - 1, 0xFFC6C6C6);

        List<RecipeHolder<BioWorkbenchRecipe>> recipes = recipes();
        scroll = Mth.clamp(scroll, 0, Math.max(0, recipes.size() - visibleRows()));
        int time = (int) (System.currentTimeMillis() / 1000L);

        for (int i = 0; i < visibleRows() && i + scroll < recipes.size(); i++) {
            BioWorkbenchRecipe recipe = recipes.get(i + scroll).value();
            int y = topPos + PANEL_PAD + i * ROW;
            boolean hovered = mouseX >= px + 2 && mouseX < right - 2 && mouseY >= y && mouseY < y + ROW;
            int background = canCraft(recipe) ? 0xFF9FC79F : 0xFFB5B5B5;
            graphics.fill(px + 3, y, right - 3, y + ROW - 1, hovered ? 0xFFE0E0E0 : background);

            graphics.renderItem(recipe.result(), px + 5, y + 2);
            graphics.renderItemDecorations(font, recipe.result(), px + 5, y + 2);
            graphics.fill(px + 24, y + 3, px + 25, y + ROW - 4, 0xFF777777);

            int ix = px + 28;
            for (BioWorkbenchRecipe.CountedIngredient ingredient : recipe.ingredients()) {
                if (ix > right - 20) {
                    break;
                }
                ItemStack shown = displayed(ingredient.ingredient(), time);
                graphics.renderItem(shown, ix, y + 2);
                graphics.renderItemDecorations(font, shown, ix, y + 2,
                        ingredient.count() > 1 ? String.valueOf(ingredient.count()) : null);
                ix += 18;
            }
        }
    }

    /** Для тегов показываем предметы по очереди, раз в секунду. */
    private static ItemStack displayed(Ingredient ingredient, int time) {
        ItemStack[] items = ingredient.getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[Math.floorMod(time, items.length)];
    }

    /** Хватает ли ингредиентов в инвентаре и на столе, чтобы собрать рецепт хотя бы раз. */
    private boolean canCraft(BioWorkbenchRecipe recipe) {
        for (BioWorkbenchRecipe.CountedIngredient ingredient : recipe.ingredients()) {
            if (available(ingredient.ingredient()) < ingredient.count()) {
                return false;
            }
        }
        return true;
    }

    private int available(Ingredient ingredient) {
        int count = 0;
        for (int index = 0; index < menu.slots.size(); index++) {
            if (index == BioWorkbenchMenu.RESULT_SLOT) {
                continue;
            }
            ItemStack stack = menu.slots.get(index).getItem();
            if (!stack.isEmpty() && ingredient.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderRecipeTooltip(graphics, mouseX, mouseY);
    }

    private void renderRecipeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        RecipeHolder<BioWorkbenchRecipe> holder = recipeAt(mouseX, mouseY);
        if (holder == null) {
            return;
        }
        BioWorkbenchRecipe recipe = holder.value();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(recipe.result().getCount() > 1 ? recipe.result().getCount() + " × " : "")
                .append(recipe.result().getHoverName()).withStyle(ChatFormatting.WHITE));
        int time = (int) (System.currentTimeMillis() / 1000L);
        for (BioWorkbenchRecipe.CountedIngredient ingredient : recipe.ingredients()) {
            boolean enough = available(ingredient.ingredient()) >= ingredient.count();
            lines.add(Component.literal(ingredient.count() + " × ")
                    .append(displayed(ingredient.ingredient(), time).getHoverName())
                    .withStyle(enough ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        lines.add(Component.translatable("mutagen.bio_workbench.fill").withStyle(ChatFormatting.DARK_GRAY));
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private RecipeHolder<BioWorkbenchRecipe> recipeAt(double mouseX, double mouseY) {
        int px = panelLeft();
        if (mouseX < px + 2 || mouseX >= leftPos - 4 || mouseY < topPos + PANEL_PAD) {
            return null;
        }
        int row = (int) ((mouseY - topPos - PANEL_PAD) / ROW);
        List<RecipeHolder<BioWorkbenchRecipe>> recipes = recipes();
        int index = row + scroll;
        if (row < 0 || row >= visibleRows() || index >= recipes.size()) {
            return null;
        }
        return recipes.get(index);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        RecipeHolder<BioWorkbenchRecipe> holder = recipeAt(mouseX, mouseY);
        if (holder != null && button == 0) {
            NetworkManager.sendToServer(new MutagenNetwork.FillRecipePayload(holder.id()));
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < leftPos) {
            scroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        // Клик по панели рецептов — не «клик мимо окна», предметы из курсора выбрасывать нельзя.
        return super.hasClickedOutside(mouseX, mouseY, left, top, button)
                && !(mouseX >= panelLeft() && mouseX < leftPos && mouseY >= topPos && mouseY < topPos + imageHeight);
    }
}
