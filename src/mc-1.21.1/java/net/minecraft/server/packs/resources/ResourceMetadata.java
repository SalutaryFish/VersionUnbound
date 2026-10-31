package net.minecraft.server.packs.resources;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.util.GsonHelper;

public interface ResourceMetadata {
    ResourceMetadata EMPTY = new ResourceMetadata() {
        @Override
        public <T> Optional<T> getSection(MetadataSectionSerializer<T> metadataSectionSerializer) {
            return Optional.empty();
        }
    };
    IoSupplier<ResourceMetadata> EMPTY_SUPPLIER = () -> EMPTY;

    static ResourceMetadata fromJsonStream(InputStream stream) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            final JsonObject jsonObject = GsonHelper.parse(bufferedReader);
            return new ResourceMetadata() {
                @Override
                public <T> Optional<T> getSection(MetadataSectionSerializer<T> metadataSectionSerializer) {
                    String string = metadataSectionSerializer.getMetadataSectionName();
                    return jsonObject.has(string)
                        ? Optional.of(metadataSectionSerializer.fromJson(GsonHelper.getAsJsonObject(jsonObject, string)))
                        : Optional.empty();
                }
            };
        }
    }

    <T> Optional<T> getSection(MetadataSectionSerializer<T> var1);

    default ResourceMetadata copySections(Collection<MetadataSectionSerializer<?>> serializers) {
        ResourceMetadata.Builder builder = new ResourceMetadata.Builder();

        for (MetadataSectionSerializer<?> metadataSectionSerializer : serializers) {
            this.copySection(builder, metadataSectionSerializer);
        }

        return builder.build();
    }

    private <T> void copySection(ResourceMetadata.Builder builder, MetadataSectionSerializer<T> serializer) {
        this.getSection(serializer).ifPresent(section -> builder.put(serializer, (T)section));
    }

    class Builder {
        private final ImmutableMap.Builder<MetadataSectionSerializer<?>, Object> map = ImmutableMap.builder();

        public <T> ResourceMetadata.Builder put(MetadataSectionSerializer<T> key, T value) {
            this.map.put(key, value);
            return this;
        }

        public ResourceMetadata build() {
            final ImmutableMap<MetadataSectionSerializer<?>, Object> immutableMap = this.map.build();
            return immutableMap.isEmpty() ? ResourceMetadata.EMPTY : new ResourceMetadata() {
                @Override
                public <T> Optional<T> getSection(MetadataSectionSerializer<T> metadataSectionSerializer) {
                    return Optional.ofNullable((T)immutableMap.get(metadataSectionSerializer));
                }
            };
        }
    }
}
