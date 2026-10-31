package net.minecraft.nbt;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.RecordBuilder.AbstractStringBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class NbtOps implements DynamicOps<Tag> {
    public static final NbtOps INSTANCE = new NbtOps();
    private static final String WRAPPER_MARKER = "";

    protected NbtOps() {
    }

    public Tag empty() {
        return EndTag.INSTANCE;
    }

    public <U> U convertTo(DynamicOps<U> ops, Tag tag) {
        return (U)(switch (tag.getId()) {
            case 0 -> (Object)ops.empty();
            case 1 -> (Object)ops.createByte(((NumericTag)tag).getAsByte());
            case 2 -> (Object)ops.createShort(((NumericTag)tag).getAsShort());
            case 3 -> (Object)ops.createInt(((NumericTag)tag).getAsInt());
            case 4 -> (Object)ops.createLong(((NumericTag)tag).getAsLong());
            case 5 -> (Object)ops.createFloat(((NumericTag)tag).getAsFloat());
            case 6 -> (Object)ops.createDouble(((NumericTag)tag).getAsDouble());
            case 7 -> (Object)ops.createByteList(ByteBuffer.wrap(((ByteArrayTag)tag).getAsByteArray()));
            case 8 -> (Object)ops.createString(tag.getAsString());
            case 9 -> (Object)this.convertList(ops, tag);
            case 10 -> (Object)this.convertMap(ops, tag);
            case 11 -> (Object)ops.createIntList(Arrays.stream(((IntArrayTag)tag).getAsIntArray()));
            case 12 -> (Object)ops.createLongList(Arrays.stream(((LongArrayTag)tag).getAsLongArray()));
            default -> throw new IllegalStateException("Unknown tag type: " + tag);
        });
    }

    public DataResult<Number> getNumberValue(Tag tag) {
        return tag instanceof NumericTag numericTag ? DataResult.success(numericTag.getAsNumber()) : DataResult.error(() -> "Not a number");
    }

    public Tag createNumeric(Number data) {
        return DoubleTag.valueOf(data.doubleValue());
    }

    public Tag createByte(byte data) {
        return ByteTag.valueOf(data);
    }

    public Tag createShort(short data) {
        return ShortTag.valueOf(data);
    }

    public Tag createInt(int data) {
        return IntTag.valueOf(data);
    }

    public Tag createLong(long data) {
        return LongTag.valueOf(data);
    }

    public Tag createFloat(float data) {
        return FloatTag.valueOf(data);
    }

    public Tag createDouble(double data) {
        return DoubleTag.valueOf(data);
    }

    public Tag createBoolean(boolean data) {
        return ByteTag.valueOf(data);
    }

    public DataResult<String> getStringValue(Tag tag) {
        return tag instanceof StringTag stringTag ? DataResult.success(stringTag.getAsString()) : DataResult.error(() -> "Not a string");
    }

    public Tag createString(String data) {
        return StringTag.valueOf(data);
    }

    public DataResult<Tag> mergeToList(Tag list, Tag tag) {
        return createCollector(list)
            .map(listCollector -> DataResult.success(listCollector.accept(tag).result()))
            .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + list, list));
    }

    public DataResult<Tag> mergeToList(Tag list, List<Tag> tags) {
        return createCollector(list)
            .map(listCollector -> DataResult.success(listCollector.acceptAll(tags).result()))
            .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + list, list));
    }

    public DataResult<Tag> mergeToMap(Tag map, Tag key, Tag value) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
        }

        if (!(key instanceof StringTag)) {
            return DataResult.error(() -> "key is not a string: " + key, map);
        }

        CompoundTag compoundTag2 = map instanceof CompoundTag compoundTag ? compoundTag.shallowCopy() : new CompoundTag();
        compoundTag2.put(key.getAsString(), value);
        return DataResult.success(compoundTag2);
    }

    public DataResult<Tag> mergeToMap(Tag map, MapLike<Tag> otherMap) {
        if (!(map instanceof CompoundTag) && !(map instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + map, map);
        }

        CompoundTag compoundTag2 = map instanceof CompoundTag compoundTag ? compoundTag.shallowCopy() : new CompoundTag();
        List<Tag> list = new ArrayList<>();
        otherMap.entries().forEach(pair -> {
            Tag tag = pair.getFirst();
            if (!(tag instanceof StringTag)) {
                list.add(tag);
            } else {
                compoundTag2.put(tag.getAsString(), pair.getSecond());
            }
        });
        return !list.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + list, compoundTag2) : DataResult.success(compoundTag2);
    }

    public DataResult<Tag> mergeToMap(Tag tag, Map<Tag, Tag> map) {
        if (!(tag instanceof CompoundTag) && !(tag instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + tag, tag);
        }

        CompoundTag compoundTag2 = tag instanceof CompoundTag compoundTag ? compoundTag.shallowCopy() : new CompoundTag();
        List<Tag> list = new ArrayList<>();

        for (Entry<Tag, Tag> entry : map.entrySet()) {
            Tag tag2 = entry.getKey();
            if (tag2 instanceof StringTag) {
                compoundTag2.put(tag2.getAsString(), entry.getValue());
            } else {
                list.add(tag2);
            }
        }

        return !list.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + list, compoundTag2) : DataResult.success(compoundTag2);
    }

    public DataResult<Stream<Pair<Tag, Tag>>> getMapValues(Tag map) {
        return map instanceof CompoundTag compoundTag
            ? DataResult.success(compoundTag.entrySet().stream().map(entry -> Pair.of(this.createString(entry.getKey()), entry.getValue())))
            : DataResult.error(() -> "Not a map: " + map);
    }

    public DataResult<Consumer<BiConsumer<Tag, Tag>>> getMapEntries(Tag map) {
        return map instanceof CompoundTag compoundTag ? DataResult.success(biConsumer -> {
            for (Entry<String, Tag> entry : compoundTag.entrySet()) {
                biConsumer.accept(this.createString(entry.getKey()), entry.getValue());
            }
        }) : DataResult.error(() -> "Not a map: " + map);
    }

    public DataResult<MapLike<Tag>> getMap(Tag map) {
        return map instanceof CompoundTag compoundTag ? DataResult.success(new MapLike<Tag>() {
            @Nullable
            public Tag get(Tag tag) {
                return compoundTag.get(tag.getAsString());
            }

            @Nullable
            public Tag get(String string) {
                return compoundTag.get(string);
            }

            @Override
            public Stream<Pair<Tag, Tag>> entries() {
                return compoundTag.entrySet().stream().map(entry -> Pair.of(NbtOps.this.createString(entry.getKey()), entry.getValue()));
            }

            @Override
            public String toString() {
                return "MapLike[" + compoundTag + "]";
            }
        }) : DataResult.error(() -> "Not a map: " + map);
    }

    public Tag createMap(Stream<Pair<Tag, Tag>> data) {
        CompoundTag compoundTag = new CompoundTag();
        data.forEach(pair -> compoundTag.put(pair.getFirst().getAsString(), pair.getSecond()));
        return compoundTag;
    }

    private static Tag tryUnwrap(CompoundTag tag) {
        if (tag.size() == 1) {
            Tag tag2 = tag.get("");
            if (tag2 != null) {
                return tag2;
            }
        }

        return tag;
    }

    public DataResult<Stream<Tag>> getStream(Tag tag) {
        if (tag instanceof ListTag listTag) {
            return listTag.getElementType() == 10
                ? DataResult.success(listTag.stream().map(tagx -> tryUnwrap((CompoundTag)tagx)))
                : DataResult.success(listTag.stream());
        } else {
            return tag instanceof CollectionTag<?> collectionTag
                ? DataResult.success(collectionTag.stream().map(tagx -> tagx))
                : DataResult.error(() -> "Not a list");
        }
    }

    public DataResult<Consumer<Consumer<Tag>>> getList(Tag tag) {
        if (tag instanceof ListTag listTag) {
            return listTag.getElementType() == 10 ? DataResult.success(consumer -> {
                for (Tag tagx : listTag) {
                    consumer.accept(tryUnwrap((CompoundTag)tagx));
                }
            }) : DataResult.success(listTag::forEach);
        } else {
            return tag instanceof CollectionTag<?> collectionTag ? DataResult.success(collectionTag::forEach) : DataResult.error(() -> "Not a list: " + tag);
        }
    }

    public DataResult<ByteBuffer> getByteBuffer(Tag tag) {
        return tag instanceof ByteArrayTag byteArrayTag
            ? DataResult.success(ByteBuffer.wrap(byteArrayTag.getAsByteArray()))
            : DynamicOps.super.getByteBuffer(tag);
    }

    public Tag createByteList(ByteBuffer data) {
        ByteBuffer byteBuffer = data.duplicate().clear();
        byte[] bs = new byte[data.capacity()];
        byteBuffer.get(0, bs, 0, bs.length);
        return new ByteArrayTag(bs);
    }

    public DataResult<IntStream> getIntStream(Tag tag) {
        return tag instanceof IntArrayTag intArrayTag ? DataResult.success(Arrays.stream(intArrayTag.getAsIntArray())) : DynamicOps.super.getIntStream(tag);
    }

    public Tag createIntList(IntStream data) {
        return new IntArrayTag(data.toArray());
    }

    public DataResult<LongStream> getLongStream(Tag tag) {
        return tag instanceof LongArrayTag longArrayTag
            ? DataResult.success(Arrays.stream(longArrayTag.getAsLongArray()))
            : DynamicOps.super.getLongStream(tag);
    }

    public Tag createLongList(LongStream data) {
        return new LongArrayTag(data.toArray());
    }

    public Tag createList(Stream<Tag> data) {
        return NbtOps.InitialListCollector.INSTANCE.acceptAll(data).result();
    }

    public Tag remove(Tag map, String removeKey) {
        if (map instanceof CompoundTag compoundTag) {
            CompoundTag compoundTag2 = compoundTag.shallowCopy();
            compoundTag2.remove(removeKey);
            return compoundTag2;
        } else {
            return map;
        }
    }

    @Override
    public String toString() {
        return "NBT";
    }

    @Override
    public RecordBuilder<Tag> mapBuilder() {
        return new NbtOps.NbtRecordBuilder();
    }

    private static Optional<NbtOps.ListCollector> createCollector(Tag tag) {
        if (tag instanceof EndTag) {
            return Optional.of(NbtOps.InitialListCollector.INSTANCE);
        }

        if (tag instanceof CollectionTag<?> collectionTag) {
            if (collectionTag.isEmpty()) {
                return Optional.of(NbtOps.InitialListCollector.INSTANCE);
            }

            if (collectionTag instanceof ListTag listTag) {
                return switch (listTag.getElementType()) {
                    case 0 -> Optional.of(NbtOps.InitialListCollector.INSTANCE);
                    case 10 -> Optional.of(new NbtOps.HeterogenousListCollector(listTag));
                    default -> Optional.of(new NbtOps.HomogenousListCollector(listTag));
                };
            }

            if (collectionTag instanceof ByteArrayTag byteArrayTag) {
                return Optional.of(new NbtOps.ByteListCollector(byteArrayTag.getAsByteArray()));
            }

            if (collectionTag instanceof IntArrayTag intArrayTag) {
                return Optional.of(new NbtOps.IntListCollector(intArrayTag.getAsIntArray()));
            }

            if (collectionTag instanceof LongArrayTag longArrayTag) {
                return Optional.of(new NbtOps.LongListCollector(longArrayTag.getAsLongArray()));
            }
        }

        return Optional.empty();
    }

    static class ByteListCollector implements NbtOps.ListCollector {
        private final ByteArrayList values = new ByteArrayList();

        public ByteListCollector(byte value) {
            this.values.add(value);
        }

        public ByteListCollector(byte[] values) {
            this.values.addElements(0, values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof ByteTag byteTag) {
                this.values.add(byteTag.getAsByte());
                return this;
            } else {
                return new NbtOps.HeterogenousListCollector(this.values).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new ByteArrayTag(this.values.toByteArray());
        }
    }

    static class HeterogenousListCollector implements NbtOps.ListCollector {
        private final ListTag result = new ListTag();

        public HeterogenousListCollector() {
        }

        public HeterogenousListCollector(Collection<Tag> tags) {
            this.result.addAll(tags);
        }

        public HeterogenousListCollector(IntArrayList data) {
            data.forEach(i -> this.result.add(wrapElement(IntTag.valueOf(i))));
        }

        public HeterogenousListCollector(ByteArrayList data) {
            data.forEach(b -> this.result.add(wrapElement(ByteTag.valueOf(b))));
        }

        public HeterogenousListCollector(LongArrayList data) {
            data.forEach(l -> this.result.add(wrapElement(LongTag.valueOf(l))));
        }

        private static boolean isWrapper(CompoundTag tag) {
            return tag.size() == 1 && tag.contains("");
        }

        private static Tag wrapIfNeeded(Tag tag) {
            return tag instanceof CompoundTag compoundTag && !isWrapper(compoundTag) ? compoundTag : wrapElement(tag);
        }

        private static CompoundTag wrapElement(Tag tag) {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.put("", tag);
            return compoundTag;
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            this.result.add(wrapIfNeeded(tag));
            return this;
        }

        @Override
        public Tag result() {
            return this.result;
        }
    }

    static class HomogenousListCollector implements NbtOps.ListCollector {
        private final ListTag result = new ListTag();

        HomogenousListCollector(Tag value) {
            this.result.add(value);
        }

        HomogenousListCollector(ListTag values) {
            this.result.addAll(values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag.getId() != this.result.getElementType()) {
                return new NbtOps.HeterogenousListCollector().acceptAll(this.result).accept(tag);
            }

            this.result.add(tag);
            return this;
        }

        @Override
        public Tag result() {
            return this.result;
        }
    }

    static class InitialListCollector implements NbtOps.ListCollector {
        public static final NbtOps.InitialListCollector INSTANCE = new NbtOps.InitialListCollector();

        private InitialListCollector() {
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof CompoundTag compoundTag) {
                return new NbtOps.HeterogenousListCollector().accept(compoundTag);
            } else if (tag instanceof ByteTag byteTag) {
                return new NbtOps.ByteListCollector(byteTag.getAsByte());
            } else if (tag instanceof IntTag intTag) {
                return new NbtOps.IntListCollector(intTag.getAsInt());
            } else {
                return tag instanceof LongTag longTag ? new NbtOps.LongListCollector(longTag.getAsLong()) : new NbtOps.HomogenousListCollector(tag);
            }
        }

        @Override
        public Tag result() {
            return new ListTag();
        }
    }

    static class IntListCollector implements NbtOps.ListCollector {
        private final IntArrayList values = new IntArrayList();

        public IntListCollector(int value) {
            this.values.add(value);
        }

        public IntListCollector(int[] values) {
            this.values.addElements(0, values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof IntTag intTag) {
                this.values.add(intTag.getAsInt());
                return this;
            } else {
                return new NbtOps.HeterogenousListCollector(this.values).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new IntArrayTag(this.values.toIntArray());
        }
    }

    interface ListCollector {
        NbtOps.ListCollector accept(Tag var1);

        default NbtOps.ListCollector acceptAll(Iterable<Tag> tags) {
            NbtOps.ListCollector listCollector = this;

            for (Tag tag : tags) {
                listCollector = listCollector.accept(tag);
            }

            return listCollector;
        }

        default NbtOps.ListCollector acceptAll(Stream<Tag> tags) {
            return this.acceptAll(tags::iterator);
        }

        Tag result();
    }

    static class LongListCollector implements NbtOps.ListCollector {
        private final LongArrayList values = new LongArrayList();

        public LongListCollector(long value) {
            this.values.add(value);
        }

        public LongListCollector(long[] values) {
            this.values.addElements(0, values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag tag) {
            if (tag instanceof LongTag longTag) {
                this.values.add(longTag.getAsLong());
                return this;
            } else {
                return new NbtOps.HeterogenousListCollector(this.values).accept(tag);
            }
        }

        @Override
        public Tag result() {
            return new LongArrayTag(this.values.toLongArray());
        }
    }

    class NbtRecordBuilder extends AbstractStringBuilder<Tag, CompoundTag> {
        protected NbtRecordBuilder() {
            super(NbtOps.this);
        }

        protected CompoundTag initBuilder() {
            return new CompoundTag();
        }

        protected CompoundTag append(String key, Tag value, CompoundTag tag) {
            tag.put(key, value);
            return tag;
        }

        protected DataResult<Tag> build(CompoundTag compoundTag, Tag tag) {
            if (tag == null || tag == EndTag.INSTANCE) {
                return DataResult.success(compoundTag);
            } else if (!(tag instanceof CompoundTag compoundTag2)) {
                return DataResult.error(() -> "mergeToMap called with not a map: " + tag, tag);
            } else {
                CompoundTag compoundTag3 = compoundTag2.shallowCopy();

                for (Entry<String, Tag> entry : compoundTag.entrySet()) {
                    compoundTag3.put(entry.getKey(), entry.getValue());
                }

                return DataResult.success(compoundTag3);
            }
        }
    }
}
