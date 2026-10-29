package net.salutaryfish.versionunbound.neoforge.client.mixin.events;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code ClientPacketListener}, for the client player network events: logging in (once the new
 * local player exists, before it joins the level) and clone (on respawn, before the new local player joins the level).
 * Logging out fires from {@code Minecraft.disconnect} ({@code MinecraftMixin}).
 *
 * <p>NeoForge's code reads the listener's {@code minecraft} field, which {@code ClientCommonPacketListenerImpl} declares
 * (so it can't be shadowed here); it holds {@code Minecraft.getInstance()}.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
    @Inject(method = "handleLogin", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;resetPos()V", shift = At.Shift.AFTER))
    private void versionunbound$loggingIn(ClientboundLoginPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientHooks.firePlayerLogin(minecraft.gameMode, minecraft.player, minecraft.getConnection().getConnection());
    }

    // The old local player is the first LocalPlayer local, the new one the second.
    @Inject(method = "handleRespawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addEntity(Lnet/minecraft/world/entity/Entity;)V"))
    private void versionunbound$clone(ClientboundRespawnPacket packet, CallbackInfo ci, @Local(ordinal = 0) LocalPlayer oldPlayer, @Local(ordinal = 1) LocalPlayer newPlayer) {
        ClientHooks.firePlayerRespawn(Minecraft.getInstance().gameMode, oldPlayer, newPlayer, newPlayer.connection.getConnection());
    }
}
