/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.IModBusEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * A loaded mod. Ported from FML 11.0.16; members not yet implemented on Fabric
 * (mod info, extension points, configs) are added as Layer A grows.
 */
public abstract class ModContainer {
    private static final Logger LOGGER = LogManager.getLogger();

    protected final String modId;
    protected final String namespace;

    public ModContainer(String modId) {
        this.modId = modId;
        this.namespace = modId;
    }

    public final String getModId() {
        return modId;
    }

    public final String getNamespace() {
        return namespace;
    }

    @ApiStatus.OverrideOnly
    protected void constructMod() {}

    @Nullable
    public abstract IEventBus getEventBus();

    public final <T extends Event & IModBusEvent> void acceptEvent(T e) {
        IEventBus bus = getEventBus();
        if (bus == null) return;

        try {
            bus.post(e);
        } catch (Throwable t) {
            LOGGER.error("Caught exception during event {} dispatch for modid {}", e, this.getModId(), t);
            throw new RuntimeException("Error during event " + e.getClass().getName() + " for mod " + modId, t);
        }
    }

    public final <T extends Event & IModBusEvent> void acceptEvent(EventPriority phase, T e) {
        IEventBus bus = getEventBus();
        if (bus == null) return;

        try {
            bus.post(phase, e);
        } catch (Throwable t) {
            LOGGER.error("Caught exception during event {} dispatch for phase {} for modid {}", e, phase, this.getModId(), t);
            throw new RuntimeException("Error during event " + e.getClass().getName() + " for mod " + modId, t);
        }
    }
}
