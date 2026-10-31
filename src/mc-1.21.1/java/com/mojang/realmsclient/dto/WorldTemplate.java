package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import javax.annotation.Nullable;
import org.slf4j.Logger;

public class WorldTemplate extends ValueObject {
    private static final Logger LOGGER = LogUtils.getLogger();
    public String id = "";
    public String name = "";
    public String version = "";
    public String author = "";
    public String link = "";
    @Nullable
    public String image;
    public String trailer = "";
    public String recommendedPlayers = "";
    public WorldTemplate.WorldTemplateType type = WorldTemplate.WorldTemplateType.WORLD_TEMPLATE;

    public static WorldTemplate parse(JsonObject json) {
        WorldTemplate worldTemplate = new WorldTemplate();

        try {
            worldTemplate.id = JsonUtils.getStringOr("id", json, "");
            worldTemplate.name = JsonUtils.getStringOr("name", json, "");
            worldTemplate.version = JsonUtils.getStringOr("version", json, "");
            worldTemplate.author = JsonUtils.getStringOr("author", json, "");
            worldTemplate.link = JsonUtils.getStringOr("link", json, "");
            worldTemplate.image = JsonUtils.getStringOr("image", json, null);
            worldTemplate.trailer = JsonUtils.getStringOr("trailer", json, "");
            worldTemplate.recommendedPlayers = JsonUtils.getStringOr("recommendedPlayers", json, "");
            worldTemplate.type = WorldTemplate.WorldTemplateType.valueOf(
                JsonUtils.getStringOr("type", json, WorldTemplate.WorldTemplateType.WORLD_TEMPLATE.name())
            );
        } catch (Exception exception) {
            LOGGER.error("Could not parse WorldTemplate: {}", exception.getMessage());
        }

        return worldTemplate;
    }

    public enum WorldTemplateType {
        WORLD_TEMPLATE,
        MINIGAME,
        ADVENTUREMAP,
        EXPERIENCE,
        INSPIRATION;
    }
}
