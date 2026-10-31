package net.minecraft.client.telemetry;

import java.util.function.Consumer;

@FunctionalInterface
public interface TelemetryEventSender {
    TelemetryEventSender DISABLED = (telemetryEventType, consumer) -> {};

    default TelemetryEventSender decorate(Consumer<TelemetryPropertyMap.Builder> consumer) {
        return (telemetryEventType, consumer2) -> this.send(telemetryEventType, builder -> {
            consumer2.accept(builder);
            consumer.accept(builder);
        });
    }

    void send(TelemetryEventType var1, Consumer<TelemetryPropertyMap.Builder> var2);
}
