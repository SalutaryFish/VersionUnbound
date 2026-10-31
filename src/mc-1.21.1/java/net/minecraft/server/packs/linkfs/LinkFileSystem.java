package net.minecraft.server.packs.linkfs;

import com.google.common.base.Splitter;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class LinkFileSystem extends FileSystem {
    private static final Set<String> VIEWS = Set.of("basic");
    public static final String PATH_SEPARATOR = "/";
    private static final Splitter PATH_SPLITTER = Splitter.on('/');
    private final FileStore store;
    private final FileSystemProvider provider = new LinkFSProvider();
    private final LinkFSPath root;

    LinkFileSystem(String name, LinkFileSystem.DirectoryEntry root) {
        this.store = new LinkFSFileStore(name);
        this.root = buildPath(root, this, "", null);
    }

    private static LinkFSPath buildPath(LinkFileSystem.DirectoryEntry directory, LinkFileSystem fileSystem, String name, @Nullable LinkFSPath parent) {
        Object2ObjectOpenHashMap<String, LinkFSPath> object2ObjectOpenHashMap = new Object2ObjectOpenHashMap<>();
        LinkFSPath linkFSPath = new LinkFSPath(fileSystem, name, parent, new PathContents.DirectoryContents(object2ObjectOpenHashMap));
        directory.files
            .forEach(
                (fileName, path) -> object2ObjectOpenHashMap.put(
                    fileName, new LinkFSPath(fileSystem, fileName, linkFSPath, new PathContents.FileContents(path))
                )
            );
        directory.children.forEach((fileName, directoryx) -> object2ObjectOpenHashMap.put(fileName, buildPath(directoryx, fileSystem, fileName, linkFSPath)));
        object2ObjectOpenHashMap.trim();
        return linkFSPath;
    }

    @Override
    public FileSystemProvider provider() {
        return this.provider;
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return List.of(this.root);
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return List.of(this.store);
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return VIEWS;
    }

    @Override
    public Path getPath(String first, String... more) {
        Stream<String> stream = Stream.of(first);
        if (more.length > 0) {
            stream = Stream.concat(stream, Stream.of(more));
        }

        String string = stream.collect(Collectors.joining("/"));
        if (string.equals("/")) {
            return this.root;
        }

        if (string.startsWith("/")) {
            LinkFSPath linkFSPath = this.root;

            for (String string2 : PATH_SPLITTER.split(string.substring(1))) {
                if (string2.isEmpty()) {
                    throw new IllegalArgumentException("Empty paths not allowed");
                }

                linkFSPath = linkFSPath.resolveName(string2);
            }

            return linkFSPath;
        } else {
            LinkFSPath linkFSPath = null;

            for (String string2 : PATH_SPLITTER.split(string)) {
                if (string2.isEmpty()) {
                    throw new IllegalArgumentException("Empty paths not allowed");
                }

                linkFSPath = new LinkFSPath(this, string2, linkFSPath, PathContents.RELATIVE);
            }

            if (linkFSPath == null) {
                throw new IllegalArgumentException("Empty paths not allowed");
            } else {
                return linkFSPath;
            }
        }
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    public FileStore store() {
        return this.store;
    }

    public LinkFSPath rootPath() {
        return this.root;
    }

    public static LinkFileSystem.Builder builder() {
        return new LinkFileSystem.Builder();
    }

    public static class Builder {
        private final LinkFileSystem.DirectoryEntry root = new LinkFileSystem.DirectoryEntry();

        public LinkFileSystem.Builder put(List<String> pathString, String fileName, Path filePath) {
            LinkFileSystem.DirectoryEntry directoryEntry = this.root;

            for (String string : pathString) {
                directoryEntry = directoryEntry.children.computeIfAbsent(string, stringx -> new LinkFileSystem.DirectoryEntry());
            }

            directoryEntry.files.put(fileName, filePath);
            return this;
        }

        public LinkFileSystem.Builder put(List<String> pathString, Path filePath) {
            if (pathString.isEmpty()) {
                throw new IllegalArgumentException("Path can't be empty");
            }

            int i = pathString.size() - 1;
            return this.put(pathString.subList(0, i), pathString.get(i), filePath);
        }

        public FileSystem build(String name) {
            return new LinkFileSystem(name, this.root);
        }
    }

    record DirectoryEntry(Map<String, LinkFileSystem.DirectoryEntry> children, Map<String, Path> files) {
        public DirectoryEntry() {
            this(new HashMap<>(), new HashMap<>());
        }
    }
}
