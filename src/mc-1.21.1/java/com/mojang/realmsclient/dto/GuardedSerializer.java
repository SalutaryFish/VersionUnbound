package com.mojang.realmsclient.dto;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import javax.annotation.Nullable;

public class GuardedSerializer {
    private final Gson gson = new Gson();

    public String toJson(ReflectionBasedSerialization reflectionBasedSerialization) {
        return this.gson.toJson(reflectionBasedSerialization);
    }

    public String toJson(JsonElement json) {
        return this.gson.toJson(json);
    }

    @Nullable
    public <T extends ReflectionBasedSerialization> T fromJson(String json, Class<T> classOfT) {
        return this.gson.fromJson(json, classOfT);
    }
}
