/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.server.loading;

import net.neoforged.fml.Logging;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforge.internal.CommonModLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class ServerModLoader extends CommonModLoader {
    private static final Logger LOGGER = LogManager.getLogger();
    private static boolean hasErrors = false;
    private static boolean gameTestServer;

    // VersionUnbound: NeoForge's load(boolean) runs begin() and load() back to back, where Main calls it after
    // Util.startTimerHackThread(). On Fabric, mods must be constructed and registered before Fabric initializes its own
    // mods, because Fabric API freezes the registries right after that. So the server Main hook calls begin(boolean)
    // after Bootstrap.validate(), and load() at NeoForge's call site.
    public static void begin(boolean gameTestServer) {
        ServerModLoader.gameTestServer = gameTestServer;
        // VersionUnbound: LanguageHook (server-side translations) isn't ported yet.
        // LanguageHook.loadBuiltinLanguages();
        try {
            begin(() -> {}, false);
        } catch (ModLoadingException error) {
            throw failed(error);
        }
    }

    public static void load() {
        try {
            load(() -> {});
        } catch (ModLoadingException error) {
            throw failed(error);
        }
        var warnings = ModLoader.getLoadingIssues().stream().filter(i -> i.severity() == ModLoadingIssue.Severity.WARNING).toList();
        if (!warnings.isEmpty()) {
            LOGGER.warn(Logging.LOADING, "Mods loaded with {} issues", warnings.size());
            for (var issue : warnings) {
                LOGGER.warn(Logging.LOADING, "{} [{}]", issue.translationKey(), issue.translationArgs());
            }
        }
    }

    private static ModLoadingException failed(ModLoadingException error) {
        ServerModLoader.hasErrors = true;
        // VersionUnbound: NeoForge reloads the built-in languages here and writes a crash report with
        // CrashReportExtender.dumpModLoadingCrashReport. Neither is ported yet; the exception's message lists the issues.
        return error;
    }

    public static boolean isGameTestServer() {
        return gameTestServer;
    }

    public static boolean hasErrors() {
        return ServerModLoader.hasErrors;
    }
}
