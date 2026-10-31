package net.minecraft.client.gui.screens.recipebook;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

public abstract class AbstractFurnaceRecipeBookComponent extends RecipeBookComponent {
    private static final WidgetSprites FILTER_SPRITES = new WidgetSprites(
        ResourceLocation.withDefaultNamespace("recipe_book/furnace_filter_enabled"),
        ResourceLocation.withDefaultNamespace("recipe_book/furnace_filter_disabled"),
        ResourceLocation.withDefaultNamespace("recipe_book/furnace_filter_enabled_highlighted"),
        ResourceLocation.withDefaultNamespace("recipe_book/furnace_filter_disabled_highlighted")
    );
    @Nullable
    private Ingredient fuels;

    @Override
    protected void initFilterButtonTextures() {
        this.filterButton.initTextureValues(FILTER_SPRITES);
    }

    @Override
    public void slotClicked(@Nullable Slot slot) {
        super.slotClicked(slot);
        if (slot != null && slot.index < this.menu.getSize()) {
            this.ghostRecipe.clear();
        }
    }

    @Override
    public void setupGhostRecipe(RecipeHolder<?> recipe, List<Slot> slots) {
        ItemStack itemStack = recipe.value().getResultItem(this.minecraft.level.registryAccess());
        this.ghostRecipe.setRecipe(recipe);
        this.ghostRecipe.addIngredient(Ingredient.of(itemStack), slots.get(2).x, slots.get(2).y);
        NonNullList<Ingredient> nonNullList = recipe.value().getIngredients();
        Slot slot = slots.get(1);
        if (slot.getItem().isEmpty()) {
            if (this.fuels == null) {
                this.fuels = Ingredient.of(
                    this.getFuelItems().stream().filter(item -> item.isEnabled(this.minecraft.level.enabledFeatures())).map(ItemStack::new)
                );
            }

            this.ghostRecipe.addIngredient(this.fuels, slot.x, slot.y);
        }

        Iterator<Ingredient> iterator = nonNullList.iterator();

        for (int i = 0; i < 2; i++) {
            if (!iterator.hasNext()) {
                return;
            }

            Ingredient ingredient = iterator.next();
            if (!ingredient.isEmpty()) {
                Slot slot2 = slots.get(i);
                this.ghostRecipe.addIngredient(ingredient, slot2.x, slot2.y);
            }
        }
    }

    protected abstract Set<Item> getFuelItems();
}
