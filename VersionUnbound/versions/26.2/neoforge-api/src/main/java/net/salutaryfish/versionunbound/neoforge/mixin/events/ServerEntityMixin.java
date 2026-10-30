package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code ServerEntity}: {@code PlayerEvent.StartTracking} once a player has been sent an entity,
 * {@code StopTracking} once it has been told to remove it. Fabric API's tracking events fire at the end of
 * {@code addPairing} and at the start of {@code removePairing}.
 */
@Mixin(ServerEntity.class)
abstract class ServerEntityMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "addPairing", at = @At("TAIL"))
    private void versionunbound$startTracking(ServerPlayer player, CallbackInfo ci) {
        EventHooks.onStartEntityTracking(this.entity, player);
    }

    @Inject(method = "removePairing", at = @At("TAIL"))
    private void versionunbound$stopTracking(ServerPlayer player, CallbackInfo ci) {
        EventHooks.onStopEntityTracking(this.entity, player);
    }
}
