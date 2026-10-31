package net.minecraft.client.telemetry.events;

import java.time.Duration;
import javax.annotation.Nullable;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.TelemetryEventType;
import net.minecraft.client.telemetry.TelemetryProperty;

public class WorldLoadTimesEvent {
    private final boolean newWorld;
    @Nullable
    private final Duration worldLoadDuration;

    public WorldLoadTimesEvent(boolean newWorld, @Nullable Duration wordLoadDuration) {
        this.worldLoadDuration = wordLoadDuration;
        this.newWorld = newWorld;
    }

    public void send(TelemetryEventSender sender) {
        if (this.worldLoadDuration != null) {
            sender.send(TelemetryEventType.WORLD_LOAD_TIMES, builder -> {
                builder.put(TelemetryProperty.WORLD_LOAD_TIME_MS, (int)this.worldLoadDuration.toMillis());
                builder.put(TelemetryProperty.NEW_WORLD, this.newWorld);
            });
        }
    }
}
