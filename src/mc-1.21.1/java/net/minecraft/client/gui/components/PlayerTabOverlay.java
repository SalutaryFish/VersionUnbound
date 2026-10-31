package net.minecraft.client.gui.components;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class PlayerTabOverlay {
    private static final ResourceLocation PING_UNKNOWN_SPRITE = ResourceLocation.withDefaultNamespace("icon/ping_unknown");
    private static final ResourceLocation PING_1_SPRITE = ResourceLocation.withDefaultNamespace("icon/ping_1");
    private static final ResourceLocation PING_2_SPRITE = ResourceLocation.withDefaultNamespace("icon/ping_2");
    private static final ResourceLocation PING_3_SPRITE = ResourceLocation.withDefaultNamespace("icon/ping_3");
    private static final ResourceLocation PING_4_SPRITE = ResourceLocation.withDefaultNamespace("icon/ping_4");
    private static final ResourceLocation PING_5_SPRITE = ResourceLocation.withDefaultNamespace("icon/ping_5");
    private static final ResourceLocation HEART_CONTAINER_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/container_blinking");
    private static final ResourceLocation HEART_CONTAINER_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation HEART_FULL_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full_blinking");
    private static final ResourceLocation HEART_HALF_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/half_blinking");
    private static final ResourceLocation HEART_ABSORBING_FULL_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full_blinking");
    private static final ResourceLocation HEART_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation HEART_ABSORBING_HALF_BLINKING_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half_blinking");
    private static final ResourceLocation HEART_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final Comparator<PlayerInfo> PLAYER_COMPARATOR = Comparator.<PlayerInfo>comparingInt(
            info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0
        )
        .thenComparing(info -> Optionull.mapOrDefault(info.getTeam(), PlayerTeam::getName, ""))
        .thenComparing(info -> info.getProfile().getName(), String::compareToIgnoreCase);
    public static final int MAX_ROWS_PER_COL = 20;
    private final Minecraft minecraft;
    private final Gui gui;
    @Nullable
    private Component footer;
    @Nullable
    private Component header;
    /**
     * Weither or not the playerlist is currently being rendered
     */
    private boolean visible;
    private final Map<UUID, PlayerTabOverlay.HealthState> healthStates = new Object2ObjectOpenHashMap<>();

    public PlayerTabOverlay(Minecraft minecraft, Gui gui) {
        this.minecraft = minecraft;
        this.gui = gui;
    }

    public Component getNameForDisplay(PlayerInfo playerInfo) {
        return playerInfo.getTabListDisplayName() != null
            ? this.decorateName(playerInfo, playerInfo.getTabListDisplayName().copy())
            : this.decorateName(playerInfo, PlayerTeam.formatNameForTeam(playerInfo.getTeam(), Component.literal(playerInfo.getProfile().getName())));
    }

    private Component decorateName(PlayerInfo playerInfo, MutableComponent name) {
        return playerInfo.getGameMode() == GameType.SPECTATOR ? name.withStyle(ChatFormatting.ITALIC) : name;
    }

    /**
     * Called by GuiIngame to update the information stored in the playerlist, does not actually render the list, however.
     */
    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            this.healthStates.clear();
            this.visible = visible;
            if (visible) {
                Component component = ComponentUtils.formatList(this.getPlayerInfos(), Component.literal(", "), this::getNameForDisplay);
                this.minecraft.getNarrator().sayNow(Component.translatable("multiplayer.player.list.narration", component));
            }
        }
    }

    private List<PlayerInfo> getPlayerInfos() {
        return this.minecraft.player.connection.getListedOnlinePlayers().stream().sorted(PLAYER_COMPARATOR).limit(80L).toList();
    }

    public void render(GuiGraphics guiGraphics, int width, Scoreboard scoreboard, @Nullable Objective objective) {
        List<PlayerInfo> list = this.getPlayerInfos();
        List<PlayerTabOverlay.ScoreDisplayEntry> list2 = new ArrayList<>(list.size());
        int i = this.minecraft.font.width(" ");
        int j = 0;
        int k = 0;

        for (PlayerInfo playerInfo : list) {
            Component component = this.getNameForDisplay(playerInfo);
            j = Math.max(j, this.minecraft.font.width(component));
            int l = 0;
            Component component2 = null;
            int m = 0;
            if (objective != null) {
                ScoreHolder scoreHolder = ScoreHolder.fromGameProfile(playerInfo.getProfile());
                ReadOnlyScoreInfo readOnlyScoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
                if (readOnlyScoreInfo != null) {
                    l = readOnlyScoreInfo.value();
                }

                if (objective.getRenderType() != ObjectiveCriteria.RenderType.HEARTS) {
                    NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT);
                    component2 = ReadOnlyScoreInfo.safeFormatValue(readOnlyScoreInfo, numberFormat);
                    m = this.minecraft.font.width(component2);
                    k = Math.max(k, m > 0 ? i + m : 0);
                }
            }

            list2.add(new PlayerTabOverlay.ScoreDisplayEntry(component, l, component2, m));
        }

        if (!this.healthStates.isEmpty()) {
            Set<UUID> set = list.stream().map(player -> player.getProfile().getId()).collect(Collectors.toSet());
            this.healthStates.keySet().removeIf(healthState -> !set.contains(healthState));
        }

        int n = list.size();
        int o = n;

        int p;
        for (p = 1; o > 20; o = (n + p - 1) / p) {
            p++;
        }

        boolean bl = this.minecraft.isLocalServer() || this.minecraft.getConnection().getConnection().isEncrypted();
        int q;
        if (objective != null) {
            if (objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS) {
                q = 90;
            } else {
                q = k;
            }
        } else {
            q = 0;
        }

        int m = Math.min(p * ((bl ? 9 : 0) + j + q + 13), width - 50) / p;
        int r = width / 2 - (m * p + (p - 1) * 5) / 2;
        int s = 10;
        int t = m * p + (p - 1) * 5;
        List<FormattedCharSequence> list3 = null;
        if (this.header != null) {
            list3 = this.minecraft.font.split(this.header, width - 50);

            for (FormattedCharSequence formattedCharSequence : list3) {
                t = Math.max(t, this.minecraft.font.width(formattedCharSequence));
            }
        }

        List<FormattedCharSequence> list4 = null;
        if (this.footer != null) {
            list4 = this.minecraft.font.split(this.footer, width - 50);

            for (FormattedCharSequence formattedCharSequence2 : list4) {
                t = Math.max(t, this.minecraft.font.width(formattedCharSequence2));
            }
        }

        if (list3 != null) {
            guiGraphics.fill(width / 2 - t / 2 - 1, s - 1, width / 2 + t / 2 + 1, s + list3.size() * 9, Integer.MIN_VALUE);

            for (FormattedCharSequence formattedCharSequence2 : list3) {
                int u = this.minecraft.font.width(formattedCharSequence2);
                guiGraphics.drawString(this.minecraft.font, formattedCharSequence2, width / 2 - u / 2, s, -1);
                s += 9;
            }

            s++;
        }

        guiGraphics.fill(width / 2 - t / 2 - 1, s - 1, width / 2 + t / 2 + 1, s + o * 9, Integer.MIN_VALUE);
        int v = this.minecraft.options.getBackgroundColor(553648127);

        for (int w = 0; w < n; w++) {
            int u = w / o;
            int x = w % o;
            int y = r + u * m + u * 5;
            int z = s + x * 9;
            guiGraphics.fill(y, z, y + m, z + 8, v);
            RenderSystem.enableBlend();
            if (w < list.size()) {
                PlayerInfo playerInfo2 = list.get(w);
                PlayerTabOverlay.ScoreDisplayEntry scoreDisplayEntry = list2.get(w);
                GameProfile gameProfile = playerInfo2.getProfile();
                if (bl) {
                    Player player = this.minecraft.level.getPlayerByUUID(gameProfile.getId());
                    boolean bl2 = player != null && LivingEntityRenderer.isEntityUpsideDown(player);
                    boolean bl3 = player != null && player.isModelPartShown(PlayerModelPart.HAT);
                    PlayerFaceRenderer.draw(guiGraphics, playerInfo2.getSkin().texture(), y, z, 8, bl3, bl2);
                    y += 9;
                }

                guiGraphics.drawString(this.minecraft.font, scoreDisplayEntry.name, y, z, playerInfo2.getGameMode() == GameType.SPECTATOR ? -1862270977 : -1);
                if (objective != null && playerInfo2.getGameMode() != GameType.SPECTATOR) {
                    int aa = y + j + 1;
                    int ab = aa + q;
                    if (ab - aa > 5) {
                        this.renderTablistScore(objective, z, scoreDisplayEntry, aa, ab, gameProfile.getId(), guiGraphics);
                    }
                }

                this.renderPingIcon(guiGraphics, m, y - (bl ? 9 : 0), z, playerInfo2);
            }
        }

        if (list4 != null) {
            s += o * 9 + 1;
            guiGraphics.fill(width / 2 - t / 2 - 1, s - 1, width / 2 + t / 2 + 1, s + list4.size() * 9, Integer.MIN_VALUE);

            for (FormattedCharSequence formattedCharSequence3 : list4) {
                int x = this.minecraft.font.width(formattedCharSequence3);
                guiGraphics.drawString(this.minecraft.font, formattedCharSequence3, width / 2 - x / 2, s, -1);
                s += 9;
            }
        }
    }

    protected void renderPingIcon(GuiGraphics guiGraphics, int width, int x, int y, PlayerInfo playerInfo) {
        ResourceLocation resourceLocation;
        if (playerInfo.getLatency() < 0) {
            resourceLocation = PING_UNKNOWN_SPRITE;
        } else if (playerInfo.getLatency() < 150) {
            resourceLocation = PING_5_SPRITE;
        } else if (playerInfo.getLatency() < 300) {
            resourceLocation = PING_4_SPRITE;
        } else if (playerInfo.getLatency() < 600) {
            resourceLocation = PING_3_SPRITE;
        } else if (playerInfo.getLatency() < 1000) {
            resourceLocation = PING_2_SPRITE;
        } else {
            resourceLocation = PING_1_SPRITE;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 100.0F);
        guiGraphics.blitSprite(resourceLocation, x + width - 11, y, 10, 8);
        guiGraphics.pose().popPose();
    }

    private void renderTablistScore(
        Objective objective, int y, PlayerTabOverlay.ScoreDisplayEntry displayEntry, int minX, int maxX, UUID playerUuid, GuiGraphics guiGraphics
    ) {
        if (objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS) {
            this.renderTablistHearts(y, minX, maxX, playerUuid, guiGraphics, displayEntry.score);
        } else if (displayEntry.formattedScore != null) {
            guiGraphics.drawString(this.minecraft.font, displayEntry.formattedScore, maxX - displayEntry.scoreWidth, y, 16777215);
        }
    }

    private void renderTablistHearts(int y, int minX, int maxX, UUID playerUuid, GuiGraphics guiGraphics, int health) {
        PlayerTabOverlay.HealthState healthState = this.healthStates.computeIfAbsent(playerUuid, uuid -> new PlayerTabOverlay.HealthState(health));
        healthState.update(health, this.gui.getGuiTicks());
        int i = Mth.positiveCeilDiv(Math.max(health, healthState.displayedValue()), 2);
        int j = Math.max(health, Math.max(healthState.displayedValue(), 20)) / 2;
        boolean bl = healthState.isBlinking(this.gui.getGuiTicks());
        if (i > 0) {
            int k = Mth.floor(Math.min((float)(maxX - minX - 4) / j, 9.0F));
            if (k <= 3) {
                float f = Mth.clamp(health / 20.0F, 0.0F, 1.0F);
                int l = (int)((1.0F - f) * 255.0F) << 16 | (int)(f * 255.0F) << 8;
                float g = health / 2.0F;
                Component component = Component.translatable("multiplayer.player.list.hp", g);
                Component component2;
                if (maxX - this.minecraft.font.width(component) >= minX) {
                    component2 = component;
                } else {
                    component2 = Component.literal(Float.toString(g));
                }

                guiGraphics.drawString(this.minecraft.font, component2, (maxX + minX - this.minecraft.font.width(component2)) / 2, y, l);
            } else {
                ResourceLocation resourceLocation = bl ? HEART_CONTAINER_BLINKING_SPRITE : HEART_CONTAINER_SPRITE;

                for (int l = i; l < j; l++) {
                    guiGraphics.blitSprite(resourceLocation, minX + l * k, y, 9, 9);
                }

                for (int l = 0; l < i; l++) {
                    guiGraphics.blitSprite(resourceLocation, minX + l * k, y, 9, 9);
                    if (bl) {
                        if (l * 2 + 1 < healthState.displayedValue()) {
                            guiGraphics.blitSprite(HEART_FULL_BLINKING_SPRITE, minX + l * k, y, 9, 9);
                        }

                        if (l * 2 + 1 == healthState.displayedValue()) {
                            guiGraphics.blitSprite(HEART_HALF_BLINKING_SPRITE, minX + l * k, y, 9, 9);
                        }
                    }

                    if (l * 2 + 1 < health) {
                        guiGraphics.blitSprite(l >= 10 ? HEART_ABSORBING_FULL_BLINKING_SPRITE : HEART_FULL_SPRITE, minX + l * k, y, 9, 9);
                    }

                    if (l * 2 + 1 == health) {
                        guiGraphics.blitSprite(l >= 10 ? HEART_ABSORBING_HALF_BLINKING_SPRITE : HEART_HALF_SPRITE, minX + l * k, y, 9, 9);
                    }
                }
            }
        }
    }

    public void setFooter(@Nullable Component footer) {
        this.footer = footer;
    }

    public void setHeader(@Nullable Component header) {
        this.header = header;
    }

    public void reset() {
        this.header = null;
        this.footer = null;
    }

    static class HealthState {
        private static final long DISPLAY_UPDATE_DELAY = 20L;
        private static final long DECREASE_BLINK_DURATION = 20L;
        private static final long INCREASE_BLINK_DURATION = 10L;
        private int lastValue;
        private int displayedValue;
        private long lastUpdateTick;
        private long blinkUntilTick;

        public HealthState(int displayedValue) {
            this.displayedValue = displayedValue;
            this.lastValue = displayedValue;
        }

        public void update(int value, long guiTicks) {
            if (value != this.lastValue) {
                long l = value < this.lastValue ? 20L : 10L;
                this.blinkUntilTick = guiTicks + l;
                this.lastValue = value;
                this.lastUpdateTick = guiTicks;
            }

            if (guiTicks - this.lastUpdateTick > 20L) {
                this.displayedValue = value;
            }
        }

        public int displayedValue() {
            return this.displayedValue;
        }

        public boolean isBlinking(long guiTicks) {
            return this.blinkUntilTick > guiTicks && (this.blinkUntilTick - guiTicks) % 6L >= 3L;
        }
    }

    record ScoreDisplayEntry(Component name, int score, @Nullable Component formattedScore, int scoreWidth) {
    }
}
