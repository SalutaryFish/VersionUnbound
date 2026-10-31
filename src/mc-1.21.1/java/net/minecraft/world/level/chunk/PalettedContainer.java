package net.minecraft.world.level.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import javax.annotation.Nullable;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.util.BitStorage;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.util.ZeroBitStorage;

public class PalettedContainer<T> implements PaletteResize<T>, PalettedContainerRO<T> {
    private static final int MIN_PALETTE_BITS = 0;
    private final PaletteResize<T> dummyPaletteResize = (i, object) -> 0;
    private final IdMap<T> registry;
    private volatile PalettedContainer.Data<T> data;
    private final PalettedContainer.Strategy strategy;
    private final ThreadingDetector threadingDetector = new ThreadingDetector("PalettedContainer");

    public void acquire() {
        this.threadingDetector.checkAndLock();
    }

    public void release() {
        this.threadingDetector.checkAndUnlock();
    }

    public static <T> Codec<PalettedContainer<T>> codecRW(IdMap<T> registry, Codec<T> codec, PalettedContainer.Strategy strategy, T value) {
        PalettedContainerRO.Unpacker<T, PalettedContainer<T>> unpacker = PalettedContainer::unpack;
        return codec(registry, codec, strategy, value, unpacker);
    }

    public static <T> Codec<PalettedContainerRO<T>> codecRO(IdMap<T> registry, Codec<T> codec, PalettedContainer.Strategy strategy, T value) {
        PalettedContainerRO.Unpacker<T, PalettedContainerRO<T>> unpacker = (idMap, strategyx, packedData) -> unpack(idMap, strategyx, packedData)
            .map(container -> (PalettedContainerRO<T>)container);
        return codec(registry, codec, strategy, value, unpacker);
    }

    private static <T, C extends PalettedContainerRO<T>> Codec<C> codec(
        IdMap<T> registry, Codec<T> codec, PalettedContainer.Strategy strategy, T value, PalettedContainerRO.Unpacker<T, C> unpacker
    ) {
        return RecordCodecBuilder.<PalettedContainerRO.PackedData>create(
                instance -> instance.group(
                        codec.mapResult(ExtraCodecs.orElsePartial(value)).listOf().fieldOf("palette").forGetter(PalettedContainerRO.PackedData::paletteEntries),
                        Codec.LONG_STREAM.lenientOptionalFieldOf("data").forGetter(PalettedContainerRO.PackedData::storage)
                    )
                    .apply(instance, PalettedContainerRO.PackedData::new)
            )
            .comapFlatMap(
                packedData -> unpacker.read(registry, strategy, (PalettedContainerRO.PackedData<T>)packedData), container -> container.pack(registry, strategy)
            );
    }

    public PalettedContainer(
        IdMap<T> registry, PalettedContainer.Strategy strategy, PalettedContainer.Configuration<T> configuration, BitStorage storage, List<T> values
    ) {
        this.registry = registry;
        this.strategy = strategy;
        this.data = new PalettedContainer.Data<>(configuration, storage, configuration.factory().create(configuration.bits(), registry, this, values));
    }

    private PalettedContainer(IdMap<T> registry, PalettedContainer.Strategy strategy, PalettedContainer.Data<T> data) {
        this.registry = registry;
        this.strategy = strategy;
        this.data = data;
    }

    public PalettedContainer(IdMap<T> registry, T palette, PalettedContainer.Strategy strategy) {
        this.strategy = strategy;
        this.registry = registry;
        this.data = this.createOrReuseData(null, 0);
        this.data.palette.idFor(palette);
    }

    private PalettedContainer.Data<T> createOrReuseData(@Nullable PalettedContainer.Data<T> data, int id) {
        PalettedContainer.Configuration<T> configuration = this.strategy.getConfiguration(this.registry, id);
        return data != null && configuration.equals(data.configuration()) ? data : configuration.createData(this.registry, this, this.strategy.size());
    }

    /**
     * Called when the underlying palette needs to resize itself to support additional objects.
     * @return The new integer mapping for the object added.
     *
     * @param bits The new palette size, in bits.
     */
    @Override
    public int onResize(int bits, T objectAdded) {
        PalettedContainer.Data<T> data = this.data;
        PalettedContainer.Data<T> data2 = this.createOrReuseData(data, bits);
        data2.copyFrom(data.palette, data.storage);
        this.data = data2;
        return data2.palette.idFor(objectAdded);
    }

    public T getAndSet(int x, int y, int z, T state) {
        this.acquire();

        try {
            return this.getAndSet(this.strategy.getIndex(x, y, z), state);
        } finally {
            this.release();
        }
    }

    public T getAndSetUnchecked(int x, int y, int z, T state) {
        return this.getAndSet(this.strategy.getIndex(x, y, z), state);
    }

    private T getAndSet(int index, T state) {
        int i = this.data.palette.idFor(state);
        int j = this.data.storage.getAndSet(index, i);
        return this.data.palette.valueFor(j);
    }

