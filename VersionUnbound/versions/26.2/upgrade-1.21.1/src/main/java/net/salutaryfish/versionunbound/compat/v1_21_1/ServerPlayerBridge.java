package net.salutaryfish.versionunbound.compat.v1_21_1;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** 1.21.1's {@code ServerPlayer} members. */
public final class ServerPlayerBridge {
    private ServerPlayerBridge() {}

    /**
     * {@code teleportTo(ServerLevel, x, y, z, yaw, pitch)}: 1.21.1 reset the camera, dismounted the player and then
     * teleported it (within the level) or changed its dimension; 26.2's eight-argument {@code teleportTo} does the same
     * through a {@code TeleportTransition}, once the player is off its vehicle.
     */
    public static void teleportTo(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        player.stopRiding();
        player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true);
    }
}
