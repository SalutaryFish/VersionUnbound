package com.mojang.realmsclient.util;

import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.Date;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

public class RealmsUtil {
    private static final Component RIGHT_NOW = Component.translatable("mco.util.time.now");
    private static final int MINUTES = 60;
    private static final int HOURS = 3600;
    private static final int DAYS = 86400;

    public static Component convertToAgePresentation(long millis) {
        if (millis < 0L) {
            return RIGHT_NOW;
        } else {
            long l = millis / 1000L;
            if (l < 60L) {
                return Component.translatable("mco.time.secondsAgo", l);
            } else if (l < 3600L) {
                long m = l / 60L;
                return Component.translatable("mco.time.minutesAgo", m);
            } else if (l < 86400L) {
                long m = l / 3600L;
                return Component.translatable("mco.time.hoursAgo", m);
            } else {
                long m = l / 86400L;
                return Component.translatable("mco.time.daysAgo", m);
            }
        }
    }

    public static Component convertToAgePresentationFromInstant(Date date) {
        return convertToAgePresentation(System.currentTimeMillis() - date.getTime());
    }

    public static void renderPlayerFace(GuiGraphics guiGraphics, int x, int y, int size, UUID playerUuid) {
        Minecraft minecraft = Minecraft.getInstance();
        ProfileResult profileResult = minecraft.getMinecraftSessionService().fetchProfile(playerUuid, false);
        PlayerSkin playerSkin = profileResult != null ? minecraft.getSkinManager().getInsecureSkin(profileResult.profile()) : DefaultPlayerSkin.get(playerUuid);
        PlayerFaceRenderer.draw(guiGraphics, playerSkin.texture(), x, y, size);
    }
}
