package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import org.slf4j.Logger;

public class RealmsNews extends ValueObject {
    private static final Logger LOGGER = LogUtils.getLogger();
    public String newsLink;

    public static RealmsNews parse(String json) {
        RealmsNews realmsNews = new RealmsNews();

        try {
            JsonParser jsonParser = new JsonParser();
            JsonObject jsonObject = jsonParser.parse(json).getAsJsonObject();
            realmsNews.newsLink = JsonUtils.getStringOr("newsLink", jsonObject, null);
        } catch (Exception exception) {
            LOGGER.error("Could not parse RealmsNews: {}", exception.getMessage());
        }

        return realmsNews;
    }
}
