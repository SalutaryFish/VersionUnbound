package net.minecraft.world.entity.npc;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public interface InventoryCarrier {
    String TAG_INVENTORY = "Inventory";

    SimpleContainer getInventory();

    static void pickUpItem(Mob mob, InventoryCarrier carrier, ItemEntity itemEntity) {
        ItemStack itemStack = itemEntity.getItem();
        if (mob.wantsToPickUp(itemStack)) {
            SimpleContainer simpleContainer = carrier.getInventory();
            boolean bl = simpleContainer.canAddItem(itemStack);
            if (!bl) {
                return;
            }

            mob.onItemPickup(itemEntity);
            int i = itemStack.getCount();
            ItemStack itemStack2 = simpleContainer.addItem(itemStack);
            mob.take(itemEntity, i - itemStack2.getCount());
            if (itemStack2.isEmpty()) {
                itemEntity.discard();
            } else {
                itemStack.setCount(itemStack2.getCount());
            }
        }
    }

    default void readInventoryFromTag(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        if (tag.contains("Inventory", 9)) {
            this.getInventory().fromTag(tag.getList("Inventory", 10), levelRegistry);
        }
    }

    default void writeInventoryToTag(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        tag.put("Inventory", this.getInventory().createTag(levelRegistry));
    }
}
