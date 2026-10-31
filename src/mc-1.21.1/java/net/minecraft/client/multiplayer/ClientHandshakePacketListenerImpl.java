package net.minecraft.client.multiplayer;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.ForcedUsernameChangeException;
import com.mojang.authlib.exceptions.InsufficientPrivilegesException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.exceptions.UserBannedException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.logging.LogUtils;
import java.math.BigInteger;
import java.security.PublicKey;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.Util;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket;
import net.minecraft.realms.DisconnectedRealmsScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerLinks;
import net.minecraft.util.Crypt;
import net.minecraft.world.flag.FeatureFlags;
import org.slf4j.Logger;

public class ClientHandshakePacketListenerImpl implements ClientLoginPacketListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Minecraft minecraft;
    @Nullable
    private final ServerData serverData;
    @Nullable
    private final Screen parent;
    private final Consumer<Component> updateStatus;
    private final Connection connection;
    private final boolean newWorld;
    @Nullable
    private final Duration worldLoadDuration;
    @Nullable
    private String minigameName;
    private final Map<ResourceLocation, byte[]> cookies;
    private final boolean wasTransferredTo;
    private final AtomicReference<ClientHandshakePacketListenerImpl.State> state = new AtomicReference<>(ClientHandshakePacketListenerImpl.State.CONNECTING);

    public ClientHandshakePacketListenerImpl(
        Connection connection,
        Minecraft minecraft,
        @Nullable ServerData serverData,
        @Nullable Screen parent,
        boolean newWorld,
        @Nullable Duration worldLoadDuration,
        Consumer<Component> updateStatus,
        @Nullable TransferState cookies
    ) {
        this.connection = connection;
        this.minecraft = minecraft;
        this.serverData = serverData;
        this.parent = parent;
        this.updateStatus = updateStatus;
        this.newWorld = newWorld;
        this.worldLoadDuration = worldLoadDuration;
        this.cookies = cookies != null ? new HashMap<>(cookies.cookies()) : new HashMap<>();
        this.wasTransferredTo = cookies != null;
    }

    private void switchState(ClientHandshakePacketListenerImpl.State state) {
        ClientHandshakePacketListenerImpl.State state2 = this.state.updateAndGet(originalState -> {
            if (!state.fromStates.contains(originalState)) {
                throw new IllegalStateException("Tried to switch to " + state + " from " + originalState + ", but expected one of " + state.fromStates);
            } else {
                return state;
            }
        });
        this.updateStatus.accept(state2.message);
    }

    @Override
    public void handleHello(ClientboundHelloPacket packet) {
        this.switchState(ClientHandshakePacketListenerImpl.State.AUTHORIZING);

        Cipher cipher;
        Cipher cipher2;
        String string;
        ServerboundKeyPacket serverboundKeyPacket;
        try {
            SecretKey secretKey = Crypt.generateSecretKey();
            PublicKey publicKey = packet.getPublicKey();
            string = new BigInteger(Crypt.digestData(packet.getServerId(), publicKey, secretKey)).toString(16);
            cipher = Crypt.getCipher(2, secretKey);
            cipher2 = Crypt.getCipher(1, secretKey);
            byte[] bs = packet.getChallenge();
            serverboundKeyPacket = new ServerboundKeyPacket(secretKey, publicKey, bs);
        } catch (Exception exception) {
            throw new IllegalStateException("Protocol error", exception);
        }

        if (packet.shouldAuthenticate()) {
            Util.ioPool().submit(() -> {
                Component component = this.authenticateServer(string);
                if (component != null) {
                    if (this.serverData == null || !this.serverData.isLan()) {
                        this.connection.disconnect(component);
                        return;
                    }

                    LOGGER.warn(component.getString());
                }

                this.setEncryption(serverboundKeyPacket, cipher, cipher2);
            });
        } else {
            this.setEncryption(serverboundKeyPacket, cipher, cipher2);
        }
    }

    private void setEncryption(ServerboundKeyPacket keyPacket, Cipher decryptingCypher, Cipher encryptingCypher) {
        this.switchState(ClientHandshakePacketListenerImpl.State.ENCRYPTING);
        this.connection.send(keyPacket, PacketSendListener.thenRun(() -> this.connection.setEncryptionKey(decryptingCypher, encryptingCypher)));
    }

    @Nullable
    private Component authenticateServer(String serverHash) {
        try {
            this.getMinecraftSessionService().joinServer(this.minecraft.getUser().getProfileId(), this.minecraft.getUser().getAccessToken(), serverHash);
            return null;
        } catch (AuthenticationUnavailableException authenticationUnavailableException) {
            return Component.translatable("disconnect.loginFailedInfo", Component.translatable("disconnect.loginFailedInfo.serversUnavailable"));
        } catch (InvalidCredentialsException invalidCredentialsException) {
            return Component.translatable("disconnect.loginFailedInfo", Component.translatable("disconnect.loginFailedInfo.invalidSession"));
        } catch (InsufficientPrivilegesException insufficientPrivilegesException) {
            return Component.translatable("disconnect.loginFailedInfo", Component.translatable("disconnect.loginFailedInfo.insufficientPrivileges"));
        } catch (UserBannedException | ForcedUsernameChangeException authenticationException) {
            return Component.translatable("disconnect.loginFailedInfo", Component.translatable("disconnect.loginFailedInfo.userBanned"));
        } catch (AuthenticationException authenticationException) {
            return Component.translatable("disconnect.loginFailedInfo", authenticationException.getMessage());
        }
    }

    private MinecraftSessionService getMinecraftSessionService() {
        return this.minecraft.getMinecraftSessionService();
    }

    @Override
    public void handleGameProfile(ClientboundGameProfilePacket packet) {
        this.switchState(ClientHandshakePacketListenerImpl.State.JOINING);
        GameProfile gameProfile = packet.gameProfile();
        this.connection
            .setupInboundProtocol(
                ConfigurationProtocols.CLIENTBOUND,
                new ClientConfigurationPacketListenerImpl(
                    this.minecraft,
                    this.connection,
                    new CommonListenerCookie(
                        gameProfile,
                        this.minecraft.getTelemetryManager().createWorldSessionManager(this.newWorld, this.worldLoadDuration, this.minigameName),
                        ClientRegistryLayer.createRegistryAccess().compositeAccess(),
                        FeatureFlags.DEFAULT_FLAGS,
                        null,
                        this.serverData,
                        this.parent,
                        this.cookies,
                        null,
                        packet.strictErrorHandling(),
                        Map.of(),
                        ServerLinks.EMPTY
                    )
                )
            );
        this.connection.send(ServerboundLoginAcknowledgedPacket.INSTANCE);
        this.connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
        this.connection.send(new ServerboundCustomPayloadPacket(new BrandPayload(ClientBrandRetriever.getClientModName())));
        this.connection.send(new ServerboundClientInformationPacket(this.minecraft.options.buildPlayerInformation()));
    }

    @Override
    public void onDisconnect(DisconnectionDetails details) {
        Component component = this.wasTransferredTo ? CommonComponents.TRANSFER_CONNECT_FAILED : CommonComponents.CONNECT_FAILED;
        if (this.serverData != null && this.serverData.isRealm()) {
            this.minecraft.setScreen(new DisconnectedRealmsScreen(this.parent, component, details.reason()));
        } else {
            this.minecraft.setScreen(new DisconnectedScreen(this.parent, component, details));
        }
    }

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected();
    }

    @Override
    public void handleDisconnect(ClientboundLoginDisconnectPacket packet) {
        this.connection.disconnect(packet.getReason());
    }

    @Override
    public void handleCompression(ClientboundLoginCompressionPacket packet) {
        if (!this.connection.isMemoryConnection()) {
            this.connection.setupCompression(packet.getCompressionThreshold(), false);
        }
    }

    @Override
    public void handleCustomQuery(ClientboundCustomQueryPacket packet) {
        this.updateStatus.accept(Component.translatable("connect.negotiating"));
        this.connection.send(new ServerboundCustomQueryAnswerPacket(packet.transactionId(), null));
    }

    public void setMinigameName(@Nullable String minigameName) {
        this.minigameName = minigameName;
    }

    @Override
    public void handleRequestCookie(ClientboundCookieRequestPacket packet) {
        this.connection.send(new ServerboundCookieResponsePacket(packet.key(), this.cookies.get(packet.key())));
    }

    @Override
    public void fillListenerSpecificCrashDetails(CrashReport crashReport, CrashReportCategory category) {
        category.setDetail("Server type", () -> this.serverData != null ? this.serverData.type().toString() : "<unknown>");
        category.setDetail("Login phase", () -> this.state.get().toString());
    }

    enum State {
        CONNECTING(Component.translatable("connect.connecting"), Set.of()),
        AUTHORIZING(Component.translatable("connect.authorizing"), Set.of(CONNECTING)),
        ENCRYPTING(Component.translatable("connect.encrypting"), Set.of(AUTHORIZING)),
        JOINING(Component.translatable("connect.joining"), Set.of(ENCRYPTING, CONNECTING));

        final Component message;
        final Set<ClientHandshakePacketListenerImpl.State> fromStates;

        State(final Component message, final Set<ClientHandshakePacketListenerImpl.State> fromStates) {
            this.message = message;
            this.fromStates = fromStates;
        }
    }
}
