package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code ThrownEnderpearl}: {@code EntityTeleportEvent.EnderPearl} when a pearl thrown by a player
 * lands. A canceled event skips the whole teleport (endermite, teleport, damage and sound; the pearl is still
 * discarded). Otherwise the player goes to the event's target, the pearl's position by default (vanilla uses the pearl's
 * previous position), keeping their motion and rotation, and takes the event's damage. Pearls thrown by other entities
 * don't fire it.
 */
@Mixin(ThrownEnderpearl.class)
abstract class ThrownEnderpearlMixin {
    // The check that the player is still connected guards the whole player branch, so a canceled event skips it.
    @WrapOperation(method = "onHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isAcceptingMessages()Z"))
    private boolean versionunbound$enderPearlLand(ServerGamePacketListenerImpl connection, Operation<Boolean> original, @Local(argsOnly = true) HitResult hitResult,
            @Local(ordinal = 0) ServerPlayer player, @Share("pearlEvent") LocalRef<EntityTeleportEvent.EnderPearl> pearlEvent) {
        if (!original.call(connection)) return false;
        ThrownEnderpearl self = (ThrownEnderpearl) (Object) this;
        EntityTeleportEvent.EnderPearl event = EventHooks.onEnderPearlLand(player, self.getX(), self.getY(), self.getZ(), self, 5.0F, hitResult);
        pearlEvent.set(event);
        return !event.isCanceled();
    }

    @WrapOperation(method = "onHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;"))
    private ServerPlayer versionunbound$teleportToTarget(ServerPlayer player, TeleportTransition transition, Operation<ServerPlayer> original,
            @Share("pearlEvent") LocalRef<EntityTeleportEvent.EnderPearl> pearlEvent) {
        return original.call(player, new TeleportTransition(
                transition.newLevel(), pearlEvent.get().getTarget(), player.getDeltaMovement(), player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
    }

    @WrapOperation(method = "onHit", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean versionunbound$attackDamage(ServerPlayer newOwner, ServerLevel level, DamageSource source, float damage, Operation<Boolean> original,
            @Share("pearlEvent") LocalRef<EntityTeleportEvent.EnderPearl> pearlEvent) {
        return original.call(newOwner, newOwner.level(), source, pearlEvent.get().getAttackDamage());
    }
}
