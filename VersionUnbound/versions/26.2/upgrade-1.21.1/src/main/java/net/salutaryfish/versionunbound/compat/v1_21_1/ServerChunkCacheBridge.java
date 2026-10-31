package net.salutaryfish.versionunbound.compat.v1_21_1;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * 1.21.1's {@code ServerChunkCache} ticket members. 1.21.1 told tickets of one type apart by a value (the {@code T} of
 * {@code TicketType<T>}); 26.2's tickets have no value, so two of one type, position and radius are one ticket.
 */
public final class ServerChunkCacheBridge {
    private ServerChunkCacheBridge() {}

    /** {@code addRegionTicket(TicketType<T>, ChunkPos, int distance, T)}: 1.21.1's distance is 26.2's radius. */
    public static void addRegionTicket(ServerChunkCache cache, TicketType type, ChunkPos pos, int distance, Object value) {
        cache.addTicketWithRadius(type, pos, distance);
    }

    /** {@code removeRegionTicket(TicketType<T>, ChunkPos, int distance, T)}. */
    public static void removeRegionTicket(ServerChunkCache cache, TicketType type, ChunkPos pos, int distance, Object value) {
        cache.removeTicketWithRadius(type, pos, distance);
    }
}
