package net.minecraft.client.multiplayer;

import java.util.EnumMap;
import net.minecraft.Util;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.network.protocol.game.ServerboundDebugSampleSubscriptionPacket;
import net.minecraft.util.debugchart.RemoteDebugSampleType;

public class DebugSampleSubscriber {
    public static final int REQUEST_INTERVAL_MS = 5000;
    private final ClientPacketListener connection;
    private final DebugScreenOverlay debugScreenOverlay;
    private final EnumMap<RemoteDebugSampleType, Long> lastRequested;

    public DebugSampleSubscriber(ClientPacketListener connection, DebugScreenOverlay debugScreenOverlay) {
        this.debugScreenOverlay = debugScreenOverlay;
        this.connection = connection;
        this.lastRequested = new EnumMap<>(RemoteDebugSampleType.class);
    }

    public void tick() {
        if (this.debugScreenOverlay.showFpsCharts()) {
            this.sendSubscriptionRequestIfNeeded(RemoteDebugSampleType.TICK_TIME);
        }
    }

    private void sendSubscriptionRequestIfNeeded(RemoteDebugSampleType sampleType) {
        long l = Util.getMillis();
        if (l > this.lastRequested.getOrDefault(sampleType, 0L) + 5000L) {
            this.connection.send(new ServerboundDebugSampleSubscriptionPacket(sampleType));
            this.lastRequested.put(sampleType, l);
        }
    }
}
