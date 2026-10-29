/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.event;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.BooleanSupplier;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// VersionUnbound: ported a feature at a time, like NeoForgeMod. The methods here are verbatim and in NeoForge's order;
// the others arrive with their events (player, interaction and block events, living and entity events, spawning, items,
// explosions, loot, chunks, ...). A mod calling a method that isn't here yet gets NoSuchMethodError.
public class EventHooks {
    public static int onItemUseStart(LivingEntity entity, ItemStack item, InteractionHand hand, int duration) {
        var event = new LivingEntityUseItemEvent.Start(entity, item, hand, duration);
        return NeoForge.EVENT_BUS.post(event).isCanceled() ? -1 : event.getDuration();
    }

    public static int onItemUseTick(LivingEntity entity, ItemStack item, int duration) {
        var event = new LivingEntityUseItemEvent.Tick(entity, item, duration);
        return NeoForge.EVENT_BUS.post(event).isCanceled() ? -1 : event.getDuration();
    }

    public static boolean onUseItemStop(LivingEntity entity, ItemStack item, int duration) {
        return NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Stop(entity, item, duration)).isCanceled();
    }

    public static ItemStack onItemUseFinish(LivingEntity entity, ItemStack item, int duration, ItemStack result) {
        LivingEntityUseItemEvent.Finish event = new LivingEntityUseItemEvent.Finish(entity, item, duration, result);
        NeoForge.EVENT_BUS.post(event);
        return event.getResultStack();
    }

    public static void onPlayerClone(Player player, Player oldPlayer, boolean wasDeath) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(player, oldPlayer, wasDeath));
    }

    public static boolean onCreateWorldSpawn(Level level, ServerLevelData settings) {
        return NeoForge.EVENT_BUS.post(new LevelEvent.CreateSpawnPosition(level, settings)).isCanceled();
    }

    public static void onCommandRegister(CommandDispatcher<CommandSourceStack> dispatcher, Commands.CommandSelection environment, CommandBuildContext context) {
        RegisterCommandsEvent event = new RegisterCommandsEvent(dispatcher, environment, context);
        NeoForge.EVENT_BUS.post(event);
    }

    public static void firePlayerChangedDimensionEvent(Player player, ResourceKey<Level> fromDim, ResourceKey<Level> toDim) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, fromDim, toDim));
    }

    public static void firePlayerLoggedIn(Player player) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
    }

    public static void firePlayerLoggedOut(Player player) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
    }

    /**
     * Called by {@link PlayerList#respawn(ServerPlayer, boolean)} before creating the new {@link ServerPlayer}
     * to fire the {@link PlayerRespawnPositionEvent}
     *
     * @param player          The old {@link ServerPlayer} that is being respawned
     * @param respawnLevel    The default level the player will respawn into
     * @param respawnAngle    The angle the player will face when they respawn
     * @param respawnPosition The position in the level the player will respawn at
     * @param fromEndFight    Whether the player is respawning because they jumped through the End return portal
     * @return The event
     */
    public static PlayerRespawnPositionEvent firePlayerRespawnPositionEvent(ServerPlayer player, TeleportTransition teleportTransition, boolean fromEndFight) {
        return NeoForge.EVENT_BUS.post(new PlayerRespawnPositionEvent(player, teleportTransition, fromEndFight));
    }

    /**
     * Called by {@link PlayerList#respawn(ServerPlayer, boolean)} after creating and initializing the new {@link ServerPlayer}.
     *
     * @param player       The new player instance created by the respawn process
     * @param fromEndFight Whether the player is respawning because they jumped through the End return portal
     */
    public static void firePlayerRespawnEvent(ServerPlayer player, boolean fromEndFight) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player, fromEndFight));
    }

    /**
     * Fires {@link EntityTickEvent.Pre}. Called from the head of {@link LivingEntity#tick()}.
     *
     * @param entity The entity being ticked
     * @return The event
     */
    public static EntityTickEvent.Pre fireEntityTickPre(Entity entity) {
        return NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(entity));
    }

    /**
     * Fires {@link EntityTickEvent.Post}. Called from the tail of {@link LivingEntity#tick()}.
     *
     * @param entity The entity being ticked
     */
    public static void fireEntityTickPost(Entity entity) {
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Post(entity));
    }

    /**
     * Fires {@link PlayerTickEvent.Pre}. Called from the head of {@link Player#tick()}.
     *
     * @param player The player being ticked
     */
    public static void firePlayerTickPre(Player player) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
    }

    /**
     * Fires {@link PlayerTickEvent.Post}. Called from the tail of {@link Player#tick()}.
     *
     * @param player The player being ticked
     */
    public static void firePlayerTickPost(Player player) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
    }

    /**
     * Fires {@link LevelTickEvent.Pre}. Called from {@link Minecraft#tick()} and {@link MinecraftServer#tickChildren(BooleanSupplier)} just before the try block for level tick is entered.
     *
     * @param level    The level being ticked
     * @param haveTime The time supplier, indicating if there is remaining time to do work in the current tick.
     */
    public static void fireLevelTickPre(Level level, BooleanSupplier haveTime) {
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Pre(haveTime, level));
    }

    /**
     * Fires {@link LevelTickEvent.Post}. Called from {@link Minecraft#tick()} and {@link MinecraftServer#tickChildren(BooleanSupplier)} just after the try block for level tick is exited.
     *
     * @param level    The level being ticked
     * @param haveTime The time supplier, indicating if there is remaining time to do work in the current tick.
     */
    public static void fireLevelTickPost(Level level, BooleanSupplier haveTime) {
        NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(haveTime, level));
    }

    /**
     * Fires {@link ServerTickEvent.Pre}. Called from the head of {@link MinecraftServer#tickServer(BooleanSupplier)}.
     *
     * @param haveTime The time supplier, indicating if there is remaining time to do work in the current tick.
     * @param server   The current server
     */
    public static void fireServerTickPre(BooleanSupplier haveTime, MinecraftServer server) {
        NeoForge.EVENT_BUS.post(new ServerTickEvent.Pre(haveTime, server));
    }

    /**
     * Fires {@link ServerTickEvent.Post}. Called from the tail of {@link MinecraftServer#tickServer(BooleanSupplier)}.
     *
     * @param haveTime The time supplier, indicating if there is remaining time to do work in the current tick.
     * @param server   The current server
     */
    public static void fireServerTickPost(BooleanSupplier haveTime, MinecraftServer server) {
        NeoForge.EVENT_BUS.post(new ServerTickEvent.Post(haveTime, server));
    }
}
