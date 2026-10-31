package com.mojang.realmsclient.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.util.UndashedUuid;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;

public class JsonUtils {
    public static <T> T getRequired(String key, JsonObject json, Function<JsonObject, T> output) {
        JsonElement jsonElement = json.get(key);
        if (jsonElement == null || jsonElement.isJsonNull()) {
            throw new IllegalStateException("Missing required property: " + key);
        } else if (!jsonElement.isJsonObject()) {
            throw new IllegalStateException("Required property " + key + " was not a JsonObject as espected");
        } else {
            return output.apply(jsonElement.getAsJsonObject());
        }
    }

    @Nullable
    public static <T> T getOptional(String key, JsonObject json, Function<JsonObject, T> output) {
        JsonElement jsonElement = json.get(key);
        if (jsonElement == null || jsonElement.isJsonNull()) {
            return null;
        } else if (!jsonElement.isJsonObject()) {
            throw new IllegalStateException("Required property " + key + " was not a JsonObject as espected");
        } else {
            return output.apply(jsonElement.getAsJsonObject());
        }
    }

    public static String getRequiredString(String key, JsonObject json) {
        String string = getStringOr(key, json, null);
        if (string == null) {
            throw new IllegalStateException("Missing required property: " + key);
        } else {
            return string;
        }
    }

    public static String getRequiredStringOr(String key, JsonObject json, String defaultValue) {
        JsonElement jsonElement = json.get(key);
        if (jsonElement != null) {
            return jsonElement.isJsonNull() ? defaultValue : jsonElement.getAsString();
        } else {
            return defaultValue;
        }
    }

    @Nullable
    public static String getStringOr(String key, JsonObject json, @Nullable String defaultValue) {
        JsonElement jsonElement = json.get(key);
        if (jsonElement != null) {
            return jsonElement.isJsonNull() ? defaultValue : jsonElement.getAsString();
        } else {
            return defaultValue;
        }
    }

    @Nullable
    public static UUID getUuidOr(String key, JsonObject json, @Nullable UUID defaultValue) {
        String string = getStringOr(key, json, null);
        return string == null ? defaultValue : UndashedUuid.fromStringLenient(string);
    }

    public static int getIntOr(String key, JsonObject json, int defaultValue) {
        JsonElement jsonElement = json.get(key);
        if (jsonElement != null) {
            return jsonElement.isJsonNull() ? defaultValue : jsonElement.getAsInt();
        } else {
            return defaultValue;
        }
    }

    public static long getLongOr(String key, JsonObject json, long defaultValue) {
        JsonElement jsonElement = json.get(key);
        if (jsonElement != null) {
            return jsonElement.isJsonNull() ? defaultValue : jsonElement.getAsLong();
        } else {
            return defaultValue;
        }
    }

    public static boolean getBooleanOr(String key, JsonObject json, boolean defaultValue) {
        JsonElement jsonElement = json.get(key);
        if (jsonElement != null) {
            return jsonElement.isJsonNull() ? defaultValue : jsonElement.getAsBoolean();
        } else {
            return defaultValue;
        }
    }

    public static Date getDateOr(String key, JsonObject json) {
        JsonElement jsonElement = json.get(key);
        return jsonElement != null ? new Date(Long.parseLong(jsonElement.getAsString())) : new Date();
    }
}
