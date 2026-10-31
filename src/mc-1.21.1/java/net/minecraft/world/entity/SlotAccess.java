package net.minecraft.world.entity;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public interface SlotAccess {
    SlotAccess NULL = new SlotAccess() {
        @Override
        public ItemStack get() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean set(ItemStack itemStack) {
            return false;
        }
    };

    static SlotAccess of(Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return getter.get();
            }

            @Override
            public boolean set(ItemStack itemStack) {
                setter.accept(itemStack);
                return true;
            }
        };
    }

    static SlotAccess forContainer(Container inventory, int slot, Predicate<ItemStack> stackFilter) {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return inventory.getItem(slot);
            }

            @Override
            public boolean set(ItemStack itemStack) {
                if (!stackFilter.test(itemStack)) {
                    return false;
                }

                inventory.setItem(slot, itemStack);
                return true;
            }
        };
    }

    static SlotAccess forContainer(Container inventory, int slot) {
        return forContainer(inventory, slot, stack -> true);
    }

    static SlotAccess forEquipmentSlot(LivingEntity entity, EquipmentSlot slot, Predicate<ItemStack> stackFilter) {
        return new SlotAccess() {
            @Override
            public ItemStack get() {
                return entity.getItemBySlot(slot);
            }

            @Override
            public boolean set(ItemStack itemStack) {
                if (!stackFilter.test(itemStack)) {
                    return false;
                }

                entity.setItemSlot(slot, itemStack);
                return true;
            }
        };
    }

    static SlotAccess forEquipmentSlot(LivingEntity entity, EquipmentSlot slot) {
        return forEquipmentSlot(entity, slot, stack -> true);
    }

    ItemStack get();

    boolean set(ItemStack var1);
}
