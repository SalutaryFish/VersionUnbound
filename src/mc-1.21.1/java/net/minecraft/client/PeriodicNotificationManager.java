package net.minecraft.client;

import com.google.common.collect.ImmutableMap;
import com.google.common.math.LongMath;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2BooleanFunction;
import java.io.Reader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class PeriodicNotificationManager
    extends SimplePreparableReloadListener<Map<String, List<PeriodicNotificationManager.Notification>>>
    implements AutoCloseable {
    private static final Codec<Map<String, List<PeriodicNotificationManager.Notification>>> CODEC = Codec.unboundedMap(
        Codec.STRING,
        RecordCodecBuilder.<PeriodicNotificationManager.Notification>create(
                instance -> instance.group(
                        Codec.LONG.optionalFieldOf("delay", 0L).forGetter(PeriodicNotificationManager.Notification::delay),
                        Codec.LONG.fieldOf("period").forGetter(PeriodicNotificationManager.Notification::period),
                        Codec.STRING.fieldOf("title").forGetter(PeriodicNotificationManager.Notification::title),
                        Codec.STRING.fieldOf("message").forGetter(PeriodicNotificationManager.Notification::message)
                    )
                    .apply(instance, PeriodicNotificationManager.Notification::new)
            )
            .listOf()
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ResourceLocation notifications;
    private final Object2BooleanFunction<String> selector;
    @Nullable
    private Timer timer;
    @Nullable
    private PeriodicNotificationManager.NotificationTask notificationTask;

    public PeriodicNotificationManager(ResourceLocation notifications, Object2BooleanFunction<String> selector) {
        this.notifications = notifications;
        this.selector = selector;
    }

    /**
     * Performs any reloading that can be done off-thread, such as file IO
     */
    protected Map<String, List<PeriodicNotificationManager.Notification>> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        try (Reader reader = resourceManager.openAsReader(this.notifications)) {
            return CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).result().orElseThrow();
        } catch (Exception exception) {
            LOGGER.warn("Failed to load {}", this.notifications, exception);
            return ImmutableMap.of();
        }
    }

    protected void apply(Map<String, List<PeriodicNotificationManager.Notification>> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<PeriodicNotificationManager.Notification> list = object.entrySet()
            .stream()
            .filter(notification -> this.selector.apply(notification.getKey()))
            .map(Entry::getValue)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
        if (list.isEmpty()) {
            this.stopTimer();
        } else if (list.stream().anyMatch(notification -> notification.period == 0L)) {
            Util.logAndPauseIfInIde("A periodic notification in " + this.notifications + " has a period of zero minutes");
            this.stopTimer();
        } else {
            long l = this.calculateInitialDelay(list);
            long m = this.calculateOptimalPeriod(list, l);
            if (this.timer == null) {
                this.timer = new Timer();
            }

            if (this.notificationTask == null) {
                this.notificationTask = new PeriodicNotificationManager.NotificationTask(list, l, m);
            } else {
                this.notificationTask = this.notificationTask.reset(list, m);
            }

            this.timer.scheduleAtFixedRate(this.notificationTask, TimeUnit.MINUTES.toMillis(l), TimeUnit.MINUTES.toMillis(m));
        }
    }

    @Override
    public void close() {
        this.stopTimer();
    }

    private void stopTimer() {
        if (this.timer != null) {
            this.timer.cancel();
        }
    }

    private long calculateOptimalPeriod(List<PeriodicNotificationManager.Notification> notifications, long delay) {
        return notifications.stream().mapToLong(notification -> {
            long m = notification.delay - delay;
            return LongMath.gcd(m, notification.period);
        }).reduce(LongMath::gcd).orElseThrow(() -> new IllegalStateException("Empty notifications from: " + this.notifications));
    }

    private long calculateInitialDelay(List<PeriodicNotificationManager.Notification> notifications) {
        return notifications.stream().mapToLong(notification -> notification.delay).min().orElse(0L);
    }

    public record Notification(long delay, long period, String title, String message) {
    }

    static class NotificationTask extends TimerTask {
        private final Minecraft minecraft = Minecraft.getInstance();
        private final List<PeriodicNotificationManager.Notification> notifications;
        private final long period;
        private final AtomicLong elapsed;

        public NotificationTask(List<PeriodicNotificationManager.Notification> notifications, long elapsed, long period) {
            this.notifications = notifications;
            this.period = period;
            this.elapsed = new AtomicLong(elapsed);
        }

        public PeriodicNotificationManager.NotificationTask reset(List<PeriodicNotificationManager.Notification> notifications, long period) {
            this.cancel();
            return new PeriodicNotificationManager.NotificationTask(notifications, this.elapsed.get(), period);
        }

        @Override
        public void run() {
            long l = this.elapsed.getAndAdd(this.period);
            long m = this.elapsed.get();

            for (PeriodicNotificationManager.Notification notification : this.notifications) {
                if (l >= notification.delay) {
                    long n = l / notification.period;
                    long o = m / notification.period;
                    if (n != o) {
                        this.minecraft
                            .execute(
                                () -> SystemToast.add(
                                    Minecraft.getInstance().getToasts(),
                                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                                    Component.translatable(notification.title, n),
                                    Component.translatable(notification.message, n)
                                )
                            );
                        return;
                    }
                }
            }
        }
    }
}
