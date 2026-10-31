package net.minecraft.world.entity.player;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.BitSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

public class StackedContents {
    private static final int EMPTY = 0;
    public final Int2IntMap contents = new Int2IntOpenHashMap();

    public void accountSimpleStack(ItemStack stack) {
        if (!stack.isDamaged() && !stack.isEnchanted() && !stack.has(DataComponents.CUSTOM_NAME)) {
            this.accountStack(stack);
        }
    }

    public void accountStack(ItemStack stack) {
        this.accountStack(stack, stack.getMaxStackSize());
    }

    public void accountStack(ItemStack stack, int amount) {
        if (!stack.isEmpty()) {
            int i = getStackingIndex(stack);
            int j = Math.min(amount, stack.getCount());
            this.put(i, j);
        }
    }

    public static int getStackingIndex(ItemStack stack) {
        return BuiltInRegistries.ITEM.getId(stack.getItem());
    }

    boolean has(int stackingIndex) {
        return this.contents.get(stackingIndex) > 0;
    }

    int take(int stackingIndex, int amount) {
        int i = this.contents.get(stackingIndex);
        if (i >= amount) {
            this.contents.put(stackingIndex, i - amount);
            return stackingIndex;
        } else {
            return 0;
        }
    }

    void put(int stackingIndex, int increment) {
        this.contents.put(stackingIndex, this.contents.get(stackingIndex) + increment);
    }

    public boolean canCraft(Recipe<?> recipe, @Nullable IntList stackingIndexList) {
        return this.canCraft(recipe, stackingIndexList, 1);
    }

    public boolean canCraft(Recipe<?> recipe, @Nullable IntList stackingIndexList, int amount) {
        return new StackedContents.RecipePicker(recipe).tryPick(amount, stackingIndexList);
    }

    public int getBiggestCraftableStack(RecipeHolder<?> recipe, @Nullable IntList stackingIndexList) {
        return this.getBiggestCraftableStack(recipe, Integer.MAX_VALUE, stackingIndexList);
    }

    public int getBiggestCraftableStack(RecipeHolder<?> recipe, int amount, @Nullable IntList stackingIndexList) {
        return new StackedContents.RecipePicker(recipe.value()).tryPickAll(amount, stackingIndexList);
    }

    public static ItemStack fromStackingIndex(int stackingIndex) {
        return stackingIndex == 0 ? ItemStack.EMPTY : new ItemStack(Item.byId(stackingIndex));
    }

    public void clear() {
        this.contents.clear();
    }

    class RecipePicker {
        private final Recipe<?> recipe;
        private final List<Ingredient> ingredients = Lists.newArrayList();
        private final int ingredientCount;
        private final int[] items;
        private final int itemCount;
        private final BitSet data;
        private final IntList path = new IntArrayList();

        public RecipePicker(final Recipe<?> recipe) {
            this.recipe = recipe;
            this.ingredients.addAll(recipe.getIngredients());
            this.ingredients.removeIf(Ingredient::isEmpty);
            this.ingredientCount = this.ingredients.size();
            this.items = this.getUniqueAvailableIngredientItems();
            this.itemCount = this.items.length;
            this.data = new BitSet(this.ingredientCount + this.itemCount + this.ingredientCount + this.ingredientCount * this.itemCount);

            for (int i = 0; i < this.ingredients.size(); i++) {
                IntList intList = this.ingredients.get(i).getStackingIds();

                for (int j = 0; j < this.itemCount; j++) {
                    if (intList.contains(this.items[j])) {
                        this.data.set(this.getIndex(true, j, i));
                    }
                }
            }
        }

        public boolean tryPick(int amount, @Nullable IntList stackingIndexList) {
            if (amount <= 0) {
                return true;
            }

            int i;
            for (i = 0; this.dfs(amount); i++) {
                StackedContents.this.take(this.items[this.path.getInt(0)], amount);
                int j = this.path.size() - 1;
                this.setSatisfied(this.path.getInt(j));

                for (int k = 0; k < j; k++) {
                    this.toggleResidual((k & 1) == 0, this.path.get(k), this.path.get(k + 1));
                }

                this.path.clear();
                this.data.clear(0, this.ingredientCount + this.itemCount);
            }

            boolean bl = i == this.ingredientCount;
            boolean bl2 = bl && stackingIndexList != null;
            if (bl2) {
                stackingIndexList.clear();
            }

            this.data.clear(0, this.ingredientCount + this.itemCount + this.ingredientCount);
            int l = 0;

            for (Ingredient ingredient : this.recipe.getIngredients()) {
                if (bl2 && ingredient.isEmpty()) {
                    stackingIndexList.add(0);
                } else {
                    for (int m = 0; m < this.itemCount; m++) {
                        if (this.hasResidual(false, l, m)) {
                            this.toggleResidual(true, m, l);
                            StackedContents.this.put(this.items[m], amount);
                            if (bl2) {
                                stackingIndexList.add(this.items[m]);
                            }
                        }
                    }

                    l++;
                }
            }

            return bl;
        }

