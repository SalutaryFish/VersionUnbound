package net.minecraft.world.item.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public class BannerDuplicateRecipe extends CustomRecipe {
    public BannerDuplicateRecipe(CraftingBookCategory category) {
        super(category);
    }

    public boolean matches(CraftingInput input, Level level) {
        DyeColor dyeColor = null;
        ItemStack itemStack = null;
        ItemStack itemStack2 = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack itemStack3 = input.getItem(i);
            if (!itemStack3.isEmpty()) {
                if (!(itemStack3.getItem() instanceof BannerItem bannerItem)) {
                    return false;
                }

                if (dyeColor == null) {
                    dyeColor = bannerItem.getColor();
                } else if (dyeColor != bannerItem.getColor()) {
                    return false;
                }

                int j = itemStack3.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().size();
                if (j > 6) {
                    return false;
                }

                if (j > 0) {
                    if (itemStack != null) {
                        return false;
                    }

                    itemStack = itemStack3;
                } else {
                    if (itemStack2 != null) {
                        return false;
                    }

                    itemStack2 = itemStack3;
                }
            }
        }

        return itemStack != null && itemStack2 != null;
    }

    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (int i = 0; i < input.size(); i++) {
            ItemStack itemStack = input.getItem(i);
            if (!itemStack.isEmpty()) {
                int j = itemStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().size();
                if (j > 0 && j <= 6) {
                    return itemStack.copyWithCount(1);
                }
            }
        }

        return ItemStack.EMPTY;
    }

    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> nonNullList = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        for (int i = 0; i < nonNullList.size(); i++) {
            ItemStack itemStack = input.getItem(i);
            if (!itemStack.isEmpty()) {
                if (itemStack.getItem().hasCraftingRemainingItem()) {
                    nonNullList.set(i, new ItemStack(itemStack.getItem().getCraftingRemainingItem()));
                } else if (!itemStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().isEmpty()) {
                    nonNullList.set(i, itemStack.copyWithCount(1));
                }
            }
        }

        return nonNullList;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeSerializer.BANNER_DUPLICATE;
    }

    /**
     * Used to determine if this recipe can fit in a grid of the given width/height
     */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }
}
