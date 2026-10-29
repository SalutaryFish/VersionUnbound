/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

// VersionUnbound: ported a feature at a time, like EventHooks; the methods here are verbatim and in NeoForge's order.
// Most of ClientHooks belongs to the client shims (Phase 5).
/**
 * Class for various client-side-only hooks.
 */
public class ClientHooks {
    public static void firePlayerLogin(MultiPlayerGameMode pc, LocalPlayer player, Connection networkManager) {
        NeoForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.LoggingIn(pc, player, networkManager));
    }

    public static void firePlayerLogout(@Nullable MultiPlayerGameMode pc, @Nullable LocalPlayer player) {
        NeoForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.LoggingOut(pc, player, player != null ? player.connection != null ? player.connection.getConnection() : null : null));
    }

    public static void firePlayerRespawn(MultiPlayerGameMode pc, LocalPlayer oldPlayer, LocalPlayer newPlayer, Connection networkManager) {
        NeoForge.EVENT_BUS.post(new ClientPlayerNetworkEvent.Clone(pc, oldPlayer, newPlayer, networkManager));
    }

    /**
     * Fires {@link ClientTickEvent.Pre}. Called from the head of {@link Minecraft#tick()}.
     */
    public static void fireClientTickPre() {
        NeoForge.EVENT_BUS.post(new ClientTickEvent.Pre());
    }

    /**
     * Fires {@link ClientTickEvent.Post}. Called from the tail of {@link Minecraft#tick()}.
     */
    public static void fireClientTickPost() {
        NeoForge.EVENT_BUS.post(new ClientTickEvent.Post());
    }
}
