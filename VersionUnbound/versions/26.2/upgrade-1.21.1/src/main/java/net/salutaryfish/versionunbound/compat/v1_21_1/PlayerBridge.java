package net.salutaryfish.versionunbound.compat.v1_21_1;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** 1.21.1's {@code Player} members (also called on {@code ServerPlayer} and {@code LocalPlayer}). */
public final class PlayerBridge {
    private PlayerBridge() {}

    /** {@code displayClientMessage(Component, boolean actionBar)}: 26.2 splits it into two methods. */
    public static void displayClientMessage(Player player, Component message, boolean actionBar) {
        if (actionBar) {
            player.sendOverlayMessage(message);
        } else {
            player.sendSystemMessage(message);
        }
    }
}
