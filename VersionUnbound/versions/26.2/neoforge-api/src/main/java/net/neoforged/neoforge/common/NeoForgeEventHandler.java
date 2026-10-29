/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.common;

import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.jetbrains.annotations.ApiStatus;

// VersionUnbound: ported a listener at a time, like NeoForgeMod; the listeners here are verbatim and in NeoForge's
// order. Not yet: custom item entities (step 5), fake players, config sync (step 6), the username cache, data maps
// (steps 7 and 8), NeoForge's commands, reload listeners and the transformation statistics.
@ApiStatus.Internal
public class NeoForgeEventHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void builtinMobSpawnBlocker(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob mob && mob.isSpawnCancelled()) {
            event.setCanceled(true);
        }
    }
}
