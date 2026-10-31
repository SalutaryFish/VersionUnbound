package com.mojang.realmsclient.dto;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.util.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class RealmsNotification {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final String NOTIFICATION_UUID = "notificationUuid";
    private static final String DISMISSABLE = "dismissable";
    private static final String SEEN = "seen";
    private static final String TYPE = "type";
    private static final String VISIT_URL = "visitUrl";
    private static final String INFO_POPUP = "infoPopup";
    static final Component BUTTON_TEXT_FALLBACK = Component.translatable("mco.notification.visitUrl.buttonText.default");
    final UUID uuid;
    final boolean dismissable;
    final boolean seen;
    final String type;

    RealmsNotification(UUID uuid, boolean dismissable, boolean seen, String type) {
        this.uuid = uuid;
        this.dismissable = dismissable;
        this.seen = seen;
        this.type = type;
    }

    public boolean seen() {
        return this.seen;
    }

    public boolean dismissable() {
        return this.dismissable;
    }

    public UUID uuid() {
        return this.uuid;
    }

    public static List<RealmsNotification> parseList(String json) {
        List<RealmsNotification> list = new ArrayList<>();

        try {
            for (JsonElement jsonElement : JsonParser.parseString(json).getAsJsonObject().get("notifications").getAsJsonArray()) {
                list.add(parse(jsonElement.getAsJsonObject()));
            }
        } catch (Exception exception) {
            LOGGER.error("Could not parse list of RealmsNotifications", exception);
        }

        return list;
    }

    private static RealmsNotification parse(JsonObject json) {
        UUID uUID = JsonUtils.getUuidOr("notificationUuid", json, null);
        if (uUID == null) {
            throw new IllegalStateException("Missing required property notificationUuid");
        }

        boolean bl = JsonUtils.getBooleanOr("dismissable", json, true);
        boolean bl2 = JsonUtils.getBooleanOr("seen", json, false);
        String string = JsonUtils.getRequiredString("type", json);
        RealmsNotification realmsNotification = new RealmsNotification(uUID, bl, bl2, string);

        return switch (string) {
            case "visitUrl" -> RealmsNotification.VisitUrl.parse(realmsNotification, json);
            case "infoPopup" -> RealmsNotification.InfoPopup.parse(realmsNotification, json);
            default -> realmsNotification;
        };
    }

    public static class InfoPopup extends RealmsNotification {
        private static final String TITLE = "title";
        private static final String MESSAGE = "message";
        private static final String IMAGE = "image";
        private static final String URL_BUTTON = "urlButton";
        private final RealmsText title;
        private final RealmsText message;
        private final ResourceLocation image;
        @Nullable
        private final RealmsNotification.UrlButton urlButton;

        private InfoPopup(
            RealmsNotification notification, RealmsText title, RealmsText message, ResourceLocation image, @Nullable RealmsNotification.UrlButton urlButton
        ) {
            super(notification.uuid, notification.dismissable, notification.seen, notification.type);
            this.title = title;
            this.message = message;
            this.image = image;
            this.urlButton = urlButton;
        }

        public static RealmsNotification.InfoPopup parse(RealmsNotification notification, JsonObject json) {
            RealmsText realmsText = JsonUtils.getRequired("title", json, RealmsText::parse);
            RealmsText realmsText2 = JsonUtils.getRequired("message", json, RealmsText::parse);
            ResourceLocation resourceLocation = ResourceLocation.parse(JsonUtils.getRequiredString("image", json));
            RealmsNotification.UrlButton urlButton = JsonUtils.getOptional("urlButton", json, RealmsNotification.UrlButton::parse);
            return new RealmsNotification.InfoPopup(notification, realmsText, realmsText2, resourceLocation, urlButton);
        }

        @Nullable
        public PopupScreen buildScreen(Screen backgroundScreen, Consumer<UUID> uuidOutput) {
            Component component = this.title.createComponent();
            if (component == null) {
                RealmsNotification.LOGGER.warn("Realms info popup had title with no available translation: {}", this.title);
                return null;
            }

            PopupScreen.Builder builder = new PopupScreen.Builder(backgroundScreen, component)
                .setImage(this.image)
                .setMessage(this.message.createComponent(CommonComponents.EMPTY));
            if (this.urlButton != null) {
                builder.addButton(this.urlButton.urlText.createComponent(RealmsNotification.BUTTON_TEXT_FALLBACK), popupScreen -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.setScreen(new ConfirmLinkScreen(bl -> {
                        if (bl) {
                            Util.getPlatform().openUri(this.urlButton.url);
                            minecraft.setScreen(backgroundScreen);
                        } else {
                            minecraft.setScreen(popupScreen);
                        }
                    }, this.urlButton.url, true));
                    uuidOutput.accept(this.uuid());
                });
            }

            builder.addButton(CommonComponents.GUI_OK, popupScreen -> {
                popupScreen.onClose();
                uuidOutput.accept(this.uuid());
            });
            builder.onClose(() -> uuidOutput.accept(this.uuid()));
            return builder.build();
        }
    }

    record UrlButton(String url, RealmsText urlText) {
        private static final String URL = "url";
        private static final String URL_TEXT = "urlText";

        public static RealmsNotification.UrlButton parse(JsonObject json) {
            String string = JsonUtils.getRequiredString("url", json);
            RealmsText realmsText = JsonUtils.getRequired("urlText", json, RealmsText::parse);
            return new RealmsNotification.UrlButton(string, realmsText);
        }
    }

    public static class VisitUrl extends RealmsNotification {
        private static final String URL = "url";
        private static final String BUTTON_TEXT = "buttonText";
        private static final String MESSAGE = "message";
        private final String url;
        private final RealmsText buttonText;
        private final RealmsText message;

        private VisitUrl(RealmsNotification notification, String url, RealmsText buttonText, RealmsText message) {
            super(notification.uuid, notification.dismissable, notification.seen, notification.type);
            this.url = url;
            this.buttonText = buttonText;
            this.message = message;
        }

        public static RealmsNotification.VisitUrl parse(RealmsNotification notification, JsonObject json) {
            String string = JsonUtils.getRequiredString("url", json);
            RealmsText realmsText = JsonUtils.getRequired("buttonText", json, RealmsText::parse);
            RealmsText realmsText2 = JsonUtils.getRequired("message", json, RealmsText::parse);
            return new RealmsNotification.VisitUrl(notification, string, realmsText, realmsText2);
        }

        public Component getMessage() {
            return this.message.createComponent(Component.translatable("mco.notification.visitUrl.message.default"));
        }

        public Button buildOpenLinkButton(Screen lastScreen) {
            Component component = this.buttonText.createComponent(RealmsNotification.BUTTON_TEXT_FALLBACK);
            return Button.builder(component, ConfirmLinkScreen.confirmLink(lastScreen, this.url)).build();
        }
    }
}
