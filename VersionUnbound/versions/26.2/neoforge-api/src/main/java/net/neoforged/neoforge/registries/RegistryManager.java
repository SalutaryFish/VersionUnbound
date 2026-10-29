/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.registries;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.fml.ModLoader;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

@ApiStatus.Internal
public class RegistryManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Marker REGISTRIES = MarkerFactory.getMarker("REGISTRIES");
    private static Set<Identifier> pendingModdedRegistries = new HashSet<>();
    private static Set<Identifier> vanillaRegistryKeys = Set.of();
    private static Set<Identifier> newRegistryKeys = Set.of(); // VersionUnbound: see getNewRegistryKeys
    // VersionUnbound: no registry snapshots. NeoForge uses them for its own registry sync (the server's snapshot is applied
    // on the client and reverted on disconnect) and to roll back a failed registration. Fabric API's registry sync does the
    // first, and a failed registration stops the game on Fabric anyway.
    private static Map<ResourceKey<Registry<?>>, Map<Identifier, DataMapType<?, ?>>> dataMaps = Map.of();

    /**
     * Called by {@link RegistryBuilder} to make sure that modders don't forget to register their registries.
     */
    static synchronized void trackModdedRegistry(Identifier registry) {
        Objects.requireNonNull(registry);

        if (pendingModdedRegistries == null) {
            throw new IllegalStateException("Attempting to instantiate registry with name " + registry + " after NewRegistryEvent was fired!");
        }

        if (!pendingModdedRegistries.add(registry)) {
            throw new IllegalStateException("Registry with name " + registry + " was already instantiated once, cannot instantiate it again!");
        }
    }

    @Nullable
    public static <R> DataMapType<R, ?> getDataMap(ResourceKey<? extends Registry<R>> registry, Identifier key) {
        final var map = dataMaps.get(registry);
        return map == null ? null : (DataMapType<R, ?>) map.get(key);
    }

    /**
     * {@return a view of all registered data maps}
     */
    public static Map<ResourceKey<Registry<?>>, Map<Identifier, DataMapType<?, ?>>> getDataMaps() {
        return dataMaps;
    }

    public static void postNewRegistryEvent() {
        NewRegistryEvent event = new NewRegistryEvent();
        DataPackRegistryEvent.NewRegistry dataPackEvent = new DataPackRegistryEvent.NewRegistry();
        vanillaRegistryKeys = Set.copyOf(BuiltInRegistries.REGISTRY.keySet());

        ModLoader.postEventWrapContainerInModOrder(event);
        ModLoader.postEventWrapContainerInModOrder(dataPackEvent);

        event.fill();
        newRegistryKeys = BuiltInRegistries.REGISTRY.keySet().stream().filter(key -> !vanillaRegistryKeys.contains(key)).collect(Collectors.toUnmodifiableSet());
        dataPackEvent.process();

        ModLoader.postEvent(new ModifyRegistriesEvent());

        pendingModdedRegistries.removeIf(BuiltInRegistries.REGISTRY::containsKey);
        if (!pendingModdedRegistries.isEmpty()) {
            throw new IllegalStateException("The following registries were created but not registered to NewRegistryEvent:"
                    + pendingModdedRegistries.stream().map(Identifier::toString).collect(Collectors.joining("\n\t - ", "\n\t - ", "")));
        }
        pendingModdedRegistries = null;
    }

    public static void initDataMaps() {
        final Map<ResourceKey<Registry<?>>, Map<Identifier, DataMapType<?, ?>>> dataMapTypes = new HashMap<>();
        ModLoader.postEvent(new RegisterDataMapTypesEvent(dataMapTypes));
        dataMaps = new IdentityHashMap<>();
        dataMapTypes.forEach((key, values) -> dataMaps.put(key, Collections.unmodifiableMap(values)));
        dataMaps = Collections.unmodifiableMap(dataMapTypes);
    }

    public static List<Identifier> getRegistryNamesForSyncToClient() {
        List<Identifier> list = new ArrayList<>();

        BuiltInRegistries.REGISTRY.entrySet().forEach(e -> {
            if (e.getValue().doesSync())
                list.add(e.getKey().identifier());
        });

        return list;
    }

    public static Set<Identifier> getVanillaRegistryKeys() {
        return vanillaRegistryKeys;
    }

    /**
     * VersionUnbound: the registries mods added in {@link NewRegistryEvent}. NeoForge never runs vanilla's registry
     * validation on these (it runs once, at bootstrap); Fabric API runs it again when it freezes the registries, so
     * {@code BuiltInRegistriesMixin} leaves these out, as they may stay empty.
     */
    public static Set<Identifier> getNewRegistryKeys() {
        return newRegistryKeys;
    }

    public static boolean isNonSyncedBuiltInRegistry(Registry<?> registry) {
        if (!BuiltInRegistries.REGISTRY.containsKey((ResourceKey) registry.key())) {
            return false; // Dynamic registry
        }
        return !registry.doesSync();
    }
}
