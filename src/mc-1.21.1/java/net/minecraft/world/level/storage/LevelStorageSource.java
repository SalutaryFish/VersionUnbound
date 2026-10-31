package net.minecraft.world.level.storage;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.Lifecycle;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.FileUtil;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtFormatException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.nbt.visitors.SkipFields;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.DirectoryLock;
import net.minecraft.util.MemoryReserve;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.validation.ContentValidationException;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;
import net.minecraft.world.level.validation.PathAllowList;
import org.slf4j.Logger;

public class LevelStorageSource {
    static final Logger LOGGER = LogUtils.getLogger();
    static final DateTimeFormatter FORMATTER = FileNameDateFormatter.create();
    private static final String TAG_DATA = "Data";
    private static final PathMatcher NO_SYMLINKS_ALLOWED = path -> false;
    public static final String ALLOWED_SYMLINKS_CONFIG_NAME = "allowed_symlinks.txt";
    private static final int UNCOMPRESSED_NBT_QUOTA = 104857600;
    private static final int DISK_SPACE_WARNING_THRESHOLD = 67108864;
    private final Path baseDir;
    private final Path backupDir;
    final DataFixer fixerUpper;
    private final DirectoryValidator worldDirValidator;

    public LevelStorageSource(Path baseDir, Path backupDir, DirectoryValidator worldDirValidator, DataFixer fixerUpper) {
        this.fixerUpper = fixerUpper;

        try {
            FileUtil.createDirectoriesSafe(baseDir);
        } catch (IOException iOException) {
            throw new UncheckedIOException(iOException);
        }

        this.baseDir = baseDir;
        this.backupDir = backupDir;
        this.worldDirValidator = worldDirValidator;
    }

    public static DirectoryValidator parseValidator(Path validator) {
        if (Files.exists(validator)) {
            try (BufferedReader bufferedReader = Files.newBufferedReader(validator)) {
                return new DirectoryValidator(PathAllowList.readPlain(bufferedReader));
            } catch (Exception exception) {
                LOGGER.error("Failed to parse {}, disallowing all symbolic links", "allowed_symlinks.txt", exception);
            }
        }

        return new DirectoryValidator(NO_SYMLINKS_ALLOWED);
    }

    public static LevelStorageSource createDefault(Path savesDir) {
        DirectoryValidator directoryValidator = parseValidator(savesDir.resolve("allowed_symlinks.txt"));
        return new LevelStorageSource(savesDir, savesDir.resolve("../backups"), directoryValidator, DataFixers.getDataFixer());
    }

    public static WorldDataConfiguration readDataConfig(Dynamic<?> dynamic) {
        return WorldDataConfiguration.CODEC.parse(dynamic).resultOrPartial(LOGGER::error).orElse(WorldDataConfiguration.DEFAULT);
    }

    public static WorldLoader.PackConfig getPackConfig(Dynamic<?> dynamic, PackRepository packRepository, boolean safeMode) {
        return new WorldLoader.PackConfig(packRepository, readDataConfig(dynamic), safeMode, false);
    }

    public static LevelDataAndDimensions getLevelDataAndDimensions(
        Dynamic<?> dynamic, WorldDataConfiguration dataConfiguration, Registry<LevelStem> levelStemRegistry, RegistryAccess.Frozen registry
    ) {
        Dynamic<?> dynamic2 = RegistryOps.injectRegistryContext(dynamic, registry);
        Dynamic<?> dynamic3 = dynamic2.get("WorldGenSettings").orElseEmptyMap();
        WorldGenSettings worldGenSettings = WorldGenSettings.CODEC.parse(dynamic3).getOrThrow();
        LevelSettings levelSettings = LevelSettings.parse(dynamic2, dataConfiguration);
        WorldDimensions.Complete complete = worldGenSettings.dimensions().bake(levelStemRegistry);
        Lifecycle lifecycle = complete.lifecycle().add(registry.allRegistriesLifecycle());
        PrimaryLevelData primaryLevelData = PrimaryLevelData.parse(
            dynamic2, levelSettings, complete.specialWorldProperty(), worldGenSettings.options(), lifecycle
        );
        return new LevelDataAndDimensions(primaryLevelData, complete);
    }

    public String getName() {
        return "Anvil";
    }

