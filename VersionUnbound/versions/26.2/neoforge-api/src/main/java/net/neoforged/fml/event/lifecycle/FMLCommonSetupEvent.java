/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.event.lifecycle;

import net.neoforged.fml.DeferredWorkQueue;
import net.neoforged.fml.ModContainer;

/**
 * Fired on each mod's bus once all mods are constructed and registries are populated.
 */
public class FMLCommonSetupEvent extends ParallelDispatchEvent {
    public FMLCommonSetupEvent(ModContainer container, DeferredWorkQueue deferredWorkQueue) {
        super(container, deferredWorkQueue);
    }
}
