package net.minecraft.client.multiplayer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundCustomReportDetailsPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundServerLinksPacket;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.realms.DisconnectedRealmsScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerLinks;
import org.slf4j.Logger;

public abstract class ClientCommonPacketListenerImpl implements ClientCommonPacketListener {
    private static final Component GENERIC_DISCONNECT_MESSAGE = Component.translatable("disconnect.lost");
    private static final Logger LOGGER = LogUtils.getLogger();
    protected final Minecraft minecraft;
    protected final Connection connection;
    @Nullable
    protected final ServerData serverData;
    @Nullable
    protected String serverBrand;
    protected final WorldSessionTelemetryManager telemetryManager;
    @Nullable
    protected final Screen postDisconnectScreen;
    protected boolean isTransferring;
    @Deprecated(forRemoval = true)
    protected final boolean strictErrorHandling;
    private final List<ClientCommonPacketListenerImpl.DeferredPacket> deferredPackets = new ArrayList<>();
    protected final Map<ResourceLocation, byte[]> serverCookies;
    protected Map<String, String> customReportDetails;
    protected ServerLinks serverLinks;

    protected ClientCommonPacketListenerImpl(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie) {
        this.minecraft = minecraft;
        this.connection = connection;
        this.serverData = commonListenerCookie.serverData();
        this.serverBrand = commonListenerCookie.serverBrand();
        this.telemetryManager = commonListenerCookie.telemetryManager();
        this.postDisconnectScreen = commonListenerCookie.postDisconnectScreen();
        this.serverCookies = commonListenerCookie.serverCookies();
        this.strictErrorHandling = commonListenerCookie.strictErrorHandling();
        this.customReportDetails = commonListenerCookie.customReportDetails();
        this.serverLinks = commonListenerCookie.serverLinks();
    }

    @Override
    public void onPacketError(Packet packet, Exception exception) {
        LOGGER.error("Failed to handle packet {}", packet, exception);
        Optional<Path> optional = this.storeDisconnectionReport(packet, exception);
        Optional<URI> optional2 = this.serverLinks.findKnownType(ServerLinks.KnownLinkType.BUG_REPORT).map(ServerLinks.Entry::link);
        if (this.strictErrorHandling) {
            this.connection.disconnect(new DisconnectionDetails(Component.translatable("disconnect.packetError"), optional, optional2));
        }
    }

    @Override
    public DisconnectionDetails createDisconnectionInfo(Component reason, Throwable error) {
        Optional<Path> optional = this.storeDisconnectionReport(null, error);
        Optional<URI> optional2 = this.serverLinks.findKnownType(ServerLinks.KnownLinkType.BUG_REPORT).map(ServerLinks.Entry::link);
        return new DisconnectionDetails(reason, optional, optional2);
    }

    private Optional<Path> storeDisconnectionReport(@Nullable Packet packet, Throwable error) {
        CrashReport crashReport = CrashReport.forThrowable(error, "Packet handling error");
        PacketUtils.fillCrashReport(crashReport, this, packet);
        Path path = this.minecraft.gameDirectory.toPath().resolve("debug");
        Path path2 = path.resolve("disconnect-" + Util.getFilenameFormattedDateTime() + "-client.txt");
        Optional<ServerLinks.Entry> optional = this.serverLinks.findKnownType(ServerLinks.KnownLinkType.BUG_REPORT);
        List<String> list = optional.<List<String>>map(entry -> List.of("Server bug reporting link: " + entry.link())).orElse(List.of());
        return crashReport.saveToFile(path2, ReportType.NETWORK_PROTOCOL_ERROR, list) ? Optional.of(path2) : Optional.empty();
    }

    @Override
    public boolean shouldHandleMessage(Packet<?> packet) {
        return ClientCommonPacketListener.super.shouldHandleMessage(packet)
            ? true
            : this.isTransferring && (packet instanceof ClientboundStoreCookiePacket || packet instanceof ClientboundTransferPacket);
    }

    @Override
    public void handleKeepAlive(ClientboundKeepAlivePacket packet) {
        this.sendWhen(new ServerboundKeepAlivePacket(packet.getId()), () -> !RenderSystem.isFrozenAtPollEvents(), Duration.ofMinutes(1L));
    }

    @Override
    public void handlePing(ClientboundPingPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.send(new ServerboundPongPacket(packet.getId()));
    }

    @Override
    public void handleCustomPayload(ClientboundCustomPayloadPacket packet) {
        CustomPacketPayload customPacketPayload = packet.payload();
        if (!(customPacketPayload instanceof DiscardedPayload)) {
            PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
            if (customPacketPayload instanceof BrandPayload brandPayload) {
                this.serverBrand = brandPayload.brand();
                this.telemetryManager.onServerBrandReceived(brandPayload.brand());
            } else {
                this.handleCustomPayload(customPacketPayload);
            }
        }
    }

    protected abstract void handleCustomPayload(CustomPacketPayload var1);

