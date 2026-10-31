package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class TheEndGatewayBlockEntity extends TheEndPortalBlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SPAWN_TIME = 200;
    private static final int COOLDOWN_TIME = 40;
    private static final int ATTENTION_INTERVAL = 2400;
    private static final int EVENT_COOLDOWN = 1;
    private static final int GATEWAY_HEIGHT_ABOVE_SURFACE = 10;
    private long age;
    private int teleportCooldown;
    @Nullable
    private BlockPos exitPortal;
    private boolean exactTeleport;

    public TheEndGatewayBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.END_GATEWAY, pos, blockState);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Age", this.age);
        if (this.exitPortal != null) {
            tag.put("exit_portal", NbtUtils.writeBlockPos(this.exitPortal));
        }

        if (this.exactTeleport) {
            tag.putBoolean("ExactTeleport", true);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.age = tag.getLong("Age");
        NbtUtils.readBlockPos(tag, "exit_portal").filter(Level::isInSpawnableBounds).ifPresent(blockPos -> this.exitPortal = blockPos);
        this.exactTeleport = tag.getBoolean("ExactTeleport");
    }

    public static void beamAnimationTick(Level level, BlockPos pos, BlockState state, TheEndGatewayBlockEntity blockEntity) {
        blockEntity.age++;
        if (blockEntity.isCoolingDown()) {
            blockEntity.teleportCooldown--;
        }
    }

    public static void portalTick(Level level, BlockPos pos, BlockState state, TheEndGatewayBlockEntity blockEntity) {
        boolean bl = blockEntity.isSpawning();
        boolean bl2 = blockEntity.isCoolingDown();
        blockEntity.age++;
        if (bl2) {
            blockEntity.teleportCooldown--;
        } else if (blockEntity.age % 2400L == 0L) {
            triggerCooldown(level, pos, state, blockEntity);
        }

        if (bl != blockEntity.isSpawning() || bl2 != blockEntity.isCoolingDown()) {
            setChanged(level, pos, state);
        }
    }

    public boolean isSpawning() {
        return this.age < 200L;
    }

    public boolean isCoolingDown() {
        return this.teleportCooldown > 0;
    }

    public float getSpawnPercent(float partialTicks) {
        return Mth.clamp(((float)this.age + partialTicks) / 200.0F, 0.0F, 1.0F);
    }

    public float getCooldownPercent(float partialTicks) {
        return 1.0F - Mth.clamp((this.teleportCooldown - partialTicks) / 40.0F, 0.0F, 1.0F);
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public static void triggerCooldown(Level level, BlockPos pos, BlockState state, TheEndGatewayBlockEntity blockEntity) {
        if (!level.isClientSide) {
            blockEntity.teleportCooldown = 40;
            level.blockEvent(pos, state.getBlock(), 1, 0);
            setChanged(level, pos, state);
        }
    }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == 1) {
            this.teleportCooldown = 40;
            return true;
        } else {
            return super.triggerEvent(id, type);
        }
    }

    @Nullable
    public Vec3 getPortalPosition(ServerLevel level, BlockPos pos) {
        if (this.exitPortal == null && level.dimension() == Level.END) {
            BlockPos blockPos = findOrCreateValidTeleportPos(level, pos);
            blockPos = blockPos.above(10);
            LOGGER.debug("Creating portal at {}", blockPos);
            spawnGatewayPortal(level, blockPos, EndGatewayConfiguration.knownExit(pos, false));
            this.setExitPosition(blockPos, this.exactTeleport);
        }

        if (this.exitPortal != null) {
            BlockPos blockPos = this.exactTeleport ? this.exitPortal : findExitPosition(level, this.exitPortal);
            return blockPos.getBottomCenter();
        } else {
            return null;
        }
    }

    private static BlockPos findExitPosition(Level level, BlockPos pos) {
        BlockPos blockPos = findTallestBlock(level, pos.offset(0, 2, 0), 5, false);
        LOGGER.debug("Best exit position for portal at {} is {}", pos, blockPos);
        return blockPos.above();
    }

    private static BlockPos findOrCreateValidTeleportPos(ServerLevel level, BlockPos pos) {
        Vec3 vec3 = findExitPortalXZPosTentative(level, pos);
        LevelChunk levelChunk = getChunk(level, vec3);
        BlockPos blockPos = findValidSpawnInChunk(levelChunk);
        if (blockPos == null) {
            BlockPos blockPos2 = BlockPos.containing(vec3.x + 0.5, 75.0, vec3.z + 0.5);
            LOGGER.debug("Failed to find a suitable block to teleport to, spawning an island on {}", blockPos2);
            level.registryAccess()
                .registry(Registries.CONFIGURED_FEATURE)
                .flatMap(registry -> registry.getHolder(EndFeatures.END_ISLAND))
                .ifPresent(
                    reference -> reference.value().place(level, level.getChunkSource().getGenerator(), RandomSource.create(blockPos2.asLong()), blockPos2)
                );
            blockPos = blockPos2;
        } else {
            LOGGER.debug("Found suitable block to teleport to: {}", blockPos);
        }

        return findTallestBlock(level, blockPos, 16, true);
    }

    private static Vec3 findExitPortalXZPosTentative(ServerLevel level, BlockPos pos) {
        Vec3 vec3 = new Vec3(pos.getX(), 0.0, pos.getZ()).normalize();
        int i = 1024;
        Vec3 vec32 = vec3.scale(1024.0);

        for (int j = 16; !isChunkEmpty(level, vec32) && j-- > 0; vec32 = vec32.add(vec3.scale(-16.0))) {
            LOGGER.debug("Skipping backwards past nonempty chunk at {}", vec32);
        }

        for (int var6 = 16; isChunkEmpty(level, vec32) && var6-- > 0; vec32 = vec32.add(vec3.scale(16.0))) {
            LOGGER.debug("Skipping forward past empty chunk at {}", vec32);
        }

        LOGGER.debug("Found chunk at {}", vec32);
        return vec32;
    }

    private static boolean isChunkEmpty(ServerLevel level, Vec3 pos) {
        return getChunk(level, pos).getHighestFilledSectionIndex() == -1;
    }

    private static BlockPos findTallestBlock(BlockGetter level, BlockPos pos, int radius, boolean allowBedrock) {
        BlockPos blockPos = null;

        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                if (i != 0 || j != 0 || allowBedrock) {
                    for (int k = level.getMaxBuildHeight() - 1; k > (blockPos == null ? level.getMinBuildHeight() : blockPos.getY()); k--) {
                        BlockPos blockPos2 = new BlockPos(pos.getX() + i, k, pos.getZ() + j);
                        BlockState blockState = level.getBlockState(blockPos2);
                        if (blockState.isCollisionShapeFullBlock(level, blockPos2) && (allowBedrock || !blockState.is(Blocks.BEDROCK))) {
                            blockPos = blockPos2;
                            break;
                        }
                    }
                }
            }
        }

        return blockPos == null ? pos : blockPos;
    }

    private static LevelChunk getChunk(Level level, Vec3 pos) {
        return level.getChunk(Mth.floor(pos.x / 16.0), Mth.floor(pos.z / 16.0));
    }

    @Nullable
    private static BlockPos findValidSpawnInChunk(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        BlockPos blockPos = new BlockPos(chunkPos.getMinBlockX(), 30, chunkPos.getMinBlockZ());
        int i = chunk.getHighestSectionPosition() + 16 - 1;
        BlockPos blockPos2 = new BlockPos(chunkPos.getMaxBlockX(), i, chunkPos.getMaxBlockZ());
        BlockPos blockPos3 = null;
        double d = 0.0;

        for (BlockPos blockPos4 : BlockPos.betweenClosed(blockPos, blockPos2)) {
            BlockState blockState = chunk.getBlockState(blockPos4);
            BlockPos blockPos5 = blockPos4.above();
            BlockPos blockPos6 = blockPos4.above(2);
            if (blockState.is(Blocks.END_STONE)
                && !chunk.getBlockState(blockPos5).isCollisionShapeFullBlock(chunk, blockPos5)
                && !chunk.getBlockState(blockPos6).isCollisionShapeFullBlock(chunk, blockPos6)) {
                double e = blockPos4.distToCenterSqr(0.0, 0.0, 0.0);
                if (blockPos3 == null || e < d) {
                    blockPos3 = blockPos4;
                    d = e;
                }
            }
        }

        return blockPos3;
    }

    private static void spawnGatewayPortal(ServerLevel level, BlockPos pos, EndGatewayConfiguration config) {
        Feature.END_GATEWAY.place(config, level, level.getChunkSource().getGenerator(), RandomSource.create(), pos);
    }

    @Override
    public boolean shouldRenderFace(Direction face) {
        return Block.shouldRenderFace(this.getBlockState(), this.level, this.getBlockPos(), face, this.getBlockPos().relative(face));
    }

    public int getParticleAmount() {
        int i = 0;

        for (Direction direction : Direction.values()) {
            i += this.shouldRenderFace(direction) ? 1 : 0;
        }

        return i;
    }

    public void setExitPosition(BlockPos exitPortal, boolean exactTeleport) {
        this.exactTeleport = exactTeleport;
        this.exitPortal = exitPortal;
        this.setChanged();
    }
}
