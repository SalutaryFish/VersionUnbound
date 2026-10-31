package net.minecraft.client.gui;

import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.components.SubtitleOverlay;
import net.minecraft.client.gui.components.spectator.SpectatorGui;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.MobEffectTextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix4fStack;

public class Gui {
    private static final ResourceLocation CROSSHAIR_SPRITE = ResourceLocation.withDefaultNamespace("hud/crosshair");
    private static final ResourceLocation CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_full");
    private static final ResourceLocation CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace(
        "hud/crosshair_attack_indicator_background"
    );
    private static final ResourceLocation CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace(
        "hud/crosshair_attack_indicator_progress"
    );
    private static final ResourceLocation EFFECT_BACKGROUND_AMBIENT_SPRITE = ResourceLocation.withDefaultNamespace("hud/effect_background_ambient");
    private static final ResourceLocation EFFECT_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/effect_background");
    private static final ResourceLocation HOTBAR_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation HOTBAR_SELECTION_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
    private static final ResourceLocation HOTBAR_OFFHAND_LEFT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_left");
    private static final ResourceLocation HOTBAR_OFFHAND_RIGHT_SPRITE = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_right");
    private static final ResourceLocation HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace(
        "hud/hotbar_attack_indicator_background"
    );
    private static final ResourceLocation HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace(
        "hud/hotbar_attack_indicator_progress"
    );
    private static final ResourceLocation JUMP_BAR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/jump_bar_background");
    private static final ResourceLocation JUMP_BAR_COOLDOWN_SPRITE = ResourceLocation.withDefaultNamespace("hud/jump_bar_cooldown");
    private static final ResourceLocation JUMP_BAR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/jump_bar_progress");
    private static final ResourceLocation EXPERIENCE_BAR_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation EXPERIENCE_BAR_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
    private static final ResourceLocation ARMOR_EMPTY_SPRITE = ResourceLocation.withDefaultNamespace("hud/armor_empty");
    private static final ResourceLocation ARMOR_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/armor_half");
    private static final ResourceLocation ARMOR_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/armor_full");
    private static final ResourceLocation FOOD_EMPTY_HUNGER_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_empty_hunger");
    private static final ResourceLocation FOOD_HALF_HUNGER_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_half_hunger");
    private static final ResourceLocation FOOD_FULL_HUNGER_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_full_hunger");
    private static final ResourceLocation FOOD_EMPTY_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final ResourceLocation FOOD_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_half");
    private static final ResourceLocation FOOD_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/food_full");
    private static final ResourceLocation AIR_SPRITE = ResourceLocation.withDefaultNamespace("hud/air");
    private static final ResourceLocation AIR_BURSTING_SPRITE = ResourceLocation.withDefaultNamespace("hud/air_bursting");
    private static final ResourceLocation HEART_VEHICLE_CONTAINER_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/vehicle_container");
    private static final ResourceLocation HEART_VEHICLE_FULL_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/vehicle_full");
    private static final ResourceLocation HEART_VEHICLE_HALF_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/vehicle_half");
    private static final ResourceLocation VIGNETTE_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/vignette.png");
    private static final ResourceLocation PUMPKIN_BLUR_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/pumpkinblur.png");
    private static final ResourceLocation SPYGLASS_SCOPE_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/spyglass_scope.png");
    private static final ResourceLocation POWDER_SNOW_OUTLINE_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/powder_snow_outline.png");
    private static final Comparator<PlayerScoreEntry> SCORE_DISPLAY_ORDER = Comparator.comparing(PlayerScoreEntry::value)
        .reversed()
        .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
    private static final Component DEMO_EXPIRED_TEXT = Component.translatable("demo.demoExpired");
    private static final Component SAVING_TEXT = Component.translatable("menu.savingLevel");
    private static final float MIN_CROSSHAIR_ATTACK_SPEED = 5.0F;
    private static final int NUM_HEARTS_PER_ROW = 10;
    private static final int LINE_HEIGHT = 10;
    private static final String SPACER = ": ";
    private static final float PORTAL_OVERLAY_ALPHA_MIN = 0.2F;
    private static final int HEART_SIZE = 9;
    private static final int HEART_SEPARATION = 8;
    private static final float AUTOSAVE_FADE_SPEED_FACTOR = 0.2F;
    private final RandomSource random = RandomSource.create();
    private final Minecraft minecraft;
    private final ChatComponent chat;
    private int tickCount;
    @Nullable
    private Component overlayMessageString;
    private int overlayMessageTime;
    private boolean animateOverlayMessageColor;
    private boolean chatDisabledByPlayerShown;
    public float vignetteBrightness = 1.0F;
    private int toolHighlightTimer;
    private ItemStack lastToolHighlight = ItemStack.EMPTY;
    private final DebugScreenOverlay debugOverlay;
    private final SubtitleOverlay subtitleOverlay;
    /**
     * The spectator GUI for this in-game GUI instance
     */
    private final SpectatorGui spectatorGui;
    private final PlayerTabOverlay tabList;
    private final BossHealthOverlay bossOverlay;
    /**
     * A timer for the current title and subtitle displayed
     */
    private int titleTime;
    /**
     * The current title displayed
     */
    @Nullable
    private Component title;
    /**
     * The current sub-title displayed
     */
    @Nullable
    private Component subtitle;
    /**
     * The time that the title take to fade in
     */
    private int titleFadeInTime;
    /**
     * The time that the title is display
     */
    private int titleStayTime;
    /**
     * The time that the title take to fade out
     */
    private int titleFadeOutTime;
    private int lastHealth;
    private int displayHealth;
    /**
     * The last recorded system time
     */
    private long lastHealthTime;
    /**
     * Used with updateCounter to make the heart bar flash
     */
    private long healthBlinkTime;
    private float autosaveIndicatorValue;
    private float lastAutosaveIndicatorValue;
    private final LayeredDraw layers = new LayeredDraw();
    private float scopeScale;

    public Gui(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.debugOverlay = new DebugScreenOverlay(minecraft);
        this.spectatorGui = new SpectatorGui(minecraft);
        this.chat = new ChatComponent(minecraft);
        this.tabList = new PlayerTabOverlay(minecraft, this);
        this.bossOverlay = new BossHealthOverlay(minecraft);
        this.subtitleOverlay = new SubtitleOverlay(minecraft);
        this.resetTitleTimes();
        LayeredDraw layeredDraw = new LayeredDraw()
            .add(this::renderCameraOverlays)
            .add(this::renderCrosshair)
            .add(this::renderHotbarAndDecorations)
            .add(this::renderExperienceLevel)
            .add(this::renderEffects)
            .add((guiGraphics, deltaTracker) -> this.bossOverlay.render(guiGraphics));
        LayeredDraw layeredDraw2 = new LayeredDraw()
            .add(this::renderDemoOverlay)
            .add((guiGraphics, deltaTracker) -> {
                if (this.debugOverlay.showDebugScreen()) {
                    this.debugOverlay.render(guiGraphics);
                }
            })
            .add(this::renderScoreboardSidebar)
            .add(this::renderOverlayMessage)
            .add(this::renderTitle)
            .add(this::renderChat)
            .add(this::renderTabList)
            .add((guiGraphics, deltaTracker) -> this.subtitleOverlay.render(guiGraphics));
        this.layers.add(layeredDraw, () -> !minecraft.options.hideGui).add(this::renderSleepOverlay).add(layeredDraw2, () -> !minecraft.options.hideGui);
    }

