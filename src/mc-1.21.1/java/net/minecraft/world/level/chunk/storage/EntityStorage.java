package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import org.slf4j.Logger;

public class EntityStorage implements EntityPersistentStorage<Entity> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ENTITIES_TAG = "Entities";
    private static final String POSITION_TAG = "Position";
    private final ServerLevel level;
    private final SimpleRegionStorage simpleRegionStorage;
    private final LongSet emptyChunks = new LongOpenHashSet();
    private final ProcessorMailbox<Runnable> entityDeserializerQueue;

    public EntityStorage(SimpleRegionStorage simpleRegionStorage, ServerLevel level, Executor executor) {
        this.simpleRegionStorage = simpleRegionStorage;
        this.level = level;
        this.entityDeserializerQueue = ProcessorMailbox.create(executor, "entity-deserializer");
    }

    @Override
    public CompletableFuture<ChunkEntities<Entity>> loadEntities(ChunkPos pos) {
        if (this.emptyChunks.contains(pos.toLong())) {
            return CompletableFuture.completedFuture(emptyChunk(pos));
        }

        CompletableFuture<Optional<CompoundTag>> completableFuture = this.simpleRegionStorage.read(pos);
        this.reportLoadFailureIfPresent(completableFuture, pos);
        return completableFuture.thenApplyAsync(entitiesTag -> {
            if (entitiesTag.isEmpty()) {
                this.emptyChunks.add(pos.toLong());
                return emptyChunk(pos);
            }

            try {
                ChunkPos chunkPos2 = readChunkPos(entitiesTag.get());
                if (!Objects.equals(pos, chunkPos2)) {
                    LOGGER.error("Chunk file at {} is in the wrong location. (Expected {}, got {})", pos, pos, chunkPos2);
                    this.level.getServer().reportMisplacedChunk(chunkPos2, pos, this.simpleRegionStorage.storageInfo());
                }
            } catch (Exception exception) {
                LOGGER.warn("Failed to parse chunk {} position info", pos, exception);
                this.level.getServer().reportChunkLoadFailure(exception, this.simpleRegionStorage.storageInfo(), pos);
            }

            CompoundTag compoundTag = this.simpleRegionStorage.upgradeChunkTag(entitiesTag.get(), -1);
            ListTag listTag = compoundTag.getList("Entities", 10);
            List<Entity> list = EntityType.loadEntitiesRecursive(listTag, this.level).collect(ImmutableList.toImmutableList());
            return new ChunkEntities<>(pos, list);
        }, this.entityDeserializerQueue::tell);
    }

    private static ChunkPos readChunkPos(CompoundTag tag) {
        int[] is = tag.getIntArray("Position");
        return new ChunkPos(is[0], is[1]);
    }

    private static void writeChunkPos(CompoundTag tag, ChunkPos pos) {
        tag.put("Position", new IntArrayTag(new int[]{pos.x, pos.z}));
    }

    private static ChunkEntities<Entity> emptyChunk(ChunkPos pos) {
        return new ChunkEntities<>(pos, ImmutableList.of());
    }

    @Override
    public void storeEntities(ChunkEntities<Entity> entities) {
        ChunkPos chunkPos = entities.getPos();
        if (entities.isEmpty()) {
            if (this.emptyChunks.add(chunkPos.toLong())) {
                this.reportSaveFailureIfPresent(this.simpleRegionStorage.write(chunkPos, null), chunkPos);
            }
        } else {
            ListTag listTag = new ListTag();
            entities.getEntities().forEach(entity -> {
                CompoundTag compoundTagx = new CompoundTag();
                if (entity.save(compoundTagx)) {
                    listTag.add(compoundTagx);
                }
            });
            CompoundTag compoundTag = NbtUtils.addCurrentDataVersion(new CompoundTag());
            compoundTag.put("Entities", listTag);
            writeChunkPos(compoundTag, chunkPos);
            this.reportSaveFailureIfPresent(this.simpleRegionStorage.write(chunkPos, compoundTag), chunkPos);
            this.emptyChunks.remove(chunkPos.toLong());
        }
    }

    private void reportSaveFailureIfPresent(CompletableFuture<?> future, ChunkPos pos) {
        future.exceptionally(throwable -> {
            LOGGER.error("Failed to store entity chunk {}", pos, throwable);
            this.level.getServer().reportChunkSaveFailure(throwable, this.simpleRegionStorage.storageInfo(), pos);
            return null;
        });
    }

    private void reportLoadFailureIfPresent(CompletableFuture<?> future, ChunkPos pos) {
        future.exceptionally(throwable -> {
            LOGGER.error("Failed to load entity chunk {}", pos, throwable);
            this.level.getServer().reportChunkLoadFailure(throwable, this.simpleRegionStorage.storageInfo(), pos);
            return null;
        });
    }

    @Override
    public void flush(boolean synchronize) {
        this.simpleRegionStorage.synchronize(synchronize).join();
        this.entityDeserializerQueue.runAll();
    }

    @Override
    public void close() throws IOException {
        this.simpleRegionStorage.close();
    }
}
