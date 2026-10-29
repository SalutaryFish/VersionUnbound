package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code FishingRodItem}: {@code PlayerDestroyItemEvent} when reeling in breaks the rod. Not yet:
 * the rest of the patch ({@code canPerformAction} for the rod's item abilities, with the item extension in step 5).
 */
@Mixin(FishingRodItem.class)
abstract class FishingRodItemMixin {
    @WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
    private void versionunbound$destroyRod(ItemStack itemStack, int dmg, LivingEntity owner, EquipmentSlot slot, Operation<Void> original,
            @Local(argsOnly = true) Player player, @Local(argsOnly = true) InteractionHand hand) {
        ItemStack copy = itemStack.copy();
        original.call(itemStack, dmg, owner, slot);
        if (itemStack.isEmpty()) {
            EventHooks.onPlayerDestroyItem(player, copy, hand);
        }
    }
}
