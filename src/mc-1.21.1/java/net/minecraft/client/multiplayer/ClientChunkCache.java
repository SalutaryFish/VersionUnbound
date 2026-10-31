package net.minecraft.client.multiplayer;

import com.mojang.logging.LogUtils;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.slf4j.Logger;

public class ClientChunkCache extends ChunkSource {
    static final Logger LOGGER = LogUtils.getLogger();
    private final LevelChunk emptyChunk;
    private final LevelLightEngine lightEngine;
    volatile ClientChunkCache.Storage storage;
    final ClientLevel level;

    public ClientChunkCache(ClientLevel level, int viewDistance) {
        this.level = level;
        this.emptyChunk = new EmptyLevelChunk(
            level, new ChunkPos(0, 0), level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS)
        );
        this.lightEngine = new LevelLightEngine(this, true, level.dimensionType().hasSkyLight());
        this.storage = new ClientChunkCache.Storage(calculateStorageRange(viewDistance));
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    private static boolean isValidChunk(@Nullable LevelChunk chunk, int x, int z) {
        if (chunk == null) {
            return false;
        }

        ChunkPos chunkPos = chunk.getPos();
        return chunkPos.x == x && chunkPos.z == z;
    }

    public void drop(ChunkPos chunkPos) {
        if (this.storage.inRange(chunkPos.x, chunkPos.z)) {
            int i = this.storage.getIndex(chunkPos.x, chunkPos.z);
            LevelChunk levelChunk = this.storage.getChunk(i);
            if (isValidChunk(levelChunk, chunkPos.x, chunkPos.z)) {
                this.storage.replace(i, levelChunk, null);
            }
        }
    }

    @Nullable
    public LevelChunk getChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        if (this.storage.inRange(x, z)) {
            LevelChunk levelChunk = this.storage.getChunk(this.storage.getIndex(x, z));
            if (isValidChunk(levelChunk, x, z)) {
                return levelChunk;
            }
        }

