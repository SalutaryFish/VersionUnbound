package net.salutaryfish.versionunbound.testmod.events;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.InclusiveRange;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.vehicle.minecart.MinecartSpawner;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.MinecartSpawnerAccessor;
import net.salutaryfish.versionunbound.testmod.events.mixin.VillagerMakeLoveInvoker;
import org.slf4j.Logger;

/**
 * Mob spawning: {@code FinalizeSpawnEvent}, which fires where vanilla calls {@code finalizeSpawn} (NeoForge's coremod
 * rewrites those calls) and in the spawners, and {@code MobSpawnEvent.PositionCheck}. On the server:
 * {@code EntityType.spawn} (the event; the spawn type the mob records, saved and loaded; the spawn cancelled; the event
 * canceled), {@code EventHooks.finalizeMobSpawn} called directly (spawn data replaced, canceled), {@code /summon} (and
 * its spawn cancelled), {@code SpawnUtil.trySpawnMob} with each of PositionCheck's results, a trial spawner, the
 * spawners' owners, and villagers breeding (and their child's join canceled). With a player ({@link #runWithPlayer}): a
 * mob spawner ticked by hand, which only spawns with a player around. The listeners only look at events the scenario
 * causes, while it runs, in its level; what it spawns and places goes again in the same tick.
 */
final class SpawnScenario {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Consumer<FinalizeSpawnEvent> NOTHING = event -> {};

    // Members NeoForge adds to vanilla classes, which javac doesn't see here; findVirtual resolves them as a mod's
    // invokevirtual does. Null if one is missing, which fails the checks that use it.
    private static final MethodHandle GET_SPAWN_TYPE = find(() -> MethodHandles.publicLookup().findVirtual(Mob.class, "getSpawnType", MethodType.methodType(EntitySpawnReason.class)));
    private static final MethodHandle SET_SPAWN_CANCELLED = find(() -> MethodHandles.publicLookup().findVirtual(Mob.class, "setSpawnCancelled", MethodType.methodType(void.class, boolean.class)));
    private static final MethodHandle IS_ADDED_TO_LEVEL = find(() -> MethodHandles.publicLookup().findVirtual(Entity.class, "isAddedToLevel", MethodType.methodType(boolean.class)));
    private static final MethodHandle SPAWNER_OWNER = find(() -> MethodHandles.publicLookup().findVirtual(BaseSpawner.class, "getOwner", MethodType.methodType(Either.class)));
    private static final MethodHandle TRIAL_SPAWNER_OWNER = find(() -> MethodHandles.publicLookup().findVirtual(TrialSpawner.class, "getOwner", MethodType.methodType(Either.class)));

    private final Checks checks;
    private final Checks playerChecks;
    private final Map<Entity, Integer> joins;
    // The listeners record and act only while the scenario runs, in this level (chunk generation fires these events
    // too, in its own region).
    private boolean active;
    private ServerLevel level;
    private final List<FinalizeSpawnEvent> finalizeSpawns = new ArrayList<>();
    private final List<MobSpawnEvent.PositionCheck> positionChecks = new ArrayList<>();
    private Consumer<FinalizeSpawnEvent> onFinalizeSpawn = NOTHING;
    private MobSpawnEvent.PositionCheck.Result positionResult = MobSpawnEvent.PositionCheck.Result.DEFAULT;
    private boolean forcePlacement;
    private boolean denyVillagerJoins;
    private final List<Entity> spawned = new ArrayList<>();
    private final List<BlockPos> placed = new ArrayList<>();
    // Chunk generation's spawns (in the chunk's region, on worker threads), only counted: they depend on the biomes
    // and on chance.
    private final AtomicInteger generationFinalizeSpawns = new AtomicInteger();
    private final AtomicInteger generationPositionChecks = new AtomicInteger();

    SpawnScenario(Checks checks, Checks playerChecks, Map<Entity, Integer> joins) {
        this.checks = checks;
        this.playerChecks = playerChecks;
        this.joins = joins;
    }