        private int[] getUniqueAvailableIngredientItems() {
            IntCollection intCollection = new IntAVLTreeSet();

            for (Ingredient ingredient : this.ingredients) {
                intCollection.addAll(ingredient.getStackingIds());
            }

            IntIterator intIterator = intCollection.iterator();

            while (intIterator.hasNext()) {
                if (!StackedContents.this.has(intIterator.nextInt())) {
                    intIterator.remove();
                }
            }

            return intCollection.toIntArray();
        }

        private boolean dfs(int amount) {
            int i = this.itemCount;

            for (int j = 0; j < i; j++) {
                if (StackedContents.this.contents.get(this.items[j]) >= amount) {
                    this.visit(false, j);

                    while (!this.path.isEmpty()) {
                        int k = this.path.size();
                        boolean bl = (k & 1) == 1;
                        int l = this.path.getInt(k - 1);
                        if (!bl && !this.isSatisfied(l)) {
                            break;
                        }

                        int m = bl ? this.ingredientCount : i;
                        int n = 0;

                        while (true) {
                            if (n < m) {
                                if (this.hasVisited(bl, n) || !this.hasConnection(bl, l, n) || !this.hasResidual(bl, l, n)) {
                                    n++;
                                    continue;
                                }

                                this.visit(bl, n);
                            }

                            n = this.path.size();
                            if (n == k) {
                                this.path.removeInt(n - 1);
                            }
                            break;
                        }
                    }

                    if (!this.path.isEmpty()) {
                        return true;
                    }
                }
            }

            return false;
        }

        private boolean isSatisfied(int stackingIndex) {
            return this.data.get(this.getSatisfiedIndex(stackingIndex));
        }

        private void setSatisfied(int stackingIndex) {
            this.data.set(this.getSatisfiedIndex(stackingIndex));
        }

        private int getSatisfiedIndex(int stackingIndex) {
            return this.ingredientCount + this.itemCount + stackingIndex;
        }

        private boolean hasConnection(boolean isIngredientPath, int stackingIndex, int pathIndex) {
            return this.data.get(this.getIndex(isIngredientPath, stackingIndex, pathIndex));
        }

        private boolean hasResidual(boolean isIngredientPath, int stackingIndex, int pathIndex) {
            return isIngredientPath != this.data.get(1 + this.getIndex(isIngredientPath, stackingIndex, pathIndex));
        }

        private void toggleResidual(boolean isIngredientPath, int stackingIndex, int pathIndex) {
            this.data.flip(1 + this.getIndex(isIngredientPath, stackingIndex, pathIndex));
        }

        private int getIndex(boolean isIngredientPath, int stackingIndex, int pathIndex) {
            int i = isIngredientPath ? stackingIndex * this.ingredientCount + pathIndex : pathIndex * this.ingredientCount + stackingIndex;
            return this.ingredientCount + this.itemCount + this.ingredientCount + 2 * i;
        }

        private void visit(boolean isIngredientPath, int pathIndex) {
            this.data.set(this.getVisitedIndex(isIngredientPath, pathIndex));
            this.path.add(pathIndex);
        }

        private boolean hasVisited(boolean isIngredientPath, int pathIndex) {
            return this.data.get(this.getVisitedIndex(isIngredientPath, pathIndex));
        }

        private int getVisitedIndex(boolean isIngredientPath, int pathIndex) {
            return (isIngredientPath ? 0 : this.ingredientCount) + pathIndex;
        }

        public int tryPickAll(int amount, @Nullable IntList stackingIndexList) {
            int i = 0;
            int j = Math.min(amount, this.getMinIngredientCount()) + 1;

            while (true) {
                int k = (i + j) / 2;
                if (this.tryPick(k, null)) {
                    if (j - i <= 1) {
                        if (k > 0) {
                            this.tryPick(k, stackingIndexList);
                        }

                        return k;
                    }

                    i = k;
                } else {
                    j = k;
                }
            }
        }

        private int getMinIngredientCount() {
            int i = Integer.MAX_VALUE;

            for (Ingredient ingredient : this.ingredients) {
                int j = 0;

                for (int k : ingredient.getStackingIds()) {
                    j = Math.max(j, StackedContents.this.contents.get(k));
                }

                if (i > 0) {
                    i = Math.min(i, j);
                }
            }

            return i;
        }
    }
}
