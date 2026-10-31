package net.minecraft.client;

import com.mojang.util.UndashedUuid;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public class User {
    private final String name;
    private final UUID uuid;
    private final String accessToken;
    private final Optional<String> xuid;
    private final Optional<String> clientId;
    private final User.Type type;

    public User(String name, UUID uuid, String accessToken, Optional<String> xuid, Optional<String> clientId, User.Type type) {
        this.name = name;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.xuid = xuid;
        this.clientId = clientId;
        this.type = type;
    }

    public String getSessionId() {
        return "token:" + this.accessToken + ":" + UndashedUuid.toString(this.uuid);
    }

    public UUID getProfileId() {
        return this.uuid;
    }

    public String getName() {
        return this.name;
    }

    public String getAccessToken() {
        return this.accessToken;
    }

    public Optional<String> getClientId() {
        return this.clientId;
    }

    public Optional<String> getXuid() {
        return this.xuid;
    }

    public User.Type getType() {
        return this.type;
    }

    public enum Type {
        LEGACY("legacy"),
        MOJANG("mojang"),
        MSA("msa");

        private static final Map<String, User.Type> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(value -> value.name, Function.identity()));
        private final String name;

        Type(final String name) {
            this.name = name;
        }

        @Nullable
        public static User.Type byName(String typeName) {
            return BY_NAME.get(typeName.toLowerCase(Locale.ROOT));
        }

        public String getName() {
            return this.name;
        }
    }
}