    public LevelStorageSource.LevelCandidates findLevelCandidates() throws LevelStorageException {
        if (!Files.isDirectory(this.baseDir)) {
            throw new LevelStorageException(Component.translatable("selectWorld.load_folder_access"));
        }

        try (Stream<Path> stream = Files.list(this.baseDir)) {
            List<LevelStorageSource.LevelDirectory> list = stream.filter(path -> Files.isDirectory(path))
                .map(LevelStorageSource.LevelDirectory::new)
                .filter(levelDirectory -> Files.isRegularFile(levelDirectory.dataFile()) || Files.isRegularFile(levelDirectory.oldDataFile()))
                .toList();
            return new LevelStorageSource.LevelCandidates(list);
        } catch (IOException iOException) {
            throw new LevelStorageException(Component.translatable("selectWorld.load_folder_access"));
        }
    }

    public CompletableFuture<List<LevelSummary>> loadLevelSummaries(LevelStorageSource.LevelCandidates candidates) {
        List<CompletableFuture<LevelSummary>> list = new ArrayList<>(candidates.levels.size());

        for (LevelStorageSource.LevelDirectory levelDirectory : candidates.levels) {
            list.add(CompletableFuture.supplyAsync(() -> {
                boolean bl;
                try {
                    bl = DirectoryLock.isLocked(levelDirectory.path());
                } catch (Exception exception) {
                    LOGGER.warn("Failed to read {} lock", levelDirectory.path(), exception);
                    return null;
                }

                try {
                    return this.readLevelSummary(levelDirectory, bl);
                } catch (OutOfMemoryError outOfMemoryError) {
                    MemoryReserve.release();
                    System.gc();
                    String string = "Ran out of memory trying to read summary of world folder \"" + levelDirectory.directoryName() + "\"";
                    LOGGER.error(LogUtils.FATAL_MARKER, string);
                    OutOfMemoryError outOfMemoryError2 = new OutOfMemoryError("Ran out of memory reading level data");
                    outOfMemoryError2.initCause(outOfMemoryError);
                    CrashReport crashReport = CrashReport.forThrowable(outOfMemoryError2, string);
                    CrashReportCategory crashReportCategory = crashReport.addCategory("World details");
                    crashReportCategory.setDetail("Folder Name", levelDirectory.directoryName());

                    try {
                        long l = Files.size(levelDirectory.dataFile());
                        crashReportCategory.setDetail("level.dat size", l);
                    } catch (IOException iOException) {
                        crashReportCategory.setDetailError("level.dat size", iOException);
                    }

                    throw new ReportedException(crashReport);
                }
            }, Util.backgroundExecutor()));
        }

        return Util.sequenceFailFastAndCancel(list).thenApply(listx -> listx.stream().filter(Objects::nonNull).sorted().toList());
    }

    private int getStorageVersion() {
        return 19133;
    }

    static CompoundTag readLevelDataTagRaw(Path levelPath) throws IOException {
        return NbtIo.readCompressed(levelPath, NbtAccounter.create(104857600L));
    }

    static Dynamic<?> readLevelDataTagFixed(Path levelPath, DataFixer dataFixer) throws IOException {
        CompoundTag compoundTag = readLevelDataTagRaw(levelPath);
        CompoundTag compoundTag2 = compoundTag.getCompound("Data");
        int i = NbtUtils.getDataVersion(compoundTag2, -1);
        Dynamic<?> dynamic = DataFixTypes.LEVEL.updateToCurrentVersion(dataFixer, new Dynamic<>(NbtOps.INSTANCE, compoundTag2), i);
        dynamic = dynamic.update("Player", dynamicx -> DataFixTypes.PLAYER.updateToCurrentVersion(dataFixer, dynamicx, i));
        return dynamic.update("WorldGenSettings", dynamicx -> DataFixTypes.WORLD_GEN_SETTINGS.updateToCurrentVersion(dataFixer, dynamicx, i));
    }

    private LevelSummary readLevelSummary(LevelStorageSource.LevelDirectory levelDirectory, boolean locked) {
        Path path = levelDirectory.dataFile();
        if (Files.exists(path)) {
            try {
                if (Files.isSymbolicLink(path)) {
                    List<ForbiddenSymlinkInfo> list = this.worldDirValidator.validateSymlink(path);
                    if (!list.isEmpty()) {
                        LOGGER.warn("{}", ContentValidationException.getMessage(path, list));
                        return new LevelSummary.SymlinkLevelSummary(levelDirectory.directoryName(), levelDirectory.iconFile());
                    }
                }

                if (readLightweightData(path) instanceof CompoundTag compoundTag) {
                    CompoundTag compoundTag2 = compoundTag.getCompound("Data");
                    int i = NbtUtils.getDataVersion(compoundTag2, -1);
                    Dynamic<?> dynamic = DataFixTypes.LEVEL.updateToCurrentVersion(this.fixerUpper, new Dynamic<>(NbtOps.INSTANCE, compoundTag2), i);
                    return this.makeLevelSummary(dynamic, levelDirectory, locked);
                }

                LOGGER.warn("Invalid root tag in {}", path);
            } catch (Exception exception) {
                LOGGER.error("Exception reading {}", path, exception);
            }
        }

        return new LevelSummary.CorruptedLevelSummary(levelDirectory.directoryName(), levelDirectory.iconFile(), getFileModificationTime(levelDirectory));
    }

