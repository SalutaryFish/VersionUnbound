package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import java.io.File;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.FileUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;

public abstract class PlayerList {
    public static final File USERBANLIST_FILE = new File("banned-players.json");
    public static final File IPBANLIST_FILE = new File("banned-ips.json");
    public static final File OPLIST_FILE = new File("ops.json");
    public static final File WHITELIST_FILE = new File("whitelist.json");
    public static final Component CHAT_FILTERED_FULL = Component.translatable("chat.filtered_full");
    public static final Component DUPLICATE_LOGIN_DISCONNECT_MESSAGE = Component.translatable("multiplayer.disconnect.duplicate_login");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEND_PLAYER_INFO_INTERVAL = 600;
    private static final SimpleDateFormat BAN_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
    private final MinecraftServer server;
    private final List<ServerPlayer> players = Lists.newArrayList();
    /**
     * A map containing the key-value pairs for UUIDs and their EntityPlayerMP objects.
     */
    private final Map<UUID, ServerPlayer> playersByUUID = Maps.newHashMap();
    private final UserBanList bans = new UserBanList(USERBANLIST_FILE);
    private final IpBanList ipBans = new IpBanList(IPBANLIST_FILE);
    private final ServerOpList ops = new ServerOpList(OPLIST_FILE);
    private final UserWhiteList whitelist = new UserWhiteList(WHITELIST_FILE);
    private final Map<UUID, ServerStatsCounter> stats = Maps.newHashMap();
    private final Map<UUID, PlayerAdvancements> advancements = Maps.newHashMap();
    private final PlayerDataStorage playerIo;
    private boolean doWhiteList;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    protected final int maxPlayers;
    private int viewDistance;
    private int simulationDistance;
    private boolean allowCommandsForAllPlayers;
    private static final boolean ALLOW_LOGOUTIVATOR = false;
    private int sendAllPlayerInfoIn;

    public PlayerList(MinecraftServer server, LayeredRegistryAccess<RegistryLayer> registries, PlayerDataStorage playerIo, int maxPlayers) {
        this.server = server;
        this.registries = registries;
        this.maxPlayers = maxPlayers;
        this.playerIo = playerIo;
    }

