package net.minecraft.client.telemetry;

import com.mojang.authlib.minecraft.TelemetryEvent;
import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.serialization.Codec;

public record TelemetryEventInstance(TelemetryEventType type, TelemetryPropertyMap properties) {
    public static final Codec<TelemetryEventInstance> CODEC = TelemetryEventType.CODEC.dispatchStable(TelemetryEventInstance::type, TelemetryEventType::codec);

    public TelemetryEventInstance {
        properties.propertySet().forEach(telemetryProperty -> {
            if (!telemetryEventType.contains((TelemetryProperty<?>)telemetryProperty)) {
                throw new IllegalArgumentException("Property '" + telemetryProperty.id() + "' not expected for event: '" + telemetryEventType.id() + "'");
            }
        });
    }

    public TelemetryEvent export(TelemetrySession session) {
        return this.type.export(session, this.properties);
    }
}
