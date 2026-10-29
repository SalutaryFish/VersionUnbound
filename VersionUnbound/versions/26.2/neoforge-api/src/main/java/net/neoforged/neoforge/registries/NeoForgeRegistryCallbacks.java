/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.registries;

import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.callback.AddCallback;
import net.neoforged.neoforge.registries.callback.ClearCallback;

class NeoForgeRegistryCallbacks {
    // VersionUnbound: BlockCallbacks, ItemCallbacks and AttributeCallbacks aren't ported. Fabric API already does their work
    // for every registration: block state ids, the block to item map, shape caches and the debug world's state list.
    // Vanilla's maps stay in use (see GameData), and attribute validation comes with entity attributes (Phase 2 step 4).

    static class PoiTypeCallbacks implements AddCallback<PoiType>, ClearCallback<PoiType> {
        static final PoiTypeCallbacks INSTANCE = new PoiTypeCallbacks();
        // VersionUnbound: vanilla's map; see GameData.getBlockStatePointOfInterestTypeMap.
        static final Map<BlockState, Holder<PoiType>> BLOCKSTATE_TO_POI_TYPE_MAP = GameData.getBlockStatePointOfInterestTypeMap();

        @Override
        public void onAdd(Registry<PoiType> registry, int id, ResourceKey<PoiType> key, PoiType value) {
            value.matchingStates().forEach(state -> {
                Holder<PoiType> oldType = BLOCKSTATE_TO_POI_TYPE_MAP.put(state, registry.getOrThrow(key));
                if (oldType != null) {
                    throw new IllegalStateException(String.format(Locale.ENGLISH,
                            "Point of interest types %s and %s both list %s in their blockstates, this is not allowed. Blockstates can only have one point of interest type each.",
                            oldType, value, state));
                }
            });
        }

        @Override
        public void onClear(Registry<PoiType> registry, boolean full) {
            if (full)
                BLOCKSTATE_TO_POI_TYPE_MAP.clear();
        }
    }
}
