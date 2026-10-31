package net.minecraft.recipebook;

import java.util.Iterator;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

public interface PlaceRecipe<T> {
    default void placeRecipe(int width, int height, int outputSlot, RecipeHolder<?> recipe, Iterator<T> ingredients, int maxAmount) {
        int i = width;
        int j = height;
        if (recipe.value() instanceof ShapedRecipe shapedRecipe) {
            i = shapedRecipe.getWidth();
            j = shapedRecipe.getHeight();
        }

        int k = 0;

        for (int l = 0; l < height; l++) {
            if (k == outputSlot) {
                k++;
            }

            boolean bl = j < height / 2.0F;
            int m = Mth.floor(height / 2.0F - j / 2.0F);
            if (bl && m > l) {
                k += width;
                l++;
            }

            for (int n = 0; n < width; n++) {
                if (!ingredients.hasNext()) {
                    return;
                }

                bl = i < width / 2.0F;
                m = Mth.floor(width / 2.0F - i / 2.0F);
                int o = i;
                boolean bl2 = n < i;
                if (bl) {
                    o = m + i;
                    bl2 = m <= n && n < m + i;
                }

                if (bl2) {
                    this.addItemToSlot(ingredients.next(), k, maxAmount, n, l);
                } else if (o == n) {
                    k += width - n;
                    break;
                }

                k++;
            }
        }
    }

    void addItemToSlot(T var1, int var2, int var3, int var4, int var5);
}