    private static long getFileModificationTime(LevelStorageSource.LevelDirectory levelDirectory) {
        Instant instant = getFileModificationTime(levelDirectory.dataFile());
        if (instant == null) {
            instant = getFileModificationTime(levelDirectory.oldDataFile());
        }

        return instant == null ? -1L : instant.toEpochMilli();
    }

    @Nullable
    static Instant getFileModificationTime(Path dataFilePath) {
        try {
            return Files.getLastModifiedTime(dataFilePath).toInstant();
        } catch (IOException var2) {
            return null;
        }
    }

    LevelSummary makeLevelSummary(Dynamic<?> dynamic, LevelStorageSource.LevelDirectory levelDirectory, boolean locked) {
        LevelVersion levelVersion = LevelVersion.parse(dynamic);
        int i = levelVersion.levelDataVersion();
        if (i != 19132 && i != 19133) {
            throw new NbtFormatException("Unknown data version: " + Integer.toHexString(i));
        }

        boolean bl = i != this.getStorageVersion();
        Path path = levelDirectory.iconFile();
        WorldDataConfiguration worldDataConfiguration = readDataConfig(dynamic);
        LevelSettings levelSettings = LevelSettings.parse(dynamic, worldDataConfiguration);
        FeatureFlagSet featureFlagSet = parseFeatureFlagsFromSummary(dynamic);
        boolean bl2 = FeatureFlags.isExperimental(featureFlagSet);
        return new LevelSummary(levelSettings, levelVersion, levelDirectory.directoryName(), bl, locked, bl2, path);
    }

    private static FeatureFlagSet parseFeatureFlagsFromSummary(Dynamic<?> dataDynamic) {
        Set<ResourceLocation> set = dataDynamic.get("enabled_features")
            .asStream()
            .flatMap(dynamic -> dynamic.asString().result().map(ResourceLocation::tryParse).stream())
            .collect(Collectors.toSet());
        return FeatureFlags.REGISTRY.fromNames(set, resourceLocation -> {});
    }

    @Nullable
    private static Tag readLightweightData(Path file) throws IOException {
        SkipFields skipFields = new SkipFields(
            new FieldSelector("Data", CompoundTag.TYPE, "Player"), new FieldSelector("Data", CompoundTag.TYPE, "WorldGenSettings")
        );
        NbtIo.parseCompressed(file, skipFields, NbtAccounter.create(104857600L));
        return skipFields.getResult();
    }

    public boolean isNewLevelIdAcceptable(String saveName) {
        try {
            Path path = this.getLevelPath(saveName);
            Files.createDirectory(path);
            Files.deleteIfExists(path);
            return true;
        } catch (IOException iOException) {
            return false;
        }
    }

    /**
     * Return whether the given world can be loaded.
     */
    public boolean levelExists(String saveName) {
        try {
            return Files.isDirectory(this.getLevelPath(saveName));
        } catch (InvalidPathException invalidPathException) {
            return false;
        }
    }

    public Path getLevelPath(String saveName) {
        return this.baseDir.resolve(saveName);
    }

    public Path getBaseDir() {
        return this.baseDir;
    }

    public Path getBackupPath() {
        return this.backupDir;
    }

    public LevelStorageSource.LevelStorageAccess validateAndCreateAccess(String saveName) throws IOException, ContentValidationException {
        Path path = this.getLevelPath(saveName);
        List<ForbiddenSymlinkInfo> list = this.worldDirValidator.validateDirectory(path, true);
        if (!list.isEmpty()) {
            throw new ContentValidationException(path, list);
        } else {
            return new LevelStorageSource.LevelStorageAccess(saveName, path);
        }
    }

    public LevelStorageSource.LevelStorageAccess createAccess(String saveName) throws IOException {
        Path path = this.getLevelPath(saveName);
        return new LevelStorageSource.LevelStorageAccess(saveName, path);
    }

    public DirectoryValidator getWorldDirValidator() {
        return this.worldDirValidator;
    }

    public record LevelCandidates(List<LevelStorageSource.LevelDirectory> levels) implements Iterable<LevelStorageSource.LevelDirectory> {
        public boolean isEmpty() {
            return this.levels.isEmpty();
        }

