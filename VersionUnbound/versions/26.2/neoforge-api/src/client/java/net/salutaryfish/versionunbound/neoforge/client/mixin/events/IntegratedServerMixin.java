package net.salutaryfish.versionunbound.neoforge.client.mixin.events;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code IntegratedServer}: server about to start just before the level loads, and server starting at
 * the end of {@code initServer}.
 */
@Mixin(IntegratedServer.class)
abstract class IntegratedServerMixin {
    @Inject(method = "initServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;loadLevel()V"))
    private void versionunbound$serverAboutToStart(CallbackInfoReturnable<Boolean> cir) {
        ServerLifecycleHooks.handleServerAboutToStart((MinecraftServer) (Object) this);
    }

    @Inject(method = "initServer", at = @At("TAIL"))
    private void versionunbound$serverStarting(CallbackInfoReturnable<Boolean> cir) {
        ServerLifecycleHooks.handleServerStarting((MinecraftServer) (Object) this);
    }
}
