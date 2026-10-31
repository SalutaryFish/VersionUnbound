package net.minecraft.client.telemetry.events;

import com.google.common.base.Stopwatch;
import com.google.common.base.Ticker;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.minecraft.client.telemetry.TelemetryEventType;
import net.minecraft.client.telemetry.TelemetryProperty;
import org.slf4j.Logger;

public class GameLoadTimesEvent {
    public static final GameLoadTimesEvent INSTANCE = new GameLoadTimesEvent(Ticker.systemTicker());
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Ticker timeSource;
    private final Map<TelemetryProperty<GameLoadTimesEvent.Measurement>, Stopwatch> measurements = new HashMap<>();
    private OptionalLong bootstrapTime = OptionalLong.empty();

    protected GameLoadTimesEvent(Ticker timeSource) {
        this.timeSource = timeSource;
    }

    public synchronized void beginStep(TelemetryProperty<GameLoadTimesEvent.Measurement> measurement) {
        this.beginStep(measurement, telemetryProperty -> Stopwatch.createStarted(this.timeSource));
    }

    public synchronized void beginStep(TelemetryProperty<GameLoadTimesEvent.Measurement> measurement, Stopwatch stopwatch) {
        this.beginStep(measurement, telemetryProperty -> stopwatch);
    }

    private synchronized void beginStep(
        TelemetryProperty<GameLoadTimesEvent.Measurement> measurement, Function<TelemetryProperty<GameLoadTimesEvent.Measurement>, Stopwatch> stopwatchGetter
    ) {
        this.measurements.computeIfAbsent(measurement, stopwatchGetter);
    }

    public synchronized void endStep(TelemetryProperty<GameLoadTimesEvent.Measurement> measurement) {
        Stopwatch stopwatch = this.measurements.get(measurement);
        if (stopwatch == null) {
            LOGGER.warn("Attempted to end step for {} before starting it", measurement.id());
        } else {
            if (stopwatch.isRunning()) {
                stopwatch.stop();
            }
        }
    }

    public void send(TelemetryEventSender sender) {
        sender.send(
            TelemetryEventType.GAME_LOAD_TIMES,
            builder -> {
                synchronized (this) {
                    this.measurements
                        .forEach(
                            (telemetryProperty, stopwatch) -> {
                                if (!stopwatch.isRunning()) {
                                    long l = stopwatch.elapsed(TimeUnit.MILLISECONDS);
                                    builder.put(
                                        (TelemetryProperty<GameLoadTimesEvent.Measurement>)telemetryProperty, new GameLoadTimesEvent.Measurement((int)l)
                                    );
                                } else {
                                    LOGGER.warn(
                                        "Measurement {} was discarded since it was still ongoing when the event {} was sent.",
                                        telemetryProperty.id(),
                                        TelemetryEventType.GAME_LOAD_TIMES.id()
                                    );
                                }
                            }
                        );
                    this.bootstrapTime.ifPresent(l -> builder.put(TelemetryProperty.LOAD_TIME_BOOTSTRAP_MS, new GameLoadTimesEvent.Measurement((int)l)));
                    this.measurements.clear();
                }
            }
        );
    }

    public synchronized void setBootstrapTime(long bootstrapTime) {
        this.bootstrapTime = OptionalLong.of(bootstrapTime);
    }

    public record Measurement(int millis) {
        public static final Codec<GameLoadTimesEvent.Measurement> CODEC = Codec.INT
            .xmap(GameLoadTimesEvent.Measurement::new, measurement -> measurement.millis);
    }
}