    public void resetTitleTimes() {
        this.titleFadeInTime = 10;
        this.titleStayTime = 70;
        this.titleFadeOutTime = 20;
    }

    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        RenderSystem.enableDepthTest();
        this.layers.render(guiGraphics, deltaTracker);
        RenderSystem.disableDepthTest();
    }

    private void renderCameraOverlays(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (Minecraft.useFancyGraphics()) {
            this.renderVignette(guiGraphics, this.minecraft.getCameraEntity());
        }

        float f = deltaTracker.getGameTimeDeltaTicks();
        this.scopeScale = Mth.lerp(0.5F * f, this.scopeScale, 1.125F);
        if (this.minecraft.options.getCameraType().isFirstPerson()) {
            if (this.minecraft.player.isScoping()) {
                this.renderSpyglassOverlay(guiGraphics, this.scopeScale);
            } else {
                this.scopeScale = 0.5F;
                ItemStack itemStack = this.minecraft.player.getInventory().getArmor(3);
                if (itemStack.is(Blocks.CARVED_PUMPKIN.asItem())) {
                    this.renderTextureOverlay(guiGraphics, PUMPKIN_BLUR_LOCATION, 1.0F);
                }
            }
        }

        if (this.minecraft.player.getTicksFrozen() > 0) {
            this.renderTextureOverlay(guiGraphics, POWDER_SNOW_OUTLINE_LOCATION, this.minecraft.player.getPercentFrozen());
        }

        float g = Mth.lerp(
            deltaTracker.getGameTimeDeltaPartialTick(false), this.minecraft.player.oSpinningEffectIntensity, this.minecraft.player.spinningEffectIntensity
        );
        if (g > 0.0F && !this.minecraft.player.hasEffect(MobEffects.CONFUSION)) {
            this.renderPortalOverlay(guiGraphics, g);
        }
    }

    private void renderSleepOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (this.minecraft.player.getSleepTimer() > 0) {
            this.minecraft.getProfiler().push("sleep");
            float f = this.minecraft.player.getSleepTimer();
            float g = f / 100.0F;
            if (g > 1.0F) {
                g = 1.0F - (f - 100.0F) / 10.0F;
            }

            int i = (int)(220.0F * g) << 24 | 1052704;
            guiGraphics.fill(RenderType.guiOverlay(), 0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), i);
            this.minecraft.getProfiler().pop();
        }
    }

    private void renderOverlayMessage(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Font font = this.getFont();
        if (this.overlayMessageString != null && this.overlayMessageTime > 0) {
            this.minecraft.getProfiler().push("overlayMessage");
            float f = this.overlayMessageTime - deltaTracker.getGameTimeDeltaPartialTick(false);
            int i = (int)(f * 255.0F / 20.0F);
            if (i > 255) {
                i = 255;
            }

            if (i > 8) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(guiGraphics.guiWidth() / 2, guiGraphics.guiHeight() - 68, 0.0F);
                int j;
                if (this.animateOverlayMessageColor) {
                    j = Mth.hsvToArgb(f / 50.0F, 0.7F, 0.6F, i);
                } else {
                    j = FastColor.ARGB32.color(i, -1);
                }

                int k = font.width(this.overlayMessageString);
                guiGraphics.drawStringWithBackdrop(font, this.overlayMessageString, -k / 2, -4, k, j);
                guiGraphics.pose().popPose();
            }

            this.minecraft.getProfiler().pop();
        }
    }

    private void renderTitle(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (this.title != null && this.titleTime > 0) {
            Font font = this.getFont();
            this.minecraft.getProfiler().push("titleAndSubtitle");
            float f = this.titleTime - deltaTracker.getGameTimeDeltaPartialTick(false);
            int i = 255;
            if (this.titleTime > this.titleFadeOutTime + this.titleStayTime) {
                float g = this.titleFadeInTime + this.titleStayTime + this.titleFadeOutTime - f;
                i = (int)(g * 255.0F / this.titleFadeInTime);
            }

            if (this.titleTime <= this.titleFadeOutTime) {
                i = (int)(f * 255.0F / this.titleFadeOutTime);
            }

            i = Mth.clamp(i, 0, 255);
            if (i > 8) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(guiGraphics.guiWidth() / 2, guiGraphics.guiHeight() / 2, 0.0F);
                guiGraphics.pose().pushPose();
                guiGraphics.pose().scale(4.0F, 4.0F, 4.0F);
                int j = font.width(this.title);
                int k = FastColor.ARGB32.color(i, -1);
                guiGraphics.drawStringWithBackdrop(font, this.title, -j / 2, -10, j, k);
                guiGraphics.pose().popPose();
                if (this.subtitle != null) {
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().scale(2.0F, 2.0F, 2.0F);
                    int l = font.width(this.subtitle);
                    guiGraphics.drawStringWithBackdrop(font, this.subtitle, -l / 2, 5, l, k);
                    guiGraphics.pose().popPose();
                }

                guiGraphics.pose().popPose();
            }

            this.minecraft.getProfiler().pop();
        }
    }

    private void renderChat(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!this.chat.isChatFocused()) {
            Window window = this.minecraft.getWindow();
            int i = Mth.floor(this.minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth());
            int j = Mth.floor(this.minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight());
            this.chat.render(guiGraphics, this.tickCount, i, j, false);
        }
    }

    private void renderScoreboardSidebar(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Scoreboard scoreboard = this.minecraft.level.getScoreboard();
        Objective objective = null;
        PlayerTeam playerTeam = scoreboard.getPlayersTeam(this.minecraft.player.getScoreboardName());
        if (playerTeam != null) {
            DisplaySlot displaySlot = DisplaySlot.teamColorToSlot(playerTeam.getColor());
            if (displaySlot != null) {
                objective = scoreboard.getDisplayObjective(displaySlot);
            }
        }

        Objective objective2 = objective != null ? objective : scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective2 != null) {
            this.displayScoreboardSidebar(guiGraphics, objective2);
        }
    }

    private void renderTabList(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Scoreboard scoreboard = this.minecraft.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
        if (!this.minecraft.options.keyPlayerList.isDown()
            || this.minecraft.isLocalServer() && this.minecraft.player.connection.getListedOnlinePlayers().size() <= 1 && objective == null) {
            this.tabList.setVisible(false);
        } else {
            this.tabList.setVisible(true);
            this.tabList.render(guiGraphics, guiGraphics.guiWidth(), scoreboard, objective);
        }
    }

    private void renderCrosshair(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Options options = this.minecraft.options;
        if (options.getCameraType().isFirstPerson()) {
            if (this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR || this.canRenderCrosshairForSpectator(this.minecraft.hitResult)) {
                RenderSystem.enableBlend();
                if (this.debugOverlay.showDebugScreen() && !this.minecraft.player.isReducedDebugInfo() && !options.reducedDebugInfo().get()) {
                    Camera camera = this.minecraft.gameRenderer.getMainCamera();
                    Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
                    matrix4fStack.pushMatrix();
                    matrix4fStack.mul(guiGraphics.pose().last().pose());
                    matrix4fStack.translate(guiGraphics.guiWidth() / 2, guiGraphics.guiHeight() / 2, 0.0F);
                    matrix4fStack.rotateX(-camera.getXRot() * (float) (Math.PI / 180.0));
                    matrix4fStack.rotateY(camera.getYRot() * (float) (Math.PI / 180.0));
                    matrix4fStack.scale(-1.0F, -1.0F, -1.0F);
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.renderCrosshair(10);
                    matrix4fStack.popMatrix();
                    RenderSystem.applyModelViewMatrix();
                } else {
                    RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO
                    );
                    int i = 15;
                    guiGraphics.blitSprite(CROSSHAIR_SPRITE, (guiGraphics.guiWidth() - 15) / 2, (guiGraphics.guiHeight() - 15) / 2, 15, 15);
                    if (this.minecraft.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
                        float f = this.minecraft.player.getAttackStrengthScale(0.0F);
                        boolean bl = false;
                        if (this.minecraft.crosshairPickEntity != null && this.minecraft.crosshairPickEntity instanceof LivingEntity && f >= 1.0F) {
                            bl = this.minecraft.player.getCurrentItemAttackStrengthDelay() > 5.0F;
                            bl &= this.minecraft.crosshairPickEntity.isAlive();
                        }

                        int j = guiGraphics.guiHeight() / 2 - 7 + 16;
                        int k = guiGraphics.guiWidth() / 2 - 8;
                        if (bl) {
                            guiGraphics.blitSprite(CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE, k, j, 16, 16);
                        } else if (f < 1.0F) {
                            int l = (int)(f * 17.0F);
                            guiGraphics.blitSprite(CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE, k, j, 16, 4);
                            guiGraphics.blitSprite(CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE, 16, 4, 0, 0, k, j, l, 4);
                        }
                    }

                    RenderSystem.defaultBlendFunc();
                }

                RenderSystem.disableBlend();
            }
        }
    }

    /**
     * Checks if the crosshair can be rendered for a spectator based on the provided {@link HitResult}.
     * <p>
     * @return {@code true} if the crosshair can be rendered for a spectator, {@code false} otherwise.
     *
     * @param rayTrace the result of a ray trace operation.
     */
    private boolean canRenderCrosshairForSpectator(@Nullable HitResult rayTrace) {
        if (rayTrace == null) {
            return false;
        } else if (rayTrace.getType() == HitResult.Type.ENTITY) {
            return ((EntityHitResult)rayTrace).getEntity() instanceof MenuProvider;
        } else if (rayTrace.getType() == HitResult.Type.BLOCK) {
            BlockPos blockPos = ((BlockHitResult)rayTrace).getBlockPos();
            Level level = this.minecraft.level;
            return level.getBlockState(blockPos).getMenuProvider(level, blockPos) != null;
        } else {
            return false;
        }
    }

    private void renderEffects(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Collection<MobEffectInstance> collection = this.minecraft.player.getActiveEffects();
        if (!collection.isEmpty()
            && !(
                this.minecraft.screen instanceof EffectRenderingInventoryScreen effectRenderingInventoryScreen
                    && effectRenderingInventoryScreen.canSeeEffects()
            )) {
            RenderSystem.enableBlend();
            int i = 0;
            int j = 0;
            MobEffectTextureManager mobEffectTextureManager = this.minecraft.getMobEffectTextures();
            List<Runnable> list = Lists.newArrayListWithExpectedSize(collection.size());

            for (MobEffectInstance mobEffectInstance : Ordering.natural().reverse().sortedCopy(collection)) {
                Holder<MobEffect> holder = mobEffectInstance.getEffect();
                if (mobEffectInstance.showIcon()) {
                    int k = guiGraphics.guiWidth();
                    int l = 1;
                    if (this.minecraft.isDemo()) {
                        l += 15;
                    }

                    if (holder.value().isBeneficial()) {
                        i++;
                        k -= 25 * i;
                    } else {
                        j++;
                        k -= 25 * j;
                        l += 26;
                    }

                    float f = 1.0F;
                    if (mobEffectInstance.isAmbient()) {
                        guiGraphics.blitSprite(EFFECT_BACKGROUND_AMBIENT_SPRITE, k, l, 24, 24);
                    } else {
                        guiGraphics.blitSprite(EFFECT_BACKGROUND_SPRITE, k, l, 24, 24);
                        if (mobEffectInstance.endsWithin(200)) {
                            int m = mobEffectInstance.getDuration();
                            int n = 10 - m / 20;
                            f = Mth.clamp(m / 10.0F / 5.0F * 0.5F, 0.0F, 0.5F)
                                + Mth.cos(m * (float) Math.PI / 5.0F) * Mth.clamp(n / 10.0F * 0.25F, 0.0F, 0.25F);
                        }
                    }

                    TextureAtlasSprite textureAtlasSprite = mobEffectTextureManager.get(holder);
                    int n = k;
                    int o = l;
                    float g = f;
                    list.add(() -> {
                        guiGraphics.setColor(1.0F, 1.0F, 1.0F, g);
                        guiGraphics.blit(n + 3, o + 3, 0, 18, 18, textureAtlasSprite);
                        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                    });
                }
            }

            list.forEach(Runnable::run);
            RenderSystem.disableBlend();
        }
    }

    private void renderHotbarAndDecorations(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (this.minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            this.spectatorGui.renderHotbar(guiGraphics);
        } else {
            this.renderItemHotbar(guiGraphics, deltaTracker);
        }

        int i = guiGraphics.guiWidth() / 2 - 91;
        PlayerRideableJumping playerRideableJumping = this.minecraft.player.jumpableVehicle();
        if (playerRideableJumping != null) {
            this.renderJumpMeter(playerRideableJumping, guiGraphics, i);
        } else if (this.isExperienceBarVisible()) {
            this.renderExperienceBar(guiGraphics, i);
        }

        if (this.minecraft.gameMode.canHurtPlayer()) {
            this.renderPlayerHealth(guiGraphics);
        }

        this.renderVehicleHealth(guiGraphics);
        if (this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) {
            this.renderSelectedItemName(guiGraphics);
        } else if (this.minecraft.player.isSpectator()) {
            this.spectatorGui.renderTooltip(guiGraphics);
        }
    }

    private void renderItemHotbar(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Player player = this.getCameraPlayer();
        if (player != null) {
            ItemStack itemStack = player.getOffhandItem();
            HumanoidArm humanoidArm = player.getMainArm().getOpposite();
            int i = guiGraphics.guiWidth() / 2;
            int j = 182;
            int k = 91;
            RenderSystem.enableBlend();
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, -90.0F);
            guiGraphics.blitSprite(HOTBAR_SPRITE, i - 91, guiGraphics.guiHeight() - 22, 182, 22);
            guiGraphics.blitSprite(HOTBAR_SELECTION_SPRITE, i - 91 - 1 + player.getInventory().selected * 20, guiGraphics.guiHeight() - 22 - 1, 24, 23);
            if (!itemStack.isEmpty()) {
                if (humanoidArm == HumanoidArm.LEFT) {
                    guiGraphics.blitSprite(HOTBAR_OFFHAND_LEFT_SPRITE, i - 91 - 29, guiGraphics.guiHeight() - 23, 29, 24);
                } else {
                    guiGraphics.blitSprite(HOTBAR_OFFHAND_RIGHT_SPRITE, i + 91, guiGraphics.guiHeight() - 23, 29, 24);
                }
            }

            guiGraphics.pose().popPose();
            RenderSystem.disableBlend();
            int l = 1;

            for (int m = 0; m < 9; m++) {
                int n = i - 90 + m * 20 + 2;
                int o = guiGraphics.guiHeight() - 16 - 3;
                this.renderSlot(guiGraphics, n, o, deltaTracker, player, player.getInventory().items.get(m), l++);
            }

            if (!itemStack.isEmpty()) {
                int m = guiGraphics.guiHeight() - 16 - 3;
                if (humanoidArm == HumanoidArm.LEFT) {
                    this.renderSlot(guiGraphics, i - 91 - 26, m, deltaTracker, player, itemStack, l++);
                } else {
                    this.renderSlot(guiGraphics, i + 91 + 10, m, deltaTracker, player, itemStack, l++);
                }
            }

            if (this.minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
                RenderSystem.enableBlend();
                float f = this.minecraft.player.getAttackStrengthScale(0.0F);
                if (f < 1.0F) {
                    int n = guiGraphics.guiHeight() - 20;
                    int o = i + 91 + 6;
                    if (humanoidArm == HumanoidArm.RIGHT) {
                        o = i - 91 - 22;
                    }

                    int p = (int)(f * 19.0F);
                    guiGraphics.blitSprite(HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE, o, n, 18, 18);
                    guiGraphics.blitSprite(HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE, 18, 18, 0, 18 - p, o, n + 18 - p, 18, p);
                }

                RenderSystem.disableBlend();
            }
        }
    }

    /**
     * Renders the jump meter for a rideable entity on the screen using the provided rideable object, GuiGraphics object, and x-coordinate.
     *
     * @param rideable    the PlayerRideableJumping object representing the rideable
     *                    entity.
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param x           the x-coordinate for rendering the jump meter.
     */
    private void renderJumpMeter(PlayerRideableJumping rideable, GuiGraphics guiGraphics, int x) {
        this.minecraft.getProfiler().push("jumpBar");
        float f = this.minecraft.player.getJumpRidingScale();
        int i = 182;
        int j = (int)(f * 183.0F);
        int k = guiGraphics.guiHeight() - 32 + 3;
        RenderSystem.enableBlend();
        guiGraphics.blitSprite(JUMP_BAR_BACKGROUND_SPRITE, x, k, 182, 5);
        if (rideable.getJumpCooldown() > 0) {
            guiGraphics.blitSprite(JUMP_BAR_COOLDOWN_SPRITE, x, k, 182, 5);
        } else if (j > 0) {
            guiGraphics.blitSprite(JUMP_BAR_PROGRESS_SPRITE, 182, 5, 0, 0, x, k, j, 5);
        }

        RenderSystem.disableBlend();
        this.minecraft.getProfiler().pop();
    }

    /**
     * Renders the experience bar on the screen using the provided GuiGraphics object and x-coordinate.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param x           the x-coordinate for rendering the experience bar.
     */
    private void renderExperienceBar(GuiGraphics guiGraphics, int x) {
        this.minecraft.getProfiler().push("expBar");
        int i = this.minecraft.player.getXpNeededForNextLevel();
        if (i > 0) {
            int j = 182;
            int k = (int)(this.minecraft.player.experienceProgress * 183.0F);
            int l = guiGraphics.guiHeight() - 32 + 3;
            RenderSystem.enableBlend();
            guiGraphics.blitSprite(EXPERIENCE_BAR_BACKGROUND_SPRITE, x, l, 182, 5);
            if (k > 0) {
                guiGraphics.blitSprite(EXPERIENCE_BAR_PROGRESS_SPRITE, 182, 5, 0, 0, x, l, k, 5);
            }

            RenderSystem.disableBlend();
        }

        this.minecraft.getProfiler().pop();
    }

    private void renderExperienceLevel(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        int i = this.minecraft.player.experienceLevel;
        if (this.isExperienceBarVisible() && i > 0) {
            this.minecraft.getProfiler().push("expLevel");
            String string = i + "";
            int j = (guiGraphics.guiWidth() - this.getFont().width(string)) / 2;
            int k = guiGraphics.guiHeight() - 31 - 4;
            guiGraphics.drawString(this.getFont(), string, j + 1, k, 0, false);
            guiGraphics.drawString(this.getFont(), string, j - 1, k, 0, false);
            guiGraphics.drawString(this.getFont(), string, j, k + 1, 0, false);
            guiGraphics.drawString(this.getFont(), string, j, k - 1, 0, false);
            guiGraphics.drawString(this.getFont(), string, j, k, 8453920, false);
            this.minecraft.getProfiler().pop();
        }
    }

    private boolean isExperienceBarVisible() {
        return this.minecraft.player.jumpableVehicle() == null && this.minecraft.gameMode.hasExperience();
    }

    /**
     * Renders the name of the selected item on the screen using the provided GuiGraphics object.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     */
    private void renderSelectedItemName(GuiGraphics guiGraphics) {
        this.minecraft.getProfiler().push("selectedItemName");
        if (this.toolHighlightTimer > 0 && !this.lastToolHighlight.isEmpty()) {
            MutableComponent mutableComponent = Component.empty()
                .append(this.lastToolHighlight.getHoverName())
                .withStyle(this.lastToolHighlight.getRarity().color());
            if (this.lastToolHighlight.has(DataComponents.CUSTOM_NAME)) {
                mutableComponent.withStyle(ChatFormatting.ITALIC);
            }

            int i = this.getFont().width(mutableComponent);
            int j = (guiGraphics.guiWidth() - i) / 2;
            int k = guiGraphics.guiHeight() - 59;
            if (!this.minecraft.gameMode.canHurtPlayer()) {
                k += 14;
            }

            int l = (int)(this.toolHighlightTimer * 256.0F / 10.0F);
            if (l > 255) {
                l = 255;
            }

            if (l > 0) {
                guiGraphics.drawStringWithBackdrop(this.getFont(), mutableComponent, j, k, i, FastColor.ARGB32.color(l, -1));
            }
        }

        this.minecraft.getProfiler().pop();
    }

    private void renderDemoOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (this.minecraft.isDemo()) {
            this.minecraft.getProfiler().push("demo");
            Component component;
            if (this.minecraft.level.getGameTime() >= 120500L) {
                component = DEMO_EXPIRED_TEXT;
            } else {
                component = Component.translatable(
                    "demo.remainingTime",
                    StringUtil.formatTickDuration((int)(120500L - this.minecraft.level.getGameTime()), this.minecraft.level.tickRateManager().tickrate())
                );
            }

            int i = this.getFont().width(component);
            int j = guiGraphics.guiWidth() - i - 10;
            int k = 5;
            guiGraphics.drawStringWithBackdrop(this.getFont(), component, j, 5, i, -1);
            this.minecraft.getProfiler().pop();
        }
    }

    /**
     * Displays the scoreboard sidebar on the screen using the provided GuiGraphics object and objective.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param objective   the objective representing the scoreboard sidebar.
     */
    private void displayScoreboardSidebar(GuiGraphics guiGraphics, Objective objective) {
        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);

        record DisplayEntry(Component name, Component score, int scoreWidth) {
        }

        DisplayEntry[] lvs = scoreboard.listPlayerScores(objective)
            .stream()
            .filter(entry -> !entry.isHidden())
            .sorted(SCORE_DISPLAY_ORDER)
            .limit(15L)
            .map(scoreEntry -> {
                PlayerTeam playerTeam = scoreboard.getPlayersTeam(scoreEntry.owner());
                Component componentx = scoreEntry.ownerName();
                Component component2 = PlayerTeam.formatNameForTeam(playerTeam, componentx);
                Component component3 = scoreEntry.formatValue(numberFormat);
                int ix = this.getFont().width(component3);
                return new DisplayEntry(component2, component3, ix);
            })
            .toArray(DisplayEntry[]::new);
        Component component = objective.getDisplayName();
        int i = this.getFont().width(component);
        int j = i;
        int k = this.getFont().width(": ");

        for (DisplayEntry lv : lvs) {
            j = Math.max(j, this.getFont().width(lv.name) + (lv.scoreWidth > 0 ? k + lv.scoreWidth : 0));
        }

        int l = j;
        guiGraphics.drawManaged(() -> {
            int kx = lvs.length;
            int lx = kx * 9;
            int m = guiGraphics.guiHeight() / 2 + lx / 3;
            int n = 3;
            int o = guiGraphics.guiWidth() - l - 3;
            int p = guiGraphics.guiWidth() - 3 + 2;
            int q = this.minecraft.options.getBackgroundColor(0.3F);
            int r = this.minecraft.options.getBackgroundColor(0.4F);
            int s = m - kx * 9;
            guiGraphics.fill(o - 2, s - 9 - 1, p, s - 1, r);
            guiGraphics.fill(o - 2, s - 1, p, m, q);
            guiGraphics.drawString(this.getFont(), component, o + l / 2 - i / 2, s - 9, -1, false);

            for (int t = 0; t < kx; t++) {
                DisplayEntry lvx = lvs[t];
                int u = m - (kx - t) * 9;
                guiGraphics.drawString(this.getFont(), lvx.name, o, u, -1, false);
                guiGraphics.drawString(this.getFont(), lvx.score, p - lvx.scoreWidth, u, -1, false);
            }
        });
    }

    @Nullable
    private Player getCameraPlayer() {
        return this.minecraft.getCameraEntity() instanceof Player player ? player : null;
    }

    @Nullable
    private LivingEntity getPlayerVehicleWithHealth() {
        Player player = this.getCameraPlayer();
        if (player != null) {
            Entity entity = player.getVehicle();
            if (entity == null) {
                return null;
            }

            if (entity instanceof LivingEntity) {
                return (LivingEntity)entity;
            }
        }

        return null;
    }

    /**
     * Retrieves the maximum number of hearts representing the vehicle's health for the given mount entity.
     * <p>
     * @return the maximum number of hearts representing the vehicle's health, or 0 if the mount entity is null or does not show vehicle health.
     *
     * @param vehicle the living entity representing the vehicle.
     */
    private int getVehicleMaxHearts(@Nullable LivingEntity vehicle) {
        if (vehicle != null && vehicle.showVehicleHealth()) {
            float f = vehicle.getMaxHealth();
            int i = (int)(f + 0.5F) / 2;
            if (i > 30) {
                i = 30;
            }

            return i;
        } else {
            return 0;
        }
    }

    /**
     * Retrieves the number of rows of visible hearts needed to represent the given mount health.
     * <p>
     * @return the number of rows of visible hearts needed to represent the mount health.
     *
     * @param vehicleHealth the health of the mount entity.
     */
    private int getVisibleVehicleHeartRows(int vehicleHealth) {
        return (int)Math.ceil(vehicleHealth / 10.0);
    }

    /**
     * Renders the player's health, armor, food, and air bars on the screen.
     *
     * @param guiGraphics the graphics object used for rendering.
     */
    private void renderPlayerHealth(GuiGraphics guiGraphics) {
        Player player = this.getCameraPlayer();
        if (player != null) {
            int i = Mth.ceil(player.getHealth());
            boolean bl = this.healthBlinkTime > this.tickCount && (this.healthBlinkTime - this.tickCount) / 3L % 2L == 1L;
            long l = Util.getMillis();
            if (i < this.lastHealth && player.invulnerableTime > 0) {
                this.lastHealthTime = l;
                this.healthBlinkTime = this.tickCount + 20;
            } else if (i > this.lastHealth && player.invulnerableTime > 0) {
                this.lastHealthTime = l;
                this.healthBlinkTime = this.tickCount + 10;
            }

            if (l - this.lastHealthTime > 1000L) {
                this.lastHealth = i;
                this.displayHealth = i;
                this.lastHealthTime = l;
            }

            this.lastHealth = i;
            int j = this.displayHealth;
            this.random.setSeed(this.tickCount * 312871);
            int k = guiGraphics.guiWidth() / 2 - 91;
            int m = guiGraphics.guiWidth() / 2 + 91;
            int n = guiGraphics.guiHeight() - 39;
            float f = Math.max((float)player.getAttributeValue(Attributes.MAX_HEALTH), Math.max(j, i));
            int o = Mth.ceil(player.getAbsorptionAmount());
            int p = Mth.ceil((f + o) / 2.0F / 10.0F);
            int q = Math.max(10 - (p - 2), 3);
            int r = n - 10;
            int s = -1;
            if (player.hasEffect(MobEffects.REGENERATION)) {
                s = this.tickCount % Mth.ceil(f + 5.0F);
            }

            this.minecraft.getProfiler().push("armor");
            renderArmor(guiGraphics, player, n, p, q, k);
            this.minecraft.getProfiler().popPush("health");
            this.renderHearts(guiGraphics, player, k, n, q, s, f, i, j, o, bl);
            LivingEntity livingEntity = this.getPlayerVehicleWithHealth();
            int t = this.getVehicleMaxHearts(livingEntity);
            if (t == 0) {
                this.minecraft.getProfiler().popPush("food");
                this.renderFood(guiGraphics, player, n, m);
                r -= 10;
            }

            this.minecraft.getProfiler().popPush("air");
            int u = player.getMaxAirSupply();
            int v = Math.min(player.getAirSupply(), u);
            if (player.isEyeInFluid(FluidTags.WATER) || v < u) {
                int w = this.getVisibleVehicleHeartRows(t) - 1;
                r -= w * 10;
                int x = Mth.ceil((v - 2) * 10.0 / u);
                int y = Mth.ceil(v * 10.0 / u) - x;
                RenderSystem.enableBlend();

                for (int z = 0; z < x + y; z++) {
                    if (z < x) {
                        guiGraphics.blitSprite(AIR_SPRITE, m - z * 8 - 9, r, 9, 9);
                    } else {
                        guiGraphics.blitSprite(AIR_BURSTING_SPRITE, m - z * 8 - 9, r, 9, 9);
                    }
                }

                RenderSystem.disableBlend();
            }

            this.minecraft.getProfiler().pop();
        }
    }

    private static void renderArmor(GuiGraphics guiGraphics, Player player, int y, int heartRows, int height, int x) {
        int i = player.getArmorValue();
        if (i > 0) {
            RenderSystem.enableBlend();
            int j = y - (heartRows - 1) * height - 10;

            for (int k = 0; k < 10; k++) {
                int l = x + k * 8;
                if (k * 2 + 1 < i) {
                    guiGraphics.blitSprite(ARMOR_FULL_SPRITE, l, j, 9, 9);
                }

                if (k * 2 + 1 == i) {
                    guiGraphics.blitSprite(ARMOR_HALF_SPRITE, l, j, 9, 9);
                }

                if (k * 2 + 1 > i) {
                    guiGraphics.blitSprite(ARMOR_EMPTY_SPRITE, l, j, 9, 9);
                }
            }

            RenderSystem.disableBlend();
        }
    }

    /**
     * Renders the player's hearts, including health, absorption, and highlight hearts, on the screen.
     *
     * @param guiGraphics      the graphics object used for rendering.
     * @param player           the player entity.
     * @param x                the x-coordinate of the hearts' position.
     * @param y                the y-coordinate of the hearts' position.
     * @param height           the height of each heart.
     * @param offsetHeartIndex the index of the offset heart.
     * @param maxHealth        the maximum health of the player.
     * @param currentHealth    the current health of the player.
     * @param displayHealth    the displayed health of the player.
     * @param absorptionAmount the absorption amount of the player.
     * @param renderHighlight  determines whether to render the highlight hearts.
     */
    private void renderHearts(
        GuiGraphics guiGraphics,
        Player player,
        int x,
        int y,
        int height,
        int offsetHeartIndex,
        float maxHealth,
        int currentHealth,
        int displayHealth,
        int absorptionAmount,
        boolean renderHighlight
    ) {
        Gui.HeartType heartType = Gui.HeartType.forPlayer(player);
        boolean bl = player.level().getLevelData().isHardcore();
        int i = Mth.ceil(maxHealth / 2.0);
        int j = Mth.ceil(absorptionAmount / 2.0);
        int k = i * 2;

        for (int l = i + j - 1; l >= 0; l--) {
            int m = l / 10;
            int n = l % 10;
            int o = x + n * 8;
            int p = y - m * height;
            if (currentHealth + absorptionAmount <= 4) {
                p += this.random.nextInt(2);
            }

            if (l < i && l == offsetHeartIndex) {
                p -= 2;
            }

            this.renderHeart(guiGraphics, Gui.HeartType.CONTAINER, o, p, bl, renderHighlight, false);
            int q = l * 2;
            boolean bl2 = l >= i;
            if (bl2) {
                int r = q - k;
                if (r < absorptionAmount) {
                    boolean bl3 = r + 1 == absorptionAmount;
                    this.renderHeart(guiGraphics, heartType == Gui.HeartType.WITHERED ? heartType : Gui.HeartType.ABSORBING, o, p, bl, false, bl3);
                }
            }

            if (renderHighlight && q < displayHealth) {
                boolean bl4 = q + 1 == displayHealth;
                this.renderHeart(guiGraphics, heartType, o, p, bl, true, bl4);
            }

            if (q < currentHealth) {
                boolean bl4 = q + 1 == currentHealth;
                this.renderHeart(guiGraphics, heartType, o, p, bl, false, bl4);
            }
        }
    }

    private void renderHeart(GuiGraphics guiGraphics, Gui.HeartType heartType, int x, int y, boolean hardcore, boolean halfHeart, boolean blinking) {
        RenderSystem.enableBlend();
        guiGraphics.blitSprite(heartType.getSprite(hardcore, blinking, halfHeart), x, y, 9, 9);
        RenderSystem.disableBlend();
    }

    private void renderFood(GuiGraphics guiGraphics, Player player, int y, int x) {
        FoodData foodData = player.getFoodData();
        int i = foodData.getFoodLevel();
        RenderSystem.enableBlend();

        for (int j = 0; j < 10; j++) {
            int k = y;
            ResourceLocation resourceLocation;
            ResourceLocation resourceLocation2;
            ResourceLocation resourceLocation3;
            if (player.hasEffect(MobEffects.HUNGER)) {
                resourceLocation = FOOD_EMPTY_HUNGER_SPRITE;
                resourceLocation2 = FOOD_HALF_HUNGER_SPRITE;
                resourceLocation3 = FOOD_FULL_HUNGER_SPRITE;
            } else {
                resourceLocation = FOOD_EMPTY_SPRITE;
                resourceLocation2 = FOOD_HALF_SPRITE;
                resourceLocation3 = FOOD_FULL_SPRITE;
            }

            if (player.getFoodData().getSaturationLevel() <= 0.0F && this.tickCount % (i * 3 + 1) == 0) {
                k += this.random.nextInt(3) - 1;
            }

            int l = x - j * 8 - 9;
            guiGraphics.blitSprite(resourceLocation, l, k, 9, 9);
            if (j * 2 + 1 < i) {
                guiGraphics.blitSprite(resourceLocation3, l, k, 9, 9);
            }

            if (j * 2 + 1 == i) {
                guiGraphics.blitSprite(resourceLocation2, l, k, 9, 9);
            }
        }

        RenderSystem.disableBlend();
    }

    /**
     * Renders the health of the player's vehicle on the screen.
     *
     * @param guiGraphics the graphics object used for rendering.
     */
    private void renderVehicleHealth(GuiGraphics guiGraphics) {
        LivingEntity livingEntity = this.getPlayerVehicleWithHealth();
        if (livingEntity != null) {
            int i = this.getVehicleMaxHearts(livingEntity);
            if (i != 0) {
                int j = (int)Math.ceil(livingEntity.getHealth());
                this.minecraft.getProfiler().popPush("mountHealth");
                int k = guiGraphics.guiHeight() - 39;
                int l = guiGraphics.guiWidth() / 2 + 91;
                int m = k;
                int n = 0;
                RenderSystem.enableBlend();

                while (i > 0) {
                    int o = Math.min(i, 10);
                    i -= o;

                    for (int p = 0; p < o; p++) {
                        int q = l - p * 8 - 9;
                        guiGraphics.blitSprite(HEART_VEHICLE_CONTAINER_SPRITE, q, m, 9, 9);
                        if (p * 2 + 1 + n < j) {
                            guiGraphics.blitSprite(HEART_VEHICLE_FULL_SPRITE, q, m, 9, 9);
                        }

                        if (p * 2 + 1 + n == j) {
                            guiGraphics.blitSprite(HEART_VEHICLE_HALF_SPRITE, q, m, 9, 9);
                        }
                    }

                    m -= 10;
                    n += 20;
                }

                RenderSystem.disableBlend();
            }
        }
    }

    /**
     * Renders a texture overlay on the screen with the specified shader location and alpha value.
     *
     * @param guiGraphics    the graphics object used for rendering.
     * @param shaderLocation the location of the shader texture.
     * @param alpha          the alpha value to apply to the overlay.
     */
    private void renderTextureOverlay(GuiGraphics guiGraphics, ResourceLocation shaderLocation, float alpha) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        guiGraphics.blit(
            shaderLocation, 0, 0, -90, 0.0F, 0.0F, guiGraphics.guiWidth(), guiGraphics.guiHeight(), guiGraphics.guiWidth(), guiGraphics.guiHeight()
        );
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Renders the overlay for the spyglass effect.
     *
     * @param guiGraphics the graphics object used for rendering.
     * @param scopeScale  the scale factor for the spyglass scope.
     */
    private void renderSpyglassOverlay(GuiGraphics guiGraphics, float scopeScale) {
        float f = Math.min(guiGraphics.guiWidth(), guiGraphics.guiHeight());
        float g = f;
        float h = Math.min(guiGraphics.guiWidth() / f, guiGraphics.guiHeight() / g) * scopeScale;
        int i = Mth.floor(f * h);
        int j = Mth.floor(g * h);
        int k = (guiGraphics.guiWidth() - i) / 2;
        int l = (guiGraphics.guiHeight() - j) / 2;
        int m = k + i;
        int n = l + j;
        RenderSystem.enableBlend();
        guiGraphics.blit(SPYGLASS_SCOPE_LOCATION, k, l, -90, 0.0F, 0.0F, i, j, i, j);
        RenderSystem.disableBlend();
        guiGraphics.fill(RenderType.guiOverlay(), 0, n, guiGraphics.guiWidth(), guiGraphics.guiHeight(), -90, -16777216);
        guiGraphics.fill(RenderType.guiOverlay(), 0, 0, guiGraphics.guiWidth(), l, -90, -16777216);
        guiGraphics.fill(RenderType.guiOverlay(), 0, l, k, n, -90, -16777216);
        guiGraphics.fill(RenderType.guiOverlay(), m, l, guiGraphics.guiWidth(), n, -90, -16777216);
    }

    /**
     * Updates the brightness of the vignette effect based on the brightness of the given entity's position.
     *
     * @param entity the entity used to determine the brightness.
     */
    private void updateVignetteBrightness(Entity entity) {
        BlockPos blockPos = BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ());
        float f = LightTexture.getBrightness(entity.level().dimensionType(), entity.level().getMaxLocalRawBrightness(blockPos));
        float g = Mth.clamp(1.0F - f, 0.0F, 1.0F);
        this.vignetteBrightness = this.vignetteBrightness + (g - this.vignetteBrightness) * 0.01F;
    }

    /**
     * Renders the vignette effect on the screen based on the distance to the world border and the entity's position.
     *
     * @param guiGraphics the graphics object used for rendering.
     * @param entity      the entity used to determine the distance to the world
     *                    border.
     */
    private void renderVignette(GuiGraphics guiGraphics, @Nullable Entity entity) {
        WorldBorder worldBorder = this.minecraft.level.getWorldBorder();
        float f = 0.0F;
        if (entity != null) {
            float g = (float)worldBorder.getDistanceToBorder(entity);
            double d = Math.min(
                worldBorder.getLerpSpeed() * worldBorder.getWarningTime() * 1000.0, Math.abs(worldBorder.getLerpTarget() - worldBorder.getSize())
            );
            double e = Math.max(worldBorder.getWarningBlocks(), d);
            if (g < e) {
                f = 1.0F - (float)(g / e);
            }
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        if (f > 0.0F) {
            f = Mth.clamp(f, 0.0F, 1.0F);
            guiGraphics.setColor(0.0F, f, f, 1.0F);
        } else {
            float g = this.vignetteBrightness;
            g = Mth.clamp(g, 0.0F, 1.0F);
            guiGraphics.setColor(g, g, g, 1.0F);
        }

        guiGraphics.blit(
            VIGNETTE_LOCATION, 0, 0, -90, 0.0F, 0.0F, guiGraphics.guiWidth(), guiGraphics.guiHeight(), guiGraphics.guiWidth(), guiGraphics.guiHeight()
        );
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Renders the portal overlay effect on the screen with the specified alpha value.
     *
     * @param guiGraphics the graphics object used for rendering.
     * @param alpha       the alpha value of the overlay.
     */
    private void renderPortalOverlay(GuiGraphics guiGraphics, float alpha) {
        if (alpha < 1.0F) {
            alpha *= alpha;
            alpha *= alpha;
            alpha = alpha * 0.8F + 0.2F;
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        TextureAtlasSprite textureAtlasSprite = this.minecraft
            .getBlockRenderer()
            .getBlockModelShaper()
            .getParticleIcon(Blocks.NETHER_PORTAL.defaultBlockState());
        guiGraphics.blit(0, 0, -90, guiGraphics.guiWidth(), guiGraphics.guiHeight(), textureAtlasSprite);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderSlot(GuiGraphics guiGraphics, int x, int y, DeltaTracker deltaTracker, Player player, ItemStack stack, int seed) {
        if (!stack.isEmpty()) {
            float f = stack.getPopTime() - deltaTracker.getGameTimeDeltaPartialTick(false);
            if (f > 0.0F) {
                float g = 1.0F + f / 5.0F;
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(x + 8, y + 12, 0.0F);
                guiGraphics.pose().scale(1.0F / g, (g + 1.0F) / 2.0F, 1.0F);
                guiGraphics.pose().translate(-(x + 8), -(y + 12), 0.0F);
            }

            guiGraphics.renderItem(player, stack, x, y, seed);
            if (f > 0.0F) {
                guiGraphics.pose().popPose();
            }

            guiGraphics.renderItemDecorations(this.minecraft.font, stack, x, y);
        }
    }

    /**
     * Advances the tick for the autosave indicator and optionally ticks the object if not paused.
     */
    public void tick(boolean pause) {
        this.tickAutosaveIndicator();
        if (!pause) {
            this.tick();
        }
    }

    private void tick() {
        if (this.overlayMessageTime > 0) {
            this.overlayMessageTime--;
        }

        if (this.titleTime > 0) {
            this.titleTime--;
            if (this.titleTime <= 0) {
                this.title = null;
                this.subtitle = null;
            }
        }

        this.tickCount++;
        Entity entity = this.minecraft.getCameraEntity();
        if (entity != null) {
            this.updateVignetteBrightness(entity);
        }

        if (this.minecraft.player != null) {
            ItemStack itemStack = this.minecraft.player.getInventory().getSelected();
            if (itemStack.isEmpty()) {
                this.toolHighlightTimer = 0;
            } else if (this.lastToolHighlight.isEmpty()
                || !itemStack.is(this.lastToolHighlight.getItem())
                || !itemStack.getHoverName().equals(this.lastToolHighlight.getHoverName())) {
                this.toolHighlightTimer = (int)(40.0 * this.minecraft.options.notificationDisplayTime().get());
            } else if (this.toolHighlightTimer > 0) {
                this.toolHighlightTimer--;
            }

            this.lastToolHighlight = itemStack;
        }

        this.chat.tick();
    }

    private void tickAutosaveIndicator() {
        MinecraftServer minecraftServer = this.minecraft.getSingleplayerServer();
        boolean bl = minecraftServer != null && minecraftServer.isCurrentlySaving();
        this.lastAutosaveIndicatorValue = this.autosaveIndicatorValue;
        this.autosaveIndicatorValue = Mth.lerp(0.2F, this.autosaveIndicatorValue, bl ? 1.0F : 0.0F);
    }

    /**
     * Sets the currently playing record display name and updates the overlay message.
     *
     * @param displayName the display name of the currently playing record.
     */
    public void setNowPlaying(Component displayName) {
        Component component = Component.translatable("record.nowPlaying", displayName);
        this.setOverlayMessage(component, true);
        this.minecraft.getNarrator().sayNow(component);
    }

    /**
     * Sets the overlay message to be displayed on the screen.
     *
     * @param component    the {@link Component} representing the overlay message.
     * @param animateColor a boolean indicating whether to animate the color of the
     *                     overlay message.
     */
    public void setOverlayMessage(Component component, boolean animateColor) {
        this.setChatDisabledByPlayerShown(false);
        this.overlayMessageString = component;
        this.overlayMessageTime = 60;
        this.animateOverlayMessageColor = animateColor;
    }

    /**
     * {@return {@code true} if the chat is disabled, {@code false} if chat is enabled}
     */
    public void setChatDisabledByPlayerShown(boolean chatDisabledByPlayerShown) {
        this.chatDisabledByPlayerShown = chatDisabledByPlayerShown;
    }

    public boolean isShowingChatDisabledByPlayer() {
        return this.chatDisabledByPlayerShown && this.overlayMessageTime > 0;
    }

    /**
     * Sets the fade-in, stay, and fade-out times for the title display.
     *
     * @param titleFadeInTime  the fade-in time for the title message in ticks.
     * @param titleStayTime    the stay time for the title message in ticks.
     * @param titleFadeOutTime the fade-out time for the title message in ticks.
     */
    public void setTimes(int titleFadeInTime, int titleStayTime, int titleFadeOutTime) {
        if (titleFadeInTime >= 0) {
            this.titleFadeInTime = titleFadeInTime;
        }

        if (titleStayTime >= 0) {
            this.titleStayTime = titleStayTime;
        }

        if (titleFadeOutTime >= 0) {
            this.titleFadeOutTime = titleFadeOutTime;
        }

        if (this.titleTime > 0) {
            this.titleTime = this.titleFadeInTime + this.titleStayTime + this.titleFadeOutTime;
        }
    }

    /**
     * Sets the subtitle to be displayed in the title screen.
     *
     * @param subtitle the subtitle {@link Component} to be displayed.
     */
    public void setSubtitle(Component subtitle) {
        this.subtitle = subtitle;
    }

    /**
     * Sets the title to be displayed in the title screen.
     *
     * @param title the title {@link Component} to be displayed.
     */
    public void setTitle(Component title) {
        this.title = title;
        this.titleTime = this.titleFadeInTime + this.titleStayTime + this.titleFadeOutTime;
    }

    public void clear() {
        this.title = null;
        this.subtitle = null;
        this.titleTime = 0;
    }

    public ChatComponent getChat() {
        return this.chat;
    }

    public int getGuiTicks() {
        return this.tickCount;
    }

    public Font getFont() {
        return this.minecraft.font;
    }

    public SpectatorGui getSpectatorGui() {
        return this.spectatorGui;
    }

    public PlayerTabOverlay getTabList() {
        return this.tabList;
    }

    public void onDisconnected() {
        this.tabList.reset();
        this.bossOverlay.reset();
        this.minecraft.getToasts().clear();
        this.debugOverlay.reset();
        this.chat.clearMessages(true);
    }

    public BossHealthOverlay getBossOverlay() {
        return this.bossOverlay;
    }

    public DebugScreenOverlay getDebugOverlay() {
        return this.debugOverlay;
    }

    public void clearCache() {
        this.debugOverlay.clearChunkCache();
    }

    public void renderSavingIndicator(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (this.minecraft.options.showAutosaveIndicator().get() && (this.autosaveIndicatorValue > 0.0F || this.lastAutosaveIndicatorValue > 0.0F)) {
            int i = Mth.floor(
                255.0F * Mth.clamp(Mth.lerp(deltaTracker.getRealtimeDeltaTicks(), this.lastAutosaveIndicatorValue, this.autosaveIndicatorValue), 0.0F, 1.0F)
            );
            if (i > 8) {
                Font font = this.getFont();
                int j = font.width(SAVING_TEXT);
                int k = FastColor.ARGB32.color(i, -1);
                int l = guiGraphics.guiWidth() - j - 2;
                int m = guiGraphics.guiHeight() - 35;
                guiGraphics.drawStringWithBackdrop(font, SAVING_TEXT, l, m, j, k);
            }
        }
    }

    enum HeartType {
        CONTAINER(
            ResourceLocation.withDefaultNamespace("hud/heart/container"),
            ResourceLocation.withDefaultNamespace("hud/heart/container_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/container"),
            ResourceLocation.withDefaultNamespace("hud/heart/container_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/container_hardcore"),
            ResourceLocation.withDefaultNamespace("hud/heart/container_hardcore_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/container_hardcore"),
            ResourceLocation.withDefaultNamespace("hud/heart/container_hardcore_blinking")
        ),
        NORMAL(
            ResourceLocation.withDefaultNamespace("hud/heart/full"),
            ResourceLocation.withDefaultNamespace("hud/heart/full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/half"),
            ResourceLocation.withDefaultNamespace("hud/heart/half_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/hardcore_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/hardcore_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/hardcore_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/hardcore_half_blinking")
        ),
        POISIONED(
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_half_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_hardcore_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_hardcore_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_hardcore_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/poisoned_hardcore_half_blinking")
        ),
        WITHERED(
            ResourceLocation.withDefaultNamespace("hud/heart/withered_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_half_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_hardcore_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_hardcore_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_hardcore_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/withered_hardcore_half_blinking")
        ),
        ABSORBING(
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/absorbing_hardcore_half_blinking")
        ),
        FROZEN(
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_half_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_hardcore_full"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_hardcore_full_blinking"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_hardcore_half"),
            ResourceLocation.withDefaultNamespace("hud/heart/frozen_hardcore_half_blinking")
        );

        private final ResourceLocation full;
        private final ResourceLocation fullBlinking;
        private final ResourceLocation half;
        private final ResourceLocation halfBlinking;
        private final ResourceLocation hardcoreFull;
        private final ResourceLocation hardcoreFullBlinking;
        private final ResourceLocation hardcoreHalf;
        private final ResourceLocation hardcoreHalfBlinking;

        HeartType(
            final ResourceLocation full,
            final ResourceLocation fullBlinking,
            final ResourceLocation half,
            final ResourceLocation halfBlinking,
            final ResourceLocation hardcoreFull,
            final ResourceLocation hardcoreBlinking,
            final ResourceLocation hardcoreHalf,
            final ResourceLocation hardcoreHalfBlinking
        ) {
            this.full = full;
            this.fullBlinking = fullBlinking;
            this.half = half;
            this.halfBlinking = halfBlinking;
            this.hardcoreFull = hardcoreFull;
            this.hardcoreFullBlinking = hardcoreBlinking;
            this.hardcoreHalf = hardcoreHalf;
            this.hardcoreHalfBlinking = hardcoreHalfBlinking;
        }

        public ResourceLocation getSprite(boolean hardcore, boolean halfHeart, boolean blinking) {
            if (!hardcore) {
                if (halfHeart) {
                    return blinking ? this.halfBlinking : this.half;
                } else {
                    return blinking ? this.fullBlinking : this.full;
                }
            } else if (halfHeart) {
                return blinking ? this.hardcoreHalfBlinking : this.hardcoreHalf;
            } else {
                return blinking ? this.hardcoreFullBlinking : this.hardcoreFull;
            }
        }

        /**
         * Returns the {@link HeartType} based on the player's status effects.
         * <p>
         * @return the {@link HeartType} based on the player's status effects.
         *
         * @param player the player for which to determine the HeartType.
         */
        static Gui.HeartType forPlayer(Player player) {
            Gui.HeartType heartType;
            if (player.hasEffect(MobEffects.POISON)) {
                heartType = POISIONED;
            } else if (player.hasEffect(MobEffects.WITHER)) {
                heartType = WITHERED;
            } else if (player.isFullyFrozen()) {
                heartType = FROZEN;
            } else {
                heartType = NORMAL;
            }

            return heartType;
        }
    }
}
