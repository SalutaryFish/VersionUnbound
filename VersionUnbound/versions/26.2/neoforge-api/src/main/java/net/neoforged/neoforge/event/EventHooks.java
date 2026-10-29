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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IOwnedSpawner;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.ChunkTicketLevelUpdatedEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

// VersionUnbound: ported a feature at a time, like NeoForgeMod. The methods here are verbatim and in NeoForge's order;
// the others arrive with their events (player, interaction and block events, living and entity events, spawning, items,
// explosions, loot, chunks, ...). A mod calling a method that isn't here yet gets NoSuchMethodError.
public class EventHooks {
    public static void onPlayerDestroyItem(Player player, ItemStack stack, @Nullable InteractionHand hand) {
        NeoForge.EVENT_BUS.post(new PlayerDestroyItemEvent(player, stack, hand));
    }

    /**
     * Internal, should only be called via {@link SpawnPlacements#checkSpawnRules}.
     * 
     * @see SpawnPlacementCheck
     */
    @ApiStatus.Internal
    public static boolean checkSpawnPlacements(EntityType<?> entityType, ServerLevelAccessor level, EntitySpawnReason spawnType, BlockPos pos, RandomSource random, boolean defaultResult) {
        var event = new SpawnPlacementCheck(entityType, level, spawnType, pos, random, defaultResult);
        return NeoForge.EVENT_BUS.post(event).getPlacementCheckResult();
    }

    /**
     * Checks if the current position of the passed mob is valid for spawning, by firing {@link PositionCheck}.<br>
     * The default check is to perform the logical and of {@link Mob#checkSpawnRules} and {@link Mob#checkSpawnObstruction}.<br>
     * 
     * @param mob       The mob being spawned.
     * @param level     The level the mob will be added to, if successful.
     * @param spawnType The spawn type of the spawn.
     * @return True, if the position is valid, as determined by the contract of {@link PositionCheck}.
     * @see PositionCheck
     */
    public static boolean checkSpawnPosition(Mob mob, ServerLevelAccessor level, EntitySpawnReason spawnType) {
        var event = new PositionCheck(mob, level, spawnType, null);
        NeoForge.EVENT_BUS.post(event);
        if (event.getResult() == PositionCheck.Result.DEFAULT) {
            return mob.checkSpawnRules(level, spawnType) && mob.checkSpawnObstruction(level);
        }
        return event.getResult() == PositionCheck.Result.SUCCEED;
    }

    /**
     * Specialized variant of {@link #checkSpawnPosition} for spawners, as they have slightly different checks, and pass through the {@link BaseSpawner} to the event.
     * 
     * @see #checkSpawnPosition(Mob, ServerLevelAccessor, EntitySpawnReason)
     * @implNote See in-line comments about custom spawn rules.
     */
    public static boolean checkSpawnPositionSpawner(Mob mob, ServerLevelAccessor level, EntitySpawnReason spawnType, SpawnData spawnData, BaseSpawner spawner) {
        var event = new PositionCheck(mob, level, spawnType, spawner);
        NeoForge.EVENT_BUS.post(event);
        if (event.getResult() == PositionCheck.Result.DEFAULT) {
            // Spawners do not evaluate Mob#checkSpawnRules if any custom rules are present. This is despite the fact that these two methods do not check the same things.
            return (spawnData.getCustomSpawnRules().isPresent() || mob.checkSpawnRules(level, spawnType)) && mob.checkSpawnObstruction(level);
        }
        return event.getResult() == PositionCheck.Result.SUCCEED;
    }

