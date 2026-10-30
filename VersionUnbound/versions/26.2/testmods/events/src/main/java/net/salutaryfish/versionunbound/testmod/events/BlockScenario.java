package net.salutaryfish.versionunbound.testmod.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Block events on the server, in the air in a loaded chunk, with blocks placed and removed again in the same tick.
 * {@code BlockEvent.NeighborNotifyEvent} at each place it fires: a block change, a lever pull (vanilla's direct calls
 * of the server's three-argument {@code updateNeighborsAt}, the VersionUnbound fix in {@code ServerLevelMixin}), updates
 * that leave a side out, a repeater updating the block in front of it, and {@code EventHooks.onNeighborNotify} called
 * directly (as Quark and Toms Storage do). Redstone lamps show whether the neighbors were updated: a lamp lights as soon
 * as an update finds it powered, and blocks placed without updates leave it unlit. {@code BlockEvent.FarmlandTrampleEvent}
 * for mobs (not added to the level) falling on farmland, with the mob griefing check {@code EntityMobGriefingEvent}, a
 * mob's own {@code canTrample}, and the player in {@link #runWithPlayer}. The listeners only look at the scenario's own
 * blocks and entities.
 */
final class BlockScenario {
    private static final Set<Direction> ALL_SIDES = EnumSet.allOf(Direction.class);
    // A safe fall: it tramples (the roll is nextFloat() < fallDistance - 0.5) and does no damage.
    private static final double FALL = 3.0;

    private final Checks checks;
    private final Checks playerChecks;
    private volatile BoundingBox area;
    private final List<Notification> notifications = new CopyOnWriteArrayList<>();
    private final Set<BlockPos> canceledNotifications = Collections.synchronizedSet(new HashSet<>());
    private final List<Trample> tramples = new CopyOnWriteArrayList<>();
    private final List<Entity> griefing = new CopyOnWriteArrayList<>();
    private final Set<Entity> tracked = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Set<Entity> canceledTramples = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Set<Entity> deniedGriefing = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    BlockScenario(Checks checks, Checks playerChecks) {
        this.checks = checks;
        this.playerChecks = playerChecks;
    }

    void neighborNotify(BlockEvent.NeighborNotifyEvent event) {
        BoundingBox box = area;
        if (box == null || event.getLevel().isClientSide() || !box.isInside(event.getPos())) return;
        notifications.add(new Notification(event.getPos(), event.getState(), EnumSet.copyOf(event.getNotifiedSides()), event.getForceRedstoneUpdate()));
        if (canceledNotifications.contains(event.getPos())) event.setCanceled(true);
    }

    void farmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!tracked.contains(event.getEntity())) return;
        tramples.add(new Trample(event.getEntity(), event.getLevel(), event.getPos(), event.getState(), event.getFallDistance()));
        if (canceledTramples.contains(event.getEntity())) event.setCanceled(true);
    }

    void mobGriefing(EntityMobGriefingEvent event) {
        if (!tracked.contains(event.getEntity())) return;
        griefing.add(event.getEntity());
        if (deniedGriefing.contains(event.getEntity())) event.setCanGrief(false);
    }

    /** Runs around {@code center}, in the air in a loaded chunk. */
    void run(ServerLevel level, BlockPos center) {
        area = BoundingBox.fromCorners(center.offset(-6, -2, -6), center.offset(6, 2, 6));
        List<BlockPos> placed = new ArrayList<>();
        try {
            neighborNotify(level, center, placed);
            farmland(level, center.offset(0, 0, -4), placed);
        } finally {
            area = null;
            // Dependent blocks first (the lever on its stone), without updates, so nothing drops.
            for (BlockPos pos : placed.reversed()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            canceledNotifications.clear();
        }
    }

    private void neighborNotify(ServerLevel level, BlockPos center, List<BlockPos> placed) {
        // A block change: one event at the block, for its new state and all six sides.
        BlockPos stone = center;
        BlockState stoneState = Blocks.STONE.defaultBlockState();
        place(level, stone, stoneState, Block.UPDATE_ALL, placed);
        checks.check("NeighborNotifyEvent fires once when a block changes, with the new state and all six sides",
                at(stone), List.of(new Notification(stone, stoneState, ALL_SIDES, false)));

        // A lever pull: the change of the lever and its two direct calls, one for itself and one for the block it's on.
        BlockPos lever = stone.above();
        BlockState leverState = Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
        place(level, lever, leverState, Block.UPDATE_CLIENTS, placed);
        notifications.clear();
        ((LeverBlock) Blocks.LEVER).pull(leverState, level, lever, null);
        BlockState pulled = leverState.setValue(BlockStateProperties.POWERED, true);
        checks.check("NeighborNotifyEvent fires for the block a pulled lever is on (the server's three-argument updateNeighborsAt)",
                at(stone), List.of(new Notification(stone, stoneState, ALL_SIDES, false)));
        checks.check("NeighborNotifyEvent fires twice for a pulled lever itself: its change and its own update",
                at(lever), List.of(new Notification(lever, pulled, ALL_SIDES, false), new Notification(lever, pulled, ALL_SIDES, false)));

        // NeoForge ignores a canceled event for a block change: the lamp next to the new redstone block lights anyway.
        BlockPos power = center.offset(2, 0, 0);
        BlockPos powerLamp = power.east();
        place(level, powerLamp, Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_CLIENTS, placed);
        canceledNotifications.add(power);
        place(level, power, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL, placed);
        checks.check("canceling NeighborNotifyEvent for a block change doesn't stop the updates, as in NeoForge",
                at(power).size() == 1 && lit(level, powerLamp));

        // Updates that leave a side out: the other five sides; canceling skips the updates.
        BlockPos source = center.offset(0, 0, 3);
        BlockPos sourceLamp = source.east();
        BlockState sourceState = Blocks.REDSTONE_BLOCK.defaultBlockState();
        place(level, source, sourceState, Block.UPDATE_CLIENTS, placed);
        place(level, sourceLamp, Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_CLIENTS, placed);
        canceledNotifications.add(source);
        level.updateNeighborsAtExceptFromFacing(source, Blocks.REDSTONE_BLOCK, Direction.UP, null);
        boolean litWhenCanceled = lit(level, sourceLamp);
        canceledNotifications.remove(source);
        level.updateNeighborsAtExceptFromFacing(source, Blocks.REDSTONE_BLOCK, Direction.UP, null);
        Set<Direction> allButUp = EnumSet.complementOf(EnumSet.of(Direction.UP));
        checks.check("NeighborNotifyEvent fires for updates that leave a side out, with the other five sides",
                at(source), List.of(new Notification(source, sourceState, allButUp, false), new Notification(source, sourceState, allButUp, false)));
        checks.check("canceling NeighborNotifyEvent for updates that leave a side out skips them", !litWhenCanceled && lit(level, sourceLamp));

        // A powered repeater placed without updates: its onPlace updates the block in front of it (east, the side its
        // facing points away from), and canceling skips that.
        BlockPos repeater = center.offset(-3, 0, 0);
        BlockPos repeaterLamp = repeater.east();
        BlockState repeaterState = Blocks.REPEATER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)
                .setValue(BlockStateProperties.POWERED, true);
        place(level, repeaterLamp, Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_CLIENTS, placed);
        canceledNotifications.add(repeater);
        place(level, repeater, repeaterState, Block.UPDATE_CLIENTS, placed);
        litWhenCanceled = lit(level, repeaterLamp);
        canceledNotifications.remove(repeater);
        level.setBlock(repeater, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(repeater, repeaterState, Block.UPDATE_CLIENTS);
        Notification inFront = new Notification(repeater, repeaterState, EnumSet.of(Direction.EAST), false);
        checks.check("NeighborNotifyEvent fires when a repeater updates the block in front of it, with that side only",
                at(repeater), List.of(inFront, inFront));
        checks.check("canceling NeighborNotifyEvent for a repeater skips its update", !litWhenCanceled && lit(level, repeaterLamp));

        // The hook itself, which Quark's and Toms Storage's blocks call: it posts the event and returns it.
        BlockPos hooked = center.offset(0, 0, -2);
        canceledNotifications.add(hooked);
        BlockEvent.NeighborNotifyEvent event = EventHooks.onNeighborNotify(level, hooked, level.getBlockState(hooked), EnumSet.of(Direction.NORTH), false);
        checks.check("EventHooks.onNeighborNotify posts the event and returns it",
                event.isCanceled() && at(hooked).equals(List.of(new Notification(hooked, Blocks.AIR.defaultBlockState(), EnumSet.of(Direction.NORTH), false))));
    }

    private void farmland(ServerLevel level, BlockPos farmland, List<BlockPos> placed) {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        Zombie trampler = mob(level, EntityTypes.ZOMBIE, farmland);
        checks.check("FarmlandTrampleEvent fires when a mob tramples farmland, with the mob, its fall, and dirt as the new state",
                fall(level, farmland, trampler, FALL, placed).equals(List.of(new Trample(trampler, level, farmland, dirt, FALL))));
        checks.check("a mob that tramples farmland turns it to dirt", level.getBlockState(farmland).is(Blocks.DIRT));
        checks.check("trampling asks EntityMobGriefingEvent about a mob", griefing, List.of(trampler));

        Zombie canceled = mob(level, EntityTypes.ZOMBIE, farmland);
        canceledTramples.add(canceled);
        checks.check("canceling FarmlandTrampleEvent keeps the farmland",
                fall(level, farmland, canceled, FALL, placed).size() == 1 && level.getBlockState(farmland).is(Blocks.FARMLAND));

        Zombie denied = mob(level, EntityTypes.ZOMBIE, farmland);
        deniedGriefing.add(denied);
        checks.check("a mob EntityMobGriefingEvent doesn't let grief doesn't trample farmland",
                fall(level, farmland, denied, FALL, placed).isEmpty() && griefing.equals(List.of(denied)) && level.getBlockState(farmland).is(Blocks.FARMLAND));

        // The roll fails for a fall of half a block or less, before the griefing check.
        Zombie low = mob(level, EntityTypes.ZOMBIE, farmland);
        checks.check("a short fall doesn't trample farmland, without asking about griefing",
                fall(level, farmland, low, 0.5, placed).isEmpty() && griefing.isEmpty() && level.getBlockState(farmland).is(Blocks.FARMLAND));

        Chicken small = mob(level, EntityTypes.CHICKEN, farmland);
        checks.check("a mob too small to trample doesn't", fall(level, farmland, small, FALL, placed).isEmpty() && level.getBlockState(farmland).is(Blocks.FARMLAND));

        // NeoForge's IEntityExtension.canTrample, overridden as mods do: this zombie tramples even without falling. (No
        // @Override: this testmod compiles without NeoForge's extension interfaces on vanilla's classes. The name and
        // descriptor are what a mod's override has.)
        Zombie heavy = new Zombie(EntityTypes.ZOMBIE, level) {
            public boolean canTrample(ServerLevel level, BlockState state, BlockPos pos, double fallDistance) {
                return true;
            }
        };
        heavy.setPos(Vec3.atBottomCenterOf(farmland.above()));
        tracked.add(heavy);
        checks.check("a mob's own canTrample decides whether it tramples",
                fall(level, farmland, heavy, 0.0, placed).equals(List.of(new Trample(heavy, level, farmland, dirt, 0.0))) && griefing.isEmpty()
                        && level.getBlockState(farmland).is(Blocks.DIRT));
    }

    /** Players trample whatever the mob griefing checks say, without being asked about. */
    void runWithPlayer(ServerPlayer player, ServerLevel level, BlockPos base) {
        BlockPos farmland = base.offset(0, 0, 4);
        List<BlockPos> placed = new ArrayList<>();
        tracked.add(player);
        deniedGriefing.add(player);
        try {
            playerChecks.check("a player tramples farmland, and isn't asked about griefing",
                    fall(level, farmland, player, FALL, placed).equals(List.of(new Trample(player, level, farmland, Blocks.DIRT.defaultBlockState(), FALL)))
                            && griefing.isEmpty() && level.getBlockState(farmland).is(Blocks.DIRT));
        } finally {
            tracked.remove(player);
            deniedGriefing.remove(player);
            for (BlockPos pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** The entity falls on fresh farmland; returns the trample events it caused. */
    private List<Trample> fall(ServerLevel level, BlockPos farmland, Entity entity, double fallDistance, List<BlockPos> placed) {
        place(level, farmland, Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS, placed);
        tramples.clear();
        griefing.clear();
        BlockState state = level.getBlockState(farmland);
        state.getBlock().fallOn(level, state, farmland, entity, fallDistance);
        return List.copyOf(tramples);
    }

    private <T extends Mob> T mob(ServerLevel level, EntityType<T> type, BlockPos farmland) {
        T mob = type.create(level, EntitySpawnReason.COMMAND);
        mob.setPos(Vec3.atBottomCenterOf(farmland.above()));
        tracked.add(mob);
        return mob;
    }

    private static void place(ServerLevel level, BlockPos pos, BlockState state, int flags, List<BlockPos> placed) {
        level.setBlock(pos, state, flags);
        if (!placed.contains(pos)) placed.add(pos);
    }

    private static boolean lit(ServerLevel level, BlockPos lamp) {
        return level.getBlockState(lamp).getValue(RedstoneLampBlock.LIT);
    }

    private List<Notification> at(BlockPos pos) {
        return notifications.stream().filter(notification -> notification.pos().equals(pos)).toList();
    }

    private record Notification(BlockPos pos, BlockState state, Set<Direction> sides, boolean forceRedstoneUpdate) {}

    private record Trample(Entity entity, LevelAccessor level, BlockPos pos, BlockState state, double fallDistance) {}
}
