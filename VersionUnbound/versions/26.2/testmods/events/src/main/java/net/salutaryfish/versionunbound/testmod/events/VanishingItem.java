package net.salutaryfish.versionunbound.testmod.events;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An item whose use hands back an empty stack, for the client's {@code PlayerDestroyItemEvent} in
 * {@code MultiPlayerGameMode.useItem}: vanilla's items either change the stack in the hand in place or hand back
 * another item, so none of them reaches that branch.
 */
final class VanishingItem extends Item {
    VanishingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.SUCCESS.heldItemTransformedTo(ItemStack.EMPTY);
    }
}
