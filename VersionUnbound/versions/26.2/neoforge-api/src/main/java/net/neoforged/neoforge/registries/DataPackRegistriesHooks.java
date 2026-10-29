/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.registries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

@ApiStatus.Internal
public final class DataPackRegistriesHooks {
    private DataPackRegistriesHooks() {} // utility class

    // VersionUnbound: no NETWORKABLE_REGISTRIES. NeoForge's patch makes that list vanilla's RegistryDataLoader.SYNCHRONIZED_REGISTRIES;
    // here Fabric API's dynamic registries add synced registries to vanilla's list directly, and grabNetworkableRegistries
    // (only called from that patch) isn't ported.
    private static final Map<ResourceKey<? extends Registry<?>>, Consumer<?>> REGISTRY_BUILDER_CONSUMERS = new HashMap<>();
    private static final List<RegistryDataLoader.RegistryData<?>> DATA_PACK_REGISTRIES = new ArrayList<>(RegistryDataLoader.WORLDGEN_REGISTRIES);
    private static final List<RegistryDataLoader.RegistryData<?>> DATA_PACK_REGISTRIES_VIEW = Collections.unmodifiableList(DATA_PACK_REGISTRIES);
    private static final Set<ResourceKey<? extends Registry<?>>> SYNCED_CUSTOM_REGISTRIES = new HashSet<>();
    private static final Set<ResourceKey<? extends Registry<?>>> SYNCED_CUSTOM_REGISTRIES_VIEW = Collections.unmodifiableSet(SYNCED_CUSTOM_REGISTRIES);

    /* Internal forge method, registers a datapack registry codec and folder. */
    static <T> void addRegistryCodec(DataPackRegistryEvent.DataPackRegistryData<T> data) {
        RegistryDataLoader.RegistryData<T> loaderData = data.loaderData();
        DATA_PACK_REGISTRIES.add(loaderData);
        // VersionUnbound: Fabric API's dynamic registries load and sync the registry, as NeoForge's patches to
        // RegistryDataLoader and RegistrySynchronization do with these lists.
        if (data.registryBuilderConsumer() != null) {
            REGISTRY_BUILDER_CONSUMERS.put(loaderData.key(), data.registryBuilderConsumer());
        }
        if (data.networkCodec() != null) {
            SYNCED_CUSTOM_REGISTRIES.add(loaderData.key());
            DynamicRegistries.registerSynced(loaderData.key(), loaderData.elementCodec(), data.networkCodec());
        } else {
            DynamicRegistries.register(loaderData.key(), loaderData.elementCodec());
        }
    }

    /**
     * {@return An unmodifiable view of the list of datapack registries}.
     * These registries are loaded from per-world datapacks on server startup.
     */
    public static List<RegistryDataLoader.RegistryData<?>> getDataPackRegistries() {
        return DATA_PACK_REGISTRIES_VIEW;
    }

    public static Stream<RegistryDataLoader.RegistryData<?>> getDataPackRegistriesWithDimensions() {
        return Stream.concat(DATA_PACK_REGISTRIES_VIEW.stream(), RegistryDataLoader.DIMENSION_REGISTRIES.stream());
    }

    /**
     * {@return An unmodifiable view of the set of synced non-vanilla datapack registry IDs}
     * Clients must have each of a server's synced datapack registries to be able to connect to that server;
     * vanilla clients therefore cannot connect if this list is non-empty on the server.
     */
    public static Set<ResourceKey<? extends Registry<?>>> getSyncedCustomRegistries() {
        return SYNCED_CUSTOM_REGISTRIES_VIEW;
    }

    @ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public static <T> RegistryDataLoader.@Nullable RegistryData<T> getSyncedRegistry(final ResourceKey<? extends Registry<T>> registry) {
        // VersionUnbound: vanilla's list, which holds the synced custom registries too (see NETWORKABLE_REGISTRIES above).
        return (RegistryDataLoader.RegistryData<T>) RegistryDataLoader.SYNCHRONIZED_REGISTRIES.stream().filter(data -> data.key().equals(registry)).findFirst().orElse(null);
    }

    /**
     * VersionUnbound: the {@link RegistryBuilder} options a datapack registry was registered with, if any. NeoForge keeps
     * them in {@code RegistryDataLoader.RegistryData}; {@code RegistryLoadTaskMixin} applies them.
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable Consumer<RegistryBuilder<T>> getRegistryBuilderConsumer(ResourceKey<? extends Registry<T>> registry) {
        return (Consumer<RegistryBuilder<T>>) REGISTRY_BUILDER_CONSUMERS.get(registry);
    }
}
