package net.salutaryfish.versionunbound.testmod.events;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.EntityAccessor;
import org.jspecify.annotations.Nullable;

/**
 * Block placement on the server, in the air in a loaded chunk, with blocks placed and removed again in the same tick.
 * Items used on a block without a player ({@code ItemStack.useOn}): {@code UseItemOnBlockEvent} in its
 * {@code ITEM_AFTER_BLOCK} phase, then {@code BlockEvent.EntityPlaceEvent} with the snapshot of what the block replaced,
 * fired before the block's neighbor updates and its {@code onPlace} (a redstone lamp next to a placed redstone block
 * lights only after the event; a placed sand block schedules its tick only after it), canceling it, and
 * {@code EntityMultiPlaceEvent} for a bed; Fabric API's {@code ItemEvents.USE_ON} next to them (the fabricprobe testmod
 * refuses a named stack). NeoForge's members on {@code Level} that mods use directly, found by name as a mod's
 * bytecode finds them: {@code markAndNotifyBlock}, {@code captureBlockSnapshots} with {@code capturedBlockSnapshots},
 * and {@code restoringBlockSnapshots}. An enderman putting down the block it carries. With the player
 * ({@link #runWithPlayer}): placing through {@code ServerPlayerGameMode.useItemOn}, with both phases of
 * {@code UseItemOnBlockEvent}, the item use stat, the last item of a stack ({@code PlayerDestroyItemEvent}) and
 * canceling. The listeners only look at the scenario's own blocks and entities.
 */
final class PlacementScenario {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState LAMP = Blocks.REDSTONE_LAMP.defaultBlockState();
    private static final BlockState REDSTONE = Blocks.REDSTONE_BLOCK.defaultBlockState();
    // BlockItem places with UPDATE_ALL_IMMEDIATE; BlockSnapshot.create without flags records UPDATE_ALL.
    private static final int ITEM_FLAGS = Block.UPDATE_ALL_IMMEDIATE;
    private static final String FABRIC_REFUSES = "versionunbound_fabric_refuses_use_on";

    private final Checks checks;
    private final Checks playerChecks;
    private volatile BoundingBox area;
    private final List<Placement> placements = new CopyOnWriteArrayList<>();
    private final List<UseItemOnBlockEvent.UsePhase> phases = new CopyOnWriteArrayList<>();
    private final List<ItemEntity> drops = new CopyOnWriteArrayList<>();
    private final List<PlayerDestroyItemEvent> destroyedItems = new CopyOnWriteArrayList<>();
    private final Set<BlockPos> canceled = Collections.synchronizedSet(new HashSet<>());
    private volatile UseItemOnBlockEvent.UsePhase canceledPhase;
    // What the place listener sees of the block's neighbors while the event fires.
    private volatile BlockPos watchedLamp;
    private volatile Boolean lampLitDuringEvent;
    private volatile Boolean tickScheduledDuringEvent;

    PlacementScenario(Checks checks, Checks playerChecks) {
        this.checks = checks;
        this.playerChecks = playerChecks;
    }

    void place(BlockEvent.EntityPlaceEvent event) {
        BoundingBox box = area;
        if (box == null || !box.isInside(event.getPos())) return;
        LevelAccessor level = event.getLevel();
        boolean multi = event instanceof BlockEvent.EntityMultiPlaceEvent;
        List<BlockPos> positions = event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace
                ? multiPlace.getReplacedBlockSnapshots().stream().map(BlockSnapshot::getPos).toList()
                : List.of(event.getPos());
        BlockSnapshot snapshot = event.getBlockSnapshot();
        placements.add(new Placement(multi, event.getEntity(), event.getPos(), positions, snapshot.getState(), level.getBlockState(event.getPos()),
                event.getPlacedBlock(), event.getPlacedAgainst(), snapshot.getFlags()));
        BlockPos lamp = watchedLamp;
        if (lamp != null) lampLitDuringEvent = lit(level, lamp);
        if (level instanceof ServerLevel serverLevel) tickScheduledDuringEvent = serverLevel.getBlockTicks().hasScheduledTick(event.getPos(), Blocks.SAND);
        if (canceled.contains(event.getPos())) event.setCanceled(true);
    }

    void useItemOnBlock(UseItemOnBlockEvent event) {
        BoundingBox box = area;
        if (box == null || !box.isInside(event.getPos())) return;
        phases.add(event.getUsePhase());
        if (event.getUsePhase() == canceledPhase) event.cancelWithResult(InteractionResult.SUCCESS);
    }

    void joined(EntityJoinLevelEvent event) {
        BoundingBox box = area;
        if (box != null && event.getEntity() instanceof ItemEntity item && box.isInside(item.blockPosition())) drops.add(item);
    }

