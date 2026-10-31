package net.minecraft.client.gui.screens.worldselection;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DatapackLoadFailureScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.NoticeWithLinkScreen;
import net.minecraft.client.gui.screens.RecoverWorldDataScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.commands.Commands;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.util.MemoryReserve;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.validation.ContentValidationException;
import org.slf4j.Logger;

public class WorldOpenFlows {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final UUID WORLD_PACK_ID = UUID.fromString("640a6a92-b6cb-48a0-b391-831586500359");
    private final Minecraft minecraft;
    private final LevelStorageSource levelSource;

    public WorldOpenFlows(Minecraft minecraft, LevelStorageSource levelSource) {
        this.minecraft = minecraft;
        this.levelSource = levelSource;
    }

    public void createFreshLevel(
        String levelName, LevelSettings levelSettings, WorldOptions worldOptions, Function<RegistryAccess, WorldDimensions> dimensionGetter, Screen lastScreen
    ) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));
        LevelStorageSource.LevelStorageAccess levelStorageAccess = this.createWorldAccess(levelName);
        if (levelStorageAccess != null) {
            PackRepository packRepository = ServerPacksSource.createPackRepository(levelStorageAccess);
            WorldDataConfiguration worldDataConfiguration = levelSettings.getDataConfiguration();

            try {
                WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(packRepository, worldDataConfiguration, false, false);
                WorldStem worldStem = this.loadWorldDataBlocking(
                    packConfig,
                    dataLoadContext -> {
                        WorldDimensions.Complete complete = dimensionGetter.apply(dataLoadContext.datapackWorldgen())
                            .bake(dataLoadContext.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM));
                        return new WorldLoader.DataLoadOutput<>(
                            new PrimaryLevelData(levelSettings, worldOptions, complete.specialWorldProperty(), complete.lifecycle()),
                            complete.dimensionsRegistryAccess()
                        );
                    },
                    WorldStem::new
                );
                this.minecraft.doWorldLoad(levelStorageAccess, packRepository, worldStem, true);
            } catch (Exception exception) {
                LOGGER.warn("Failed to load datapacks, can't proceed with server load", exception);
                levelStorageAccess.safeClose();
                this.minecraft.setScreen(lastScreen);
            }
        }
    }

    @Nullable
    private LevelStorageSource.LevelStorageAccess createWorldAccess(String levelName) {
        try {
            return this.levelSource.validateAndCreateAccess(levelName);
        } catch (IOException iOException) {
            LOGGER.warn("Failed to read level {} data", levelName, iOException);
            SystemToast.onWorldAccessFailure(this.minecraft, levelName);
            this.minecraft.setScreen(null);
            return null;
        } catch (ContentValidationException contentValidationException) {
            LOGGER.warn("{}", contentValidationException.getMessage());
            this.minecraft.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> this.minecraft.setScreen(null)));
            return null;
        }
    }

    public void createLevelFromExistingSettings(
        LevelStorageSource.LevelStorageAccess levelStorage,
        ReloadableServerResources resources,
        LayeredRegistryAccess<RegistryLayer> registries,
        WorldData worldData
    ) {
        PackRepository packRepository = ServerPacksSource.createPackRepository(levelStorage);
        CloseableResourceManager closeableResourceManager = new WorldLoader.PackConfig(packRepository, worldData.getDataConfiguration(), false, false)
            .createResourceManager()
            .getSecond();
        this.minecraft.doWorldLoad(levelStorage, packRepository, new WorldStem(closeableResourceManager, resources, registries, worldData), true);
    }

    public WorldStem loadWorldStem(Dynamic<?> dynamic, boolean safeMode, PackRepository packRepository) throws Exception {
        WorldLoader.PackConfig packConfig = LevelStorageSource.getPackConfig(dynamic, packRepository, safeMode);
        return this.loadWorldDataBlocking(
            packConfig,
            dataLoadContext -> {
                Registry<LevelStem> registry = dataLoadContext.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM);
                LevelDataAndDimensions levelDataAndDimensions = LevelStorageSource.getLevelDataAndDimensions(
                    dynamic, dataLoadContext.dataConfiguration(), registry, dataLoadContext.datapackWorldgen()
                );
                return new WorldLoader.DataLoadOutput<>(levelDataAndDimensions.worldData(), levelDataAndDimensions.dimensions().dimensionsRegistryAccess());
            },
            WorldStem::new
        );
    }

    public Pair<LevelSettings, WorldCreationContext> recreateWorldData(LevelStorageSource.LevelStorageAccess levelStorage) throws Exception {
        PackRepository packRepository = ServerPacksSource.createPackRepository(levelStorage);
        Dynamic<?> dynamic = levelStorage.getDataTag();
        WorldLoader.PackConfig packConfig = LevelStorageSource.getPackConfig(dynamic, packRepository, false);

        record Data(LevelSettings levelSettings, WorldOptions options, Registry<LevelStem> existingDimensions) {
        }

        return this.loadWorldDataBlocking(
            packConfig,
            dataLoadContext -> {
                Registry<LevelStem> registry = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable()).freeze();
                LevelDataAndDimensions levelDataAndDimensions = LevelStorageSource.getLevelDataAndDimensions(
                    dynamic, dataLoadContext.dataConfiguration(), registry, dataLoadContext.datapackWorldgen()
                );
                return new WorldLoader.DataLoadOutput<>(
                    new Data(
                        levelDataAndDimensions.worldData().getLevelSettings(),
                        levelDataAndDimensions.worldData().worldGenOptions(),
                        levelDataAndDimensions.dimensions().dimensions()
                    ),
                    dataLoadContext.datapackDimensions()
                );
            },
            (closeableResourceManager, reloadableServerResources, layeredRegistryAccess, arg) -> {
                closeableResourceManager.close();
                return Pair.of(
                    arg.levelSettings,
                    new WorldCreationContext(
                        arg.options,
                        new WorldDimensions(arg.existingDimensions),
                        layeredRegistryAccess,
                        reloadableServerResources,
                        arg.levelSettings.getDataConfiguration()
                    )
                );
            }
        );
    }

    private <D, R> R loadWorldDataBlocking(
        WorldLoader.PackConfig packConfig, WorldLoader.WorldDataSupplier<D> worldDataSupplier, WorldLoader.ResultFactory<D, R> resultFactory
    ) throws Exception {
        WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(packConfig, Commands.CommandSelection.INTEGRATED, 2);
        CompletableFuture<R> completableFuture = WorldLoader.load(initConfig, worldDataSupplier, resultFactory, Util.backgroundExecutor(), this.minecraft);
        this.minecraft.managedBlock(completableFuture::isDone);
        return completableFuture.get();
    }

    private void askForBackup(LevelStorageSource.LevelStorageAccess levelStorage, boolean customized, Runnable loadLevel, Runnable onCancel) {
        Component component;
        Component component2;
        if (customized) {
            component = Component.translatable("selectWorld.backupQuestion.customized");
            component2 = Component.translatable("selectWorld.backupWarning.customized");
        } else {
            component = Component.translatable("selectWorld.backupQuestion.experimental");
            component2 = Component.translatable("selectWorld.backupWarning.experimental");
        }

        this.minecraft.setScreen(new BackupConfirmScreen(onCancel, (bl, bl2) -> {
            if (bl) {
                EditWorldScreen.makeBackupAndShowToast(levelStorage);
            }

            loadLevel.run();
        }, component, component2, false));
    }

    public static void confirmWorldCreation(Minecraft minecraft, CreateWorldScreen screen, Lifecycle lifecycle, Runnable loadWorld, boolean skipWarnings) {
        BooleanConsumer booleanConsumer = bl -> {
            if (bl) {
                loadWorld.run();
            } else {
                minecraft.setScreen(screen);
            }
        };
        if (skipWarnings || lifecycle == Lifecycle.stable()) {
            loadWorld.run();
        } else if (lifecycle == Lifecycle.experimental()) {
            minecraft.setScreen(
                new ConfirmScreen(
                    booleanConsumer,
                    Component.translatable("selectWorld.warning.experimental.title"),
                    Component.translatable("selectWorld.warning.experimental.question")
                )
            );
        } else {
            minecraft.setScreen(
                new ConfirmScreen(
                    booleanConsumer,
                    Component.translatable("selectWorld.warning.deprecated.title"),
                    Component.translatable("selectWorld.warning.deprecated.question")
                )
            );
        }
    }

    public void openWorld(String worldName, Runnable onFail) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));
        LevelStorageSource.LevelStorageAccess levelStorageAccess = this.createWorldAccess(worldName);
        if (levelStorageAccess != null) {
            this.openWorldLoadLevelData(levelStorageAccess, onFail);
        }
    }

    private void openWorldLoadLevelData(LevelStorageSource.LevelStorageAccess levelStorage, Runnable onFail) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));

        Dynamic<?> dynamic;
        LevelSummary levelSummary;
        try {
            dynamic = levelStorage.getDataTag();
            levelSummary = levelStorage.getSummary(dynamic);
        } catch (IOException | NbtException | ReportedNbtException exception) {
            this.minecraft.setScreen(new RecoverWorldDataScreen(this.minecraft, bl -> {
                if (bl) {
                    this.openWorldLoadLevelData(levelStorage, onFail);
                } else {
                    levelStorage.safeClose();
                    onFail.run();
                }
            }, levelStorage));
            return;
        } catch (OutOfMemoryError outOfMemoryError) {
            MemoryReserve.release();
            System.gc();
            String string = "Ran out of memory trying to read level data of world folder \"" + levelStorage.getLevelId() + "\"";
            LOGGER.error(LogUtils.FATAL_MARKER, string);
            OutOfMemoryError outOfMemoryError2 = new OutOfMemoryError("Ran out of memory reading level data");
            outOfMemoryError2.initCause(outOfMemoryError);
            CrashReport crashReport = CrashReport.forThrowable(outOfMemoryError2, string);
            CrashReportCategory crashReportCategory = crashReport.addCategory("World details");
            crashReportCategory.setDetail("World folder", levelStorage.getLevelId());
            throw new ReportedException(crashReport);
        }

        this.openWorldCheckVersionCompatibility(levelStorage, levelSummary, dynamic, onFail);
    }

    private void openWorldCheckVersionCompatibility(
        LevelStorageSource.LevelStorageAccess levelStorage, LevelSummary levelSummary, Dynamic<?> levelData, Runnable onFail
    ) {
        if (!levelSummary.isCompatible()) {
            levelStorage.safeClose();
            this.minecraft
                .setScreen(
                    new AlertScreen(
                        onFail,
                        Component.translatable("selectWorld.incompatible.title").withColor(-65536),
                        Component.translatable("selectWorld.incompatible.description", levelSummary.getWorldVersionName())
                    )
                );
        } else {
            LevelSummary.BackupStatus backupStatus = levelSummary.backupStatus();
            if (backupStatus.shouldBackup()) {
                String string = "selectWorld.backupQuestion." + backupStatus.getTranslationKey();
                String string2 = "selectWorld.backupWarning." + backupStatus.getTranslationKey();
                MutableComponent mutableComponent = Component.translatable(string);
                if (backupStatus.isSevere()) {
                    mutableComponent.withColor(-2142128);
                }

                Component component = Component.translatable(string2, levelSummary.getWorldVersionName(), SharedConstants.getCurrentVersion().getName());
                this.minecraft.setScreen(new BackupConfirmScreen(() -> {
                    levelStorage.safeClose();
                    onFail.run();
                }, (bl, bl2) -> {
                    if (bl) {
                        EditWorldScreen.makeBackupAndShowToast(levelStorage);
                    }

                    this.openWorldLoadLevelStem(levelStorage, levelData, false, onFail);
                }, mutableComponent, component, false));
            } else {
                this.openWorldLoadLevelStem(levelStorage, levelData, false, onFail);
            }
        }
    }

    private void openWorldLoadLevelStem(LevelStorageSource.LevelStorageAccess levelStorage, Dynamic<?> levelData, boolean safeMode, Runnable onFail) {
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.resource_load")));
        PackRepository packRepository = ServerPacksSource.createPackRepository(levelStorage);

        WorldStem worldStem;
        try {
            worldStem = this.loadWorldStem(levelData, safeMode, packRepository);

            for (LevelStem levelStem : worldStem.registries().compositeAccess().registryOrThrow(Registries.LEVEL_STEM)) {
                levelStem.generator().validate();
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to load level data or datapacks, can't proceed with server load", exception);
            if (!safeMode) {
                this.minecraft.setScreen(new DatapackLoadFailureScreen(() -> {
                    levelStorage.safeClose();
                    onFail.run();
                }, () -> this.openWorldLoadLevelStem(levelStorage, levelData, true, onFail)));
            } else {
                levelStorage.safeClose();
                this.minecraft
                    .setScreen(
                        new AlertScreen(
                            onFail,
                            Component.translatable("datapackFailure.safeMode.failed.title"),
                            Component.translatable("datapackFailure.safeMode.failed.description"),
                            CommonComponents.GUI_BACK,
                            true
                        )
                    );
            }

            return;
        }

        this.openWorldCheckWorldStemCompatibility(levelStorage, worldStem, packRepository, onFail);
    }

    private void openWorldCheckWorldStemCompatibility(
        LevelStorageSource.LevelStorageAccess levelStorage, WorldStem worldStem, PackRepository packRepository, Runnable onFail
    ) {
        WorldData worldData = worldStem.worldData();
        boolean bl = worldData.worldGenOptions().isOldCustomizedWorld();
        boolean bl2 = worldData.worldGenSettingsLifecycle() != Lifecycle.stable();
        if (!bl && !bl2) {
            this.openWorldLoadBundledResourcePack(levelStorage, worldStem, packRepository, onFail);
        } else {
            this.askForBackup(levelStorage, bl, () -> this.openWorldLoadBundledResourcePack(levelStorage, worldStem, packRepository, onFail), () -> {
                worldStem.close();
                levelStorage.safeClose();
                onFail.run();
            });
        }
    }

    private void openWorldLoadBundledResourcePack(
        LevelStorageSource.LevelStorageAccess levelStorage, WorldStem worldStem, PackRepository packRepository, Runnable onFail
    ) {
        DownloadedPackSource downloadedPackSource = this.minecraft.getDownloadedPackSource();
        this.loadBundledResourcePack(downloadedPackSource, levelStorage).thenApply(void_ -> true).exceptionallyComposeAsync(throwable -> {
            LOGGER.warn("Failed to load pack: ", throwable);
            return this.promptBundledPackLoadFailure();
        }, this.minecraft).thenAcceptAsync(boolean_ -> {
            if (boolean_) {
                this.openWorldCheckDiskSpace(levelStorage, worldStem, downloadedPackSource, packRepository, onFail);
            } else {
                downloadedPackSource.popAll();
                worldStem.close();
                levelStorage.safeClose();
                onFail.run();
            }
        }, this.minecraft).exceptionally(throwable -> {
            this.minecraft.delayCrash(CrashReport.forThrowable(throwable, "Load world"));
            return null;
        });
    }

    private void openWorldCheckDiskSpace(
        LevelStorageSource.LevelStorageAccess levelStorage,
        WorldStem worldStem,
        DownloadedPackSource packSource,
        PackRepository packRepository,
        Runnable onFail
    ) {
        if (levelStorage.checkForLowDiskSpace()) {
            this.minecraft
                .setScreen(
                    new ConfirmScreen(
                        bl -> {
                            if (bl) {
                                this.openWorldDoLoad(levelStorage, worldStem, packRepository);
                            } else {
                                packSource.popAll();
                                worldStem.close();
                                levelStorage.safeClose();
                                onFail.run();
                            }
                        },
                        Component.translatable("selectWorld.warning.lowDiskSpace.title").withStyle(ChatFormatting.RED),
                        Component.translatable("selectWorld.warning.lowDiskSpace.description"),
                        CommonComponents.GUI_CONTINUE,
                        CommonComponents.GUI_BACK
                    )
                );
        } else {
            this.openWorldDoLoad(levelStorage, worldStem, packRepository);
        }
    }

    private void openWorldDoLoad(LevelStorageSource.LevelStorageAccess levelStorage, WorldStem worldStem, PackRepository packRepository) {
        this.minecraft.doWorldLoad(levelStorage, packRepository, worldStem, false);
    }

    private CompletableFuture<Void> loadBundledResourcePack(DownloadedPackSource packSource, LevelStorageSource.LevelStorageAccess level) {
        Path path = level.getLevelPath(LevelResource.MAP_RESOURCE_FILE);
        if (Files.exists(path) && !Files.isDirectory(path)) {
            packSource.configureForLocalWorld();
            CompletableFuture<Void> completableFuture = packSource.waitForPackFeedback(WORLD_PACK_ID);
            packSource.pushLocalPack(WORLD_PACK_ID, path);
            return completableFuture;
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Boolean> promptBundledPackLoadFailure() {
        CompletableFuture<Boolean> completableFuture = new CompletableFuture<>();
        this.minecraft
            .setScreen(
                new ConfirmScreen(
                    completableFuture::complete,
                    Component.translatable("multiplayer.texturePrompt.failure.line1"),
                    Component.translatable("multiplayer.texturePrompt.failure.line2"),
                    CommonComponents.GUI_PROCEED,
                    CommonComponents.GUI_CANCEL
                )
            );
        return completableFuture;
    }
}