    @Override
    public void handleResourcePackPush(ClientboundResourcePackPushPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        UUID uUID = packet.id();
        URL uRL = parseResourcePackUrl(packet.url());
        if (uRL == null) {
            this.connection.send(new ServerboundResourcePackPacket(uUID, ServerboundResourcePackPacket.Action.INVALID_URL));
        } else {
            String string = packet.hash();
            boolean bl = packet.required();
            ServerData.ServerPackStatus serverPackStatus = this.serverData != null
                ? this.serverData.getResourcePackStatus()
                : ServerData.ServerPackStatus.PROMPT;
            if (serverPackStatus != ServerData.ServerPackStatus.PROMPT && (!bl || serverPackStatus != ServerData.ServerPackStatus.DISABLED)) {
                this.minecraft.getDownloadedPackSource().pushPack(uUID, uRL, string);
            } else {
                this.minecraft.setScreen(this.addOrUpdatePackPrompt(uUID, uRL, string, bl, packet.prompt().orElse(null)));
            }
        }
    }

    @Override
    public void handleResourcePackPop(ClientboundResourcePackPopPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        packet.id().ifPresentOrElse(uUID -> this.minecraft.getDownloadedPackSource().popPack(uUID), () -> this.minecraft.getDownloadedPackSource().popAll());
    }

    static Component preparePackPrompt(Component line1, @Nullable Component line2) {
        return line2 == null ? line1 : Component.translatable("multiplayer.texturePrompt.serverPrompt", line1, line2);
    }

    @Nullable
    private static URL parseResourcePackUrl(String url) {
        try {
            URL uRL = new URL(url);
            String string = uRL.getProtocol();
            return !"http".equals(string) && !"https".equals(string) ? null : uRL;
        } catch (MalformedURLException malformedURLException) {
            return null;
        }
    }

    @Override
    public void handleRequestCookie(ClientboundCookieRequestPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.connection.send(new ServerboundCookieResponsePacket(packet.key(), this.serverCookies.get(packet.key())));
    }

    @Override
    public void handleStoreCookie(ClientboundStoreCookiePacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.serverCookies.put(packet.key(), packet.payload());
    }

    @Override
    public void handleCustomReportDetails(ClientboundCustomReportDetailsPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        this.customReportDetails = packet.details();
    }

    @Override
    public void handleServerLinks(ClientboundServerLinksPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        List<ServerLinks.UntrustedEntry> list = packet.links();
        Builder<ServerLinks.Entry> builder = ImmutableList.builderWithExpectedSize(list.size());

        for (ServerLinks.UntrustedEntry untrustedEntry : list) {
            try {
                URI uRI = Util.parseAndValidateUntrustedUri(untrustedEntry.link());
                builder.add(new ServerLinks.Entry(untrustedEntry.type(), uRI));
            } catch (Exception exception) {
                LOGGER.warn("Received invalid link for type {}:{}", untrustedEntry.type(), untrustedEntry.link(), exception);
            }
        }

        this.serverLinks = new ServerLinks(builder.build());
    }

    @Override
    public void handleTransfer(ClientboundTransferPacket packet) {
        this.isTransferring = true;
        PacketUtils.ensureRunningOnSameThread(packet, this, this.minecraft);
        if (this.serverData == null) {
            throw new IllegalStateException("Cannot transfer to server from singleplayer");
        }

        this.connection.disconnect(Component.translatable("disconnect.transfer"));
        this.connection.setReadOnly();
        this.connection.handleDisconnection();
        ServerAddress serverAddress = new ServerAddress(packet.host(), packet.port());
        ConnectScreen.startConnecting(
            Objects.requireNonNullElseGet(this.postDisconnectScreen, TitleScreen::new),
            this.minecraft,
            serverAddress,
            this.serverData,
            false,
            new TransferState(this.serverCookies)
        );
    }

    @Override
    public void handleDisconnect(ClientboundDisconnectPacket packet) {
        this.connection.disconnect(packet.reason());
    }

    protected void sendDeferredPackets() {
        Iterator<ClientCommonPacketListenerImpl.DeferredPacket> iterator = this.deferredPackets.iterator();

        while (iterator.hasNext()) {
            ClientCommonPacketListenerImpl.DeferredPacket deferredPacket = iterator.next();
            if (deferredPacket.sendCondition().getAsBoolean()) {
                this.send(deferredPacket.packet);
                iterator.remove();
            } else if (deferredPacket.expirationTime() <= Util.getMillis()) {
                iterator.remove();
            }
        }
    }

    public void send(Packet<?> packet) {
        this.connection.send(packet);
    }

    @Override
    public void onDisconnect(DisconnectionDetails details) {
        this.telemetryManager.onDisconnect();
        this.minecraft.disconnect(this.createDisconnectScreen(details), this.isTransferring);
        LOGGER.warn("Client disconnected with reason: {}", details.reason().getString());
    }

