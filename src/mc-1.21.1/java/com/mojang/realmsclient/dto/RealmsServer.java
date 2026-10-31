package com.mojang.realmsclient.dto;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.multiplayer.ServerData;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.slf4j.Logger;

public class RealmsServer extends ValueObject {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_VALUE = -1;
    public long id;
    public String remoteSubscriptionId;
    public String name;
    public String motd;
    public RealmsServer.State state;
    public String owner;
    public UUID ownerUUID = Util.NIL_UUID;
    public List<PlayerInfo> players;
    public Map<Integer, RealmsWorldOptions> slots;
    public boolean expired;
    public boolean expiredTrial;
    public int daysLeft;
    public RealmsServer.WorldType worldType;
    public int activeSlot;
    @Nullable
    public String minigameName;
    public int minigameId;
    public String minigameImage;
    public long parentRealmId = -1L;
    @Nullable
    public String parentWorldName;
    public String activeVersion = "";
    public RealmsServer.Compatibility compatibility = RealmsServer.Compatibility.UNVERIFIABLE;

    public String getDescription() {
        return this.motd;
    }

    public String getName() {
        return this.name;
    }

    @Nullable
    public String getMinigameName() {
        return this.minigameName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String motd) {
        this.motd = motd;
    }

    public static RealmsServer parse(JsonObject json) {
        RealmsServer realmsServer = new RealmsServer();

        try {
            realmsServer.id = JsonUtils.getLongOr("id", json, -1L);
            realmsServer.remoteSubscriptionId = JsonUtils.getStringOr("remoteSubscriptionId", json, null);
            realmsServer.name = JsonUtils.getStringOr("name", json, null);
            realmsServer.motd = JsonUtils.getStringOr("motd", json, null);
            realmsServer.state = getState(JsonUtils.getStringOr("state", json, RealmsServer.State.CLOSED.name()));
            realmsServer.owner = JsonUtils.getStringOr("owner", json, null);
            if (json.get("players") != null && json.get("players").isJsonArray()) {
                realmsServer.players = parseInvited(json.get("players").getAsJsonArray());
                sortInvited(realmsServer);
            } else {
                realmsServer.players = Lists.newArrayList();
            }

            realmsServer.daysLeft = JsonUtils.getIntOr("daysLeft", json, 0);
            realmsServer.expired = JsonUtils.getBooleanOr("expired", json, false);
            realmsServer.expiredTrial = JsonUtils.getBooleanOr("expiredTrial", json, false);
            realmsServer.worldType = getWorldType(JsonUtils.getStringOr("worldType", json, RealmsServer.WorldType.NORMAL.name()));
            realmsServer.ownerUUID = JsonUtils.getUuidOr("ownerUUID", json, Util.NIL_UUID);
            if (json.get("slots") != null && json.get("slots").isJsonArray()) {
                realmsServer.slots = parseSlots(json.get("slots").getAsJsonArray());
            } else {
                realmsServer.slots = createEmptySlots();
            }

            realmsServer.minigameName = JsonUtils.getStringOr("minigameName", json, null);
            realmsServer.activeSlot = JsonUtils.getIntOr("activeSlot", json, -1);
            realmsServer.minigameId = JsonUtils.getIntOr("minigameId", json, -1);
            realmsServer.minigameImage = JsonUtils.getStringOr("minigameImage", json, null);
            realmsServer.parentRealmId = JsonUtils.getLongOr("parentWorldId", json, -1L);
            realmsServer.parentWorldName = JsonUtils.getStringOr("parentWorldName", json, null);
            realmsServer.activeVersion = JsonUtils.getStringOr("activeVersion", json, "");
            realmsServer.compatibility = getCompatibility(JsonUtils.getStringOr("compatibility", json, RealmsServer.Compatibility.UNVERIFIABLE.name()));
        } catch (Exception exception) {
            LOGGER.error("Could not parse McoServer: {}", exception.getMessage());
        }

        return realmsServer;
    }

