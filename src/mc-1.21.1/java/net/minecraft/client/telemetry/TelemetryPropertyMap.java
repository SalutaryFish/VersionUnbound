package net.minecraft.client.telemetry;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class TelemetryPropertyMap {
    final Map<TelemetryProperty<?>, Object> entries;

    TelemetryPropertyMap(Map<TelemetryProperty<?>, Object> entries) {
        this.entries = entries;
    }

    public static TelemetryPropertyMap.Builder builder() {
        return new TelemetryPropertyMap.Builder();
    }

    public static MapCodec<TelemetryPropertyMap> createCodec(List<TelemetryProperty<?>> properties) {
        return new MapCodec<TelemetryPropertyMap>() {
            public <T> RecordBuilder<T> encode(TelemetryPropertyMap map, DynamicOps<T> ops, RecordBuilder<T> builder) {
                RecordBuilder<T> recordBuilder = builder;

                for (TelemetryProperty<?> telemetryProperty : properties) {
                    recordBuilder = this.encodeProperty(map, recordBuilder, telemetryProperty);
                }

                return recordBuilder;
            }

            private <T, V> RecordBuilder<T> encodeProperty(TelemetryPropertyMap map, RecordBuilder<T> builder, TelemetryProperty<V> key) {
                V object = map.get(key);
                return object != null ? builder.add(key.id(), object, key.codec()) : builder;
            }

            @Override
            public <T> DataResult<TelemetryPropertyMap> decode(DynamicOps<T> ops, MapLike<T> value) {
                DataResult<TelemetryPropertyMap.Builder> dataResult = DataResult.success(new TelemetryPropertyMap.Builder());

                for (TelemetryProperty<?> telemetryProperty : properties) {
                    dataResult = this.decodeProperty(dataResult, ops, value, telemetryProperty);
                }

                return dataResult.map(TelemetryPropertyMap.Builder::build);
            }

            private <T, V> DataResult<TelemetryPropertyMap.Builder> decodeProperty(
                DataResult<TelemetryPropertyMap.Builder> result, DynamicOps<T> ops, MapLike<T> value, TelemetryProperty<V> property
            ) {
                T object = value.get(property.id());
                if (object != null) {
                    DataResult<V> dataResult = property.codec().parse(ops, object);
                    return result.apply2stable((builder, objectx) -> builder.put(property, (V)objectx), dataResult);
                } else {
                    return result;
                }
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> dynamicOps) {
                return properties.stream().map(TelemetryProperty::id).map(dynamicOps::createString);
            }
        };
    }

    @Nullable
    public <T> T get(TelemetryProperty<T> key) {
        return (T)this.entries.get(key);
    }

    @Override
    public String toString() {
        return this.entries.toString();
    }

    public Set<TelemetryProperty<?>> propertySet() {
        return this.entries.keySet();
    }

    public static class Builder {
        private final Map<TelemetryProperty<?>, Object> entries = new Reference2ObjectOpenHashMap<>();

        Builder() {
        }

        public <T> TelemetryPropertyMap.Builder put(TelemetryProperty<T> key, T value) {
            this.entries.put(key, value);
            return this;
        }

        public <T> TelemetryPropertyMap.Builder putIfNotNull(TelemetryProperty<T> key, @Nullable T value) {
            if (value != null) {
                this.entries.put(key, value);
            }

            return this;
        }

        public TelemetryPropertyMap.Builder putAll(TelemetryPropertyMap propertyMap) {
            this.entries.putAll(propertyMap.entries);
            return this;
        }

        public TelemetryPropertyMap build() {
            return new TelemetryPropertyMap(this.entries);
        }
    }
}
