package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code PlayerChunkSender}: {@code ChunkWatchEvent.Sent} once a chunk has been sent to a player.
 * Not yet: the auxiliary light data NeoForge adds to the chunk packet (the block getter extension, Phase 2 step 5).
 */
@Mixin(PlayerChunkSender.class)
abstract class PlayerChunkSenderMixin {
    @Inject(method = "sendChunk", at = @At("TAIL"))
    private static void versionunbound$chunkSent(ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk, CallbackInfo ci) {
        EventHooks.fireChunkSent(connection.player, chunk, level);
    }
}
