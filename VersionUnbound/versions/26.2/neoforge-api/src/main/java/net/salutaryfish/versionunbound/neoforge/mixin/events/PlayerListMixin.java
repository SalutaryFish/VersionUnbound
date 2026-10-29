package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code PlayerList}, for the events it fires: player logged in (once the player is fully placed),
 * logged out (before anything is saved or removed), and the respawn position and respawn events. NeoForge also syncs
 * data attachments to the player at login and respawn; that arrives with attachments (Phase 2 step 9).
 */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;resumeFlushing()V", shift = At.Shift.AFTER))
    private void versionunbound$playerLoggedIn(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        EventHooks.firePlayerLoggedIn(player);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void versionunbound$playerLoggedOut(ServerPlayer player, CallbackInfo ci) {
        EventHooks.firePlayerLoggedOut(player);
    }

    // Neo: Allow changing the respawn position of players. The local dimension transition is updated with the new target.
    @ModifyVariable(method = "respawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removePlayerImmediately(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity$RemovalReason;)V", shift = At.Shift.AFTER))
    private TeleportTransition versionunbound$respawnPosition(TeleportTransition respawnInfo, @Local(argsOnly = true) ServerPlayer serverPlayer,
            @Local(argsOnly = true) boolean keepAllPlayerData, @Share("respawnPosition") LocalRef<PlayerRespawnPositionEvent> event) {
        event.set(EventHooks.firePlayerRespawnPositionEvent(serverPlayer, respawnInfo, keepAllPlayerData));
        return event.get().getTeleportTransition();
    }

    // Neo: Allow the event to control if the original spawn position is copied
    @ModifyExpressionValue(method = "respawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/TeleportTransition;missingRespawnBlock()Z", ordinal = 0))
    private boolean versionunbound$copyOriginalSpawnPosition(boolean missingRespawnBlock, @Share("respawnPosition") LocalRef<PlayerRespawnPositionEvent> event) {
        return !event.get().copyOriginalSpawnPosition();
    }

    // Once the new player is in the level and its menu and health are set up. The new player is the second ServerPlayer
    // local, after the old one (the first parameter).
    @Inject(method = "respawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;setHealth(F)V", shift = At.Shift.AFTER))
    private void versionunbound$playerRespawned(ServerPlayer serverPlayer, boolean keepAllPlayerData, Entity.RemovalReason removalReason,
            CallbackInfoReturnable<ServerPlayer> cir, @Local(ordinal = 1) ServerPlayer player) {
        EventHooks.firePlayerRespawnEvent(player, keepAllPlayerData);
    }
}
