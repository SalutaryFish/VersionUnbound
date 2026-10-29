package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code ServerPlayer}, for the events it fires: death (cancellable; the player overrides
 * {@code LivingEntity.die}), travel to dimension (cancellable, at the head of every teleport), changed dimension (at the
 * end of a teleport to another level) and clone.
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
}
