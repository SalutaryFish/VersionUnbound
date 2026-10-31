package net.minecraft.recipebook;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;

public class ServerPlaceRecipe<I extends RecipeInput, R extends Recipe<I>> implements PlaceRecipe<Integer> {
    private static final int ITEM_NOT_FOUND = -1;
    protected final StackedContents stackedContents = new StackedContents();
    protected Inventory inventory;
    protected RecipeBookMenu<I, R> menu;

    public ServerPlaceRecipe(RecipeBookMenu<I, R> menu) {
        this.menu = menu;
    }

    public void recipeClicked(ServerPlayer player, @Nullable RecipeHolder<R> recipe, boolean placeAll) {
        if (recipe != null && player.getRecipeBook().contains(recipe)) {
            this.inventory = player.getInventory();
            if (this.testClearGrid() || player.isCreative()) {
                this.stackedContents.clear();
                player.getInventory().fillStackedContents(this.stackedContents);
                this.menu.fillCraftSlotsStackedContents(this.stackedContents);
                if (this.stackedContents.canCraft(recipe.value(), null)) {
                    this.handleRecipeClicked(recipe, placeAll);
                } else {
                    this.clearGrid();
                    player.connection.send(new ClientboundPlaceGhostRecipePacket(player.containerMenu.containerId, recipe));
                }

                player.getInventory().setChanged();
            }
        }
    }

    protected void clearGrid() {
        for (int i = 0; i < this.menu.getSize(); i++) {
            if (this.menu.shouldMoveToInventory(i)) {
                ItemStack itemStack = this.menu.getSlot(i).getItem().copy();
                this.inventory.placeItemBackInInventory(itemStack, false);
                this.menu.getSlot(i).set(itemStack);
            }
        }

        this.menu.clearCraftingContent();
    }

    protected void handleRecipeClicked(RecipeHolder<R> recipe, boolean placeAll) {
        boolean bl = this.menu.recipeMatches(recipe);
        int i = this.stackedContents.getBiggestCraftableStack(recipe, null);
        if (bl) {
            for (int j = 0; j < this.menu.getGridHeight() * this.menu.getGridWidth() + 1; j++) {
                if (j != this.menu.getResultSlotIndex()) {
                    ItemStack itemStack = this.menu.getSlot(j).getItem();
                    if (!itemStack.isEmpty() && Math.min(i, itemStack.getMaxStackSize()) < itemStack.getCount() + 1) {
                        return;
                    }
                }
            }
        }

        int j = this.getStackSize(placeAll, i, bl);
        IntList intList = new IntArrayList();
        if (this.stackedContents.canCraft(recipe.value(), intList, j)) {
            int k = j;

            for (int l : intList) {
                ItemStack itemStack2 = StackedContents.fromStackingIndex(l);
                if (!itemStack2.isEmpty()) {
                    int m = itemStack2.getMaxStackSize();
                    if (m < k) {
                        k = m;
                    }
                }
            }

            j = k;
            if (this.stackedContents.canCraft(recipe.value(), intList, j)) {
                this.clearGrid();
                this.placeRecipe(this.menu.getGridWidth(), this.menu.getGridHeight(), this.menu.getResultSlotIndex(), recipe, intList.iterator(), j);
            }
        }
    }

    public void addItemToSlot(Integer item, int slot, int maxAmount, int x, int y) {
        Slot slot2 = this.menu.getSlot(slot);
        ItemStack itemStack = StackedContents.fromStackingIndex(item);
        if (!itemStack.isEmpty()) {
            int i = maxAmount;

            while (i > 0) {
                i = this.moveItemToGrid(slot2, itemStack, i);
                if (i == -1) {
                    return;
                }
            }
        }
    }

    protected int getStackSize(boolean placeAll, int maxPossible, boolean recipeMatches) {
        int i = 1;
        if (placeAll) {
            i = maxPossible;
        } else if (recipeMatches) {
            i = Integer.MAX_VALUE;

            for (int j = 0; j < this.menu.getGridWidth() * this.menu.getGridHeight() + 1; j++) {
                if (j != this.menu.getResultSlotIndex()) {
                    ItemStack itemStack = this.menu.getSlot(j).getItem();
                    if (!itemStack.isEmpty() && i > itemStack.getCount()) {
                        i = itemStack.getCount();
                    }
                }
            }

            if (i != Integer.MAX_VALUE) {
                i++;
            }
        }

        return i;
    }

    protected int moveItemToGrid(Slot slot, ItemStack stack, int maxAmount) {
        int i = this.inventory.findSlotMatchingUnusedItem(stack);
        if (i == -1) {
            return -1;
        }

        ItemStack itemStack = this.inventory.getItem(i);
        int j;
        if (maxAmount < itemStack.getCount()) {
            this.inventory.removeItem(i, maxAmount);
            j = maxAmount;
        } else {
            this.inventory.removeItemNoUpdate(i);
            j = itemStack.getCount();
        }

        if (slot.getItem().isEmpty()) {
            slot.set(itemStack.copyWithCount(j));
        } else {
            slot.getItem().grow(j);
        }

        return maxAmount - j;
    }

    private boolean testClearGrid() {
        List<ItemStack> list = Lists.newArrayList();
        int i = this.getAmountOfFreeSlotsInInventory();

        for (int j = 0; j < this.menu.getGridWidth() * this.menu.getGridHeight() + 1; j++) {
            if (j != this.menu.getResultSlotIndex()) {
                ItemStack itemStack = this.menu.getSlot(j).getItem().copy();
                if (!itemStack.isEmpty()) {
                    int k = this.inventory.getSlotWithRemainingSpace(itemStack);
                    if (k == -1 && list.size() <= i) {
                        for (ItemStack itemStack2 : list) {
                            if (ItemStack.isSameItem(itemStack2, itemStack)
                                && itemStack2.getCount() != itemStack2.getMaxStackSize()
                                && itemStack2.getCount() + itemStack.getCount() <= itemStack2.getMaxStackSize()) {
                                itemStack2.grow(itemStack.getCount());
                                itemStack.setCount(0);
                                break;
                            }
                        }

                        if (!itemStack.isEmpty()) {
                            if (list.size() >= i) {
                                return false;
                            }

                            list.add(itemStack);
                        }
                    } else if (k == -1) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private int getAmountOfFreeSlotsInInventory() {
        int i = 0;

        for (ItemStack itemStack : this.inventory.items) {
            if (itemStack.isEmpty()) {
                i++;
            }
        }

        return i;
    }
}
