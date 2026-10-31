package net.minecraft.world.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class CartographyTableMenu extends AbstractContainerMenu {
    public static final int MAP_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    private final ContainerLevelAccess access;
    long lastSoundTime;
    public final Container container = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            CartographyTableMenu.this.slotsChanged(this);
            super.setChanged();
        }
    };
    private final ResultContainer resultContainer = new ResultContainer() {
        @Override
        public void setChanged() {
            CartographyTableMenu.this.slotsChanged(this);
            super.setChanged();
        }
    };

    public CartographyTableMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public CartographyTableMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(MenuType.CARTOGRAPHY_TABLE, containerId);
        this.access = access;
        this.addSlot(new Slot(this.container, 0, 15, 15) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.is(Items.FILLED_MAP);
            }
        });
        this.addSlot(new Slot(this.container, 1, 15, 52) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.is(Items.PAPER) || itemStack.is(Items.MAP) || itemStack.is(Items.GLASS_PANE);
            }
        });
        this.addSlot(new Slot(this.resultContainer, 2, 145, 39) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack itemStack) {
                CartographyTableMenu.this.slots.get(0).remove(1);
                CartographyTableMenu.this.slots.get(1).remove(1);
                itemStack.getItem().onCraftedBy(itemStack, player.level(), player);
                access.execute((level, blockPos) -> {
                    long l = level.getGameTime();
                    if (CartographyTableMenu.this.lastSoundTime != l) {
                        level.playSound((Player)null, blockPos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                        CartographyTableMenu.this.lastSoundTime = l;
                    }
                });
                super.onTake(player, itemStack);
            }
        });

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (int i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    /**
     * Determines whether supplied player can use this container
     */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, Blocks.CARTOGRAPHY_TABLE);
    }

    /**
     * Callback for when the crafting matrix is changed.
     */
    @Override
    public void slotsChanged(Container inventory) {
        ItemStack itemStack = this.container.getItem(0);
        ItemStack itemStack2 = this.container.getItem(1);
        ItemStack itemStack3 = this.resultContainer.getItem(2);
        if (itemStack3.isEmpty() || !itemStack.isEmpty() && !itemStack2.isEmpty()) {
            if (!itemStack.isEmpty() && !itemStack2.isEmpty()) {
                this.setupResultSlot(itemStack, itemStack2, itemStack3);
            }
        } else {
            this.resultContainer.removeItemNoUpdate(2);
        }
    }

    private void setupResultSlot(ItemStack map, ItemStack firstSlotStack, ItemStack resultOutput) {
        this.access.execute((level, blockPos) -> {
            MapItemSavedData mapItemSavedData = MapItem.getSavedData(map, level);
            if (mapItemSavedData != null) {
                ItemStack itemStack4;
                if (firstSlotStack.is(Items.PAPER) && !mapItemSavedData.locked && mapItemSavedData.scale < 4) {
                    itemStack4 = map.copyWithCount(1);
                    itemStack4.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.SCALE);
                    this.broadcastChanges();
                } else if (firstSlotStack.is(Items.GLASS_PANE) && !mapItemSavedData.locked) {
                    itemStack4 = map.copyWithCount(1);
                    itemStack4.set(DataComponents.MAP_POST_PROCESSING, MapPostProcessing.LOCK);
                    this.broadcastChanges();
                } else {
                    if (!firstSlotStack.is(Items.MAP)) {
                        this.resultContainer.removeItemNoUpdate(2);
                        this.broadcastChanges();
                        return;
                    }

                    itemStack4 = map.copyWithCount(2);
                    this.broadcastChanges();
                }

                if (!ItemStack.matches(itemStack4, resultOutput)) {
                    this.resultContainer.setItem(2, itemStack4);
                    this.broadcastChanges();
                }
            }
        });
    }

    /**
     * Called to determine if the current slot is valid for the stack merging (double-click) code. The stack passed in is null for the initial slot that was double-clicked.
     */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultContainer && super.canTakeItemForPickAll(stack, slot);
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
            if (index == 2) {
                itemStack2.getItem().onCraftedBy(itemStack2, player.level(), player);
                if (!this.moveItemStackTo(itemStack2, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemStack2, itemStack);
            } else if (index != 1 && index != 0) {
                if (itemStack2.is(Items.FILLED_MAP)) {
                    if (!this.moveItemStackTo(itemStack2, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!itemStack2.is(Items.PAPER) && !itemStack2.is(Items.MAP) && !itemStack2.is(Items.GLASS_PANE)) {
                    if (index >= 3 && index < 30) {
                        if (!this.moveItemStackTo(itemStack2, 30, 39, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (index >= 30 && index < 39 && !this.moveItemStackTo(itemStack2, 3, 30, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemStack2, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemStack2, 3, 39, false)) {
                return ItemStack.EMPTY;
            }

            if (itemStack2.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            }

            slot.setChanged();
            if (itemStack2.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemStack2);
            this.broadcastChanges();
        }

        return itemStack;
    }

    /**
     * Called when the container is closed.
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultContainer.removeItemNoUpdate(2);
        this.access.execute((level, blockPos) -> this.clearContainer(player, this.container));
    }
}
