package net.minecraft.world;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class SimpleContainer implements Container, StackedContentsCompatible {
    private final int size;
    private final NonNullList<ItemStack> items;
    @Nullable
    private List<ContainerListener> listeners;

    public SimpleContainer(int size) {
        this.size = size;
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    public SimpleContainer(ItemStack... items) {
        this.size = items.length;
        this.items = NonNullList.of(ItemStack.EMPTY, items);
    }

    /**
     * Add a listener that will be notified when any item in this inventory is modified.
     */
    public void addListener(ContainerListener listener) {
        if (this.listeners == null) {
            this.listeners = Lists.newArrayList();
        }

        this.listeners.add(listener);
    }

    /**
     * Removes the specified {@link net.minecraft.world.ContainerListener} from receiving further change notices.
     */
    public void removeListener(ContainerListener listener) {
        if (this.listeners != null) {
            this.listeners.remove(listener);
        }
    }

    /**
     * Returns the stack in the given slot.
     */
    @Override
    public ItemStack getItem(int index) {
        return index >= 0 && index < this.items.size() ? this.items.get(index) : ItemStack.EMPTY;
    }

    public List<ItemStack> removeAllItems() {
        List<ItemStack> list = this.items.stream().filter(stack -> !stack.isEmpty()).collect(Collectors.toList());
        this.clearContent();
        return list;
    }

    /**
     * Removes up to a specified number of items from an inventory slot and returns them in a new stack.
     */
    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack itemStack = ContainerHelper.removeItem(this.items, index, count);
        if (!itemStack.isEmpty()) {
            this.setChanged();
        }

        return itemStack;
    }

    public ItemStack removeItemType(Item item, int amount) {
        ItemStack itemStack = new ItemStack(item, 0);

        for (int i = this.size - 1; i >= 0; i--) {
            ItemStack itemStack2 = this.getItem(i);
            if (itemStack2.getItem().equals(item)) {
                int j = amount - itemStack.getCount();
                ItemStack itemStack3 = itemStack2.split(j);
                itemStack.grow(itemStack3.getCount());
                if (itemStack.getCount() == amount) {
                    break;
                }
            }
        }

        if (!itemStack.isEmpty()) {
            this.setChanged();
        }

        return itemStack;
    }

    public ItemStack addItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack itemStack = stack.copy();
        this.moveItemToOccupiedSlotsWithSameType(itemStack);
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        this.moveItemToEmptySlots(itemStack);
        return itemStack.isEmpty() ? ItemStack.EMPTY : itemStack;
    }

    public boolean canAddItem(ItemStack stack) {
        boolean bl = false;

        for (ItemStack itemStack : this.items) {
            if (itemStack.isEmpty() || ItemStack.isSameItemSameComponents(itemStack, stack) && itemStack.getCount() < itemStack.getMaxStackSize()) {
                bl = true;
                break;
            }
        }

        return bl;
    }

    /**
     * Removes a stack from the given slot and returns it.
     */
    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack itemStack = this.items.get(index);
        if (itemStack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        this.items.set(index, ItemStack.EMPTY);
        return itemStack;
    }

    /**
     * Sets the given item stack to the specified slot in the inventory (can be crafting or armor sections).
     */
    @Override
    public void setItem(int index, ItemStack stack) {
        this.items.set(index, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        this.setChanged();
    }

    @Override
    public int getContainerSize() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : this.items) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void setChanged() {
        if (this.listeners != null) {
            for (ContainerListener containerListener : this.listeners) {
                containerListener.containerChanged(this);
            }
        }
    }

    /**
     * Don't rename this method to canInteractWith due to conflicts with Container
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }

    @Override
    public void fillStackedContents(StackedContents helper) {
        for (ItemStack itemStack : this.items) {
            helper.accountStack(itemStack);
        }
    }

    @Override
    public String toString() {
        return this.items.stream().filter(stack -> !stack.isEmpty()).collect(Collectors.toList()).toString();
    }

    private void moveItemToEmptySlots(ItemStack stack) {
        for (int i = 0; i < this.size; i++) {
            ItemStack itemStack = this.getItem(i);
            if (itemStack.isEmpty()) {
                this.setItem(i, stack.copyAndClear());
                return;
            }
        }
    }

    private void moveItemToOccupiedSlotsWithSameType(ItemStack stack) {
        for (int i = 0; i < this.size; i++) {
            ItemStack itemStack = this.getItem(i);
            if (ItemStack.isSameItemSameComponents(itemStack, stack)) {
                this.moveItemsBetweenStacks(stack, itemStack);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }
    }

    private void moveItemsBetweenStacks(ItemStack stack, ItemStack other) {
        int i = this.getMaxStackSize(other);
        int j = Math.min(stack.getCount(), i - other.getCount());
        if (j > 0) {
            other.grow(j);
            stack.shrink(j);
            this.setChanged();
        }
    }

    public void fromTag(ListTag tag, HolderLookup.Provider levelRegistry) {
        this.clearContent();

        for (int i = 0; i < tag.size(); i++) {
            ItemStack.parse(levelRegistry, tag.getCompound(i)).ifPresent(this::addItem);
        }
    }

    public ListTag createTag(HolderLookup.Provider levelRegistry) {
        ListTag listTag = new ListTag();

        for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack itemStack = this.getItem(i);
            if (!itemStack.isEmpty()) {
                listTag.add(itemStack.save(levelRegistry));
            }
        }

        return listTag;
    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }
}
