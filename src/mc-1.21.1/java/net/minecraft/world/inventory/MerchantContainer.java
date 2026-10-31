package net.minecraft.world.inventory;

import javax.annotation.Nullable;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public class MerchantContainer implements Container {
    private final Merchant merchant;
    private final NonNullList<ItemStack> itemStacks = NonNullList.withSize(3, ItemStack.EMPTY);
    @Nullable
    private MerchantOffer activeOffer;
    private int selectionHint;
    private int futureXp;

    public MerchantContainer(Merchant merchant) {
        this.merchant = merchant;
    }

    @Override
    public int getContainerSize() {
        return this.itemStacks.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemStack : this.itemStacks) {
            if (!itemStack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the stack in the given slot.
     */
    @Override
    public ItemStack getItem(int index) {
        return this.itemStacks.get(index);
    }

    /**
     * Removes up to a specified number of items from an inventory slot and returns them in a new stack.
     */
    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack itemStack = this.itemStacks.get(index);
        if (index == 2 && !itemStack.isEmpty()) {
            return ContainerHelper.removeItem(this.itemStacks, index, itemStack.getCount());
        }

        ItemStack itemStack2 = ContainerHelper.removeItem(this.itemStacks, index, count);
        if (!itemStack2.isEmpty() && this.isPaymentSlot(index)) {
            this.updateSellItem();
        }

        return itemStack2;
    }

    /**
     * if par1 slot has changed, does resetRecipeAndSlots need to be called?
     */
    private boolean isPaymentSlot(int slot) {
        return slot == 0 || slot == 1;
    }

    /**
     * Removes a stack from the given slot and returns it.
     */
    @Override
    public ItemStack removeItemNoUpdate(int index) {
        return ContainerHelper.takeItem(this.itemStacks, index);
    }

    /**
     * Sets the given item stack to the specified slot in the inventory (can be crafting or armor sections).
     */
    @Override
    public void setItem(int index, ItemStack stack) {
        this.itemStacks.set(index, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        if (this.isPaymentSlot(index)) {
            this.updateSellItem();
        }
    }

    /**
     * Don't rename this method to canInteractWith due to conflicts with Container
     */
    @Override
    public boolean stillValid(Player player) {
        return this.merchant.getTradingPlayer() == player;
    }

    @Override
    public void setChanged() {
        this.updateSellItem();
    }

    public void updateSellItem() {
        this.activeOffer = null;
        ItemStack itemStack;
        ItemStack itemStack2;
        if (this.itemStacks.get(0).isEmpty()) {
            itemStack = this.itemStacks.get(1);
            itemStack2 = ItemStack.EMPTY;
        } else {
            itemStack = this.itemStacks.get(0);
            itemStack2 = this.itemStacks.get(1);
        }

        if (itemStack.isEmpty()) {
            this.setItem(2, ItemStack.EMPTY);
            this.futureXp = 0;
        } else {
            MerchantOffers merchantOffers = this.merchant.getOffers();
            if (!merchantOffers.isEmpty()) {
                MerchantOffer merchantOffer = merchantOffers.getRecipeFor(itemStack, itemStack2, this.selectionHint);
                if (merchantOffer == null || merchantOffer.isOutOfStock()) {
                    this.activeOffer = merchantOffer;
                    merchantOffer = merchantOffers.getRecipeFor(itemStack2, itemStack, this.selectionHint);
                }

                if (merchantOffer != null && !merchantOffer.isOutOfStock()) {
                    this.activeOffer = merchantOffer;
                    this.setItem(2, merchantOffer.assemble());
                    this.futureXp = merchantOffer.getXp();
                } else {
                    this.setItem(2, ItemStack.EMPTY);
                    this.futureXp = 0;
                }
            }

            this.merchant.notifyTradeUpdated(this.getItem(2));
        }
    }

    @Nullable
    public MerchantOffer getActiveOffer() {
        return this.activeOffer;
    }

    public void setSelectionHint(int currentRecipeIndex) {
        this.selectionHint = currentRecipeIndex;
        this.updateSellItem();
    }

    @Override
    public void clearContent() {
        this.itemStacks.clear();
    }

    public int getFutureXp() {
        return this.futureXp;
    }
}
