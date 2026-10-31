package net.minecraft.data.recipes;

import javax.annotation.Nullable;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.level.ItemLike;

public interface RecipeBuilder {
    ResourceLocation ROOT_RECIPE_ADVANCEMENT = ResourceLocation.withDefaultNamespace("recipes/root");

    RecipeBuilder unlockedBy(String var1, Criterion<?> var2);

    RecipeBuilder group(@Nullable String var1);

    Item getResult();

    void save(RecipeOutput var1, ResourceLocation var2);

    default void save(RecipeOutput recipeOutput) {
        this.save(recipeOutput, getDefaultRecipeId(this.getResult()));
    }

    default void save(RecipeOutput recipeOutput, String id) {
        ResourceLocation resourceLocation = getDefaultRecipeId(this.getResult());
        ResourceLocation resourceLocation2 = ResourceLocation.parse(id);
        if (resourceLocation2.equals(resourceLocation)) {
            throw new IllegalStateException("Recipe " + id + " should remove its 'save' argument as it is equal to default one");
        }

        this.save(recipeOutput, resourceLocation2);
    }

    static ResourceLocation getDefaultRecipeId(ItemLike itemLike) {
        return BuiltInRegistries.ITEM.getKey(itemLike.asItem());
    }

    static CraftingBookCategory determineBookCategory(RecipeCategory category) {
        return switch (category) {
            case BUILDING_BLOCKS -> CraftingBookCategory.BUILDING;
            case TOOLS, COMBAT -> CraftingBookCategory.EQUIPMENT;
            case REDSTONE -> CraftingBookCategory.REDSTONE;
            default -> CraftingBookCategory.MISC;
        };
    }
}
