package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

public interface Equipable {
    EquipmentSlot getEquipmentSlot();

    default Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_GENERIC;
    }

    default InteractionResultHolder<ItemStack> swapWithEquipmentSlot(Item item, Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        EquipmentSlot equipmentSlot = player.getEquipmentSlotForItem(itemStack);
        if (!player.canUseSlot(equipmentSlot)) {
            return InteractionResultHolder.pass(itemStack);
        }

        ItemStack itemStack2 = player.getItemBySlot(equipmentSlot);
        if ((!EnchantmentHelper.has(itemStack2, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) || player.isCreative())
            && !ItemStack.matches(itemStack, itemStack2)) {
            if (!level.isClientSide()) {
                player.awardStat(Stats.ITEM_USED.get(item));
            }

            ItemStack itemStack3 = itemStack2.isEmpty() ? itemStack : itemStack2.copyAndClear();
            ItemStack itemStack4 = player.isCreative() ? itemStack.copy() : itemStack.copyAndClear();
            player.setItemSlot(equipmentSlot, itemStack4);
            return InteractionResultHolder.sidedSuccess(itemStack3, level.isClientSide());
        } else {
            return InteractionResultHolder.fail(itemStack);
        }
    }

    @Nullable
    static Equipable get(ItemStack stack) {
        if (stack.getItem() instanceof Equipable equipable) {
            return equipable;
        } else {
            return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof Equipable equipable2 ? equipable2 : null;
        }
    }
}
