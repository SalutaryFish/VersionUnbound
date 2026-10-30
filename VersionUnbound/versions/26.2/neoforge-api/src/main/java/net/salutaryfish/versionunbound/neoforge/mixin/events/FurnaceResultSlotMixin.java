package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.EventHooks;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge's patch to {@code FurnaceResultSlot}: {@code PlayerEvent.ItemSmeltedEvent} when a player takes smelted items. */
@Mixin(FurnaceResultSlot.class)
abstract class FurnaceResultSlotMixin {
    @Shadow
    @Final
    private Player player;

    @Shadow
    private int removeCount;

    // Before the count is reset, the method's last statement.
    @Inject(method = "checkTakeAchievements", at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/FurnaceResultSlot;removeCount:I", opcode = Opcodes.PUTFIELD))
    private void versionunbound$itemSmelted(ItemStack carried, CallbackInfo ci) {
        if (this.removeCount != 0) {
            EventHooks.firePlayerSmeltedEvent(this.player, carried, this.removeCount);
        }
    }
}