        @Override
        public Iterator<LevelStorageSource.LevelDirectory> iterator() {
            return this.levels.iterator();
        }
    }

    public record LevelDirectory(Path path) {
        public String directoryName() {
            return this.path.getFileName().toString();
        }

        public Path dataFile() {
            return this.resourcePath(LevelResource.LEVEL_DATA_FILE);
        }

        public Path oldDataFile() {
            return this.resourcePath(LevelResource.OLD_LEVEL_DATA_FILE);
        }

        public Path corruptedDataFile(LocalDateTime dateTime) {
            return this.path.resolve(LevelResource.LEVEL_DATA_FILE.getId() + "_corrupted_" + dateTime.format(LevelStorageSource.FORMATTER));
        }

        public Path rawDataFile(LocalDateTime dateTime) {
            return this.path.resolve(LevelResource.LEVEL_DATA_FILE.getId() + "_raw_" + dateTime.format(LevelStorageSource.FORMATTER));
        }

        public Path iconFile() {
            return this.resourcePath(LevelResource.ICON_FILE);
        }

        public Path lockFile() {
            return this.resourcePath(LevelResource.LOCK_FILE);
        }

        public Path resourcePath(LevelResource resource) {
            return this.path.resolve(resource.getId());
        }
    }

    public class LevelStorageAccess implements AutoCloseable {
        final DirectoryLock lock;
        final LevelStorageSource.LevelDirectory levelDirectory;
        private final String levelId;
        private final Map<LevelResource, Path> resources = Maps.newHashMap();

        LevelStorageAccess(final String levelId, final Path levelDir) throws IOException {
            this.levelId = levelId;
            this.levelDirectory = new LevelStorageSource.LevelDirectory(levelDir);
            this.lock = DirectoryLock.create(levelDir);
        }

        public long estimateDiskSpace() {
            try {
                return Files.getFileStore(this.levelDirectory.path).getUsableSpace();
            } catch (Exception exception) {
                return Long.MAX_VALUE;
            }
        }

        public boolean checkForLowDiskSpace() {
            return this.estimateDiskSpace() < 67108864L;
        }

        public void safeClose() {
            try {
                this.close();
            } catch (IOException iOException) {
                LevelStorageSource.LOGGER.warn("Failed to unlock access to level {}", this.getLevelId(), iOException);
            }
        }

        public LevelStorageSource parent() {
            return LevelStorageSource.this;
        }

        public LevelStorageSource.LevelDirectory getLevelDirectory() {
            return this.levelDirectory;
        }

        public String getLevelId() {
            return this.levelId;
        }

        public Path getLevelPath(LevelResource folderName) {
            return this.resources.computeIfAbsent(folderName, this.levelDirectory::resourcePath);
        }

        public Path getDimensionPath(ResourceKey<Level> dimensionPath) {
            return DimensionType.getStorageFolder(dimensionPath, this.levelDirectory.path());
        }

        private void checkLock() {
            if (!this.lock.isValid()) {
                throw new IllegalStateException("Lock is no longer valid");
            }
        }

        public PlayerDataStorage createPlayerStorage() {
            this.checkLock();
            return new PlayerDataStorage(this, LevelStorageSource.this.fixerUpper);
        }

        public LevelSummary getSummary(Dynamic<?> dynamic) {
            this.checkLock();
            return LevelStorageSource.this.makeLevelSummary(dynamic, this.levelDirectory, false);
        }

        public Dynamic<?> getDataTag() throws IOException {
            return this.getDataTag(false);
        }

        public Dynamic<?> getDataTagFallback() throws IOException {
            return this.getDataTag(true);
        }

        private Dynamic<?> getDataTag(boolean useFallback) throws IOException {
            this.checkLock();
            return LevelStorageSource.readLevelDataTagFixed(
                useFallback ? this.levelDirectory.oldDataFile() : this.levelDirectory.dataFile(), LevelStorageSource.this.fixerUpper
            );
        }

        public void saveDataTag(RegistryAccess registries, WorldData serverConfiguration) {
            this.saveDataTag(registries, serverConfiguration, null);
        }

        public void saveDataTag(RegistryAccess registries, WorldData serverConfiguration, @Nullable CompoundTag hostPlayerNBT) {
            CompoundTag compoundTag = serverConfiguration.createTag(registries, hostPlayerNBT);
            CompoundTag compoundTag2 = new CompoundTag();
            compoundTag2.put("Data", compoundTag);
            this.saveLevelData(compoundTag2);
        }

