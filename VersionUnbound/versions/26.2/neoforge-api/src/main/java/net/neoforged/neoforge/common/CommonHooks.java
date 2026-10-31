/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModLoader;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDrownEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.NoteBlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.salutaryfish.versionunbound.neoforge.patches.AttributeSupplierBuilderExtension;
import net.salutaryfish.versionunbound.neoforge.patches.EntityDropsCapture;
import net.salutaryfish.versionunbound.neoforge.patches.LevelBlockSnapshots;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TippedArrowItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

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

    /**
     * This is invoked in {@link LivingEntity#doHurtEquipment(DamageSource, float, EquipmentSlot...)}
     * and replaces the existing item hurt and break logic with an event-sensitive version.
     * <br>
     * Each armor slot is collected and passed into a {@link ArmorHurtEvent} and posted. If not cancelled,
     * the final durability loss values for each equipment item from the event will be applied.
     *
     * @param source        the damage source applied to the entity and armor
     * @param slots         an array of applicable slots for damage
     * @param damage        the durability damage individual items will receive
     * @param armoredEntity the entity wearing the armor
     */
    public static void onArmorHurt(DamageSource source, EquipmentSlot[] slots, float damage, LivingEntity armoredEntity) {
        EnumMap<EquipmentSlot, ArmorHurtEvent.ArmorEntry> armorMap = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : slots) {
            ItemStack armorPiece = armoredEntity.getItemBySlot(slot);
            if (armorPiece.isEmpty()) continue;
            Equippable equippable = armorPiece.get(DataComponents.EQUIPPABLE);
            float damageAfterFireResist = (equippable != null && equippable.damageOnHurt() && armorPiece.isDamageableItem() && armorPiece.canBeHurtBy(source)) ? damage : 0;
            armorMap.put(slot, new ArmorHurtEvent.ArmorEntry(armorPiece, damageAfterFireResist));
        }

        ArmorHurtEvent event = NeoForge.EVENT_BUS.post(new ArmorHurtEvent(armorMap, armoredEntity, source));
        if (event.isCanceled()) return;
        event.getArmorMap().forEach((slot, entry) -> entry.armorItemStack.hurtAndBreak((int) entry.newDamage, armoredEntity, slot));
    }

    public static boolean onLivingDeath(LivingEntity entity, DamageSource src) {
        return NeoForge.EVENT_BUS.post(new LivingDeathEvent(entity, src)).isCanceled();
    }

    public static boolean onLivingDrops(LivingEntity entity, DamageSource source, Collection<ItemEntity> drops, boolean recentlyHit) {
        return NeoForge.EVENT_BUS.post(new LivingDropsEvent(entity, source, drops, recentlyHit)).isCanceled();
    }

    public static Optional<BlockPos> isLivingOnLadder(BlockState state, Level level, BlockPos pos, LivingEntity entity) {
        boolean isSpectator = (entity instanceof Player && entity.isSpectator());
        if (isSpectator)
            return Optional.empty();
        // VersionUnbound: NeoForge asks NeoForgeServerConfig.INSTANCE.fullBoundingBoxLadders (false by default) until
        // NeoForge's configs are ported (Phase 2 step 6); its default stands in.
        if (!false) {
            return state.isLadder(level, pos, entity) ? Optional.of(pos) : Optional.empty();
        } else {
            AABB bb = entity.getBoundingBox();
            int mX = Mth.floor(bb.minX);
            int mY = Mth.floor(bb.minY);
            int mZ = Mth.floor(bb.minZ);
            for (int y2 = mY; y2 < bb.maxY; y2++) {
                for (int x2 = mX; x2 < bb.maxX; x2++) {
                    for (int z2 = mZ; z2 < bb.maxZ; z2++) {
                        BlockPos tmp = new BlockPos(x2, y2, z2);
                        state = level.getBlockState(tmp);
                        if (state.isLadder(level, tmp, entity)) {
                            return Optional.of(tmp);
                        }
                    }
                }
            }
            return Optional.empty();
        }
    }

    public static LivingFallEvent onLivingFall(LivingEntity entity, double distance, float damageMultiplier) {
        LivingFallEvent event = new LivingFallEvent(entity, distance, damageMultiplier);
        return NeoForge.EVENT_BUS.post(event);
    }

    public static double getEntityVisibilityMultiplier(LivingEntity entity, Entity lookingEntity, double originalMultiplier) {
        LivingEvent.LivingVisibilityEvent event = new LivingEvent.LivingVisibilityEvent(entity, lookingEntity, originalMultiplier);
        NeoForge.EVENT_BUS.post(event);
        return Math.max(0, event.getVisibilityModifier());
    }

    public static void onLivingJump(LivingEntity entity) {
        NeoForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(entity));
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

    public static InteractionResult onPlaceItemIntoWorld(UseOnContext context) {
        // VersionUnbound: NeoForge's body is in the overload below, which ItemStackMixin calls with vanilla's own
        // Item.useOn call in ItemStack.useOn (Fabric API wraps that call for its ItemEvents.USE_ON)
        return onPlaceItemIntoWorld(context, c -> c.getItemInHand().getItem().useOn(c));
    }

    // VersionUnbound: NeoForge's onPlaceItemIntoWorld, with the item's useOn passed in. The snapshot fields and
    // markAndNotifyBlock come from LevelMixin through LevelBlockSnapshots.
    public static InteractionResult onPlaceItemIntoWorld(UseOnContext context, Function<UseOnContext, InteractionResult> useOn) {
        ItemStack itemstack = context.getItemInHand();
        Level level = context.getLevel();
        LevelBlockSnapshots snapshots = (LevelBlockSnapshots) level;

        Player player = context.getPlayer();
        if (player != null && !player.getAbilities().mayBuild) {
            AdventureModePredicate adventureModePredicate = itemstack.get(DataComponents.CAN_PLACE_ON);
            if (adventureModePredicate == null || !adventureModePredicate.test(new BlockInWorld(level, context.getClickedPos(), false))) {
                return InteractionResult.PASS;
            }
        }

        // handle all placement events here
        Item item = itemstack.getItem();
        int size = itemstack.getCount();
        DataComponentMap components = itemstack.getComponents();

        if (!(itemstack.getItem() instanceof BucketItem)) // if not bucket
            snapshots.versionunbound$setCaptureBlockSnapshots(true);

        ItemStack copy = itemstack.copy();
        InteractionResult ret = useOn.apply(context);
        if (itemstack.isEmpty())
            EventHooks.onPlayerDestroyItem(player, copy, context.getHand());

        snapshots.versionunbound$setCaptureBlockSnapshots(false);

        if (ret.consumesAction()) {
            // save new item data
            int newSize = itemstack.getCount();
            DataComponentMap newComponents = itemstack.getComponents();
            @SuppressWarnings("unchecked")
            List<BlockSnapshot> blockSnapshots = (List<BlockSnapshot>) snapshots.versionunbound$capturedBlockSnapshots().clone();
            snapshots.versionunbound$capturedBlockSnapshots().clear();

            // make sure to set pre-placement item data for event
            itemstack.setCount(size);
            itemstack.applyComponents(components);
            //TODO: Set pre-placement item attachments?

            Direction side = context.getClickedFace();

            boolean eventResult = false;
            if (blockSnapshots.size() > 1) {
                eventResult = EventHooks.onMultiBlockPlace(player, blockSnapshots, side);
            } else if (blockSnapshots.size() == 1) {
                eventResult = EventHooks.onBlockPlace(player, blockSnapshots.get(0), side);
            }

            if (eventResult) {
                ret = InteractionResult.FAIL; // cancel placement
                // revert back all captured blocks
                for (BlockSnapshot blocksnapshot : Lists.reverse(blockSnapshots)) {
                    snapshots.versionunbound$setRestoringBlockSnapshots(true);
                    blocksnapshot.restore(blocksnapshot.getFlags() | Block.UPDATE_CLIENTS);
                    snapshots.versionunbound$setRestoringBlockSnapshots(false);
                }
                // inform the client that the item was not consumed
                if (player instanceof ServerPlayer serverPlayer) {
                    int slot = context.getHand() == InteractionHand.MAIN_HAND ? serverPlayer.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
                    serverPlayer.connection.send(serverPlayer.getInventory().createInventoryUpdatePacket(slot));
                }
            } else {
                // Change the stack to its new content
                itemstack.setCount(newSize);
                itemstack.applyComponents(newComponents);

                for (BlockSnapshot snap : blockSnapshots) {
                    int updateFlag = snap.getFlags();
                    BlockState oldBlock = snap.getState();
                    BlockState newBlock = level.getBlockState(snap.getPos());
                    newBlock.onPlace(level, snap.getPos(), oldBlock, false);

                    snapshots.markAndNotifyBlock(snap.getPos(), level.getChunkAt(snap.getPos()), oldBlock, newBlock, updateFlag, 512);
                }
                if (player != null)
                    player.awardStat(Stats.ITEM_USED.get(item));
            }
        }
        snapshots.versionunbound$capturedBlockSnapshots().clear();

        return ret;
    }

    private static final ThreadLocal<@Nullable Player> craftingPlayer = new ThreadLocal<>();

    public static void setCraftingPlayer(@Nullable Player player) {
        craftingPlayer.set(player);
    }

    @Nullable
    public static Player getCraftingPlayer() {
        return craftingPlayer.get();
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

    /**
     * Checks if a crop can grow by firing {@link CropGrowEvent.Pre}.
     *
     * @param level The level the crop is in
     * @param pos   The position of the crop
     * @param state The state of the crop
     * @param def   The result of the default checks performed by the crop.
     * @return true if the crop can grow
     */
    public static boolean canCropGrow(Level level, BlockPos pos, BlockState state, boolean def) {
        var ev = new CropGrowEvent.Pre(level, pos, state);
        NeoForge.EVENT_BUS.post(ev);
        return (ev.getResult() == CropGrowEvent.Pre.Result.GROW || (ev.getResult() == CropGrowEvent.Pre.Result.DEFAULT && def));
    }

    public static void fireCropGrowPost(Level level, BlockPos pos, BlockState state) {
        NeoForge.EVENT_BUS.post(new CropGrowEvent.Post(level, pos, state, level.getBlockState(pos)));
    }

    public static boolean onFarmlandTrample(ServerLevel level, BlockPos pos, BlockState state, double fallDistance, Entity entity) {
        if (entity.canTrample(level, state, pos, fallDistance)) {
            BlockEvent.FarmlandTrampleEvent event = new BlockEvent.FarmlandTrampleEvent(level, pos, state, fallDistance, entity);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        }
        return false;
    }

    public static int onNoteChange(Level level, BlockPos pos, BlockState state, int old, int _new) {
        NoteBlockEvent.Change event = new NoteBlockEvent.Change(level, pos, state, old, _new);
        if (NeoForge.EVENT_BUS.post(event).isCanceled())
            return -1;
        return event.getVanillaNoteId();
    }

    public static boolean canEntityDestroy(ServerLevel level, BlockPos pos, LivingEntity entity) {
        if (!level.isLoaded(pos))
            return false;
        BlockState state = level.getBlockState(pos);
        return EventHooks.canEntityGrief(level, entity) && state.canEntityDestroy(level, pos, entity) && EventHooks.onEntityDestroyBlock(entity, pos, state);
    }

    private static final Map<EntityType<? extends LivingEntity>, AttributeSupplier> FORGE_ATTRIBUTES = new HashMap<>();

    /** FOR INTERNAL USE ONLY, DO NOT CALL DIRECTLY */
    @Deprecated
    public static Map<EntityType<? extends LivingEntity>, AttributeSupplier> getAttributesView() {
        return Collections.unmodifiableMap(FORGE_ATTRIBUTES);
    }

    /** FOR INTERNAL USE ONLY, DO NOT CALL DIRECTLY */
    @Deprecated
    public static void modifyAttributes() {
        ModLoader.postEvent(new EntityAttributeCreationEvent(FORGE_ATTRIBUTES));
        Map<EntityType<? extends LivingEntity>, AttributeSupplier.Builder> finalMap = new HashMap<>();
        ModLoader.postEvent(new EntityAttributeModificationEvent(finalMap));

        finalMap.forEach((k, v) -> {
            AttributeSupplier supplier = DefaultAttributes.getSupplier(k);
            // VersionUnbound: instead of NeoForge's Builder(AttributeSupplier) constructor (see AttributeSupplierBuilderExtension).
            AttributeSupplier.Builder newBuilder = supplier != null ? AttributeSupplierBuilderExtension.copyOf(supplier) : new AttributeSupplier.Builder();
            newBuilder.combine(v);
            FORGE_ATTRIBUTES.put(k, newBuilder.build());
        });
    }

    public static LivingShieldBlockEvent onDamageBlock(LivingEntity blocker, DamageContainer container, float blockedDamage, boolean originalBlocked) {
        LivingShieldBlockEvent e = new LivingShieldBlockEvent(blocker, container, blockedDamage, originalBlocked);
        NeoForge.EVENT_BUS.post(e);
        return e;
    }

    // VersionUnbound: NeoForge asks the fluid types the entity's eyes are in (isEyeInFluidMatching, with the extensions'
    // canDrownInFluidType and canBeRiddenUnderFluidType), which arrive with fluid types. With vanilla's fluids their
    // defaults come down to vanilla's checks: water and lava are the fluids that aren't air, only water drowns (unless the
    // entity can breathe under water), and only water makes a vehicle that dismounts under water drop its rider.
    public static void onLivingBreathe(LivingEntity entity, ServerLevel level, int consumeAirAmount, int refillAirAmount) {
        // Check things that vanilla considers to be air - these will cause the air supply to be increased.
        boolean eyeInWater = entity.isEyeInFluid(FluidTags.WATER);
        boolean isAir = !(eyeInWater || entity.isEyeInFluid(FluidTags.LAVA)) || entity.level().getBlockState(BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ())).is(Blocks.BUBBLE_COLUMN);
        boolean canBreathe = isAir;
        // The following effects cause the entity to not drown, but do not cause the air supply to be increased.
        if (!isAir && (MobEffectUtil.hasWaterBreathing(entity) || !(eyeInWater && !entity.canBreatheUnderwater()) || (entity instanceof Player player && player.getAbilities().invulnerable))) {
            canBreathe = true;
            refillAirAmount = 0;
        }
        LivingBreatheEvent breatheEvent = new LivingBreatheEvent(entity, canBreathe, consumeAirAmount, refillAirAmount);
        NeoForge.EVENT_BUS.post(breatheEvent);
        if (breatheEvent.canBreathe()) {
            entity.setAirSupply(Math.min(entity.getAirSupply() + breatheEvent.getRefillAirAmount(), entity.getMaxAirSupply()));
        } else {
            entity.setAirSupply(entity.getAirSupply() - breatheEvent.getConsumeAirAmount());
        }

        if (entity.getAirSupply() <= 0) {
            LivingDrownEvent drownEvent = new LivingDrownEvent(entity);
            if (!NeoForge.EVENT_BUS.post(drownEvent).isCanceled() && drownEvent.isDrowning()) {
                entity.setAirSupply(0);
                Vec3 vec3 = entity.getDeltaMovement();

                for (int i = 0; i < drownEvent.getBubbleCount(); ++i) {
                    double d2 = entity.getRandom().nextDouble() - entity.getRandom().nextDouble();
                    double d3 = entity.getRandom().nextDouble() - entity.getRandom().nextDouble();
                    double d4 = entity.getRandom().nextDouble() - entity.getRandom().nextDouble();
                    // VersionUnbound: sent to the clients. NeoForge adds the particles to the server level, where they do
                    // nothing (vanilla's 26.2 air code runs on the server only), so none of the bubbles the event's
                    // bubble count promises appear. A count of 0 makes the client use the offsets as the particle's speed.
                    level.sendParticles(ParticleTypes.BUBBLE, entity.getX() + d2, entity.getY() + d3, entity.getZ() + d4, 0, vec3.x, vec3.y, vec3.z, 1.0);
                }

                if (drownEvent.getDamageAmount() > 0) {
                    entity.hurtServer(level, entity.damageSources().drown(), drownEvent.getDamageAmount());
                }
            }
        }

        if (!isAir && entity.isPassenger() && entity.getVehicle() != null && eyeInWater && entity.getVehicle().dismountsUnderwater()) {
            entity.stopRiding();
        }
    }

    /**
     * Checks if a mob effect can be applied to an entity by firing {@link MobEffectEvent.Applicable}.
     *
     * @param entity The target entity the mob effect is being applied to.
     * @param effect The mob effect being applied.
     * @param source The source entity who is applying the mob effect, or {@code null} if none exists.
     * @return True if the mob effect can be applied, otherwise false.
     */
    public static boolean canMobEffectBeApplied(LivingEntity entity, MobEffectInstance effect, @Nullable Entity source) {
        var event = new MobEffectEvent.Applicable(entity, effect, source);
        return NeoForge.EVENT_BUS.post(event).getApplicationResult();
    }

    /**
     * Used as the default implementation of {@link Item#getCreatorModId}. Call that method instead.
     */
    @Nullable
    public static String getDefaultCreatorModId(HolderLookup.Provider registries, ItemStack itemStack) {
        Item item = itemStack.getItem();
        Identifier registryName = BuiltInRegistries.ITEM.getKey(item);
        String modId = registryName == null ? null : registryName.getNamespace();
        if ("minecraft".equals(modId)) {
            if (itemStack.has(DataComponents.STORED_ENCHANTMENTS)) {
                Set<Holder<Enchantment>> enchantments = itemStack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).keySet();
                if (enchantments.size() == 1) {
                    Holder<Enchantment> enchantmentHolder = enchantments.iterator().next();
                    Optional<ResourceKey<Enchantment>> key = enchantmentHolder.unwrapKey();
                    if (key.isPresent()) {
                        return key.get().identifier().getNamespace();
                    }
                }
            } else if (item instanceof PotionItem || item instanceof TippedArrowItem) {
                PotionContents potionContents = itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
                Optional<Holder<Potion>> potionType = potionContents.potion();
                Optional<ResourceKey<Potion>> key = potionType.flatMap(Holder::unwrapKey);
                if (key.isPresent()) {
                    return key.get().identifier().getNamespace();
                }
            } else if (item instanceof SpawnEggItem spawnEggItem) {
                Optional<ResourceKey<EntityType<?>>> key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(spawnEggItem.getType(itemStack));
                if (key.isPresent()) {
                    return key.get().identifier().getNamespace();
                }
            }
        }
        return modId;
    }

    /**
     * Hook to fire {@link ItemAttributeModifierEvent}. Modders should use {@link ItemStack#forEachModifier(EquipmentSlot, BiConsumer)} instead.
     */
    public static ItemAttributeModifiers computeModifiedAttributes(ItemStack stack, ItemAttributeModifiers defaultModifiers) {
        ItemAttributeModifierEvent event = new ItemAttributeModifierEvent(stack, defaultModifiers);
        NeoForge.EVENT_BUS.post(event);
        return event.build();
    }
}
