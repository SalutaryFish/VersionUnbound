package com.mojang.realmsclient.dto;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

public class RealmsServerPlayerLists extends ValueObject {
    private static final Logger LOGGER = LogUtils.getLogger();
    public Map<Long, List<ProfileResult>> servers = Map.of();

    public static RealmsServerPlayerLists parse(String json) {
        RealmsServerPlayerLists realmsServerPlayerLists = new RealmsServerPlayerLists();
        Builder<Long, List<ProfileResult>> builder = ImmutableMap.builder();

        try {
            JsonObject jsonObject = GsonHelper.parse(json);
            if (GsonHelper.isArrayNode(jsonObject, "lists")) {
                for (JsonElement jsonElement : jsonObject.getAsJsonArray("lists")) {
                    JsonObject jsonObject2 = jsonElement.getAsJsonObject();
                    String string = JsonUtils.getStringOr("playerList", jsonObject2, null);
                    List<ProfileResult> list;
                    if (string != null) {
                        JsonElement jsonElement2 = JsonParser.parseString(string);
                        if (jsonElement2.isJsonArray()) {
                            list = parsePlayers(jsonElement2.getAsJsonArray());
                        } else {
                            list = Lists.newArrayList();
                        }
                    } else {
                        list = Lists.newArrayList();
                    }

                    builder.put(JsonUtils.getLongOr("serverId", jsonObject2, -1L), list);
                }
            }
        } catch (Exception exception) {
            LOGGER.error("Could not parse RealmsServerPlayerLists: {}", exception.getMessage());
        }

        realmsServerPlayerLists.servers = builder.build();
        return realmsServerPlayerLists;
    }

    private static List<ProfileResult> parsePlayers(JsonArray json) {
        List<ProfileResult> list = new ArrayList<>(json.size());
        MinecraftSessionService minecraftSessionService = Minecraft.getInstance().getMinecraftSessionService();

        for (JsonElement jsonElement : json) {
            if (jsonElement.isJsonObject()) {
                UUID uUID = JsonUtils.getUuidOr("playerId", jsonElement.getAsJsonObject(), null);
                if (uUID != null && !Minecraft.getInstance().isLocalPlayer(uUID)) {
                    try {
                        ProfileResult profileResult = minecraftSessionService.fetchProfile(uUID, false);
                        if (profileResult != null) {
                            list.add(profileResult);
                        }
                    } catch (Exception exception) {
                        LOGGER.error("Could not get name for {}", uUID, exception);
                    }
                }
            }
        }

        return list;
    }

    public List<ProfileResult> getProfileResultsFor(long index) {
        List<ProfileResult> list = this.servers.get(index);
        return list != null ? list : List.of();
    }
}
