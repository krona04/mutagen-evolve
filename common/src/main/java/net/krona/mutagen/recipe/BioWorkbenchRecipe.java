package net.krona.mutagen.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.krona.mutagen.registry.MutagenRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Рецепт верстака биолога: набор ингредиентов с количествами, без формы.
 * <p>
 * Лабораторная сборка — это «сколько чего», а не «что где лежит»: пять слитков железа кладутся
 * одной стопкой в любую ячейку. Лишнего на столе быть не должно — иначе рецепт не сработает.
 */
public class BioWorkbenchRecipe implements Recipe<BioWorkbenchInput> {
    private final List<CountedIngredient> ingredients;
    private final ItemStack result;

    public BioWorkbenchRecipe(List<CountedIngredient> ingredients, ItemStack result) {
        this.ingredients = List.copyOf(ingredients);
        this.result = result;
    }

    public List<CountedIngredient> ingredients() {
        return ingredients;
    }

    public ItemStack result() {
        return result;
    }

    /**
     * Сколько взять из каждой ячейки, чтобы собрать рецепт один раз, или null, если он не собирается.
     */
    @Nullable
    public int[] plan(BioWorkbenchInput input) {
        int size = input.size();
        int[] take = new int[size];
        int[] left = new int[size];
        boolean[] used = new boolean[size];
        for (int slot = 0; slot < size; slot++) {
            left[slot] = input.getItem(slot).getCount();
        }

        for (CountedIngredient required : ingredients) {
            int need = required.count();
            for (int slot = 0; slot < size && need > 0; slot++) {
                ItemStack stack = input.getItem(slot);
                if (stack.isEmpty() || !required.ingredient().test(stack)) {
                    continue;
                }
                used[slot] = true;
                int amount = Math.min(need, left[slot]);
                take[slot] += amount;
                left[slot] -= amount;
                need -= amount;
            }
            if (need > 0) {
                return null;
            }
        }

        for (int slot = 0; slot < size; slot++) {
            if (!input.getItem(slot).isEmpty() && !used[slot]) {
                return null;
            }
        }
        return take;
    }

    /** Забирает ингредиенты одного сбора из контейнера ячеек. */
    public boolean consume(Container container) {
        int[] take = plan(BioWorkbenchInput.of(container));
        if (take == null) {
            return false;
        }
        for (int slot = 0; slot < take.length; slot++) {
            if (take[slot] > 0) {
                container.removeItem(slot, take[slot]);
            }
        }
        return true;
    }

    @Override
    public boolean matches(BioWorkbenchInput input, Level level) {
        return plan(input) != null;
    }

    @Override
    public ItemStack assemble(BioWorkbenchInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= ingredients.size();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (CountedIngredient ingredient : ingredients) {
            list.add(ingredient.ingredient());
        }
        return list;
    }

    /** Книга рецептов о нашем типе не знает: у верстака свой список на экране. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MutagenRecipes.BIO_WORKBENCH_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return MutagenRecipes.BIO_WORKBENCH_TYPE.get();
    }

    /**
     * Ингредиент с количеством. В JSON пишется полной формой
     * {@code {"ingredient": {"item": "..."}, "count": 5}} или короткой {@code {"item": "..."}}.
     */
    public record CountedIngredient(Ingredient ingredient, int count) {
        private static final Codec<CountedIngredient> FULL_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(CountedIngredient::count)
        ).apply(instance, CountedIngredient::new));

        public static final Codec<CountedIngredient> CODEC = Codec.withAlternative(FULL_CODEC,
                Ingredient.CODEC_NONEMPTY.xmap(ingredient -> new CountedIngredient(ingredient, 1),
                        CountedIngredient::ingredient));

        public static final StreamCodec<RegistryFriendlyByteBuf, CountedIngredient> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, CountedIngredient::ingredient,
                        ByteBufCodecs.VAR_INT, CountedIngredient::count,
                        CountedIngredient::new);
    }

    public static class Serializer implements RecipeSerializer<BioWorkbenchRecipe> {
        private static final MapCodec<BioWorkbenchRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                CountedIngredient.CODEC.listOf().fieldOf("ingredients").forGetter(BioWorkbenchRecipe::ingredients),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(BioWorkbenchRecipe::result)
        ).apply(instance, BioWorkbenchRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, BioWorkbenchRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        CountedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), BioWorkbenchRecipe::ingredients,
                        ItemStack.STREAM_CODEC, BioWorkbenchRecipe::result,
                        BioWorkbenchRecipe::new);

        @Override
        public MapCodec<BioWorkbenchRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BioWorkbenchRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
