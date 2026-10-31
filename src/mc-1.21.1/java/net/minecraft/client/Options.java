package net.minecraft.client;

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.GpuWarnlistManager;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;

public class Options {
    static final Logger LOGGER = LogUtils.getLogger();
    static final Gson GSON = new Gson();
    private static final TypeToken<List<String>> LIST_OF_STRINGS_TYPE = new TypeToken<List<String>>() {};
    public static final int RENDER_DISTANCE_TINY = 2;
    public static final int RENDER_DISTANCE_SHORT = 4;
    public static final int RENDER_DISTANCE_NORMAL = 8;
    public static final int RENDER_DISTANCE_FAR = 12;
    public static final int RENDER_DISTANCE_REALLY_FAR = 16;
    public static final int RENDER_DISTANCE_EXTREME = 32;
    private static final Splitter OPTION_SPLITTER = Splitter.on(':').limit(2);
    public static final String DEFAULT_SOUND_DEVICE = "";
    private static final Component ACCESSIBILITY_TOOLTIP_DARK_MOJANG_BACKGROUND = Component.translatable("options.darkMojangStudiosBackgroundColor.tooltip");
    private final OptionInstance<Boolean> darkMojangStudiosBackground = OptionInstance.createBoolean(
        "options.darkMojangStudiosBackgroundColor", OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_DARK_MOJANG_BACKGROUND), false
    );
    private static final Component ACCESSIBILITY_TOOLTIP_HIDE_LIGHTNING_FLASHES = Component.translatable("options.hideLightningFlashes.tooltip");
    private final OptionInstance<Boolean> hideLightningFlash = OptionInstance.createBoolean(
        "options.hideLightningFlashes", OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_HIDE_LIGHTNING_FLASHES), false
    );
    private static final Component ACCESSIBILITY_TOOLTIP_HIDE_SPLASH_TEXTS = Component.translatable("options.hideSplashTexts.tooltip");
    private final OptionInstance<Boolean> hideSplashTexts = OptionInstance.createBoolean(
        "options.hideSplashTexts", OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_HIDE_SPLASH_TEXTS), false
    );
    private final OptionInstance<Double> sensitivity = new OptionInstance<>(
        "options.sensitivity",
        OptionInstance.noTooltip(),
        (component, sensitivity) -> {
            if (sensitivity == 0.0) {
                return genericValueLabel(component, Component.translatable("options.sensitivity.min"));
            } else {
                return sensitivity == 1.0
                    ? genericValueLabel(component, Component.translatable("options.sensitivity.max"))
                    : percentValueLabel(component, 2.0 * sensitivity);
            }
        },
        OptionInstance.UnitDouble.INSTANCE,
        0.5,
        sensitivity -> {}
    );
    private final OptionInstance<Integer> renderDistance;
    private final OptionInstance<Integer> simulationDistance;
    private int serverRenderDistance = 0;
    private final OptionInstance<Double> entityDistanceScaling = new OptionInstance<>(
        "options.entityDistanceScaling",
        OptionInstance.noTooltip(),
        Options::percentValueLabel,
        new OptionInstance.IntRange(2, 20).xmap(i -> i / 4.0, double_ -> (int)(double_ * 4.0)),
        Codec.doubleRange(0.5, 5.0),
        1.0,
        double_ -> {}
    );
    public static final int UNLIMITED_FRAMERATE_CUTOFF = 260;
    private final OptionInstance<Integer> framerateLimit = new OptionInstance<>(
        "options.framerateLimit",
        OptionInstance.noTooltip(),
        (component, framerateLimit) -> framerateLimit == 260
            ? genericValueLabel(component, Component.translatable("options.framerateLimit.max"))
            : genericValueLabel(component, Component.translatable("options.framerate", framerateLimit)),
        new OptionInstance.IntRange(1, 26).xmap(framerateLimit -> framerateLimit * 10, framerateLimit -> framerateLimit / 10),
        Codec.intRange(10, 260),
        120,
        framerateLimit -> Minecraft.getInstance().getWindow().setFramerateLimit(framerateLimit)
    );
    private final OptionInstance<CloudStatus> cloudStatus = new OptionInstance<>(
        "options.renderClouds",
        OptionInstance.noTooltip(),
        OptionInstance.forOptionEnum(),
        new OptionInstance.Enum<>(
            Arrays.asList(CloudStatus.values()),
            Codec.withAlternative(CloudStatus.CODEC, Codec.BOOL, boolean_ -> boolean_ ? CloudStatus.FANCY : CloudStatus.OFF)
        ),
        CloudStatus.FANCY,
        cloudStatus -> {
            if (Minecraft.useShaderTransparency()) {
                RenderTarget renderTarget = Minecraft.getInstance().levelRenderer.getCloudsTarget();
                if (renderTarget != null) {
                    renderTarget.clear(Minecraft.ON_OSX);
                }
            }
        }
    );
    private static final Component GRAPHICS_TOOLTIP_FAST = Component.translatable("options.graphics.fast.tooltip");
    private static final Component GRAPHICS_TOOLTIP_FABULOUS = Component.translatable(
        "options.graphics.fabulous.tooltip", Component.translatable("options.graphics.fabulous").withStyle(ChatFormatting.ITALIC)
    );
    private static final Component GRAPHICS_TOOLTIP_FANCY = Component.translatable("options.graphics.fancy.tooltip");
    private final OptionInstance<GraphicsStatus> graphicsMode = new OptionInstance<>(
        "options.graphics",
        status -> {
            return switch (status) {
                case FANCY -> Tooltip.create(GRAPHICS_TOOLTIP_FANCY);
                case FAST -> Tooltip.create(GRAPHICS_TOOLTIP_FAST);
                case FABULOUS -> Tooltip.create(GRAPHICS_TOOLTIP_FABULOUS);
            };
        },
        (component, status) -> {
            MutableComponent mutableComponent = Component.translatable(status.getKey());
            return status == GraphicsStatus.FABULOUS ? mutableComponent.withStyle(ChatFormatting.ITALIC) : mutableComponent;
        },
        new OptionInstance.AltEnum<>(
            Arrays.asList(GraphicsStatus.values()),
            Stream.of(GraphicsStatus.values()).filter(status -> status != GraphicsStatus.FABULOUS).collect(Collectors.toList()),
            () -> Minecraft.getInstance().isRunning() && Minecraft.getInstance().getGpuWarnlistManager().isSkippingFabulous(),
            (statusOption, status) -> {
                Minecraft minecraftx = Minecraft.getInstance();
                GpuWarnlistManager gpuWarnlistManager = minecraftx.getGpuWarnlistManager();
                if (status == GraphicsStatus.FABULOUS && gpuWarnlistManager.willShowWarning()) {
                    gpuWarnlistManager.showWarning();
                } else {
                    statusOption.set(status);
                    minecraftx.levelRenderer.allChanged();
                }
            },
            Codec.INT.xmap(GraphicsStatus::byId, GraphicsStatus::getId)
        ),
        GraphicsStatus.FANCY,
        status -> {}
    );
    private final OptionInstance<Boolean> ambientOcclusion = OptionInstance.createBoolean(
        "options.ao", true, ambientOcclusion -> Minecraft.getInstance().levelRenderer.allChanged()
    );
    private static final Component PRIORITIZE_CHUNK_TOOLTIP_NONE = Component.translatable("options.prioritizeChunkUpdates.none.tooltip");
    private static final Component PRIORITIZE_CHUNK_TOOLTIP_PLAYER_AFFECTED = Component.translatable("options.prioritizeChunkUpdates.byPlayer.tooltip");
    private static final Component PRIORITIZE_CHUNK_TOOLTIP_NEARBY = Component.translatable("options.prioritizeChunkUpdates.nearby.tooltip");
    private final OptionInstance<PrioritizeChunkUpdates> prioritizeChunkUpdates = new OptionInstance<>(
        "options.prioritizeChunkUpdates",
        chunkUpdates -> {
            return switch (chunkUpdates) {
                case NONE -> Tooltip.create(PRIORITIZE_CHUNK_TOOLTIP_NONE);
                case PLAYER_AFFECTED -> Tooltip.create(PRIORITIZE_CHUNK_TOOLTIP_PLAYER_AFFECTED);
                case NEARBY -> Tooltip.create(PRIORITIZE_CHUNK_TOOLTIP_NEARBY);
            };
        },
        OptionInstance.forOptionEnum(),
        new OptionInstance.Enum<>(Arrays.asList(PrioritizeChunkUpdates.values()), Codec.INT.xmap(PrioritizeChunkUpdates::byId, PrioritizeChunkUpdates::getId)),
        PrioritizeChunkUpdates.NONE,
        value -> {}
    );
    public List<String> resourcePacks = Lists.newArrayList();
    public List<String> incompatibleResourcePacks = Lists.newArrayList();
    private final OptionInstance<ChatVisiblity> chatVisibility = new OptionInstance<>(
        "options.chat.visibility",
        OptionInstance.noTooltip(),
        OptionInstance.forOptionEnum(),
        new OptionInstance.Enum<>(Arrays.asList(ChatVisiblity.values()), Codec.INT.xmap(ChatVisiblity::byId, ChatVisiblity::getId)),
        ChatVisiblity.FULL,
        value -> {}
    );
    private final OptionInstance<Double> chatOpacity = new OptionInstance<>(
        "options.chat.opacity",
        OptionInstance.noTooltip(),
        (component, value) -> percentValueLabel(component, value * 0.9 + 0.1),
        OptionInstance.UnitDouble.INSTANCE,
        1.0,
        value -> Minecraft.getInstance().gui.getChat().rescaleChat()
    );
    private final OptionInstance<Double> chatLineSpacing = new OptionInstance<>(
        "options.chat.line_spacing", OptionInstance.noTooltip(), Options::percentValueLabel, OptionInstance.UnitDouble.INSTANCE, 0.0, value -> {}
    );
    private static final Component MENU_BACKGROUND_BLURRINESS_TOOLTIP = Component.translatable("options.accessibility.menu_background_blurriness.tooltip");
    private static final int BLURRINESS_DEFAULT_VALUE = 5;
    private final OptionInstance<Integer> menuBackgroundBlurriness = new OptionInstance<>(
        "options.accessibility.menu_background_blurriness",
        OptionInstance.cachedConstantTooltip(MENU_BACKGROUND_BLURRINESS_TOOLTIP),
        Options::genericValueOrOffLabel,
        new OptionInstance.IntRange(0, 10),
        5,
        integer -> {}
    );
    private final OptionInstance<Double> textBackgroundOpacity = new OptionInstance<>(
        "options.accessibility.text_background_opacity",
        OptionInstance.noTooltip(),
        Options::percentValueLabel,
        OptionInstance.UnitDouble.INSTANCE,
        0.5,
        value -> Minecraft.getInstance().gui.getChat().rescaleChat()
    );
    private final OptionInstance<Double> panoramaSpeed = new OptionInstance<>(
        "options.accessibility.panorama_speed", OptionInstance.noTooltip(), Options::percentValueLabel, OptionInstance.UnitDouble.INSTANCE, 1.0, double_ -> {}
    );
    private static final Component ACCESSIBILITY_TOOLTIP_CONTRAST_MODE = Component.translatable("options.accessibility.high_contrast.tooltip");
    private final OptionInstance<Boolean> highContrast = OptionInstance.createBoolean(
        "options.accessibility.high_contrast", OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_CONTRAST_MODE), false, boolean_ -> {
            PackRepository packRepository = Minecraft.getInstance().getResourcePackRepository();
            boolean blx = packRepository.getSelectedIds().contains("high_contrast");
            if (!blx && boolean_) {
                if (packRepository.addPack("high_contrast")) {
                    this.updateResourcePacks(packRepository);
                }
            } else if (blx && !boolean_ && packRepository.removePack("high_contrast")) {
                this.updateResourcePacks(packRepository);
            }
        }
    );
    private final OptionInstance<Boolean> narratorHotkey = OptionInstance.createBoolean(
        "options.accessibility.narrator_hotkey",
        OptionInstance.cachedConstantTooltip(
            Minecraft.ON_OSX
                ? Component.translatable("options.accessibility.narrator_hotkey.mac.tooltip")
                : Component.translatable("options.accessibility.narrator_hotkey.tooltip")
        ),
        true
    );
    @Nullable
    public String fullscreenVideoModeString;
    public boolean hideServerAddress;
    public boolean advancedItemTooltips;
    public boolean pauseOnLostFocus = true;
    private final Set<PlayerModelPart> modelParts = EnumSet.allOf(PlayerModelPart.class);
    private final OptionInstance<HumanoidArm> mainHand = new OptionInstance<>(
        "options.mainHand",
        OptionInstance.noTooltip(),
        OptionInstance.forOptionEnum(),
        new OptionInstance.Enum<>(Arrays.asList(HumanoidArm.values()), HumanoidArm.CODEC),
        HumanoidArm.RIGHT,
        humanoidArm -> this.broadcastOptions()
    );
    public int overrideWidth;
    public int overrideHeight;
    private final OptionInstance<Double> chatScale = new OptionInstance<>(
        "options.chat.scale",
        OptionInstance.noTooltip(),
        (component, double_) -> double_ == 0.0 ? CommonComponents.optionStatus(component, false) : percentValueLabel(component, double_),
        OptionInstance.UnitDouble.INSTANCE,
        1.0,
        double_ -> Minecraft.getInstance().gui.getChat().rescaleChat()
    );
    private final OptionInstance<Double> chatWidth = new OptionInstance<>(
        "options.chat.width",
        OptionInstance.noTooltip(),
        (component, double_) -> pixelValueLabel(component, ChatComponent.getWidth(double_)),
        OptionInstance.UnitDouble.INSTANCE,
        1.0,
        double_ -> Minecraft.getInstance().gui.getChat().rescaleChat()
    );
    private final OptionInstance<Double> chatHeightUnfocused = new OptionInstance<>(
        "options.chat.height.unfocused",
        OptionInstance.noTooltip(),
        (component, double_) -> pixelValueLabel(component, ChatComponent.getHeight(double_)),
        OptionInstance.UnitDouble.INSTANCE,
        ChatComponent.defaultUnfocusedPct(),
        double_ -> Minecraft.getInstance().gui.getChat().rescaleChat()
    );
    private final OptionInstance<Double> chatHeightFocused = new OptionInstance<>(
        "options.chat.height.focused",
        OptionInstance.noTooltip(),
        (component, double_) -> pixelValueLabel(component, ChatComponent.getHeight(double_)),
        OptionInstance.UnitDouble.INSTANCE,
        1.0,
        double_ -> Minecraft.getInstance().gui.getChat().rescaleChat()
    );
    private final OptionInstance<Double> chatDelay = new OptionInstance<>(
        "options.chat.delay_instant",
        OptionInstance.noTooltip(),
        (component, double_) -> double_ <= 0.0
            ? Component.translatable("options.chat.delay_none")
            : Component.translatable("options.chat.delay", String.format(Locale.ROOT, "%.1f", double_)),
        new OptionInstance.IntRange(0, 60).xmap(i -> i / 10.0, double_ -> (int)(double_ * 10.0)),
        Codec.doubleRange(0.0, 6.0),
        0.0,
        double_ -> Minecraft.getInstance().getChatListener().setMessageDelay(double_)
    );
    private static final Component ACCESSIBILITY_TOOLTIP_NOTIFICATION_DISPLAY_TIME = Component.translatable("options.notifications.display_time.tooltip");
    private final OptionInstance<Double> notificationDisplayTime = new OptionInstance<>(
        "options.notifications.display_time",
        OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_NOTIFICATION_DISPLAY_TIME),
        (component, double_) -> genericValueLabel(component, Component.translatable("options.multiplier", double_)),
        new OptionInstance.IntRange(5, 100).xmap(i -> i / 10.0, double_ -> (int)(double_ * 10.0)),
        Codec.doubleRange(0.5, 10.0),
        1.0,
        double_ -> {}
    );
    private final OptionInstance<Integer> mipmapLevels = new OptionInstance<>(
        "options.mipmapLevels",
        OptionInstance.noTooltip(),
        (component, integer) -> integer == 0 ? CommonComponents.optionStatus(component, false) : genericValueLabel(component, integer),
        new OptionInstance.IntRange(0, 4),
        4,
        integer -> {}
    );
    public boolean useNativeTransport = true;
    private final OptionInstance<AttackIndicatorStatus> attackIndicator = new OptionInstance<>(
        "options.attackIndicator",
        OptionInstance.noTooltip(),
        OptionInstance.forOptionEnum(),
        new OptionInstance.Enum<>(Arrays.asList(AttackIndicatorStatus.values()), Codec.INT.xmap(AttackIndicatorStatus::byId, AttackIndicatorStatus::getId)),
        AttackIndicatorStatus.CROSSHAIR,
        attackIndicatorStatus -> {}
    );
    public TutorialSteps tutorialStep = TutorialSteps.MOVEMENT;
    public boolean joinedFirstServer = false;
    public boolean hideBundleTutorial = false;
    private final OptionInstance<Integer> biomeBlendRadius = new OptionInstance<>(
        "options.biomeBlendRadius", OptionInstance.noTooltip(), (component, integer) -> {
            int i = integer * 2 + 1;
            return genericValueLabel(component, Component.translatable("options.biomeBlendRadius." + i));
        }, new OptionInstance.IntRange(0, 7, false), 2, integer -> Minecraft.getInstance().levelRenderer.allChanged()
    );
    private final OptionInstance<Double> mouseWheelSensitivity = new OptionInstance<>(
        "options.mouseWheelSensitivity",
        OptionInstance.noTooltip(),
        (component, double_) -> genericValueLabel(component, Component.literal(String.format(Locale.ROOT, "%.2f", double_))),
        new OptionInstance.IntRange(-200, 100).xmap(Options::logMouse, Options::unlogMouse),
        Codec.doubleRange(logMouse(-200), logMouse(100)),
        logMouse(0),
        double_ -> {}
    );
    private final OptionInstance<Boolean> rawMouseInput = OptionInstance.createBoolean("options.rawMouseInput", true, vsync -> {
        Window window = Minecraft.getInstance().getWindow();
        if (window != null) {
            window.updateRawMouseInput(vsync);
        }
    });
    public int glDebugVerbosity = 1;
    private final OptionInstance<Boolean> autoJump = OptionInstance.createBoolean("options.autoJump", false);
    private final OptionInstance<Boolean> operatorItemsTab = OptionInstance.createBoolean("options.operatorItemsTab", false);
    private final OptionInstance<Boolean> autoSuggestions = OptionInstance.createBoolean("options.autoSuggestCommands", true);
    private final OptionInstance<Boolean> chatColors = OptionInstance.createBoolean("options.chat.color", true);
    private final OptionInstance<Boolean> chatLinks = OptionInstance.createBoolean("options.chat.links", true);
    private final OptionInstance<Boolean> chatLinksPrompt = OptionInstance.createBoolean("options.chat.links.prompt", true);
    private final OptionInstance<Boolean> enableVsync = OptionInstance.createBoolean("options.vsync", true, forceUnicode -> {
        if (Minecraft.getInstance().getWindow() != null) {
            Minecraft.getInstance().getWindow().updateVsync(forceUnicode);
        }
    });
    private final OptionInstance<Boolean> entityShadows = OptionInstance.createBoolean("options.entityShadows", true);
    private final OptionInstance<Boolean> forceUnicodeFont = OptionInstance.createBoolean("options.forceUnicodeFont", false, boolean_ -> updateFontOptions());
    private final OptionInstance<Boolean> japaneseGlyphVariants = OptionInstance.createBoolean(
        "options.japaneseGlyphVariants",
        OptionInstance.cachedConstantTooltip(Component.translatable("options.japaneseGlyphVariants.tooltip")),
        japaneseGlyphVariantsDefault(),
        boolean_ -> updateFontOptions()
    );
    private final OptionInstance<Boolean> invertYMouse = OptionInstance.createBoolean("options.invertMouse", false);
    private final OptionInstance<Boolean> discreteMouseScroll = OptionInstance.createBoolean("options.discrete_mouse_scroll", false);
    private static final Component REALMS_NOTIFICATIONS_TOOLTIP = Component.translatable("options.realmsNotifications.tooltip");
    private final OptionInstance<Boolean> realmsNotifications = OptionInstance.createBoolean(
        "options.realmsNotifications", OptionInstance.cachedConstantTooltip(REALMS_NOTIFICATIONS_TOOLTIP), true
    );
    private static final Component ALLOW_SERVER_LISTING_TOOLTIP = Component.translatable("options.allowServerListing.tooltip");
    private final OptionInstance<Boolean> allowServerListing = OptionInstance.createBoolean(
        "options.allowServerListing", OptionInstance.cachedConstantTooltip(ALLOW_SERVER_LISTING_TOOLTIP), true, boolean_ -> this.broadcastOptions()
    );
    private final OptionInstance<Boolean> reducedDebugInfo = OptionInstance.createBoolean("options.reducedDebugInfo", false);
    private final Map<SoundSource, OptionInstance<Double>> soundSourceVolumes = Util.make(new EnumMap<>(SoundSource.class), enumMap -> {
        for (SoundSource soundSource : SoundSource.values()) {
            enumMap.put(soundSource, this.createSoundSliderOptionInstance("soundCategory." + soundSource.getName(), soundSource));
        }
    });
    private final OptionInstance<Boolean> showSubtitles = OptionInstance.createBoolean("options.showSubtitles", false);
    private static final Component DIRECTIONAL_AUDIO_TOOLTIP_ON = Component.translatable("options.directionalAudio.on.tooltip");
    private static final Component DIRECTIONAL_AUDIO_TOOLTIP_OFF = Component.translatable("options.directionalAudio.off.tooltip");
    private final OptionInstance<Boolean> directionalAudio = OptionInstance.createBoolean(
        "options.directionalAudio",
        boolean_ -> boolean_ ? Tooltip.create(DIRECTIONAL_AUDIO_TOOLTIP_ON) : Tooltip.create(DIRECTIONAL_AUDIO_TOOLTIP_OFF),
        false,
        boolean_ -> {
            SoundManager soundManager = Minecraft.getInstance().getSoundManager();
            soundManager.reload();
            soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    );
    private final OptionInstance<Boolean> backgroundForChatOnly = new OptionInstance<>(
        "options.accessibility.text_background",
        OptionInstance.noTooltip(),
        (component, boolean_) -> boolean_
            ? Component.translatable("options.accessibility.text_background.chat")
            : Component.translatable("options.accessibility.text_background.everywhere"),
        OptionInstance.BOOLEAN_VALUES,
        true,
        boolean_ -> {}
    );
    private final OptionInstance<Boolean> touchscreen = OptionInstance.createBoolean("options.touchscreen", false);
    private final OptionInstance<Boolean> fullscreen = OptionInstance.createBoolean("options.fullscreen", false, boolean_ -> {
        Minecraft minecraftx = Minecraft.getInstance();
        if (minecraftx.getWindow() != null && minecraftx.getWindow().isFullscreen() != boolean_) {
            minecraftx.getWindow().toggleFullScreen();
            this.fullscreen().set(minecraftx.getWindow().isFullscreen());
        }
    });
    private final OptionInstance<Boolean> bobView = OptionInstance.createBoolean("options.viewBobbing", true);
    private static final Component MOVEMENT_TOGGLE = Component.translatable("options.key.toggle");
    private static final Component MOVEMENT_HOLD = Component.translatable("options.key.hold");
    private final OptionInstance<Boolean> toggleCrouch = new OptionInstance<>(
        "key.sneak",
        OptionInstance.noTooltip(),
        (component, boolean_) -> boolean_ ? MOVEMENT_TOGGLE : MOVEMENT_HOLD,
        OptionInstance.BOOLEAN_VALUES,
        false,
        boolean_ -> {}
    );
    private final OptionInstance<Boolean> toggleSprint = new OptionInstance<>(
        "key.sprint",
        OptionInstance.noTooltip(),
        (component, boolean_) -> boolean_ ? MOVEMENT_TOGGLE : MOVEMENT_HOLD,
        OptionInstance.BOOLEAN_VALUES,
        false,
        boolean_ -> {}
    );
    public boolean skipMultiplayerWarning;
    private static final Component CHAT_TOOLTIP_HIDE_MATCHED_NAMES = Component.translatable("options.hideMatchedNames.tooltip");
    private final OptionInstance<Boolean> hideMatchedNames = OptionInstance.createBoolean(
        "options.hideMatchedNames", OptionInstance.cachedConstantTooltip(CHAT_TOOLTIP_HIDE_MATCHED_NAMES), true
    );
    private final OptionInstance<Boolean> showAutosaveIndicator = OptionInstance.createBoolean("options.autosaveIndicator", true);
    private static final Component CHAT_TOOLTIP_ONLY_SHOW_SECURE = Component.translatable("options.onlyShowSecureChat.tooltip");
    private final OptionInstance<Boolean> onlyShowSecureChat = OptionInstance.createBoolean(
        "options.onlyShowSecureChat", OptionInstance.cachedConstantTooltip(CHAT_TOOLTIP_ONLY_SHOW_SECURE), false
    );
    public final KeyMapping keyUp = new KeyMapping("key.forward", 87, "key.categories.movement");
    public final KeyMapping keyLeft = new KeyMapping("key.left", 65, "key.categories.movement");
    public final KeyMapping keyDown = new KeyMapping("key.back", 83, "key.categories.movement");
    public final KeyMapping keyRight = new KeyMapping("key.right", 68, "key.categories.movement");
    public final KeyMapping keyJump = new KeyMapping("key.jump", 32, "key.categories.movement");
    public final KeyMapping keyShift = new ToggleKeyMapping("key.sneak", 340, "key.categories.movement", this.toggleCrouch::get);
    public final KeyMapping keySprint = new ToggleKeyMapping("key.sprint", 341, "key.categories.movement", this.toggleSprint::get);
    public final KeyMapping keyInventory = new KeyMapping("key.inventory", 69, "key.categories.inventory");
    public final KeyMapping keySwapOffhand = new KeyMapping("key.swapOffhand", 70, "key.categories.inventory");
    public final KeyMapping keyDrop = new KeyMapping("key.drop", 81, "key.categories.inventory");
    public final KeyMapping keyUse = new KeyMapping("key.use", InputConstants.Type.MOUSE, 1, "key.categories.gameplay");
    public final KeyMapping keyAttack = new KeyMapping("key.attack", InputConstants.Type.MOUSE, 0, "key.categories.gameplay");
    public final KeyMapping keyPickItem = new KeyMapping("key.pickItem", InputConstants.Type.MOUSE, 2, "key.categories.gameplay");
    public final KeyMapping keyChat = new KeyMapping("key.chat", 84, "key.categories.multiplayer");
    public final KeyMapping keyPlayerList = new KeyMapping("key.playerlist", 258, "key.categories.multiplayer");
    public final KeyMapping keyCommand = new KeyMapping("key.command", 47, "key.categories.multiplayer");
    public final KeyMapping keySocialInteractions = new KeyMapping("key.socialInteractions", 80, "key.categories.multiplayer");
    public final KeyMapping keyScreenshot = new KeyMapping("key.screenshot", 291, "key.categories.misc");
    public final KeyMapping keyTogglePerspective = new KeyMapping("key.togglePerspective", 294, "key.categories.misc");
    public final KeyMapping keySmoothCamera = new KeyMapping("key.smoothCamera", InputConstants.UNKNOWN.getValue(), "key.categories.misc");
    public final KeyMapping keyFullscreen = new KeyMapping("key.fullscreen", 300, "key.categories.misc");
    public final KeyMapping keySpectatorOutlines = new KeyMapping("key.spectatorOutlines", InputConstants.UNKNOWN.getValue(), "key.categories.misc");
    public final KeyMapping keyAdvancements = new KeyMapping("key.advancements", 76, "key.categories.misc");
    public final KeyMapping[] keyHotbarSlots = new KeyMapping[]{
        new KeyMapping("key.hotbar.1", 49, "key.categories.inventory"),
        new KeyMapping("key.hotbar.2", 50, "key.categories.inventory"),
        new KeyMapping("key.hotbar.3", 51, "key.categories.inventory"),
        new KeyMapping("key.hotbar.4", 52, "key.categories.inventory"),
        new KeyMapping("key.hotbar.5", 53, "key.categories.inventory"),
        new KeyMapping("key.hotbar.6", 54, "key.categories.inventory"),
        new KeyMapping("key.hotbar.7", 55, "key.categories.inventory"),
        new KeyMapping("key.hotbar.8", 56, "key.categories.inventory"),
        new KeyMapping("key.hotbar.9", 57, "key.categories.inventory")
    };
    public final KeyMapping keySaveHotbarActivator = new KeyMapping("key.saveToolbarActivator", 67, "key.categories.creative");
    public final KeyMapping keyLoadHotbarActivator = new KeyMapping("key.loadToolbarActivator", 88, "key.categories.creative");
    public final KeyMapping[] keyMappings = ArrayUtils.addAll(
        new KeyMapping[]{
            this.keyAttack,
            this.keyUse,
            this.keyUp,
            this.keyLeft,
            this.keyDown,
            this.keyRight,
            this.keyJump,
            this.keyShift,
            this.keySprint,
            this.keyDrop,
            this.keyInventory,
            this.keyChat,
            this.keyPlayerList,
            this.keyPickItem,
            this.keyCommand,
            this.keySocialInteractions,
            this.keyScreenshot,
            this.keyTogglePerspective,
            this.keySmoothCamera,
            this.keyFullscreen,
            this.keySpectatorOutlines,
            this.keySwapOffhand,
            this.keySaveHotbarActivator,
            this.keyLoadHotbarActivator,
            this.keyAdvancements
        },
        this.keyHotbarSlots
    );
    protected Minecraft minecraft;
    private final File optionsFile;
    public boolean hideGui;
    private CameraType cameraType = CameraType.FIRST_PERSON;
    public String lastMpIp = "";
    public boolean smoothCamera;
    private final OptionInstance<Integer> fov = new OptionInstance<>(
        "options.fov",
        OptionInstance.noTooltip(),
        (component, integer) -> {
            return switch (integer) {
                case 70 -> genericValueLabel(component, Component.translatable("options.fov.min"));
                case 110 -> genericValueLabel(component, Component.translatable("options.fov.max"));
                default -> genericValueLabel(component, integer);
            };
        },
        new OptionInstance.IntRange(30, 110),
        Codec.DOUBLE.xmap(double_ -> (int)(double_ * 40.0 + 70.0), integer -> (integer.intValue() - 70.0) / 40.0),
        70,
        integer -> Minecraft.getInstance().levelRenderer.needsUpdate()
    );
    private static final Component TELEMETRY_TOOLTIP = Component.translatable(
        "options.telemetry.button.tooltip", Component.translatable("options.telemetry.state.minimal"), Component.translatable("options.telemetry.state.all")
    );
    private final OptionInstance<Boolean> telemetryOptInExtra = OptionInstance.createBoolean(
        "options.telemetry.button",
        OptionInstance.cachedConstantTooltip(TELEMETRY_TOOLTIP),
        (component, boolean_) -> {
            Minecraft minecraftx = Minecraft.getInstance();
            if (!minecraftx.allowsTelemetry()) {
                return Component.translatable("options.telemetry.state.none");
            } else {
                return boolean_ && minecraftx.extraTelemetryAvailable()
                    ? Component.translatable("options.telemetry.state.all")
                    : Component.translatable("options.telemetry.state.minimal");
            }
        },
        false,
        boolean_ -> {}
    );
    private static final Component ACCESSIBILITY_TOOLTIP_SCREEN_EFFECT = Component.translatable("options.screenEffectScale.tooltip");
    private final OptionInstance<Double> screenEffectScale = new OptionInstance<>(
        "options.screenEffectScale",
        OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_SCREEN_EFFECT),
        Options::percentValueOrOffLabel,
        OptionInstance.UnitDouble.INSTANCE,
        1.0,
        effectScale -> {}
    );
    private static final Component ACCESSIBILITY_TOOLTIP_FOV_EFFECT = Component.translatable("options.fovEffectScale.tooltip");
    private final OptionInstance<Double> fovEffectScale = new OptionInstance<>(
        "options.fovEffectScale",
        OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_FOV_EFFECT),
        Options::percentValueOrOffLabel,
        OptionInstance.UnitDouble.INSTANCE.xmap(Mth::square, Math::sqrt),
        Codec.doubleRange(0.0, 1.0),
        1.0,
        double_ -> {}
    );
    private static final Component ACCESSIBILITY_TOOLTIP_DARKNESS_EFFECT = Component.translatable("options.darknessEffectScale.tooltip");
    private final OptionInstance<Double> darknessEffectScale = new OptionInstance<>(
        "options.darknessEffectScale",
        OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_DARKNESS_EFFECT),
        Options::percentValueOrOffLabel,
        OptionInstance.UnitDouble.INSTANCE.xmap(Mth::square, Math::sqrt),
        1.0,
        double_ -> {}
    );
    private static final Component ACCESSIBILITY_TOOLTIP_GLINT_SPEED = Component.translatable("options.glintSpeed.tooltip");
    private final OptionInstance<Double> glintSpeed = new OptionInstance<>(
        "options.glintSpeed",
        OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_GLINT_SPEED),
        Options::percentValueOrOffLabel,
        OptionInstance.UnitDouble.INSTANCE,
        0.5,
        double_ -> {}
    );
    private static final Component ACCESSIBILITY_TOOLTIP_GLINT_STRENGTH = Component.translatable("options.glintStrength.tooltip");
    private final OptionInstance<Double> glintStrength = new OptionInstance<>(
        "options.glintStrength",
        OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_GLINT_STRENGTH),
        Options::percentValueOrOffLabel,
        OptionInstance.UnitDouble.INSTANCE,
        0.75,
        RenderSystem::setShaderGlintAlpha
    );
    private static final Component ACCESSIBILITY_TOOLTIP_DAMAGE_TILT_STRENGTH = Component.translatable("options.damageTiltStrength.tooltip");
    private final OptionInstance<Double> damageTiltStrength = new OptionInstance<>(
        "options.damageTiltStrength",
        OptionInstance.cachedConstantTooltip(ACCESSIBILITY_TOOLTIP_DAMAGE_TILT_STRENGTH),
        Options::percentValueOrOffLabel,
        OptionInstance.UnitDouble.INSTANCE,
        1.0,
        darknessScale -> {}
    );
    private final OptionInstance<Double> gamma = new OptionInstance<>("options.gamma", OptionInstance.noTooltip(), (component, glintSpeed) -> {
        int i = (int)(glintSpeed * 100.0);
        if (i == 0) {
            return genericValueLabel(component, Component.translatable("options.gamma.min"));
        } else if (i == 50) {
            return genericValueLabel(component, Component.translatable("options.gamma.default"));
        } else {
            return i == 100 ? genericValueLabel(component, Component.translatable("options.gamma.max")) : genericValueLabel(component, i);
        }
    }, OptionInstance.UnitDouble.INSTANCE, 0.5, glintSpeed -> {});
    public static final int AUTO_GUI_SCALE = 0;
    private static final int MAX_GUI_SCALE_INCLUSIVE = 2147483646;
    private final OptionInstance<Integer> guiScale = new OptionInstance<>(
        "options.guiScale",
        OptionInstance.noTooltip(),
        (component, integer) -> integer == 0 ? Component.translatable("options.guiScale.auto") : Component.literal(Integer.toString(integer)),
        new OptionInstance.ClampingLazyMaxIntRange(0, () -> {
            Minecraft minecraftx = Minecraft.getInstance();
            return !minecraftx.isRunning() ? 2147483646 : minecraftx.getWindow().calculateScale(0, minecraftx.isEnforceUnicode());
        }, 2147483646),
        0,
        integer -> this.minecraft.resizeDisplay()
    );
    private final OptionInstance<ParticleStatus> particles = new OptionInstance<>(
        "options.particles",
        OptionInstance.noTooltip(),
        OptionInstance.forOptionEnum(),
        new OptionInstance.Enum<>(Arrays.asList(ParticleStatus.values()), Codec.INT.xmap(ParticleStatus::byId, ParticleStatus::getId)),
        ParticleStatus.ALL,
        particleStatus -> {}
    );
    private final OptionInstance<NarratorStatus> narrator = new OptionInstance<>(
        "options.narrator",
        OptionInstance.noTooltip(),
        (component, narratorStatus) -> this.minecraft.getNarrator().isActive()
            ? narratorStatus.getName()
            : Component.translatable("options.narrator.notavailable"),
        new OptionInstance.Enum<>(Arrays.asList(NarratorStatus.values()), Codec.INT.xmap(NarratorStatus::byId, NarratorStatus::getId)),
        NarratorStatus.OFF,
        narratorStatus -> this.minecraft.getNarrator().updateNarratorStatus(narratorStatus)
    );
    public String languageCode = "en_us";
    private final OptionInstance<String> soundDevice = new OptionInstance<>(
        "options.audioDevice",
        OptionInstance.noTooltip(),
        (component, string) -> {
            if ("".equals(string)) {
                return Component.translatable("options.audioDevice.default");
            } else {
                return string.startsWith("OpenAL Soft on ")
                    ? Component.literal(string.substring(SoundEngine.OPEN_AL_SOFT_PREFIX_LENGTH))
                    : Component.literal(string);
            }
        },
        new OptionInstance.LazyEnum<>(
            () -> Stream.concat(Stream.of(""), Minecraft.getInstance().getSoundManager().getAvailableSoundDevices().stream()).toList(),
            string -> Minecraft.getInstance().isRunning()
                    && string != ""
                    && !Minecraft.getInstance().getSoundManager().getAvailableSoundDevices().contains(string)
                ? Optional.empty()
                : Optional.of(string),
            Codec.STRING
        ),
        "",
        string -> {
            SoundManager soundManager = Minecraft.getInstance().getSoundManager();
            soundManager.reload();
            soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    );
    public boolean onboardAccessibility = true;
    public boolean syncWrites;

    public OptionInstance<Boolean> darkMojangStudiosBackground() {
        return this.darkMojangStudiosBackground;
    }

    public OptionInstance<Boolean> hideLightningFlash() {
        return this.hideLightningFlash;
    }

    public OptionInstance<Boolean> hideSplashTexts() {
        return this.hideSplashTexts;
    }

    public OptionInstance<Double> sensitivity() {
        return this.sensitivity;
    }

    public OptionInstance<Integer> renderDistance() {
        return this.renderDistance;
    }

    public OptionInstance<Integer> simulationDistance() {
        return this.simulationDistance;
    }

    public OptionInstance<Double> entityDistanceScaling() {
        return this.entityDistanceScaling;
    }

    public OptionInstance<Integer> framerateLimit() {
        return this.framerateLimit;
    }

    public OptionInstance<CloudStatus> cloudStatus() {
        return this.cloudStatus;
    }

    public OptionInstance<GraphicsStatus> graphicsMode() {
        return this.graphicsMode;
    }

    public OptionInstance<Boolean> ambientOcclusion() {
        return this.ambientOcclusion;
    }

    public OptionInstance<PrioritizeChunkUpdates> prioritizeChunkUpdates() {
        return this.prioritizeChunkUpdates;
    }

    public void updateResourcePacks(PackRepository packRepository) {
        List<String> list = ImmutableList.copyOf(this.resourcePacks);
        this.resourcePacks.clear();
        this.incompatibleResourcePacks.clear();

        for (Pack pack : packRepository.getSelectedPacks()) {
            if (!pack.isFixedPosition()) {
                this.resourcePacks.add(pack.getId());
                if (!pack.getCompatibility().isCompatible()) {
                    this.incompatibleResourcePacks.add(pack.getId());
                }
            }
        }

        this.save();
        List<String> list2 = ImmutableList.copyOf(this.resourcePacks);
        if (!list2.equals(list)) {
            this.minecraft.reloadResourcePacks();
        }
    }

    public OptionInstance<ChatVisiblity> chatVisibility() {
        return this.chatVisibility;
    }

    public OptionInstance<Double> chatOpacity() {
        return this.chatOpacity;
    }

    public OptionInstance<Double> chatLineSpacing() {
        return this.chatLineSpacing;
    }

    public OptionInstance<Integer> menuBackgroundBlurriness() {
        return this.menuBackgroundBlurriness;
    }

    public int getMenuBackgroundBlurriness() {
        return this.menuBackgroundBlurriness().get();
    }

    public OptionInstance<Double> textBackgroundOpacity() {
        return this.textBackgroundOpacity;
    }

    public OptionInstance<Double> panoramaSpeed() {
        return this.panoramaSpeed;
    }

    public OptionInstance<Boolean> highContrast() {
        return this.highContrast;
    }

    public OptionInstance<Boolean> narratorHotkey() {
        return this.narratorHotkey;
    }

    public OptionInstance<HumanoidArm> mainHand() {
        return this.mainHand;
    }

    public OptionInstance<Double> chatScale() {
        return this.chatScale;
    }

    public OptionInstance<Double> chatWidth() {
        return this.chatWidth;
    }

    public OptionInstance<Double> chatHeightUnfocused() {
        return this.chatHeightUnfocused;
    }

    public OptionInstance<Double> chatHeightFocused() {
        return this.chatHeightFocused;
    }

    public OptionInstance<Double> chatDelay() {
        return this.chatDelay;
    }

    public OptionInstance<Double> notificationDisplayTime() {
        return this.notificationDisplayTime;
    }

    public OptionInstance<Integer> mipmapLevels() {
        return this.mipmapLevels;
    }

    public OptionInstance<AttackIndicatorStatus> attackIndicator() {
        return this.attackIndicator;
    }

    public OptionInstance<Integer> biomeBlendRadius() {
        return this.biomeBlendRadius;
    }

    private static double logMouse(int input) {
        return Math.pow(10.0, input / 100.0);
    }

    private static int unlogMouse(double input) {
        return Mth.floor(Math.log10(input) * 100.0);
    }

    public OptionInstance<Double> mouseWheelSensitivity() {
        return this.mouseWheelSensitivity;
    }

    public OptionInstance<Boolean> rawMouseInput() {
        return this.rawMouseInput;
    }

    public OptionInstance<Boolean> autoJump() {
        return this.autoJump;
    }

    public OptionInstance<Boolean> operatorItemsTab() {
        return this.operatorItemsTab;
    }

    public OptionInstance<Boolean> autoSuggestions() {
        return this.autoSuggestions;
    }

    public OptionInstance<Boolean> chatColors() {
        return this.chatColors;
    }

    public OptionInstance<Boolean> chatLinks() {
        return this.chatLinks;
    }

    public OptionInstance<Boolean> chatLinksPrompt() {
        return this.chatLinksPrompt;
    }

    public OptionInstance<Boolean> enableVsync() {
        return this.enableVsync;
    }

    public OptionInstance<Boolean> entityShadows() {
        return this.entityShadows;
    }

    private static void updateFontOptions() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() != null) {
            minecraft.updateFontOptions();
            minecraft.resizeDisplay();
        }
    }

    public OptionInstance<Boolean> forceUnicodeFont() {
        return this.forceUnicodeFont;
    }

    private static boolean japaneseGlyphVariantsDefault() {
        return Locale.getDefault().getLanguage().equalsIgnoreCase("ja");
    }

    public OptionInstance<Boolean> japaneseGlyphVariants() {
        return this.japaneseGlyphVariants;
    }

    public OptionInstance<Boolean> invertYMouse() {
        return this.invertYMouse;
    }

    public OptionInstance<Boolean> discreteMouseScroll() {
        return this.discreteMouseScroll;
    }

    public OptionInstance<Boolean> realmsNotifications() {
        return this.realmsNotifications;
    }

    public OptionInstance<Boolean> allowServerListing() {
        return this.allowServerListing;
    }

    public OptionInstance<Boolean> reducedDebugInfo() {
        return this.reducedDebugInfo;
    }

    public final float getSoundSourceVolume(SoundSource category) {
        return this.getSoundSourceOptionInstance(category).get().floatValue();
    }

    public final OptionInstance<Double> getSoundSourceOptionInstance(SoundSource soundSource) {
        return Objects.requireNonNull(this.soundSourceVolumes.get(soundSource));
    }

    private OptionInstance<Double> createSoundSliderOptionInstance(String text, SoundSource soundSource) {
        return new OptionInstance<>(
            text,
            OptionInstance.noTooltip(),
            Options::percentValueOrOffLabel,
            OptionInstance.UnitDouble.INSTANCE,
            1.0,
            double_ -> Minecraft.getInstance().getSoundManager().updateSourceVolume(soundSource, double_.floatValue())
        );
    }

    public OptionInstance<Boolean> showSubtitles() {
        return this.showSubtitles;
    }

    public OptionInstance<Boolean> directionalAudio() {
        return this.directionalAudio;
    }

    public OptionInstance<Boolean> backgroundForChatOnly() {
        return this.backgroundForChatOnly;
    }

    public OptionInstance<Boolean> touchscreen() {
        return this.touchscreen;
    }

    public OptionInstance<Boolean> fullscreen() {
        return this.fullscreen;
    }

    public OptionInstance<Boolean> bobView() {
        return this.bobView;
    }

    public OptionInstance<Boolean> toggleCrouch() {
        return this.toggleCrouch;
    }

    public OptionInstance<Boolean> toggleSprint() {
        return this.toggleSprint;
    }

    public OptionInstance<Boolean> hideMatchedNames() {
        return this.hideMatchedNames;
    }

    public OptionInstance<Boolean> showAutosaveIndicator() {
        return this.showAutosaveIndicator;
    }

    public OptionInstance<Boolean> onlyShowSecureChat() {
        return this.onlyShowSecureChat;
    }

    public OptionInstance<Integer> fov() {
        return this.fov;
    }

    public OptionInstance<Boolean> telemetryOptInExtra() {
        return this.telemetryOptInExtra;
    }

    public OptionInstance<Double> screenEffectScale() {
        return this.screenEffectScale;
    }

    public OptionInstance<Double> fovEffectScale() {
        return this.fovEffectScale;
    }

    public OptionInstance<Double> darknessEffectScale() {
        return this.darknessEffectScale;
    }

    public OptionInstance<Double> glintSpeed() {
        return this.glintSpeed;
    }

    public OptionInstance<Double> glintStrength() {
        return this.glintStrength;
    }

    public OptionInstance<Double> damageTiltStrength() {
        return this.damageTiltStrength;
    }

    public OptionInstance<Double> gamma() {
        return this.gamma;
    }

    public OptionInstance<Integer> guiScale() {
        return this.guiScale;
    }

    public OptionInstance<ParticleStatus> particles() {
        return this.particles;
    }

    public OptionInstance<NarratorStatus> narrator() {
        return this.narrator;
    }

    public OptionInstance<String> soundDevice() {
        return this.soundDevice;
    }

    public void onboardingAccessibilityFinished() {
        this.onboardAccessibility = false;
        this.save();
    }

    public Options(Minecraft minecraft, File gameDirectory) {
        this.minecraft = minecraft;
        this.optionsFile = new File(gameDirectory, "options.txt");
        boolean bl = Runtime.getRuntime().maxMemory() >= 1000000000L;
        this.renderDistance = new OptionInstance<>(
            "options.renderDistance",
            OptionInstance.noTooltip(),
            (component, integer) -> genericValueLabel(component, Component.translatable("options.chunks", integer)),
            new OptionInstance.IntRange(2, bl ? 32 : 16, false),
            12,
            integer -> Minecraft.getInstance().levelRenderer.needsUpdate()
        );
        this.simulationDistance = new OptionInstance<>(
            "options.simulationDistance",
            OptionInstance.noTooltip(),
            (component, integer) -> genericValueLabel(component, Component.translatable("options.chunks", integer)),
            new OptionInstance.IntRange(5, bl ? 32 : 16, false),
            12,
            integer -> {}
        );
        this.syncWrites = Util.getPlatform() == Util.OS.WINDOWS;
        this.load();
    }

    public float getBackgroundOpacity(float opacity) {
        return this.backgroundForChatOnly.get() ? opacity : this.textBackgroundOpacity().get().floatValue();
    }

    public int getBackgroundColor(float opacity) {
        return (int)(this.getBackgroundOpacity(opacity) * 255.0F) << 24 & 0xFF000000;
    }

    public int getBackgroundColor(int chatColor) {
        return this.backgroundForChatOnly.get() ? chatColor : (int)(this.textBackgroundOpacity.get() * 255.0) << 24 & 0xFF000000;
    }

    public void setKey(KeyMapping keyBinding, InputConstants.Key input) {
        keyBinding.setKey(input);
        this.save();
    }

    private void processDumpedOptions(Options.OptionAccess optionAccess) {
        optionAccess.process("ao", this.ambientOcclusion);
        optionAccess.process("biomeBlendRadius", this.biomeBlendRadius);
        optionAccess.process("enableVsync", this.enableVsync);
        optionAccess.process("entityDistanceScaling", this.entityDistanceScaling);
        optionAccess.process("entityShadows", this.entityShadows);
        optionAccess.process("forceUnicodeFont", this.forceUnicodeFont);
        optionAccess.process("japaneseGlyphVariants", this.japaneseGlyphVariants);
        optionAccess.process("fov", this.fov);
        optionAccess.process("fovEffectScale", this.fovEffectScale);
        optionAccess.process("darknessEffectScale", this.darknessEffectScale);
        optionAccess.process("glintSpeed", this.glintSpeed);
        optionAccess.process("glintStrength", this.glintStrength);
        optionAccess.process("prioritizeChunkUpdates", this.prioritizeChunkUpdates);
        optionAccess.process("fullscreen", this.fullscreen);
        optionAccess.process("gamma", this.gamma);
        optionAccess.process("graphicsMode", this.graphicsMode);
        optionAccess.process("guiScale", this.guiScale);
        optionAccess.process("maxFps", this.framerateLimit);
        optionAccess.process("mipmapLevels", this.mipmapLevels);
        optionAccess.process("narrator", this.narrator);
        optionAccess.process("particles", this.particles);
        optionAccess.process("reducedDebugInfo", this.reducedDebugInfo);
        optionAccess.process("renderClouds", this.cloudStatus);
        optionAccess.process("renderDistance", this.renderDistance);
        optionAccess.process("simulationDistance", this.simulationDistance);
        optionAccess.process("screenEffectScale", this.screenEffectScale);
        optionAccess.process("soundDevice", this.soundDevice);
    }

    private void processOptions(Options.FieldAccess accessor) {
        this.processDumpedOptions(accessor);
        accessor.process("autoJump", this.autoJump);
        accessor.process("operatorItemsTab", this.operatorItemsTab);
        accessor.process("autoSuggestions", this.autoSuggestions);
        accessor.process("chatColors", this.chatColors);
        accessor.process("chatLinks", this.chatLinks);
        accessor.process("chatLinksPrompt", this.chatLinksPrompt);
        accessor.process("discrete_mouse_scroll", this.discreteMouseScroll);
        accessor.process("invertYMouse", this.invertYMouse);
        accessor.process("realmsNotifications", this.realmsNotifications);
        accessor.process("showSubtitles", this.showSubtitles);
        accessor.process("directionalAudio", this.directionalAudio);
        accessor.process("touchscreen", this.touchscreen);
        accessor.process("bobView", this.bobView);
        accessor.process("toggleCrouch", this.toggleCrouch);
        accessor.process("toggleSprint", this.toggleSprint);
        accessor.process("darkMojangStudiosBackground", this.darkMojangStudiosBackground);
        accessor.process("hideLightningFlashes", this.hideLightningFlash);
        accessor.process("hideSplashTexts", this.hideSplashTexts);
        accessor.process("mouseSensitivity", this.sensitivity);
        accessor.process("damageTiltStrength", this.damageTiltStrength);
        accessor.process("highContrast", this.highContrast);
        accessor.process("narratorHotkey", this.narratorHotkey);
        this.resourcePacks = accessor.process("resourcePacks", this.resourcePacks, Options::readListOfStrings, GSON::toJson);
        this.incompatibleResourcePacks = accessor.process("incompatibleResourcePacks", this.incompatibleResourcePacks, Options::readListOfStrings, GSON::toJson);
        this.lastMpIp = accessor.process("lastServer", this.lastMpIp);
        this.languageCode = accessor.process("lang", this.languageCode);
        accessor.process("chatVisibility", this.chatVisibility);
        accessor.process("chatOpacity", this.chatOpacity);
        accessor.process("chatLineSpacing", this.chatLineSpacing);
        accessor.process("textBackgroundOpacity", this.textBackgroundOpacity);
        accessor.process("backgroundForChatOnly", this.backgroundForChatOnly);
        this.hideServerAddress = accessor.process("hideServerAddress", this.hideServerAddress);
        this.advancedItemTooltips = accessor.process("advancedItemTooltips", this.advancedItemTooltips);
        this.pauseOnLostFocus = accessor.process("pauseOnLostFocus", this.pauseOnLostFocus);
        this.overrideWidth = accessor.process("overrideWidth", this.overrideWidth);
        this.overrideHeight = accessor.process("overrideHeight", this.overrideHeight);
        accessor.process("chatHeightFocused", this.chatHeightFocused);
        accessor.process("chatDelay", this.chatDelay);
        accessor.process("chatHeightUnfocused", this.chatHeightUnfocused);
        accessor.process("chatScale", this.chatScale);
        accessor.process("chatWidth", this.chatWidth);
        accessor.process("notificationDisplayTime", this.notificationDisplayTime);
        this.useNativeTransport = accessor.process("useNativeTransport", this.useNativeTransport);
        accessor.process("mainHand", this.mainHand);
        accessor.process("attackIndicator", this.attackIndicator);
        this.tutorialStep = accessor.process("tutorialStep", this.tutorialStep, TutorialSteps::getByName, TutorialSteps::getName);
        accessor.process("mouseWheelSensitivity", this.mouseWheelSensitivity);
        accessor.process("rawMouseInput", this.rawMouseInput);
        this.glDebugVerbosity = accessor.process("glDebugVerbosity", this.glDebugVerbosity);
        this.skipMultiplayerWarning = accessor.process("skipMultiplayerWarning", this.skipMultiplayerWarning);
        accessor.process("hideMatchedNames", this.hideMatchedNames);
        this.joinedFirstServer = accessor.process("joinedFirstServer", this.joinedFirstServer);
        this.hideBundleTutorial = accessor.process("hideBundleTutorial", this.hideBundleTutorial);
        this.syncWrites = accessor.process("syncChunkWrites", this.syncWrites);
        accessor.process("showAutosaveIndicator", this.showAutosaveIndicator);
        accessor.process("allowServerListing", this.allowServerListing);
        accessor.process("onlyShowSecureChat", this.onlyShowSecureChat);
        accessor.process("panoramaScrollSpeed", this.panoramaSpeed);
        accessor.process("telemetryOptInExtra", this.telemetryOptInExtra);
        this.onboardAccessibility = accessor.process("onboardAccessibility", this.onboardAccessibility);
        accessor.process("menuBackgroundBlurriness", this.menuBackgroundBlurriness);

        for (KeyMapping keyMapping : this.keyMappings) {
            String string = keyMapping.saveString();
            String string2 = accessor.process("key_" + keyMapping.getName(), string);
            if (!string.equals(string2)) {
                keyMapping.setKey(InputConstants.getKey(string2));
            }
        }

        for (SoundSource soundSource : SoundSource.values()) {
            accessor.process("soundCategory_" + soundSource.getName(), this.soundSourceVolumes.get(soundSource));
        }

        for (PlayerModelPart playerModelPart : PlayerModelPart.values()) {
            boolean bl = this.modelParts.contains(playerModelPart);
            boolean bl2 = accessor.process("modelPart_" + playerModelPart.getId(), bl);
            if (bl2 != bl) {
                this.setModelPart(playerModelPart, bl2);
            }
        }
    }

    public void load() {
        try {
            if (!this.optionsFile.exists()) {
                return;
            }

            CompoundTag compoundTag = new CompoundTag();

            try (BufferedReader bufferedReader = Files.newReader(this.optionsFile, Charsets.UTF_8)) {
                bufferedReader.lines().forEach(string -> {
                    try {
                        Iterator<String> iterator = OPTION_SPLITTER.split(string).iterator();
                        compoundTag.putString(iterator.next(), iterator.next());
                    } catch (Exception exceptionx) {
                        LOGGER.warn("Skipping bad option: {}", string);
                    }
                });
            }

            final CompoundTag compoundTag2 = this.dataFix(compoundTag);
            if (!compoundTag2.contains("graphicsMode") && compoundTag2.contains("fancyGraphics")) {
                if (isTrue(compoundTag2.getString("fancyGraphics"))) {
                    this.graphicsMode.set(GraphicsStatus.FANCY);
                } else {
                    this.graphicsMode.set(GraphicsStatus.FAST);
                }
            }

            this.processOptions(
                new Options.FieldAccess() {
                    @Nullable
                    private String getValueOrNull(String name) {
                        return compoundTag2.contains(name) ? compoundTag2.get(name).getAsString() : null;
                    }

                    @Override
                    public <T> void process(String name, OptionInstance<T> value) {
                        String string = this.getValueOrNull(name);
                        if (string != null) {
                            JsonReader jsonReader = new JsonReader(new StringReader(string.isEmpty() ? "\"\"" : string));
                            JsonElement jsonElement = JsonParser.parseReader(jsonReader);
                            DataResult<T> dataResult = value.codec().parse(JsonOps.INSTANCE, jsonElement);
                            dataResult.error()
                                .ifPresent(
                                    error -> Options.LOGGER.error("Error parsing option value " + string + " for option " + value + ": " + error.message())
                                );
                            dataResult.ifSuccess(value::set);
                        }
                    }

                    @Override
                    public int process(String name, int value) {
                        String string = this.getValueOrNull(name);
                        if (string != null) {
                            try {
                                return Integer.parseInt(string);
                            } catch (NumberFormatException numberFormatException) {
                                Options.LOGGER.warn("Invalid integer value for option {} = {}", name, string, numberFormatException);
                            }
                        }

                        return value;
                    }

                    @Override
                    public boolean process(String name, boolean value) {
                        String string = this.getValueOrNull(name);
                        return string != null ? Options.isTrue(string) : value;
                    }

                    @Override
                    public String process(String name, String value) {
                        return MoreObjects.firstNonNull(this.getValueOrNull(name), value);
                    }

                    @Override
                    public float process(String name, float value) {
                        String string = this.getValueOrNull(name);
                        if (string == null) {
                            return value;
                        }

                        if (Options.isTrue(string)) {
                            return 1.0F;
                        }

                        if (Options.isFalse(string)) {
                            return 0.0F;
                        }

                        try {
                            return Float.parseFloat(string);
                        } catch (NumberFormatException numberFormatException) {
                            Options.LOGGER.warn("Invalid floating point value for option {} = {}", name, string, numberFormatException);
                            return value;
                        }
                    }

                    @Override
                    public <T> T process(String name, T value, Function<String, T> stringValuefier, Function<T, String> valueStringifier) {
                        String string = this.getValueOrNull(name);
                        return string == null ? value : stringValuefier.apply(string);
                    }
                }
            );
            if (compoundTag2.contains("fullscreenResolution")) {
                this.fullscreenVideoModeString = compoundTag2.getString("fullscreenResolution");
            }

            if (this.minecraft.getWindow() != null) {
                this.minecraft.getWindow().setFramerateLimit(this.framerateLimit.get());
            }

            KeyMapping.resetMapping();
        } catch (Exception exception) {
            LOGGER.error("Failed to load options", exception);
        }
    }

    static boolean isTrue(String value) {
        return "true".equals(value);
    }

    static boolean isFalse(String value) {
        return "false".equals(value);
    }

    private CompoundTag dataFix(CompoundTag nbt) {
        int i = 0;

        try {
            i = Integer.parseInt(nbt.getString("version"));
        } catch (RuntimeException var4) {
        }

        return DataFixTypes.OPTIONS.updateToCurrentVersion(this.minecraft.getFixerUpper(), nbt, i);
    }

    public void save() {
        try (final PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(this.optionsFile), StandardCharsets.UTF_8))) {
            printWriter.println("version:" + SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            this.processOptions(
                new Options.FieldAccess() {
                    public void writePrefix(String prefix) {
                        printWriter.print(prefix);
                        printWriter.print(':');
                    }

                    @Override
                    public <T> void process(String name, OptionInstance<T> value) {
                        value.codec()
                            .encodeStart(JsonOps.INSTANCE, value.get())
                            .ifError(error -> Options.LOGGER.error("Error saving option " + value + ": " + error))
                            .ifSuccess(jsonElement -> {
                                this.writePrefix(name);
                                printWriter.println(Options.GSON.toJson(jsonElement));
                            });
                    }

                    @Override
                    public int process(String name, int value) {
                        this.writePrefix(name);
                        printWriter.println(value);
                        return value;
                    }

                    @Override
                    public boolean process(String name, boolean value) {
                        this.writePrefix(name);
                        printWriter.println(value);
                        return value;
                    }

                    @Override
                    public String process(String name, String value) {
                        this.writePrefix(name);
                        printWriter.println(value);
                        return value;
                    }

                    @Override
                    public float process(String name, float value) {
                        this.writePrefix(name);
                        printWriter.println(value);
                        return value;
                    }

                    @Override
                    public <T> T process(String name, T value, Function<String, T> stringValuefier, Function<T, String> valueStringifier) {
                        this.writePrefix(name);
                        printWriter.println(valueStringifier.apply(value));
                        return value;
                    }
                }
            );
            if (this.minecraft.getWindow().getPreferredFullscreenVideoMode().isPresent()) {
                printWriter.println("fullscreenResolution:" + this.minecraft.getWindow().getPreferredFullscreenVideoMode().get().write());
            }
        } catch (Exception exception) {
            LOGGER.error("Failed to save options", exception);
        }

        this.broadcastOptions();
    }

    public ClientInformation buildPlayerInformation() {
        int i = 0;

        for (PlayerModelPart playerModelPart : this.modelParts) {
            i |= playerModelPart.getMask();
        }

        return new ClientInformation(
            this.languageCode,
            this.renderDistance.get(),
            this.chatVisibility.get(),
            this.chatColors.get(),
            i,
            this.mainHand.get(),
            this.minecraft.isTextFilteringEnabled(),
            this.allowServerListing.get()
        );
    }

    public void broadcastOptions() {
        if (this.minecraft.player != null) {
            this.minecraft.player.connection.send(new ServerboundClientInformationPacket(this.buildPlayerInformation()));
        }
    }

    private void setModelPart(PlayerModelPart modelPart, boolean enable) {
        if (enable) {
            this.modelParts.add(modelPart);
        } else {
            this.modelParts.remove(modelPart);
        }
    }

    public boolean isModelPartEnabled(PlayerModelPart playerModelPart) {
        return this.modelParts.contains(playerModelPart);
    }

    public void toggleModelPart(PlayerModelPart playerModelPart, boolean enable) {
        this.setModelPart(playerModelPart, enable);
        this.broadcastOptions();
    }

    public CloudStatus getCloudsType() {
        return this.getEffectiveRenderDistance() >= 4 ? this.cloudStatus.get() : CloudStatus.OFF;
    }

    public boolean useNativeTransport() {
        return this.useNativeTransport;
    }

    public void loadSelectedResourcePacks(PackRepository resourcePackList) {
        Set<String> set = Sets.newLinkedHashSet();
        Iterator<String> iterator = this.resourcePacks.iterator();

        while (iterator.hasNext()) {
            String string = iterator.next();
            Pack pack = resourcePackList.getPack(string);
            if (pack == null && !string.startsWith("file/")) {
                pack = resourcePackList.getPack("file/" + string);
            }

            if (pack == null) {
                LOGGER.warn("Removed resource pack {} from options because it doesn't seem to exist anymore", string);
                iterator.remove();
            } else if (!pack.getCompatibility().isCompatible() && !this.incompatibleResourcePacks.contains(string)) {
                LOGGER.warn("Removed resource pack {} from options because it is no longer compatible", string);
                iterator.remove();
            } else if (pack.getCompatibility().isCompatible() && this.incompatibleResourcePacks.contains(string)) {
                LOGGER.info("Removed resource pack {} from incompatibility list because it's now compatible", string);
                this.incompatibleResourcePacks.remove(string);
            } else {
                set.add(pack.getId());
            }
        }

        resourcePackList.setSelected(set);
    }

    public CameraType getCameraType() {
        return this.cameraType;
    }

    public void setCameraType(CameraType pointOfView) {
        this.cameraType = pointOfView;
    }

    private static List<String> readListOfStrings(String json) {
        List<String> list = GsonHelper.fromNullableJson(GSON, json, LIST_OF_STRINGS_TYPE);
        return list != null ? list : Lists.newArrayList();
    }

    public File getFile() {
        return this.optionsFile;
    }

    public String dumpOptionsForReport() {
        final List<Pair<String, Object>> list = new ArrayList<>();
        this.processDumpedOptions(new Options.OptionAccess() {
            @Override
            public <T> void process(String string, OptionInstance<T> optionInstance) {
                list.add(Pair.of(string, optionInstance.get()));
            }
        });
        list.add(Pair.of("fullscreenResolution", String.valueOf(this.fullscreenVideoModeString)));
        list.add(Pair.of("glDebugVerbosity", this.glDebugVerbosity));
        list.add(Pair.of("overrideHeight", this.overrideHeight));
        list.add(Pair.of("overrideWidth", this.overrideWidth));
        list.add(Pair.of("syncChunkWrites", this.syncWrites));
        list.add(Pair.of("useNativeTransport", this.useNativeTransport));
        list.add(Pair.of("resourcePacks", this.resourcePacks));
        return list.stream()
            .sorted(Comparator.comparing(Pair::getFirst))
            .map(pair -> pair.getFirst() + ": " + pair.getSecond())
            .collect(Collectors.joining(System.lineSeparator()));
    }

    public void setServerRenderDistance(int serverRenderDistance) {
        this.serverRenderDistance = serverRenderDistance;
    }

    public int getEffectiveRenderDistance() {
        return this.serverRenderDistance > 0 ? Math.min(this.renderDistance.get(), this.serverRenderDistance) : this.renderDistance.get();
    }

    private static Component pixelValueLabel(Component text, int value) {
        return Component.translatable("options.pixel_value", text, value);
    }

    private static Component percentValueLabel(Component text, double value) {
        return Component.translatable("options.percent_value", text, (int)(value * 100.0));
    }

    public static Component genericValueLabel(Component text, Component value) {
        return Component.translatable("options.generic_value", text, value);
    }

    public static Component genericValueLabel(Component text, int value) {
        return genericValueLabel(text, Component.literal(Integer.toString(value)));
    }

    public static Component genericValueOrOffLabel(Component text, int value) {
        return value == 0 ? genericValueLabel(text, CommonComponents.OPTION_OFF) : genericValueLabel(text, value);
    }

    private static Component percentValueOrOffLabel(Component text, double value) {
        return value == 0.0 ? genericValueLabel(text, CommonComponents.OPTION_OFF) : percentValueLabel(text, value);
    }

    interface FieldAccess extends Options.OptionAccess {
        int process(String var1, int var2);

        boolean process(String var1, boolean var2);

        String process(String var1, String var2);

        float process(String var1, float var2);

        <T> T process(String var1, T var2, Function<String, T> var3, Function<T, String> var4);
    }

    interface OptionAccess {
        <T> void process(String var1, OptionInstance<T> var2);
    }
}
