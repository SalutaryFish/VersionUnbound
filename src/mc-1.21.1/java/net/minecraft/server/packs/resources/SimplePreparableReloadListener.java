package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.util.profiling.ProfilerFiller;

public abstract class SimplePreparableReloadListener<T> implements PreparableReloadListener {
    @Override
    public final CompletableFuture<Void> reload(
        PreparableReloadListener.PreparationBarrier stage,
        ResourceManager resourceManager,
        ProfilerFiller preparationsProfiler,
        ProfilerFiller reloadProfiler,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        return CompletableFuture.<T>supplyAsync(() -> this.prepare(resourceManager, preparationsProfiler), backgroundExecutor)
            .thenCompose(stage::wait)
            .thenAcceptAsync(object -> this.apply((T)object, resourceManager, reloadProfiler), gameExecutor);
    }

    /**
     * Performs any reloading that can be done off-thread, such as file IO
     */
    protected abstract T prepare(ResourceManager var1, ProfilerFiller var2);

    protected abstract void apply(T var1, ResourceManager var2, ProfilerFiller var3);
}
