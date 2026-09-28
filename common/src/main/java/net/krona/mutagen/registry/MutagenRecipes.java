package net.krona.mutagen.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.krona.mutagen.Mutagen;
import net.krona.mutagen.recipe.BioWorkbenchRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

public final class MutagenRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Mutagen.MOD_ID, Registries.RECIPE_TYPE);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Mutagen.MOD_ID, Registries.RECIPE_SERIALIZER);

    public static final RegistrySupplier<RecipeType<BioWorkbenchRecipe>> BIO_WORKBENCH_TYPE =
            TYPES.register("bio_workbench", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return Mutagen.id("bio_workbench").toString();
                }
            });

    public static final RegistrySupplier<RecipeSerializer<BioWorkbenchRecipe>> BIO_WORKBENCH_SERIALIZER =
            SERIALIZERS.register("bio_workbench", BioWorkbenchRecipe.Serializer::new);

    private MutagenRecipes() {
    }

    public static void register() {
        TYPES.register();
        SERIALIZERS.register();
    }
}
