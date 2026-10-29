/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.loading;

import com.electronwill.nightconfig.core.Config;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.IBindingsProvider;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.javafmlmod.FMLJavaModLanguageProvider;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.ModFileParser;
import net.neoforged.fml.loading.moddiscovery.NightConfigWrapper;
import net.neoforged.fml.loading.modscan.BackgroundScanHandler;
import net.neoforged.fml.mclanguageprovider.MinecraftModLanguageProvider;
import net.neoforged.fml.util.PathPrettyPrinting;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The running loader: which side we are on, the game directory and the list of mods being loaded.
 *
 * <p>VersionUnbound: FML's FMLLoader also starts the game, builds the module layers and class loaders, and runs
 * class transformers. On Fabric, Fabric Loader, Knot and Mixin do all of that, so this class keeps only the state
 * that mods and the rest of FML read. VersionUnbound's loader mod creates it in {@code preLaunch} and hands it the
 * NeoForge mod jars it found; {@link #discoverMods} then follows FML's discovery: read each jar, add the
 * {@code minecraft} and {@code neoforge} system mods, sort, check dependencies and pick language loaders.
 * {@link #startModScan} starts scanning for annotations, once the loader mod has registered the mods' mixin configs.
 */
public final class FMLLoader {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final AtomicReference<@Nullable FMLLoader> current = new AtomicReference<>();

    private final ClassLoader currentClassLoader;
    private final Dist dist;
    private final boolean production;
    private final Path gameDir;
    private final VersionInfo versionInfo;
    private final VersionSupportMatrix versionSupportMatrix;
    private final LanguageProviderLoader languageProviderLoader;
    @Nullable
    private LoadingModList loadingModList;
    public BackgroundScanHandler backgroundScanHandler;
    @Nullable
    private volatile IBindingsProvider bindings;

    private FMLLoader(ClassLoader classLoader, Dist dist, boolean production, Path gameDir, VersionInfo versionInfo) {
        this.currentClassLoader = classLoader;
        this.dist = dist;
        this.production = production;
        this.gameDir = gameDir;
        this.versionInfo = versionInfo;
        this.versionSupportMatrix = new VersionSupportMatrix(versionInfo);
        this.languageProviderLoader = new LanguageProviderLoader(List.of(new FMLJavaModLanguageProvider(), new MinecraftModLanguageProvider()));
    }

    /**
     * Creates the loader and makes it current, then sets up {@link FMLPaths} and reads {@code config/fml.toml},
     * as FML does before discovery.
     *
     * @param classLoader the class loader that mod classes are loaded from (Fabric's Knot)
     * @param versionInfo the NeoForge and Minecraft versions that Layer A implements
     */
    @ApiStatus.Internal
    public static FMLLoader create(ClassLoader classLoader, Dist dist, boolean production, Path gameDir, VersionInfo versionInfo) {
        PathPrettyPrinting.addRoot(gameDir);
        var loader = new FMLLoader(classLoader, dist, production, gameDir, versionInfo);
        FMLLoader witness = current.compareAndExchange(null, loader);
        if (witness != null) {
            throw new IllegalStateException("Another FML loader is already active: " + witness);
        }
        FMLPaths.loadAbsolutePaths(gameDir);
        FMLConfig.load();
        return loader;
    }

    /**
     * Reads the given NeoForge mod jars and builds the {@link LoadingModList}. Problems with individual files, and
     * dependency errors found by the sorter, are recorded as loading issues on the returned list, as FML does;
     * they are thrown later by {@link net.neoforged.fml.ModLoader#gatherAndInitializeMods}.
     *
     * @param minecraftPath the Minecraft jar, shown as the location of the {@code minecraft} system mod
     * @param neoForgePaths the jar of Layer A, or its folders in a dev run: the contents of the {@code neoforge} system mod
     * @param modJars       NeoForge mod jars, each containing {@code META-INF/neoforge.mods.toml}
     */
    @ApiStatus.Internal
    public LoadingModList discoverMods(Path minecraftPath, List<Path> neoForgePaths, List<Path> modJars) {
        if (loadingModList != null) {
            throw new IllegalStateException("Mods were already discovered");
        }
        var issues = new ArrayList<ModLoadingIssue>();
        var modFiles = new ArrayList<ModFile>();
        // The system mods. Minecraft's contents are empty: FML scans the Minecraft jar too, but nothing in it is meant
        // for mods to find, and scanning it would only slow down startup. NeoForge's are Layer A, which holds NeoForge's
        // own mod: like NeoForge's jar, it is scanned for its @Mod class and @EventBusSubscriber classes.
        modFiles.add(systemModFile(JarContents.empty(minecraftPath), "minecraft", "minecraft", versionInfo.mcVersion(),
                "Minecraft", "Minecraft", "Mojang Studios, All Rights Reserved"));
        try {
            modFiles.add(systemModFile(JarContents.ofPaths(neoForgePaths), FMLJavaModLanguageProvider.NAME, "neoforge",
                    versionInfo.neoForgeVersion(), "NeoForge",
                    "A free, open-source, community-oriented modding API for Minecraft: Java Edition.", "LGPL v2.1"));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read NeoForge's mod contents from " + neoForgePaths, e);
        }

        for (Path jar : modJars) {
            JarContents contents = null;
            try {
                contents = JarContents.ofPath(jar);
                var modFile = new ModFile(contents, ModFileParser::modsTomlParser, ModFileDiscoveryAttributes.DEFAULT);
                if (modFile.getModFileInfo() == null) {
                    modFile.close();
                    continue;
                }
                modFiles.add(modFile);
            } catch (ModLoadingException e) {
                issues.addAll(e.getIssues());
                closeQuietly(contents);
            } catch (Exception e) {
                issues.add(ModLoadingIssue.error("fml.modloadingissue.brokenfile").withAffectedPath(jar).withCause(e));
                closeQuietly(contents);
            }
        }

        var list = ModSorter.sort(modFiles, issues);
        for (var modFileInfo : list.getModFiles()) {
            try {
                modFileInfo.getFile().identifyLanguage();
            } catch (ModLoadingException e) {
                list.getModLoadingIssues().addAll(e.getIssues());
            }
        }
        this.loadingModList = list;

        LOGGER.debug(LogMarkers.LOADING, "Discovered NeoForge mods: {}", list.getMods().stream()
                .map(mod -> mod.getModId() + "@" + mod.getVersion()).collect(Collectors.joining(", ")));
        return list;
    }

    /**
     * Starts scanning the discovered mod files for annotations, in the background, as FML does after discovery.
     *
     * <p>Not part of {@link #discoverMods}: the scan threads load FML's scanner classes, and on Fabric every class load
     * goes through Mixin, which takes up newly added mixin configs as it goes. Mods' configs must be registered, and
     * tagged with their mod, before that can happen on another thread.
     */
    @ApiStatus.Internal
    public void startModScan() {
        if (backgroundScanHandler != null) {
            throw new IllegalStateException("The mod scan was already started");
        }
        this.backgroundScanHandler = new BackgroundScanHandler(getLoadingModList().getAllModFiles());
    }

    private static ModFile systemModFile(JarContents contents, String modLoader, String modId, String version, String displayName, String description, String license) {
        return new ModFile(contents, file -> {
            // Built in memory, as FML does for Minecraft's mod info; NeoForge's jar has a neoforge.mods.toml instead.
            var conf = Config.inMemory();
            conf.set("modLoader", modLoader);
            conf.set("loaderVersion", "1");
            conf.set("license", license);
            var mod = Config.inMemory();
            mod.set("modId", modId);
            mod.set("version", version);
            mod.set("displayName", displayName);
            mod.set("description", description);
            conf.set("mods", List.of(mod));
            var configWrapper = new NightConfigWrapper(conf);
            return new ModFileInfo((ModFile) file, configWrapper, configWrapper::setFile, List.of());
        }, IModFile.Type.MOD, ModFileDiscoveryAttributes.DEFAULT);
    }

    private static void closeQuietly(@Nullable JarContents contents) {
        if (contents == null) return;
        try {
            contents.close();
        } catch (Exception e) {
            LOGGER.debug("Failed to close {}", contents, e);
        }
    }

    /**
     * Tries to get the mod file that a given class belongs to.
     *
     * @return Null, if the class doesn't belong to a mod file.
     */
    @Nullable
    public IModFile getModFileByClass(Class<?> clazz) {
        var packageName = clazz.getPackageName();
        if (packageName.isEmpty()) {
            return null;
        }
        return getLoadingModList().getPackageIndex().get(packageName);
    }

    public ClassLoader getCurrentClassLoader() {
        return currentClassLoader;
    }

    /**
     * Found as a service on the game class loader, as FML does on the game layer. Layer A provides it.
     */
    @ApiStatus.Internal
    public IBindingsProvider getBindings() {
        var result = bindings;
        if (result == null) {
            synchronized (this) {
                result = bindings;
                if (result == null) {
                    var providers = ServiceLoader.load(IBindingsProvider.class, currentClassLoader).stream().toList();
                    if (providers.isEmpty()) {
                        throw new IllegalStateException("Could not find bindings provider");
                    } else if (providers.size() > 1) {
                        String providerList = providers.stream().map(p -> p.type().getName()).collect(Collectors.joining(", "));
                        throw new IllegalStateException("Found more than one bindings provider: " + providerList);
                    }
                    bindings = result = providers.getFirst().get();
                }
            }
        }
        return result;
    }

    public static LanguageProviderLoader getLanguageLoadingProvider() {
        return getCurrent().languageProviderLoader;
    }

    public static FMLLoader getCurrent() {
        var current = getCurrentOrNull();
        if (current == null) {
            throw new IllegalStateException("There is no current FML Loader");
        }
        return current;
    }

    @Nullable
    public static FMLLoader getCurrentOrNull() {
        return current.get();
    }

    public Dist getDist() {
        return dist;
    }

    /**
     * @throws IllegalStateException if the loading mod list hasn't been built yet.
     */
    public LoadingModList getLoadingModList() {
        if (loadingModList == null) {
            throw new IllegalStateException("The loading mod list isn't built yet.");
        }
        return loadingModList;
    }

    public Path getGameDir() {
        return gameDir;
    }

    public boolean isProduction() {
        return production;
    }

    /**
     * VersionUnbound: mods are not Java modules on Fabric; they live in the unnamed module of Fabric's class
     * loader. The boot layer is returned so that callers expecting a layer still get one.
     */
    public ModuleLayer getGameLayer() {
        return ModuleLayer.boot();
    }

    public VersionInfo getVersionInfo() {
        return versionInfo;
    }

    VersionSupportMatrix getVersionSupportMatrix() {
        return versionSupportMatrix;
    }
}
