package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class SculkShriekerBlockEntity extends BlockEntity implements GameEventListener.Provider<VibrationSystem.Listener>, VibrationSystem {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WARNING_SOUND_RADIUS = 10;
    private static final int WARDEN_SPAWN_ATTEMPTS = 20;
    private static final int WARDEN_SPAWN_RANGE_XZ = 5;
    private static final int WARDEN_SPAWN_RANGE_Y = 6;
    private static final int DARKNESS_RADIUS = 40;
    private static final int SHRIEKING_TICKS = 90;
    private static final Int2ObjectMap<SoundEvent> SOUND_BY_LEVEL = Util.make(new Int2ObjectOpenHashMap<>(), map -> {
        map.put(1, SoundEvents.WARDEN_NEARBY_CLOSE);
        map.put(2, SoundEvents.WARDEN_NEARBY_CLOSER);
        map.put(3, SoundEvents.WARDEN_NEARBY_CLOSEST);
        map.put(4, SoundEvents.WARDEN_LISTENING_ANGRY);
    });
    private int warningLevel;
    private final VibrationSystem.User vibrationUser = new SculkShriekerBlockEntity.VibrationUser();
    private VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.Listener vibrationListener = new VibrationSystem.Listener(this);

    public SculkShriekerBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.SCULK_SHRIEKER, pos, blockState);
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this.vibrationUser;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("warning_level", 99)) {
            this.warningLevel = tag.getInt("warning_level");
        }

        RegistryOps<Tag> registryOps = registries.createSerializationContext(NbtOps.INSTANCE);
        if (tag.contains("listener", 10)) {
            VibrationSystem.Data.CODEC
                .parse(registryOps, tag.getCompound("listener"))
                .resultOrPartial(string -> LOGGER.error("Failed to parse vibration listener for Sculk Shrieker: '{}'", string))
                .ifPresent(data -> this.vibrationData = data);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("warning_level", this.warningLevel);
        RegistryOps<Tag> registryOps = registries.createSerializationContext(NbtOps.INSTANCE);
        VibrationSystem.Data.CODEC
            .encodeStart(registryOps, this.vibrationData)
            .resultOrPartial(string -> LOGGER.error("Failed to encode vibration listener for Sculk Shrieker: '{}'", string))
            .ifPresent(tagx -> tag.put("listener", tagx));
    }

    @Nullable
    public static ServerPlayer tryGetPlayer(@Nullable Entity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        } else if (entity != null && entity.getControllingPassenger() instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        } else if (entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer serverPlayer2) {
            return serverPlayer2;
        } else {
            return entity instanceof ItemEntity itemEntity && itemEntity.getOwner() instanceof ServerPlayer serverPlayer2 ? serverPlayer2 : null;
        }
    }

    public void tryShriek(ServerLevel level, @Nullable ServerPlayer player) {
        if (player != null) {
            BlockState blockState = this.getBlockState();
            if (!blockState.getValue(SculkShriekerBlock.SHRIEKING)) {
                this.warningLevel = 0;
                if (!this.canRespond(level) || this.tryToWarn(level, player)) {
                    this.shriek(level, player);
                }
            }
        }
    }

    private boolean tryToWarn(ServerLevel level, ServerPlayer player) {
        OptionalInt optionalInt = WardenSpawnTracker.tryWarn(level, this.getBlockPos(), player);
        optionalInt.ifPresent(i -> this.warningLevel = i);
        return optionalInt.isPresent();
    }

    private void shriek(ServerLevel level, @Nullable Entity sourceEntity) {
        BlockPos blockPos = this.getBlockPos();
        BlockState blockState = this.getBlockState();
        level.setBlock(blockPos, blockState.setValue(SculkShriekerBlock.SHRIEKING, true), 2);
        level.scheduleTick(blockPos, blockState.getBlock(), 90);
        level.levelEvent(3007, blockPos, 0);
        level.gameEvent(GameEvent.SHRIEK, blockPos, GameEvent.Context.of(sourceEntity));
    }

    private boolean canRespond(ServerLevel level) {
        return this.getBlockState().getValue(SculkShriekerBlock.CAN_SUMMON)
            && level.getDifficulty() != Difficulty.PEACEFUL
            && level.getGameRules().getBoolean(GameRules.RULE_DO_WARDEN_SPAWNING);
    }

    public void tryRespond(ServerLevel level) {
        if (this.canRespond(level) && this.warningLevel > 0) {
            if (!this.trySummonWarden(level)) {
                this.playWardenReplySound(level);
            }

            Warden.applyDarknessAround(level, Vec3.atCenterOf(this.getBlockPos()), null, 40);
        }
    }

    private void playWardenReplySound(Level level) {
        SoundEvent soundEvent = SOUND_BY_LEVEL.get(this.warningLevel);
        if (soundEvent != null) {
            BlockPos blockPos = this.getBlockPos();
            int i = blockPos.getX() + Mth.randomBetweenInclusive(level.random, -10, 10);
            int j = blockPos.getY() + Mth.randomBetweenInclusive(level.random, -10, 10);
            int k = blockPos.getZ() + Mth.randomBetweenInclusive(level.random, -10, 10);
            level.playSound(null, i, j, k, soundEvent, SoundSource.HOSTILE, 5.0F, 1.0F);
        }
    }

    private boolean trySummonWarden(ServerLevel level) {
        return this.warningLevel < 4
            ? false
            : SpawnUtil.trySpawnMob(EntityType.WARDEN, MobSpawnType.TRIGGERED, level, this.getBlockPos(), 20, 5, 6, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER)
                .isPresent();
    }

    public VibrationSystem.Listener getListener() {
        return this.vibrationListener;
    }

    class VibrationUser implements VibrationSystem.User {
        private static final int LISTENER_RADIUS = 8;
        private final PositionSource positionSource = new BlockPositionSource(SculkShriekerBlockEntity.this.worldPosition);

        public VibrationUser() {
        }

        @Override
        public int getListenerRadius() {
            return 8;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.SHRIEKER_CAN_LISTEN;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel serverLevel, BlockPos blockPos, Holder<GameEvent> holder, GameEvent.Context context) {
            return !SculkShriekerBlockEntity.this.getBlockState().getValue(SculkShriekerBlock.SHRIEKING)
                && SculkShriekerBlockEntity.tryGetPlayer(context.sourceEntity()) != null;
        }

        @Override
        public void onReceiveVibration(
            ServerLevel serverLevel, BlockPos blockPos, Holder<GameEvent> holder, @Nullable Entity entity, @Nullable Entity entity2, float f
        ) {
            SculkShriekerBlockEntity.this.tryShriek(serverLevel, SculkShriekerBlockEntity.tryGetPlayer(entity2 != null ? entity2 : entity));
        }

        @Override
        public void onDataChanged() {
            SculkShriekerBlockEntity.this.setChanged();
        }

        @Override
        public boolean requiresAdjacentChunksToBeTicking() {
            return true;
        }
    }
}
