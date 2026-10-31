package net.minecraft.world.level.block.entity.trialspawner;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public class TrialSpawnerData {
    public static final String TAG_SPAWN_DATA = "spawn_data";
    private static final String TAG_NEXT_MOB_SPAWNS_AT = "next_mob_spawns_at";
    private static final int DELAY_BETWEEN_PLAYER_SCANS = 20;
    private static final int TRIAL_OMEN_PER_BAD_OMEN_LEVEL = 18000;
    public static MapCodec<TrialSpawnerData> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                UUIDUtil.CODEC_SET
                    .lenientOptionalFieldOf("registered_players", Sets.newHashSet())
                    .forGetter(trialSpawnerData -> trialSpawnerData.detectedPlayers),
                UUIDUtil.CODEC_SET.lenientOptionalFieldOf("current_mobs", Sets.newHashSet()).forGetter(trialSpawnerData -> trialSpawnerData.currentMobs),
                Codec.LONG.lenientOptionalFieldOf("cooldown_ends_at", 0L).forGetter(trialSpawnerData -> trialSpawnerData.cooldownEndsAt),
                Codec.LONG.lenientOptionalFieldOf("next_mob_spawns_at", 0L).forGetter(trialSpawnerData -> trialSpawnerData.nextMobSpawnsAt),
                Codec.intRange(0, Integer.MAX_VALUE)
                    .lenientOptionalFieldOf("total_mobs_spawned", 0)
                    .forGetter(trialSpawnerData -> trialSpawnerData.totalMobsSpawned),
                SpawnData.CODEC.lenientOptionalFieldOf("spawn_data").forGetter(trialSpawnerData -> trialSpawnerData.nextSpawnData),
                ResourceKey.codec(Registries.LOOT_TABLE)
                    .lenientOptionalFieldOf("ejecting_loot_table")
                    .forGetter(trialSpawnerData -> trialSpawnerData.ejectingLootTable)
            )
            .apply(instance, TrialSpawnerData::new)
    );
    protected final Set<UUID> detectedPlayers = new HashSet<>();
    protected final Set<UUID> currentMobs = new HashSet<>();
    protected long cooldownEndsAt;
    protected long nextMobSpawnsAt;
    protected int totalMobsSpawned;
    protected Optional<SpawnData> nextSpawnData;
    protected Optional<ResourceKey<LootTable>> ejectingLootTable;
    @Nullable
    protected Entity displayEntity;
    @Nullable
    private SimpleWeightedRandomList<ItemStack> dispensing;
    protected double spin;
    protected double oSpin;

    public TrialSpawnerData() {
        this(Collections.emptySet(), Collections.emptySet(), 0L, 0L, 0, Optional.empty(), Optional.empty());
    }

    public TrialSpawnerData(
        Set<UUID> detectedPlayers,
        Set<UUID> currentMobs,
        long cooldownEndsAt,
        long nextMobSpawnsAt,
        int totalMobsSpawned,
        Optional<SpawnData> nextSpawnData,
        Optional<ResourceKey<LootTable>> ejectingLootTable
    ) {
        this.detectedPlayers.addAll(detectedPlayers);
        this.currentMobs.addAll(currentMobs);
        this.cooldownEndsAt = cooldownEndsAt;
        this.nextMobSpawnsAt = nextMobSpawnsAt;
        this.totalMobsSpawned = totalMobsSpawned;
        this.nextSpawnData = nextSpawnData;
        this.ejectingLootTable = ejectingLootTable;
    }

    public void reset() {
        this.detectedPlayers.clear();
        this.totalMobsSpawned = 0;
        this.nextMobSpawnsAt = 0L;
        this.cooldownEndsAt = 0L;
        this.currentMobs.clear();
        this.nextSpawnData = Optional.empty();
    }

    public boolean hasMobToSpawn(TrialSpawner trialSpawner, RandomSource random) {
        boolean bl = this.getOrCreateNextSpawnData(trialSpawner, random).getEntityToSpawn().contains("id", 8);
        return bl || !trialSpawner.getConfig().spawnPotentialsDefinition().isEmpty();
    }

    public boolean hasFinishedSpawningAllMobs(TrialSpawnerConfig config, int players) {
        return this.totalMobsSpawned >= config.calculateTargetTotalMobs(players);
    }

    public boolean haveAllCurrentMobsDied() {
        return this.currentMobs.isEmpty();
    }

    public boolean isReadyToSpawnNextMob(ServerLevel level, TrialSpawnerConfig config, int players) {
        return level.getGameTime() >= this.nextMobSpawnsAt && this.currentMobs.size() < config.calculateTargetSimultaneousMobs(players);
    }

    public int countAdditionalPlayers(BlockPos pos) {
        if (this.detectedPlayers.isEmpty()) {
            Util.logAndPauseIfInIde("Trial Spawner at " + pos + " has no detected players");
        }

        return Math.max(0, this.detectedPlayers.size() - 1);
    }

    public void tryDetectPlayers(ServerLevel level, BlockPos pos, TrialSpawner spawner) {
        boolean bl = (pos.asLong() + level.getGameTime()) % 20L != 0L;
        if (!bl) {
            if (!spawner.getState().equals(TrialSpawnerState.COOLDOWN) || !spawner.isOminous()) {
                List<UUID> list = spawner.getPlayerDetector().detect(level, spawner.getEntitySelector(), pos, spawner.getRequiredPlayerRange(), true);
                boolean bl2;
                if (!spawner.isOminous() && !list.isEmpty()) {
                    Optional<Pair<Player, Holder<MobEffect>>> optional = findPlayerWithOminousEffect(level, list);
                    optional.ifPresent(pair -> {
                        Player player = pair.getFirst();
                        if (pair.getSecond() == MobEffects.BAD_OMEN) {
                            transformBadOmenIntoTrialOmen(player);
                        }

                        level.levelEvent(3020, BlockPos.containing(player.getEyePosition()), 0);
                        spawner.applyOminous(level, pos);
                    });
                    bl2 = optional.isPresent();
                } else {
                    bl2 = false;
                }

                if (!spawner.getState().equals(TrialSpawnerState.COOLDOWN) || bl2) {
                    boolean bl3 = spawner.getData().detectedPlayers.isEmpty();
                    List<UUID> list2 = bl3
                        ? list
                        : spawner.getPlayerDetector().detect(level, spawner.getEntitySelector(), pos, spawner.getRequiredPlayerRange(), false);
                    if (this.detectedPlayers.addAll(list2)) {
                        this.nextMobSpawnsAt = Math.max(level.getGameTime() + 40L, this.nextMobSpawnsAt);
                        if (!bl2) {
                            int i = spawner.isOminous() ? 3019 : 3013;
                            level.levelEvent(i, pos, this.detectedPlayers.size());
                        }
                    }
                }
            }
        }
    }

    private static Optional<Pair<Player, Holder<MobEffect>>> findPlayerWithOminousEffect(ServerLevel level, List<UUID> players) {
        Player player = null;

        for (UUID uUID : players) {
            Player player2 = level.getPlayerByUUID(uUID);
            if (player2 != null) {
                Holder<MobEffect> holder = MobEffects.TRIAL_OMEN;
                if (player2.hasEffect(holder)) {
                    return Optional.of(Pair.of(player2, holder));
                }

                if (player2.hasEffect(MobEffects.BAD_OMEN)) {
                    player = player2;
                }
            }
        }

        return Optional.ofNullable(player).map(playerx -> Pair.of(playerx, MobEffects.BAD_OMEN));
    }

    public void resetAfterBecomingOminous(TrialSpawner spawner, ServerLevel level) {
        this.currentMobs.stream().map(level::getEntity).forEach(entity -> {
            if (entity != null) {
                level.levelEvent(3012, entity.blockPosition(), TrialSpawner.FlameParticle.NORMAL.encode());
                if (entity instanceof Mob mob) {
                    mob.dropPreservedEquipment();
                }

                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        });
        if (!spawner.getOminousConfig().spawnPotentialsDefinition().isEmpty()) {
            this.nextSpawnData = Optional.empty();
        }

        this.totalMobsSpawned = 0;
        this.currentMobs.clear();
        this.nextMobSpawnsAt = level.getGameTime() + spawner.getOminousConfig().ticksBetweenSpawn();
        spawner.markUpdated();
        this.cooldownEndsAt = level.getGameTime() + spawner.getOminousConfig().ticksBetweenItemSpawners();
    }

    private static void transformBadOmenIntoTrialOmen(Player player) {
        MobEffectInstance mobEffectInstance = player.getEffect(MobEffects.BAD_OMEN);
        if (mobEffectInstance != null) {
            int i = mobEffectInstance.getAmplifier() + 1;
            int j = 18000 * i;
            player.removeEffect(MobEffects.BAD_OMEN);
            player.addEffect(new MobEffectInstance(MobEffects.TRIAL_OMEN, j, 0));
        }
    }

    public boolean isReadyToOpenShutter(ServerLevel level, float delay, int targetCooldownLength) {
        long l = this.cooldownEndsAt - targetCooldownLength;
        return (float)level.getGameTime() >= (float)l + delay;
    }

    public boolean isReadyToEjectItems(ServerLevel level, float delay, int targetCooldownLength) {
        long l = this.cooldownEndsAt - targetCooldownLength;
        return (float)(level.getGameTime() - l) % delay == 0.0F;
    }

    public boolean isCooldownFinished(ServerLevel level) {
        return level.getGameTime() >= this.cooldownEndsAt;
    }

    public void setEntityId(TrialSpawner spawner, RandomSource random, EntityType<?> entityType) {
        this.getOrCreateNextSpawnData(spawner, random).getEntityToSpawn().putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString());
    }

    protected SpawnData getOrCreateNextSpawnData(TrialSpawner spawner, RandomSource random) {
        if (this.nextSpawnData.isPresent()) {
            return this.nextSpawnData.get();
        }

        SimpleWeightedRandomList<SpawnData> simpleWeightedRandomList = spawner.getConfig().spawnPotentialsDefinition();
        Optional<SpawnData> optional = simpleWeightedRandomList.isEmpty()
            ? this.nextSpawnData
            : simpleWeightedRandomList.getRandom(random).map(WeightedEntry.Wrapper::data);
        this.nextSpawnData = Optional.of(optional.orElseGet(SpawnData::new));
        spawner.markUpdated();
        return this.nextSpawnData.get();
    }

    @Nullable
    public Entity getOrCreateDisplayEntity(TrialSpawner spawner, Level level, TrialSpawnerState spawnerState) {
        if (!spawnerState.hasSpinningMob()) {
            return null;
        }

        if (this.displayEntity == null) {
            CompoundTag compoundTag = this.getOrCreateNextSpawnData(spawner, level.getRandom()).getEntityToSpawn();
            if (compoundTag.contains("id", 8)) {
                this.displayEntity = EntityType.loadEntityRecursive(compoundTag, level, Function.identity());
            }
        }

        return this.displayEntity;
    }

    public CompoundTag getUpdateTag(TrialSpawnerState spawnerState) {
        CompoundTag compoundTag = new CompoundTag();
        if (spawnerState == TrialSpawnerState.ACTIVE) {
            compoundTag.putLong("next_mob_spawns_at", this.nextMobSpawnsAt);
        }

        this.nextSpawnData
            .ifPresent(
                spawnData -> compoundTag.put(
                    "spawn_data",
                    SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, spawnData).result().orElseThrow(() -> new IllegalStateException("Invalid SpawnData"))
                )
            );
        return compoundTag;
    }

    public double getSpin() {
        return this.spin;
    }

    public double getOSpin() {
        return this.oSpin;
    }

    SimpleWeightedRandomList<ItemStack> getDispensingItems(ServerLevel level, TrialSpawnerConfig config, BlockPos pos) {
        if (this.dispensing != null) {
            return this.dispensing;
        }

        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(config.itemsToDropWhenOminous());
        LootParams lootParams = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
        long l = lowResolutionPosition(level, pos);
        ObjectArrayList<ItemStack> objectArrayList = lootTable.getRandomItems(lootParams, l);
        if (objectArrayList.isEmpty()) {
            return SimpleWeightedRandomList.empty();
        }

        SimpleWeightedRandomList.Builder<ItemStack> builder = new SimpleWeightedRandomList.Builder<>();

        for (ItemStack itemStack : objectArrayList) {
            builder.add(itemStack.copyWithCount(1), itemStack.getCount());
        }

        this.dispensing = builder.build();
        return this.dispensing;
    }

    private static long lowResolutionPosition(ServerLevel level, BlockPos pos) {
        BlockPos blockPos = new BlockPos(Mth.floor(pos.getX() / 30.0F), Mth.floor(pos.getY() / 20.0F), Mth.floor(pos.getZ() / 30.0F));
        return level.getSeed() + blockPos.asLong();
    }
}
