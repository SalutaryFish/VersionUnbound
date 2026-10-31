package net.minecraft.client.telemetry;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.concurrent.Executor;
import net.minecraft.util.eventlog.JsonEventLog;
import net.minecraft.util.thread.ProcessorMailbox;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class TelemetryEventLog implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final JsonEventLog<TelemetryEventInstance> log;
    private final ProcessorMailbox<Runnable> mailbox;

    public TelemetryEventLog(FileChannel channel, Executor dispatcher) {
        this.log = new JsonEventLog<>(TelemetryEventInstance.CODEC, channel);
        this.mailbox = ProcessorMailbox.create(dispatcher, "telemetry-event-log");
    }

    public TelemetryEventLogger logger() {
        return telemetryEventInstance -> this.mailbox.tell(() -> {
            try {
                this.log.write(telemetryEventInstance);
            } catch (IOException iOException) {
                LOGGER.error("Failed to write telemetry event to log", iOException);
            }
        });
    }

    @Override
    public void close() {
        this.mailbox.tell(() -> IOUtils.closeQuietly(this.log));
        this.mailbox.close();
    }
}
