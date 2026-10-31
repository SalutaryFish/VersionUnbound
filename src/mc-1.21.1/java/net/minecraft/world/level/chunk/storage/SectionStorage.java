package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.OptionalDynamic;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.slf4j.Logger;

public class SectionStorage<R> implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SECTIONS_TAG = "Sections";
    private final SimpleRegionStorage simpleRegionStorage;
    private final Long2ObjectMap<Optional<R>> storage = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet dirty = new LongLinkedOpenHashSet();
    private final Function<Runnable, Codec<R>> codec;
    private final Function<Runnable, R> factory;
    private final RegistryAccess registryAccess;
    private final ChunkIOErrorReporter errorReporter;
    protected final LevelHeightAccessor levelHeightAccessor;

    public SectionStorage(
        SimpleRegionStorage simpleRegionStorage,
        Function<Runnable, Codec<R>> codec,
        Function<Runnable, R> factory,
        RegistryAccess registryAccess,
        ChunkIOErrorReporter errorReporter,
        LevelHeightAccessor levelHeightAccessor
    ) {
        this.simpleRegionStorage = simpleRegionStorage;
        this.codec = codec;
        this.factory = factory;
        this.registryAccess = registryAccess;
        this.errorReporter = errorReporter;
        this.levelHeightAccessor = levelHeightAccessor;
    }

    protected void tick(BooleanSupplier aheadOfTime) {
        while (this.hasWork() && aheadOfTime.getAsBoolean()) {
            ChunkPos chunkPos = SectionPos.of(this.dirty.firstLong()).chunk();
            this.writeColumn(chunkPos);
        }
    }

    public boolean hasWork() {
        return !this.dirty.isEmpty();
    }

    @Nullable
    protected Optional<R> get(long sectionKey) {
        return this.storage.get(sectionKey);
    }

    protected Optional<R> getOrLoad(long sectionKey) {
        if (this.outsideStoredRange(sectionKey)) {
            return Optional.empty();
        } else {
            Optional<R> optional = this.get(sectionKey);
            if (optional != null) {
                return optional;
            } else {
                this.readColumn(SectionPos.of(sectionKey).chunk());
                optional = this.get(sectionKey);
                if (optional == null) {
                    throw (IllegalStateException)Util.pauseInIde(new IllegalStateException());
                } else {
                    return optional;
                }
            }
        }
    }

    protected boolean outsideStoredRange(long sectionKey) {
        int i = SectionPos.sectionToBlockCoord(SectionPos.y(sectionKey));
        return this.levelHeightAccessor.isOutsideBuildHeight(i);
    }

    protected R getOrCreate(long sectionKey) {
        if (this.outsideStoredRange(sectionKey)) {
            throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException("sectionPos out of bounds"));
        }

        Optional<R> optional = this.getOrLoad(sectionKey);
        if (optional.isPresent()) {
            return optional.get();
        }

        R object = this.factory.apply(() -> this.setDirty(sectionKey));
        this.storage.put(sectionKey, Optional.of(object));
        return object;
    }

    private void readColumn(ChunkPos chunkPos) {
        Optional<CompoundTag> optional = this.tryRead(chunkPos).join();
        RegistryOps<Tag> registryOps = this.registryAccess.createSerializationContext(NbtOps.INSTANCE);
        this.readColumn(chunkPos, registryOps, optional.orElse(null));
    }

    private CompletableFuture<Optional<CompoundTag>> tryRead(ChunkPos chunkPos) {
        return this.simpleRegionStorage.read(chunkPos).exceptionally(throwable2 -> {
            if (throwable2 instanceof IOException iOException) {
                LOGGER.error("Error reading chunk {} data from disk", chunkPos, iOException);
                this.errorReporter.reportChunkLoadFailure(iOException, this.simpleRegionStorage.storageInfo(), chunkPos);
                return Optional.empty();
            } else {
                throw new CompletionException(throwable2);
            }
        });
    }

    private void readColumn(ChunkPos chunkPos, RegistryOps<Tag> ops, @Nullable CompoundTag tag) {
        if (tag == null) {
            for (int i = this.levelHeightAccessor.getMinSection(); i < this.levelHeightAccessor.getMaxSection(); i++) {
                this.storage.put(getKey(chunkPos, i), Optional.empty());
            }
        } else {
            Dynamic<Tag> dynamic = new Dynamic<>(ops, tag);
            int j = getVersion(dynamic);
            int k = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            boolean bl = j != k;
            Dynamic<Tag> dynamic2 = this.simpleRegionStorage.upgradeChunkTag(dynamic, j);
            OptionalDynamic<Tag> optionalDynamic = dynamic2.get("Sections");

            for (int l = this.levelHeightAccessor.getMinSection(); l < this.levelHeightAccessor.getMaxSection(); l++) {
                long m = getKey(chunkPos, l);
                Optional<R> optional = optionalDynamic.get(Integer.toString(l))
                    .result()
                    .flatMap(sectionDynamic -> this.codec.apply(() -> this.setDirty(m)).parse((Dynamic<Tag>)sectionDynamic).resultOrPartial(LOGGER::error));
                this.storage.put(m, optional);
                optional.ifPresent(parsedValue -> {
                    this.onSectionLoad(m);
                    if (bl) {
                        this.setDirty(m);
                    }
                });
            }
        }
    }

    private void writeColumn(ChunkPos chunkPos) {
        RegistryOps<Tag> registryOps = this.registryAccess.createSerializationContext(NbtOps.INSTANCE);
        Dynamic<Tag> dynamic = this.writeColumn(chunkPos, registryOps);
        Tag tag = dynamic.getValue();
        if (tag instanceof CompoundTag) {
            this.simpleRegionStorage.write(chunkPos, (CompoundTag)tag).exceptionally(throwable -> {
                this.errorReporter.reportChunkSaveFailure(throwable, this.simpleRegionStorage.storageInfo(), chunkPos);
                return null;
            });
        } else {
            LOGGER.error("Expected compound tag, got {}", tag);
        }
    }

    private <T> Dynamic<T> writeColumn(ChunkPos chunkPos, DynamicOps<T> ops) {
        Map<T, T> map = Maps.newHashMap();

        for (int i = this.levelHeightAccessor.getMinSection(); i < this.levelHeightAccessor.getMaxSection(); i++) {
            long l = getKey(chunkPos, i);
            this.dirty.remove(l);
            Optional<R> optional = this.storage.get(l);
            if (optional != null && !optional.isEmpty()) {
                DataResult<T> dataResult = this.codec.apply(() -> this.setDirty(l)).encodeStart(ops, optional.get());
                String string = Integer.toString(i);
                dataResult.resultOrPartial(LOGGER::error).ifPresent(object -> map.put(ops.createString(string), (T)object));
            }
        }

        return new Dynamic<>(
            ops,
            ops.createMap(
                ImmutableMap.of(
                    ops.createString("Sections"),
                    ops.createMap(map),
                    ops.createString("DataVersion"),
                    ops.createInt(SharedConstants.getCurrentVersion().getDataVersion().getVersion())
                )
            )
        );
    }

    private static long getKey(ChunkPos chunkPos, int sectionY) {
        return SectionPos.asLong(chunkPos.x, sectionY, chunkPos.z);
    }

    protected void onSectionLoad(long sectionKey) {
    }

    protected void setDirty(long sectionPos) {
        Optional<R> optional = this.storage.get(sectionPos);
        if (optional != null && !optional.isEmpty()) {
            this.dirty.add(sectionPos);
        } else {
            LOGGER.warn("No data for position: {}", SectionPos.of(sectionPos));
        }
    }

    private static int getVersion(Dynamic<?> columnData) {
        return columnData.get("DataVersion").asInt(1945);
    }

    public void flush(ChunkPos chunkPos) {
        if (this.hasWork()) {
            for (int i = this.levelHeightAccessor.getMinSection(); i < this.levelHeightAccessor.getMaxSection(); i++) {
                long l = getKey(chunkPos, i);
                if (this.dirty.contains(l)) {
                    this.writeColumn(chunkPos);
                    return;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        this.simpleRegionStorage.close();
    }
}
