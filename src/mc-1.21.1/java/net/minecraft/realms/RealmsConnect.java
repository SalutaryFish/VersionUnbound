package net.minecraft.realms;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.dto.RealmsServer;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.quickplay.QuickPlayLog;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import org.slf4j.Logger;

public class RealmsConnect {
    static final Logger LOGGER = LogUtils.getLogger();
    final Screen onlineScreen;
    volatile boolean aborted;
    @Nullable
    Connection connection;

    public RealmsConnect(Screen onlineScreen) {
        this.onlineScreen = onlineScreen;
    }

    public void connect(RealmsServer server, ServerAddress address) {
        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.prepareForMultiplayer();
        minecraft.getNarrator().sayNow(Component.translatable("mco.connect.success"));
        final String string = address.getHost();
        final int i = address.getPort();
        (new Thread("Realms-connect-task") {
                @Override
                public void run() {
                    InetSocketAddress inetSocketAddress = null;

                    try {
                        inetSocketAddress = new InetSocketAddress(string, i);
                        if (RealmsConnect.this.aborted) {
                            return;
                        }

                        RealmsConnect.this.connection = Connection.connectToServer(
                            inetSocketAddress, minecraft.options.useNativeTransport(), minecraft.getDebugOverlay().getBandwidthLogger()
                        );
                        if (RealmsConnect.this.aborted) {
                            return;
                        }

                        ClientHandshakePacketListenerImpl clientHandshakePacketListenerImpl = new ClientHandshakePacketListenerImpl(
                            RealmsConnect.this.connection,
                            minecraft,
                            server.toServerData(string),
                            RealmsConnect.this.onlineScreen,
                            false,
                            null,
                            component -> {},
                            null
                        );
                        if (server.isMinigameActive()) {
                            clientHandshakePacketListenerImpl.setMinigameName(server.minigameName);
                        }

                        if (RealmsConnect.this.aborted) {
                            return;
                        }

                        RealmsConnect.this.connection.initiateServerboundPlayConnection(string, i, clientHandshakePacketListenerImpl);
                        if (RealmsConnect.this.aborted) {
                            return;
                        }

                        RealmsConnect.this.connection.send(new ServerboundHelloPacket(minecraft.getUser().getName(), minecraft.getUser().getProfileId()));
                        minecraft.updateReportEnvironment(ReportEnvironment.realm(server));
                        minecraft.quickPlayLog().setWorldData(QuickPlayLog.Type.REALMS, String.valueOf(server.id), server.name);
                        minecraft.getDownloadedPackSource()
                            .configureForServerControl(RealmsConnect.this.connection, ServerPackManager.PackPromptStatus.ALLOWED);
                    } catch (Exception exception) {
                        minecraft.getDownloadedPackSource().cleanupAfterDisconnect();
                        if (RealmsConnect.this.aborted) {
                            return;
                        }

                        RealmsConnect.LOGGER.error("Couldn't connect to world", exception);
                        String stringx = exception.toString();
                        if (inetSocketAddress != null) {
                            String string2 = inetSocketAddress + ":" + i;
                            stringx = stringx.replaceAll(string2, "");
                        }

                        DisconnectedRealmsScreen disconnectedRealmsScreen = new DisconnectedRealmsScreen(
                            RealmsConnect.this.onlineScreen, CommonComponents.CONNECT_FAILED, Component.translatable("disconnect.genericReason", stringx)
                        );
                        minecraft.execute(() -> minecraft.setScreen(disconnectedRealmsScreen));
                    }
                }
            })
            .start();
    }

    public void abort() {
        this.aborted = true;
        if (this.connection != null && this.connection.isConnected()) {
            this.connection.disconnect(Component.translatable("disconnect.genericReason"));
            this.connection.handleDisconnection();
        }
    }

    public void tick() {
        if (this.connection != null) {
            if (this.connection.isConnected()) {
                this.connection.tick();
            } else {
                this.connection.handleDisconnection();
            }
        }
    }
}
