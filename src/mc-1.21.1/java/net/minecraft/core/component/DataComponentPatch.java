package net.minecraft.core.component;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;

public final class DataComponentPatch {
    public static final DataComponentPatch EMPTY = new DataComponentPatch(Reference2ObjectMaps.emptyMap());
    public static final Codec<DataComponentPatch> CODEC = Codec.dispatchedMap(DataComponentPatch.PatchKey.CODEC, DataComponentPatch.PatchKey::valueCodec)
        .xmap(map -> {
            if (map.isEmpty()) {
                return EMPTY;
            }

            Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap = new Reference2ObjectArrayMap<>(map.size());

            for (Entry<DataComponentPatch.PatchKey, ?> entry : map.entrySet()) {
                DataComponentPatch.PatchKey patchKey = entry.getKey();
                if (patchKey.removed()) {
                    reference2ObjectMap.put(patchKey.type(), Optional.empty());
                } else {
                    reference2ObjectMap.put(patchKey.type(), Optional.of(entry.getValue()));
                }
            }

            return new DataComponentPatch(reference2ObjectMap);
        }, dataComponentPatch -> {
            Reference2ObjectMap<DataComponentPatch.PatchKey, Object> reference2ObjectMap = new Reference2ObjectArrayMap<>(dataComponentPatch.map.size());

            for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(dataComponentPatch.map)) {
                DataComponentType<?> dataComponentType = entry.getKey();
                if (!dataComponentType.isTransient()) {
                    Optional<?> optional = entry.getValue();
                    if (optional.isPresent()) {
                        reference2ObjectMap.put(new DataComponentPatch.PatchKey(dataComponentType, false), optional.get());
                    } else {
                        reference2ObjectMap.put(new DataComponentPatch.PatchKey(dataComponentType, true), Unit.INSTANCE);
                    }
                }
            }

            return reference2ObjectMap;
        });
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch>() {
        public DataComponentPatch decode(RegistryFriendlyByteBuf buffer) {
            int i = buffer.readVarInt();
            int j = buffer.readVarInt();
            if (i == 0 && j == 0) {
                return DataComponentPatch.EMPTY;
            }

            int k = i + j;
            Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap = new Reference2ObjectArrayMap<>(Math.min(k, 65536));

            for (int l = 0; l < i; l++) {
                DataComponentType<?> dataComponentType = DataComponentType.STREAM_CODEC.decode(buffer);
                Object object = dataComponentType.streamCodec().decode(buffer);
                reference2ObjectMap.put(dataComponentType, Optional.of(object));
            }

            for (int l = 0; l < j; l++) {
                DataComponentType<?> dataComponentType = DataComponentType.STREAM_CODEC.decode(buffer);
                reference2ObjectMap.put(dataComponentType, Optional.empty());
            }

            return new DataComponentPatch(reference2ObjectMap);
        }

        public void encode(RegistryFriendlyByteBuf buffer, DataComponentPatch value) {
            if (value.isEmpty()) {
                buffer.writeVarInt(0);
                buffer.writeVarInt(0);
            } else {
                int i = 0;
                int j = 0;

                for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
                    value.map
                )) {
                    if (entry.getValue().isPresent()) {
                        i++;
                    } else {
                        j++;
                    }
                }

                buffer.writeVarInt(i);
                buffer.writeVarInt(j);

                for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
                    value.map
                )) {
                    Optional<?> optional = entry.getValue();
                    if (optional.isPresent()) {
                        DataComponentType<?> dataComponentType = entry.getKey();
                        DataComponentType.STREAM_CODEC.encode(buffer, dataComponentType);
                        encodeComponent(buffer, dataComponentType, optional.get());
                    }
                }

                for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
                    value.map
                )) {
                    if (entry.getValue().isEmpty()) {
                        DataComponentType<?> dataComponentType2 = entry.getKey();
                        DataComponentType.STREAM_CODEC.encode(buffer, dataComponentType2);
                    }
                }
            }
        }

        private static <T> void encodeComponent(RegistryFriendlyByteBuf buffer, DataComponentType<T> component, Object value) {
            component.streamCodec().encode(buffer, (T)value);
        }
    };
    private static final String REMOVED_PREFIX = "!";
    final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map;

    DataComponentPatch(Reference2ObjectMap<DataComponentType<?>, Optional<?>> map) {
        this.map = map;
    }

    public static DataComponentPatch.Builder builder() {
        return new DataComponentPatch.Builder();
    }

    @Nullable
    public <T> Optional<? extends T> get(DataComponentType<? extends T> component) {
        return (Optional<? extends T>)this.map.get(component);
    }

    public Set<Entry<DataComponentType<?>, Optional<?>>> entrySet() {
        return this.map.entrySet();
    }

    public int size() {
        return this.map.size();
    }

    public DataComponentPatch forget(Predicate<DataComponentType<?>> predicate) {
        if (this.isEmpty()) {
            return EMPTY;
        }

        Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2ObjectMap = new Reference2ObjectArrayMap<>(this.map);
        reference2ObjectMap.keySet().removeIf(predicate);
        return reference2ObjectMap.isEmpty() ? EMPTY : new DataComponentPatch(reference2ObjectMap);
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    public DataComponentPatch.SplitResult split() {
        if (this.isEmpty()) {
            return DataComponentPatch.SplitResult.EMPTY;
        }

        DataComponentMap.Builder builder = DataComponentMap.builder();
        Set<DataComponentType<?>> set = Sets.newIdentityHashSet();
        this.map.forEach((dataComponentType, optional) -> {
            if (optional.isPresent()) {
                builder.setUnchecked((DataComponentType<?>)dataComponentType, optional.get());
            } else {
                set.add((DataComponentType<?>)dataComponentType);
            }
        });
        return new DataComponentPatch.SplitResult(builder.build(), set);
    }

    @Override
    public boolean equals(Object other) {
        return this == other ? true : other instanceof DataComponentPatch dataComponentPatch && this.map.equals(dataComponentPatch.map);
    }

    @Override
    public int hashCode() {
        return this.map.hashCode();
    }

    @Override
    public String toString() {
        return toString(this.map);
    }

    static String toString(Reference2ObjectMap<DataComponentType<?>, Optional<?>> map) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append('{');
        boolean bl = true;

        for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(map)) {
            if (bl) {
                bl = false;
            } else {
                stringBuilder.append(", ");
            }

            Optional<?> optional = entry.getValue();
            if (optional.isPresent()) {
                stringBuilder.append(entry.getKey());
                stringBuilder.append("=>");
                stringBuilder.append(optional.get());
            } else {
                stringBuilder.append("!");
                stringBuilder.append(entry.getKey());
            }
        }

        stringBuilder.append('}');
        return stringBuilder.toString();
    }

    public static class Builder {
        private final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map = new Reference2ObjectArrayMap<>();

        Builder() {
        }

        public <T> DataComponentPatch.Builder set(DataComponentType<T> component, T value) {
            this.map.put(component, Optional.of(value));
            return this;
        }

        public <T> DataComponentPatch.Builder remove(DataComponentType<T> component) {
            this.map.put(component, Optional.empty());
            return this;
        }

        public <T> DataComponentPatch.Builder set(TypedDataComponent<T> component) {
            return this.set(component.type(), component.value());
        }

        public DataComponentPatch build() {
            return this.map.isEmpty() ? DataComponentPatch.EMPTY : new DataComponentPatch(this.map);
        }
    }

    record PatchKey(DataComponentType<?> type, boolean removed) {
        public static final Codec<DataComponentPatch.PatchKey> CODEC = Codec.STRING
            .flatXmap(
                string -> {
                    boolean bl = string.startsWith("!");
                    if (bl) {
                        string = string.substring("!".length());
                    }

                    ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
                    DataComponentType<?> dataComponentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(resourceLocation);
                    if (dataComponentType == null) {
                        return DataResult.error(() -> "No component with type: '" + resourceLocation + "'");
                    } else {
                        return dataComponentType.isTransient()
                            ? DataResult.error(() -> "'" + resourceLocation + "' is not a persistent component")
                            : DataResult.success(new DataComponentPatch.PatchKey(dataComponentType, bl));
                    }
                },
                patchKey -> {
                    DataComponentType<?> dataComponentType = patchKey.type();
                    ResourceLocation resourceLocation = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(dataComponentType);
                    return resourceLocation == null
                        ? DataResult.error(() -> "Unregistered component: " + dataComponentType)
                        : DataResult.success(patchKey.removed() ? "!" + resourceLocation : resourceLocation.toString());
                }
            );

        public Codec<?> valueCodec() {
            return this.removed ? Codec.EMPTY.codec() : this.type.codecOrThrow();
        }
    }

    public record SplitResult(DataComponentMap added, Set<DataComponentType<?>> removed) {
        public static final DataComponentPatch.SplitResult EMPTY = new DataComponentPatch.SplitResult(DataComponentMap.EMPTY, Set.of());
    }
}