    public void placeNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie cookie) {
        GameProfile gameProfile = player.getGameProfile();
        GameProfileCache gameProfileCache = this.server.getProfileCache();
        String string;
        if (gameProfileCache != null) {
            Optional<GameProfile> optional = gameProfileCache.get(gameProfile.getId());
            string = optional.map(GameProfile::getName).orElse(gameProfile.getName());
            gameProfileCache.add(gameProfile);
        } else {
            string = gameProfile.getName();
        }

        Optional<CompoundTag> optional = this.load(player);
        ResourceKey<Level> resourceKey = optional.<ResourceKey<Level>>flatMap(
                compoundTagx -> DimensionType.parseLegacy(new Dynamic<>(NbtOps.INSTANCE, compoundTagx.get("Dimension"))).resultOrPartial(LOGGER::error)
            )
            .orElse(Level.OVERWORLD);
        ServerLevel serverLevel = this.server.getLevel(resourceKey);
        ServerLevel serverLevel2;
        if (serverLevel == null) {
            LOGGER.warn("Unknown respawn dimension {}, defaulting to overworld", resourceKey);
            serverLevel2 = this.server.overworld();
        } else {
            serverLevel2 = serverLevel;
        }

        player.setServerLevel(serverLevel2);
        String string2 = connection.getLoggableAddress(this.server.logIPs());
        LOGGER.info(
            "{}[{}] logged in with entity id {} at ({}, {}, {})",
            player.getName().getString(),
            string2,
            player.getId(),
            player.getX(),
            player.getY(),
            player.getZ()
        );
        LevelData levelData = serverLevel2.getLevelData();
        player.loadGameTypes(optional.orElse(null));
        ServerGamePacketListenerImpl serverGamePacketListenerImpl = new ServerGamePacketListenerImpl(this.server, connection, player, cookie);
        connection.setupInboundProtocol(
            GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess())), serverGamePacketListenerImpl
        );
        GameRules gameRules = serverLevel2.getGameRules();
        boolean bl = gameRules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        boolean bl2 = gameRules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO);
        boolean bl3 = gameRules.getBoolean(GameRules.RULE_LIMITED_CRAFTING);
        serverGamePacketListenerImpl.send(
            new ClientboundLoginPacket(
                player.getId(),
                levelData.isHardcore(),
                this.server.levelKeys(),
                this.getMaxPlayers(),
                this.viewDistance,
                this.simulationDistance,
                bl2,
                !bl,
                bl3,
                player.createCommonSpawnInfo(serverLevel2),
                this.server.enforceSecureProfile()
            )
        );
        serverGamePacketListenerImpl.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
        serverGamePacketListenerImpl.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        serverGamePacketListenerImpl.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        serverGamePacketListenerImpl.send(new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getOrderedRecipes()));
        this.sendPlayerPermissionLevel(player);
        player.getStats().markAllDirty();
        player.getRecipeBook().sendInitialRecipeBook(player);
        this.updateEntireScoreboard(serverLevel2.getScoreboard(), player);
        this.server.invalidateStatus();
        MutableComponent mutableComponent;
        if (player.getGameProfile().getName().equalsIgnoreCase(string)) {
            mutableComponent = Component.translatable("multiplayer.player.joined", player.getDisplayName());
        } else {
            mutableComponent = Component.translatable("multiplayer.player.joined.renamed", player.getDisplayName(), string);
        }

        this.broadcastSystemMessage(mutableComponent.withStyle(ChatFormatting.YELLOW), false);
        serverGamePacketListenerImpl.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        ServerStatus serverStatus = this.server.getStatus();
        if (serverStatus != null && !cookie.transferred()) {
            player.sendServerStatus(serverStatus);
        }

        player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(this.players));
        this.players.add(player);
        this.playersByUUID.put(player.getUUID(), player);
        this.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
        this.sendLevelInfo(player, serverLevel2);
        serverLevel2.addNewPlayer(player);
        this.server.getCustomBossEvents().onPlayerConnect(player);
        this.sendActivePlayerEffects(player);
        if (optional.isPresent() && optional.get().contains("RootVehicle", 10)) {
            CompoundTag compoundTag = optional.get().getCompound("RootVehicle");
            Entity entity = EntityType.loadEntityRecursive(
                compoundTag.getCompound("Entity"), serverLevel2, entityx -> !serverLevel2.addWithUUID(entityx) ? null : entityx
            );
            if (entity != null) {
                UUID uUID;
                if (compoundTag.hasUUID("Attach")) {
                    uUID = compoundTag.getUUID("Attach");
                } else {
                    uUID = null;
                }

                if (entity.getUUID().equals(uUID)) {
                    player.startRiding(entity, true);
                } else {
                    for (Entity entity2 : entity.getIndirectPassengers()) {
                        if (entity2.getUUID().equals(uUID)) {
                            player.startRiding(entity2, true);
                            break;
                        }
                    }
                }

                if (!player.isPassenger()) {
                    LOGGER.warn("Couldn't reattach entity to player");
                    entity.discard();

                    for (Entity entity2 : entity.getIndirectPassengers()) {
                        entity2.discard();
                    }
                }
            }
        }

        player.initInventoryMenu();
    }

    protected void updateEntireScoreboard(ServerScoreboard scoreboard, ServerPlayer player) {
        Set<Objective> set = Sets.newHashSet();

        for (PlayerTeam playerTeam : scoreboard.getPlayerTeams()) {
            player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(playerTeam, true));
        }

        for (DisplaySlot displaySlot : DisplaySlot.values()) {
            Objective objective = scoreboard.getDisplayObjective(displaySlot);
            if (objective != null && !set.contains(objective)) {
                for (Packet<?> packet : scoreboard.getStartTrackingPackets(objective)) {
                    player.connection.send(packet);
                }

                set.add(objective);
            }
        }
    }

    public void addWorldborderListener(ServerLevel level) {
        level.getWorldBorder().addListener(new BorderChangeListener() {
            @Override
            public void onBorderSizeSet(WorldBorder worldBorder, double d) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderSizePacket(worldBorder));
            }

            @Override
            public void onBorderSizeLerping(WorldBorder worldBorder, double d, double e, long l) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderLerpSizePacket(worldBorder));
            }

            @Override
            public void onBorderCenterSet(WorldBorder worldBorder, double d, double e) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderCenterPacket(worldBorder));
            }

            @Override
            public void onBorderSetWarningTime(WorldBorder worldBorder, int i) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDelayPacket(worldBorder));
            }

            @Override
            public void onBorderSetWarningBlocks(WorldBorder worldBorder, int i) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDistancePacket(worldBorder));
            }

            @Override
            public void onBorderSetDamagePerBlock(WorldBorder worldBorder, double d) {
            }

            @Override
            public void onBorderSetDamageSafeZOne(WorldBorder worldBorder, double d) {
            }
        });
    }

    public Optional<CompoundTag> load(ServerPlayer player) {
        CompoundTag compoundTag = this.server.getWorldData().getLoadedPlayerTag();
        Optional<CompoundTag> optional;
        if (this.server.isSingleplayerOwner(player.getGameProfile()) && compoundTag != null) {
            optional = Optional.of(compoundTag);
            player.load(compoundTag);
            LOGGER.debug("loading single player");
        } else {
            optional = this.playerIo.load(player);
        }

        return optional;
    }

    /**
     * Also stores the NBTTags if this is an IntegratedPlayerList.
     */
    protected void save(ServerPlayer player) {
        this.playerIo.save(player);
        ServerStatsCounter serverStatsCounter = this.stats.get(player.getUUID());
        if (serverStatsCounter != null) {
            serverStatsCounter.save();
        }

        PlayerAdvancements playerAdvancements = this.advancements.get(player.getUUID());
        if (playerAdvancements != null) {
            playerAdvancements.save();
        }
    }

    /**
     * Called when a player disconnects from the game. Writes player data to disk and removes them from the world.
     */
    public void remove(ServerPlayer player) {
        ServerLevel serverLevel = player.serverLevel();
        player.awardStat(Stats.LEAVE_GAME);
        this.save(player);
        if (player.isPassenger()) {
            Entity entity = player.getRootVehicle();
            if (entity.hasExactlyOnePlayerPassenger()) {
                LOGGER.debug("Removing player mount");
                player.stopRiding();
                entity.getPassengersAndSelf().forEach(entityx -> entityx.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER));
            }
        }

        player.unRide();
        serverLevel.removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        player.getAdvancements().stopListening();
        this.players.remove(player);
        this.server.getCustomBossEvents().onPlayerDisconnect(player);
        UUID uUID = player.getUUID();
        ServerPlayer serverPlayer = this.playersByUUID.get(uUID);
        if (serverPlayer == player) {
            this.playersByUUID.remove(uUID);
            this.stats.remove(uUID);
            this.advancements.remove(uUID);
        }

        this.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
    }

    @Nullable
    public Component canPlayerLogin(SocketAddress socketAddress, GameProfile gameProfile) {
        if (this.bans.isBanned(gameProfile)) {
            UserBanListEntry userBanListEntry = this.bans.get(gameProfile);
            MutableComponent mutableComponent = Component.translatable("multiplayer.disconnect.banned.reason", userBanListEntry.getReason());
            if (userBanListEntry.getExpires() != null) {
                mutableComponent.append(
                    Component.translatable("multiplayer.disconnect.banned.expiration", BAN_DATE_FORMAT.format(userBanListEntry.getExpires()))
                );
            }

            return mutableComponent;
        } else {
            if (!this.isWhiteListed(gameProfile)) {
                return Component.translatable("multiplayer.disconnect.not_whitelisted");
            }

            if (this.ipBans.isBanned(socketAddress)) {
                IpBanListEntry ipBanListEntry = this.ipBans.get(socketAddress);
                MutableComponent mutableComponent = Component.translatable("multiplayer.disconnect.banned_ip.reason", ipBanListEntry.getReason());
                if (ipBanListEntry.getExpires() != null) {
                    mutableComponent.append(
                        Component.translatable("multiplayer.disconnect.banned_ip.expiration", BAN_DATE_FORMAT.format(ipBanListEntry.getExpires()))
                    );
                }

                return mutableComponent;
            } else {
                return this.players.size() >= this.maxPlayers && !this.canBypassPlayerLimit(gameProfile)
                    ? Component.translatable("multiplayer.disconnect.server_full")
                    : null;
            }
        }
    }

    public ServerPlayer getPlayerForLogin(GameProfile gameProfile, ClientInformation clientInformation) {
        return new ServerPlayer(this.server, this.server.overworld(), gameProfile, clientInformation);
    }

    public boolean disconnectAllPlayersWithProfile(GameProfile gameProfile) {
        UUID uUID = gameProfile.getId();
        Set<ServerPlayer> set = Sets.newIdentityHashSet();

        for (ServerPlayer serverPlayer : this.players) {
            if (serverPlayer.getUUID().equals(uUID)) {
                set.add(serverPlayer);
            }
        }

        ServerPlayer serverPlayer2 = this.playersByUUID.get(gameProfile.getId());
        if (serverPlayer2 != null) {
            set.add(serverPlayer2);
        }

        for (ServerPlayer serverPlayer3 : set) {
            serverPlayer3.connection.disconnect(DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
        }

        return !set.isEmpty();
    }

    public ServerPlayer respawn(ServerPlayer player, boolean keepInventory, Entity.RemovalReason reason) {
        this.players.remove(player);
        player.serverLevel().removePlayerImmediately(player, reason);
        DimensionTransition dimensionTransition = player.findRespawnPositionAndUseSpawnBlock(keepInventory, DimensionTransition.DO_NOTHING);
        ServerLevel serverLevel = dimensionTransition.newLevel();
        ServerPlayer serverPlayer = new ServerPlayer(this.server, serverLevel, player.getGameProfile(), player.clientInformation());
        serverPlayer.connection = player.connection;
        serverPlayer.restoreFrom(player, keepInventory);
        serverPlayer.setId(player.getId());
        serverPlayer.setMainArm(player.getMainArm());
        if (!dimensionTransition.missingRespawnBlock()) {
            serverPlayer.copyRespawnPosition(player);
        }

        for (String string : player.getTags()) {
            serverPlayer.addTag(string);
        }

        Vec3 vec3 = dimensionTransition.pos();
        serverPlayer.moveTo(vec3.x, vec3.y, vec3.z, dimensionTransition.yRot(), dimensionTransition.xRot());
        if (dimensionTransition.missingRespawnBlock()) {
            serverPlayer.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
        }

        byte b = (byte)(keepInventory ? 1 : 0);
        ServerLevel serverLevel2 = serverPlayer.serverLevel();
        LevelData levelData = serverLevel2.getLevelData();
        serverPlayer.connection.send(new ClientboundRespawnPacket(serverPlayer.createCommonSpawnInfo(serverLevel2), b));
        serverPlayer.connection.teleport(serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), serverPlayer.getYRot(), serverPlayer.getXRot());
        serverPlayer.connection.send(new ClientboundSetDefaultSpawnPositionPacket(serverLevel.getSharedSpawnPos(), serverLevel.getSharedSpawnAngle()));
        serverPlayer.connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
        serverPlayer.connection
            .send(new ClientboundSetExperiencePacket(serverPlayer.experienceProgress, serverPlayer.totalExperience, serverPlayer.experienceLevel));
        this.sendActivePlayerEffects(serverPlayer);
        this.sendLevelInfo(serverPlayer, serverLevel);
        this.sendPlayerPermissionLevel(serverPlayer);
        serverLevel.addRespawnedPlayer(serverPlayer);
        this.players.add(serverPlayer);
        this.playersByUUID.put(serverPlayer.getUUID(), serverPlayer);
        serverPlayer.initInventoryMenu();
        serverPlayer.setHealth(serverPlayer.getHealth());
        if (!keepInventory) {
            BlockPos blockPos = BlockPos.containing(dimensionTransition.pos());
            BlockState blockState = serverLevel.getBlockState(blockPos);
            if (blockState.is(Blocks.RESPAWN_ANCHOR)) {
                serverPlayer.connection
                    .send(
                        new ClientboundSoundPacket(
                            SoundEvents.RESPAWN_ANCHOR_DEPLETE,
                            SoundSource.BLOCKS,
                            blockPos.getX(),
                            blockPos.getY(),
                            blockPos.getZ(),
                            1.0F,
                            1.0F,
                            serverLevel.getRandom().nextLong()
                        )
                    );
            }
        }

        return serverPlayer;
    }

    public void sendActivePlayerEffects(ServerPlayer player) {
        this.sendActiveEffects(player, player.connection);
    }

    public void sendActiveEffects(LivingEntity entity, ServerGamePacketListenerImpl connection) {
        for (MobEffectInstance mobEffectInstance : entity.getActiveEffects()) {
            connection.send(new ClientboundUpdateMobEffectPacket(entity.getId(), mobEffectInstance, false));
        }
    }

    public void sendPlayerPermissionLevel(ServerPlayer player) {
        GameProfile gameProfile = player.getGameProfile();
        int i = this.server.getProfilePermissions(gameProfile);
        this.sendPlayerPermissionLevel(player, i);
    }

    public void tick() {
        if (++this.sendAllPlayerInfoIn > 600) {
            this.broadcastAll(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), this.players));
            this.sendAllPlayerInfoIn = 0;
        }
    }

    public void broadcastAll(Packet<?> packet) {
        for (ServerPlayer serverPlayer : this.players) {
            serverPlayer.connection.send(packet);
        }
    }

    public void broadcastAll(Packet<?> packet, ResourceKey<Level> dimension) {
        for (ServerPlayer serverPlayer : this.players) {
            if (serverPlayer.level().dimension() == dimension) {
                serverPlayer.connection.send(packet);
            }
        }
    }

    public void broadcastSystemToTeam(Player player, Component message) {
        Team team = player.getTeam();
        if (team != null) {
            for (String string : team.getPlayers()) {
                ServerPlayer serverPlayer = this.getPlayerByName(string);
                if (serverPlayer != null && serverPlayer != player) {
                    serverPlayer.sendSystemMessage(message);
                }
            }
        }
    }

    public void broadcastSystemToAllExceptTeam(Player player, Component message) {
        Team team = player.getTeam();
        if (team == null) {
            this.broadcastSystemMessage(message, false);
        } else {
            for (int i = 0; i < this.players.size(); i++) {
                ServerPlayer serverPlayer = this.players.get(i);
                if (serverPlayer.getTeam() != team) {
                    serverPlayer.sendSystemMessage(message);
                }
            }
        }
    }

    public String[] getPlayerNamesArray() {
        String[] strings = new String[this.players.size()];

        for (int i = 0; i < this.players.size(); i++) {
            strings[i] = this.players.get(i).getGameProfile().getName();
        }

        return strings;
    }

    public UserBanList getBans() {
        return this.bans;
    }

    public IpBanList getIpBans() {
        return this.ipBans;
    }

    public void op(GameProfile profile) {
        this.ops.add(new ServerOpListEntry(profile, this.server.getOperatorUserPermissionLevel(), this.ops.canBypassPlayerLimit(profile)));
        ServerPlayer serverPlayer = this.getPlayer(profile.getId());
        if (serverPlayer != null) {
            this.sendPlayerPermissionLevel(serverPlayer);
        }
    }

    public void deop(GameProfile profile) {
        this.ops.remove(profile);
        ServerPlayer serverPlayer = this.getPlayer(profile.getId());
        if (serverPlayer != null) {
            this.sendPlayerPermissionLevel(serverPlayer);
        }
    }

    private void sendPlayerPermissionLevel(ServerPlayer player, int permLevel) {
        if (player.connection != null) {
            byte b;
            if (permLevel <= 0) {
                b = 24;
            } else if (permLevel >= 4) {
                b = 28;
            } else {
                b = (byte)(24 + permLevel);
            }

            player.connection.send(new ClientboundEntityEventPacket(player, b));
        }

        this.server.getCommands().sendCommands(player);
    }

    public boolean isWhiteListed(GameProfile profile) {
        return !this.doWhiteList || this.ops.contains(profile) || this.whitelist.contains(profile);
    }

    public boolean isOp(GameProfile profile) {
        return this.ops.contains(profile)
            || this.server.isSingleplayerOwner(profile) && this.server.getWorldData().isAllowCommands()
            || this.allowCommandsForAllPlayers;
    }

    @Nullable
    public ServerPlayer getPlayerByName(String username) {
        int i = this.players.size();

        for (int j = 0; j < i; j++) {
            ServerPlayer serverPlayer = this.players.get(j);
            if (serverPlayer.getGameProfile().getName().equalsIgnoreCase(username)) {
                return serverPlayer;
            }
        }

        return null;
    }

    public void broadcast(@Nullable Player except, double x, double y, double z, double radius, ResourceKey<Level> dimension, Packet<?> packet) {
        for (int i = 0; i < this.players.size(); i++) {
            ServerPlayer serverPlayer = this.players.get(i);
            if (serverPlayer != except && serverPlayer.level().dimension() == dimension) {
                double d = x - serverPlayer.getX();
                double e = y - serverPlayer.getY();
                double f = z - serverPlayer.getZ();
                if (d * d + e * e + f * f < radius * radius) {
                    serverPlayer.connection.send(packet);
                }
            }
        }
    }

    public void saveAll() {
        for (int i = 0; i < this.players.size(); i++) {
            this.save(this.players.get(i));
        }
    }

    public UserWhiteList getWhiteList() {
        return this.whitelist;
    }

    public String[] getWhiteListNames() {
        return this.whitelist.getUserList();
    }

    public ServerOpList getOps() {
        return this.ops;
    }

    public String[] getOpNames() {
        return this.ops.getUserList();
    }

    public void reloadWhiteList() {
    }

    /**
     * Updates the time and weather for the given player to those of the given world
     */
    public void sendLevelInfo(ServerPlayer player, ServerLevel level) {
        WorldBorder worldBorder = this.server.overworld().getWorldBorder();
        player.connection.send(new ClientboundInitializeBorderPacket(worldBorder));
        player.connection.send(new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
        player.connection.send(new ClientboundSetDefaultSpawnPositionPacket(level.getSharedSpawnPos(), level.getSharedSpawnAngle()));
        if (level.isRaining()) {
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0F));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level.getRainLevel(1.0F)));
            player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1.0F)));
        }

        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
        this.server.tickRateManager().updateJoiningPlayer(player);
    }

    /**
     * Sends the players inventory to himself.
     */
    public void sendAllPlayerInfo(ServerPlayer player) {
        player.inventoryMenu.sendAllDataToRemote();
        player.resetSentInfo();
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public boolean isUsingWhitelist() {
        return this.doWhiteList;
    }

    public void setUsingWhiteList(boolean whitelistEnabled) {
        this.doWhiteList = whitelistEnabled;
    }

    public List<ServerPlayer> getPlayersWithAddress(String address) {
        List<ServerPlayer> list = Lists.newArrayList();

        for (ServerPlayer serverPlayer : this.players) {
            if (serverPlayer.getIpAddress().equals(address)) {
                list.add(serverPlayer);
            }
        }

        return list;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public int getSimulationDistance() {
        return this.simulationDistance;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    @Nullable
    public CompoundTag getSingleplayerData() {
        return null;
    }

    public void setAllowCommandsForAllPlayers(boolean allowCommandsForAllPlayers) {
        this.allowCommandsForAllPlayers = allowCommandsForAllPlayers;
    }

    public void removeAll() {
        for (int i = 0; i < this.players.size(); i++) {
            this.players.get(i).connection.disconnect(Component.translatable("multiplayer.disconnect.server_shutdown"));
        }
    }

    public void broadcastSystemMessage(Component message, boolean bypassHiddenChat) {
        this.broadcastSystemMessage(message, serverPlayer -> message, bypassHiddenChat);
    }

    public void broadcastSystemMessage(Component serverMessage, Function<ServerPlayer, Component> playerMessageFactory, boolean bypassHiddenChat) {
        this.server.sendSystemMessage(serverMessage);

        for (ServerPlayer serverPlayer : this.players) {
            Component component = playerMessageFactory.apply(serverPlayer);
            if (component != null) {
                serverPlayer.sendSystemMessage(component, bypassHiddenChat);
            }
        }
    }

    public void broadcastChatMessage(PlayerChatMessage message, CommandSourceStack sender, ChatType.Bound boundChatType) {
        this.broadcastChatMessage(message, sender::shouldFilterMessageTo, sender.getPlayer(), boundChatType);
    }

    public void broadcastChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound boundChatType) {
        this.broadcastChatMessage(message, sender::shouldFilterMessageTo, sender, boundChatType);
    }

    private void broadcastChatMessage(
        PlayerChatMessage message, Predicate<ServerPlayer> shouldFilterMessageTo, @Nullable ServerPlayer sender, ChatType.Bound boundChatType
    ) {
        boolean bl = this.verifyChatTrusted(message);
        this.server.logChatMessage(message.decoratedContent(), boundChatType, bl ? null : "Not Secure");
        OutgoingChatMessage outgoingChatMessage = OutgoingChatMessage.create(message);
        boolean bl2 = false;

        for (ServerPlayer serverPlayer : this.players) {
            boolean bl3 = shouldFilterMessageTo.test(serverPlayer);
            serverPlayer.sendChatMessage(outgoingChatMessage, bl3, boundChatType);
            bl2 |= bl3 && message.isFullyFiltered();
        }

        if (bl2 && sender != null) {
            sender.sendSystemMessage(CHAT_FILTERED_FULL);
        }
    }

    private boolean verifyChatTrusted(PlayerChatMessage message) {
        return message.hasSignature() && !message.hasExpiredServer(Instant.now());
    }

    public ServerStatsCounter getPlayerStats(Player player) {
        UUID uUID = player.getUUID();
        ServerStatsCounter serverStatsCounter = this.stats.get(uUID);
        if (serverStatsCounter == null) {
            File file = this.server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
            File file2 = new File(file, uUID + ".json");
            if (!file2.exists()) {
                File file3 = new File(file, player.getName().getString() + ".json");
                Path path = file3.toPath();
                if (FileUtil.isPathNormalized(path) && FileUtil.isPathPortable(path) && path.startsWith(file.getPath()) && file3.isFile()) {
                    file3.renameTo(file2);
                }
            }

            serverStatsCounter = new ServerStatsCounter(this.server, file2);
            this.stats.put(uUID, serverStatsCounter);
        }

        return serverStatsCounter;
    }

    public PlayerAdvancements getPlayerAdvancements(ServerPlayer player) {
        UUID uUID = player.getUUID();
        PlayerAdvancements playerAdvancements = this.advancements.get(uUID);
        if (playerAdvancements == null) {
            Path path = this.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(uUID + ".json");
            playerAdvancements = new PlayerAdvancements(this.server.getFixerUpper(), this, this.server.getAdvancements(), path, player);
            this.advancements.put(uUID, playerAdvancements);
        }

        playerAdvancements.setPlayer(player);
        return playerAdvancements;
    }

    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
        this.broadcastAll(new ClientboundSetChunkCacheRadiusPacket(viewDistance));

        for (ServerLevel serverLevel : this.server.getAllLevels()) {
            if (serverLevel != null) {
                serverLevel.getChunkSource().setViewDistance(viewDistance);
            }
        }
    }

    public void setSimulationDistance(int simulationDistance) {
        this.simulationDistance = simulationDistance;
        this.broadcastAll(new ClientboundSetSimulationDistancePacket(simulationDistance));

        for (ServerLevel serverLevel : this.server.getAllLevels()) {
            if (serverLevel != null) {
                serverLevel.getChunkSource().setSimulationDistance(simulationDistance);
            }
        }
    }

    public List<ServerPlayer> getPlayers() {
        return this.players;
    }

    /**
     * Gets the ServerPlayer object representing the player with the UUID.
     */
    @Nullable
    public ServerPlayer getPlayer(UUID playerUUID) {
        return this.playersByUUID.get(playerUUID);
    }

    public boolean canBypassPlayerLimit(GameProfile profile) {
        return false;
    }

    public void reloadResources() {
        for (PlayerAdvancements playerAdvancements : this.advancements.values()) {
            playerAdvancements.reload(this.server.getAdvancements());
        }

        this.broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
        ClientboundUpdateRecipesPacket clientboundUpdateRecipesPacket = new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getOrderedRecipes());

        for (ServerPlayer serverPlayer : this.players) {
            serverPlayer.connection.send(clientboundUpdateRecipesPacket);
            serverPlayer.getRecipeBook().sendInitialRecipeBook(serverPlayer);
        }
    }

    public boolean isAllowCommandsForAllPlayers() {
        return this.allowCommandsForAllPlayers;
    }
}
