package net.minecraft.server.level;

import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class ChunkHolder extends GenerationChunkHolder {
    public static final ChunkResult<LevelChunk> UNLOADED_LEVEL_CHUNK = ChunkResult.error("Unloaded level chunk");
    private static final CompletableFuture<ChunkResult<LevelChunk>> UNLOADED_LEVEL_CHUNK_FUTURE = CompletableFuture.completedFuture(UNLOADED_LEVEL_CHUNK);
    private final LevelHeightAccessor levelHeightAccessor;
    /**
     * A future that returns the chunk if it is a border chunk, {@link net.minecraft.world.server.ChunkHolder.ChunkLoadingFailure#UNLOADED} otherwise.
     */
    private volatile CompletableFuture<ChunkResult<LevelChunk>> fullChunkFuture = UNLOADED_LEVEL_CHUNK_FUTURE;
    /**
     * A future that returns the chunk if it is a ticking chunk, {@link net.minecraft.world.server.ChunkHolder.ChunkLoadingFailure#UNLOADED} otherwise.
     */
    private volatile CompletableFuture<ChunkResult<LevelChunk>> tickingChunkFuture = UNLOADED_LEVEL_CHUNK_FUTURE;
    /**
     * A future that returns the chunk if it is an entity ticking chunk, {@link net.minecraft.world.server.ChunkHolder.ChunkLoadingFailure#UNLOADED} otherwise.
     */
    private volatile CompletableFuture<ChunkResult<LevelChunk>> entityTickingChunkFuture = UNLOADED_LEVEL_CHUNK_FUTURE;
    private int oldTicketLevel;
    private int ticketLevel;
    private int queueLevel;
    private boolean hasChangedSections;
    private final ShortSet[] changedBlocksPerSection;
    private final BitSet blockChangedLightSectionFilter = new BitSet();
    private final BitSet skyChangedLightSectionFilter = new BitSet();
    private final LevelLightEngine lightEngine;
    private final ChunkHolder.LevelChangeListener onLevelChange;
    private final ChunkHolder.PlayerProvider playerProvider;
    private boolean wasAccessibleSinceLastSave;
    private CompletableFuture<?> pendingFullStateConfirmation = CompletableFuture.completedFuture(null);
    private CompletableFuture<?> sendSync = CompletableFuture.completedFuture(null);
    private CompletableFuture<?> saveSync = CompletableFuture.completedFuture(null);

    public ChunkHolder(
        ChunkPos pos,
        int ticketLevel,
        LevelHeightAccessor levelHeightAccessor,
        LevelLightEngine lightEngine,
        ChunkHolder.LevelChangeListener onLevelChange,
        ChunkHolder.PlayerProvider playerProvider
    ) {
        super(pos);
        this.levelHeightAccessor = levelHeightAccessor;
        this.lightEngine = lightEngine;
        this.onLevelChange = onLevelChange;
        this.playerProvider = playerProvider;
        this.oldTicketLevel = ChunkLevel.MAX_LEVEL + 1;
        this.ticketLevel = this.oldTicketLevel;
        this.queueLevel = this.oldTicketLevel;
        this.setTicketLevel(ticketLevel);
        this.changedBlocksPerSection = new ShortSet[levelHeightAccessor.getSectionsCount()];
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getTickingChunkFuture() {
        return this.tickingChunkFuture;
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getEntityTickingChunkFuture() {
        return this.entityTickingChunkFuture;
    }

    public CompletableFuture<ChunkResult<LevelChunk>> getFullChunkFuture() {
        return this.fullChunkFuture;
    }

    @Nullable
    public LevelChunk getTickingChunk() {
        return this.getTickingChunkFuture().getNow(UNLOADED_LEVEL_CHUNK).orElse(null);
    }

    @Nullable
    public LevelChunk getChunkToSend() {
        return !this.sendSync.isDone() ? null : this.getTickingChunk();
    }

    public CompletableFuture<?> getSendSyncFuture() {
        return this.sendSync;
    }

    public void addSendDependency(CompletableFuture<?> dependency) {
        if (this.sendSync.isDone()) {
            this.sendSync = dependency;
        } else {
            this.sendSync = this.sendSync.thenCombine((CompletionStage<? extends Object>)dependency, (object, object2) -> null);
        }
    }

    public CompletableFuture<?> getSaveSyncFuture() {
        return this.saveSync;
    }

    public boolean isReadyForSaving() {
        return this.getGenerationRefCount() == 0 && this.saveSync.isDone();
    }

    private void addSaveDependency(CompletableFuture<?> dependency) {
        if (this.saveSync.isDone()) {
            this.saveSync = dependency;
        } else {
            this.saveSync = this.saveSync.thenCombine((CompletionStage<? extends Object>)dependency, (object, object2) -> null);
        }
    }

    public void blockChanged(BlockPos pos) {
        LevelChunk levelChunk = this.getTickingChunk();
        if (levelChunk != null) {
            int i = this.levelHeightAccessor.getSectionIndex(pos.getY());
            if (this.changedBlocksPerSection[i] == null) {
                this.hasChangedSections = true;
                this.changedBlocksPerSection[i] = new ShortOpenHashSet();
            }

            this.changedBlocksPerSection[i].add(SectionPos.sectionRelativePos(pos));
        }
    }

    public void sectionLightChanged(LightLayer type, int sectionY) {
        ChunkAccess chunkAccess = this.getChunkIfPresent(ChunkStatus.INITIALIZE_LIGHT);
        if (chunkAccess != null) {
            chunkAccess.setUnsaved(true);
            LevelChunk levelChunk = this.getTickingChunk();
            if (levelChunk != null) {
                int i = this.lightEngine.getMinLightSection();
                int j = this.lightEngine.getMaxLightSection();
                if (sectionY >= i && sectionY <= j) {
                    int k = sectionY - i;
                    if (type == LightLayer.SKY) {
                        this.skyChangedLightSectionFilter.set(k);
                    } else {
                        this.blockChangedLightSectionFilter.set(k);
                    }
                }
            }
        }
    }

    public void broadcastChanges(LevelChunk chunk) {
        if (this.hasChangedSections || !this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty()) {
            Level level = chunk.getLevel();
            if (!this.skyChangedLightSectionFilter.isEmpty() || !this.blockChangedLightSectionFilter.isEmpty()) {
                List<ServerPlayer> list = this.playerProvider.getPlayers(this.pos, true);
                if (!list.isEmpty()) {
                    ClientboundLightUpdatePacket clientboundLightUpdatePacket = new ClientboundLightUpdatePacket(
                        chunk.getPos(), this.lightEngine, this.skyChangedLightSectionFilter, this.blockChangedLightSectionFilter
                    );
                    this.broadcast(list, clientboundLightUpdatePacket);
                }

                this.skyChangedLightSectionFilter.clear();
                this.blockChangedLightSectionFilter.clear();
            }

            if (this.hasChangedSections) {
                List<ServerPlayer> list = this.playerProvider.getPlayers(this.pos, false);

                for (int i = 0; i < this.changedBlocksPerSection.length; i++) {
                    ShortSet shortSet = this.changedBlocksPerSection[i];
                    if (shortSet != null) {
                        this.changedBlocksPerSection[i] = null;
                        if (!list.isEmpty()) {
                            int j = this.levelHeightAccessor.getSectionYFromSectionIndex(i);
                            SectionPos sectionPos = SectionPos.of(chunk.getPos(), j);
                            if (shortSet.size() == 1) {
                                BlockPos blockPos = sectionPos.relativeToBlockPos(shortSet.iterator().nextShort());
                                BlockState blockState = level.getBlockState(blockPos);
                                this.broadcast(list, new ClientboundBlockUpdatePacket(blockPos, blockState));
                                this.broadcastBlockEntityIfNeeded(list, level, blockPos, blockState);
                            } else {
                                LevelChunkSection levelChunkSection = chunk.getSection(i);
                                ClientboundSectionBlocksUpdatePacket clientboundSectionBlocksUpdatePacket = new ClientboundSectionBlocksUpdatePacket(
                                    sectionPos, shortSet, levelChunkSection
                                );
                                this.broadcast(list, clientboundSectionBlocksUpdatePacket);
                                clientboundSectionBlocksUpdatePacket.runUpdates(
                                    (blockPos, blockState) -> this.broadcastBlockEntityIfNeeded(list, level, blockPos, blockState)
                                );
                            }
                        }
                    }
                }

                this.hasChangedSections = false;
            }
        }
    }

    private void broadcastBlockEntityIfNeeded(List<ServerPlayer> players, Level level, BlockPos pos, BlockState state) {
        if (state.hasBlockEntity()) {
            this.broadcastBlockEntity(players, level, pos);
        }
    }

    private void broadcastBlockEntity(List<ServerPlayer> players, Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            Packet<?> packet = blockEntity.getUpdatePacket();
            if (packet != null) {
                this.broadcast(players, packet);
            }
        }
    }

    private void broadcast(List<ServerPlayer> players, Packet<?> packet) {
        players.forEach(player -> player.connection.send(packet));
    }

    @Override
    public int getTicketLevel() {
        return this.ticketLevel;
    }

    @Override
    public int getQueueLevel() {
        return this.queueLevel;
    }

    private void setQueueLevel(int queueLevel) {
        this.queueLevel = queueLevel;
    }

    public void setTicketLevel(int level) {
        this.ticketLevel = level;
    }

    private void scheduleFullChunkPromotion(
        ChunkMap chunkMap, CompletableFuture<ChunkResult<LevelChunk>> future, Executor executor, FullChunkStatus fullChunkStatus
    ) {
        this.pendingFullStateConfirmation.cancel(false);
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        completableFuture.thenRunAsync(() -> chunkMap.onFullChunkStatusChange(this.pos, fullChunkStatus), executor);
        this.pendingFullStateConfirmation = completableFuture;
        future.thenAccept(chunkResult -> chunkResult.ifSuccess(levelChunk -> completableFuture.complete(null)));
    }

    private void demoteFullChunk(ChunkMap chunkMap, FullChunkStatus fullChunkStatus) {
        this.pendingFullStateConfirmation.cancel(false);
        chunkMap.onFullChunkStatusChange(this.pos, fullChunkStatus);
    }

    protected void updateFutures(ChunkMap chunkMap, Executor executor) {
        FullChunkStatus fullChunkStatus = ChunkLevel.fullStatus(this.oldTicketLevel);
        FullChunkStatus fullChunkStatus2 = ChunkLevel.fullStatus(this.ticketLevel);
        boolean bl = fullChunkStatus.isOrAfter(FullChunkStatus.FULL);
        boolean bl2 = fullChunkStatus2.isOrAfter(FullChunkStatus.FULL);
        this.wasAccessibleSinceLastSave |= bl2;
        if (!bl && bl2) {
            this.fullChunkFuture = chunkMap.prepareAccessibleChunk(this);
            this.scheduleFullChunkPromotion(chunkMap, this.fullChunkFuture, executor, FullChunkStatus.FULL);
            this.addSaveDependency(this.fullChunkFuture);
        }

        if (bl && !bl2) {
            this.fullChunkFuture.complete(UNLOADED_LEVEL_CHUNK);
            this.fullChunkFuture = UNLOADED_LEVEL_CHUNK_FUTURE;
        }

        boolean bl3 = fullChunkStatus.isOrAfter(FullChunkStatus.BLOCK_TICKING);
        boolean bl4 = fullChunkStatus2.isOrAfter(FullChunkStatus.BLOCK_TICKING);
        if (!bl3 && bl4) {
            this.tickingChunkFuture = chunkMap.prepareTickingChunk(this);
            this.scheduleFullChunkPromotion(chunkMap, this.tickingChunkFuture, executor, FullChunkStatus.BLOCK_TICKING);
            this.addSaveDependency(this.tickingChunkFuture);
        }

        if (bl3 && !bl4) {
            this.tickingChunkFuture.complete(UNLOADED_LEVEL_CHUNK);
            this.tickingChunkFuture = UNLOADED_LEVEL_CHUNK_FUTURE;
        }

        boolean bl5 = fullChunkStatus.isOrAfter(FullChunkStatus.ENTITY_TICKING);
        boolean bl6 = fullChunkStatus2.isOrAfter(FullChunkStatus.ENTITY_TICKING);
        if (!bl5 && bl6) {
            if (this.entityTickingChunkFuture != UNLOADED_LEVEL_CHUNK_FUTURE) {
                throw (IllegalStateException)Util.pauseInIde(new IllegalStateException());
            }

            this.entityTickingChunkFuture = chunkMap.prepareEntityTickingChunk(this);
            this.scheduleFullChunkPromotion(chunkMap, this.entityTickingChunkFuture, executor, FullChunkStatus.ENTITY_TICKING);
            this.addSaveDependency(this.entityTickingChunkFuture);
        }

        if (bl5 && !bl6) {
            this.entityTickingChunkFuture.complete(UNLOADED_LEVEL_CHUNK);
            this.entityTickingChunkFuture = UNLOADED_LEVEL_CHUNK_FUTURE;
        }

        if (!fullChunkStatus2.isOrAfter(fullChunkStatus)) {
            this.demoteFullChunk(chunkMap, fullChunkStatus2);
        }

        this.onLevelChange.onLevelChange(this.pos, this::getQueueLevel, this.ticketLevel, this::setQueueLevel);
        this.oldTicketLevel = this.ticketLevel;
    }

    public boolean wasAccessibleSinceLastSave() {
        return this.wasAccessibleSinceLastSave;
    }

    public void refreshAccessibility() {
        this.wasAccessibleSinceLastSave = ChunkLevel.fullStatus(this.ticketLevel).isOrAfter(FullChunkStatus.FULL);
    }

    @FunctionalInterface
    public interface LevelChangeListener {
        void onLevelChange(ChunkPos var1, IntSupplier var2, int var3, IntConsumer var4);
    }

    public interface PlayerProvider {
        /**
         * Returns the players tracking the given chunk.
         */
        List<ServerPlayer> getPlayers(ChunkPos var1, boolean var2);
    }
}
