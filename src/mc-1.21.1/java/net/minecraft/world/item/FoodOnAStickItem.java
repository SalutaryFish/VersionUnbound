package net.minecraft.world.item;

import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ItemSteerable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class FoodOnAStickItem<T extends Entity & ItemSteerable> extends Item {
    private final EntityType<T> canInteractWith;
    private final int consumeItemDamage;

    public FoodOnAStickItem(Item.Properties properties, EntityType<T> canInteractWith, int consumeItemDamage) {
        super(properties);
        this.canInteractWith = canInteractWith;
        this.consumeItemDamage = consumeItemDamage;
    }

    /**
     * Called to trigger the item's "innate" right click behavior. To handle when this item is used on a Block, see {@link #onItemUse}.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.pass(itemStack);
        } else {
            Entity entity = player.getControlledVehicle();
            if (player.isPassenger() && entity instanceof ItemSteerable itemSteerable && entity.getType() == this.canInteractWith && itemSteerable.boost()) {
                EquipmentSlot equipmentSlot = LivingEntity.getSlotForHand(hand);
                ItemStack itemStack2 = itemStack.hurtAndConvertOnBreak(this.consumeItemDamage, Items.FISHING_ROD, player, equipmentSlot);
                return InteractionResultHolder.success(itemStack2);
            } else {
                player.awardStat(Stats.ITEM_USED.get(this));
                return InteractionResultHolder.pass(itemStack);
            }
        }
    }
}
