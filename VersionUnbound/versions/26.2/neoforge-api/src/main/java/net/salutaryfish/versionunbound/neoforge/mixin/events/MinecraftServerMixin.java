package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code MinecraftServer}, for the events it fires: server started, stopping and stopped, server and
 * level ticks, level load and unload, and the world spawn of a new world. Server about-to-start and starting fire from
 * each server's {@code initServer} ({@code DedicatedServerMixin}, and {@code IntegratedServerMixin} on the client).
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    @Final
    private Map<ResourceKey<Level>, ServerLevel> levels;

    // Once initServer has succeeded, before the first tick.
    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getNanos()J", ordinal = 0))
    private void versionunbound$serverStarted(CallbackInfo ci) {
        ServerLifecycleHooks.handleServerStarted((MinecraftServer) (Object) this);
    }

    // NeoForge posts this at the end of runServer's try block, when the tick loop ends without a crash. The closest
    // instruction is the first one of the inlined finally block, which is inside the try around stopServer(): a listener
    // that throws there would make vanilla skip stopServer and with it the world save. So the exception is logged
    // instead, and the server stops as usual (NeoForge writes a crash report and then stops the server too).
    @Inject(method = "runServer", at = @At(value = "FIELD", target = "Lnet/minecraft/server/MinecraftServer;stopped:Z", opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void versionunbound$serverStopping(CallbackInfo ci) {
        try {
            ServerLifecycleHooks.handleServerStopping((MinecraftServer) (Object) this);
        } catch (Throwable t) {
            LOGGER.error("Exception in a ServerStoppingEvent listener, stopping the server anyway", t);
        }
        ServerLifecycleHooks.expectServerStopped(); // Forge: Has to come before MinecraftServer#onServerCrash to avoid race conditions
    }

    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;onServerCrash(Lnet/minecraft/CrashReport;)V"))
    private void versionunbound$expectStopAfterCrash(CallbackInfo ci) {
        ServerLifecycleHooks.expectServerStopped(); // Forge: Has to come before MinecraftServer#onServerCrash to avoid race conditions
    }

    // Every copy of the finally block, whichever way runServer ends.
    @Inject(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;onServerExit()V"))
    private void versionunbound$serverStopped(CallbackInfo ci) {
        ServerLifecycleHooks.handleServerStopped((MinecraftServer) (Object) this);
    }

    // After the empty-server pause check, as in NeoForge: a paused server doesn't tick.
    @Inject(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerTickRateManager;tick()V"))
    private void versionunbound$serverTickPre(BooleanSupplier haveTime, CallbackInfo ci) {
        EventHooks.fireServerTickPre(haveTime, (MinecraftServer) (Object) this);
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void versionunbound$serverTickPost(BooleanSupplier haveTime, CallbackInfo ci) {
        EventHooks.fireServerTickPost(haveTime, (MinecraftServer) (Object) this);
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void versionunbound$levelTick(ServerLevel level, BooleanSupplier haveTime, Operation<Void> original) {
        EventHooks.fireLevelTickPre(level, haveTime);
        original.call(level, haveTime);
        EventHooks.fireLevelTickPost(level, haveTime);
    }

    // The overworld loads once its scoreboard, command storage and stopwatches are loaded, before the world spawn is set.
    @Inject(method = "createLevels", at = @At(value = "FIELD", target = "Lnet/minecraft/server/MinecraftServer;stopwatches:Lnet/minecraft/world/Stopwatches;", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void versionunbound$loadOverworld(CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(this.levels.get(Level.OVERWORLD)));
    }

    // The other dimensions load as they are added.
    @WrapOperation(method = "createLevels", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 1))
    private Object versionunbound$loadLevel(Map<Object, Object> levels, Object dimension, Object level, Operation<Object> original) {
        Object previous = original.call(levels, dimension, level);
        NeoForge.EVENT_BUS.post(new LevelEvent.Load((ServerLevel) level));
        return previous;
    }

    @Inject(method = "setInitialSpawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getChunkSource()Lnet/minecraft/server/level/ServerChunkCache;", ordinal = 0, shift = At.Shift.AFTER), cancellable = true)
    private static void versionunbound$createWorldSpawn(ServerLevel level, ServerLevelData levelData, boolean spawnBonusChest, boolean isDebug, LevelLoadListener levelLoadListener, CallbackInfo ci) {
        if (EventHooks.onCreateWorldSpawn(level, levelData)) ci.cancel();
    }

    // Only once the level closed without an exception.
    @WrapOperation(method = "stopServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;close()V"))
    private void versionunbound$unloadLevel(ServerLevel level, Operation<Void> original) {
        original.call(level);
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
    }
}