        private void saveLevelData(CompoundTag tag) {
            Path path = this.levelDirectory.path();

            try {
                Path path2 = Files.createTempFile(path, "level", ".dat");
                NbtIo.writeCompressed(tag, path2);
                Path path3 = this.levelDirectory.oldDataFile();
                Path path4 = this.levelDirectory.dataFile();
                Util.safeReplaceFile(path4, path2, path3);
            } catch (Exception exception) {
                LevelStorageSource.LOGGER.error("Failed to save level {}", path, exception);
            }
        }

        public Optional<Path> getIconFile() {
            return !this.lock.isValid() ? Optional.empty() : Optional.of(this.levelDirectory.iconFile());
        }

        public void deleteLevel() throws IOException {
            this.checkLock();
            final Path path = this.levelDirectory.lockFile();
            LevelStorageSource.LOGGER.info("Deleting level {}", this.levelId);

            for (int i = 1; i <= 5; i++) {
                LevelStorageSource.LOGGER.info("Attempt {}...", i);

                try {
                    Files.walkFileTree(this.levelDirectory.path(), new SimpleFileVisitor<Path>() {
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            if (!file.equals(path)) {
                                LevelStorageSource.LOGGER.debug("Deleting {}", file);
                                Files.delete(file);
                            }

                            return FileVisitResult.CONTINUE;
                        }

                        public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exception) throws IOException {
                            if (exception != null) {
                                throw exception;
                            }

                            if (dir.equals(LevelStorageAccess.this.levelDirectory.path())) {
                                LevelStorageAccess.this.lock.close();
                                Files.deleteIfExists(path);
                            }

                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                    break;
                } catch (IOException iOException) {
                    if (i >= 5) {
                        throw iOException;
                    }

                    LevelStorageSource.LOGGER.warn("Failed to delete {}", this.levelDirectory.path(), iOException);

                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException var5) {
                    }
                }
            }
        }

        public void renameLevel(String saveName) throws IOException {
            this.modifyLevelDataWithoutDatafix(compoundTag -> compoundTag.putString("LevelName", saveName.trim()));
        }

        public void renameAndDropPlayer(String saveName) throws IOException {
            this.modifyLevelDataWithoutDatafix(compoundTag -> {
                compoundTag.putString("LevelName", saveName.trim());
                compoundTag.remove("Player");
            });
        }

        private void modifyLevelDataWithoutDatafix(Consumer<CompoundTag> modifier) throws IOException {
            this.checkLock();
            CompoundTag compoundTag = LevelStorageSource.readLevelDataTagRaw(this.levelDirectory.dataFile());
            modifier.accept(compoundTag.getCompound("Data"));
            this.saveLevelData(compoundTag);
        }

        public long makeWorldBackup() throws IOException {
            this.checkLock();
            String string = LocalDateTime.now().format(LevelStorageSource.FORMATTER) + "_" + this.levelId;
            Path path = LevelStorageSource.this.getBackupPath();

            try {
                FileUtil.createDirectoriesSafe(path);
            } catch (IOException iOException) {
                throw new RuntimeException(iOException);
            }

            Path path2 = path.resolve(FileUtil.findAvailableName(path, string, ".zip"));

            try (final ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(path2)))) {
                final Path path3 = Paths.get(this.levelId);
                Files.walkFileTree(this.levelDirectory.path(), new SimpleFileVisitor<Path>() {
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.endsWith("session.lock")) {
                            return FileVisitResult.CONTINUE;
                        }

                        String stringx = path3.resolve(LevelStorageAccess.this.levelDirectory.path().relativize(file)).toString().replace('\\', '/');
                        ZipEntry zipEntry = new ZipEntry(stringx);
                        zipOutputStream.putNextEntry(zipEntry);
                        com.google.common.io.Files.asByteSource(file.toFile()).copyTo(zipOutputStream);
                        zipOutputStream.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            return Files.size(path2);
        }

        public boolean hasWorldData() {
            return Files.exists(this.levelDirectory.dataFile()) || Files.exists(this.levelDirectory.oldDataFile());
        }

        @Override
        public void close() throws IOException {
            this.lock.close();
        }

        public boolean restoreLevelDataFromOld() {
            return Util.safeReplaceOrMoveFile(
                this.levelDirectory.dataFile(), this.levelDirectory.oldDataFile(), this.levelDirectory.corruptedDataFile(LocalDateTime.now()), true
            );
        }

        @Nullable
        public Instant getFileModificationTime(boolean useFallback) {
            return LevelStorageSource.getFileModificationTime(useFallback ? this.levelDirectory.oldDataFile() : this.levelDirectory.dataFile());
        }
    }
}
