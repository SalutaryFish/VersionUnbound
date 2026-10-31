package net.minecraft.world.level;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public abstract class BaseSpawner {
    public static final String SPAWN_DATA_TAG = "SpawnData";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EVENT_SPAWN = 1;
    private int spawnDelay = 20;
    private SimpleWeightedRandomList<SpawnData> spawnPotentials = SimpleWeightedRandomList.empty();
    @Nullable
    private SpawnData nextSpawnData;
    private double spin;
    private double oSpin;
    private int minSpawnDelay = 200;
    private int maxSpawnDelay = 800;
    private int spawnCount = 4;
    /**
     * Cached instance of the entity to render inside the spawner.
     */
    @Nullable
    private Entity displayEntity;
    private int maxNearbyEntities = 6;
    private int requiredPlayerRange = 16;
    private int spawnRange = 4;

    public void setEntityId(EntityType<?> type, @Nullable Level level, RandomSource random, BlockPos pos) {
        this.getOrCreateNextSpawnData(level, random, pos).getEntityToSpawn().putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    private boolean isNearPlayer(Level level, BlockPos pos) {
        return level.hasNearbyAlivePlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, this.requiredPlayerRange);
    }

    public void clientTick(Level level, BlockPos pos) {
        if (!this.isNearPlayer(level, pos)) {
            this.oSpin = this.spin;
        } else if (this.displayEntity != null) {
            RandomSource randomSource = level.getRandom();
            double d = pos.getX() + randomSource.nextDouble();
            double e = pos.getY() + randomSource.nextDouble();
            double f = pos.getZ() + randomSource.nextDouble();
            level.addParticle(ParticleTypes.SMOKE, d, e, f, 0.0, 0.0, 0.0);
            level.addParticle(ParticleTypes.FLAME, d, e, f, 0.0, 0.0, 0.0);
            if (this.spawnDelay > 0) {
                this.spawnDelay--;
            }

            this.oSpin = this.spin;
            this.spin = (this.spin + 1000.0F / (this.spawnDelay + 200.0F)) % 360.0;
        }
    }

    public void serverTick(ServerLevel serverLevel, BlockPos pos) {
        if (this.isNearPlayer(serverLevel, pos)) {
            if (this.spawnDelay == -1) {
                this.delay(serverLevel, pos);
            }

            if (this.spawnDelay > 0) {
                this.spawnDelay--;
            } else {
                boolean bl = false;
                RandomSource randomSource = serverLevel.getRandom();
                SpawnData spawnData = this.getOrCreateNextSpawnData(serverLevel, randomSource, pos);

                for (int i = 0; i < this.spawnCount; i++) {
                    CompoundTag compoundTag = spawnData.getEntityToSpawn();
                    Optional<EntityType<?>> optional = EntityType.by(compoundTag);
                    if (optional.isEmpty()) {
                        this.delay(serverLevel, pos);
                        return;
                    }

                    ListTag listTag = compoundTag.getList("Pos", 6);
                    int j = listTag.size();
                    double d = j >= 1 ? listTag.getDouble(0) : pos.getX() + (randomSource.nextDouble() - randomSource.nextDouble()) * this.spawnRange + 0.5;
                    double e = j >= 2 ? listTag.getDouble(1) : pos.getY() + randomSource.nextInt(3) - 1;
                    double f = j >= 3 ? listTag.getDouble(2) : pos.getZ() + (randomSource.nextDouble() - randomSource.nextDouble()) * this.spawnRange + 0.5;
                    if (serverLevel.noCollision(optional.get().getSpawnAABB(d, e, f))) {
                        BlockPos blockPos = BlockPos.containing(d, e, f);
                        if (spawnData.getCustomSpawnRules().isPresent()) {
                            if (!optional.get().getCategory().isFriendly() && serverLevel.getDifficulty() == Difficulty.PEACEFUL) {
                                continue;
                            }

                            SpawnData.CustomSpawnRules customSpawnRules = spawnData.getCustomSpawnRules().get();
                            if (!customSpawnRules.isValidPosition(blockPos, serverLevel)) {
                                continue;
                            }
                        } else if (!SpawnPlacements.checkSpawnRules(optional.get(), serverLevel, MobSpawnType.SPAWNER, blockPos, serverLevel.getRandom())) {
                            continue;
                        }

                        Entity entity = EntityType.loadEntityRecursive(compoundTag, serverLevel, entityx -> {
                            entityx.moveTo(d, e, f, entityx.getYRot(), entityx.getXRot());
                            return entityx;
                        });
                        if (entity == null) {
                            this.delay(serverLevel, pos);
                            return;
                        }

                        int k = serverLevel.getEntities(
                                EntityTypeTest.forExactClass(entity.getClass()),
                                new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1).inflate(this.spawnRange),
                                EntitySelector.NO_SPECTATORS
                            )
                            .size();
                        if (k >= this.maxNearbyEntities) {
                            this.delay(serverLevel, pos);
                            return;
                        }

                        entity.moveTo(entity.getX(), entity.getY(), entity.getZ(), randomSource.nextFloat() * 360.0F, 0.0F);
                        if (entity instanceof Mob mob) {
                            if (spawnData.getCustomSpawnRules().isEmpty() && !mob.checkSpawnRules(serverLevel, MobSpawnType.SPAWNER)
                                || !mob.checkSpawnObstruction(serverLevel)) {
                                continue;
                            }

                            boolean bl2 = spawnData.getEntityToSpawn().size() == 1 && spawnData.getEntityToSpawn().contains("id", 8);
                            if (bl2) {
                                ((Mob)entity)
                                    .finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(entity.blockPosition()), MobSpawnType.SPAWNER, null);
                            }

                            spawnData.getEquipment().ifPresent(mob::equip);
                        }

                        if (!serverLevel.tryAddFreshEntityWithPassengers(entity)) {
                            this.delay(serverLevel, pos);
                            return;
                        }

                        serverLevel.levelEvent(2004, pos, 0);
                        serverLevel.gameEvent(entity, GameEvent.ENTITY_PLACE, blockPos);
                        if (entity instanceof Mob) {
                            ((Mob)entity).spawnAnim();
                        }

                        bl = true;
                    }
                }

                if (bl) {
                    this.delay(serverLevel, pos);
                }
            }
        }
    }

    private void delay(Level level, BlockPos pos) {
        RandomSource randomSource = level.random;
        if (this.maxSpawnDelay <= this.minSpawnDelay) {
            this.spawnDelay = this.minSpawnDelay;
        } else {
            this.spawnDelay = this.minSpawnDelay + randomSource.nextInt(this.maxSpawnDelay - this.minSpawnDelay);
        }

        this.spawnPotentials.getRandom(randomSource).ifPresent(data -> this.setNextSpawnData(level, pos, data.data()));
        this.broadcastEvent(level, pos, 1);
    }

    public void load(@Nullable Level level, BlockPos pos, CompoundTag tag) {
        this.spawnDelay = tag.getShort("Delay");
        boolean bl = tag.contains("SpawnData", 10);
        if (bl) {
            SpawnData spawnData = SpawnData.CODEC
                .parse(NbtOps.INSTANCE, tag.getCompound("SpawnData"))
                .resultOrPartial(data -> LOGGER.warn("Invalid SpawnData: {}", data))
                .orElseGet(SpawnData::new);
            this.setNextSpawnData(level, pos, spawnData);
        }

        boolean bl2 = tag.contains("SpawnPotentials", 9);
        if (bl2) {
            ListTag listTag = tag.getList("SpawnPotentials", 10);
            this.spawnPotentials = SpawnData.LIST_CODEC
                .parse(NbtOps.INSTANCE, listTag)
                .resultOrPartial(potentials -> LOGGER.warn("Invalid SpawnPotentials list: {}", potentials))
                .orElseGet(SimpleWeightedRandomList::empty);
        } else {
            this.spawnPotentials = SimpleWeightedRandomList.single(this.nextSpawnData != null ? this.nextSpawnData : new SpawnData());
        }

        if (tag.contains("MinSpawnDelay", 99)) {
            this.minSpawnDelay = tag.getShort("MinSpawnDelay");
            this.maxSpawnDelay = tag.getShort("MaxSpawnDelay");
            this.spawnCount = tag.getShort("SpawnCount");
        }

        if (tag.contains("MaxNearbyEntities", 99)) {
            this.maxNearbyEntities = tag.getShort("MaxNearbyEntities");
            this.requiredPlayerRange = tag.getShort("RequiredPlayerRange");
        }

        if (tag.contains("SpawnRange", 99)) {
            this.spawnRange = tag.getShort("SpawnRange");
        }

        this.displayEntity = null;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putShort("Delay", (short)this.spawnDelay);
        tag.putShort("MinSpawnDelay", (short)this.minSpawnDelay);
        tag.putShort("MaxSpawnDelay", (short)this.maxSpawnDelay);
        tag.putShort("SpawnCount", (short)this.spawnCount);
        tag.putShort("MaxNearbyEntities", (short)this.maxNearbyEntities);
        tag.putShort("RequiredPlayerRange", (short)this.requiredPlayerRange);
        tag.putShort("SpawnRange", (short)this.spawnRange);
        if (this.nextSpawnData != null) {
            tag.put(
                "SpawnData",
                SpawnData.CODEC
                    .encodeStart(NbtOps.INSTANCE, this.nextSpawnData)
                    .getOrThrow(string -> new IllegalStateException("Invalid SpawnData: " + string))
            );
        }

        tag.put("SpawnPotentials", SpawnData.LIST_CODEC.encodeStart(NbtOps.INSTANCE, this.spawnPotentials).getOrThrow());
        return tag;
    }

    @Nullable
    public Entity getOrCreateDisplayEntity(Level level, BlockPos pos) {
        if (this.displayEntity == null) {
            CompoundTag compoundTag = this.getOrCreateNextSpawnData(level, level.getRandom(), pos).getEntityToSpawn();
            if (!compoundTag.contains("id", 8)) {
                return null;
            }

            this.displayEntity = EntityType.loadEntityRecursive(compoundTag, level, Function.identity());
            if (compoundTag.size() == 1 && this.displayEntity instanceof Mob) {
            }
        }

        return this.displayEntity;
    }

    public boolean onEventTriggered(Level level, int id) {
        if (id == 1) {
            if (level.isClientSide) {
                this.spawnDelay = this.minSpawnDelay;
            }

            return true;
        } else {
            return false;
        }
    }

    protected void setNextSpawnData(@Nullable Level level, BlockPos pos, SpawnData nextSpawnData) {
        this.nextSpawnData = nextSpawnData;
    }

    private SpawnData getOrCreateNextSpawnData(@Nullable Level level, RandomSource random, BlockPos pos) {
        if (this.nextSpawnData != null) {
            return this.nextSpawnData;
        }

        this.setNextSpawnData(level, pos, this.spawnPotentials.getRandom(random).map(WeightedEntry.Wrapper::data).orElseGet(SpawnData::new));
        return this.nextSpawnData;
    }

    public abstract void broadcastEvent(Level var1, BlockPos var2, int var3);

    public double getSpin() {
        return this.spin;
    }

    public double getoSpin() {
        return this.oSpin;
    }
}
