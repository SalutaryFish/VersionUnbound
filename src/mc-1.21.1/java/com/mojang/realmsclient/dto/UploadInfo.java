package com.mojang.realmsclient.dto;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.slf4j.Logger;

public class UploadInfo extends ValueObject {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_SCHEMA = "http://";
    private static final int DEFAULT_PORT = 8080;
    private static final Pattern URI_SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z][-a-zA-Z0-9+.]+:");
    private final boolean worldClosed;
    @Nullable
    private final String token;
    private final URI uploadEndpoint;

    private UploadInfo(boolean worldClosed, @Nullable String token, URI uploadEndpoint) {
        this.worldClosed = worldClosed;
        this.token = token;
        this.uploadEndpoint = uploadEndpoint;
    }

    @Nullable
    public static UploadInfo parse(String json) {
        try {
            JsonParser jsonParser = new JsonParser();
            JsonObject jsonObject = jsonParser.parse(json).getAsJsonObject();
            String string = JsonUtils.getStringOr("uploadEndpoint", jsonObject, null);
            if (string != null) {
                int i = JsonUtils.getIntOr("port", jsonObject, -1);
                URI uRI = assembleUri(string, i);
                if (uRI != null) {
                    boolean bl = JsonUtils.getBooleanOr("worldClosed", jsonObject, false);
                    String string2 = JsonUtils.getStringOr("token", jsonObject, null);
                    return new UploadInfo(bl, string2, uRI);
                }
            }
        } catch (Exception exception) {
            LOGGER.error("Could not parse UploadInfo: {}", exception.getMessage());
        }

        return null;
    }

    @Nullable
    @VisibleForTesting
    public static URI assembleUri(String uri, int port) {
        Matcher matcher = URI_SCHEMA_PATTERN.matcher(uri);
        String string = ensureEndpointSchema(uri, matcher);

        try {
            URI uRI = new URI(string);
            int i = selectPortOrDefault(port, uRI.getPort());
            return i != uRI.getPort() ? new URI(uRI.getScheme(), uRI.getUserInfo(), uRI.getHost(), i, uRI.getPath(), uRI.getQuery(), uRI.getFragment()) : uRI;
        } catch (URISyntaxException uRISyntaxException) {
            LOGGER.warn("Failed to parse URI {}", string, uRISyntaxException);
            return null;
        }
    }

    private static int selectPortOrDefault(int port, int defaultPort) {
        if (port != -1) {
            return port;
        } else {
            return defaultPort != -1 ? defaultPort : 8080;
        }
    }

    private static String ensureEndpointSchema(String uri, Matcher matcher) {
        return matcher.find() ? uri : "http://" + uri;
    }

    public static String createRequest(@Nullable String token) {
        JsonObject jsonObject = new JsonObject();
        if (token != null) {
            jsonObject.addProperty("token", token);
        }

        return jsonObject.toString();
    }

    @Nullable
    public String getToken() {
        return this.token;
    }

    public URI getUploadEndpoint() {
        return this.uploadEndpoint;
    }

    public boolean isWorldClosed() {
        return this.worldClosed;
    }
}
