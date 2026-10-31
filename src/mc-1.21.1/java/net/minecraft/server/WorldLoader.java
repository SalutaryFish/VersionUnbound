package net.minecraft.server;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.WorldDataConfiguration;
import org.slf4j.Logger;

public class WorldLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static <D, R> CompletableFuture<R> load(
        WorldLoader.InitConfig initConfig,
        WorldLoader.WorldDataSupplier<D> worldDataSupplier,
        WorldLoader.ResultFactory<D, R> resultFactory,
        Executor backgroundExecutor,
        Executor gameExecutor
    ) {
        try {
            Pair<WorldDataConfiguration, CloseableResourceManager> pair = initConfig.packConfig.createResourceManager();
            CloseableResourceManager closeableResourceManager = pair.getSecond();
            LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess = RegistryLayer.createRegistryAccess();
            LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess2 = loadAndReplaceLayer(
                closeableResourceManager, layeredRegistryAccess, RegistryLayer.WORLDGEN, RegistryDataLoader.WORLDGEN_REGISTRIES
            );
            RegistryAccess.Frozen frozen = layeredRegistryAccess2.getAccessForLoading(RegistryLayer.DIMENSIONS);
            RegistryAccess.Frozen frozen2 = RegistryDataLoader.load(closeableResourceManager, frozen, RegistryDataLoader.DIMENSION_REGISTRIES);
            WorldDataConfiguration worldDataConfiguration = pair.getFirst();
            WorldLoader.DataLoadOutput<D> dataLoadOutput = worldDataSupplier.get(
                new WorldLoader.DataLoadContext(closeableResourceManager, worldDataConfiguration, frozen, frozen2)
            );
            LayeredRegistryAccess<RegistryLayer> layeredRegistryAccess3 = layeredRegistryAccess2.replaceFrom(
                RegistryLayer.DIMENSIONS, dataLoadOutput.finalDimensions
            );
            return ReloadableServerResources.loadResources(
                    closeableResourceManager,
                    layeredRegistryAccess3,
                    worldDataConfiguration.enabledFeatures(),
                    initConfig.commandSelection(),
                    initConfig.functionCompilationLevel(),
                    backgroundExecutor,
                    gameExecutor
                )
                .whenComplete((result, exceptionx) -> {
                    if (exceptionx != null) {
                        closeableResourceManager.close();
                    }
                })
                .thenApplyAsync(reloadableServerResources -> {
                    reloadableServerResources.updateRegistryTags();
                    return resultFactory.create(closeableResourceManager, reloadableServerResources, layeredRegistryAccess3, dataLoadOutput.cookie);
                }, gameExecutor);
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static RegistryAccess.Frozen loadLayer(
        ResourceManager resourceManager,
        LayeredRegistryAccess<RegistryLayer> registryAccess,
        RegistryLayer registryLayer,
        List<RegistryDataLoader.RegistryData<?>> registryData
    ) {
        RegistryAccess.Frozen frozen = registryAccess.getAccessForLoading(registryLayer);
        return RegistryDataLoader.load(resourceManager, frozen, registryData);
    }

    private static LayeredRegistryAccess<RegistryLayer> loadAndReplaceLayer(
        ResourceManager resourceManager,
        LayeredRegistryAccess<RegistryLayer> registryAccess,
        RegistryLayer registryLayer,
        List<RegistryDataLoader.RegistryData<?>> registryData
    ) {
        RegistryAccess.Frozen frozen = loadLayer(resourceManager, registryAccess, registryLayer, registryData);
        return registryAccess.replaceFrom(registryLayer, frozen);
    }

    public record DataLoadContext(
        ResourceManager resources, WorldDataConfiguration dataConfiguration, RegistryAccess.Frozen datapackWorldgen, RegistryAccess.Frozen datapackDimensions
    ) {
    }

    public record DataLoadOutput<D>(D cookie, RegistryAccess.Frozen finalDimensions) {
    }

    public record InitConfig(WorldLoader.PackConfig packConfig, Commands.CommandSelection commandSelection, int functionCompilationLevel) {
    }

    public record PackConfig(PackRepository packRepository, WorldDataConfiguration initialDataConfig, boolean safeMode, boolean initMode) {
        public Pair<WorldDataConfiguration, CloseableResourceManager> createResourceManager() {
            WorldDataConfiguration worldDataConfiguration = MinecraftServer.configurePackRepository(
                this.packRepository, this.initialDataConfig, this.initMode, this.safeMode
            );
            List<PackResources> list = this.packRepository.openAllSelected();
            CloseableResourceManager closeableResourceManager = new MultiPackResourceManager(PackType.SERVER_DATA, list);
            return Pair.of(worldDataConfiguration, closeableResourceManager);
        }
    }

    @FunctionalInterface
    public interface ResultFactory<D, R> {
        R create(CloseableResourceManager var1, ReloadableServerResources var2, LayeredRegistryAccess<RegistryLayer> var3, D var4);
    }

    @FunctionalInterface
    public interface WorldDataSupplier<D> {
        WorldLoader.DataLoadOutput<D> get(WorldLoader.DataLoadContext var1);
    }
}