        return requireChunk ? this.emptyChunk : null;
    }

    @Override
    public BlockGetter getLevel() {
        return this.level;
    }

    public void replaceBiomes(int x, int z, FriendlyByteBuf buffer) {
        if (!this.storage.inRange(x, z)) {
            LOGGER.warn("Ignoring chunk since it's not in the view range: {}, {}", x, z);
        } else {
            int i = this.storage.getIndex(x, z);
            LevelChunk levelChunk = this.storage.chunks.get(i);
            if (!isValidChunk(levelChunk, x, z)) {
                LOGGER.warn("Ignoring chunk since it's not present: {}, {}", x, z);
            } else {
                levelChunk.replaceBiomes(buffer);
            }
        }
    }

    @Nullable
    public LevelChunk replaceWithPacketData(
        int x, int z, FriendlyByteBuf buffer, CompoundTag tag, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer
    ) {
        if (!this.storage.inRange(x, z)) {
            LOGGER.warn("Ignoring chunk since it's not in the view range: {}, {}", x, z);
            return null;
        }

        int i = this.storage.getIndex(x, z);
        LevelChunk levelChunk = this.storage.chunks.get(i);
        ChunkPos chunkPos = new ChunkPos(x, z);
        if (!isValidChunk(levelChunk, x, z)) {
            levelChunk = new LevelChunk(this.level, chunkPos);
            levelChunk.replaceWithPacketData(buffer, tag, consumer);
            this.storage.replace(i, levelChunk);
        } else {
            levelChunk.replaceWithPacketData(buffer, tag, consumer);
        }

        this.level.onChunkLoaded(chunkPos);
        return levelChunk;
    }

    @Override
    public void tick(BooleanSupplier hasTimeLeft, boolean tickChunks) {
    }

    public void updateViewCenter(int x, int z) {
        this.storage.viewCenterX = x;
        this.storage.viewCenterZ = z;
    }

    public void updateViewRadius(int viewDistance) {
        int i = this.storage.chunkRadius;
        int j = calculateStorageRange(viewDistance);
        if (i != j) {
            ClientChunkCache.Storage storage = new ClientChunkCache.Storage(j);
            storage.viewCenterX = this.storage.viewCenterX;
            storage.viewCenterZ = this.storage.viewCenterZ;

            for (int k = 0; k < this.storage.chunks.length(); k++) {
                LevelChunk levelChunk = this.storage.chunks.get(k);
                if (levelChunk != null) {
                    ChunkPos chunkPos = levelChunk.getPos();
                    if (storage.inRange(chunkPos.x, chunkPos.z)) {
                        storage.replace(storage.getIndex(chunkPos.x, chunkPos.z), levelChunk);
                    }
                }
            }

            this.storage = storage;
        }
    }

    private static int calculateStorageRange(int viewDistance) {
        return Math.max(2, viewDistance) + 3;
    }

    @Override
    public String gatherStats() {
        return this.storage.chunks.length() + ", " + this.getLoadedChunksCount();
    }

    @Override
    public int getLoadedChunksCount() {
        return this.storage.chunkCount;
    }

    @Override
    public void onLightUpdate(LightLayer type, SectionPos pos) {
        Minecraft.getInstance().levelRenderer.setSectionDirty(pos.x(), pos.y(), pos.z());
    }

    final class Storage {
        final AtomicReferenceArray<LevelChunk> chunks;
        final int chunkRadius;
        private final int viewRange;
        volatile int viewCenterX;
        volatile int viewCenterZ;
        int chunkCount;

        Storage(final int chunkRadius) {
            this.chunkRadius = chunkRadius;
            this.viewRange = chunkRadius * 2 + 1;
            this.chunks = new AtomicReferenceArray<>(this.viewRange * this.viewRange);
        }

        int getIndex(int x, int z) {
            return Math.floorMod(z, this.viewRange) * this.viewRange + Math.floorMod(x, this.viewRange);
        }

        protected void replace(int chunkIndex, @Nullable LevelChunk chunk) {
            LevelChunk levelChunk = this.chunks.getAndSet(chunkIndex, chunk);
            if (levelChunk != null) {
                this.chunkCount--;
                ClientChunkCache.this.level.unload(levelChunk);
            }

            if (chunk != null) {
                this.chunkCount++;
            }
        }

        protected LevelChunk replace(int chunkIndex, LevelChunk chunk, @Nullable LevelChunk replaceWith) {
            if (this.chunks.compareAndSet(chunkIndex, chunk, replaceWith) && replaceWith == null) {
                this.chunkCount--;
            }

            ClientChunkCache.this.level.unload(chunk);
            return chunk;
        }

        boolean inRange(int x, int z) {
            return Math.abs(x - this.viewCenterX) <= this.chunkRadius && Math.abs(z - this.viewCenterZ) <= this.chunkRadius;
        }

        @Nullable
        protected LevelChunk getChunk(int chunkIndex) {
            return this.chunks.get(chunkIndex);
        }

        private void dumpChunks(String filePath) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(filePath)) {
                int i = ClientChunkCache.this.storage.chunkRadius;

                for (int j = this.viewCenterZ - i; j <= this.viewCenterZ + i; j++) {
                    for (int k = this.viewCenterX - i; k <= this.viewCenterX + i; k++) {
                        LevelChunk levelChunk = ClientChunkCache.this.storage.chunks.get(ClientChunkCache.this.storage.getIndex(k, j));
                        if (levelChunk != null) {
                            ChunkPos chunkPos = levelChunk.getPos();
                            fileOutputStream.write((chunkPos.x + "\t" + chunkPos.z + "\t" + levelChunk.isEmpty() + "\n").getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
            } catch (IOException iOException) {
                ClientChunkCache.LOGGER.error("Failed to dump chunks to file {}", filePath, iOException);
            }
        }
    }
}