    /**
     * Finalizes the spawn of a mob by firing the {@link FinalizeSpawnEvent} and calling {@link Mob#finalizeSpawn} with the result.
     * <p>
     * Mods should call this method in place of calling {@link Mob#finalizeSpawn}, unless calling super from within an override.
     * Vanilla calls to {@link Mob#finalizeSpawn} are replaced with calls to this method via coremod, so calls to this method will not show in an IDE.
     * <p>
     * When interfacing with this event, write all code as normal, and replace the call to {@link Mob#finalizeSpawn} with a call to this method.<p>
     * As an example, the following code block:
     * <code>
     * 
     * <pre>
     * var zombie = new Zombie(level);
     * zombie.finalizeSpawn(level, difficulty, spawnType, spawnData);
     * level.tryAddFreshEntityWithPassengers(zombie);
     * if (zombie.isAddedToLevel()) {
     *     // Do stuff with your new zombie
     * }
     * </pre>
     * 
     * </code>
     * Would become:
     * <code>
     * 
     * <pre>
     * var zombie = new Zombie(level);
     * EventHooks.finalizeMobSpawn(zombie, level, difficulty, spawnType, spawnData);
     * level.tryAddFreshEntityWithPassengers(zombie);
     * if (zombie.isAddedToLevel()) {
     *     // Do stuff with your new zombie
     * }
     * </pre>
     * 
     * </code>
     * The only code that changes is the {@link Mob#finalizeSpawn} call.
     * 
     * @param mob        The mob whose spawn is being finalized
     * @param level      The level the mob will be spawned in
     * @param difficulty The local difficulty at the position of the mob
     * @param spawnType  The type of spawn that is occuring
     * @param spawnData  Optional spawn data relevant to the mob being spawned
     * @return The SpawnGroupData from the finalize, or null if it was canceled. The return value of this method has no bearing on if the entity will be spawned
     * 
     * @see FinalizeSpawnEvent
     * @see Mob#finalizeSpawn(ServerLevelAccessor, DifficultyInstance, EntitySpawnReason, SpawnGroupData)
     * 
     * @apiNote Callers do not need to check if the entity's spawn was cancelled, as the spawn will be blocked by Forge.
     * 
     * @implNote Changes to the signature of this method must be reflected in the method redirector coremod.
     */
    @Nullable
    @SuppressWarnings("deprecation") // Call to deprecated Mob#finalizeSpawn is expected.
    public static SpawnGroupData finalizeMobSpawn(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnType, @Nullable SpawnGroupData spawnData) {
        var event = new FinalizeSpawnEvent(mob, level, mob.getX(), mob.getY(), mob.getZ(), difficulty, spawnType, spawnData, null);
        NeoForge.EVENT_BUS.post(event);

        if (!event.isCanceled()) {
            return mob.finalizeSpawn(level, event.getDifficulty(), event.getSpawnType(), event.getSpawnData());
        }

        return null;
    }

    /**
     * Finalizes the spawn of a mob by firing the {@link FinalizeSpawnEvent} and calling {@link Mob#finalizeSpawn} with the result.
     * <p>
     * This method is separate since mob spawners perform special finalizeSpawn handling when NBT data is present, but we still want to fire the event.
     * <p>
     * This overload is also the only way to pass through an {@link IOwnedSpawner} instance.
     * 
     * @param mob        The mob whose spawn is being finalized
     * @param level      The level the mob will be spawned in
     * @param difficulty The local difficulty at the position of the mob
     * @param spawnType  The type of spawn that is occuring
     * @param spawnData  Optional spawn data relevant to the mob being spawned
     * @param spawner    The spawner that is attempting to spawn the mob
     * @param def        If the spawner would normally call finalizeSpawn, regardless of the event
     */
    @SuppressWarnings("deprecation") // Call to deprecated Mob#finalizeSpawn is expected.
    public static FinalizeSpawnEvent finalizeMobSpawnSpawner(Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnType, @Nullable SpawnGroupData spawnData, IOwnedSpawner spawner, boolean def) {
        var event = new FinalizeSpawnEvent(mob, level, mob.getX(), mob.getY(), mob.getZ(), difficulty, spawnType, spawnData, spawner.getOwner());
        NeoForge.EVENT_BUS.post(event);

        if (!event.isCanceled() && def) {
            // Spawners only call finalizeSpawn under certain conditions, which are passed through as def.
            // Spawners also do not propagate the SpawnGroupData between spawns, so we ignore the result of Mob#finalizeSpawn
            mob.finalizeSpawn(level, event.getDifficulty(), event.getSpawnType(), event.getSpawnData());
        }

        return event;
    }

