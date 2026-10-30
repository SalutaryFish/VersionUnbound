package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code ServerPlayer}, for the events it fires: death (cancellable; the player overrides
 * {@code LivingEntity.die}), travel to dimension (cancellable, at the head of every teleport), changed dimension (at the
 * end of a teleport to another level), clone, tossing the selected item ({@code ItemTossEvent}, as in
 * {@code PlayerMixin}), and {@code PlayerContainerEvent} when a menu opens (also a horse's or nautilus's inventory) or
 * closes.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;gameEvent(Lnet/minecraft/core/Holder;)V", shift = At.Shift.AFTER), cancellable = true)
    private void versionunbound$livingDeath(DamageSource source, CallbackInfo ci) {
        if (CommonHooks.onLivingDeath((ServerPlayer) (Object) this, source)) ci.cancel();
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At("HEAD"), cancellable = true)
    private void versionunbound$travelToDimension(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir) {
        if (!CommonHooks.onTravelToDimension((ServerPlayer) (Object) this, transition.newLevel().dimension())) cir.setReturnValue(null);
    }

    // NeoForge first sends the player's data attachments again (the client loses them on a dimension change); that
    // arrives with attachments (Phase 2 step 9).
    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;teleportSpectators(Lnet/minecraft/world/level/portal/TeleportTransition;Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER))
    private void versionunbound$changedDimension(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir, @Local ResourceKey<Level> lastDimension) {
        EventHooks.firePlayerChangedDimensionEvent((ServerPlayer) (Object) this, lastDimension, transition.newLevel().dimension());
    }

    // NeoForge also copies the persisted part of the old player's persistent data first, which arrives with the entity
    // extension (getPersistentData, Phase 2 step 5).
    @Inject(method = "restoreFrom", at = @At("TAIL"))
    private void versionunbound$clone(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
        EventHooks.onPlayerClone((ServerPlayer) (Object) this, oldPlayer, !restoreAll);
    }

    // NeoForge also asks the item (onDroppedByPlayer, the item stack extension, Phase 2 step 5; vanilla's answer is yes).
    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void versionunbound$dropNothing(boolean all, CallbackInfo ci) {
        ItemStack selected = ((ServerPlayer) (Object) this).getInventory().getSelectedItem();
        if (selected.isEmpty()) ci.cancel();
    }

    @WrapOperation(method = "drop(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity versionunbound$tossSelected(ServerPlayer player, ItemStack removed, boolean randomly, boolean thrownFromHand, Operation<ItemEntity> original) {
        return CommonHooks.onPlayerTossEvent(player, removed, randomly, thrownFromHand);
    }

    // Once the new menu is the player's (NeoForge's extra-data screen packet comes with networking, Phase 2 step 7).
    @Inject(method = "openMenu", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayer;containerMenu:Lnet/minecraft/world/inventory/AbstractContainerMenu;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void versionunbound$containerOpen(CallbackInfoReturnable<OptionalInt> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Open(self, self.containerMenu));
    }

    @Inject(method = {"openHorseInventory", "openNautilusInventory"}, at = @At("TAIL"))
    private void versionunbound$mountContainerOpen(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Open(self, self.containerMenu));
    }

    // Before the player's menu goes back to the inventory.
    @Inject(method = "doCloseContainer", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayer;containerMenu:Lnet/minecraft/world/inventory/AbstractContainerMenu;", opcode = Opcodes.PUTFIELD))
    private void versionunbound$containerClose(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(self, self.containerMenu));
    }
}
