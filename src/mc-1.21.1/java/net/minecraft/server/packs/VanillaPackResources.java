package net.minecraft.server.packs;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.slf4j.Logger;

public class VanillaPackResources implements PackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;
    private final BuiltInMetadata metadata;
    private final Set<String> namespaces;
    private final List<Path> rootPaths;
    private final Map<PackType, List<Path>> pathsForType;

    VanillaPackResources(
        PackLocationInfo location, BuiltInMetadata metadata, Set<String> namespaces, List<Path> rootPaths, Map<PackType, List<Path>> pathsForType
    ) {
        this.location = location;
        this.metadata = metadata;
        this.namespaces = namespaces;
        this.rootPaths = rootPaths;
        this.pathsForType = pathsForType;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        FileUtil.validatePath(elements);
        List<String> list = List.of(elements);

        for (Path path : this.rootPaths) {
            Path path2 = FileUtil.resolvePath(path, list);
            if (Files.exists(path2) && PathPackResources.validatePath(path2)) {
                return IoSupplier.create(path2);
            }
        }

        return null;
    }

    public void listRawPaths(PackType packType, ResourceLocation packLocation, Consumer<Path> output) {
        FileUtil.decomposePath(packLocation.getPath()).ifSuccess(elements -> {
            String string = packLocation.getNamespace();

            for (Path path : this.pathsForType.get(packType)) {
                Path path2 = path.resolve(string);
                output.accept(FileUtil.resolvePath(path2, (List<String>)elements));
            }
        }).ifError(error -> LOGGER.error("Invalid path {}: {}", packLocation, error.message()));
    }

    @Override
    public void listResources(PackType packType, String namespace, String path, PackResources.ResourceOutput resourceOutput) {
        FileUtil.decomposePath(path).ifSuccess(paths -> {
            List<Path> list = this.pathsForType.get(packType);
            int i = list.size();
            if (i == 1) {
                getResources(resourceOutput, namespace, list.get(0), (List<String>)paths);
            } else if (i > 1) {
                Map<ResourceLocation, IoSupplier<InputStream>> map = new HashMap<>();

                for (int j = 0; j < i - 1; j++) {
                    getResources(map::putIfAbsent, namespace, list.get(j), (List<String>)paths);
                }

                Path pathx = list.get(i - 1);
                if (map.isEmpty()) {
                    getResources(resourceOutput, namespace, pathx, (List<String>)paths);
                } else {
                    getResources(map::putIfAbsent, namespace, pathx, (List<String>)paths);
                    map.forEach(resourceOutput);
                }
            }
        }).ifError(error -> LOGGER.error("Invalid path {}: {}", path, error.message()));
    }

    private static void getResources(PackResources.ResourceOutput resourceOutput, String namespace, Path root, List<String> paths) {
        Path path = root.resolve(namespace);
        PathPackResources.listPath(namespace, path, paths, resourceOutput);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
        return FileUtil.decomposePath(location.getPath()).mapOrElse(paths -> {
            String string = location.getNamespace();

            for (Path path : this.pathsForType.get(packType)) {
                Path path2 = FileUtil.resolvePath(path.resolve(string), (List<String>)paths);
                if (Files.exists(path2) && PathPackResources.validatePath(path2)) {
                    return IoSupplier.create(path2);
                }
            }

            return null;
        }, error -> {
            LOGGER.error("Invalid path {}: {}", location, error.message());
            return null;
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return this.namespaces;
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        IoSupplier<InputStream> ioSupplier = this.getRootResource("pack.mcmeta");
        if (ioSupplier != null) {
            try (InputStream inputStream = ioSupplier.get()) {
                T object = AbstractPackResources.getMetadataFromStream(deserializer, inputStream);
                if (object != null) {
                    return object;
                }
            } catch (IOException var8) {
            }
        }

        return this.metadata.get(deserializer);
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }

    @Override
    public void close() {
    }

    public ResourceProvider asProvider() {
        return location -> Optional.ofNullable(this.getResource(PackType.CLIENT_RESOURCES, location))
            .map(ioSupplier -> new Resource(this, (IoSupplier<InputStream>)ioSupplier));
    }
}
