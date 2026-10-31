package net.minecraft.client;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;

public class ClientRecipeBook extends RecipeBook {
    private static final Logger LOGGER = LogUtils.getLogger();
    private Map<RecipeBookCategories, List<RecipeCollection>> collectionsByTab = ImmutableMap.of();
    private List<RecipeCollection> allCollections = ImmutableList.of();

    public void setupCollections(Iterable<RecipeHolder<?>> recipes, RegistryAccess registryAccess) {
        Map<RecipeBookCategories, List<List<RecipeHolder<?>>>> map = categorizeAndGroupRecipes(recipes);
        Map<RecipeBookCategories, List<RecipeCollection>> map2 = Maps.newHashMap();
        Builder<RecipeCollection> builder = ImmutableList.builder();
        map.forEach(
            (categories, recipeHolderList) -> map2.put(
                categories,
                recipeHolderList.stream()
                    .map(recipeHolders -> new RecipeCollection(registryAccess, (List<RecipeHolder<?>>)recipeHolders))
                    .peek(builder::add)
                    .collect(ImmutableList.toImmutableList())
            )
        );
        RecipeBookCategories.AGGREGATE_CATEGORIES
            .forEach(
                (aggregatedCategory, categories) -> map2.put(
                    aggregatedCategory,
                    categories.stream()
                        .flatMap(categoriesx -> map2.getOrDefault(categoriesx, ImmutableList.of()).stream())
                        .collect(ImmutableList.toImmutableList())
                )
            );
        this.collectionsByTab = ImmutableMap.copyOf(map2);
        this.allCollections = builder.build();
    }

    private static Map<RecipeBookCategories, List<List<RecipeHolder<?>>>> categorizeAndGroupRecipes(Iterable<RecipeHolder<?>> recipes) {
        Map<RecipeBookCategories, List<List<RecipeHolder<?>>>> map = Maps.newHashMap();
        Table<RecipeBookCategories, String, List<RecipeHolder<?>>> table = HashBasedTable.create();

        for (RecipeHolder<?> recipeHolder : recipes) {
            Recipe<?> recipe = recipeHolder.value();
            if (!recipe.isSpecial() && !recipe.isIncomplete()) {
                RecipeBookCategories recipeBookCategories = getCategory(recipeHolder);
                String string = recipe.getGroup();
                if (string.isEmpty()) {
                    map.computeIfAbsent(recipeBookCategories, category -> Lists.newArrayList()).add(ImmutableList.of(recipeHolder));
                } else {
                    List<RecipeHolder<?>> list = table.get(recipeBookCategories, string);
                    if (list == null) {
                        list = Lists.newArrayList();
                        table.put(recipeBookCategories, string, list);
                        map.computeIfAbsent(recipeBookCategories, categories -> Lists.newArrayList()).add(list);
                    }

                    list.add(recipeHolder);
                }
            }
        }

        return map;
    }

    private static RecipeBookCategories getCategory(RecipeHolder<?> recipe) {
        Recipe<?> recipe2 = recipe.value();
        if (recipe2 instanceof CraftingRecipe craftingRecipe) {
            return switch (craftingRecipe.category()) {
                case BUILDING -> RecipeBookCategories.CRAFTING_BUILDING_BLOCKS;
                case EQUIPMENT -> RecipeBookCategories.CRAFTING_EQUIPMENT;
                case REDSTONE -> RecipeBookCategories.CRAFTING_REDSTONE;
                case MISC -> RecipeBookCategories.CRAFTING_MISC;
            };
        } else {
            RecipeType<?> recipeType = recipe2.getType();
            if (recipe2 instanceof AbstractCookingRecipe abstractCookingRecipe) {
                CookingBookCategory cookingBookCategory = abstractCookingRecipe.category();
                if (recipeType == RecipeType.SMELTING) {
                    return switch (cookingBookCategory) {
                        case BLOCKS -> RecipeBookCategories.FURNACE_BLOCKS;
                        case FOOD -> RecipeBookCategories.FURNACE_FOOD;
                        case MISC -> RecipeBookCategories.FURNACE_MISC;
                    };
                }

                if (recipeType == RecipeType.BLASTING) {
                    return cookingBookCategory == CookingBookCategory.BLOCKS
                        ? RecipeBookCategories.BLAST_FURNACE_BLOCKS
                        : RecipeBookCategories.BLAST_FURNACE_MISC;
                }

                if (recipeType == RecipeType.SMOKING) {
                    return RecipeBookCategories.SMOKER_FOOD;
                }

                if (recipeType == RecipeType.CAMPFIRE_COOKING) {
                    return RecipeBookCategories.CAMPFIRE;
                }
            }

            if (recipeType == RecipeType.STONECUTTING) {
                return RecipeBookCategories.STONECUTTER;
            }

            if (recipeType == RecipeType.SMITHING) {
                return RecipeBookCategories.SMITHING;
            }

            LOGGER.warn(
                "Unknown recipe category: {}/{}", LogUtils.defer(() -> BuiltInRegistries.RECIPE_TYPE.getKey(recipe2.getType())), LogUtils.defer(recipe::id)
            );
            return RecipeBookCategories.UNKNOWN;
        }
    }

    public List<RecipeCollection> getCollections() {
        return this.allCollections;
    }

    public List<RecipeCollection> getCollection(RecipeBookCategories categories) {
        return this.collectionsByTab.getOrDefault(categories, Collections.emptyList());
    }
}