    public static int getExperienceDrop(LivingEntity entity, @Nullable Player attackingPlayer, int originalExperience) {
        LivingExperienceDropEvent event = new LivingExperienceDropEvent(entity, attackingPlayer, originalExperience);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            return 0;
        }
        return event.getDroppedExperience();
    }

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

    public static int onItemExpire(ItemEntity entity) {
        ItemExpireEvent event = new ItemExpireEvent(entity);
        NeoForge.EVENT_BUS.post(event);
        return event.getExtraLife();
    }

    /**
     * Called in {@link ItemEntity#playerTouch(Player)} before any other processing occurs.
     * <p>
     * Fires {@link ItemEntityPickupEvent.Pre} and returns the event.
     * 
     * @param itemEntity The item entity that a player collided with
     * @param player     The player that collided with the item entity
     */
    public static ItemEntityPickupEvent.Pre fireItemPickupPre(ItemEntity itemEntity, Player player) {
        return NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(player, itemEntity));
    }

    /**
     * Called in {@link ItemEntity#playerTouch(Player)} after an item was successfully picked up.
     * <p>
     * Fires {@link ItemEntityPickupEvent.Post}.
     * 
     * @param itemEntity The item entity that a player collided with
     * @param player     The player that collided with the item entity
     * @param copy       A copy of the item entity's item stack before the pickup
     */
    public static void fireItemPickupPost(ItemEntity itemEntity, Player player, ItemStack copy) {
        NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Post(player, itemEntity, copy));
    }

    public static boolean canMountEntity(Entity entityMounting, Entity entityBeingMounted, boolean isMounting) {
        boolean isCanceled = NeoForge.EVENT_BUS.post(new EntityMountEvent(entityMounting, entityBeingMounted, entityMounting.level(), isMounting)).isCanceled();

        if (isCanceled) {
            entityMounting.absSnapTo(entityMounting.getX(), entityMounting.getY(), entityMounting.getZ(), entityMounting.yRotO, entityMounting.xRotO);
            return false;
        } else
            return true;
    }

    public static boolean onAnimalTame(Animal animal, Player tamer) {
        return NeoForge.EVENT_BUS.post(new AnimalTameEvent(animal, tamer)).isCanceled();
    }

    public static void onPlayerClone(Player player, Player oldPlayer, boolean wasDeath) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(player, oldPlayer, wasDeath));
    }

    public static boolean onCreateWorldSpawn(Level level, ServerLevelData settings) {
        return NeoForge.EVENT_BUS.post(new LevelEvent.CreateSpawnPosition(level, settings)).isCanceled();
    }

    public static float onLivingHeal(LivingEntity entity, float amount) {
        LivingHealEvent event = new LivingHealEvent(entity, amount);
        return (NeoForge.EVENT_BUS.post(event).isCanceled() ? 0 : event.getAmount());
    }

    public static boolean onProjectileImpact(Projectile projectile, HitResult ray) {
        return NeoForge.EVENT_BUS.post(new ProjectileImpactEvent(projectile, ray)).isCanceled();
    }

    public static void fireChunkTicketLevelUpdated(ServerLevel level, long chunkPos, int oldTicketLevel, int newTicketLevel, @Nullable ChunkHolder chunkHolder) {
        if (oldTicketLevel != newTicketLevel)
            NeoForge.EVENT_BUS.post(new ChunkTicketLevelUpdatedEvent(level, chunkPos, oldTicketLevel, newTicketLevel, chunkHolder));
    }

    public static void fireChunkWatch(ServerPlayer entity, LevelChunk chunk, ServerLevel level) {
        NeoForge.EVENT_BUS.post(new ChunkWatchEvent.Watch(entity, chunk, level));
    }

    public static void fireChunkSent(ServerPlayer entity, LevelChunk chunk, ServerLevel level) {
        NeoForge.EVENT_BUS.post(new ChunkWatchEvent.Sent(entity, chunk, level));
    }

    public static void fireChunkUnWatch(ServerPlayer entity, ChunkPos chunkpos, ServerLevel level) {
        NeoForge.EVENT_BUS.post(new ChunkWatchEvent.UnWatch(entity, chunkpos, level));
    }

    public static void onCommandRegister(CommandDispatcher<CommandSourceStack> dispatcher, Commands.CommandSelection environment, CommandBuildContext context) {
        RegisterCommandsEvent event = new RegisterCommandsEvent(dispatcher, environment, context);
        NeoForge.EVENT_BUS.post(event);
    }

    public static EntityTeleportEvent.TeleportCommand onEntityTeleportCommand(Entity entity, ServerLevel targetLevel, double targetX, double targetY, double targetZ) {
        EntityTeleportEvent.TeleportCommand event = new EntityTeleportEvent.TeleportCommand(entity, targetLevel, targetX, targetY, targetZ);
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    public static EntityTeleportEvent.SpreadPlayersCommand onEntityTeleportSpreadPlayersCommand(Entity entity, ServerLevel targetLevel, double targetX, double targetY, double targetZ) {
        EntityTeleportEvent.SpreadPlayersCommand event = new EntityTeleportEvent.SpreadPlayersCommand(entity, targetLevel, targetX, targetY, targetZ);
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    public static EntityTeleportEvent.EnderEntity onEnderTeleport(LivingEntity entity, double targetX, double targetY, double targetZ) {
        EntityTeleportEvent.EnderEntity event = new EntityTeleportEvent.EnderEntity(entity, targetX, targetY, targetZ);
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    @ApiStatus.Internal
    public static EntityTeleportEvent.EnderPearl onEnderPearlLand(ServerPlayer entity, double targetX, double targetY, double targetZ, ThrownEnderpearl pearlEntity, float attackDamage, HitResult hitResult) {
        EntityTeleportEvent.EnderPearl event = new EntityTeleportEvent.EnderPearl(entity, targetX, targetY, targetZ, pearlEntity, attackDamage, hitResult);
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    public static EntityTeleportEvent.ItemConsumption onItemConsumptionTeleport(LivingEntity entity, ItemStack itemStack, double targetX, double targetY, double targetZ) {
        EntityTeleportEvent.ItemConsumption event = new EntityTeleportEvent.ItemConsumption(entity, itemStack, targetX, targetY, targetZ);
        NeoForge.EVENT_BUS.post(event);
        return event;
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
