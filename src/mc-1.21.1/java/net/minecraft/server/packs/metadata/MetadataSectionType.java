package net.minecraft.server.packs.metadata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

public interface MetadataSectionType<T> extends MetadataSectionSerializer<T> {
    JsonObject toJson(T var1);

    static <T> MetadataSectionType<T> fromCodec(String name, Codec<T> codec) {
        return new MetadataSectionType<T>() {
            @Override
            public String getMetadataSectionName() {
                return name;
            }

            @Override
            public T fromJson(JsonObject jsonObject) {
                return codec.parse(JsonOps.INSTANCE, jsonObject).getOrThrow(JsonParseException::new);
            }

            @Override
            public JsonObject toJson(T object) {
                return codec.encodeStart(JsonOps.INSTANCE, object).getOrThrow(IllegalArgumentException::new).getAsJsonObject();
            }
        };
    }
}
