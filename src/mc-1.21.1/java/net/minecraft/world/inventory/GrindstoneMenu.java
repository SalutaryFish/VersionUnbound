package net.minecraft.world.inventory;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class GrindstoneMenu extends AbstractContainerMenu {
    public static final int MAX_NAME_LENGTH = 35;
    public static final int INPUT_SLOT = 0;
    public static final int ADDITIONAL_SLOT = 1;
    public static final int RESULT_SLOT = 2;
    private static final int INV_SLOT_START = 3;
    private static final int INV_SLOT_END = 30;
    private static final int USE_ROW_SLOT_START = 30;
    private static final int USE_ROW_SLOT_END = 39;
    /**
     * The inventory slot that stores the output of the crafting recipe.
     */
    private final Container resultSlots = new ResultContainer();
    final Container repairSlots = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            GrindstoneMenu.this.slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;

    public GrindstoneMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, ContainerLevelAccess.NULL);
    }

    public GrindstoneMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(MenuType.GRINDSTONE, containerId);
        this.access = access;
        this.addSlot(new Slot(this.repairSlots, 0, 49, 19) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(itemStack);
            }
        });
        this.addSlot(new Slot(this.repairSlots, 1, 49, 40) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return itemStack.isDamageableItem() || EnchantmentHelper.hasAnyEnchantments(itemStack);
            }
        });
        this.addSlot(new Slot(this.resultSlots, 2, 129, 34) {
            /**
             * Check if the stack is allowed to be placed in this slot, used for armor slots as well as furnace fuel.
             */
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                access.execute((level, blockPos) -> {
                    if (level instanceof ServerLevel) {
                        ExperienceOrb.award((ServerLevel)level, Vec3.atCenterOf(blockPos), this.getExperienceAmount(level));
                    }

                    level.levelEvent(1042, blockPos, 0);
                });
                GrindstoneMenu.this.repairSlots.setItem(0, ItemStack.EMPTY);
                GrindstoneMenu.this.repairSlots.setItem(1, ItemStack.EMPTY);
            }

            /**
             * Returns the total amount of XP stored in all the input slots of this container. The return value is randomized, so that it returns between 50% and 100% of the total XP.
             */
            private int getExperienceAmount(Level level) {
                int i = 0;
                i += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(0));
                i += this.getExperienceFromItem(GrindstoneMenu.this.repairSlots.getItem(1));
                if (i > 0) {
                    int j = (int)Math.ceil(i / 2.0);
                    return j + level.random.nextInt(j);
                } else {
                    return 0;
                }
            }

            /**
             * Returns the total amount of XP stored in the enchantments of this stack.
             */
            private int getExperienceFromItem(ItemStack stack) {
                int i = 0;
                ItemEnchantments itemEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);

                for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
                    Holder<Enchantment> holder = entry.getKey();
                    int j = entry.getIntValue();
                    if (!holder.is(EnchantmentTags.CURSE)) {
                        i += holder.value().getMinCost(j);
                    }
                }

                return i;
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
     * Callback for when the crafting matrix is changed.
     */
    @Override
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        if (inventory == this.repairSlots) {
            this.createResult();
        }
    }

    private void createResult() {
        this.resultSlots.setItem(0, this.computeResult(this.repairSlots.getItem(0), this.repairSlots.getItem(1)));
        this.broadcastChanges();
    }

    private ItemStack computeResult(ItemStack inputItem, ItemStack additionalItem) {
        boolean bl = !inputItem.isEmpty() || !additionalItem.isEmpty();
        if (!bl) {
            return ItemStack.EMPTY;
        }

        if (inputItem.getCount() <= 1 && additionalItem.getCount() <= 1) {
            boolean bl2 = !inputItem.isEmpty() && !additionalItem.isEmpty();
            if (!bl2) {
                ItemStack itemStack = !inputItem.isEmpty() ? inputItem : additionalItem;
                return !EnchantmentHelper.hasAnyEnchantments(itemStack) ? ItemStack.EMPTY : this.removeNonCursesFrom(itemStack.copy());
            } else {
                return this.mergeItems(inputItem, additionalItem);
            }
        } else {
            return ItemStack.EMPTY;
        }
    }

    private ItemStack mergeItems(ItemStack inputItem, ItemStack additionalItem) {
        if (!inputItem.is(additionalItem.getItem())) {
            return ItemStack.EMPTY;
        }

        int i = Math.max(inputItem.getMaxDamage(), additionalItem.getMaxDamage());
        int j = inputItem.getMaxDamage() - inputItem.getDamageValue();
        int k = additionalItem.getMaxDamage() - additionalItem.getDamageValue();
        int l = j + k + i * 5 / 100;
        int m = 1;
        if (!inputItem.isDamageableItem()) {
            if (inputItem.getMaxStackSize() < 2 || !ItemStack.matches(inputItem, additionalItem)) {
                return ItemStack.EMPTY;
            }

            m = 2;
        }

        ItemStack itemStack = inputItem.copyWithCount(m);
        if (itemStack.isDamageableItem()) {
            itemStack.set(DataComponents.MAX_DAMAGE, i);
            itemStack.setDamageValue(Math.max(i - l, 0));
        }

        this.mergeEnchantsFrom(itemStack, additionalItem);
        return this.removeNonCursesFrom(itemStack);
    }

    private void mergeEnchantsFrom(ItemStack inputItem, ItemStack additionalItem) {
        EnchantmentHelper.updateEnchantments(inputItem, mutable -> {
            ItemEnchantments itemEnchantments = EnchantmentHelper.getEnchantmentsForCrafting(additionalItem);

            for (Entry<Holder<Enchantment>> entry : itemEnchantments.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                if (!holder.is(EnchantmentTags.CURSE) || mutable.getLevel(holder) == 0) {
                    mutable.upgrade(holder, entry.getIntValue());
                }
            }
        });
    }

    private ItemStack removeNonCursesFrom(ItemStack item) {
        ItemEnchantments itemEnchantments = EnchantmentHelper.updateEnchantments(item, mutable -> mutable.removeIf(holder -> !holder.is(EnchantmentTags.CURSE)));
        if (item.is(Items.ENCHANTED_BOOK) && itemEnchantments.isEmpty()) {
            item = item.transmuteCopy(Items.BOOK);
        }

        int i = 0;

        for (int j = 0; j < itemEnchantments.size(); j++) {
            i = AnvilMenu.calculateIncreasedRepairCost(i);
        }

        item.set(DataComponents.REPAIR_COST, i);
        return item;
    }

    /**
     * Called when the container is closed.
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, blockPos) -> this.clearContainer(player, this.repairSlots));
    }

    /**
     * Determines whether supplied player can use this container
     */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, Blocks.GRINDSTONE);
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
            ItemStack itemStack3 = this.repairSlots.getItem(0);
            ItemStack itemStack4 = this.repairSlots.getItem(1);
            if (index == 2) {
                if (!this.moveItemStackTo(itemStack2, 3, 39, true)) {
                    return ItemStack.EMPTY;
                }

                slot.onQuickCraft(itemStack2, itemStack);
            } else if (index != 0 && index != 1) {
                if (!itemStack3.isEmpty() && !itemStack4.isEmpty()) {
                    if (index >= 3 && index < 30) {
                        if (!this.moveItemStackTo(itemStack2, 30, 39, false)) {
                            return ItemStack.EMPTY;
                        }
                    } else if (index >= 30 && index < 39 && !this.moveItemStackTo(itemStack2, 3, 30, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemStack2, 0, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemStack2, 3, 39, false)) {
                return ItemStack.EMPTY;
            }

            if (itemStack2.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (itemStack2.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, itemStack2);
        }

        return itemStack;
    }
}
