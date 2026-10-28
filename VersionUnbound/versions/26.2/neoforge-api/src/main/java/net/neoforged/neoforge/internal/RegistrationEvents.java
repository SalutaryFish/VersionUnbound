/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.internal;

public class RegistrationEvents {
    static void init() {
        // VersionUnbound: none of these are ported yet. Each comes back with its feature: data maps and component
        // modifiers with registries (Phase 2 step 3), cauldron fluids and capabilities in step 9.
        // CauldronFluidContent.init(); // must be before capability event
        // CauldronInteractions.registerCustomInteractions();
        // CapabilityHooks.init(); // must be after cauldron event
        // ForcedChunkManager.init();
        // RegistryManager.initDataMaps();
        // DataComponentModifiers.init();
        // PoiTypeExtender.extendPoiTypes();
    }
}
