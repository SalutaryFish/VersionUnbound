package net.salutaryfish.versionunbound.compat.v1_21_1;

import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** 1.21.1's {@code Entity} members. */
public final class EntityBridge {
    private EntityBridge() {}

    /**
     * {@code hasPermissions(int)}: 1.21.1 compared {@code getPermissionLevel()} (0 but for players) with the level; 26.2
     * gives only players a permission set.
     */
    public static boolean hasPermissions(Entity entity, int level) {
        if (entity instanceof Player player) {
            return player.permissions().hasPermission(commandLevel(level));
        }
        return level <= 0;
    }

    /** 26.2's permission for 1.21.1's command level {@code level} (clamped to 0–4, as 26.2's levels are). */
    static Permission commandLevel(int level) {
        return new Permission.HasCommandLevel(PermissionLevel.byId(level));
    }
}
