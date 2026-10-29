/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.salutaryfish.versionunbound.neoforge.patches.EntityDropsCapture;
import org.jspecify.annotations.Nullable;

// VersionUnbound: ported a feature at a time, like EventHooks. The methods here are verbatim (deviations are marked) and
// in NeoForge's order; the others arrive with their features. A mod calling a method that isn't here yet gets
// NoSuchMethodError.
public class CommonHooks {
    public static LivingChangeTargetEvent onLivingChangeTarget(LivingEntity entity, @Nullable LivingEntity originalTarget, LivingChangeTargetEvent.ILivingTargetType targetType) {
        LivingChangeTargetEvent event = new LivingChangeTargetEvent(entity, originalTarget, targetType);
        NeoForge.EVENT_BUS.post(event);

        return event;
    }

    public static boolean isEntityInvulnerableTo(Entity entity, DamageSource source, boolean isInvul) {
        return NeoForge.EVENT_BUS.post(new EntityInvulnerabilityCheckEvent(entity, source, isInvul)).isInvulnerable();
    }

    /**
     * Called after invulnerability checks in {@link LivingEntity#hurt(DamageSource, float)},
     * this method creates and posts the first event in the LivingEntity damage sequence,
     * {@link LivingIncomingDamageEvent}.
     *
     * @param entity    the entity to receive damage
     * @param container the newly instantiated container for damage to be dealt. Most properties of
     *                  the container will be empty at this stage.
     * @return if the event is cancelled or the entity was killed during the event. If true, further processing stops and no damage will be applied to the entity
     */
    public static boolean onEntityIncomingDamage(LivingEntity entity, DamageContainer container) {
        Preconditions.checkArgument(!entity.isDeadOrDying(), "The LivingIncomingDamageEvent cannot be fired with a dead entity.");
        var event = NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(entity, container));
        return event.isCanceled() || entity.isDeadOrDying();
    }

    public static LivingKnockBackEvent onLivingKnockBack(LivingEntity target, float strength, double ratioX, double ratioZ) {
        LivingKnockBackEvent event = new LivingKnockBackEvent(target, strength, ratioX, ratioZ);
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    /**
     * Creates and posts an {@link LivingDamageEvent.Pre}. This is invoked in
     * {@link LivingEntity#actuallyHurt(DamageSource, float)} and {@link Player#actuallyHurt(DamageSource, float)}
     * and requires access to the internal field {@link LivingEntity#damageContainers} as a parameter.
     *
     * @param entity    the entity to receive damage
     * @param container the container object holding the final values of the damage pipeline while they are still mutable
     * @return the current damage value to be applied to the entity's health
     *
     */
    public static float onLivingDamagePre(LivingEntity entity, DamageContainer container) {
        return NeoForge.EVENT_BUS.post(new LivingDamageEvent.Pre(entity, container)).getNewDamage();
    }

    /**
     * Creates and posts a {@link LivingDamageEvent.Post}. This is invoked in
     * {@link LivingEntity#actuallyHurt(DamageSource, float)} and {@link Player#actuallyHurt(DamageSource, float)}
     * and requires access to the internal field {@link LivingEntity#damageContainers} as a parameter.
     *
     * @param entity    the entity to receive damage
     * @param container the container object holding the truly final values of the damage pipeline. The values
     *                  of this container and used to instantiate final fields in the event.
     */
    public static void onLivingDamagePost(LivingEntity entity, DamageContainer container) {
        NeoForge.EVENT_BUS.post(new LivingDamageEvent.Post(entity, container));
    }

    public static boolean onLivingDeath(LivingEntity entity, DamageSource src) {
        return NeoForge.EVENT_BUS.post(new LivingDeathEvent(entity, src)).isCanceled();
    }

    public static boolean onLivingDrops(LivingEntity entity, DamageSource source, Collection<ItemEntity> drops, boolean recentlyHit) {
        return NeoForge.EVENT_BUS.post(new LivingDropsEvent(entity, source, drops, recentlyHit)).isCanceled();
    }

    @Nullable
    public static ItemEntity onPlayerTossEvent(Player player, ItemStack item, boolean dropAround, boolean includeName) {
        // VersionUnbound: captureDrops comes from EntityMixin through EntityDropsCapture until IEntityExtension is ported
        ((EntityDropsCapture) player).captureDrops(Lists.newArrayList());
        ItemEntity ret = player.drop(item, dropAround, includeName);
        ((EntityDropsCapture) player).captureDrops(null);

        if (ret == null)
            return null;

        ItemTossEvent event = new ItemTossEvent(ret, player);
        if (NeoForge.EVENT_BUS.post(event).isCanceled())
            return null;

        if (!player.level().isClientSide())
            player.level().addFreshEntity(event.getEntity());
        return event.getEntity();
    }

    /**
     * Fires {@link BlockEvent.BreakEvent}, pre-emptively canceling the event based on the conditions that will cause the block to not be broken anyway.
     * <p>
     * Note that undoing the pre-cancel will not permit breaking the block, since the vanilla conditions will always be checked.
     *
     * @param level    The level
     * @param gameType The game type of the breaking player
     * @param player   The breaking player
     * @param pos      The position of the block being broken
     * @param state    The state of the block being broken
     * @return The event
     */
    public static BreakBlockEvent fireBlockBreak(Level level, GameType gameType, Player player, BlockPos pos, BlockState state) {
        boolean preCancelEvent = false;

        ItemStack itemstack = player.getMainHandItem();
        if (!itemstack.isEmpty() && !itemstack.canDestroyBlock(state, level, pos, player)) {
            preCancelEvent = true;
        }

        if (player.blockActionRestricted(level, pos, gameType)) {
            preCancelEvent = true;
        }

        if (state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks()) {
            preCancelEvent = true;
        }

        var event = new BreakBlockEvent(level, pos, state, player);
        event.setCanceled(preCancelEvent);
        NeoForge.EVENT_BUS.post(event);

        // If the event is canceled on the server, let the client know the block still exists
        if (event.isCanceled() && event.shouldNotifyClient() && player instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundBlockUpdatePacket(pos, state));
        }

        return event;
    }

    public static boolean onPlayerAttackTarget(Player player, Entity target) {
        if (NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, target)).isCanceled())
            return false;
        ItemStack stack = player.getMainHandItem();
        // VersionUnbound: IItemExtension.onLeftClickEntity arrives with the extension interfaces (Phase 2 step 5); its
        // default returns false, which leaves the attack to vanilla.
        // return stack.isEmpty() || !stack.getItem().onLeftClickEntity(stack, player, target);
        return true;
    }

    public static boolean onTravelToDimension(Entity entity, ResourceKey<Level> dimension) {
        EntityTravelToDimensionEvent event = new EntityTravelToDimensionEvent(entity, dimension);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    @Nullable
    public static InteractionResult onInteractEntity(Player player, Entity entity, InteractionHand hand, Vec3 location) {
        PlayerInteractEvent.EntityInteract evt = new PlayerInteractEvent.EntityInteract(player, hand, entity, location);
        NeoForge.EVENT_BUS.post(evt);
        return evt.isCanceled() ? evt.getCancellationResult() : null;
    }

    @Nullable
    public static InteractionResult onItemRightClick(Player player, InteractionHand hand) {
        PlayerInteractEvent.RightClickItem evt = new PlayerInteractEvent.RightClickItem(player, hand);
        NeoForge.EVENT_BUS.post(evt);
        return evt.isCanceled() ? evt.getCancellationResult() : null;
    }

    public static PlayerInteractEvent.LeftClickBlock onLeftClickBlock(Player player, BlockPos pos, Direction face, ServerboundPlayerActionPacket.Action action) {
        PlayerInteractEvent.LeftClickBlock evt = new PlayerInteractEvent.LeftClickBlock(player, pos, face, PlayerInteractEvent.LeftClickBlock.Action.convert(action));
        NeoForge.EVENT_BUS.post(evt);
        return evt;
    }

    public static PlayerInteractEvent.LeftClickBlock onClientMineHold(Player player, BlockPos pos, Direction face) {
        PlayerInteractEvent.LeftClickBlock evt = new PlayerInteractEvent.LeftClickBlock(player, pos, face, PlayerInteractEvent.LeftClickBlock.Action.CLIENT_HOLD);
        NeoForge.EVENT_BUS.post(evt);
        return evt;
    }

    public static PlayerInteractEvent.RightClickBlock onRightClickBlock(Player player, InteractionHand hand, BlockPos pos, BlockHitResult hitVec) {
        PlayerInteractEvent.RightClickBlock evt = new PlayerInteractEvent.RightClickBlock(player, hand, pos, hitVec);
        NeoForge.EVENT_BUS.post(evt);
        return evt;
    }

    public static void onEmptyClick(Player player, InteractionHand hand) {
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickEmpty(player, hand));
    }

    public static void onEmptyLeftClick(Player player) {
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.LeftClickEmpty(player));
    }
}
