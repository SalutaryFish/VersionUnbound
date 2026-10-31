package net.minecraft.client.multiplayer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.time.Instant;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.DebugQueryHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DemoIntroScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.debug.BrainDebugRenderer;
import net.minecraft.client.renderer.debug.NeighborsUpdateRenderer;
import net.minecraft.client.renderer.debug.VillageSectionsDebugRenderer;
import net.minecraft.client.renderer.debug.WorldGenAttemptRenderer;
import net.minecraft.client.resources.sounds.BeeAggressiveSoundInstance;
import net.minecraft.client.resources.sounds.BeeFlyingSoundInstance;
import net.minecraft.client.resources.sounds.BeeSoundInstance;
import net.minecraft.client.resources.sounds.GuardianAttackSoundInstance;
import net.minecraft.client.resources.sounds.MinecartSoundInstance;
import net.minecraft.client.resources.sounds.SnifferSoundInstance;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessagesTracker;
import net.minecraft.network.chat.LocalChatSession;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.chat.SignableCommand;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.chat.SignedMessageLink;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.common.custom.BeeDebugPayload;
import net.minecraft.network.protocol.common.custom.BrainDebugPayload;
import net.minecraft.network.protocol.common.custom.BreezeDebugPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.GameEventDebugPayload;
import net.minecraft.network.protocol.common.custom.GameEventListenerDebugPayload;
import net.minecraft.network.protocol.common.custom.GameTestAddMarkerDebugPayload;
import net.minecraft.network.protocol.common.custom.GameTestClearMarkersDebugPayload;
import net.minecraft.network.protocol.common.custom.GoalDebugPayload;
import net.minecraft.network.protocol.common.custom.HiveDebugPayload;
import net.minecraft.network.protocol.common.custom.NeighborUpdatesDebugPayload;
import net.minecraft.network.protocol.common.custom.PathfindingDebugPayload;
import net.minecraft.network.protocol.common.custom.PoiAddedDebugPayload;
import net.minecraft.network.protocol.common.custom.PoiRemovedDebugPayload;
import net.minecraft.network.protocol.common.custom.PoiTicketCountDebugPayload;
import net.minecraft.network.protocol.common.custom.RaidsDebugPayload;
import net.minecraft.network.protocol.common.custom.StructuresDebugPayload;
import net.minecraft.network.protocol.common.custom.VillageSectionsDebugPayload;
import net.minecraft.network.protocol.common.custom.WorldGenAttemptDebugPayload;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundCustomChatCompletionsPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundDebugSamplePacket;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundProjectilePowerPacket;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerLinks;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stat;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Crypt;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.SignatureValidator;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.slf4j.Logger;