    void destroyItem(PlayerDestroyItemEvent event) {
        if (area != null) destroyedItems.add(event);
    }

    /** Runs around {@code center}, in the air in a loaded chunk. */
    void run(ServerLevel level, BlockPos center) {
        area = BoundingBox.fromCorners(center.offset(-8, -3, -8), center.offset(8, 3, 8));
        List<BlockPos> placed = new ArrayList<>();
        try {
            items(level, center, placed);
            levelMembers(level, center.offset(0, 0, 6), placed);
            enderman(level, center.offset(0, 0, -6), placed);
        } finally {
            area = null;
            // Dependent blocks first (a bed's halves on their support), without updates, so nothing drops.
            for (BlockPos pos : placed.reversed()) level.setBlock(pos, AIR, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            drops.forEach(Entity::discard);
            drops.clear();
            canceled.clear();
            clear();
        }
    }

    private void items(ServerLevel level, BlockPos center, List<BlockPos> placed) {
        // A block placed on top of another.
        BlockPos support = center;
        BlockPos target = support.above();
        place(level, support, STONE, placed);
        placed.add(target);
        ItemStack stones = new ItemStack(Items.STONE, 2);
        clear();
        InteractionResult result = use(level, stones, support);
        checks.check("an item used on a block fires UseItemOnBlockEvent (ITEM_AFTER_BLOCK), then EntityPlaceEvent once for the block it places",
                phases.equals(List.of(UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK)) && placements.size() == 1 && result.consumesAction()
                        && level.getBlockState(target).is(Blocks.STONE) && stones.getCount() == 1);
        // Without a player the event's placed block is the snapshot's state, here the air the stone replaced (NeoForge's constructor).
        checks.check("EntityPlaceEvent has the replaced block and setBlock's flags in its snapshot, the new block in the level, and the block it was placed against",
                placements, List.of(new Placement(false, null, target, List.of(target), AIR, STONE, AIR, STONE, ITEM_FLAGS)));

        // A redstone block next to a lamp: the lamp is still unlit while the event fires, and lit once the use is done.
        BlockPos powerSupport = center.offset(3, 0, 0);
        BlockPos power = powerSupport.above();
        BlockPos powerLamp = power.east();
        place(level, powerSupport, STONE, placed);
        place(level, powerLamp, LAMP, placed);
        placed.add(power);
        clear();
        watchedLamp = powerLamp;
        use(level, new ItemStack(Items.REDSTONE_BLOCK, 2), powerSupport);
        watchedLamp = null;
        checks.check("an item's block reaches its neighbors only after EntityPlaceEvent: a lamp next to a placed redstone block lights after the event, not during it",
                placements.size() == 1 && Boolean.FALSE.equals(lampLitDuringEvent) && lit(level, powerLamp));

        // Sand schedules its tick in onPlace, which waits for the event too.
        BlockPos sandSupport = center.offset(-3, 0, 0);
        BlockPos sand = sandSupport.above();
        place(level, sandSupport, STONE, placed);
        placed.add(sand);
        clear();
        use(level, new ItemStack(Items.SAND, 2), sandSupport);
        checks.check("an item's block runs its onPlace only after EntityPlaceEvent: placed sand schedules its tick after the event, not during it",
                placements.size() == 1 && Boolean.FALSE.equals(tickScheduledDuringEvent) && level.getBlockTicks().hasScheduledTick(sand, Blocks.SAND));

        // Canceled: the redstone block goes again before the lamp next to it ever sees it (a lit lamp takes 4 ticks to turn off).
        BlockPos refusedSupport = center.offset(0, 0, 3);
        BlockPos refused = refusedSupport.above();
        BlockPos refusedLamp = refused.east();
        place(level, refusedSupport, STONE, placed);
        place(level, refusedLamp, LAMP, placed);
        placed.add(refused);
        canceled.add(refused);
        ItemStack refusedBlocks = new ItemStack(Items.REDSTONE_BLOCK, 2);
        clear();
        InteractionResult refusedResult = use(level, refusedBlocks, refusedSupport);
        checks.check("canceling EntityPlaceEvent puts the replaced block back and the item too, fails the use, and the neighbors never see the block",
                placements.size() == 1 && refusedResult == InteractionResult.FAIL && level.getBlockState(refused).isAir() && refusedBlocks.getCount() == 2
                        && !lit(level, refusedLamp));

        // A bed places its foot, then (in setPlacedBy) its head, north of it without a player: one event for both. Beds
        // stack to 1, but a stack of 2 keeps the use from emptying it (PlayerDestroyItemEvent needs a player).
        BlockPos bedSupport = center.offset(-3, 0, 3);
        BlockPos foot = bedSupport.above();
        BlockPos head = foot.north();
        place(level, bedSupport, STONE, placed);
        placed.add(head);
        placed.add(foot);
        ItemStack beds = new ItemStack(Items.BED.white(), 2);
        clear();
        use(level, beds, bedSupport);
        checks.check("an item placing two blocks (a bed) fires EntityMultiPlaceEvent once, with both snapshots in the order they were placed",
                placements.size() == 1 && placements.getFirst().multi() && placements.getFirst().positions().equals(List.of(foot, head))
                        && level.getBlockState(foot).is(Blocks.BED.white()) && level.getBlockState(head).is(Blocks.BED.white()) && beds.getCount() == 1);

        BlockPos refusedBedSupport = center.offset(3, 0, 3);
        BlockPos refusedFoot = refusedBedSupport.above();
        BlockPos refusedHead = refusedFoot.north();
        place(level, refusedBedSupport, STONE, placed);
        placed.add(refusedHead);
        placed.add(refusedFoot);
        canceled.add(refusedFoot);
        ItemStack refusedBeds = new ItemStack(Items.BED.white(), 2);
        clear();
        InteractionResult refusedBedResult = use(level, refusedBeds, refusedBedSupport);
        checks.check("canceling EntityMultiPlaceEvent puts both blocks back",
                placements.size() == 1 && placements.getFirst().multi() && refusedBedResult == InteractionResult.FAIL && level.getBlockState(refusedFoot).isAir()
                        && level.getBlockState(refusedHead).isAir() && refusedBeds.getCount() == 2);

        BlockPos skippedSupport = center.offset(0, 0, -3);
        BlockPos skipped = skippedSupport.above();
        place(level, skippedSupport, STONE, placed);
        placed.add(skipped);
        ItemStack skippedStones = new ItemStack(Items.STONE, 2);
        clear();
        canceledPhase = UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK;
        InteractionResult skippedResult = use(level, skippedStones, skippedSupport);
        canceledPhase = null;
        checks.check("canceling UseItemOnBlockEvent returns its cancellation result without using the item",
                List.of(phases, skippedResult, placements, level.getBlockState(skipped), skippedStones.getCount()),
                List.of(List.of(UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK), InteractionResult.SUCCESS, List.of(), AIR, 2));

        // Fabric API's event wraps the same Item.useOn call as the placement hook; whichever is outside, it still decides.
        BlockPos fabricSupport = center.offset(3, 0, -3);
        BlockPos fabricTarget = fabricSupport.above();
        place(level, fabricSupport, STONE, placed);
        placed.add(fabricTarget);
        ItemStack named = new ItemStack(Items.STONE, 2);
        named.set(DataComponents.CUSTOM_NAME, Component.literal(FABRIC_REFUSES));
        clear();
        InteractionResult fabricResult = use(level, named, fabricSupport);
        checks.check("Fabric API's ItemEvents.USE_ON still decides an item's use on a block on the server (fabricprobe refuses this stack)",
                fabricResult == InteractionResult.FAIL && placements.isEmpty() && level.getBlockState(fabricTarget).isAir() && named.getCount() == 2);
    }

    private void levelMembers(ServerLevel level, BlockPos center, List<BlockPos> placed) {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        checks.check("Level.markAndNotifyBlock, which mods call, sends the updates for a block change made without them", () -> {
            MethodHandle markAndNotifyBlock = lookup.findVirtual(Level.class, "markAndNotifyBlock",
                    MethodType.methodType(void.class, BlockPos.class, LevelChunk.class, BlockState.class, BlockState.class, int.class, int.class));
            BlockPos power = center;
            BlockPos lamp = power.east();
            place(level, lamp, LAMP, placed);
            place(level, power, REDSTONE, placed);
            boolean litBefore = lit(level, lamp);
            markAndNotifyBlock.invoke(level, power, level.getChunkAt(power), AIR, REDSTONE, Block.UPDATE_ALL, 512);
            return !litBefore && lit(level, lamp);
        });

        checks.check("setting Level.captureBlockSnapshots, as mods do, records setBlock's changes in capturedBlockSnapshots and holds back their updates", () -> {
            MethodHandle setCapture = lookup.findSetter(Level.class, "captureBlockSnapshots", boolean.class);
            MethodHandle captured = lookup.findGetter(Level.class, "capturedBlockSnapshots", ArrayList.class);
            BlockPos power = center.offset(3, 0, 0);
            BlockPos lamp = power.east();
            place(level, lamp, LAMP, placed);
            placed.add(power);
            List<?> snapshots;
            setCapture.invoke(level, true);
            try {
                level.setBlock(power, REDSTONE, Block.UPDATE_ALL);
                snapshots = List.copyOf((List<?>) captured.invoke(level));
            } finally {
                setCapture.invoke(level, false);
                ((List<?>) captured.invoke(level)).clear();
            }
            return snapshots.size() == 1 && snapshots.getFirst() instanceof BlockSnapshot snapshot && snapshot.getPos().equals(power) && snapshot.getState().isAir()
                    && snapshot.getFlags() == Block.UPDATE_ALL && level.getBlockState(power).is(Blocks.REDSTONE_BLOCK) && !lit(level, lamp);
        });

        checks.check("while Level.restoringBlockSnapshots is set, as mods set it, blocks drop nothing", () -> {
            MethodHandle setRestoring = lookup.findSetter(Level.class, "restoringBlockSnapshots", boolean.class);
            BlockPos pos = center.offset(-3, 0, 0);
            drops.clear();
            setRestoring.invoke(level, true);
            try {
                Block.popResource(level, pos, new ItemStack(Items.STONE));
            } finally {
                setRestoring.invoke(level, false);
            }
            boolean droppedWhileRestoring = !drops.isEmpty();
            Block.popResource(level, pos, new ItemStack(Items.STONE));
            return !droppedWhileRestoring && drops.size() == 1;
        });
    }

    private void enderman(ServerLevel level, BlockPos below, List<BlockPos> placed) {
        BlockPos target = below.above();
        place(level, below, STONE, placed);
        placed.add(target);
        // NeoForge's snapshot is of the block the enderman puts its block on; without a player, that's also the placed block.
        EnderMan enderman = enderman(level, below);
        clear();
        goal(enderman, named("EndermanLeaveBlockGoal")).tick();
        checks.check("EntityPlaceEvent fires when an enderman puts down its block, with the enderman and, as in NeoForge, the block it puts it on",
                placements.equals(List.of(new Placement(false, enderman, below, List.of(below), STONE, STONE, STONE, AIR, Block.UPDATE_ALL)))
                        && level.getBlockState(target).is(Blocks.DIRT) && enderman.getCarriedBlock() == null);

        level.setBlock(target, AIR, Block.UPDATE_CLIENTS);
        EnderMan refusing = enderman(level, below);
        canceled.add(below);
        clear();
        goal(refusing, named("EndermanLeaveBlockGoal")).tick();
        checks.check("canceling the enderman's EntityPlaceEvent keeps the block in its hands",
                placements.size() == 1 && level.getBlockState(target).isAir() && refusing.getCarriedBlock() != null && refusing.getCarriedBlock().is(Blocks.DIRT));
    }

    /** Placing through the game mode, as the packet handler does, in survival. */
    void runWithPlayer(ServerPlayer player, ServerLevel level, BlockPos base) {
        BlockPos center = base.offset(0, 5, -6);
        area = BoundingBox.fromCorners(center.offset(-3, -1, -3), center.offset(3, 2, 3));
        GameType gameType = player.gameMode.getGameModeForPlayer();
        ItemStack held = player.getMainHandItem().copy();
        List<BlockPos> placed = new ArrayList<>();
        player.setGameMode(GameType.SURVIVAL);
        try {
            BlockPos support = center;
            BlockPos target = support.above();
            place(level, support, STONE, placed);
            placed.add(target);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 2));
            int used = player.getStats().getValue(Stats.ITEM_USED.get(Items.STONE));
            clear();
            InteractionResult result = useItemOn(player, level, support);
            // For a player the event's placed block is the block now in the level.
            playerChecks.check("a player placing a block fires UseItemOnBlockEvent for the block, then for the item, then EntityPlaceEvent with the player and the placed block",
                    phases.equals(List.of(UseItemOnBlockEvent.UsePhase.BLOCK, UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK))
                            && placements.equals(List.of(new Placement(false, player, target, List.of(target), AIR, STONE, STONE, STONE, ITEM_FLAGS)))
                            && result.consumesAction() && player.getMainHandItem().getCount() == 1);
            playerChecks.check("placing a block counts the item's use once", player.getStats().getValue(Stats.ITEM_USED.get(Items.STONE)) - used, 1);

            BlockPos refusedSupport = center.offset(2, 0, 0);
            BlockPos refused = refusedSupport.above();
            place(level, refusedSupport, STONE, placed);
            placed.add(refused);
            canceled.add(refused);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 2));
            clear();
            InteractionResult refusedResult = useItemOn(player, level, refusedSupport);
            playerChecks.check("canceling a player's EntityPlaceEvent puts the block back and gives the item back",
                    placements.size() == 1 && refusedResult == InteractionResult.FAIL && level.getBlockState(refused).isAir() && player.getMainHandItem().getCount() == 2);

            BlockPos lastSupport = center.offset(-2, 0, 0);
            BlockPos last = lastSupport.above();
            place(level, lastSupport, STONE, placed);
            placed.add(last);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 1));
            clear();
            useItemOn(player, level, lastSupport);
            playerChecks.check("placing the last item of a stack fires PlayerDestroyItemEvent with it",
                    destroyedItems.size() == 1 && destroyedItems.getFirst().getEntity() == player && destroyedItems.getFirst().getOriginal().is(Items.STONE)
                            && destroyedItems.getFirst().getHand() == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()
                            && level.getBlockState(last).is(Blocks.STONE));

            BlockPos blockedSupport = center.offset(0, 0, 2);
            BlockPos blocked = blockedSupport.above();
            place(level, blockedSupport, STONE, placed);
            placed.add(blocked);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 2));
            clear();
            canceledPhase = UseItemOnBlockEvent.UsePhase.BLOCK;
            InteractionResult blockedResult = useItemOn(player, level, blockedSupport);
            canceledPhase = null;
            playerChecks.check("canceling UseItemOnBlockEvent for the block returns its cancellation result, without using the item",
                    phases.equals(List.of(UseItemOnBlockEvent.UsePhase.BLOCK)) && blockedResult == InteractionResult.SUCCESS && placements.isEmpty()
                            && level.getBlockState(blocked).isAir() && player.getMainHandItem().getCount() == 2);
        } finally {
            area = null;
            canceledPhase = null;
            player.setGameMode(gameType);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            for (BlockPos pos : placed.reversed()) level.setBlock(pos, AIR, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            canceled.clear();
            clear();
        }
    }

    /** Uses the item on the top of {@code against} without a player. */
    private static InteractionResult use(ServerLevel level, ItemStack stack, BlockPos against) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(against), Direction.UP, against, false);
        // BlockPlaceContext's constructor without a player is protected in vanilla (public through NeoForge's AT).
        return stack.useOn(new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, stack, hit) {});
    }

    private static InteractionResult useItemOn(ServerPlayer player, ServerLevel level, BlockPos against) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(against), Direction.UP, against, false);
        return player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    /** An enderman carrying dirt in the middle of {@code below}'s top, not added to the level, whose rolls are all 0.5. */
    private static EnderMan enderman(ServerLevel level, BlockPos below) {
        EnderMan enderman = EntityTypes.ENDERMAN.create(level, EntitySpawnReason.COMMAND);
        enderman.setPos(Vec3.atBottomCenterOf(below));
        ((EntityAccessor) enderman).events$setRandom(new HalfRandom());
        enderman.setCarriedBlock(Blocks.DIRT.defaultBlockState());
        return enderman;
    }

    private void clear() {
        placements.clear();
        phases.clear();
        destroyedItems.clear();
        lampLitDuringEvent = null;
        tickScheduledDuringEvent = null;
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state, List<BlockPos> placed) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        if (!placed.contains(pos)) placed.add(pos);
    }

    private static boolean lit(LevelAccessor level, BlockPos lamp) {
        BlockState state = level.getBlockState(lamp);
        return state.is(Blocks.REDSTONE_LAMP) && state.getValue(RedstoneLampBlock.LIT);
    }

    private static Goal goal(EnderMan mob, Predicate<Goal> which) {
        List<Goal> goals = new ArrayList<>();
        for (WrappedGoal wrapped : mob.getGoalSelector().getAvailableGoals()) {
            if (which.test(wrapped.getGoal())) goals.add(wrapped.getGoal());
        }
        if (goals.size() != 1) throw new IllegalStateException("expected one matching goal on " + mob + ", found " + goals);
        return goals.getFirst();
    }

    private static Predicate<Goal> named(String simpleName) {
        return goal -> goal.getClass().getSimpleName().equals(simpleName);
    }

    /**
     * The enderman's three rolls for where to put its block ({@code x - 1 + 2r}, {@code y + 2r}, {@code z - 1 + 2r}):
     * at 0.5 that's the block above the one it stands in.
     */
    private static final class HalfRandom extends LegacyRandomSource {
        HalfRandom() {
            super(0L);
        }

        @Override
        public double nextDouble() {
            return 0.5;
        }
    }

    private record Placement(boolean multi, @Nullable Entity entity, BlockPos pos, List<BlockPos> positions, BlockState replaced, BlockState inLevel,
            BlockState placed, BlockState against, int flags) {}
}
