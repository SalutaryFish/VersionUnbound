package net.minecraft.client.multiplayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.tags.TagNetworkSerialization;

public class RegistryDataCollector {
    @Nullable
    private RegistryDataCollector.ContentsCollector contentsCollector;
    @Nullable
    private TagCollector tagCollector;

    public void appendContents(ResourceKey<? extends Registry<?>> registryKey, List<RegistrySynchronization.PackedRegistryEntry> registryEntries) {
        if (this.contentsCollector == null) {
            this.contentsCollector = new RegistryDataCollector.ContentsCollector();
        }

        this.contentsCollector.append(registryKey, registryEntries);
    }

    public void appendTags(Map<ResourceKey<? extends Registry<?>>, TagNetworkSerialization.NetworkPayload> tags) {
        if (this.tagCollector == null) {
            this.tagCollector = new TagCollector();
        }

        tags.forEach(this.tagCollector::append);
    }

    public RegistryAccess.Frozen collectGameRegistries(ResourceProvider resourceProvider, RegistryAccess registryAccess, boolean isMemoryConnection) {
        LayeredRegistryAccess<ClientRegistryLayer> layeredRegistryAccess = ClientRegistryLayer.createRegistryAccess();
        RegistryAccess registryAccess2;
        if (this.contentsCollector != null) {
            RegistryAccess.Frozen frozen = layeredRegistryAccess.getAccessForLoading(ClientRegistryLayer.REMOTE);
            RegistryAccess.Frozen frozen2 = this.contentsCollector.loadRegistries(resourceProvider, frozen).freeze();
            registryAccess2 = layeredRegistryAccess.replaceFrom(ClientRegistryLayer.REMOTE, frozen2).compositeAccess();
        } else {
            registryAccess2 = registryAccess;
        }

        if (this.tagCollector != null) {
            this.tagCollector.updateTags(registryAccess2, isMemoryConnection);
        }

        return registryAccess2.freeze();
    }

    static class ContentsCollector {
        private final Map<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> elements = new HashMap<>();

        public void append(ResourceKey<? extends Registry<?>> registryKey, List<RegistrySynchronization.PackedRegistryEntry> entries) {
            this.elements.computeIfAbsent(registryKey, resourceKey -> new ArrayList<>()).addAll(entries);
        }

        public RegistryAccess loadRegistries(ResourceProvider resourceProvider, RegistryAccess registryAccess) {
            return RegistryDataLoader.load(this.elements, resourceProvider, registryAccess, RegistryDataLoader.SYNCHRONIZED_REGISTRIES);
        }
    }
}
