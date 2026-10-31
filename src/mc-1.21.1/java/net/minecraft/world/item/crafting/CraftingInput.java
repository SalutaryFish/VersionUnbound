package net.minecraft.world.item.crafting;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;

public class CraftingInput implements RecipeInput {
    public static final CraftingInput EMPTY = new CraftingInput(0, 0, List.of());
    private final int width;
    private final int height;
    private final List<ItemStack> items;
    private final StackedContents stackedContents = new StackedContents();
    private final int ingredientCount;

    private CraftingInput(int width, int height, List<ItemStack> item) {
        this.width = width;
        this.height = height;
        this.items = item;
        int i = 0;

        for (ItemStack itemStack : item) {
            if (!itemStack.isEmpty()) {
                i++;
                this.stackedContents.accountStack(itemStack, 1);
            }
        }

        this.ingredientCount = i;
    }

    public static CraftingInput of(int width, int height, List<ItemStack> items) {
        return ofPositioned(width, height, items).input();
    }

    public static CraftingInput.Positioned ofPositioned(int width, int height, List<ItemStack> items) {
        if (width != 0 && height != 0) {
            int i = width - 1;
            int j = 0;
            int k = height - 1;
            int l = 0;

            for (int m = 0; m < height; m++) {
                boolean bl = true;

                for (int n = 0; n < width; n++) {
                    ItemStack itemStack = items.get(n + m * width);
                    if (!itemStack.isEmpty()) {
                        i = Math.min(i, n);
                        j = Math.max(j, n);
                        bl = false;
                    }
                }

                if (!bl) {
                    k = Math.min(k, m);
                    l = Math.max(l, m);
                }
            }

            int m = j - i + 1;
            int o = l - k + 1;
            if (m <= 0 || o <= 0) {
                return CraftingInput.Positioned.EMPTY;
            }

            if (m == width && o == height) {
                return new CraftingInput.Positioned(new CraftingInput(width, height, items), i, k);
            }

            List<ItemStack> list = new ArrayList<>(m * o);

            for (int p = 0; p < o; p++) {
                for (int q = 0; q < m; q++) {
                    int r = q + i + (p + k) * width;
                    list.add(items.get(r));
                }
            }

            return new CraftingInput.Positioned(new CraftingInput(m, o, list), i, k);
        } else {
            return CraftingInput.Positioned.EMPTY;
        }
    }

    @Override
    public ItemStack getItem(int index) {
        return this.items.get(index);
    }

    public ItemStack getItem(int row, int column) {
        return this.items.get(row + column * this.width);
    }

    @Override
    public int size() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        return this.ingredientCount == 0;
    }

    public StackedContents stackedContents() {
        return this.stackedContents;
    }

    public List<ItemStack> items() {
        return this.items;
    }

    public int ingredientCount() {
        return this.ingredientCount;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else {
            return !(other instanceof CraftingInput craftingInput)
                ? false
                : this.width == craftingInput.width
                    && this.height == craftingInput.height
                    && this.ingredientCount == craftingInput.ingredientCount
                    && ItemStack.listMatches(this.items, craftingInput.items);
        }
    }

    @Override
    public int hashCode() {
        int i = ItemStack.hashStackList(this.items);
        i = 31 * i + this.width;
        return 31 * i + this.height;
    }

    public record Positioned(CraftingInput input, int left, int top) {
        public static final CraftingInput.Positioned EMPTY = new CraftingInput.Positioned(CraftingInput.EMPTY, 0, 0);
    }
}