public class ClientPacketListener extends ClientCommonPacketListenerImpl implements ClientGamePacketListener, TickablePacketListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component UNSECURE_SERVER_TOAST_TITLE = Component.translatable("multiplayer.unsecureserver.toast.title");
    private static final Component UNSERURE_SERVER_TOAST = Component.translatable("multiplayer.unsecureserver.toast");
    private static final Component INVALID_PACKET = Component.translatable("multiplayer.disconnect.invalid_packet");
    private static final Component CHAT_VALIDATION_FAILED_ERROR = Component.translatable("multiplayer.disconnect.chat_validation_failed");
    private static final Component RECONFIGURE_SCREEN_MESSAGE = Component.translatable("connect.reconfiguring");
    private static final int PENDING_OFFSET_THRESHOLD = 64;
    private final GameProfile localGameProfile;
    /**
     * Reference to the current ClientWorld instance, which many handler methods operate on
     */
    private ClientLevel level;
    private ClientLevel.ClientLevelData levelData;
    /**
     * A mapping from player names to their respective GuiPlayerInfo (specifies the clients response time to the server)
     */
    private final Map<UUID, PlayerInfo> playerInfoMap = Maps.newHashMap();
    private final Set<PlayerInfo> listedPlayers = new ReferenceOpenHashSet<>();
    private final ClientAdvancements advancements;
    private final ClientSuggestionProvider suggestionsProvider;
    private final DebugQueryHandler debugQueryHandler = new DebugQueryHandler(this);
    private int serverChunkRadius = 3;
    private int serverSimulationDistance = 3;
    /**
     * Just an ordinary random number generator, used to randomize audio pitch of item/orb pickup and randomize both particlespawn offset and velocity
     */
    private final RandomSource random = RandomSource.createThreadSafe();
    private CommandDispatcher<SharedSuggestionProvider> commands = new CommandDispatcher<>();
    private final RecipeManager recipeManager;
    private final UUID id = UUID.randomUUID();
    private Set<ResourceKey<Level>> levels;
    private final RegistryAccess.Frozen registryAccess;
    private final FeatureFlagSet enabledFeatures;
    private final PotionBrewing potionBrewing;
    @Nullable
    private LocalChatSession chatSession;
    private SignedMessageChain.Encoder signedMessageEncoder = SignedMessageChain.Encoder.UNSIGNED;
    private LastSeenMessagesTracker lastSeenMessages = new LastSeenMessagesTracker(20);
    private MessageSignatureCache messageSignatureCache = MessageSignatureCache.createDefault();
    private final ChunkBatchSizeCalculator chunkBatchSizeCalculator = new ChunkBatchSizeCalculator();
    private final PingDebugMonitor pingDebugMonitor;
    private final DebugSampleSubscriber debugSampleSubscriber;
    @Nullable
    private LevelLoadStatusManager levelLoadStatusManager;
    private boolean serverEnforcesSecureChat;
    private boolean seenInsecureChatWarning = false;
    private volatile boolean closed;
    private final Scoreboard scoreboard = new Scoreboard();
    private final SessionSearchTrees searchTrees = new SessionSearchTrees();

    public ClientPacketListener(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
        super(minecraft, connection, commonListenerCookie);
        this.localGameProfile = commonListenerCookie.localGameProfile();
        this.registryAccess = commonListenerCookie.receivedRegistries();
        this.enabledFeatures = commonListenerCookie.enabledFeatures();
        this.advancements = new ClientAdvancements(minecraft, this.telemetryManager);
        this.suggestionsProvider = new ClientSuggestionProvider(this, minecraft);
        this.pingDebugMonitor = new PingDebugMonitor(this, minecraft.getDebugOverlay().getPingLogger());
        this.recipeManager = new RecipeManager(this.registryAccess);
        this.debugSampleSubscriber = new DebugSampleSubscriber(this, minecraft.getDebugOverlay());
        if (commonListenerCookie.chatState() != null) {
            minecraft.gui.getChat().restoreState(commonListenerCookie.chatState());
        }

        this.potionBrewing = PotionBrewing.bootstrap(this.enabledFeatures);
    }

    public ClientSuggestionProvider getSuggestionsProvider() {
        return this.suggestionsProvider;
    }

    public void close() {
        this.closed = true;
        this.clearLevel();
        this.telemetryManager.onDisconnect();
    }

    public void clearLevel() {
        this.level = null;
        this.levelLoadStatusManager = null;
    }

    public RecipeManager getRecipeManager() {
        return this.recipeManager;
    }

    /**
     * Registers some server properties (gametype, hardcore-mode, terraintype, difficulty, player limit), creates a new WorldClient and sets the player initial dimension.
     */
    @Override
    public void handleLogin(ClientboundLoginPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gameMode = new MultiPlayerGameMode(this.minecraft, this);
        CommonPlayerSpawnInfo commonPlayerSpawnInfo = packet.commonPlayerSpawnInfo();
        List<ResourceKey<Level>> list = Lists.newArrayList(packet.levels());
        Collections.shuffle(list);
        this.levels = Sets.newLinkedHashSet(list);
        ResourceKey<Level> resourceKey = commonPlayerSpawnInfo.dimension();
        Holder<DimensionType> holder = commonPlayerSpawnInfo.dimensionType();
        this.serverChunkRadius = packet.chunkRadius();
        this.serverSimulationDistance = packet.simulationDistance();
        boolean bl = commonPlayerSpawnInfo.isDebug();
        boolean bl2 = commonPlayerSpawnInfo.isFlat();
        ClientLevel.ClientLevelData clientLevelData = new ClientLevel.ClientLevelData(Difficulty.NORMAL, packet.hardcore(), bl2);
        this.levelData = clientLevelData;
        this.level = new ClientLevel(
            this,
            clientLevelData,
            resourceKey,
            holder,
            this.serverChunkRadius,
            this.serverSimulationDistance,
            this.minecraft::getProfiler,
            this.minecraft.levelRenderer,
            bl,
            commonPlayerSpawnInfo.seed()
        );
        this.minecraft.setLevel(this.level, ReceivingLevelScreen.Reason.OTHER);
        if (this.minecraft.player == null) {
            this.minecraft.player = this.minecraft.gameMode.createPlayer(this.level, new StatsCounter(), new ClientRecipeBook());
            this.minecraft.player.setYRot(-180.0F);
            if (this.minecraft.getSingleplayerServer() != null) {
                this.minecraft.getSingleplayerServer().setUUID(this.minecraft.player.getUUID());
            }
        }

        this.minecraft.debugRenderer.clear();
        this.minecraft.player.resetPos();
        this.minecraft.player.setId(packet.playerId());
        this.level.addEntity(this.minecraft.player);
        this.minecraft.player.input = new KeyboardInput(this.minecraft.options);
        this.minecraft.gameMode.adjustPlayer(this.minecraft.player);
        this.minecraft.cameraEntity = this.minecraft.player;
        this.startWaitingForNewLevel(this.minecraft.player, this.level, ReceivingLevelScreen.Reason.OTHER);
        this.minecraft.player.setReducedDebugInfo(packet.reducedDebugInfo());
        this.minecraft.player.setShowDeathScreen(packet.showDeathScreen());
        this.minecraft.player.setDoLimitedCrafting(packet.doLimitedCrafting());
        this.minecraft.player.setLastDeathLocation(commonPlayerSpawnInfo.lastDeathLocation());
        this.minecraft.player.setPortalCooldown(commonPlayerSpawnInfo.portalCooldown());
        this.minecraft.gameMode.setLocalMode(commonPlayerSpawnInfo.gameType(), commonPlayerSpawnInfo.previousGameType());
        this.minecraft.options.setServerRenderDistance(packet.chunkRadius());
        this.chatSession = null;
        this.lastSeenMessages = new LastSeenMessagesTracker(20);
        this.messageSignatureCache = MessageSignatureCache.createDefault();
        if (this.connection.isEncrypted()) {
            this.minecraft.getProfileKeyPairManager().prepareKeyPair().thenAcceptAsync(keyPair -> keyPair.ifPresent(this::setKeyPair), this.minecraft);
        }

        this.telemetryManager.onPlayerInfoReceived(commonPlayerSpawnInfo.gameType(), packet.hardcore());
        this.minecraft.quickPlayLog().log(this.minecraft);
        this.serverEnforcesSecureChat = packet.enforcesSecureChat();
        if (this.serverData != null && !this.seenInsecureChatWarning && !this.enforcesSecureChat()) {
            SystemToast systemToast = SystemToast.multiline(
                this.minecraft, SystemToast.SystemToastId.UNSECURE_SERVER_WARNING, UNSECURE_SERVER_TOAST_TITLE, UNSERURE_SERVER_TOAST
            );
            this.minecraft.getToasts().addToast(systemToast);
            this.seenInsecureChatWarning = true;
        }
    }

    /**
     * Spawns an instance of the objecttype indicated by the packet and sets its position and momentum
     */
    @Override
    public void handleAddEntity(ClientboundAddEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.createEntityFromPacket(packet);
        if (entity != null) {
            entity.recreateFromPacket(packet);
            this.level.addEntity(entity);
            this.postAddEntitySoundInstance(entity);
        } else {
            LOGGER.warn("Skipping Entity with id {}", packet.getType());
        }
    }

    @Nullable
    private Entity createEntityFromPacket(ClientboundAddEntityPacket packet) {
        EntityType<?> entityType = packet.getType();
        if (entityType == EntityType.PLAYER) {
            PlayerInfo playerInfo = this.getPlayerInfo(packet.getUUID());
            if (playerInfo == null) {
                LOGGER.warn("Server attempted to add player prior to sending player info (Player id {})", packet.getUUID());
                return null;
            } else {
                return new RemotePlayer(this.level, playerInfo.getProfile());
            }
        } else {
            return entityType.create(this.level);
        }
    }

    private void postAddEntitySoundInstance(Entity entity) {
        if (entity instanceof AbstractMinecart abstractMinecart) {
            this.minecraft.getSoundManager().play(new MinecartSoundInstance(abstractMinecart));
        } else if (entity instanceof Bee bee) {
            boolean bl = bee.isAngry();
            BeeSoundInstance beeSoundInstance;
            if (bl) {
                beeSoundInstance = new BeeAggressiveSoundInstance(bee);
            } else {
                beeSoundInstance = new BeeFlyingSoundInstance(bee);
            }

            this.minecraft.getSoundManager().queueTickingSound(beeSoundInstance);
        }
    }

    /**
     * Spawns an experience orb and sets its value (amount of XP)
     */
    @Override
    public void handleAddExperienceOrb(ClientboundAddExperienceOrbPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        double d = packet.getX();
        double e = packet.getY();
        double f = packet.getZ();
        Entity entity = new ExperienceOrb(this.level, d, e, f, packet.getValue());
        entity.syncPacketPositionCodec(d, e, f);
        entity.setYRot(0.0F);
        entity.setXRot(0.0F);
        entity.setId(packet.getId());
        this.level.addEntity(entity);
    }

    /**
     * Sets the velocity of the specified entity to the specified value
     */
    @Override
    public void handleSetEntityMotion(ClientboundSetEntityMotionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getId());
        if (entity != null) {
            entity.lerpMotion(packet.getXa(), packet.getYa(), packet.getZa());
        }
    }

    /**
     * Invoked when the server registers new proximate objects in your watchlist or when objects in your watchlist have changed -> Registers any changes locally
     */
    @Override
    public void handleSetEntityData(ClientboundSetEntityDataPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.id());
        if (entity != null) {
            entity.getEntityData().assignValues(packet.packedItems());
        }
    }

    /**
     * Updates an entity's position and rotation as specified by the packet
     */
    @Override
    public void handleTeleportEntity(ClientboundTeleportEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getId());
        if (entity != null) {
            double d = packet.getX();
            double e = packet.getY();
            double f = packet.getZ();
            entity.syncPacketPositionCodec(d, e, f);
            if (!entity.isControlledByLocalInstance()) {
                float g = packet.getyRot() * 360 / 256.0F;
                float h = packet.getxRot() * 360 / 256.0F;
                entity.lerpTo(d, e, f, g, h, 3);
                entity.setOnGround(packet.isOnGround());
            }
        }
    }

    @Override
    public void handleTickingState(ClientboundTickingStatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.minecraft.level != null) {
            TickRateManager tickRateManager = this.minecraft.level.tickRateManager();
            tickRateManager.setTickRate(packet.tickRate());
            tickRateManager.setFrozen(packet.isFrozen());
        }
    }

    @Override
    public void handleTickingStep(ClientboundTickingStepPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.minecraft.level != null) {
            TickRateManager tickRateManager = this.minecraft.level.tickRateManager();
            tickRateManager.setFrozenTicksToRun(packet.tickSteps());
        }
    }

    /**
     * Updates which hotbar slot of the player is currently selected
     */
    @Override
    public void handleSetCarriedItem(ClientboundSetCarriedItemPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (Inventory.isHotbarSlot(packet.getSlot())) {
            this.minecraft.player.getInventory().selected = packet.getSlot();
        }
    }

    /**
     * Updates the specified entity's position by the specified relative momentum and absolute rotation. Note that subclassing of the packet allows for the specification of a subset of this data (e.g. only rel. position, abs. rotation or both).
     */
    @Override
    public void handleMoveEntity(ClientboundMoveEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getEntity(this.level);
        if (entity != null) {
            if (!entity.isControlledByLocalInstance()) {
                if (packet.hasPosition()) {
                    VecDeltaCodec vecDeltaCodec = entity.getPositionCodec();
                    Vec3 vec3 = vecDeltaCodec.decode(packet.getXa(), packet.getYa(), packet.getZa());
                    vecDeltaCodec.setBase(vec3);
                    float f = packet.hasRotation() ? packet.getyRot() * 360 / 256.0F : entity.lerpTargetYRot();
                    float g = packet.hasRotation() ? packet.getxRot() * 360 / 256.0F : entity.lerpTargetXRot();
                    entity.lerpTo(vec3.x(), vec3.y(), vec3.z(), f, g, 3);
                } else if (packet.hasRotation()) {
                    float h = packet.getyRot() * 360 / 256.0F;
                    float i = packet.getxRot() * 360 / 256.0F;
                    entity.lerpTo(entity.lerpTargetX(), entity.lerpTargetY(), entity.lerpTargetZ(), h, i, 3);
                }

                entity.setOnGround(packet.isOnGround());
            }
        }
    }

    /**
     * Updates the direction in which the specified entity is looking, normally this head rotation is independent of the rotation of the entity itself
     */
    @Override
    public void handleRotateMob(ClientboundRotateHeadPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getEntity(this.level);
        if (entity != null) {
            float f = packet.getYHeadRot() * 360 / 256.0F;
            entity.lerpHeadTo(f, 3);
        }
    }

    @Override
    public void handleRemoveEntities(ClientboundRemoveEntitiesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        packet.getEntityIds().forEach(entityId -> this.level.removeEntity(entityId, Entity.RemovalReason.DISCARDED));
    }

    @Override
    public void handleMovePlayer(ClientboundPlayerPositionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Player player = this.minecraft.player;
        Vec3 vec3 = player.getDeltaMovement();
        boolean bl = packet.getRelativeArguments().contains(RelativeMovement.X);
        boolean bl2 = packet.getRelativeArguments().contains(RelativeMovement.Y);
        boolean bl3 = packet.getRelativeArguments().contains(RelativeMovement.Z);
        double d;
        double e;
        if (bl) {
            d = vec3.x();
            e = player.getX() + packet.getX();
            player.xOld = player.xOld + packet.getX();
            player.xo = player.xo + packet.getX();
        } else {
            d = 0.0;
            e = packet.getX();
            player.xOld = e;
            player.xo = e;
        }

        double f;
        double g;
        if (bl2) {
            f = vec3.y();
            g = player.getY() + packet.getY();
            player.yOld = player.yOld + packet.getY();
            player.yo = player.yo + packet.getY();
        } else {
            f = 0.0;
            g = packet.getY();
            player.yOld = g;
            player.yo = g;
        }

        double h;
        double i;
        if (bl3) {
            h = vec3.z();
            i = player.getZ() + packet.getZ();
            player.zOld = player.zOld + packet.getZ();
            player.zo = player.zo + packet.getZ();
        } else {
            h = 0.0;
            i = packet.getZ();
            player.zOld = i;
            player.zo = i;
        }

        player.setPos(e, g, i);
        player.setDeltaMovement(d, f, h);
        float j = packet.getYRot();
        float k = packet.getXRot();
        if (packet.getRelativeArguments().contains(RelativeMovement.X_ROT)) {
            player.setXRot(player.getXRot() + k);
            player.xRotO += k;
        } else {
            player.setXRot(k);
            player.xRotO = k;
        }

        if (packet.getRelativeArguments().contains(RelativeMovement.Y_ROT)) {
            player.setYRot(player.getYRot() + j);
            player.yRotO += j;
        } else {
            player.setYRot(j);
            player.yRotO = j;
        }

        this.connection.send(new ServerboundAcceptTeleportationPacket(packet.getId()));
        this.connection.send(new ServerboundMovePlayerPacket.PosRot(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(), false));
    }

    /**
     * Received from the servers PlayerManager if between 1 and 64 blocks in a chunk are changed. If only one block requires an update, the server sends S23PacketBlockChange and if 64 or more blocks are changed, the server sends S21PacketChunkData
     */
    @Override
    public void handleChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        packet.runUpdates((blockPos, blockState) -> this.level.setServerVerifiedBlockState(blockPos, blockState, 19));
    }

    @Override
    public void handleLevelChunkWithLight(ClientboundLevelChunkWithLightPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        int i = packet.getX();
        int j = packet.getZ();
        this.updateLevelChunk(i, j, packet.getChunkData());
        ClientboundLightUpdatePacketData clientboundLightUpdatePacketData = packet.getLightData();
        this.level.queueLightUpdate(() -> {
            this.applyLightData(i, j, clientboundLightUpdatePacketData);
            LevelChunk levelChunk = this.level.getChunkSource().getChunk(i, j, false);
            if (levelChunk != null) {
                this.enableChunkLight(levelChunk, i, j);
            }
        });
    }

    @Override
    public void handleChunksBiomes(ClientboundChunksBiomesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);

        for (ClientboundChunksBiomesPacket.ChunkBiomeData chunkBiomeData : packet.chunkBiomeData()) {
            this.level.getChunkSource().replaceBiomes(chunkBiomeData.pos().x, chunkBiomeData.pos().z, chunkBiomeData.getReadBuffer());
        }

        for (ClientboundChunksBiomesPacket.ChunkBiomeData chunkBiomeData : packet.chunkBiomeData()) {
            this.level.onChunkLoaded(new ChunkPos(chunkBiomeData.pos().x, chunkBiomeData.pos().z));
        }

        for (ClientboundChunksBiomesPacket.ChunkBiomeData chunkBiomeData : packet.chunkBiomeData()) {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    for (int k = this.level.getMinSection(); k < this.level.getMaxSection(); k++) {
                        this.minecraft.levelRenderer.setSectionDirty(chunkBiomeData.pos().x + i, k, chunkBiomeData.pos().z + j);
                    }
                }
            }
        }
    }

    private void updateLevelChunk(int x, int z, ClientboundLevelChunkPacketData data) {
        this.level.getChunkSource().replaceWithPacketData(x, z, data.getReadBuffer(), data.getHeightmaps(), data.getBlockEntitiesTagsConsumer(x, z));
    }

    private void enableChunkLight(LevelChunk chunk, int x, int z) {
        LevelLightEngine levelLightEngine = this.level.getChunkSource().getLightEngine();
        LevelChunkSection[] levelChunkSections = chunk.getSections();
        ChunkPos chunkPos = chunk.getPos();

        for (int i = 0; i < levelChunkSections.length; i++) {
            LevelChunkSection levelChunkSection = levelChunkSections[i];
            int j = this.level.getSectionYFromSectionIndex(i);
            levelLightEngine.updateSectionStatus(SectionPos.of(chunkPos, j), levelChunkSection.hasOnlyAir());
            this.level.setSectionDirtyWithNeighbors(x, j, z);
        }
    }

    @Override
    public void handleForgetLevelChunk(ClientboundForgetLevelChunkPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getChunkSource().drop(packet.pos());
        this.queueLightRemoval(packet);
    }

    private void queueLightRemoval(ClientboundForgetLevelChunkPacket packet) {
        ChunkPos chunkPos = packet.pos();
        this.level.queueLightUpdate(() -> {
            LevelLightEngine levelLightEngine = this.level.getLightEngine();
            levelLightEngine.setLightEnabled(chunkPos, false);

            for (int i = levelLightEngine.getMinLightSection(); i < levelLightEngine.getMaxLightSection(); i++) {
                SectionPos sectionPos = SectionPos.of(chunkPos, i);
                levelLightEngine.queueSectionData(LightLayer.BLOCK, sectionPos, null);
                levelLightEngine.queueSectionData(LightLayer.SKY, sectionPos, null);
            }

            for (int i = this.level.getMinSection(); i < this.level.getMaxSection(); i++) {
                levelLightEngine.updateSectionStatus(SectionPos.of(chunkPos, i), true);
            }
        });
    }

    /**
     * Updates the block and metadata and generates a blockupdate (and notify the clients)
     */
    @Override
    public void handleBlockUpdate(ClientboundBlockUpdatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.setServerVerifiedBlockState(packet.getPos(), packet.getBlockState(), 19);
    }

    @Override
    public void handleConfigurationStart(ClientboundStartConfigurationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.getChatListener().clearQueue();
        this.sendChatAcknowledgement();
        ChatComponent.State state = this.minecraft.gui.getChat().storeState();
        this.minecraft.clearClientLevel(new ServerReconfigScreen(RECONFIGURE_SCREEN_MESSAGE, this.connection));
        this.connection
            .setupInboundProtocol(
                ConfigurationProtocols.CLIENTBOUND,
                new ClientConfigurationPacketListenerImpl(
                    this.minecraft,
                    this.connection,
                    new CommonListenerCookie(
                        this.localGameProfile,
                        this.telemetryManager,
                        this.registryAccess,
                        this.enabledFeatures,
                        this.serverBrand,
                        this.serverData,
                        this.postDisconnectScreen,
                        this.serverCookies,
                        state,
                        this.strictErrorHandling,
                        this.customReportDetails,
                        this.serverLinks
                    )
                )
            );
        this.send(ServerboundConfigurationAcknowledgedPacket.INSTANCE);
        this.connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
    }

    @Override
    public void handleTakeItemEntity(ClientboundTakeItemEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getItemId());
        LivingEntity livingEntity = (LivingEntity)this.level.getEntity(packet.getPlayerId());
        if (livingEntity == null) {
            livingEntity = this.minecraft.player;
        }

        if (entity != null) {
            if (entity instanceof ExperienceOrb) {
                this.level
                    .playLocalSound(
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP,
                        SoundSource.PLAYERS,
                        0.1F,
                        (this.random.nextFloat() - this.random.nextFloat()) * 0.35F + 0.9F,
                        false
                    );
            } else {
                this.level
                    .playLocalSound(
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        SoundEvents.ITEM_PICKUP,
                        SoundSource.PLAYERS,
                        0.2F,
                        (this.random.nextFloat() - this.random.nextFloat()) * 1.4F + 2.0F,
                        false
                    );
            }

            this.minecraft
                .particleEngine
                .add(new ItemPickupParticle(this.minecraft.getEntityRenderDispatcher(), this.minecraft.renderBuffers(), this.level, entity, livingEntity));
            if (entity instanceof ItemEntity itemEntity) {
                ItemStack itemStack = itemEntity.getItem();
                if (!itemStack.isEmpty()) {
                    itemStack.shrink(packet.getAmount());
                }

                if (itemStack.isEmpty()) {
                    this.level.removeEntity(packet.getItemId(), Entity.RemovalReason.DISCARDED);
                }
            } else if (!(entity instanceof ExperienceOrb)) {
                this.level.removeEntity(packet.getItemId(), Entity.RemovalReason.DISCARDED);
            }
        }
    }

    @Override
    public void handleSystemChat(ClientboundSystemChatPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.getChatListener().handleSystemMessage(packet.content(), packet.overlay());
    }

    @Override
    public void handlePlayerChat(ClientboundPlayerChatPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Optional<SignedMessageBody> optional = packet.body().unpack(this.messageSignatureCache);
        if (optional.isEmpty()) {
            this.connection.disconnect(INVALID_PACKET);
        } else {
            this.messageSignatureCache.push(optional.get(), packet.signature());
            UUID uUID = packet.sender();
            PlayerInfo playerInfo = this.getPlayerInfo(uUID);
            if (playerInfo == null) {
                LOGGER.error("Received player chat packet for unknown player with ID: {}", uUID);
                this.minecraft.getChatListener().handleChatMessageError(uUID, packet.chatType());
            } else {
                RemoteChatSession remoteChatSession = playerInfo.getChatSession();
                SignedMessageLink signedMessageLink;
                if (remoteChatSession != null) {
                    signedMessageLink = new SignedMessageLink(packet.index(), uUID, remoteChatSession.sessionId());
                } else {
                    signedMessageLink = SignedMessageLink.unsigned(uUID);
                }

                PlayerChatMessage playerChatMessage = new PlayerChatMessage(
                    signedMessageLink, packet.signature(), optional.get(), packet.unsignedContent(), packet.filterMask()
                );
                playerChatMessage = playerInfo.getMessageValidator().updateAndValidate(playerChatMessage);
                if (playerChatMessage != null) {
                    this.minecraft.getChatListener().handlePlayerChatMessage(playerChatMessage, playerInfo.getProfile(), packet.chatType());
                } else {
                    this.minecraft.getChatListener().handleChatMessageError(uUID, packet.chatType());
                }
            }
        }
    }

    @Override
    public void handleDisguisedChat(ClientboundDisguisedChatPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.getChatListener().handleDisguisedChatMessage(packet.message(), packet.chatType());
    }

    @Override
    public void handleDeleteChat(ClientboundDeleteChatPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Optional<MessageSignature> optional = packet.messageSignature().unpack(this.messageSignatureCache);
        if (optional.isEmpty()) {
            this.connection.disconnect(INVALID_PACKET);
        } else {
            this.lastSeenMessages.ignorePending(optional.get());
            if (!this.minecraft.getChatListener().removeFromDelayedMessageQueue(optional.get())) {
                this.minecraft.gui.getChat().deleteMessage(optional.get());
            }
        }
    }

    /**
     * Renders a specified animation: Waking up a player, a living entity swinging its currently held item, being hurt or receiving a critical hit by normal or magical means
     */
    @Override
    public void handleAnimate(ClientboundAnimatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getId());
        if (entity != null) {
            if (packet.getAction() == 0) {
                LivingEntity livingEntity = (LivingEntity)entity;
                livingEntity.swing(InteractionHand.MAIN_HAND);
            } else if (packet.getAction() == 3) {
                LivingEntity livingEntity = (LivingEntity)entity;
                livingEntity.swing(InteractionHand.OFF_HAND);
            } else if (packet.getAction() == 2) {
                Player player = (Player)entity;
                player.stopSleepInBed(false, false);
            } else if (packet.getAction() == 4) {
                this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.CRIT);
            } else if (packet.getAction() == 5) {
                this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.ENCHANTED_HIT);
            }
        }
    }

    @Override
    public void handleHurtAnimation(ClientboundHurtAnimationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.id());
        if (entity != null) {
            entity.animateHurt(packet.yaw());
        }
    }

    @Override
    public void handleSetTime(ClientboundSetTimePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.level.setGameTime(packet.getGameTime());
        this.minecraft.level.setDayTime(packet.getDayTime());
        this.telemetryManager.setTime(packet.getGameTime());
    }

    @Override
    public void handleSetSpawn(ClientboundSetDefaultSpawnPositionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.level.setDefaultSpawnPos(packet.getPos(), packet.getAngle());
    }

    @Override
    public void handleSetEntityPassengersPacket(ClientboundSetPassengersPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getVehicle());
        if (entity == null) {
            LOGGER.warn("Received passengers for unknown entity");
        } else {
            boolean bl = entity.hasIndirectPassenger(this.minecraft.player);
            entity.ejectPassengers();

            for (int i : packet.getPassengers()) {
                Entity entity2 = this.level.getEntity(i);
                if (entity2 != null) {
                    entity2.startRiding(entity, true);
                    if (entity2 == this.minecraft.player && !bl) {
                        if (entity instanceof Boat) {
                            this.minecraft.player.yRotO = entity.getYRot();
                            this.minecraft.player.setYRot(entity.getYRot());
                            this.minecraft.player.setYHeadRot(entity.getYRot());
                        }

                        Component component = Component.translatable("mount.onboard", this.minecraft.options.keyShift.getTranslatedKeyMessage());
                        this.minecraft.gui.setOverlayMessage(component, false);
                        this.minecraft.getNarrator().sayNow(component);
                    }
                }
            }
        }
    }

    @Override
    public void handleEntityLinkPacket(ClientboundSetEntityLinkPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.level.getEntity(packet.getSourceId()) instanceof Leashable leashable) {
            leashable.setDelayedLeashHolderId(packet.getDestId());
        }
    }

    private static ItemStack findTotem(Player player) {
        for (InteractionHand interactionHand : InteractionHand.values()) {
            ItemStack itemStack = player.getItemInHand(interactionHand);
            if (itemStack.is(Items.TOTEM_OF_UNDYING)) {
                return itemStack;
            }
        }

        return new ItemStack(Items.TOTEM_OF_UNDYING);
    }

    /**
     * Invokes the entities' handleUpdateHealth method which is implemented in LivingBase (hurt/death), MinecartMobSpawner (spawn delay), FireworkRocket & MinecartTNT (explosion), IronGolem (throwing, ...), Witch (spawn particles), Zombie (villager transformation), Animal (breeding mode particles), Horse (breeding/smoke particles), Sheep (...), Tameable (...), Villager (particles for breeding mode, angry and happy), Wolf (...)
     */
    @Override
    public void handleEntityEvent(ClientboundEntityEventPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getEntity(this.level);
        if (entity != null) {
            switch (packet.getEventId()) {
                case 21:
                    this.minecraft.getSoundManager().play(new GuardianAttackSoundInstance((Guardian)entity));
                    break;
                case 35:
                    int i = 40;
                    this.minecraft.particleEngine.createTrackingEmitter(entity, ParticleTypes.TOTEM_OF_UNDYING, 30);
                    this.level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TOTEM_USE, entity.getSoundSource(), 1.0F, 1.0F, false);
                    if (entity == this.minecraft.player) {
                        this.minecraft.gameRenderer.displayItemActivation(findTotem(this.minecraft.player));
                    }
                    break;
                case 63:
                    this.minecraft.getSoundManager().play(new SnifferSoundInstance((Sniffer)entity));
                    break;
                default:
                    entity.handleEntityEvent(packet.getEventId());
            }
        }
    }

    @Override
    public void handleDamageEvent(ClientboundDamageEventPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.entityId());
        if (entity != null) {
            entity.handleDamageEvent(packet.getSource(this.level));
        }
    }

    @Override
    public void handleSetHealth(ClientboundSetHealthPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.player.hurtTo(packet.getHealth());
        this.minecraft.player.getFoodData().setFoodLevel(packet.getFood());
        this.minecraft.player.getFoodData().setSaturation(packet.getSaturation());
    }

    @Override
    public void handleSetExperience(ClientboundSetExperiencePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.player.setExperienceValues(packet.getExperienceProgress(), packet.getTotalExperience(), packet.getExperienceLevel());
    }

    @Override
    public void handleRespawn(ClientboundRespawnPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        CommonPlayerSpawnInfo commonPlayerSpawnInfo = packet.commonPlayerSpawnInfo();
        ResourceKey<Level> resourceKey = commonPlayerSpawnInfo.dimension();
        Holder<DimensionType> holder = commonPlayerSpawnInfo.dimensionType();
        LocalPlayer localPlayer = this.minecraft.player;
        ResourceKey<Level> resourceKey2 = localPlayer.level().dimension();
        boolean bl = resourceKey != resourceKey2;
        ReceivingLevelScreen.Reason reason = this.determineLevelLoadingReason(localPlayer.isDeadOrDying(), resourceKey, resourceKey2);
        if (bl) {
            Map<MapId, MapItemSavedData> map = this.level.getAllMapData();
            boolean bl2 = commonPlayerSpawnInfo.isDebug();
            boolean bl3 = commonPlayerSpawnInfo.isFlat();
            ClientLevel.ClientLevelData clientLevelData = new ClientLevel.ClientLevelData(this.levelData.getDifficulty(), this.levelData.isHardcore(), bl3);
            this.levelData = clientLevelData;
            this.level = new ClientLevel(
                this,
                clientLevelData,
                resourceKey,
                holder,
                this.serverChunkRadius,
                this.serverSimulationDistance,
                this.minecraft::getProfiler,
                this.minecraft.levelRenderer,
                bl2,
                commonPlayerSpawnInfo.seed()
            );
            this.level.addMapData(map);
            this.minecraft.setLevel(this.level, reason);
        }

        this.minecraft.cameraEntity = null;
        if (localPlayer.hasContainerOpen()) {
            localPlayer.closeContainer();
        }

        LocalPlayer localPlayer2;
        if (packet.shouldKeep((byte)2)) {
            localPlayer2 = this.minecraft
                .gameMode
                .createPlayer(this.level, localPlayer.getStats(), localPlayer.getRecipeBook(), localPlayer.isShiftKeyDown(), localPlayer.isSprinting());
        } else {
            localPlayer2 = this.minecraft.gameMode.createPlayer(this.level, localPlayer.getStats(), localPlayer.getRecipeBook());
        }

        this.startWaitingForNewLevel(localPlayer2, this.level, reason);
        localPlayer2.setId(localPlayer.getId());
        this.minecraft.player = localPlayer2;
        if (bl) {
            this.minecraft.getMusicManager().stopPlaying();
        }

        this.minecraft.cameraEntity = localPlayer2;
        if (packet.shouldKeep((byte)2)) {
            List<SynchedEntityData.DataValue<?>> list = localPlayer.getEntityData().getNonDefaultValues();
            if (list != null) {
                localPlayer2.getEntityData().assignValues(list);
            }
        }

        if (packet.shouldKeep((byte)1)) {
            localPlayer2.getAttributes().assignAllValues(localPlayer.getAttributes());
        } else {
            localPlayer2.getAttributes().assignBaseValues(localPlayer.getAttributes());
        }

        localPlayer2.resetPos();
        this.level.addEntity(localPlayer2);
        localPlayer2.setYRot(-180.0F);
        localPlayer2.input = new KeyboardInput(this.minecraft.options);
        this.minecraft.gameMode.adjustPlayer(localPlayer2);
        localPlayer2.setReducedDebugInfo(localPlayer.isReducedDebugInfo());
        localPlayer2.setShowDeathScreen(localPlayer.shouldShowDeathScreen());
        localPlayer2.setLastDeathLocation(commonPlayerSpawnInfo.lastDeathLocation());
        localPlayer2.setPortalCooldown(commonPlayerSpawnInfo.portalCooldown());
        localPlayer2.spinningEffectIntensity = localPlayer.spinningEffectIntensity;
        localPlayer2.oSpinningEffectIntensity = localPlayer.oSpinningEffectIntensity;
        if (this.minecraft.screen instanceof DeathScreen || this.minecraft.screen instanceof DeathScreen.TitleConfirmScreen) {
            this.minecraft.setScreen(null);
        }

        this.minecraft.gameMode.setLocalMode(commonPlayerSpawnInfo.gameType(), commonPlayerSpawnInfo.previousGameType());
    }

    private ReceivingLevelScreen.Reason determineLevelLoadingReason(boolean dying, ResourceKey<Level> spawnDimension, ResourceKey<Level> currentDimension) {
        ReceivingLevelScreen.Reason reason = ReceivingLevelScreen.Reason.OTHER;
        if (!dying) {
            if (spawnDimension == Level.NETHER || currentDimension == Level.NETHER) {
                reason = ReceivingLevelScreen.Reason.NETHER_PORTAL;
            } else if (spawnDimension == Level.END || currentDimension == Level.END) {
                reason = ReceivingLevelScreen.Reason.END_PORTAL;
            }
        }

        return reason;
    }

    /**
     * Initiates a new explosion (sound, particles, drop spawn) for the affected blocks indicated by the packet.
     */
    @Override
    public void handleExplosion(ClientboundExplodePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Explosion explosion = new Explosion(
            this.minecraft.level,
            null,
            packet.getX(),
            packet.getY(),
            packet.getZ(),
            packet.getPower(),
            packet.getToBlow(),
            packet.getBlockInteraction(),
            packet.getSmallExplosionParticles(),
            packet.getLargeExplosionParticles(),
            packet.getExplosionSound()
        );
        explosion.finalizeExplosion(true);
        this.minecraft
            .player
            .setDeltaMovement(this.minecraft.player.getDeltaMovement().add(packet.getKnockbackX(), packet.getKnockbackY(), packet.getKnockbackZ()));
    }

    @Override
    public void handleHorseScreenOpen(ClientboundHorseScreenOpenPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.level.getEntity(packet.getEntityId()) instanceof AbstractHorse abstractHorse) {
            LocalPlayer localPlayer = this.minecraft.player;
            int i = packet.getInventoryColumns();
            SimpleContainer simpleContainer = new SimpleContainer(AbstractHorse.getInventorySize(i));
            HorseInventoryMenu horseInventoryMenu = new HorseInventoryMenu(
                packet.getContainerId(), localPlayer.getInventory(), simpleContainer, abstractHorse, i
            );
            localPlayer.containerMenu = horseInventoryMenu;
            this.minecraft.setScreen(new HorseInventoryScreen(horseInventoryMenu, localPlayer.getInventory(), abstractHorse, i));
        }
    }

    @Override
    public void handleOpenScreen(ClientboundOpenScreenPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        MenuScreens.create(packet.getType(), this.minecraft, packet.getContainerId(), packet.getTitle());
    }

    /**
     * Handles picking up an ItemStack or dropping one in your inventory or an open (non-creative) container
     */
    @Override
    public void handleContainerSetSlot(ClientboundContainerSetSlotPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Player player = this.minecraft.player;
        ItemStack itemStack = packet.getItem();
        int i = packet.getSlot();
        this.minecraft.getTutorial().onGetItem(itemStack);
        if (packet.getContainerId() == -1) {
            if (!(this.minecraft.screen instanceof CreativeModeInventoryScreen)) {
                player.containerMenu.setCarried(itemStack);
            }
        } else if (packet.getContainerId() == -2) {
            player.getInventory().setItem(i, itemStack);
        } else {
            boolean bl = false;
            if (this.minecraft.screen instanceof CreativeModeInventoryScreen creativeModeInventoryScreen) {
                bl = !creativeModeInventoryScreen.isInventoryOpen();
            }

            if (packet.getContainerId() == 0 && InventoryMenu.isHotbarSlot(i)) {
                if (!itemStack.isEmpty()) {
                    ItemStack itemStack2 = player.inventoryMenu.getSlot(i).getItem();
                    if (itemStack2.isEmpty() || itemStack2.getCount() < itemStack.getCount()) {
                        itemStack.setPopTime(5);
                    }
                }

                player.inventoryMenu.setItem(i, packet.getStateId(), itemStack);
            } else if (packet.getContainerId() == player.containerMenu.containerId && (packet.getContainerId() != 0 || !bl)) {
                player.containerMenu.setItem(i, packet.getStateId(), itemStack);
            }
        }
    }

    /**
     * Handles the placement of a specified ItemStack in a specified container/inventory slot
     */
    @Override
    public void handleContainerContent(ClientboundContainerSetContentPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Player player = this.minecraft.player;
        if (packet.getContainerId() == 0) {
            player.inventoryMenu.initializeContents(packet.getStateId(), packet.getItems(), packet.getCarriedItem());
        } else if (packet.getContainerId() == player.containerMenu.containerId) {
            player.containerMenu.initializeContents(packet.getStateId(), packet.getItems(), packet.getCarriedItem());
        }
    }

    /**
     * Creates a sign in the specified location if it didn't exist and opens the GUI to edit its text
     */
    @Override
    public void handleOpenSignEditor(ClientboundOpenSignEditorPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        BlockPos blockPos = packet.getPos();
        if (this.level.getBlockEntity(blockPos) instanceof SignBlockEntity signBlockEntity) {
            this.minecraft.player.openTextEdit(signBlockEntity, packet.isFrontText());
        } else {
            BlockState blockState = this.level.getBlockState(blockPos);
            SignBlockEntity signBlockEntity2 = new SignBlockEntity(blockPos, blockState);
            signBlockEntity2.setLevel(this.level);
            this.minecraft.player.openTextEdit(signBlockEntity2, packet.isFrontText());
        }
    }

    /**
     * Updates the NBTTagCompound metadata of instances of the following entitytypes: Mob spawners, command blocks, beacons, skulls, flowerpot
     */
    @Override
    public void handleBlockEntityData(ClientboundBlockEntityDataPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        BlockPos blockPos = packet.getPos();
        this.minecraft.level.getBlockEntity(blockPos, packet.getType()).ifPresent(blockEntity -> {
            CompoundTag compoundTag = packet.getTag();
            if (!compoundTag.isEmpty()) {
                blockEntity.loadWithComponents(compoundTag, this.registryAccess);
            }

            if (blockEntity instanceof CommandBlockEntity && this.minecraft.screen instanceof CommandBlockEditScreen) {
                ((CommandBlockEditScreen)this.minecraft.screen).updateGui();
            }
        });
    }

    /**
     * Sets the progressbar of the opened window to the specified value
     */
    @Override
    public void handleContainerSetData(ClientboundContainerSetDataPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Player player = this.minecraft.player;
        if (player.containerMenu != null && player.containerMenu.containerId == packet.getContainerId()) {
            player.containerMenu.setData(packet.getId(), packet.getValue());
        }
    }

    @Override
    public void handleSetEquipment(ClientboundSetEquipmentPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.level.getEntity(packet.getEntity()) instanceof LivingEntity livingEntity) {
            packet.getSlots().forEach(slot -> livingEntity.setItemSlot(slot.getFirst(), slot.getSecond()));
        }
    }

    /**
     * Resets the ItemStack held in hand and closes the window that is opened
     */
    @Override
    public void handleContainerClose(ClientboundContainerClosePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.player.clientSideCloseContainer();
    }

    /**
     * Triggers Block.onBlockEventReceived, which is implemented in BlockPistonBase for extension/retraction, BlockNote for setting the instrument (including audiovisual feedback) and in BlockContainer to set the number of players accessing a (Ender)Chest
     */
    @Override
    public void handleBlockEvent(ClientboundBlockEventPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.level.blockEvent(packet.getPos(), packet.getBlock(), packet.getB0(), packet.getB1());
    }

    /**
     * Updates all registered IWorldAccess instances with destroyBlockInWorldPartially
     */
    @Override
    public void handleBlockDestruction(ClientboundBlockDestructionPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.level.destroyBlockProgress(packet.getId(), packet.getPos(), packet.getProgress());
    }

    @Override
    public void handleGameEvent(ClientboundGameEventPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Player player = this.minecraft.player;
        ClientboundGameEventPacket.Type type = packet.getEvent();
        float f = packet.getParam();
        int i = Mth.floor(f + 0.5F);
        if (type == ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE) {
            player.displayClientMessage(Component.translatable("block.minecraft.spawn.not_valid"), false);
        } else if (type == ClientboundGameEventPacket.START_RAINING) {
            this.level.getLevelData().setRaining(true);
            this.level.setRainLevel(0.0F);
        } else if (type == ClientboundGameEventPacket.STOP_RAINING) {
            this.level.getLevelData().setRaining(false);
            this.level.setRainLevel(1.0F);
        } else if (type == ClientboundGameEventPacket.CHANGE_GAME_MODE) {
            this.minecraft.gameMode.setLocalMode(GameType.byId(i));
        } else if (type == ClientboundGameEventPacket.WIN_GAME) {
            this.minecraft.setScreen(new WinScreen(true, () -> {
                this.minecraft.player.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                this.minecraft.setScreen(null);
            }));
        } else if (type == ClientboundGameEventPacket.DEMO_EVENT) {
            Options options = this.minecraft.options;
            if (f == 0.0F) {
                this.minecraft.setScreen(new DemoIntroScreen());
            } else if (f == 101.0F) {
                this.minecraft
                    .gui
                    .getChat()
                    .addMessage(
                        Component.translatable(
                            "demo.help.movement",
                            options.keyUp.getTranslatedKeyMessage(),
                            options.keyLeft.getTranslatedKeyMessage(),
                            options.keyDown.getTranslatedKeyMessage(),
                            options.keyRight.getTranslatedKeyMessage()
                        )
                    );
            } else if (f == 102.0F) {
                this.minecraft.gui.getChat().addMessage(Component.translatable("demo.help.jump", options.keyJump.getTranslatedKeyMessage()));
            } else if (f == 103.0F) {
                this.minecraft.gui.getChat().addMessage(Component.translatable("demo.help.inventory", options.keyInventory.getTranslatedKeyMessage()));
            } else if (f == 104.0F) {
                this.minecraft.gui.getChat().addMessage(Component.translatable("demo.day.6", options.keyScreenshot.getTranslatedKeyMessage()));
            }
        } else if (type == ClientboundGameEventPacket.ARROW_HIT_PLAYER) {
            this.level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.18F, 0.45F);
        } else if (type == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE) {
            this.level.setRainLevel(f);
        } else if (type == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE) {
            this.level.setThunderLevel(f);
        } else if (type == ClientboundGameEventPacket.PUFFER_FISH_STING) {
            this.level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.PUFFER_FISH_STING, SoundSource.NEUTRAL, 1.0F, 1.0F);
        } else if (type == ClientboundGameEventPacket.GUARDIAN_ELDER_EFFECT) {
            this.level.addParticle(ParticleTypes.ELDER_GUARDIAN, player.getX(), player.getY(), player.getZ(), 0.0, 0.0, 0.0);
            if (i == 1) {
                this.level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0F, 1.0F);
            }
        } else if (type == ClientboundGameEventPacket.IMMEDIATE_RESPAWN) {
            this.minecraft.player.setShowDeathScreen(f == 0.0F);
        } else if (type == ClientboundGameEventPacket.LIMITED_CRAFTING) {
            this.minecraft.player.setDoLimitedCrafting(f == 1.0F);
        } else if (type == ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START && this.levelLoadStatusManager != null) {
            this.levelLoadStatusManager.loadingPacketsReceived();
        }
    }

    private void startWaitingForNewLevel(LocalPlayer player, ClientLevel level, ReceivingLevelScreen.Reason reason) {
        this.levelLoadStatusManager = new LevelLoadStatusManager(player, level, this.minecraft.levelRenderer);
        this.minecraft.setScreen(new ReceivingLevelScreen(this.levelLoadStatusManager::levelReady, reason));
    }

    /**
     * Updates the worlds MapStorage with the specified MapData for the specified map-identifier and invokes a MapItemRenderer for it
     */
    @Override
    public void handleMapItemData(ClientboundMapItemDataPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        MapRenderer mapRenderer = this.minecraft.gameRenderer.getMapRenderer();
        MapId mapId = packet.mapId();
        MapItemSavedData mapItemSavedData = this.minecraft.level.getMapData(mapId);
        if (mapItemSavedData == null) {
            mapItemSavedData = MapItemSavedData.createForClient(packet.scale(), packet.locked(), this.minecraft.level.dimension());
            this.minecraft.level.overrideMapData(mapId, mapItemSavedData);
        }

        packet.applyToMap(mapItemSavedData);
        mapRenderer.update(mapId, mapItemSavedData);
    }

    @Override
    public void handleLevelEvent(ClientboundLevelEventPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (packet.isGlobalEvent()) {
            this.minecraft.level.globalLevelEvent(packet.getType(), packet.getPos(), packet.getData());
        } else {
            this.minecraft.level.levelEvent(packet.getType(), packet.getPos(), packet.getData());
        }
    }

    @Override
    public void handleUpdateAdvancementsPacket(ClientboundUpdateAdvancementsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.advancements.update(packet);
    }

    @Override
    public void handleSelectAdvancementsTab(ClientboundSelectAdvancementsTabPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        ResourceLocation resourceLocation = packet.getTab();
        if (resourceLocation == null) {
            this.advancements.setSelectedTab(null, false);
        } else {
            AdvancementHolder advancementHolder = this.advancements.get(resourceLocation);
            this.advancements.setSelectedTab(advancementHolder, false);
        }
    }

    @Override
    public void handleCommands(ClientboundCommandsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.commands = new CommandDispatcher<>(packet.getRoot(CommandBuildContext.simple(this.registryAccess, this.enabledFeatures)));
    }

    @Override
    public void handleStopSoundEvent(ClientboundStopSoundPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.getSoundManager().stop(packet.getName(), packet.getSource());
    }

    /**
     * This method is only called for manual tab-completion (the {@link net.minecraft.commands.synchronization.SuggestionProviders#ASK_SERVER minecraft:ask_server} suggestion provider).
     */
    @Override
    public void handleCommandSuggestions(ClientboundCommandSuggestionsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.suggestionsProvider.completeCustomSuggestions(packet.id(), packet.toSuggestions());
    }

    @Override
    public void handleUpdateRecipes(ClientboundUpdateRecipesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.recipeManager.replaceRecipes(packet.getRecipes());
        ClientRecipeBook clientRecipeBook = this.minecraft.player.getRecipeBook();
        clientRecipeBook.setupCollections(this.recipeManager.getOrderedRecipes(), this.minecraft.level.registryAccess());
        this.searchTrees.updateRecipes(clientRecipeBook, this.registryAccess);
    }

    @Override
    public void handleLookAt(ClientboundPlayerLookAtPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Vec3 vec3 = packet.getPosition(this.level);
        if (vec3 != null) {
            this.minecraft.player.lookAt(packet.getFromAnchor(), vec3);
        }
    }

    @Override
    public void handleTagQueryPacket(ClientboundTagQueryPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (!this.debugQueryHandler.handleResponse(packet.getTransactionId(), packet.getTag())) {
            LOGGER.debug("Got unhandled response to tag query {}", packet.getTransactionId());
        }
    }

    /**
     * Updates the players statistics or achievements
     */
    @Override
    public void handleAwardStats(ClientboundAwardStatsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);

        for (Entry<Stat<?>> entry : packet.stats().object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            int i = entry.getIntValue();
            this.minecraft.player.getStats().setValue(this.minecraft.player, stat, i);
        }

        if (this.minecraft.screen instanceof StatsScreen statsScreen) {
            statsScreen.onStatsUpdated();
        }
    }

    @Override
    public void handleAddOrRemoveRecipes(ClientboundRecipePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        ClientRecipeBook clientRecipeBook = this.minecraft.player.getRecipeBook();
        clientRecipeBook.setBookSettings(packet.getBookSettings());
        ClientboundRecipePacket.State state = packet.getState();
        switch (state) {
            case REMOVE:
                for (ResourceLocation resourceLocation : packet.getRecipes()) {
                    this.recipeManager.byKey(resourceLocation).ifPresent(clientRecipeBook::remove);
                }
                break;
            case INIT:
                for (ResourceLocation resourceLocation : packet.getRecipes()) {
                    this.recipeManager.byKey(resourceLocation).ifPresent(clientRecipeBook::add);
                }

                for (ResourceLocation resourceLocation : packet.getHighlights()) {
                    this.recipeManager.byKey(resourceLocation).ifPresent(clientRecipeBook::addHighlight);
                }
                break;
            case ADD:
                for (ResourceLocation resourceLocation : packet.getRecipes()) {
                    this.recipeManager.byKey(resourceLocation).ifPresent(recipeHolder -> {
                        clientRecipeBook.add((RecipeHolder<?>)recipeHolder);
                        clientRecipeBook.addHighlight((RecipeHolder<?>)recipeHolder);
                        if (recipeHolder.value().showNotification()) {
                            RecipeToast.addOrUpdate(this.minecraft.getToasts(), (RecipeHolder<?>)recipeHolder);
                        }
                    });
                }
        }

        clientRecipeBook.getCollections().forEach(recipeCollection -> recipeCollection.updateKnownRecipes(clientRecipeBook));
        if (this.minecraft.screen instanceof RecipeUpdateListener) {
            ((RecipeUpdateListener)this.minecraft.screen).recipesUpdated();
        }
    }

    @Override
    public void handleUpdateMobEffect(ClientboundUpdateMobEffectPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getEntityId());
        if (entity instanceof LivingEntity) {
            Holder<MobEffect> holder = packet.getEffect();
            MobEffectInstance mobEffectInstance = new MobEffectInstance(
                holder,
                packet.getEffectDurationTicks(),
                packet.getEffectAmplifier(),
                packet.isEffectAmbient(),
                packet.isEffectVisible(),
                packet.effectShowsIcon(),
                null
            );
            if (!packet.shouldBlend()) {
                mobEffectInstance.skipBlending();
            }

            ((LivingEntity)entity).forceAddEffect(mobEffectInstance, null);
        }
    }

    @Override
    public void handleUpdateTags(ClientboundUpdateTagsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        TagCollector tagCollector = new TagCollector();
        packet.getTags().forEach(tagCollector::append);
        tagCollector.updateTags(this.registryAccess, this.connection.isMemoryConnection());
        List<ItemStack> list = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
        this.searchTrees.updateCreativeTags(list);
    }

    @Override
    public void handlePlayerCombatEnd(ClientboundPlayerCombatEndPacket packet) {
    }

    @Override
    public void handlePlayerCombatEnter(ClientboundPlayerCombatEnterPacket packet) {
    }

    @Override
    public void handlePlayerCombatKill(ClientboundPlayerCombatKillPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.playerId());
        if (entity == this.minecraft.player) {
            if (this.minecraft.player.shouldShowDeathScreen()) {
                this.minecraft.setScreen(new DeathScreen(packet.message(), this.level.getLevelData().isHardcore()));
            } else {
                this.minecraft.player.respawn();
            }
        }
    }

    @Override
    public void handleChangeDifficulty(ClientboundChangeDifficultyPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.levelData.setDifficulty(packet.getDifficulty());
        this.levelData.setDifficultyLocked(packet.isLocked());
    }

    @Override
    public void handleSetCamera(ClientboundSetCameraPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = packet.getEntity(this.level);
        if (entity != null) {
            this.minecraft.setCameraEntity(entity);
        }
    }

    @Override
    public void handleInitializeBorder(ClientboundInitializeBorderPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        WorldBorder worldBorder = this.level.getWorldBorder();
        worldBorder.setCenter(packet.getNewCenterX(), packet.getNewCenterZ());
        long l = packet.getLerpTime();
        if (l > 0L) {
            worldBorder.lerpSizeBetween(packet.getOldSize(), packet.getNewSize(), l);
        } else {
            worldBorder.setSize(packet.getNewSize());
        }

        worldBorder.setAbsoluteMaxSize(packet.getNewAbsoluteMaxSize());
        worldBorder.setWarningBlocks(packet.getWarningBlocks());
        worldBorder.setWarningTime(packet.getWarningTime());
    }

    @Override
    public void handleSetBorderCenter(ClientboundSetBorderCenterPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getWorldBorder().setCenter(packet.getNewCenterX(), packet.getNewCenterZ());
    }

    @Override
    public void handleSetBorderLerpSize(ClientboundSetBorderLerpSizePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getWorldBorder().lerpSizeBetween(packet.getOldSize(), packet.getNewSize(), packet.getLerpTime());
    }

    @Override
    public void handleSetBorderSize(ClientboundSetBorderSizePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getWorldBorder().setSize(packet.getSize());
    }

    @Override
    public void handleSetBorderWarningDistance(ClientboundSetBorderWarningDistancePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getWorldBorder().setWarningBlocks(packet.getWarningBlocks());
    }

    @Override
    public void handleSetBorderWarningDelay(ClientboundSetBorderWarningDelayPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getWorldBorder().setWarningTime(packet.getWarningDelay());
    }

    @Override
    public void handleTitlesClear(ClientboundClearTitlesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.clear();
        if (packet.shouldResetTimes()) {
            this.minecraft.gui.resetTitleTimes();
        }
    }

    @Override
    public void handleServerData(ClientboundServerDataPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.serverData != null) {
            this.serverData.motd = packet.motd();
            packet.iconBytes().map(ServerData::validateIcon).ifPresent(this.serverData::setIconBytes);
            ServerList.saveSingleServer(this.serverData);
        }
    }

    @Override
    public void handleCustomChatCompletions(ClientboundCustomChatCompletionsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.suggestionsProvider.modifyCustomCompletions(packet.action(), packet.entries());
    }

    @Override
    public void setActionBarText(ClientboundSetActionBarTextPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.setOverlayMessage(packet.text(), false);
    }

    @Override
    public void setTitleText(ClientboundSetTitleTextPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.setTitle(packet.text());
    }

    @Override
    public void setSubtitleText(ClientboundSetSubtitleTextPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.setSubtitle(packet.text());
    }

    @Override
    public void setTitlesAnimation(ClientboundSetTitlesAnimationPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.setTimes(packet.getFadeIn(), packet.getStay(), packet.getFadeOut());
    }

    @Override
    public void handleTabListCustomisation(ClientboundTabListPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.getTabList().setHeader(packet.header().getString().isEmpty() ? null : packet.header());
        this.minecraft.gui.getTabList().setFooter(packet.footer().getString().isEmpty() ? null : packet.footer());
    }

    @Override
    public void handleRemoveMobEffect(ClientboundRemoveMobEffectPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (packet.getEntity(this.level) instanceof LivingEntity livingEntity) {
            livingEntity.removeEffectNoUpdate(packet.effect());
        }
    }

    @Override
    public void handlePlayerInfoRemove(ClientboundPlayerInfoRemovePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);

        for (UUID uUID : packet.profileIds()) {
            this.minecraft.getPlayerSocialManager().removePlayer(uUID);
            PlayerInfo playerInfo = this.playerInfoMap.remove(uUID);
            if (playerInfo != null) {
                this.listedPlayers.remove(playerInfo);
            }
        }
    }

    @Override
    public void handlePlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);

        for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.newEntries()) {
            PlayerInfo playerInfo = new PlayerInfo(Objects.requireNonNull(entry.profile()), this.enforcesSecureChat());
            if (this.playerInfoMap.putIfAbsent(entry.profileId(), playerInfo) == null) {
                this.minecraft.getPlayerSocialManager().addPlayer(playerInfo);
            }
        }

        for (ClientboundPlayerInfoUpdatePacket.Entry entry : packet.entries()) {
            PlayerInfo playerInfo = this.playerInfoMap.get(entry.profileId());
            if (playerInfo == null) {
                LOGGER.warn("Ignoring player info update for unknown player {} ({})", entry.profileId(), packet.actions());
            } else {
                for (ClientboundPlayerInfoUpdatePacket.Action action : packet.actions()) {
                    this.applyPlayerInfoUpdate(action, entry, playerInfo);
                }
            }
        }
    }

    private void applyPlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket.Action action, ClientboundPlayerInfoUpdatePacket.Entry entry, PlayerInfo playerInfo) {
        switch (action) {
            case INITIALIZE_CHAT:
                this.initializeChatSession(entry, playerInfo);
                break;
            case UPDATE_GAME_MODE:
                if (playerInfo.getGameMode() != entry.gameMode() && this.minecraft.player != null && this.minecraft.player.getUUID().equals(entry.profileId())) {
                    this.minecraft.player.onGameModeChanged(entry.gameMode());
                }

                playerInfo.setGameMode(entry.gameMode());
                break;
            case UPDATE_LISTED:
                if (entry.listed()) {
                    this.listedPlayers.add(playerInfo);
                } else {
                    this.listedPlayers.remove(playerInfo);
                }
                break;
            case UPDATE_LATENCY:
                playerInfo.setLatency(entry.latency());
                break;
            case UPDATE_DISPLAY_NAME:
                playerInfo.setTabListDisplayName(entry.displayName());
        }
    }

    private void initializeChatSession(ClientboundPlayerInfoUpdatePacket.Entry entry, PlayerInfo playerInfo) {
        GameProfile gameProfile = playerInfo.getProfile();
        SignatureValidator signatureValidator = this.minecraft.getProfileKeySignatureValidator();
        if (signatureValidator == null) {
            LOGGER.warn("Ignoring chat session from {} due to missing Services public key", gameProfile.getName());
            playerInfo.clearChatSession(this.enforcesSecureChat());
        } else {
            RemoteChatSession.Data data = entry.chatSession();
            if (data != null) {
                try {
                    RemoteChatSession remoteChatSession = data.validate(gameProfile, signatureValidator);
                    playerInfo.setChatSession(remoteChatSession);
                } catch (ProfilePublicKey.ValidationException validationException) {
                    LOGGER.error("Failed to validate profile key for player: '{}'", gameProfile.getName(), validationException);
                    playerInfo.clearChatSession(this.enforcesSecureChat());
                }
            } else {
                playerInfo.clearChatSession(this.enforcesSecureChat());
            }
        }
    }

    private boolean enforcesSecureChat() {
        return this.minecraft.canValidateProfileKeys() && this.serverEnforcesSecureChat;
    }

    @Override
    public void handlePlayerAbilities(ClientboundPlayerAbilitiesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Player player = this.minecraft.player;
        player.getAbilities().flying = packet.isFlying();
        player.getAbilities().instabuild = packet.canInstabuild();
        player.getAbilities().invulnerable = packet.isInvulnerable();
        player.getAbilities().mayfly = packet.canFly();
        player.getAbilities().setFlyingSpeed(packet.getFlyingSpeed());
        player.getAbilities().setWalkingSpeed(packet.getWalkingSpeed());
    }

    @Override
    public void handleSoundEvent(ClientboundSoundPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft
            .level
            .playSeededSound(
                this.minecraft.player,
                packet.getX(),
                packet.getY(),
                packet.getZ(),
                packet.getSound(),
                packet.getSource(),
                packet.getVolume(),
                packet.getPitch(),
                packet.getSeed()
            );
    }

    @Override
    public void handleSoundEntityEvent(ClientboundSoundEntityPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getId());
        if (entity != null) {
            this.minecraft
                .level
                .playSeededSound(this.minecraft.player, entity, packet.getSound(), packet.getSource(), packet.getVolume(), packet.getPitch(), packet.getSeed());
        }
    }

    @Override
    public void handleBossUpdate(ClientboundBossEventPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.minecraft.gui.getBossOverlay().update(packet);
    }

    @Override
    public void handleItemCooldown(ClientboundCooldownPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (packet.duration() == 0) {
            this.minecraft.player.getCooldowns().removeCooldown(packet.item());
        } else {
            this.minecraft.player.getCooldowns().addCooldown(packet.item(), packet.duration());
        }
    }

    @Override
    public void handleMoveVehicle(ClientboundMoveVehiclePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.minecraft.player.getRootVehicle();
        if (entity != this.minecraft.player && entity.isControlledByLocalInstance()) {
            entity.absMoveTo(packet.getX(), packet.getY(), packet.getZ(), packet.getYRot(), packet.getXRot());
            this.connection.send(new ServerboundMoveVehiclePacket(entity));
        }
    }

    @Override
    public void handleOpenBook(ClientboundOpenBookPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        ItemStack itemStack = this.minecraft.player.getItemInHand(packet.getHand());
        BookViewScreen.BookAccess bookAccess = BookViewScreen.BookAccess.fromItem(itemStack);
        if (bookAccess != null) {
            this.minecraft.setScreen(new BookViewScreen(bookAccess));
        }
    }

    @Override
    public void handleCustomPayload(CustomPacketPayload payload) {
        if (payload instanceof PathfindingDebugPayload pathfindingDebugPayload) {
            this.minecraft
                .debugRenderer
                .pathfindingRenderer
                .addPath(pathfindingDebugPayload.entityId(), pathfindingDebugPayload.path(), pathfindingDebugPayload.maxNodeDistance());
        } else if (payload instanceof NeighborUpdatesDebugPayload neighborUpdatesDebugPayload) {
            ((NeighborsUpdateRenderer)this.minecraft.debugRenderer.neighborsUpdateRenderer)
                .addUpdate(neighborUpdatesDebugPayload.time(), neighborUpdatesDebugPayload.pos());
        } else if (payload instanceof StructuresDebugPayload structuresDebugPayload) {
            this.minecraft
                .debugRenderer
                .structureRenderer
                .addBoundingBox(structuresDebugPayload.mainBB(), structuresDebugPayload.pieces(), structuresDebugPayload.dimension());
        } else if (payload instanceof WorldGenAttemptDebugPayload worldGenAttemptDebugPayload) {
            ((WorldGenAttemptRenderer)this.minecraft.debugRenderer.worldGenAttemptRenderer)
                .addPos(
                    worldGenAttemptDebugPayload.pos(),
                    worldGenAttemptDebugPayload.scale(),
                    worldGenAttemptDebugPayload.red(),
                    worldGenAttemptDebugPayload.green(),
                    worldGenAttemptDebugPayload.blue(),
                    worldGenAttemptDebugPayload.alpha()
                );
        } else if (payload instanceof PoiTicketCountDebugPayload poiTicketCountDebugPayload) {
            this.minecraft.debugRenderer.brainDebugRenderer.setFreeTicketCount(poiTicketCountDebugPayload.pos(), poiTicketCountDebugPayload.freeTicketCount());
        } else if (payload instanceof PoiAddedDebugPayload poiAddedDebugPayload) {
            BrainDebugRenderer.PoiInfo poiInfo = new BrainDebugRenderer.PoiInfo(
                poiAddedDebugPayload.pos(), poiAddedDebugPayload.poiType(), poiAddedDebugPayload.freeTicketCount()
            );
            this.minecraft.debugRenderer.brainDebugRenderer.addPoi(poiInfo);
        } else if (payload instanceof PoiRemovedDebugPayload poiRemovedDebugPayload) {
            this.minecraft.debugRenderer.brainDebugRenderer.removePoi(poiRemovedDebugPayload.pos());
        } else if (payload instanceof VillageSectionsDebugPayload villageSectionsDebugPayload) {
            VillageSectionsDebugRenderer villageSectionsDebugRenderer = this.minecraft.debugRenderer.villageSectionsDebugRenderer;
            villageSectionsDebugPayload.villageChunks().forEach(villageSectionsDebugRenderer::setVillageSection);
            villageSectionsDebugPayload.notVillageChunks().forEach(villageSectionsDebugRenderer::setNotVillageSection);
        } else if (payload instanceof GoalDebugPayload goalDebugPayload) {
            this.minecraft.debugRenderer.goalSelectorRenderer.addGoalSelector(goalDebugPayload.entityId(), goalDebugPayload.pos(), goalDebugPayload.goals());
        } else if (payload instanceof BrainDebugPayload brainDebugPayload) {
            this.minecraft.debugRenderer.brainDebugRenderer.addOrUpdateBrainDump(brainDebugPayload.brainDump());
        } else if (payload instanceof BeeDebugPayload beeDebugPayload) {
            this.minecraft.debugRenderer.beeDebugRenderer.addOrUpdateBeeInfo(beeDebugPayload.beeInfo());
        } else if (payload instanceof HiveDebugPayload hiveDebugPayload) {
            this.minecraft.debugRenderer.beeDebugRenderer.addOrUpdateHiveInfo(hiveDebugPayload.hiveInfo(), this.level.getGameTime());
        } else if (payload instanceof GameTestAddMarkerDebugPayload gameTestAddMarkerDebugPayload) {
            this.minecraft
                .debugRenderer
                .gameTestDebugRenderer
                .addMarker(
                    gameTestAddMarkerDebugPayload.pos(),
                    gameTestAddMarkerDebugPayload.color(),
                    gameTestAddMarkerDebugPayload.text(),
                    gameTestAddMarkerDebugPayload.durationMs()
                );
        } else if (payload instanceof GameTestClearMarkersDebugPayload) {
            this.minecraft.debugRenderer.gameTestDebugRenderer.clear();
        } else if (payload instanceof RaidsDebugPayload raidsDebugPayload) {
            this.minecraft.debugRenderer.raidDebugRenderer.setRaidCenters(raidsDebugPayload.raidCenters());
        } else if (payload instanceof GameEventDebugPayload gameEventDebugPayload) {
            this.minecraft.debugRenderer.gameEventListenerRenderer.trackGameEvent(gameEventDebugPayload.gameEventType(), gameEventDebugPayload.pos());
        } else if (payload instanceof GameEventListenerDebugPayload gameEventListenerDebugPayload) {
            this.minecraft
                .debugRenderer
                .gameEventListenerRenderer
                .trackListener(gameEventListenerDebugPayload.listenerPos(), gameEventListenerDebugPayload.listenerRange());
        } else if (payload instanceof BreezeDebugPayload breezeDebugPayload) {
            this.minecraft.debugRenderer.breezeDebugRenderer.add(breezeDebugPayload.breezeInfo());
        } else {
            this.handleUnknownCustomPayload(payload);
        }
    }

    private void handleUnknownCustomPayload(CustomPacketPayload packet) {
        LOGGER.warn("Unknown custom packet payload: {}", packet.type().id());
    }

    /**
     * May create a scoreboard objective, remove an objective from the scoreboard or update an objectives' displayname
     */
    @Override
    public void handleAddObjective(ClientboundSetObjectivePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        String string = packet.getObjectiveName();
        if (packet.getMethod() == 0) {
            this.scoreboard
                .addObjective(string, ObjectiveCriteria.DUMMY, packet.getDisplayName(), packet.getRenderType(), false, packet.getNumberFormat().orElse(null));
        } else {
            Objective objective = this.scoreboard.getObjective(string);
            if (objective != null) {
                if (packet.getMethod() == 1) {
                    this.scoreboard.removeObjective(objective);
                } else if (packet.getMethod() == 2) {
                    objective.setRenderType(packet.getRenderType());
                    objective.setDisplayName(packet.getDisplayName());
                    objective.setNumberFormat(packet.getNumberFormat().orElse(null));
                }
            }
        }
    }

    /**
     * Either updates the score with a specified value or removes the score for an objective
     */
    @Override
    public void handleSetScore(ClientboundSetScorePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        String string = packet.objectiveName();
        ScoreHolder scoreHolder = ScoreHolder.forNameOnly(packet.owner());
        Objective objective = this.scoreboard.getObjective(string);
        if (objective != null) {
            ScoreAccess scoreAccess = this.scoreboard.getOrCreatePlayerScore(scoreHolder, objective, true);
            scoreAccess.set(packet.score());
            scoreAccess.display(packet.display().orElse(null));
            scoreAccess.numberFormatOverride(packet.numberFormat().orElse(null));
        } else {
            LOGGER.warn("Received packet for unknown scoreboard objective: {}", string);
        }
    }

    @Override
    public void handleResetScore(ClientboundResetScorePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        String string = packet.objectiveName();
        ScoreHolder scoreHolder = ScoreHolder.forNameOnly(packet.owner());
        if (string == null) {
            this.scoreboard.resetAllPlayerScores(scoreHolder);
        } else {
            Objective objective = this.scoreboard.getObjective(string);
            if (objective != null) {
                this.scoreboard.resetSinglePlayerScore(scoreHolder, objective);
            } else {
                LOGGER.warn("Received packet for unknown scoreboard objective: {}", string);
            }
        }
    }

    /**
     * Removes or sets the ScoreObjective to be displayed at a particular scoreboard position (list, sidebar, below name)
     */
    @Override
    public void handleSetDisplayObjective(ClientboundSetDisplayObjectivePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        String string = packet.getObjectiveName();
        Objective objective = string == null ? null : this.scoreboard.getObjective(string);
        this.scoreboard.setDisplayObjective(packet.getSlot(), objective);
    }

    /**
     * Updates a team managed by the scoreboard: Create/Remove the team registration, Register/Remove the player-team-memberships, Set team displayname/prefix/suffix and/or whether friendly fire is enabled
     */
    @Override
    public void handleSetPlayerTeamPacket(ClientboundSetPlayerTeamPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        ClientboundSetPlayerTeamPacket.Action action = packet.getTeamAction();
        PlayerTeam playerTeam;
        if (action == ClientboundSetPlayerTeamPacket.Action.ADD) {
            playerTeam = this.scoreboard.addPlayerTeam(packet.getName());
        } else {
            playerTeam = this.scoreboard.getPlayerTeam(packet.getName());
            if (playerTeam == null) {
                LOGGER.warn(
                    "Received packet for unknown team {}: team action: {}, player action: {}",
                    packet.getName(),
                    packet.getTeamAction(),
                    packet.getPlayerAction()
                );
                return;
            }
        }

        Optional<ClientboundSetPlayerTeamPacket.Parameters> optional = packet.getParameters();
        optional.ifPresent(parameters -> {
            playerTeam.setDisplayName(parameters.getDisplayName());
            playerTeam.setColor(parameters.getColor());
            playerTeam.unpackOptions(parameters.getOptions());
            Team.Visibility visibility = Team.Visibility.byName(parameters.getNametagVisibility());
            if (visibility != null) {
                playerTeam.setNameTagVisibility(visibility);
            }

            Team.CollisionRule collisionRule = Team.CollisionRule.byName(parameters.getCollisionRule());
            if (collisionRule != null) {
                playerTeam.setCollisionRule(collisionRule);
            }

            playerTeam.setPlayerPrefix(parameters.getPlayerPrefix());
            playerTeam.setPlayerSuffix(parameters.getPlayerSuffix());
        });
        ClientboundSetPlayerTeamPacket.Action action2 = packet.getPlayerAction();
        if (action2 == ClientboundSetPlayerTeamPacket.Action.ADD) {
            for (String string : packet.getPlayers()) {
                this.scoreboard.addPlayerToTeam(string, playerTeam);
            }
        } else if (action2 == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
            for (String string : packet.getPlayers()) {
                this.scoreboard.removePlayerFromTeam(string, playerTeam);
            }
        }

        if (action == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
            this.scoreboard.removePlayerTeam(playerTeam);
        }
    }

    /**
     * Spawns a specified number of particles at the specified location with a randomized displacement according to specified bounds
     */
    @Override
    public void handleParticleEvent(ClientboundLevelParticlesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (packet.getCount() == 0) {
            double d = packet.getMaxSpeed() * packet.getXDist();
            double e = packet.getMaxSpeed() * packet.getYDist();
            double f = packet.getMaxSpeed() * packet.getZDist();

            try {
                this.level.addParticle(packet.getParticle(), packet.isOverrideLimiter(), packet.getX(), packet.getY(), packet.getZ(), d, e, f);
            } catch (Throwable throwable) {
                LOGGER.warn("Could not spawn particle effect {}", packet.getParticle());
            }
        } else {
            for (int i = 0; i < packet.getCount(); i++) {
                double g = this.random.nextGaussian() * packet.getXDist();
                double h = this.random.nextGaussian() * packet.getYDist();
                double j = this.random.nextGaussian() * packet.getZDist();
                double k = this.random.nextGaussian() * packet.getMaxSpeed();
                double l = this.random.nextGaussian() * packet.getMaxSpeed();
                double m = this.random.nextGaussian() * packet.getMaxSpeed();

                try {
                    this.level.addParticle(packet.getParticle(), packet.isOverrideLimiter(), packet.getX() + g, packet.getY() + h, packet.getZ() + j, k, l, m);
                } catch (Throwable throwable2) {
                    LOGGER.warn("Could not spawn particle effect {}", packet.getParticle());
                    return;
                }
            }
        }
    }

    /**
     * Updates en entity's attributes and their respective modifiers, which are used for speed bonuses (player sprinting, animals fleeing, baby speed), weapon/tool attackDamage, hostiles followRange randomization, zombie maxHealth and knockback resistance as well as reinforcement spawning chance.
     */
    @Override
    public void handleUpdateAttributes(ClientboundUpdateAttributesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        Entity entity = this.level.getEntity(packet.getEntityId());
        if (entity != null) {
            if (!(entity instanceof LivingEntity)) {
                throw new IllegalStateException("Server tried to update attributes of a non-living entity (actually: " + entity + ")");
            }

            AttributeMap attributeMap = ((LivingEntity)entity).getAttributes();

            for (ClientboundUpdateAttributesPacket.AttributeSnapshot attributeSnapshot : packet.getValues()) {
                AttributeInstance attributeInstance = attributeMap.getInstance(attributeSnapshot.attribute());
                if (attributeInstance == null) {
                    LOGGER.warn("Entity {} does not have attribute {}", entity, attributeSnapshot.attribute().getRegisteredName());
                } else {
                    attributeInstance.setBaseValue(attributeSnapshot.base());
                    attributeInstance.removeModifiers();

                    for (AttributeModifier attributeModifier : attributeSnapshot.modifiers()) {
                        attributeInstance.addTransientModifier(attributeModifier);
                    }
                }
            }
        }
    }

    @Override
    public void handlePlaceRecipe(ClientboundPlaceGhostRecipePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        AbstractContainerMenu abstractContainerMenu = this.minecraft.player.containerMenu;
        if (abstractContainerMenu.containerId == packet.getContainerId()) {
            this.recipeManager.byKey(packet.getRecipe()).ifPresent(recipeHolder -> {
                if (this.minecraft.screen instanceof RecipeUpdateListener) {
                    RecipeBookComponent recipeBookComponent = ((RecipeUpdateListener)this.minecraft.screen).getRecipeBookComponent();
                    recipeBookComponent.setupGhostRecipe((RecipeHolder<?>)recipeHolder, abstractContainerMenu.slots);
                }
            });
        }
    }

    @Override
    public void handleLightUpdatePacket(ClientboundLightUpdatePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        int i = packet.getX();
        int j = packet.getZ();
        ClientboundLightUpdatePacketData clientboundLightUpdatePacketData = packet.getLightData();
        this.level.queueLightUpdate(() -> this.applyLightData(i, j, clientboundLightUpdatePacketData));
    }

    private void applyLightData(int x, int z, ClientboundLightUpdatePacketData data) {
        LevelLightEngine levelLightEngine = this.level.getChunkSource().getLightEngine();
        BitSet bitSet = data.getSkyYMask();
        BitSet bitSet2 = data.getEmptySkyYMask();
        Iterator<byte[]> iterator = data.getSkyUpdates().iterator();
        this.readSectionList(x, z, levelLightEngine, LightLayer.SKY, bitSet, bitSet2, iterator);
        BitSet bitSet3 = data.getBlockYMask();
        BitSet bitSet4 = data.getEmptyBlockYMask();
        Iterator<byte[]> iterator2 = data.getBlockUpdates().iterator();
        this.readSectionList(x, z, levelLightEngine, LightLayer.BLOCK, bitSet3, bitSet4, iterator2);
        levelLightEngine.setLightEnabled(new ChunkPos(x, z), true);
    }

    @Override
    public void handleMerchantOffers(ClientboundMerchantOffersPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        AbstractContainerMenu abstractContainerMenu = this.minecraft.player.containerMenu;
        if (packet.getContainerId() == abstractContainerMenu.containerId && abstractContainerMenu instanceof MerchantMenu merchantMenu) {
            merchantMenu.setOffers(packet.getOffers());
            merchantMenu.setXp(packet.getVillagerXp());
            merchantMenu.setMerchantLevel(packet.getVillagerLevel());
            merchantMenu.setShowProgressBar(packet.showProgress());
            merchantMenu.setCanRestock(packet.canRestock());
        }
    }

    @Override
    public void handleSetChunkCacheRadius(ClientboundSetChunkCacheRadiusPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.serverChunkRadius = packet.getRadius();
        this.minecraft.options.setServerRenderDistance(this.serverChunkRadius);
        this.level.getChunkSource().updateViewRadius(packet.getRadius());
    }

    @Override
    public void handleSetSimulationDistance(ClientboundSetSimulationDistancePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.serverSimulationDistance = packet.simulationDistance();
        this.level.setServerSimulationDistance(this.serverSimulationDistance);
    }

    @Override
    public void handleSetChunkCacheCenter(ClientboundSetChunkCacheCenterPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.getChunkSource().updateViewCenter(packet.getX(), packet.getZ());
    }

    @Override
    public void handleBlockChangedAck(ClientboundBlockChangedAckPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.level.handleBlockChangedAck(packet.sequence());
    }

    @Override
    public void handleBundlePacket(ClientboundBundlePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);

        for (Packet<? super ClientGamePacketListener> packet2 : packet.subPackets()) {
            packet2.handle(this);
        }
    }

    @Override
    public void handleProjectilePowerPacket(ClientboundProjectilePowerPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.level.getEntity(packet.getId()) instanceof AbstractHurtingProjectile abstractHurtingProjectile) {
            abstractHurtingProjectile.accelerationPower = packet.getAccelerationPower();
        }
    }

    @Override
    public void handleChunkBatchStart(ClientboundChunkBatchStartPacket packet) {
        this.chunkBatchSizeCalculator.onBatchStart();
    }

    @Override
    public void handleChunkBatchFinished(ClientboundChunkBatchFinishedPacket packet) {
        this.chunkBatchSizeCalculator.onBatchFinished(packet.batchSize());
        this.send(new ServerboundChunkBatchReceivedPacket(this.chunkBatchSizeCalculator.getDesiredChunksPerTick()));
    }

    @Override
    public void handleDebugSample(ClientboundDebugSamplePacket packet) {
        this.minecraft.getDebugOverlay().logRemoteSample(packet.sample(), packet.debugSampleType());
    }

    @Override
    public void handlePongResponse(ClientboundPongResponsePacket packet) {
        this.pingDebugMonitor.onPongReceived(packet);
    }

    private void readSectionList(
        int x, int z, LevelLightEngine lightEngine, LightLayer lightLayer, BitSet skyYMask, BitSet emptySkyYMask, Iterator<byte[]> skyUpdates
    ) {
        for (int i = 0; i < lightEngine.getLightSectionCount(); i++) {
            int j = lightEngine.getMinLightSection() + i;
            boolean bl = skyYMask.get(i);
            boolean bl2 = emptySkyYMask.get(i);
            if (bl || bl2) {
                lightEngine.queueSectionData(lightLayer, SectionPos.of(x, j, z), bl ? new DataLayer((byte[])skyUpdates.next().clone()) : new DataLayer());
                this.level.setSectionDirtyWithNeighbors(x, j, z);
            }
        }
    }

    public Connection getConnection() {
        return this.connection;
    }

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected() && !this.closed;
    }

    public Collection<PlayerInfo> getListedOnlinePlayers() {
        return this.listedPlayers;
    }

    public Collection<PlayerInfo> getOnlinePlayers() {
        return this.playerInfoMap.values();
    }

    public Collection<UUID> getOnlinePlayerIds() {
        return this.playerInfoMap.keySet();
    }

    @Nullable
    public PlayerInfo getPlayerInfo(UUID uniqueId) {
        return this.playerInfoMap.get(uniqueId);
    }

    /**
     * Gets the client's description information about another player on the server.
     */
    @Nullable
    public PlayerInfo getPlayerInfo(String name) {
        for (PlayerInfo playerInfo : this.playerInfoMap.values()) {
            if (playerInfo.getProfile().getName().equals(name)) {
                return playerInfo;
            }
        }

        return null;
    }

    public GameProfile getLocalGameProfile() {
        return this.localGameProfile;
    }

    public ClientAdvancements getAdvancements() {
        return this.advancements;
    }

    public CommandDispatcher<SharedSuggestionProvider> getCommands() {
        return this.commands;
    }

    public ClientLevel getLevel() {
        return this.level;
    }

    public DebugQueryHandler getDebugQueryHandler() {
        return this.debugQueryHandler;
    }

    public UUID getId() {
        return this.id;
    }

    public Set<ResourceKey<Level>> levels() {
        return this.levels;
    }

    public RegistryAccess.Frozen registryAccess() {
        return this.registryAccess;
    }

    public void markMessageAsProcessed(PlayerChatMessage chatMessage, boolean acknowledged) {
        MessageSignature messageSignature = chatMessage.signature();
        if (messageSignature != null && this.lastSeenMessages.addPending(messageSignature, acknowledged) && this.lastSeenMessages.offset() > 64) {
            this.sendChatAcknowledgement();
        }
    }

    private void sendChatAcknowledgement() {
        int i = this.lastSeenMessages.getAndClearOffset();
        if (i > 0) {
            this.send(new ServerboundChatAckPacket(i));
        }
    }

    public void sendChat(String message) {
        Instant instant = Instant.now();
        long l = Crypt.SaltSupplier.getLong();
        LastSeenMessagesTracker.Update update = this.lastSeenMessages.generateAndApplyUpdate();
        MessageSignature messageSignature = this.signedMessageEncoder.pack(new SignedMessageBody(message, instant, l, update.lastSeen()));
        this.send(new ServerboundChatPacket(message, instant, l, messageSignature, update.update()));
    }

    public void sendCommand(String command) {
        SignableCommand<SharedSuggestionProvider> signableCommand = SignableCommand.of(this.parseCommand(command));
        if (signableCommand.arguments().isEmpty()) {
            this.send(new ServerboundChatCommandPacket(command));
        } else {
            Instant instant = Instant.now();
            long l = Crypt.SaltSupplier.getLong();
            LastSeenMessagesTracker.Update update = this.lastSeenMessages.generateAndApplyUpdate();
            ArgumentSignatures argumentSignatures = ArgumentSignatures.signCommand(signableCommand, string -> {
                SignedMessageBody signedMessageBody = new SignedMessageBody(string, instant, l, update.lastSeen());
                return this.signedMessageEncoder.pack(signedMessageBody);
            });
            this.send(new ServerboundChatCommandSignedPacket(command, instant, l, argumentSignatures, update.update()));
        }
    }

    public boolean sendUnsignedCommand(String command) {
        if (!SignableCommand.hasSignableArguments(this.parseCommand(command))) {
            this.send(new ServerboundChatCommandPacket(command));
            return true;
        } else {
            return false;
        }
    }

    private ParseResults<SharedSuggestionProvider> parseCommand(String command) {
        return this.commands.parse(command, this.suggestionsProvider);
    }

    @Override
    public void tick() {
        if (this.connection.isEncrypted()) {
            ProfileKeyPairManager profileKeyPairManager = this.minecraft.getProfileKeyPairManager();
            if (profileKeyPairManager.shouldRefreshKeyPair()) {
                profileKeyPairManager.prepareKeyPair().thenAcceptAsync(optional -> optional.ifPresent(this::setKeyPair), this.minecraft);
            }
        }

        this.sendDeferredPackets();
        if (this.minecraft.getDebugOverlay().showNetworkCharts()) {
            this.pingDebugMonitor.tick();
        }

        this.debugSampleSubscriber.tick();
        this.telemetryManager.tick();
        if (this.levelLoadStatusManager != null) {
            this.levelLoadStatusManager.tick();
        }
    }

    public void setKeyPair(ProfileKeyPair keyPair) {
        if (this.minecraft.isLocalPlayer(this.localGameProfile.getId())) {
            if (this.chatSession == null || !this.chatSession.keyPair().equals(keyPair)) {
                this.chatSession = LocalChatSession.create(keyPair);
                this.signedMessageEncoder = this.chatSession.createMessageEncoder(this.localGameProfile.getId());
                this.send(new ServerboundChatSessionUpdatePacket(this.chatSession.asRemote().asData()));
            }
        }
    }

    @Nullable
    public ServerData getServerData() {
        return this.serverData;
    }

    public FeatureFlagSet enabledFeatures() {
        return this.enabledFeatures;
    }

    public boolean isFeatureEnabled(FeatureFlagSet enabledFeatures) {
        return enabledFeatures.isSubsetOf(this.enabledFeatures());
    }

    public Scoreboard scoreboard() {
        return this.scoreboard;
    }

    public PotionBrewing potionBrewing() {
        return this.potionBrewing;
    }

    public void updateSearchTrees() {
        this.searchTrees.rebuildAfterLanguageChange();
    }

    public SessionSearchTrees searchTrees() {
        return this.searchTrees;
    }

    public ServerLinks serverLinks() {
        return this.serverLinks;
    }
}
