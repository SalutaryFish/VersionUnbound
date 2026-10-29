package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code DedicatedServer}, for the events it fires: server about to start (just before the level
 * loads), server starting (once {@code initServer} has succeeded) and game shutting down. NeoForge's LAN pinger for
 * dedicated servers and its console handler aren't ported.
 */
@Mixin(DedicatedServer.class)
abstract class DedicatedServerMixin {
    @Inject(method = "initServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/dedicated/DedicatedServer;loadLevel()V"))
    private void versionunbound$serverAboutToStart(CallbackInfoReturnable<Boolean> cir) {
        ServerLifecycleHooks.handleServerAboutToStart((MinecraftServer) (Object) this);
    }

    @Inject(method = "initServer", at = @At("TAIL"))
    private void versionunbound$serverStarting(CallbackInfoReturnable<Boolean> cir) {
        ServerLifecycleHooks.handleServerStarting((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;stopServer()V"))
    private void versionunbound$gameShuttingDown(CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new GameShuttingDownEvent());
    }
}
