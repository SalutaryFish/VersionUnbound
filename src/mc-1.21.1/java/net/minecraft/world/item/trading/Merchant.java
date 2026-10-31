package net.minecraft.world.item.trading;

import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;

public interface Merchant {
    void setTradingPlayer(@Nullable Player var1);

    @Nullable
    Player getTradingPlayer();

    MerchantOffers getOffers();

    void overrideOffers(MerchantOffers var1);

    void notifyTrade(MerchantOffer var1);

    /**
     * Notifies the merchant of a possible merchant recipe being fulfilled or not. Usually, this is just a sound byte being played depending on whether the suggested {@link net.minecraft.world.item.ItemStack} is not empty.
     */
    void notifyTradeUpdated(ItemStack var1);

    int getVillagerXp();

    void overrideXp(int var1);

    boolean showProgressBar();

    SoundEvent getNotifyTradeSound();

    default boolean canRestock() {
        return false;
    }

    default void openTradingScreen(Player player, Component displayName, int level) {
        OptionalInt optionalInt = player.openMenu(
            new SimpleMenuProvider((containerId, inventory, playerx) -> new MerchantMenu(containerId, inventory, this), displayName)
        );
        if (optionalInt.isPresent()) {
            MerchantOffers merchantOffers = this.getOffers();
            if (!merchantOffers.isEmpty()) {
                player.sendMerchantOffers(optionalInt.getAsInt(), merchantOffers, level, this.getVillagerXp(), this.showProgressBar(), this.canRestock());
            }
        }
    }

    boolean isClientSide();
}
