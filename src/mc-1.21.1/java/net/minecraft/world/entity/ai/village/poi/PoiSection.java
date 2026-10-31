package net.minecraft.world.entity.ai.village.poi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.VisibleForDebug;
import org.slf4j.Logger;

public class PoiSection {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Short2ObjectMap<PoiRecord> records = new Short2ObjectOpenHashMap<>();
    private final Map<Holder<PoiType>, Set<PoiRecord>> byType = Maps.newHashMap();
    private final Runnable setDirty;
    private boolean isValid;

    public static Codec<PoiSection> codec(Runnable executable) {
        return RecordCodecBuilder.<PoiSection>create(
                instance -> instance.group(
                        RecordCodecBuilder.point(executable),
                        Codec.BOOL.lenientOptionalFieldOf("Valid", false).forGetter(getter -> getter.isValid),
                        PoiRecord.codec(executable).listOf().fieldOf("Records").forGetter(getter -> ImmutableList.copyOf(getter.records.values()))
                    )
                    .apply(instance, PoiSection::new)
            )
            .orElseGet(Util.prefix("Failed to read POI section: ", LOGGER::error), () -> new PoiSection(executable, false, ImmutableList.of()));
    }

    public PoiSection(Runnable setDirty) {
        this(setDirty, true, ImmutableList.of());
    }

    private PoiSection(Runnable setDirty, boolean isValid, List<PoiRecord> records) {
        this.setDirty = setDirty;
        this.isValid = isValid;
        records.forEach(this::add);
    }

    public Stream<PoiRecord> getRecords(Predicate<Holder<PoiType>> typePredicate, PoiManager.Occupancy status) {
        return this.byType
            .entrySet()
            .stream()
            .filter(type -> typePredicate.test(type.getKey()))
            .flatMap(type -> type.getValue().stream())
            .filter(status.getTest());
    }

    public void add(BlockPos pos, Holder<PoiType> type) {
        if (this.add(new PoiRecord(pos, type, this.setDirty))) {
            LOGGER.debug("Added POI of type {} @ {}", type.getRegisteredName(), pos);
            this.setDirty.run();
        }
    }

    private boolean add(PoiRecord record) {
        BlockPos blockPos = record.getPos();
        Holder<PoiType> holder = record.getPoiType();
        short s = SectionPos.sectionRelativePos(blockPos);
        PoiRecord poiRecord = this.records.get(s);
        if (poiRecord != null) {
            if (holder.equals(poiRecord.getPoiType())) {
                return false;
            }

            Util.logAndPauseIfInIde("POI data mismatch: already registered at " + blockPos);
        }

        this.records.put(s, record);
        this.byType.computeIfAbsent(holder, holderx -> Sets.newHashSet()).add(record);
        return true;
    }

    public void remove(BlockPos pos) {
        PoiRecord poiRecord = this.records.remove(SectionPos.sectionRelativePos(pos));
        if (poiRecord == null) {
            LOGGER.error("POI data mismatch: never registered at {}", pos);
        } else {
            this.byType.get(poiRecord.getPoiType()).remove(poiRecord);
            LOGGER.debug("Removed POI of type {} @ {}", LogUtils.defer(poiRecord::getPoiType), LogUtils.defer(poiRecord::getPos));
            this.setDirty.run();
        }
    }

    @Deprecated
    @VisibleForDebug
    public int getFreeTickets(BlockPos pos) {
        return this.getPoiRecord(pos).map(PoiRecord::getFreeTickets).orElse(0);
    }

    public boolean release(BlockPos pos) {
        PoiRecord poiRecord = this.records.get(SectionPos.sectionRelativePos(pos));
        if (poiRecord == null) {
            throw (IllegalStateException)Util.pauseInIde(new IllegalStateException("POI never registered at " + pos));
        }

        boolean bl = poiRecord.releaseTicket();
        this.setDirty.run();
        return bl;
    }

    public boolean exists(BlockPos pos, Predicate<Holder<PoiType>> typePredicate) {
        return this.getType(pos).filter(typePredicate).isPresent();
    }

    public Optional<Holder<PoiType>> getType(BlockPos pos) {
        return this.getPoiRecord(pos).map(PoiRecord::getPoiType);
    }

    private Optional<PoiRecord> getPoiRecord(BlockPos pos) {
        return Optional.ofNullable(this.records.get(SectionPos.sectionRelativePos(pos)));
    }

    public void refresh(Consumer<BiConsumer<BlockPos, Holder<PoiType>>> posToTypeConsumer) {
        if (!this.isValid) {
            Short2ObjectMap<PoiRecord> short2ObjectMap = new Short2ObjectOpenHashMap<>(this.records);
            this.clear();
            posToTypeConsumer.accept((blockPos, holder) -> {
                short s = SectionPos.sectionRelativePos(blockPos);
                PoiRecord poiRecord = short2ObjectMap.computeIfAbsent(s, sx -> new PoiRecord(blockPos, holder, this.setDirty));
                this.add(poiRecord);
            });
            this.isValid = true;
            this.setDirty.run();
        }
    }

    private void clear() {
        this.records.clear();
        this.byType.clear();
    }

    boolean isValid() {
        return this.isValid;
    }
}