    void finalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() == EntitySpawnReason.CHUNK_GENERATION) generationFinalizeSpawns.incrementAndGet();
        if (!active || event.getLevel() != level) return;
        finalizeSpawns.add(event);
        onFinalizeSpawn.accept(event);
    }

    void positionCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() == EntitySpawnReason.CHUNK_GENERATION) generationPositionChecks.incrementAndGet();
        if (!active || event.getLevel() != level) return;
        positionChecks.add(event);
        event.setResult(positionResult);
    }

    // Lets the trial spawner's mob spawn in the air.
    void placementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (active && forcePlacement && event.getLevel() == level) event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
    }

    void joined(EntityJoinLevelEvent event) {
        if (active && denyVillagerJoins && event.getEntity() instanceof Villager) event.setCanceled(true);
    }

    /** Runs around {@code center}, in the air in the middle of a loaded chunk. */
    void run(ServerLevel level, BlockPos center) {
        this.level = level;
        active = true;
        try {
            entityTypeSpawn(level, center);
            finalizeMobSpawn(level, center.offset(2, 0, 0));
            summon(level, center.offset(-2, 0, 0));
            positionCheck(level, center.offset(0, -4, 2));
            trialSpawner(level, center.below(8));
            owners(level, center);
            breeding(level, center.offset(0, 0, -3));
        } finally {
            finish();
        }
    }

    private void entityTypeSpawn(ServerLevel level, BlockPos pos) {
        Pig pig = EntityTypes.PIG.spawn(level, pos, EntitySpawnReason.SPAWN_ITEM_USE);
        FinalizeSpawnEvent event = eventFor(pig);
        checks.check("EntityType.spawn fires FinalizeSpawnEvent (a rewritten finalizeSpawn call) with the mob, its level, spawn type and position, and no spawner",
                pig != null && event != null && event.getLevel() == level && event.getSpawnType() == EntitySpawnReason.SPAWN_ITEM_USE && event.getSpawner() == null
                        && event.getDifficulty() != null && event.getX() == pig.getX() && event.getY() == pig.getY() && event.getZ() == pig.getZ());
        checks.check("finalizeSpawn records the spawn type on the mob", () -> spawnType(pig) == EntitySpawnReason.SPAWN_ITEM_USE);
        checks.check("the mob EntityType.spawn makes joins the level and is added to it", () -> joins.getOrDefault(pig, 0) == 1 && isAddedToLevel(pig));
        checks.check("a mob's spawn type is saved as neoforge:spawn_type and loaded again", () -> {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            pig.saveWithoutId(output);
            CompoundTag saved = output.buildResult();
            Pig loaded = EntityTypes.PIG.create(level, EntitySpawnReason.LOAD);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
            return saved.getStringOr("neoforge:spawn_type", "").equals("SPAWN_ITEM_USE") && spawnType(loaded) == EntitySpawnReason.SPAWN_ITEM_USE;
        });
        checks.check("Mob.setSpawnCancelled throws once the mob is in the level", () -> {
            try {
                SET_SPAWN_CANCELLED.invoke(pig, true);
                return false;
            } catch (UnsupportedOperationException e) {
                return true;
            }
        });
        pig.discard();
        checks.check("isAddedToLevel is false once the level stops tracking the mob", () -> !isAddedToLevel(pig));

        onFinalizeSpawn = cancel -> cancel.setSpawnCancelled(true);
        int before = finalizeSpawns.size();
        Pig cancelled = EntityTypes.PIG.spawn(level, pos, EntitySpawnReason.SPAWN_ITEM_USE);
        FinalizeSpawnEvent cancelledEvent = finalizeSpawns.size() > before ? finalizeSpawns.getLast() : null;
        checks.check("a spawn cancelled in FinalizeSpawnEvent: EntityType.spawn discards the mob and returns null, and the mob never joins the level",
                cancelled == null && cancelledEvent != null && cancelledEvent.isSpawnCancelled() && cancelledEvent.getEntity().isRemoved()
                        && joins.getOrDefault(cancelledEvent.getEntity(), 0) == 0);

        onFinalizeSpawn = cancel -> cancel.setCanceled(true);
        Pig unfinalized = EntityTypes.PIG.spawn(level, pos, EntitySpawnReason.SPAWN_ITEM_USE);
        onFinalizeSpawn = NOTHING;
        checks.check("canceling FinalizeSpawnEvent skips finalizeSpawn, and the mob still spawns",
                () -> unfinalized != null && eventFor(unfinalized) != null && spawnType(unfinalized) == null && joins.getOrDefault(unfinalized, 0) == 1);
    }

    private void finalizeMobSpawn(ServerLevel level, BlockPos pos) {
        Pig pig = pig(level, pos);
        DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
        AgeableMob.AgeableMobGroupData data = new AgeableMob.AgeableMobGroupData(false);
        onFinalizeSpawn = event -> event.setSpawnData(data);
        SpawnGroupData result = EventHooks.finalizeMobSpawn(pig, level, difficulty, EntitySpawnReason.EVENT, null);
        FinalizeSpawnEvent event = eventFor(pig);
        // An ageable mob's finalizeSpawn counts itself into the group data it gets, and returns it.
        checks.check("EventHooks.finalizeMobSpawn fires FinalizeSpawnEvent with the caller's difficulty, and finalizeSpawn gets the event's spawn data",
                event != null && event.getDifficulty() == difficulty && result == data && data.getGroupSize() == 1);

        Pig canceled = pig(level, pos);
        onFinalizeSpawn = cancel -> cancel.setCanceled(true);
        SpawnGroupData canceledResult = EventHooks.finalizeMobSpawn(canceled, level, difficulty, EntitySpawnReason.EVENT, new AgeableMob.AgeableMobGroupData(false));
        onFinalizeSpawn = NOTHING;
        checks.check("canceling FinalizeSpawnEvent makes EventHooks.finalizeMobSpawn return null without finalizing the mob",
                () -> eventFor(canceled) != null && canceledResult == null && spawnType(canceled) == null);
    }

    private void summon(ServerLevel level, BlockPos pos) {
        FinalizeSpawnEvent event = summonPig(level, pos);
        checks.check("/summon fires FinalizeSpawnEvent (a rewritten finalizeSpawn call), and the mob joins the level with its spawn type",
                () -> event != null && event.getSpawnType() == EntitySpawnReason.COMMAND && joins.getOrDefault(event.getEntity(), 0) == 1
                        && spawnType(event.getEntity()) == EntitySpawnReason.COMMAND);
        onFinalizeSpawn = cancel -> cancel.setSpawnCancelled(true);
        FinalizeSpawnEvent cancelled = summonPig(level, pos);
        onFinalizeSpawn = NOTHING;
        // NeoForge's own EntityJoinLevelEvent listener refuses it.
        checks.check("a spawn cancelled in FinalizeSpawnEvent keeps a summoned mob out of the level",
                cancelled != null && joins.getOrDefault(cancelled.getEntity(), 0) == 0 && level.getEntity(cancelled.getEntity().getUUID()) == null);
    }

    private FinalizeSpawnEvent summonPig(ServerLevel level, BlockPos pos) {
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level);
        int before = finalizeSpawns.size();
        level.getServer().getCommands().performPrefixedCommand(source, String.format(Locale.ROOT, "summon minecraft:pig %d %d %d", pos.getX(), pos.getY(), pos.getZ()));
        return finalizeSpawns.size() > before ? finalizeSpawns.getLast() : null;
    }

    // On a grass block, where vanilla's checks let a pig spawn, and in water on it, where they don't.
    private void positionCheck(ServerLevel level, BlockPos spot) {
        place(level, spot.below(), Blocks.GRASS_BLOCK);
        int before = positionChecks.size();
        Optional<Pig> allowed = trySpawnPig(level, spot, MobSpawnEvent.PositionCheck.Result.DEFAULT);
        MobSpawnEvent.PositionCheck event = positionChecks.size() > before ? positionChecks.getLast() : null;
        checks.check("SpawnUtil.trySpawnMob fires MobSpawnEvent.PositionCheck for the mob, with its level and spawn type and no spawner",
                allowed.isPresent() && event != null && event.getEntity() == allowed.get() && event.getLevel() == level
                        && event.getSpawnType() == EntitySpawnReason.MOB_SUMMONED && event.getSpawner() == null);
        checks.check("with PositionCheck's default result, the mob spawns where vanilla's checks allow it", allowed.isPresent() && joins.getOrDefault(allowed.get(), 0) == 1);
        before = positionChecks.size();
        checks.check("PositionCheck's FAIL stops a spawn that vanilla's checks allow",
                trySpawnPig(level, spot, MobSpawnEvent.PositionCheck.Result.FAIL).isEmpty() && positionChecks.size() == before + 1);
        place(level, spot, Blocks.WATER);
        before = positionChecks.size();
        checks.check("with PositionCheck's default result, no mob spawns where vanilla's checks refuse (in water)",
                trySpawnPig(level, spot, MobSpawnEvent.PositionCheck.Result.DEFAULT).isEmpty() && positionChecks.size() == before + 1);
        Optional<Pig> forced = trySpawnPig(level, spot, MobSpawnEvent.PositionCheck.Result.SUCCEED);
        checks.check("PositionCheck's SUCCEED spawns a mob where vanilla's checks refuse", forced.isPresent() && joins.getOrDefault(forced.get(), 0) == 1);
    }

    // One try, straight down onto the spot's block: the position is known.
    private Optional<Pig> trySpawnPig(ServerLevel level, BlockPos spot, MobSpawnEvent.PositionCheck.Result result) {
        positionResult = result;
        try {
            return SpawnUtil.trySpawnMob(EntityTypes.PIG, EntitySpawnReason.MOB_SUMMONED, level, spot, 1, 0, 1, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER, false);
        } finally {
            positionResult = MobSpawnEvent.PositionCheck.Result.DEFAULT;
        }
    }

    private void trialSpawner(ServerLevel level, BlockPos pos) {
        place(level, pos, Blocks.TRIAL_SPAWNER);
        TrialSpawnerBlockEntity trial = (TrialSpawnerBlockEntity) level.getBlockEntity(pos);
        trial.setEntityId(EntityTypes.PIG, level.getRandom());
        forcePlacement = true;
        Optional<UUID> spawnedId = Optional.empty();
        // A try gives up before making the mob when its random position is blocked (by the spawner itself).
        for (int i = 0; i < 10 && spawnedId.isEmpty(); i++) {
            spawnedId = trial.getTrialSpawner().spawnMob(level, pos);
        }
        forcePlacement = false;
        UUID id = spawnedId.orElse(null);
        FinalizeSpawnEvent event = finalizeSpawns.isEmpty() ? null : finalizeSpawns.getLast();
        checks.check("a trial spawner fires FinalizeSpawnEvent with its block entity as the spawner, and finalizes the mob",
                () -> id != null && event != null && event.getEntity().getUUID().equals(id) && event.getSpawnType() == EntitySpawnReason.TRIAL_SPAWNER
                        && Either.left(trial).equals(event.getSpawner()) && spawnType(event.getEntity()) == EntitySpawnReason.TRIAL_SPAWNER);
        checks.check("a trial spawner's owner is its block entity", () -> Either.left(trial).equals(TRIAL_SPAWNER_OWNER.invoke(trial.getTrialSpawner())));
    }

    private void owners(ServerLevel level, BlockPos pos) {
        SpawnerBlockEntity block = new SpawnerBlockEntity(pos, Blocks.SPAWNER.defaultBlockState());
        checks.check("a mob spawner's owner is its block entity", () -> Either.left(block).equals(SPAWNER_OWNER.invoke(block.getSpawner())));
        MinecartSpawner minecart = new MinecartSpawner(EntityTypes.SPAWNER_MINECART, level);
        checks.check("a spawner minecart's owner is the minecart",
                () -> Either.right(minecart).equals(SPAWNER_OWNER.invoke(((MinecartSpawnerAccessor) minecart).events$getSpawner())));
    }

    private void breeding(ServerLevel level, BlockPos pos) {
        VillagerMakeLoveInvoker behavior = (VillagerMakeLoveInvoker) new VillagerMakeLove();
        Optional<Villager> child = behavior.events$breed(level, villager(level, pos), villager(level, pos));
        child.ifPresent(spawned::add);
        checks.check("villagers breed when the level adds their child", () -> child.isPresent() && isAddedToLevel(child.get()));
        denyVillagerJoins = true;
        Optional<Villager> denied = behavior.events$breed(level, villager(level, pos), villager(level, pos));
        denyVillagerJoins = false;
        denied.ifPresent(spawned::add);
        checks.check("villagers don't breed when the level doesn't add their child (its EntityJoinLevelEvent canceled)", denied.isEmpty());
    }

    void logChunkGeneration() {
        LOGGER.info("[events] chunk generation's spawns: {} PositionCheck, {} FinalizeSpawnEvent", generationPositionChecks.get(), generationFinalizeSpawns.get());
    }

    /** The mob spawner, ticked by hand a few blocks above the player ({@code base}). */
    void runWithPlayer(ServerPlayer player, ServerLevel level, BlockPos base) {
        this.level = level;
        active = true;
        try {
            BlockPos pos = base.above(4);
            SpawnerBlockEntity block = new SpawnerBlockEntity(pos, Blocks.SPAWNER.defaultBlockState());
            BaseSpawner spawner = block.getSpawner();
            MobSpawnEvent.PositionCheck check = tickSpawner(level, spawner, pos, pigTag(), MobSpawnEvent.PositionCheck.Result.DEFAULT);
            FinalizeSpawnEvent event = check == null ? null : eventFor(check.getEntity());
            playerChecks.check("a mob spawner fires MobSpawnEvent.PositionCheck with itself as the spawner",
                    check != null && check.getSpawner() == spawner && check.getSpawnType() == EntitySpawnReason.SPAWNER);
            playerChecks.check("a mob spawner fires FinalizeSpawnEvent with its block entity as the spawner, finalizes the mob and spawns it",
                    () -> event != null && event.getSpawnType() == EntitySpawnReason.SPAWNER && Either.left(block).equals(event.getSpawner())
                            && spawnType(event.getEntity()) == EntitySpawnReason.SPAWNER && joins.getOrDefault(event.getEntity(), 0) == 1);
            // The spawner picks a random spot next to the last one, and a mob there obstructs the next spawn.
            if (check != null) check.getEntity().discard();

            // Spawn data with more than the id: vanilla doesn't finalize the mob, NeoForge still fires the event.
            CompoundTag configured = pigTag();
            configured.putBoolean("NoAI", true);
            MobSpawnEvent.PositionCheck configuredCheck = tickSpawner(level, spawner, pos, configured, MobSpawnEvent.PositionCheck.Result.DEFAULT);
            FinalizeSpawnEvent unfinalized = configuredCheck == null ? null : eventFor(configuredCheck.getEntity());
            playerChecks.check("a mob spawner fires FinalizeSpawnEvent for spawn data with more than an id, without finalizing the mob, as vanilla",
                    () -> unfinalized != null && spawnType(unfinalized.getEntity()) == null && joins.getOrDefault(unfinalized.getEntity(), 0) == 1);

            int events = finalizeSpawns.size();
            MobSpawnEvent.PositionCheck failed = tickSpawner(level, spawner, pos, pigTag(), MobSpawnEvent.PositionCheck.Result.FAIL);
            playerChecks.check("PositionCheck's FAIL stops a mob spawner's spawn",
                    failed != null && finalizeSpawns.size() == events && joins.getOrDefault(failed.getEntity(), 0) == 0);
        } finally {
            finish();
        }
    }

    // Ticks the spawner until it tries a position (the random one it picks may be blocked). Null if it never does.
    private MobSpawnEvent.PositionCheck tickSpawner(ServerLevel level, BaseSpawner spawner, BlockPos pos, CompoundTag entity, MobSpawnEvent.PositionCheck.Result result) {
        CompoundTag config = new CompoundTag();
        config.putShort("Delay", (short) 0);
        config.putInt("SpawnCount", 1);
        config.putInt("MaxNearbyEntities", 100);
        // Any player, wherever they are.
        config.putInt("RequiredPlayerRange", -1);
        config.putInt("SpawnRange", 1);
        // Custom spawn rules that any light meets: the spawner then skips the placement rules (there's no ground here).
        SpawnData spawnData = new SpawnData(entity, Optional.of(new SpawnData.CustomSpawnRules(new InclusiveRange<>(0, 15), new InclusiveRange<>(0, 15))), Optional.empty());
        config.put("SpawnData", SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, spawnData).getOrThrow());
        spawner.load(level, pos, TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), config));
        positionResult = result;
        int before = positionChecks.size();
        try {
            for (int i = 0; i < 10 && positionChecks.size() == before; i++) {
                spawner.serverTick(level, pos);
            }
        } finally {
            positionResult = MobSpawnEvent.PositionCheck.Result.DEFAULT;
        }
        return positionChecks.size() > before ? positionChecks.getLast() : null;
    }

    private static CompoundTag pigTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:pig");
        return tag;
    }

    private FinalizeSpawnEvent eventFor(Entity entity) {
        for (FinalizeSpawnEvent event : finalizeSpawns) {
            if (event.getEntity() == entity) return event;
        }
        return null;
    }

    private Pig pig(ServerLevel level, BlockPos pos) {
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.EVENT);
        pig.setPos(Vec3.atBottomCenterOf(pos));
        spawned.add(pig);
        return pig;
    }

    private Villager villager(ServerLevel level, BlockPos pos) {
        Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.BREEDING);
        villager.setPos(Vec3.atBottomCenterOf(pos));
        spawned.add(villager);
        return villager;
    }

    private void place(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), 3);
        placed.add(pos);
    }

    // Everything the scenario spawned, the mobs the events saw included, and the blocks it placed (water before the
    // grass under it).
    private void finish() {
        active = false;
        onFinalizeSpawn = NOTHING;
        positionResult = MobSpawnEvent.PositionCheck.Result.DEFAULT;
        forcePlacement = false;
        denyVillagerJoins = false;
        finalizeSpawns.forEach(event -> event.getEntity().discard());
        positionChecks.forEach(event -> event.getEntity().discard());
        spawned.forEach(Entity::discard);
        for (BlockPos pos : placed.reversed()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        finalizeSpawns.clear();
        positionChecks.clear();
        spawned.clear();
        placed.clear();
    }

    private static EntitySpawnReason spawnType(Mob mob) throws Throwable {
        return (EntitySpawnReason) GET_SPAWN_TYPE.invoke(mob);
    }

    private static boolean isAddedToLevel(Entity entity) throws Throwable {
        return (boolean) IS_ADDED_TO_LEVEL.invoke(entity);
    }

    private static MethodHandle find(Lookup lookup) {
        try {
            return lookup.find();
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[events] a member NeoForge adds to a vanilla class is missing", e);
            return null;
        }
    }

    @FunctionalInterface
    private interface Lookup {
        MethodHandle find() throws ReflectiveOperationException;
    }
}
