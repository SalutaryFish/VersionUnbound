package net.minecraft.server.packs.resources;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;

public class FallbackResourceManager implements ResourceManager {
    static final Logger LOGGER = LogUtils.getLogger();
    protected final List<FallbackResourceManager.PackEntry> fallbacks = Lists.newArrayList();
    private final PackType type;
    private final String namespace;

    public FallbackResourceManager(PackType type, String namespace) {
        this.type = type;
        this.namespace = namespace;
    }

    public void push(PackResources resources) {
        this.pushInternal(resources.packId(), resources, null);
    }

    public void push(PackResources resources, Predicate<ResourceLocation> filter) {
        this.pushInternal(resources.packId(), resources, filter);
    }

    public void pushFilterOnly(String name, Predicate<ResourceLocation> filter) {
        this.pushInternal(name, null, filter);
    }

    private void pushInternal(String name, @Nullable PackResources resources, @Nullable Predicate<ResourceLocation> filter) {
        this.fallbacks.add(new FallbackResourceManager.PackEntry(name, resources, filter));
    }

    @Override
    public Set<String> getNamespaces() {
        return ImmutableSet.of(this.namespace);
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation location) {
        for (int i = this.fallbacks.size() - 1; i >= 0; i--) {
            FallbackResourceManager.PackEntry packEntry = this.fallbacks.get(i);
            PackResources packResources = packEntry.resources;
            if (packResources != null) {
                IoSupplier<InputStream> ioSupplier = packResources.getResource(this.type, location);
                if (ioSupplier != null) {
                    IoSupplier<ResourceMetadata> ioSupplier2 = this.createStackMetadataFinder(location, i);
                    return Optional.of(createResource(packResources, location, ioSupplier, ioSupplier2));
                }
            }

            if (packEntry.isFiltered(location)) {
                LOGGER.warn("Resource {} not found, but was filtered by pack {}", location, packEntry.name);
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static Resource createResource(
        PackResources source, ResourceLocation location, IoSupplier<InputStream> streamSupplier, IoSupplier<ResourceMetadata> metadataSupplier
    ) {
        return new Resource(source, wrapForDebug(location, source, streamSupplier), metadataSupplier);
    }

    private static IoSupplier<InputStream> wrapForDebug(ResourceLocation location, PackResources packResources, IoSupplier<InputStream> stream) {
        return LOGGER.isDebugEnabled()
            ? () -> new FallbackResourceManager.LeakedResourceWarningInputStream(stream.get(), location, packResources.packId())
            : stream;
    }

    @Override
    public List<Resource> getResourceStack(ResourceLocation location) {
        ResourceLocation resourceLocation = getMetadataLocation(location);
        List<Resource> list = new ArrayList<>();
        boolean bl = false;
        String string = null;

        for (int i = this.fallbacks.size() - 1; i >= 0; i--) {
            FallbackResourceManager.PackEntry packEntry = this.fallbacks.get(i);
            PackResources packResources = packEntry.resources;
            if (packResources != null) {
                IoSupplier<InputStream> ioSupplier = packResources.getResource(this.type, location);
                if (ioSupplier != null) {
                    IoSupplier<ResourceMetadata> ioSupplier2;
                    if (bl) {
                        ioSupplier2 = ResourceMetadata.EMPTY_SUPPLIER;
                    } else {
                        ioSupplier2 = () -> {
                            IoSupplier<InputStream> ioSupplierx = packResources.getResource(this.type, resourceLocation);
                            return ioSupplierx != null ? parseMetadata(ioSupplierx) : ResourceMetadata.EMPTY;
                        };
                    }

                    list.add(new Resource(packResources, ioSupplier, ioSupplier2));
                }
            }

            if (packEntry.isFiltered(location)) {
                string = packEntry.name;
                break;
            }

            if (packEntry.isFiltered(resourceLocation)) {
                bl = true;
            }
        }

        if (list.isEmpty() && string != null) {
            LOGGER.warn("Resource {} not found, but was filtered by pack {}", location, string);
        }

        return Lists.reverse(list);
    }

    private static boolean isMetadata(ResourceLocation location) {
        return location.getPath().endsWith(".mcmeta");
    }

    private static ResourceLocation getResourceLocationFromMetadata(ResourceLocation metadataResourceLocation) {
        String string = metadataResourceLocation.getPath().substring(0, metadataResourceLocation.getPath().length() - ".mcmeta".length());
        return metadataResourceLocation.withPath(string);
    }

    static ResourceLocation getMetadataLocation(ResourceLocation location) {
        return location.withPath(location.getPath() + ".mcmeta");
    }

    @Override
    public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
        record ResourceWithSourceAndIndex(PackResources packResources, IoSupplier<InputStream> resource, int packIndex) {
        }

        Map<ResourceLocation, ResourceWithSourceAndIndex> map = new HashMap<>();
        Map<ResourceLocation, ResourceWithSourceAndIndex> map2 = new HashMap<>();
        int i = this.fallbacks.size();

        for (int j = 0; j < i; j++) {
            FallbackResourceManager.PackEntry packEntry = this.fallbacks.get(j);
            packEntry.filterAll(map.keySet());
            packEntry.filterAll(map2.keySet());
            PackResources packResources = packEntry.resources;
            if (packResources != null) {
                int k = j;
                packResources.listResources(this.type, this.namespace, path, (pathx, inputStream) -> {
                    if (isMetadata(pathx)) {
                        if (filter.test(getResourceLocationFromMetadata(pathx))) {
                            map2.put(pathx, new ResourceWithSourceAndIndex(packResources, inputStream, k));
                        }
                    } else if (filter.test(pathx)) {
                        map.put(pathx, new ResourceWithSourceAndIndex(packResources, inputStream, k));
                    }
                });
            }
        }

        Map<ResourceLocation, Resource> map3 = Maps.newTreeMap();
        map.forEach((pathx, resource) -> {
            ResourceLocation resourceLocation = getMetadataLocation(pathx);
            ResourceWithSourceAndIndex lv = map2.get(resourceLocation);
            IoSupplier<ResourceMetadata> ioSupplier;
            if (lv != null && lv.packIndex >= resource.packIndex) {
                ioSupplier = convertToMetadata(lv.resource);
            } else {
                ioSupplier = ResourceMetadata.EMPTY_SUPPLIER;
            }

            map3.put(pathx, createResource(resource.packResources, pathx, resource.resource, ioSupplier));
        });
        return map3;
    }

    private IoSupplier<ResourceMetadata> createStackMetadataFinder(ResourceLocation location, int fallbackIndex) {
        return () -> {
            ResourceLocation resourceLocation2 = getMetadataLocation(location);

            for (int j = this.fallbacks.size() - 1; j >= fallbackIndex; j--) {
                FallbackResourceManager.PackEntry packEntry = this.fallbacks.get(j);
                PackResources packResources = packEntry.resources;
                if (packResources != null) {
                    IoSupplier<InputStream> ioSupplier = packResources.getResource(this.type, resourceLocation2);
                    if (ioSupplier != null) {
                        return parseMetadata(ioSupplier);
                    }
                }

                if (packEntry.isFiltered(resourceLocation2)) {
                    break;
                }
            }

            return ResourceMetadata.EMPTY;
        };
    }

    private static IoSupplier<ResourceMetadata> convertToMetadata(IoSupplier<InputStream> streamSupplier) {
        return () -> parseMetadata(streamSupplier);
    }

    private static ResourceMetadata parseMetadata(IoSupplier<InputStream> streamSupplier) throws IOException {
        try (InputStream inputStream = streamSupplier.get()) {
            return ResourceMetadata.fromJsonStream(inputStream);
        }
    }

    private static void applyPackFiltersToExistingResources(
        FallbackResourceManager.PackEntry packEntry, Map<ResourceLocation, FallbackResourceManager.EntryStack> resources
    ) {
        for (FallbackResourceManager.EntryStack entryStack : resources.values()) {
            if (packEntry.isFiltered(entryStack.fileLocation)) {
                entryStack.fileSources.clear();
            } else if (packEntry.isFiltered(entryStack.metadataLocation())) {
                entryStack.metaSources.clear();
            }
        }
    }

    private void listPackResources(
        FallbackResourceManager.PackEntry entry,
        String path,
        Predicate<ResourceLocation> filter,
        Map<ResourceLocation, FallbackResourceManager.EntryStack> output
    ) {
        PackResources packResources = entry.resources;
        if (packResources != null) {
            packResources.listResources(
                this.type,
                this.namespace,
                path,
                (pathx, inputStream) -> {
                    if (isMetadata(pathx)) {
                        ResourceLocation resourceLocation = getResourceLocationFromMetadata(pathx);
                        if (!filter.test(resourceLocation)) {
                            return;
                        }

                        output.computeIfAbsent(resourceLocation, FallbackResourceManager.EntryStack::new).metaSources.put(packResources, inputStream);
                    } else {
                        if (!filter.test(pathx)) {
                            return;
                        }

                        output.computeIfAbsent(pathx, FallbackResourceManager.EntryStack::new)
                            .fileSources
                            .add(new FallbackResourceManager.ResourceWithSource(packResources, inputStream));
                    }
                }
            );
        }
    }

    @Override
    public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
        Map<ResourceLocation, FallbackResourceManager.EntryStack> map = Maps.newHashMap();

        for (FallbackResourceManager.PackEntry packEntry : this.fallbacks) {
            applyPackFiltersToExistingResources(packEntry, map);
            this.listPackResources(packEntry, path, filter, map);
        }

        TreeMap<ResourceLocation, List<Resource>> treeMap = Maps.newTreeMap();

        for (FallbackResourceManager.EntryStack entryStack : map.values()) {
            if (!entryStack.fileSources.isEmpty()) {
                List<Resource> list = new ArrayList<>();

                for (FallbackResourceManager.ResourceWithSource resourceWithSource : entryStack.fileSources) {
                    PackResources packResources = resourceWithSource.source;
                    IoSupplier<InputStream> ioSupplier = entryStack.metaSources.get(packResources);
                    IoSupplier<ResourceMetadata> ioSupplier2 = ioSupplier != null ? convertToMetadata(ioSupplier) : ResourceMetadata.EMPTY_SUPPLIER;
                    list.add(createResource(packResources, entryStack.fileLocation, resourceWithSource.resource, ioSupplier2));
                }

                treeMap.put(entryStack.fileLocation, list);
            }
        }

        return treeMap;
    }

    @Override
    public Stream<PackResources> listPacks() {
        return this.fallbacks.stream().map(fallback -> fallback.resources).filter(Objects::nonNull);
    }

    record EntryStack(
        ResourceLocation fileLocation,
        ResourceLocation metadataLocation,
        List<FallbackResourceManager.ResourceWithSource> fileSources,
        Map<PackResources, IoSupplier<InputStream>> metaSources
    ) {
        EntryStack(ResourceLocation fileLocation) {
            this(fileLocation, FallbackResourceManager.getMetadataLocation(fileLocation), new ArrayList<>(), new Object2ObjectArrayMap<>());
        }
    }

    static class LeakedResourceWarningInputStream extends FilterInputStream {
        private final Supplier<String> message;
        private boolean closed;

        public LeakedResourceWarningInputStream(InputStream inputStream, ResourceLocation resourceLocation, String packName) {
            super(inputStream);
            Exception exception = new Exception("Stacktrace");
            this.message = () -> {
                StringWriter stringWriter = new StringWriter();
                exception.printStackTrace(new PrintWriter(stringWriter));
                return "Leaked resource: '" + resourceLocation + "' loaded from pack: '" + packName + "'\n" + stringWriter;
            };
        }

        @Override
        public void close() throws IOException {
            super.close();
            this.closed = true;
        }

        @Override
        protected void finalize() throws Throwable {
            if (!this.closed) {
                FallbackResourceManager.LOGGER.warn("{}", this.message.get());
            }

            super.finalize();
        }
    }

    record PackEntry(String name, @Nullable PackResources resources, @Nullable Predicate<ResourceLocation> filter) {
        public void filterAll(Collection<ResourceLocation> locations) {
            if (this.filter != null) {
                locations.removeIf(this.filter);
            }
        }

        public boolean isFiltered(ResourceLocation location) {
            return this.filter != null && this.filter.test(location);
        }
    }

    record ResourceWithSource(PackResources source, IoSupplier<InputStream> resource) {
    }
}
