package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import org.slf4j.Logger;

public class RealmsServerAddress extends ValueObject {
    private static final Logger LOGGER = LogUtils.getLogger();
    public String address;
    public String resourcePackUrl;
    public String resourcePackHash;

    public static RealmsServerAddress parse(String json) {
        JsonParser jsonParser = new JsonParser();
        RealmsServerAddress realmsServerAddress = new RealmsServerAddress();

        try {
            JsonObject jsonObject = jsonParser.parse(json).getAsJsonObject();
            realmsServerAddress.address = JsonUtils.getStringOr("address", jsonObject, null);
            realmsServerAddress.resourcePackUrl = JsonUtils.getStringOr("resourcePackUrl", jsonObject, null);
            realmsServerAddress.resourcePackHash = JsonUtils.getStringOr("resourcePackHash", jsonObject, null);
        } catch (Exception exception) {
            LOGGER.error("Could not parse RealmsServerAddress: {}", exception.getMessage());
        }

        return realmsServerAddress;
    }
}