    public void set(int x, int y, int z, T state) {
        this.acquire();

        try {
            this.set(this.strategy.getIndex(x, y, z), state);
        } finally {
            this.release();
        }
    }

    private void set(int index, T state) {
        int i = this.data.palette.idFor(state);
        this.data.storage.set(index, i);
    }

    @Override
    public T get(int x, int y, int z) {
        return this.get(this.strategy.getIndex(x, y, z));
    }

    protected T get(int index) {
        PalettedContainer.Data<T> data = this.data;
        return data.palette.valueFor(data.storage.get(index));
    }

    @Override
    public void getAll(Consumer<T> consumer) {
        Palette<T> palette = this.data.palette();
        IntSet intSet = new IntArraySet();
        this.data.storage.getAll(intSet::add);
        intSet.forEach(id -> consumer.accept(palette.valueFor(id)));
    }

    public void read(FriendlyByteBuf buffer) {
        this.acquire();

        try {
            int i = buffer.readByte();
            PalettedContainer.Data<T> data = this.createOrReuseData(this.data, i);
            data.palette.read(buffer);
            buffer.readLongArray(data.storage.getRaw());
            this.data = data;
        } finally {
            this.release();
        }
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        this.acquire();

        try {
            this.data.write(buffer);
        } finally {
            this.release();
        }
    }

    private static <T> DataResult<PalettedContainer<T>> unpack(
        IdMap<T> registry, PalettedContainer.Strategy strategy, PalettedContainerRO.PackedData<T> packedData
    ) {
        List<T> list = packedData.paletteEntries();
        int i = strategy.size();
        int j = strategy.calculateBitsForSerialization(registry, list.size());
        PalettedContainer.Configuration<T> configuration = strategy.getConfiguration(registry, j);
        BitStorage bitStorage;
        if (j == 0) {
            bitStorage = new ZeroBitStorage(i);
        } else {
            Optional<LongStream> optional = packedData.storage();
            if (optional.isEmpty()) {
                return DataResult.error(() -> "Missing values for non-zero storage");
            }

            long[] ls = optional.get().toArray();

            try {
                if (configuration.factory() == PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY) {
                    Palette<T> palette = new HashMapPalette<>(registry, j, (ix, object) -> 0, list);
                    SimpleBitStorage simpleBitStorage = new SimpleBitStorage(j, i, ls);
                    int[] is = new int[i];
                    simpleBitStorage.unpack(is);
                    swapPalette(is, id -> registry.getId(palette.valueFor(id)));
                    bitStorage = new SimpleBitStorage(configuration.bits(), i, is);
                } else {
                    bitStorage = new SimpleBitStorage(configuration.bits(), i, ls);
                }
            } catch (SimpleBitStorage.InitializationException initializationException) {
                return DataResult.error(() -> "Failed to read PalettedContainer: " + initializationException.getMessage());
            }
        }

        return DataResult.success(new PalettedContainer<>(registry, strategy, configuration, bitStorage, list));
    }

    @Override
    public PalettedContainerRO.PackedData<T> pack(IdMap<T> registry, PalettedContainer.Strategy strategy) {
        this.acquire();

        try {
            HashMapPalette<T> hashMapPalette = new HashMapPalette<>(registry, this.data.storage.getBits(), this.dummyPaletteResize);
            int i = strategy.size();
            int[] is = new int[i];
            this.data.storage.unpack(is);
            swapPalette(is, id -> hashMapPalette.idFor(this.data.palette.valueFor(id)));
            int j = strategy.calculateBitsForSerialization(registry, hashMapPalette.getSize());
            Optional<LongStream> optional;
            if (j != 0) {
                SimpleBitStorage simpleBitStorage = new SimpleBitStorage(j, i, is);
                optional = Optional.of(Arrays.stream(simpleBitStorage.getRaw()));
            } else {
                optional = Optional.empty();
            }

            return new PalettedContainerRO.PackedData<>(hashMapPalette.getEntries(), optional);
        } finally {
            this.release();
        }
    }

    private static <T> void swapPalette(int[] bits, IntUnaryOperator operator) {
        int i = -1;
        int j = -1;

        for (int k = 0; k < bits.length; k++) {
            int l = bits[k];
            if (l != i) {
                i = l;
                j = operator.applyAsInt(l);
            }

            bits[k] = j;
        }
    }

    @Override
    public int getSerializedSize() {
        return this.data.getSerializedSize();
    }

    @Override
    public boolean maybeHas(Predicate<T> predicate) {
        return this.data.palette.maybeHas(predicate);
    }

    public PalettedContainer<T> copy() {
        return new PalettedContainer<>(this.registry, this.strategy, this.data.copy());
    }

    @Override
    public PalettedContainer<T> recreate() {
        return new PalettedContainer<>(this.registry, this.data.palette.valueFor(0), this.strategy);
    }

