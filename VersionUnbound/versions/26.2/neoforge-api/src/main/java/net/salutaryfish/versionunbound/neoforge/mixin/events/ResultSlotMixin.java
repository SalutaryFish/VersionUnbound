package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code ResultSlot}: {@code PlayerEvent.ItemCraftedEvent} when a player takes a crafted item (after
 * the item's own {@code onCraftedBy}), and the crafting player ({@code CommonHooks.getCraftingPlayer}) set while the
 * recipe works out what stays in the grid.
 */
@Mixin(ResultSlot.class)
abstract class ResultSlotMixin {
    @Shadow
    @Final
    private Player player;

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Inject(method = "checkTakeAchievements", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;onCraftedBy(Lnet/minecraft/world/entity/player/Player;I)V", shift = At.Shift.AFTER))
    private void versionunbound$itemCrafted(ItemStack carried, CallbackInfo ci) {
        EventHooks.firePlayerCraftingEvent(this.player, carried, this.craftSlots);
    }

    @WrapOperation(method = "onTake", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/ResultSlot;getRemainingItems(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Lnet/minecraft/core/NonNullList;"))
    private NonNullList<ItemStack> versionunbound$craftingPlayer(ResultSlot slot, CraftingInput input, Level level, Operation<NonNullList<ItemStack>> original, Player player) {
        CommonHooks.setCraftingPlayer(player);
        NonNullList<ItemStack> remaining = original.call(slot, input, level);
        CommonHooks.setCraftingPlayer(null);
        return remaining;
    }
}
