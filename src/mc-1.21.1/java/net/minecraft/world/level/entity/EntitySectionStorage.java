package net.minecraft.world.level.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.Objects;
import java.util.Spliterators;
import java.util.PrimitiveIterator.OfLong;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

public class EntitySectionStorage<T extends EntityAccess> {
    private final Class<T> entityClass;
    private final Long2ObjectFunction<Visibility> intialSectionVisibility;
    private final Long2ObjectMap<EntitySection<T>> sections = new Long2ObjectOpenHashMap<>();
    private final LongSortedSet sectionIds = new LongAVLTreeSet();

    public EntitySectionStorage(Class<T> entityClass, Long2ObjectFunction<Visibility> initialSectionVisibility) {
        this.entityClass = entityClass;
        this.intialSectionVisibility = initialSectionVisibility;
    }

    public void forEachAccessibleNonEmptySection(AABB boundingBox, AbortableIterationConsumer<EntitySection<T>> consumer) {
        int i = 2;
        int j = SectionPos.posToSectionCoord(boundingBox.minX - 2.0);
        int k = SectionPos.posToSectionCoord(boundingBox.minY - 4.0);
        int l = SectionPos.posToSectionCoord(boundingBox.minZ - 2.0);
        int m = SectionPos.posToSectionCoord(boundingBox.maxX + 2.0);
        int n = SectionPos.posToSectionCoord(boundingBox.maxY + 0.0);
        int o = SectionPos.posToSectionCoord(boundingBox.maxZ + 2.0);

        for (int p = j; p <= m; p++) {
            long q = SectionPos.asLong(p, 0, 0);
            long r = SectionPos.asLong(p, -1, -1);
            LongIterator longIterator = this.sectionIds.subSet(q, r + 1L).iterator();

            while (longIterator.hasNext()) {
                long s = longIterator.nextLong();
                int t = SectionPos.y(s);
                int u = SectionPos.z(s);
                if (t >= k && t <= n && u >= l && u <= o) {
                    EntitySection<T> entitySection = this.sections.get(s);
                    if (entitySection != null
                        && !entitySection.isEmpty()
                        && entitySection.getStatus().isAccessible()
                        && consumer.accept(entitySection).shouldAbort()) {
                        return;
                    }
                }
            }
        }
    }

    public LongStream getExistingSectionPositionsInChunk(long pos) {
        int i = ChunkPos.getX(pos);
        int j = ChunkPos.getZ(pos);
        LongSortedSet longSortedSet = this.getChunkSections(i, j);
        if (longSortedSet.isEmpty()) {
            return LongStream.empty();
        }

        OfLong ofLong = longSortedSet.iterator();
        return StreamSupport.longStream(Spliterators.spliteratorUnknownSize(ofLong, 1301), false);
    }

    private LongSortedSet getChunkSections(int x, int z) {
        long l = SectionPos.asLong(x, 0, z);
        long m = SectionPos.asLong(x, -1, z);
        return this.sectionIds.subSet(l, m + 1L);
    }

    public Stream<EntitySection<T>> getExistingSectionsInChunk(long pos) {
        return this.getExistingSectionPositionsInChunk(pos).mapToObj(this.sections::get).filter(Objects::nonNull);
    }

    private static long getChunkKeyFromSectionKey(long pos) {
        return ChunkPos.asLong(SectionPos.x(pos), SectionPos.z(pos));
    }

    public EntitySection<T> getOrCreateSection(long sectionPos) {
        return this.sections.computeIfAbsent(sectionPos, this::createSection);
    }

    @Nullable
    public EntitySection<T> getSection(long sectionPos) {
        return this.sections.get(sectionPos);
    }

    private EntitySection<T> createSection(long sectionPos) {
        long l = getChunkKeyFromSectionKey(sectionPos);
        Visibility visibility = this.intialSectionVisibility.get(l);
        this.sectionIds.add(sectionPos);
        return new EntitySection<>(this.entityClass, visibility);
    }

    public LongSet getAllChunksWithExistingSections() {
        LongSet longSet = new LongOpenHashSet();
        this.sections.keySet().forEach(sectionId -> longSet.add(getChunkKeyFromSectionKey(sectionId)));
        return longSet;
    }

    public void getEntities(AABB bounds, AbortableIterationConsumer<T> consumer) {
        this.forEachAccessibleNonEmptySection(bounds, section -> section.getEntities(bounds, consumer));
    }

    public <U extends T> void getEntities(EntityTypeTest<T, U> test, AABB bounds, AbortableIterationConsumer<U> consumer) {
        this.forEachAccessibleNonEmptySection(bounds, section -> section.getEntities(test, bounds, consumer));
    }

    public void remove(long sectionId) {
        this.sections.remove(sectionId);
        this.sectionIds.remove(sectionId);
    }

    @VisibleForDebug
    public int count() {
        return this.sectionIds.size();
    }
}
