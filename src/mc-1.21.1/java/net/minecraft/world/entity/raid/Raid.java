package net.minecraft.world.entity.raid;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class Raid {
    private static final int SECTION_RADIUS_FOR_FINDING_NEW_VILLAGE_CENTER = 2;
    private static final int ATTEMPT_RAID_FARTHEST = 0;
    private static final int ATTEMPT_RAID_CLOSE = 1;
    private static final int ATTEMPT_RAID_INSIDE = 2;
    private static final int VILLAGE_SEARCH_RADIUS = 32;
    private static final int RAID_TIMEOUT_TICKS = 48000;
    private static final int NUM_SPAWN_ATTEMPTS = 3;
    private static final Component OMINOUS_BANNER_PATTERN_NAME = Component.translatable("block.minecraft.ominous_banner").withStyle(ChatFormatting.GOLD);
    private static final String RAIDERS_REMAINING = "event.minecraft.raid.raiders_remaining";
    public static final int VILLAGE_RADIUS_BUFFER = 16;
    private static final int POST_RAID_TICK_LIMIT = 40;
    private static final int DEFAULT_PRE_RAID_TICKS = 300;
    public static final int MAX_NO_ACTION_TIME = 2400;
    public static final int MAX_CELEBRATION_TICKS = 600;
    private static final int OUTSIDE_RAID_BOUNDS_TIMEOUT = 30;
    public static final int TICKS_PER_DAY = 24000;
    public static final int DEFAULT_MAX_RAID_OMEN_LEVEL = 5;
    private static final int LOW_MOB_THRESHOLD = 2;
    private static final Component RAID_NAME_COMPONENT = Component.translatable("event.minecraft.raid");
    private static final Component RAID_BAR_VICTORY_COMPONENT = Component.translatable("event.minecraft.raid.victory.full");
    private static final Component RAID_BAR_DEFEAT_COMPONENT = Component.translatable("event.minecraft.raid.defeat.full");
    private static final int HERO_OF_THE_VILLAGE_DURATION = 48000;
    public static final int VALID_RAID_RADIUS_SQR = 9216;
    public static final int RAID_REMOVAL_THRESHOLD_SQR = 12544;
    private final Map<Integer, Raider> groupToLeaderMap = Maps.newHashMap();
    private final Map<Integer, Set<Raider>> groupRaiderMap = Maps.newHashMap();
    private final Set<UUID> heroesOfTheVillage = Sets.newHashSet();
    private long ticksActive;
    private BlockPos center;
    private final ServerLevel level;
    private boolean started;
    private final int id;
    private float totalHealth;
    private int raidOmenLevel;
    private boolean active;
    private int groupsSpawned;
    private final ServerBossEvent raidEvent = new ServerBossEvent(RAID_NAME_COMPONENT, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private int postRaidTicks;
    private int raidCooldownTicks;
    private final RandomSource random = RandomSource.create();
    private final int numGroups;
    private Raid.RaidStatus status;
    private int celebrationTicks;
    private Optional<BlockPos> waveSpawnPos = Optional.empty();

    public Raid(int id, ServerLevel level, BlockPos center) {
        this.id = id;
        this.level = level;
        this.active = true;
        this.raidCooldownTicks = 300;
        this.raidEvent.setProgress(0.0F);
        this.center = center;
        this.numGroups = this.getNumGroups(level.getDifficulty());
        this.status = Raid.RaidStatus.ONGOING;
    }

    public Raid(ServerLevel level, CompoundTag compound) {
        this.level = level;
        this.id = compound.getInt("Id");
        this.started = compound.getBoolean("Started");
        this.active = compound.getBoolean("Active");
        this.ticksActive = compound.getLong("TicksActive");
        this.raidOmenLevel = compound.getInt("BadOmenLevel");
        this.groupsSpawned = compound.getInt("GroupsSpawned");
        this.raidCooldownTicks = compound.getInt("PreRaidTicks");
        this.postRaidTicks = compound.getInt("PostRaidTicks");
        this.totalHealth = compound.getFloat("TotalHealth");
        this.center = new BlockPos(compound.getInt("CX"), compound.getInt("CY"), compound.getInt("CZ"));
        this.numGroups = compound.getInt("NumGroups");
        this.status = Raid.RaidStatus.getByName(compound.getString("Status"));
        this.heroesOfTheVillage.clear();
        if (compound.contains("HeroesOfTheVillage", 9)) {
            for (Tag tag : compound.getList("HeroesOfTheVillage", 11)) {
                this.heroesOfTheVillage.add(NbtUtils.loadUUID(tag));
            }
        }
    }

    public boolean isOver() {
        return this.isVictory() || this.isLoss();
    }

    public boolean isBetweenWaves() {
        return this.hasFirstWaveSpawned() && this.getTotalRaidersAlive() == 0 && this.raidCooldownTicks > 0;
    }

    public boolean hasFirstWaveSpawned() {
        return this.groupsSpawned > 0;
    }

    public boolean isStopped() {
        return this.status == Raid.RaidStatus.STOPPED;
    }

    public boolean isVictory() {
        return this.status == Raid.RaidStatus.VICTORY;
    }

    public boolean isLoss() {
        return this.status == Raid.RaidStatus.LOSS;
    }

    public float getTotalHealth() {
        return this.totalHealth;
    }

    public Set<Raider> getAllRaiders() {
        Set<Raider> set = Sets.newHashSet();

        for (Set<Raider> set2 : this.groupRaiderMap.values()) {
            set.addAll(set2);
        }

        return set;
    }

    public Level getLevel() {
        return this.level;
    }

    public boolean isStarted() {
        return this.started;
    }

    public int getGroupsSpawned() {
        return this.groupsSpawned;
    }

    private Predicate<ServerPlayer> validPlayer() {
        return player -> {
            BlockPos blockPos = player.blockPosition();
            return player.isAlive() && this.level.getRaidAt(blockPos) == this;
        };
    }

    private void updatePlayers() {
        Set<ServerPlayer> set = Sets.newHashSet(this.raidEvent.getPlayers());
        List<ServerPlayer> list = this.level.getPlayers(this.validPlayer());

        for (ServerPlayer serverPlayer : list) {
            if (!set.contains(serverPlayer)) {
                this.raidEvent.addPlayer(serverPlayer);
            }
        }

        for (ServerPlayer serverPlayer : set) {
            if (!list.contains(serverPlayer)) {
                this.raidEvent.removePlayer(serverPlayer);
            }
        }
    }

    public int getMaxRaidOmenLevel() {
        return 5;
    }

    public int getRaidOmenLevel() {
        return this.raidOmenLevel;
    }

    public void setRaidOmenLevel(int raidOmenLevel) {
        this.raidOmenLevel = raidOmenLevel;
    }

    public boolean absorbRaidOmen(ServerPlayer player) {
        MobEffectInstance mobEffectInstance = player.getEffect(MobEffects.RAID_OMEN);
        if (mobEffectInstance == null) {
            return false;
        }

        this.raidOmenLevel = this.raidOmenLevel + mobEffectInstance.getAmplifier() + 1;
        this.raidOmenLevel = Mth.clamp(this.raidOmenLevel, 0, this.getMaxRaidOmenLevel());
        if (!this.hasFirstWaveSpawned()) {
            player.awardStat(Stats.RAID_TRIGGER);
            CriteriaTriggers.RAID_OMEN.trigger(player);
        }

        return true;
    }

    public void stop() {
        this.active = false;
        this.raidEvent.removeAllPlayers();
        this.status = Raid.RaidStatus.STOPPED;
    }

    public void tick() {
        if (!this.isStopped()) {
            if (this.status == Raid.RaidStatus.ONGOING) {
                boolean bl = this.active;
                this.active = this.level.hasChunkAt(this.center);
                if (this.level.getDifficulty() == Difficulty.PEACEFUL) {
                    this.stop();
                    return;
                }

                if (bl != this.active) {
                    this.raidEvent.setVisible(this.active);
                }

                if (!this.active) {
                    return;
                }

                if (!this.level.isVillage(this.center)) {
                    this.moveRaidCenterToNearbyVillageSection();
                }

                if (!this.level.isVillage(this.center)) {
                    if (this.groupsSpawned > 0) {
                        this.status = Raid.RaidStatus.LOSS;
                    } else {
                        this.stop();
                    }
                }

                this.ticksActive++;
                if (this.ticksActive >= 48000L) {
                    this.stop();
                    return;
                }

                int i = this.getTotalRaidersAlive();
                if (i == 0 && this.hasMoreWaves()) {
                    if (this.raidCooldownTicks <= 0) {
                        if (this.raidCooldownTicks == 0 && this.groupsSpawned > 0) {
                            this.raidCooldownTicks = 300;
                            this.raidEvent.setName(RAID_NAME_COMPONENT);
                            return;
                        }
                    } else {
                        boolean bl2 = this.waveSpawnPos.isPresent();
                        boolean bl3 = !bl2 && this.raidCooldownTicks % 5 == 0;
                        if (bl2 && !this.level.isPositionEntityTicking(this.waveSpawnPos.get())) {
                            bl3 = true;
                        }

                        if (bl3) {
                            int j = 0;
                            if (this.raidCooldownTicks < 100) {
                                j = 1;
                            } else if (this.raidCooldownTicks < 40) {
                                j = 2;
                            }

                            this.waveSpawnPos = this.getValidSpawnPos(j);
                        }

                        if (this.raidCooldownTicks == 300 || this.raidCooldownTicks % 20 == 0) {
                            this.updatePlayers();
                        }

                        this.raidCooldownTicks--;
                        this.raidEvent.setProgress(Mth.clamp((300 - this.raidCooldownTicks) / 300.0F, 0.0F, 1.0F));
                    }
                }

                if (this.ticksActive % 20L == 0L) {
                    this.updatePlayers();
                    this.updateRaiders();
                    if (i > 0) {
                        if (i <= 2) {
                            this.raidEvent
                                .setName(RAID_NAME_COMPONENT.copy().append(" - ").append(Component.translatable("event.minecraft.raid.raiders_remaining", i)));
                        } else {
                            this.raidEvent.setName(RAID_NAME_COMPONENT);
                        }
                    } else {
                        this.raidEvent.setName(RAID_NAME_COMPONENT);
                    }
                }

                boolean bl2 = false;
                int k = 0;

                while (this.shouldSpawnGroup()) {
                    BlockPos blockPos = this.waveSpawnPos.isPresent() ? this.waveSpawnPos.get() : this.findRandomSpawnPos(k, 20);
                    if (blockPos != null) {
                        this.started = true;
                        this.spawnGroup(blockPos);
                        if (!bl2) {
                            this.playSound(blockPos);
                            bl2 = true;
                        }
                    } else {
                        k++;
                    }

                    if (k > 3) {
                        this.stop();
                        break;
                    }
                }

                if (this.isStarted() && !this.hasMoreWaves() && i == 0) {
                    if (this.postRaidTicks < 40) {
                        this.postRaidTicks++;
                    } else {
                        this.status = Raid.RaidStatus.VICTORY;

                        for (UUID uUID : this.heroesOfTheVillage) {
                            Entity entity = this.level.getEntity(uUID);
                            if (entity instanceof LivingEntity livingEntity && !entity.isSpectator()) {
                                livingEntity.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 48000, this.raidOmenLevel - 1, false, false, true));
                                if (livingEntity instanceof ServerPlayer serverPlayer) {
                                    serverPlayer.awardStat(Stats.RAID_WIN);
                                    CriteriaTriggers.RAID_WIN.trigger(serverPlayer);
                                }
                            }
                        }
                    }
                }

                this.setDirty();
            } else if (this.isOver()) {
                this.celebrationTicks++;
                if (this.celebrationTicks >= 600) {
                    this.stop();
                    return;
                }

                if (this.celebrationTicks % 20 == 0) {
                    this.updatePlayers();
                    this.raidEvent.setVisible(true);
                    if (this.isVictory()) {
                        this.raidEvent.setProgress(0.0F);
                        this.raidEvent.setName(RAID_BAR_VICTORY_COMPONENT);
                    } else {
                        this.raidEvent.setName(RAID_BAR_DEFEAT_COMPONENT);
                    }
                }
            }
        }
    }

    private void moveRaidCenterToNearbyVillageSection() {
        Stream<SectionPos> stream = SectionPos.cube(SectionPos.of(this.center), 2);
        stream.filter(this.level::isVillage)
            .map(SectionPos::center)
            .min(Comparator.comparingDouble(pos -> pos.distSqr(this.center)))
            .ifPresent(this::setCenter);
    }

    private Optional<BlockPos> getValidSpawnPos(int offsetMultiplier) {
        for (int i = 0; i < 3; i++) {
            BlockPos blockPos = this.findRandomSpawnPos(offsetMultiplier, 1);
            if (blockPos != null) {
                return Optional.of(blockPos);
            }
        }

        return Optional.empty();
    }

    private boolean hasMoreWaves() {
        return this.hasBonusWave() ? !this.hasSpawnedBonusWave() : !this.isFinalWave();
    }

    private boolean isFinalWave() {
        return this.getGroupsSpawned() == this.numGroups;
    }

    private boolean hasBonusWave() {
        return this.raidOmenLevel > 1;
    }

    private boolean hasSpawnedBonusWave() {
        return this.getGroupsSpawned() > this.numGroups;
    }

    private boolean shouldSpawnBonusGroup() {
        return this.isFinalWave() && this.getTotalRaidersAlive() == 0 && this.hasBonusWave();
    }

    private void updateRaiders() {
        Iterator<Set<Raider>> iterator = this.groupRaiderMap.values().iterator();
        Set<Raider> set = Sets.newHashSet();

        while (iterator.hasNext()) {
            Set<Raider> set2 = iterator.next();

            for (Raider raider : set2) {
                BlockPos blockPos = raider.blockPosition();
                if (raider.isRemoved() || raider.level().dimension() != this.level.dimension() || this.center.distSqr(blockPos) >= 12544.0) {
                    set.add(raider);
                } else if (raider.tickCount > 600) {
                    if (this.level.getEntity(raider.getUUID()) == null) {
                        set.add(raider);
                    }

                    if (!this.level.isVillage(blockPos) && raider.getNoActionTime() > 2400) {
                        raider.setTicksOutsideRaid(raider.getTicksOutsideRaid() + 1);
                    }

                    if (raider.getTicksOutsideRaid() >= 30) {
                        set.add(raider);
                    }
                }
            }
        }

        for (Raider raider2 : set) {
            this.removeFromRaid(raider2, true);
        }
    }

    private void playSound(BlockPos pos) {
        float f = 13.0F;
        int i = 64;
        Collection<ServerPlayer> collection = this.raidEvent.getPlayers();
        long l = this.random.nextLong();

        for (ServerPlayer serverPlayer : this.level.players()) {
            Vec3 vec3 = serverPlayer.position();
            Vec3 vec32 = Vec3.atCenterOf(pos);
            double d = Math.sqrt((vec32.x - vec3.x) * (vec32.x - vec3.x) + (vec32.z - vec3.z) * (vec32.z - vec3.z));
            double e = vec3.x + 13.0 / d * (vec32.x - vec3.x);
            double g = vec3.z + 13.0 / d * (vec32.z - vec3.z);
            if (d <= 64.0 || collection.contains(serverPlayer)) {
                serverPlayer.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.NEUTRAL, e, serverPlayer.getY(), g, 64.0F, 1.0F, l));
            }
        }
    }

    private void spawnGroup(BlockPos pos) {
        boolean bl = false;
        int i = this.groupsSpawned + 1;
        this.totalHealth = 0.0F;
        DifficultyInstance difficultyInstance = this.level.getCurrentDifficultyAt(pos);
        boolean bl2 = this.shouldSpawnBonusGroup();

        for (Raid.RaiderType raiderType : Raid.RaiderType.VALUES) {
            int j = this.getDefaultNumSpawns(raiderType, i, bl2) + this.getPotentialBonusSpawns(raiderType, this.random, i, difficultyInstance, bl2);
            int k = 0;

            for (int l = 0; l < j; l++) {
                Raider raider = raiderType.entityType.create(this.level);
                if (raider == null) {
                    break;
                }

                if (!bl && raider.canBeLeader()) {
                    raider.setPatrolLeader(true);
                    this.setLeader(i, raider);
                    bl = true;
                }

                this.joinRaid(i, raider, pos, false);
                if (raiderType.entityType == EntityType.RAVAGER) {
                    Raider raider2 = null;
                    if (i == this.getNumGroups(Difficulty.NORMAL)) {
                        raider2 = EntityType.PILLAGER.create(this.level);
                    } else if (i >= this.getNumGroups(Difficulty.HARD)) {
                        if (k == 0) {
                            raider2 = EntityType.EVOKER.create(this.level);
                        } else {
                            raider2 = EntityType.VINDICATOR.create(this.level);
                        }
                    }

                    k++;
                    if (raider2 != null) {
                        this.joinRaid(i, raider2, pos, false);
                        raider2.moveTo(pos, 0.0F, 0.0F);
                        raider2.startRiding(raider);
                    }
                }
            }
        }

        this.waveSpawnPos = Optional.empty();
        this.groupsSpawned++;
        this.updateBossbar();
        this.setDirty();
    }

    public void joinRaid(int wave, Raider raider, @Nullable BlockPos pos, boolean isRecruited) {
        boolean bl = this.addWaveMob(wave, raider);
        if (bl) {
            raider.setCurrentRaid(this);
            raider.setWave(wave);
            raider.setCanJoinRaid(true);
            raider.setTicksOutsideRaid(0);
            if (!isRecruited && pos != null) {
                raider.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                raider.finalizeSpawn(this.level, this.level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
                raider.applyRaidBuffs(this.level, wave, false);
                raider.setOnGround(true);
                this.level.addFreshEntityWithPassengers(raider);
            }
        }
    }

    public void updateBossbar() {
        this.raidEvent.setProgress(Mth.clamp(this.getHealthOfLivingRaiders() / this.totalHealth, 0.0F, 1.0F));
    }

    public float getHealthOfLivingRaiders() {
        float f = 0.0F;

        for (Set<Raider> set : this.groupRaiderMap.values()) {
            for (Raider raider : set) {
                f += raider.getHealth();
            }
        }

        return f;
    }

    private boolean shouldSpawnGroup() {
        return this.raidCooldownTicks == 0 && (this.groupsSpawned < this.numGroups || this.shouldSpawnBonusGroup()) && this.getTotalRaidersAlive() == 0;
    }

    public int getTotalRaidersAlive() {
        return this.groupRaiderMap.values().stream().mapToInt(Set::size).sum();
    }

    public void removeFromRaid(Raider raider, boolean wanderedOutOfRaid) {
        Set<Raider> set = this.groupRaiderMap.get(raider.getWave());
        if (set != null) {
            boolean bl = set.remove(raider);
            if (bl) {
                if (wanderedOutOfRaid) {
                    this.totalHealth = this.totalHealth - raider.getHealth();
                }

                raider.setCurrentRaid(null);
                this.updateBossbar();
                this.setDirty();
            }
        }
    }

    private void setDirty() {
        this.level.getRaids().setDirty();
    }

    public static ItemStack getLeaderBannerInstance(HolderGetter<BannerPattern> patternRegistry) {
        ItemStack itemStack = new ItemStack(Items.WHITE_BANNER);
        BannerPatternLayers bannerPatternLayers = new BannerPatternLayers.Builder()
            .addIfRegistered(patternRegistry, BannerPatterns.RHOMBUS_MIDDLE, DyeColor.CYAN)
            .addIfRegistered(patternRegistry, BannerPatterns.STRIPE_BOTTOM, DyeColor.LIGHT_GRAY)
            .addIfRegistered(patternRegistry, BannerPatterns.STRIPE_CENTER, DyeColor.GRAY)
            .addIfRegistered(patternRegistry, BannerPatterns.BORDER, DyeColor.LIGHT_GRAY)
            .addIfRegistered(patternRegistry, BannerPatterns.STRIPE_MIDDLE, DyeColor.BLACK)
            .addIfRegistered(patternRegistry, BannerPatterns.HALF_HORIZONTAL, DyeColor.LIGHT_GRAY)
            .addIfRegistered(patternRegistry, BannerPatterns.CIRCLE_MIDDLE, DyeColor.LIGHT_GRAY)
            .addIfRegistered(patternRegistry, BannerPatterns.BORDER, DyeColor.BLACK)
            .build();
        itemStack.set(DataComponents.BANNER_PATTERNS, bannerPatternLayers);
        itemStack.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        itemStack.set(DataComponents.ITEM_NAME, OMINOUS_BANNER_PATTERN_NAME);
        return itemStack;
    }

    @Nullable
    public Raider getLeader(int wave) {
        return this.groupToLeaderMap.get(wave);
    }

    @Nullable
    private BlockPos findRandomSpawnPos(int offsetMultiplier, int maxTry) {
        int i = offsetMultiplier == 0 ? 2 : 2 - offsetMultiplier;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        SpawnPlacementType spawnPlacementType = SpawnPlacements.getPlacementType(EntityType.RAVAGER);

        for (int j = 0; j < maxTry; j++) {
            float f = this.level.random.nextFloat() * (float) (Math.PI * 2);
            int k = this.center.getX() + Mth.floor(Mth.cos(f) * 32.0F * i) + this.level.random.nextInt(5);
            int l = this.center.getZ() + Mth.floor(Mth.sin(f) * 32.0F * i) + this.level.random.nextInt(5);
            int m = this.level.getHeight(Heightmap.Types.WORLD_SURFACE, k, l);
            mutableBlockPos.set(k, m, l);
            if (!this.level.isVillage(mutableBlockPos) || offsetMultiplier >= 2) {
                int n = 10;
                if (this.level.hasChunksAt(mutableBlockPos.getX() - 10, mutableBlockPos.getZ() - 10, mutableBlockPos.getX() + 10, mutableBlockPos.getZ() + 10)
                    && this.level.isPositionEntityTicking(mutableBlockPos)
                    && (
                        spawnPlacementType.isSpawnPositionOk(this.level, mutableBlockPos, EntityType.RAVAGER)
                            || this.level.getBlockState(mutableBlockPos.below()).is(Blocks.SNOW) && this.level.getBlockState(mutableBlockPos).isAir()
                    )) {
                    return mutableBlockPos;
                }
            }
        }

        return null;
    }

    private boolean addWaveMob(int wave, Raider raider) {
        return this.addWaveMob(wave, raider, true);
    }

    public boolean addWaveMob(int wave, Raider raider, boolean isRecruited) {
        this.groupRaiderMap.computeIfAbsent(wave, value -> Sets.newHashSet());
        Set<Raider> set = this.groupRaiderMap.get(wave);
        Raider raider2 = null;

        for (Raider raider3 : set) {
            if (raider3.getUUID().equals(raider.getUUID())) {
                raider2 = raider3;
                break;
            }
        }

        if (raider2 != null) {
            set.remove(raider2);
            set.add(raider);
        }

        set.add(raider);
        if (isRecruited) {
            this.totalHealth = this.totalHealth + raider.getHealth();
        }

        this.updateBossbar();
        this.setDirty();
        return true;
    }

    public void setLeader(int wave, Raider raider) {
        this.groupToLeaderMap.put(wave, raider);
        raider.setItemSlot(EquipmentSlot.HEAD, getLeaderBannerInstance(raider.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
        raider.setDropChance(EquipmentSlot.HEAD, 2.0F);
    }

    public void removeLeader(int wave) {
        this.groupToLeaderMap.remove(wave);
    }

    public BlockPos getCenter() {
        return this.center;
    }

    private void setCenter(BlockPos center) {
        this.center = center;
    }

    public int getId() {
        return this.id;
    }

    private int getDefaultNumSpawns(Raid.RaiderType raiderType, int wave, boolean shouldSpawnBonusGroup) {
        return shouldSpawnBonusGroup ? raiderType.spawnsPerWaveBeforeBonus[this.numGroups] : raiderType.spawnsPerWaveBeforeBonus[wave];
    }

    private int getPotentialBonusSpawns(Raid.RaiderType raiderType, RandomSource random, int wave, DifficultyInstance difficulty, boolean shouldSpawnBonusGroup) {
        Difficulty difficulty2 = difficulty.getDifficulty();
        boolean bl = difficulty2 == Difficulty.EASY;
        boolean bl2 = difficulty2 == Difficulty.NORMAL;
        int i;
        switch (raiderType) {
            case VINDICATOR:
            case PILLAGER:
                if (bl) {
                    i = random.nextInt(2);
                } else if (bl2) {
                    i = 1;
                } else {
                    i = 2;
                }
                break;
            case EVOKER:
            default:
                return 0;
            case WITCH:
                if (bl || wave <= 2 || wave == 4) {
                    return 0;
                }

                i = 1;
                break;
            case RAVAGER:
                i = !bl && shouldSpawnBonusGroup ? 1 : 0;
        }

        return i > 0 ? random.nextInt(i + 1) : 0;
    }

    public boolean isActive() {
        return this.active;
    }

    public CompoundTag save(CompoundTag compound) {
        compound.putInt("Id", this.id);
        compound.putBoolean("Started", this.started);
        compound.putBoolean("Active", this.active);
        compound.putLong("TicksActive", this.ticksActive);
        compound.putInt("BadOmenLevel", this.raidOmenLevel);
        compound.putInt("GroupsSpawned", this.groupsSpawned);
        compound.putInt("PreRaidTicks", this.raidCooldownTicks);
        compound.putInt("PostRaidTicks", this.postRaidTicks);
        compound.putFloat("TotalHealth", this.totalHealth);
        compound.putInt("NumGroups", this.numGroups);
        compound.putString("Status", this.status.getName());
        compound.putInt("CX", this.center.getX());
        compound.putInt("CY", this.center.getY());
        compound.putInt("CZ", this.center.getZ());
        ListTag listTag = new ListTag();

        for (UUID uUID : this.heroesOfTheVillage) {
            listTag.add(NbtUtils.createUUID(uUID));
        }

        compound.put("HeroesOfTheVillage", listTag);
        return compound;
    }

    public int getNumGroups(Difficulty difficulty) {
        switch (difficulty) {
            case EASY:
                return 3;
            case NORMAL:
                return 5;
            case HARD:
                return 7;
            default:
                return 0;
        }
    }

    public float getEnchantOdds() {
        int i = this.getRaidOmenLevel();
        if (i == 2) {
            return 0.1F;
        } else if (i == 3) {
            return 0.25F;
        } else if (i == 4) {
            return 0.5F;
        } else {
            return i == 5 ? 0.75F : 0.0F;
        }
    }

    public void addHeroOfTheVillage(Entity player) {
        this.heroesOfTheVillage.add(player.getUUID());
    }

    enum RaidStatus {
        ONGOING,
        VICTORY,
        LOSS,
        STOPPED;

        private static final Raid.RaidStatus[] VALUES = values();

        static Raid.RaidStatus getByName(String name) {
            for (Raid.RaidStatus raidStatus : VALUES) {
                if (name.equalsIgnoreCase(raidStatus.name())) {
                    return raidStatus;
                }
            }

            return ONGOING;
        }

        public String getName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    enum RaiderType {
        VINDICATOR(EntityType.VINDICATOR, new int[]{0, 0, 2, 0, 1, 4, 2, 5}),
        EVOKER(EntityType.EVOKER, new int[]{0, 0, 0, 0, 0, 1, 1, 2}),
        PILLAGER(EntityType.PILLAGER, new int[]{0, 4, 3, 3, 4, 4, 4, 2}),
        WITCH(EntityType.WITCH, new int[]{0, 0, 0, 0, 3, 0, 0, 1}),
        RAVAGER(EntityType.RAVAGER, new int[]{0, 0, 0, 1, 0, 1, 0, 2});

        static final Raid.RaiderType[] VALUES = values();
        final EntityType<? extends Raider> entityType;
        final int[] spawnsPerWaveBeforeBonus;

        RaiderType(final EntityType<? extends Raider> entityType, final int[] spawnsPerWaveBeforeBonus) {
            this.entityType = entityType;
            this.spawnsPerWaveBeforeBonus = spawnsPerWaveBeforeBonus;
        }
    }
}