    private static void sortInvited(RealmsServer realmsServer) {
        realmsServer.players
            .sort(
                (playerInfo, playerInfo2) -> ComparisonChain.start()
                    .compareFalseFirst(playerInfo2.getAccepted(), playerInfo.getAccepted())
                    .compare(playerInfo.getName().toLowerCase(Locale.ROOT), playerInfo2.getName().toLowerCase(Locale.ROOT))
                    .result()
            );
    }

    private static List<PlayerInfo> parseInvited(JsonArray jsonArray) {
        List<PlayerInfo> list = Lists.newArrayList();

        for (JsonElement jsonElement : jsonArray) {
            try {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                PlayerInfo playerInfo = new PlayerInfo();
                playerInfo.setName(JsonUtils.getStringOr("name", jsonObject, null));
                playerInfo.setUuid(JsonUtils.getUuidOr("uuid", jsonObject, Util.NIL_UUID));
                playerInfo.setOperator(JsonUtils.getBooleanOr("operator", jsonObject, false));
                playerInfo.setAccepted(JsonUtils.getBooleanOr("accepted", jsonObject, false));
                playerInfo.setOnline(JsonUtils.getBooleanOr("online", jsonObject, false));
                list.add(playerInfo);
            } catch (Exception var6) {
            }
        }

        return list;
    }

    private static Map<Integer, RealmsWorldOptions> parseSlots(JsonArray jsonArray) {
        Map<Integer, RealmsWorldOptions> map = Maps.newHashMap();

        for (JsonElement jsonElement : jsonArray) {
            try {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                JsonParser jsonParser = new JsonParser();
                JsonElement jsonElement2 = jsonParser.parse(jsonObject.get("options").getAsString());
                RealmsWorldOptions realmsWorldOptions;
                if (jsonElement2 == null) {
                    realmsWorldOptions = RealmsWorldOptions.createDefaults();
                } else {
                    realmsWorldOptions = RealmsWorldOptions.parse(jsonElement2.getAsJsonObject());
                }

                int i = JsonUtils.getIntOr("slotId", jsonObject, -1);
                map.put(i, realmsWorldOptions);
            } catch (Exception var9) {
            }
        }

        for (int j = 1; j <= 3; j++) {
            if (!map.containsKey(j)) {
                map.put(j, RealmsWorldOptions.createEmptyDefaults());
            }
        }

        return map;
    }

    private static Map<Integer, RealmsWorldOptions> createEmptySlots() {
        Map<Integer, RealmsWorldOptions> map = Maps.newHashMap();
        map.put(1, RealmsWorldOptions.createEmptyDefaults());
        map.put(2, RealmsWorldOptions.createEmptyDefaults());
        map.put(3, RealmsWorldOptions.createEmptyDefaults());
        return map;
    }

    public static RealmsServer parse(String json) {
        try {
            return parse(new JsonParser().parse(json).getAsJsonObject());
        } catch (Exception exception) {
            LOGGER.error("Could not parse McoServer: {}", exception.getMessage());
            return new RealmsServer();
        }
    }

    private static RealmsServer.State getState(String name) {
        try {
            return RealmsServer.State.valueOf(name);
        } catch (Exception exception) {
            return RealmsServer.State.CLOSED;
        }
    }

    private static RealmsServer.WorldType getWorldType(String name) {
        try {
            return RealmsServer.WorldType.valueOf(name);
        } catch (Exception exception) {
            return RealmsServer.WorldType.NORMAL;
        }
    }

    public static RealmsServer.Compatibility getCompatibility(@Nullable String id) {
        try {
            return RealmsServer.Compatibility.valueOf(id);
        } catch (Exception exception) {
            return RealmsServer.Compatibility.UNVERIFIABLE;
        }
    }

    public boolean isCompatible() {
        return this.compatibility.isCompatible();
    }

    public boolean needsUpgrade() {
        return this.compatibility.needsUpgrade();
    }

