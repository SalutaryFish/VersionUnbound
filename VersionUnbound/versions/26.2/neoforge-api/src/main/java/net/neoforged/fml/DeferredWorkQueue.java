/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Work that mods enqueue during parallel lifecycle events, run afterwards on the main thread.
 * Ported from FML 11.0.16.
 */
public class DeferredWorkQueue {
    private static final Logger LOGGER = LogManager.getLogger();

    private final ConcurrentLinkedDeque<TaskInfo> tasks = new ConcurrentLinkedDeque<>();
    private final String name;

    public DeferredWorkQueue(String name) {
        this.name = name;
    }

    public void runTasks() {
        if (tasks.isEmpty()) return;
        LOGGER.debug("Dispatching synchronous work for work queue {}: {} jobs", name, tasks.size());
        RuntimeException aggregate = new RuntimeException();
        for (TaskInfo ti : tasks) {
            ti.future.exceptionally(t -> captureException(ti.owner.getModId(), aggregate, t));
            ti.task.run();
        }
        tasks.clear();
        if (aggregate.getSuppressed().length > 0) {
            LOGGER.fatal("Synchronous work queue {} completed exceptionally, see suppressed exceptions for details:", name, aggregate);
            throw aggregate;
        }
    }

    private static <T> T captureException(String modId, RuntimeException aggregate, Throwable throwable) {
        if (throwable instanceof CompletionException ce) {
            throwable = ce.getCause();
        }
        aggregate.addSuppressed(throwable);
        LOGGER.error("Mod '{}' encountered an error in a deferred task:", modId, throwable);
        return null;
    }

    public CompletableFuture<Void> enqueueWork(ModContainer modInfo, Runnable work) {
        return enqueueWork(modInfo, taskInfo -> CompletableFuture.runAsync(work, r -> taskInfo.task = r));
    }

    public <T> CompletableFuture<T> enqueueWork(ModContainer modInfo, Supplier<T> work) {
        return enqueueWork(modInfo, taskInfo -> CompletableFuture.supplyAsync(work, r -> taskInfo.task = r));
    }

    private <T> CompletableFuture<T> enqueueWork(ModContainer modInfo, Function<TaskInfo, CompletableFuture<T>> futureGen) {
        TaskInfo taskInfo = new TaskInfo(modInfo);
        CompletableFuture<T> future = futureGen.apply(taskInfo);
        taskInfo.future = future;
        tasks.add(taskInfo);
        return future;
    }

    private static class TaskInfo {
        private final ModContainer owner;
        private Runnable task;
        private CompletableFuture<?> future;

        private TaskInfo(ModContainer owner) {
            this.owner = owner;
        }
    }
}
