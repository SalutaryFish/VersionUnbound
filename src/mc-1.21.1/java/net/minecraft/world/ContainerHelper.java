package net.minecraft.world;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

public class ContainerHelper {
    public static final String TAG_ITEMS = "Items";

    public static ItemStack removeItem(List<ItemStack> stacks, int index, int amount) {
        return index >= 0 && index < stacks.size() && !stacks.get(index).isEmpty() && amount > 0 ? stacks.get(index).split(amount) : ItemStack.EMPTY;
    }

    public static ItemStack takeItem(List<ItemStack> stacks, int index) {
        return index >= 0 && index < stacks.size() ? stacks.set(index, ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    public static CompoundTag saveAllItems(CompoundTag tag, NonNullList<ItemStack> items, HolderLookup.Provider levelRegistry) {
        return saveAllItems(tag, items, true, levelRegistry);
    }

    public static CompoundTag saveAllItems(CompoundTag tag, NonNullList<ItemStack> items, boolean alwaysPutTag, HolderLookup.Provider levelRegistry) {
        ListTag listTag = new ListTag();

        for (int i = 0; i < items.size(); i++) {
            ItemStack itemStack = items.get(i);
            if (!itemStack.isEmpty()) {
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.putByte("Slot", (byte)i);
                listTag.add(itemStack.save(levelRegistry, compoundTag));
            }
        }

        if (!listTag.isEmpty() || alwaysPutTag) {
            tag.put("Items", listTag);
        }

        return tag;
    }

    public static void loadAllItems(CompoundTag tag, NonNullList<ItemStack> items, HolderLookup.Provider levelRegistry) {
        ListTag listTag = tag.getList("Items", 10);

        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag compoundTag = listTag.getCompound(i);
            int j = compoundTag.getByte("Slot") & 255;
            if (j >= 0 && j < items.size()) {
                items.set(j, ItemStack.parse(levelRegistry, compoundTag).orElse(ItemStack.EMPTY));
            }
        }
    }

    /**
     * Clears items from the inventory matching a predicate.
     * @return The amount of items cleared
     *
     * @param maxItems The maximum amount of items to be cleared. A negative value
     *                 means unlimited and 0 means count how many items are found that
     *                 could be cleared.
     */
    public static int clearOrCountMatchingItems(Container container, Predicate<ItemStack> itemPredicate, int maxItems, boolean simulate) {
        int i = 0;

        for (int j = 0; j < container.getContainerSize(); j++) {
            ItemStack itemStack = container.getItem(j);
            int k = clearOrCountMatchingItems(itemStack, itemPredicate, maxItems - i, simulate);
            if (k > 0 && !simulate && itemStack.isEmpty()) {
                container.setItem(j, ItemStack.EMPTY);
            }

            i += k;
        }

        return i;
    }

    public static int clearOrCountMatchingItems(ItemStack stack, Predicate<ItemStack> itemPredicate, int maxItems, boolean simulate) {
        if (stack.isEmpty() || !itemPredicate.test(stack)) {
            return 0;
        }

        if (simulate) {
            return stack.getCount();
        }

        int i = maxItems < 0 ? stack.getCount() : Math.min(maxItems, stack.getCount());
        stack.shrink(i);
        return i;
    }
}