    public boolean needsDowngrade() {
        return this.compatibility.needsDowngrade();
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.name, this.motd, this.state, this.owner, this.expired);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }

        if (other == this) {
            return true;
        }

        if (other.getClass() != this.getClass()) {
            return false;
        }

        RealmsServer realmsServer = (RealmsServer)other;
        return new EqualsBuilder()
            .append(this.id, realmsServer.id)
            .append(this.name, realmsServer.name)
            .append(this.motd, realmsServer.motd)
            .append(this.state, realmsServer.state)
            .append(this.owner, realmsServer.owner)
            .append(this.expired, realmsServer.expired)
            .append(this.worldType, this.worldType)
            .isEquals();
    }

    public RealmsServer clone() {
        RealmsServer realmsServer = new RealmsServer();
        realmsServer.id = this.id;
        realmsServer.remoteSubscriptionId = this.remoteSubscriptionId;
        realmsServer.name = this.name;
        realmsServer.motd = this.motd;
        realmsServer.state = this.state;
        realmsServer.owner = this.owner;
        realmsServer.players = this.players;
        realmsServer.slots = this.cloneSlots(this.slots);
        realmsServer.expired = this.expired;
        realmsServer.expiredTrial = this.expiredTrial;
        realmsServer.daysLeft = this.daysLeft;
        realmsServer.worldType = this.worldType;
        realmsServer.ownerUUID = this.ownerUUID;
        realmsServer.minigameName = this.minigameName;
        realmsServer.activeSlot = this.activeSlot;
        realmsServer.minigameId = this.minigameId;
        realmsServer.minigameImage = this.minigameImage;
        realmsServer.parentWorldName = this.parentWorldName;
        realmsServer.parentRealmId = this.parentRealmId;
        realmsServer.activeVersion = this.activeVersion;
        realmsServer.compatibility = this.compatibility;
        return realmsServer;
    }

    public Map<Integer, RealmsWorldOptions> cloneSlots(Map<Integer, RealmsWorldOptions> slots) {
        Map<Integer, RealmsWorldOptions> map = Maps.newHashMap();

        for (Entry<Integer, RealmsWorldOptions> entry : slots.entrySet()) {
            map.put(entry.getKey(), entry.getValue().clone());
        }

        return map;
    }

    public boolean isSnapshotRealm() {
        return this.parentRealmId != -1L;
    }

    public boolean isMinigameActive() {
        return this.worldType == RealmsServer.WorldType.MINIGAME;
    }

    public String getWorldName(int slot) {
        return this.name + " (" + this.slots.get(slot).getSlotName(slot) + ")";
    }

    public ServerData toServerData(String ip) {
        return new ServerData(this.name, ip, ServerData.Type.REALM);
    }

    public enum Compatibility {
        UNVERIFIABLE,
        INCOMPATIBLE,
        RELEASE_TYPE_INCOMPATIBLE,
        NEEDS_DOWNGRADE,
        NEEDS_UPGRADE,
        COMPATIBLE;

        public boolean isCompatible() {
            return this == COMPATIBLE;
        }

        public boolean needsUpgrade() {
            return this == NEEDS_UPGRADE;
        }

        public boolean needsDowngrade() {
            return this == NEEDS_DOWNGRADE;
        }
    }

    public static class McoServerComparator implements Comparator<RealmsServer> {
        private final String refOwner;

        public McoServerComparator(String refOwner) {
            this.refOwner = refOwner;
        }

        public int compare(RealmsServer first, RealmsServer second) {
            return ComparisonChain.start()
                .compareTrueFirst(first.isSnapshotRealm(), second.isSnapshotRealm())
                .compareTrueFirst(first.state == RealmsServer.State.UNINITIALIZED, second.state == RealmsServer.State.UNINITIALIZED)
                .compareTrueFirst(first.expiredTrial, second.expiredTrial)
                .compareTrueFirst(first.owner.equals(this.refOwner), second.owner.equals(this.refOwner))
                .compareFalseFirst(first.expired, second.expired)
                .compareTrueFirst(first.state == RealmsServer.State.OPEN, second.state == RealmsServer.State.OPEN)
                .compare(first.id, second.id)
                .result();
        }
    }

    public enum State {
        CLOSED,
        OPEN,
        UNINITIALIZED;
    }

    public enum WorldType {
        NORMAL,
        MINIGAME,
        ADVENTUREMAP,
        EXPERIENCE,
        INSPIRATION;
    }
}