    @Override
    public void fillListenerSpecificCrashDetails(CrashReport crashReport, CrashReportCategory category) {
        category.setDetail("Server type", () -> this.serverData != null ? this.serverData.type().toString() : "<none>");
        category.setDetail("Server brand", () -> this.serverBrand);
        if (!this.customReportDetails.isEmpty()) {
            CrashReportCategory crashReportCategory = crashReport.addCategory("Custom Server Details");
            this.customReportDetails.forEach(crashReportCategory::setDetail);
        }
    }

    protected Screen createDisconnectScreen(DisconnectionDetails details) {
        Screen screen = Objects.requireNonNullElseGet(this.postDisconnectScreen, () -> new JoinMultiplayerScreen(new TitleScreen()));
        return this.serverData != null && this.serverData.isRealm()
            ? new DisconnectedRealmsScreen(screen, GENERIC_DISCONNECT_MESSAGE, details.reason())
            : new DisconnectedScreen(screen, GENERIC_DISCONNECT_MESSAGE, details);
    }

    @Nullable
    public String serverBrand() {
        return this.serverBrand;
    }

    private void sendWhen(Packet<? extends ServerboundPacketListener> packet, BooleanSupplier sendCondition, Duration expirationTime) {
        if (sendCondition.getAsBoolean()) {
            this.send(packet);
        } else {
            this.deferredPackets.add(new ClientCommonPacketListenerImpl.DeferredPacket(packet, sendCondition, Util.getMillis() + expirationTime.toMillis()));
        }
    }

    private Screen addOrUpdatePackPrompt(UUID id, URL url, String hash, boolean required, @Nullable Component prompt) {
        Screen screen = this.minecraft.screen;
        return screen instanceof ClientCommonPacketListenerImpl.PackConfirmScreen packConfirmScreen
            ? packConfirmScreen.update(this.minecraft, id, url, hash, required, prompt)
            : new ClientCommonPacketListenerImpl.PackConfirmScreen(
                this.minecraft, screen, List.of(new ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest(id, url, hash)), required, prompt
            );
    }

    record DeferredPacket(Packet<? extends ServerboundPacketListener> packet, BooleanSupplier sendCondition, long expirationTime) {
    }

    class PackConfirmScreen extends ConfirmScreen {
        private final List<ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest> requests;
        @Nullable
        private final Screen parentScreen;

        PackConfirmScreen(
            final Minecraft minecraft,
            @Nullable final Screen parentScreen,
            final List<ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest> requests,
            final boolean required,
            @Nullable final Component prompt
        ) {
            super(
                bl2 -> {
                    minecraft.setScreen(parentScreen);
                    DownloadedPackSource downloadedPackSource = minecraft.getDownloadedPackSource();
                    if (bl2) {
                        if (ClientCommonPacketListenerImpl.this.serverData != null) {
                            ClientCommonPacketListenerImpl.this.serverData.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
                        }

                        downloadedPackSource.allowServerPacks();
                    } else {
                        downloadedPackSource.rejectServerPacks();
                        if (required) {
                            ClientCommonPacketListenerImpl.this.connection.disconnect(Component.translatable("multiplayer.requiredTexturePrompt.disconnect"));
                        } else if (ClientCommonPacketListenerImpl.this.serverData != null) {
                            ClientCommonPacketListenerImpl.this.serverData.setResourcePackStatus(ServerData.ServerPackStatus.DISABLED);
                        }
                    }

                    for (ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest pendingRequest : requests) {
                        downloadedPackSource.pushPack(pendingRequest.id, pendingRequest.url, pendingRequest.hash);
                    }

                    if (ClientCommonPacketListenerImpl.this.serverData != null) {
                        ServerList.saveSingleServer(ClientCommonPacketListenerImpl.this.serverData);
                    }
                },
                required ? Component.translatable("multiplayer.requiredTexturePrompt.line1") : Component.translatable("multiplayer.texturePrompt.line1"),
                ClientCommonPacketListenerImpl.preparePackPrompt(
                    required
                        ? Component.translatable("multiplayer.requiredTexturePrompt.line2").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)
                        : Component.translatable("multiplayer.texturePrompt.line2"),
                    prompt
                ),
                required ? CommonComponents.GUI_PROCEED : CommonComponents.GUI_YES,
                required ? CommonComponents.GUI_DISCONNECT : CommonComponents.GUI_NO
            );
            this.requests = requests;
            this.parentScreen = parentScreen;
        }

        public ClientCommonPacketListenerImpl.PackConfirmScreen update(
            Minecraft minecraft, UUID id, URL url, String hash, boolean required, @Nullable Component prompt
        ) {
            List<ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest> list = ImmutableList.<ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest>builderWithExpectedSize(
                    this.requests.size() + 1
                )
                .addAll(this.requests)
                .add(new ClientCommonPacketListenerImpl.PackConfirmScreen.PendingRequest(id, url, hash))
                .build();
            return ClientCommonPacketListenerImpl.this.new PackConfirmScreen(minecraft, this.parentScreen, list, required, prompt);
        }

        record PendingRequest(UUID id, URL url, String hash) {
        }
    }
}
