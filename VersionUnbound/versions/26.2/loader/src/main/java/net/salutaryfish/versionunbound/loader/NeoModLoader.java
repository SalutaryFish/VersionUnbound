package net.salutaryfish.versionunbound.loader;

import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModWorkManager;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;

/**
 * Drives NeoForge mods through FML's loading sequence, following NeoForge's {@code CommonModLoader}.
 */
public final class NeoModLoader {
    private NeoModLoader() {}

    /** Builds the mod containers and constructs the mods. Throws a {@code ModLoadingException} if loading failed. */
    static void begin() {
        ModLoader.gatherAndInitializeMods(ModWorkManager.syncExecutor(), ModWorkManager.parallelExecutor(), () -> {});
    }

    /** Fires the setup events that follow construction. */
    static void load() {
        var syncExecutor = ModWorkManager.syncExecutor();
        var parallelExecutor = ModWorkManager.parallelExecutor();
        Runnable periodicTask = () -> {};
        ModLoader.dispatchParallelEvent("Common setup", syncExecutor, parallelExecutor, periodicTask, FMLCommonSetupEvent::new);
        ModLoader.dispatchParallelEvent("Enqueue IMC", syncExecutor, parallelExecutor, periodicTask, InterModEnqueueEvent::new);
        ModLoader.dispatchParallelEvent("Process IMC", syncExecutor, parallelExecutor, periodicTask, InterModProcessEvent::new);
    }
}