    /**
     * Counts the number of instances of each state in the container.
     * The provided consumer is invoked for each state with the number of instances.
     */
    @Override
    public void count(PalettedContainer.CountConsumer<T> countConsumer) {
        if (this.data.palette.getSize() == 1) {
            countConsumer.accept(this.data.palette.valueFor(0), this.data.storage.getSize());
        } else {
            Int2IntOpenHashMap int2IntOpenHashMap = new Int2IntOpenHashMap();
            this.data.storage.getAll(id -> int2IntOpenHashMap.addTo(id, 1));
            int2IntOpenHashMap.int2IntEntrySet()
                .forEach(idEntry -> countConsumer.accept(this.data.palette.valueFor(idEntry.getIntKey()), idEntry.getIntValue()));
        }
    }

    record Configuration<T>(Palette.Factory factory, int bits) {
        public PalettedContainer.Data<T> createData(IdMap<T> registry, PaletteResize<T> paletteResize, int size) {
            BitStorage bitStorage = this.bits == 0 ? new ZeroBitStorage(size) : new SimpleBitStorage(this.bits, size);
            Palette<T> palette = this.factory.create(this.bits, registry, paletteResize, List.of());
            return new PalettedContainer.Data<>(this, bitStorage, palette);
        }
    }

    @FunctionalInterface
    public interface CountConsumer<T> {
        void accept(T var1, int var2);
    }

    record Data<T>(PalettedContainer.Configuration<T> configuration, BitStorage storage, Palette<T> palette) {
        public void copyFrom(Palette<T> palette, BitStorage bitStorage) {
            for (int i = 0; i < bitStorage.getSize(); i++) {
                T object = palette.valueFor(bitStorage.get(i));
                this.storage.set(i, this.palette.idFor(object));
            }
        }

        public int getSerializedSize() {
            return 1 + this.palette.getSerializedSize() + VarInt.getByteSize(this.storage.getRaw().length) + this.storage.getRaw().length * 8;
        }

        public void write(FriendlyByteBuf buffer) {
            buffer.writeByte(this.storage.getBits());
            this.palette.write(buffer);
            buffer.writeLongArray(this.storage.getRaw());
        }

        public PalettedContainer.Data<T> copy() {
            return new PalettedContainer.Data<>(this.configuration, this.storage.copy(), this.palette.copy());
        }
    }

    public abstract static class Strategy {
        public static final Palette.Factory SINGLE_VALUE_PALETTE_FACTORY = SingleValuePalette::create;
        public static final Palette.Factory LINEAR_PALETTE_FACTORY = LinearPalette::create;
        public static final Palette.Factory HASHMAP_PALETTE_FACTORY = HashMapPalette::create;
        static final Palette.Factory GLOBAL_PALETTE_FACTORY = GlobalPalette::create;
        public static final PalettedContainer.Strategy SECTION_STATES = new PalettedContainer.Strategy(4) {
            @Override
            public <A> PalettedContainer.Configuration<A> getConfiguration(IdMap<A> idMap, int i) {
                return switch (i) {
                    case 0 -> new PalettedContainer.Configuration(SINGLE_VALUE_PALETTE_FACTORY, i);
                    case 1, 2, 3, 4 -> new PalettedContainer.Configuration(LINEAR_PALETTE_FACTORY, 4);
                    case 5, 6, 7, 8 -> new PalettedContainer.Configuration(HASHMAP_PALETTE_FACTORY, i);
                    default -> new PalettedContainer.Configuration(PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY, Mth.ceillog2(idMap.size()));
                };
            }
        };
        public static final PalettedContainer.Strategy SECTION_BIOMES = new PalettedContainer.Strategy(2) {
            @Override
            public <A> PalettedContainer.Configuration<A> getConfiguration(IdMap<A> idMap, int i) {
                return switch (i) {
                    case 0 -> new PalettedContainer.Configuration(SINGLE_VALUE_PALETTE_FACTORY, i);
                    case 1, 2, 3 -> new PalettedContainer.Configuration(LINEAR_PALETTE_FACTORY, i);
                    default -> new PalettedContainer.Configuration(PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY, Mth.ceillog2(idMap.size()));
                };
            }
        };
        private final int sizeBits;

        Strategy(int sizeBits) {
            this.sizeBits = sizeBits;
        }

        public int size() {
            return 1 << this.sizeBits * 3;
        }

        public int getIndex(int x, int y, int z) {
            return (y << this.sizeBits | z) << this.sizeBits | x;
        }

        public abstract <A> PalettedContainer.Configuration<A> getConfiguration(IdMap<A> var1, int var2);

        <A> int calculateBitsForSerialization(IdMap<A> registry, int size) {
            int i = Mth.ceillog2(size);
            PalettedContainer.Configuration<A> configuration = this.getConfiguration(registry, i);
            return configuration.factory() == GLOBAL_PALETTE_FACTORY ? i : configuration.bits();
        }
    }
}
