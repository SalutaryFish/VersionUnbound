/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.capabilities;

import net.neoforged.fml.ModLoader;
import org.jetbrains.annotations.ApiStatus;

// VersionUnbound: partial. The flags and init() are NeoForge's; the vanilla providers (registerVanillaProviders and the
// rest, built on the transfer package) and the cache invalidation listeners come with capabilities (Phase 2 step 9),
// when RegistrationEvents calls init().
@ApiStatus.Internal
public class CapabilityHooks {
    private static boolean initialized = false;
    static boolean initFinished = false;

    public static void init() {
        if (initialized)
            throw new IllegalArgumentException("CapabilityHooks.init() called twice");
        initialized = true;

        var event = new RegisterCapabilitiesEvent();
        ModLoader.postEventWrapContainerInModOrder(event);

        initFinished = true;
    }
}
