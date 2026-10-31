package net.minecraft.realms;

import com.google.common.util.concurrent.RateLimiter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.GameNarrator;
import net.minecraft.network.chat.Component;

public class RepeatedNarrator {
    private final float permitsPerSecond;
    private final AtomicReference<RepeatedNarrator.Params> params = new AtomicReference<>();

    public RepeatedNarrator(Duration duration) {
        this.permitsPerSecond = 1000.0F / (float)duration.toMillis();
    }

    public void narrate(GameNarrator narrator, Component narration) {
        RepeatedNarrator.Params params = this.params
            .updateAndGet(
                paramsx -> paramsx != null && narration.equals(paramsx.narration)
                    ? paramsx
                    : new RepeatedNarrator.Params(narration, RateLimiter.create(this.permitsPerSecond))
            );
        if (params.rateLimiter.tryAcquire(1)) {
            narrator.sayNow(narration);
        }
    }

    static class Params {
        final Component narration;
        final RateLimiter rateLimiter;

        Params(Component narration, RateLimiter rateLimiter) {
            this.narration = narration;
            this.rateLimiter = rateLimiter;
        }
    }
}
