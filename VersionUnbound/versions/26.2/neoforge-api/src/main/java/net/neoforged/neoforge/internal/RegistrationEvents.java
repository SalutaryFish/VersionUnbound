/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.internal;

import net.neoforged.neoforge.registries.RegistryManager;

public class RegistrationEvents {
    static void init() {
        // VersionUnbound: the calls commented out here come back with their features: cauldron fluids and capabilities in
        // Phase 2 step 9, forced chunks with the game events (step 4), component modifiers and POI extension with the
        // extension interfaces (step 5).
        // CauldronFluidContent.init(); // must be before capability event
        // CauldronInteractions.registerCustomInteractions();
        // CapabilityHooks.init(); // must be after cauldron event
        // ForcedChunkManager.init();
        RegistryManager.initDataMaps();
        // DataComponentModifiers.init();
        // PoiTypeExtender.extendPoiTypes();
    }
}
