package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class HorseInventoryMenu extends AbstractContainerMenu {
    private final Container horseContainer;
    private final Container armorContainer;
    private final AbstractHorse horse;
    private static final int SLOT_BODY_ARMOR = 1;
    private static final int SLOT_HORSE_INVENTORY_START = 2;

    public HorseInventoryMenu(int containerId, Inventory inventory, Container horseContainer, AbstractHorse horse, int columns) {
        super(null, containerId);
        this.horseContainer = horseContainer;
        this.armorContainer = horse.getBodyArmorAccess();
        this.horse = horse;
        int i = 3;
        horseContainer.startOpen(inventory.player);
        int j = -18;
        this.addSlot(new Slot(horseContainer, 0, 8, 18) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.is(Items.SADDLE) && !this.hasItem() && horse.isSaddleable();
            }

            @Override
            public boolean isActive() {
                return horse.isSaddleable();
            }
        });
        this.addSlot(new ArmorSlot(this.armorContainer, horse, EquipmentSlot.BODY, 0, 8, 36, null) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return horse.isBodyArmorItem(itemStack);
            }

            @Override
            public boolean isActive() {
                return horse.canUseSlot(EquipmentSlot.BODY);
            }
        });
        if (columns > 0) {
            for (int k = 0; k < 3; k++) {
                for (int l = 0; l < columns; l++) {
                    this.addSlot(new Slot(horseContainer, 1 + l + k * columns, 80 + l * 18, 18 + k * 18));
                }
            }
        }

        for (int k = 0; k < 3; k++) {
            for (int l = 0; l < 9; l++) {
                this.addSlot(new Slot(inventory, l + k * 9 + 9, 8 + l * 18, 102 + k * 18 + -18));
            }
        }

        for (int k = 0; k < 9; k++) {
            this.addSlot(new Slot(inventory, k, 8 + k * 18, 142));
        }
    }

    /**
     * Determines whether supplied player can use this container
     */
    @Override
    public boolean stillValid(Player player) {
        return !this.horse.hasInventoryChanged(this.horseContainer)
            && this.horseContainer.stillValid(player)
            && this.armorContainer.stillValid(player)
            && this.horse.isAlive()
            && player.canInteractWithEntity(this.horse, 4.0);
    }

    /**
     * Handle when the stack in slot {@code index} is shift-clicked. Normally this moves the stack between the player inventory and the other inventory(s).
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemStack2 = slot.getItem();
            itemStack = itemStack2.copy();
            int i = this.horseContainer.getContainerSize() + 1;
            if (index < i) {
                if (!this.moveItemStackTo(itemStack2, i, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(1).mayPlace(itemStack2) && !this.getSlot(1).hasItem()) {
                if (!this.moveItemStackTo(itemStack2, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(0).mayPlace(itemStack2)) {
                if (!this.moveItemStackTo(itemStack2, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (i <= 1 || !this.moveItemStackTo(itemStack2, 2, i, false)) {
                int j = i;
                int k = j + 27;
                int l = k;
                int m = l + 9;
                if (index >= l && index < m) {
                    if (!this.moveItemStackTo(itemStack2, j, k, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= j && index < k) {
                    if (!this.moveItemStackTo(itemStack2, l, m, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemStack2, l, k, false)) {
                    return ItemStack.EMPTY;
                }

                return ItemStack.EMPTY;
            }

            if (itemStack2.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemStack;
    }

    /**
     * Called when the container is closed.
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        this.horseContainer.stopOpen(player);
    }
}
