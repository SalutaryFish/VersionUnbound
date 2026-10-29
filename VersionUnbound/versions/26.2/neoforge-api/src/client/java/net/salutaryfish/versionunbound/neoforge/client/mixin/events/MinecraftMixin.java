package net.salutaryfish.versionunbound.neoforge.client.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code Minecraft}, for the events it fires: client ticks (once the game has loaded), client level
 * ticks, level unload (on a dimension change and on leaving the world), game shutting down, empty left and right
 * clicks, and the local player logging out. The loading hooks are in the other {@code MinecraftMixin}. Not yet:
 * {@code InputEvent.InteractionKeyMappingTriggered} around the clicks, with the client input events (Phase 5).
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Shadow
    public @Nullable ClientLevel level;

    @Shadow
    private boolean gameLoadFinished;

    @Shadow
    public @Nullable LocalPlayer player;

    @Shadow
    public @Nullable MultiPlayerGameMode gameMode;

    @Shadow
    public @Nullable HitResult hitResult;

    @Shadow
    public abstract boolean isRunning();

    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;clientTickCount:J", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void versionunbound$clientTickPre(CallbackInfo ci) {
        if (this.gameLoadFinished) {
            ClientHooks.fireClientTickPre();
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void versionunbound$levelTick(ClientLevel level, BooleanSupplier haveTime, Operation<Void> original) {
        EventHooks.fireLevelTickPre(level, haveTime);
        original.call(level, haveTime);
        EventHooks.fireLevelTickPost(level, haveTime);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void versionunbound$clientTickPost(CallbackInfo ci) {
        if (this.gameLoadFinished) {
            ClientHooks.fireClientTickPost();
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void versionunbound$unloadPreviousLevel(ClientLevel level, CallbackInfo ci) {
        if (this.level != null) NeoForge.EVENT_BUS.post(new LevelEvent.Unload(this.level));
    }

    // Just before vanilla clears the level field.
    @Inject(method = { "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", "clearClientLevel" }, at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;level:Lnet/minecraft/client/multiplayer/ClientLevel;", opcode = Opcodes.PUTFIELD))
    private void versionunbound$unloadLevel(CallbackInfo ci) {
        if (this.level != null) NeoForge.EVENT_BUS.post(new LevelEvent.Unload(this.level));
    }

    // An attack that hit nothing.
    @Inject(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V", shift = At.Shift.AFTER))
    private void versionunbound$emptyLeftClick(CallbackInfoReturnable<Boolean> cir) {
        CommonHooks.onEmptyLeftClick(this.player);
    }

    // For each hand, after the entity and block interactions and before the item is used.
    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 1))
    private void versionunbound$emptyRightClick(CallbackInfo ci, @Local InteractionHand hand, @Local ItemStack heldItem) {
        if (heldItem.isEmpty() && (this.hitResult == null || this.hitResult.getType() == HitResult.Type.MISS))
            CommonHooks.onEmptyClick(this.player, hand);
    }

    // After the game renderer is reset, before the game mode is cleared.
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameMode:Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;", opcode = Opcodes.PUTFIELD))
    private void versionunbound$loggingOut(CallbackInfo ci) {
        ClientHooks.firePlayerLogout(this.gameMode, this.player);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void versionunbound$gameShuttingDown(CallbackInfo ci) {
        if (this.isRunning()) NeoForge.EVENT_BUS.post(new GameShuttingDownEvent());
    }
}
