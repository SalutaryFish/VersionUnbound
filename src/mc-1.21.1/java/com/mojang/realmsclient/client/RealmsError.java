package com.mojang.realmsclient.client;

import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.exception.RealmsHttpException;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

public interface RealmsError {
    Component NO_MESSAGE = Component.translatable("mco.errorMessage.noDetails");
    Logger LOGGER = LogUtils.getLogger();

    int errorCode();

    Component errorMessage();

    String logMessage();

    static RealmsError parse(int httpCode, String payload) {
        if (httpCode == 429) {
            return RealmsError.CustomError.SERVICE_BUSY;
        }

        if (Strings.isNullOrEmpty(payload)) {
            return RealmsError.CustomError.noPayload(httpCode);
        }

        try {
            JsonObject jsonObject = JsonParser.parseString(payload).getAsJsonObject();
            String string = GsonHelper.getAsString(jsonObject, "reason", null);
            String string2 = GsonHelper.getAsString(jsonObject, "errorMsg", null);
            int i = GsonHelper.getAsInt(jsonObject, "errorCode", -1);
            if (string2 != null || string != null || i != -1) {
                return new RealmsError.ErrorWithJsonPayload(httpCode, i != -1 ? i : httpCode, string, string2);
            }
        } catch (Exception exception) {
            LOGGER.error("Could not parse RealmsError", exception);
        }

        return new RealmsError.ErrorWithRawPayload(httpCode, payload);
    }

    record AuthenticationError(String message) implements RealmsError {
        public static final int ERROR_CODE = 401;

        @Override
        public int errorCode() {
            return 401;
        }

        @Override
        public Component errorMessage() {
            return Component.literal(this.message);
        }

        @Override
        public String logMessage() {
            return String.format(Locale.ROOT, "Realms authentication error with message '%s'", this.message);
        }
    }

    record CustomError(int httpCode, @Nullable Component payload) implements RealmsError {
        public static final RealmsError.CustomError SERVICE_BUSY = new RealmsError.CustomError(429, Component.translatable("mco.errorMessage.serviceBusy"));
        public static final Component RETRY_MESSAGE = Component.translatable("mco.errorMessage.retry");

        public static RealmsError.CustomError unknownCompatibilityResponse(String payload) {
            return new RealmsError.CustomError(500, Component.translatable("mco.errorMessage.realmsService.unknownCompatibility", payload));
        }

        public static RealmsError.CustomError connectivityError(RealmsHttpException payload) {
            return new RealmsError.CustomError(500, Component.translatable("mco.errorMessage.realmsService.connectivity", payload.getMessage()));
        }

        public static RealmsError.CustomError retry(int httpCode) {
            return new RealmsError.CustomError(httpCode, RETRY_MESSAGE);
        }

        public static RealmsError.CustomError noPayload(int httpCode) {
            return new RealmsError.CustomError(httpCode, null);
        }

        @Override
        public int errorCode() {
            return this.httpCode;
        }

        @Override
        public Component errorMessage() {
            return this.payload != null ? this.payload : NO_MESSAGE;
        }

        @Override
        public String logMessage() {
            return this.payload != null
                ? String.format(Locale.ROOT, "Realms service error (%d) with message '%s'", this.httpCode, this.payload.getString())
                : String.format(Locale.ROOT, "Realms service error (%d) with no payload", this.httpCode);
        }
    }

    record ErrorWithJsonPayload(int httpCode, int code, @Nullable String reason, @Nullable String message) implements RealmsError {
        @Override
        public int errorCode() {
            return this.code;
        }

        @Override
        public Component errorMessage() {
            String string = "mco.errorMessage." + this.code;
            if (I18n.exists(string)) {
                return Component.translatable(string);
            }

            if (this.reason != null) {
                String string2 = "mco.errorReason." + this.reason;
                if (I18n.exists(string2)) {
                    return Component.translatable(string2);
                }
            }

            return this.message != null ? Component.literal(this.message) : NO_MESSAGE;
        }

        @Override
        public String logMessage() {
            return String.format(Locale.ROOT, "Realms service error (%d/%d/%s) with message '%s'", this.httpCode, this.code, this.reason, this.message);
        }
    }

    record ErrorWithRawPayload(int httpCode, String payload) implements RealmsError {
        @Override
        public int errorCode() {
            return this.httpCode;
        }

        @Override
        public Component errorMessage() {
            return Component.literal(this.payload);
        }

        @Override
        public String logMessage() {
            return String.format(Locale.ROOT, "Realms service error (%d) with raw payload '%s'", this.httpCode, this.payload);
        }
    }
}
