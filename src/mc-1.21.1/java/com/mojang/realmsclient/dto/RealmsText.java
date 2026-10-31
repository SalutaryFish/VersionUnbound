package com.mojang.realmsclient.dto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.realmsclient.util.JsonUtils;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public class RealmsText {
    private static final String TRANSLATION_KEY = "translationKey";
    private static final String ARGS = "args";
    private final String translationKey;
    @Nullable
    private final String[] args;

    private RealmsText(String translationKey, @Nullable String[] args) {
        this.translationKey = translationKey;
        this.args = args;
    }

    public Component createComponent(Component defaultValue) {
        return Objects.requireNonNullElse(this.createComponent(), defaultValue);
    }

    @Nullable
    public Component createComponent() {
        if (!I18n.exists(this.translationKey)) {
            return null;
        } else {
            return this.args == null ? Component.translatable(this.translationKey) : Component.translatable(this.translationKey, this.args);
        }
    }

    public static RealmsText parse(JsonObject json) {
        String string = JsonUtils.getRequiredString("translationKey", json);
        JsonElement jsonElement = json.get("args");
        String[] strings;
        if (jsonElement != null && !jsonElement.isJsonNull()) {
            JsonArray jsonArray = jsonElement.getAsJsonArray();
            strings = new String[jsonArray.size()];

            for (int i = 0; i < jsonArray.size(); i++) {
                strings[i] = jsonArray.get(i).getAsString();
            }
        } else {
            strings = null;
        }

        return new RealmsText(string, strings);
    }

    @Override
    public String toString() {
        return this.translationKey;
    }
}
