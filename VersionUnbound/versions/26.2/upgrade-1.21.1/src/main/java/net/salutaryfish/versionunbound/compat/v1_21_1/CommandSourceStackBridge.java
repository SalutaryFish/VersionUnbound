package net.salutaryfish.versionunbound.compat.v1_21_1;

import net.minecraft.commands.CommandSourceStack;

/** 1.21.1's {@code CommandSourceStack} members. */
public final class CommandSourceStackBridge {
    private CommandSourceStackBridge() {}

    /** {@code hasPermission(int)}: 1.21.1 compared the source's permission level; 26.2 asks its permission set. */
    public static boolean hasPermission(CommandSourceStack source, int level) {
        return source.permissions().hasPermission(EntityBridge.commandLevel(level));
    }
}
