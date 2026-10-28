/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.loading;

import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModWorkManager;
import net.neoforged.neoforge.internal.CommonModLoader;
import org.jetbrains.annotations.ApiStatus;

// VersionUnbound: only the loading sequence is ported so far. Still missing: setupModResourcePacks (Phase 2 step 8),
// checkForUpdates, and completeModLoading/reportFatalError (the loading-warnings screen and FML's fatal error window).
@ApiStatus.Internal
public class ClientModLoader extends CommonModLoader {
    public static void begin() {
        // VersionUnbound: NeoForge adds a log4j shutdown hook here because its launcher disables log4j's own. Fabric
        // keeps log4j's hook. Fabric also has no early loading screen to tick, and LanguageHook isn't ported yet.
        begin(() -> {}, false);
    }

    public static void finish() {
        Runnable periodicTick = () -> {};
        // VersionUnbound: NeoForge catches ModLoadingException here, writes a crash report and shows FML's fatal error
        // window. Without that window, the exception propagates out of Minecraft's constructor, and client Main writes
        // the crash report and exits.
        load(periodicTick);
        // VersionUnbound: ClientNetworkRegistry arrives with networking (Phase 2 step 7).
        ModLoader.runInitTask("Client network registry lock", ModWorkManager.syncExecutor(), periodicTick, () -> {});
    }
}
