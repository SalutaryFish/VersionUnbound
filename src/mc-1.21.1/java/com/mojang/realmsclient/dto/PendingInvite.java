package com.mojang.realmsclient.dto;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.util.Date;
import java.util.UUID;
import net.minecraft.Util;
import org.slf4j.Logger;

public class PendingInvite extends ValueObject {
    private static final Logger LOGGER = LogUtils.getLogger();
    public String invitationId;
    public String realmName;
    public String realmOwnerName;
    public UUID realmOwnerUuid;
    public Date date;

    public static PendingInvite parse(JsonObject json) {
        PendingInvite pendingInvite = new PendingInvite();

        try {
            pendingInvite.invitationId = JsonUtils.getStringOr("invitationId", json, "");
            pendingInvite.realmName = JsonUtils.getStringOr("worldName", json, "");
            pendingInvite.realmOwnerName = JsonUtils.getStringOr("worldOwnerName", json, "");
            pendingInvite.realmOwnerUuid = JsonUtils.getUuidOr("worldOwnerUuid", json, Util.NIL_UUID);
            pendingInvite.date = JsonUtils.getDateOr("date", json);
        } catch (Exception exception) {
            LOGGER.error("Could not parse PendingInvite: {}", exception.getMessage());
        }

        return pendingInvite;
    }
}
