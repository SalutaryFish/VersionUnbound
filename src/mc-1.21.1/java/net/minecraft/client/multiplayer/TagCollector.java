package net.minecraft.client.multiplayer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public class TagCollector {
    private final Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags = new HashMap<>();

    public void append(ResourceKey<? extends Registry<?>> registryKey, TagNetworkSerialization.NetworkPayload networkPayload) {
        this.tags.put(registryKey, networkPayload);
    }

    private static void refreshBuiltInTagDependentData() {
        AbstractFurnaceBlockEntity.invalidateCache();
        Blocks.rebuildCache();
    }

    private void applyTags(RegistryAccess registryAccess, Predicate<ResourceKey<? extends Registry<?>>> filter) {
        this.tags.forEach((resourceKey, networkPayload) -> {
            if (filter.test((ResourceKey<? extends Registry<?>>)resourceKey)) {
                networkPayload.applyToRegistry(registryAccess.registryOrThrow((ResourceKey<? extends Registry<?>>)resourceKey));
            }
        });
    }

    public void updateTags(RegistryAccess registryAccess, boolean isMemoryConnection) {
        if (isMemoryConnection) {
            this.applyTags(registryAccess, RegistrySynchronization.NETWORKABLE_REGISTRIES::contains);
        } else {
            registryAccess.registries()
                .filter(registryEntry -> !RegistrySynchronization.NETWORKABLE_REGISTRIES.contains(registryEntry.key()))
                .forEach(registryEntry -> registryEntry.value().resetTags());
            this.applyTags(registryAccess, resourceKey -> true);
            refreshBuiltInTagDependentData();
        }
    }
}
