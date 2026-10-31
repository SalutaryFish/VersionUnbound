package net.minecraft.world.inventory;

import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

public class MerchantResultSlot extends Slot {
    private final MerchantContainer slots;
    private final Player player;
    private int removeCount;
    private final Merchant merchant;

    public MerchantResultSlot(Player player, Merchant merchant, MerchantContainer slots, int slot, int xPosition, int yPosition) {
        super(slots, slot, xPosition, yPosition);
        this.player = player;
        this.merchant = merchant;
        this.slots = slots;
    }

    /**
     * Check if the stack is allowed to be placed in this slot, used for armor slots as well as furnace fuel.
     */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    /**
     * Decrease the size of the stack in slot (first int arg) by the amount of the second int arg. Returns the new stack.
     */
    @Override
    public ItemStack remove(int amount) {
        if (this.hasItem()) {
            this.removeCount = this.removeCount + Math.min(amount, this.getItem().getCount());
        }

        return super.remove(amount);
    }

    /**
     * Typically increases an internal count, then calls {@code onCrafting(item)}.
     *
     * @param stack the output - ie, iron ingots, and pickaxes, not ore and wood.
     */
    @Override
    protected void onQuickCraft(ItemStack stack, int amount) {
        this.removeCount += amount;
        this.checkTakeAchievements(stack);
    }

    /**
     * @param stack the output - ie, iron ingots, and pickaxes, not ore and wood.
     */
    @Override
    protected void checkTakeAchievements(ItemStack stack) {
        stack.onCraftedBy(this.player.level(), this.player, this.removeCount);
        this.removeCount = 0;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        this.checkTakeAchievements(stack);
        MerchantOffer merchantOffer = this.slots.getActiveOffer();
        if (merchantOffer != null) {
            ItemStack itemStack = this.slots.getItem(0);
            ItemStack itemStack2 = this.slots.getItem(1);
            if (merchantOffer.take(itemStack, itemStack2) || merchantOffer.take(itemStack2, itemStack)) {
                this.merchant.notifyTrade(merchantOffer);
                player.awardStat(Stats.TRADED_WITH_VILLAGER);
                this.slots.setItem(0, itemStack);
                this.slots.setItem(1, itemStack2);
            }

            this.merchant.overrideXp(this.merchant.getVillagerXp() + merchantOffer.getXp());
        }
    }
}
