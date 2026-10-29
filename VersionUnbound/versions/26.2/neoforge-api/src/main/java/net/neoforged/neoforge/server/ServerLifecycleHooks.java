/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jspecify.annotations.Nullable;

// VersionUnbound: the server lifecycle part is ported. Not yet: biome and structure modifiers (runModifiers and
// ensureProperSync, with their registries), server-side translations (LanguageHook), the permission API, and the JSON-RPC
// schema event (fireSchemaRegistryEvent).
public class ServerLifecycleHooks {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker SERVERHOOKS = MarkerManager.getMarker("SERVERHOOKS");
    private static final LevelResource SERVERCONFIG = new LevelResource("serverconfig");
    @Nullable
    private static volatile CountDownLatch exitLatch = null;
    @Nullable
    private static MinecraftServer currentServer;

    private static Path getServerConfigPath(final MinecraftServer server) {
        final Path serverConfig = server.getWorldPath(SERVERCONFIG);
        if (!Files.isDirectory(serverConfig)) {
            try {
                Files.createDirectories(serverConfig);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        final Path explanation = serverConfig.resolve("readme.txt");
        if (!Files.exists(explanation)) {
            try {
                Files.writeString(explanation, """
                        Any server configs put in this folder will override the corresponding server config from <instance path>/config/<config path>.
                        If the config being transferred is in a subfolder of the base config folder make sure to include that folder here in the path to the file you are overwriting.
                        For example if you are overwriting a config with the path <instance path>/config/ExampleMod/config-server.toml, you would need to put it in serverconfig/ExampleMod/config-server.toml
                        """, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return serverConfig;
    }

    public static void handleServerAboutToStart(final MinecraftServer server) {
        currentServer = server;
        // on the dedi server we need to force the stuff to setup properly
        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.SERVER, FMLPaths.CONFIGDIR.get(), getServerConfigPath(server));
        // VersionUnbound: biome and structure modifiers aren't ported yet.
        // runModifiers(server);
        NeoForge.EVENT_BUS.post(new ServerAboutToStartEvent(server));
    }

    public static void handleServerStarting(final MinecraftServer server) {
        // VersionUnbound: LanguageHook (server-side translations) and the permission API aren't ported yet.
        // if (FMLEnvironment.getDist().isDedicatedServer()) {
        //     LanguageHook.loadModLanguages(server);
        // }
        // PermissionAPI.initializePermissionAPI();
        NeoForge.EVENT_BUS.post(new ServerStartingEvent(server));
    }

    public static void handleServerStarted(final MinecraftServer server) {
        NeoForge.EVENT_BUS.post(new ServerStartedEvent(server));
    }

    public static void handleServerStopping(final MinecraftServer server) {
        NeoForge.EVENT_BUS.post(new ServerStoppingEvent(server));
    }

    public static void expectServerStopped() {
        exitLatch = new CountDownLatch(1);
    }

    public static void handleServerStopped(final MinecraftServer server) {
        NeoForge.EVENT_BUS.post(new ServerStoppedEvent(server));
        currentServer = null;
        CountDownLatch latch = exitLatch;

        if (latch != null) {
            latch.countDown();
            exitLatch = null;
        }
        ConfigTracker.INSTANCE.unloadConfigs(ModConfig.Type.SERVER);
    }

    @Nullable
    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    public static void handleExit(int retVal) {
        System.exit(retVal);
    }
}
