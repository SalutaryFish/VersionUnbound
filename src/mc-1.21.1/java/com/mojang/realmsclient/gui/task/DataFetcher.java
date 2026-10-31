package com.mojang.realmsclient.gui.task;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.util.TimeSource;
import org.slf4j.Logger;

public class DataFetcher {
    static final Logger LOGGER = LogUtils.getLogger();
    final Executor executor;
    final TimeUnit resolution;
    final TimeSource timeSource;

    public DataFetcher(Executor executor, TimeUnit resolution, TimeSource timeSource) {
        this.executor = executor;
        this.resolution = resolution;
        this.timeSource = timeSource;
    }

    public <T> DataFetcher.Task<T> createTask(String id, Callable<T> updater, Duration period, RepeatedDelayStrategy repeatStrategy) {
        long l = this.resolution.convert(period);
        if (l == 0L) {
            throw new IllegalArgumentException("Period of " + period + " too short for selected resolution of " + this.resolution);
        } else {
            return new DataFetcher.Task<>(id, updater, l, repeatStrategy);
        }
    }

    public DataFetcher.Subscription createSubscription() {
        return new DataFetcher.Subscription();
    }

    record ComputationResult<T>(Either<T, Exception> value, long time) {
    }

    class SubscribedTask<T> {
        private final DataFetcher.Task<T> task;
        private final Consumer<T> output;
        private long lastCheckTime = -1L;

        SubscribedTask(final DataFetcher.Task<T> task, final Consumer<T> output) {
            this.task = task;
            this.output = output;
        }

        void update(long time) {
            this.task.updateIfNeeded(time);
            this.runCallbackIfNeeded();
        }

        void runCallbackIfNeeded() {
            DataFetcher.SuccessfulComputationResult<T> successfulComputationResult = this.task.lastResult;
            if (successfulComputationResult != null && this.lastCheckTime < successfulComputationResult.time) {
                this.output.accept(successfulComputationResult.value);
                this.lastCheckTime = successfulComputationResult.time;
            }
        }

        void runCallback() {
            DataFetcher.SuccessfulComputationResult<T> successfulComputationResult = this.task.lastResult;
            if (successfulComputationResult != null) {
                this.output.accept(successfulComputationResult.value);
                this.lastCheckTime = successfulComputationResult.time;
            }
        }

        void reset() {
            this.task.reset();
            this.lastCheckTime = -1L;
        }
    }

    public class Subscription {
        private final List<DataFetcher.SubscribedTask<?>> subscriptions = new ArrayList<>();

        public <T> void subscribe(DataFetcher.Task<T> task, Consumer<T> output) {
            DataFetcher.SubscribedTask<T> subscribedTask = DataFetcher.this.new SubscribedTask<>(task, output);
            this.subscriptions.add(subscribedTask);
            subscribedTask.runCallbackIfNeeded();
        }

        public void forceUpdate() {
            for (DataFetcher.SubscribedTask<?> subscribedTask : this.subscriptions) {
                subscribedTask.runCallback();
            }
        }

        public void tick() {
            for (DataFetcher.SubscribedTask<?> subscribedTask : this.subscriptions) {
                subscribedTask.update(DataFetcher.this.timeSource.get(DataFetcher.this.resolution));
            }
        }

        public void reset() {
            for (DataFetcher.SubscribedTask<?> subscribedTask : this.subscriptions) {
                subscribedTask.reset();
            }
        }
    }

    record SuccessfulComputationResult<T>(T value, long time) {
    }

    public class Task<T> {
        private final String id;
        private final Callable<T> updater;
        private final long period;
        private final RepeatedDelayStrategy repeatStrategy;
        @Nullable
        private CompletableFuture<DataFetcher.ComputationResult<T>> pendingTask;
        @Nullable
        DataFetcher.SuccessfulComputationResult<T> lastResult;
        private long nextUpdate = -1L;

        Task(final String id, final Callable<T> updater, final long period, final RepeatedDelayStrategy repeatStrategy) {
            this.id = id;
            this.updater = updater;
            this.period = period;
            this.repeatStrategy = repeatStrategy;
        }

        void updateIfNeeded(long time) {
            if (this.pendingTask != null) {
                DataFetcher.ComputationResult<T> computationResult = this.pendingTask.getNow(null);
                if (computationResult == null) {
                    return;
                }

                this.pendingTask = null;
                long l = computationResult.time;
                computationResult.value().ifLeft(object -> {
                    this.lastResult = new DataFetcher.SuccessfulComputationResult<>((T)object, l);
                    this.nextUpdate = l + this.period * this.repeatStrategy.delayCyclesAfterSuccess();
                }).ifRight(exception -> {
                    long m = this.repeatStrategy.delayCyclesAfterFailure();
                    DataFetcher.LOGGER.warn("Failed to process task {}, will repeat after {} cycles", this.id, m, exception);
                    this.nextUpdate = l + this.period * m;
                });
            }

            if (this.nextUpdate <= time) {
                this.pendingTask = CompletableFuture.supplyAsync(() -> {
                    try {
                        T object = this.updater.call();
                        long lx = DataFetcher.this.timeSource.get(DataFetcher.this.resolution);
                        return new DataFetcher.ComputationResult<>(Either.left(object), lx);
                    } catch (Exception exception) {
                        long lxx = DataFetcher.this.timeSource.get(DataFetcher.this.resolution);
                        return new DataFetcher.ComputationResult<>(Either.right(exception), lxx);
                    }
                }, DataFetcher.this.executor);
            }
        }

        public void reset() {
            this.pendingTask = null;
            this.lastResult = null;
            this.nextUpdate = -1L;
        }
    }
}
