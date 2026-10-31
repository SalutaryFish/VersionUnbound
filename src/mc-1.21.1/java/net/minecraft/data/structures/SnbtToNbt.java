package net.minecraft.data.structures;

import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class SnbtToNbt implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackOutput output;
    private final Iterable<Path> inputFolders;
    private final List<SnbtToNbt.Filter> filters = Lists.newArrayList();

    public SnbtToNbt(PackOutput output, Iterable<Path> inputFolders) {
        this.output = output;
        this.inputFolders = inputFolders;
    }

    public SnbtToNbt addFilter(SnbtToNbt.Filter filter) {
        this.filters.add(filter);
        return this;
    }

    private CompoundTag applyFilters(String fileName, CompoundTag tag) {
        CompoundTag compoundTag = tag;

        for (SnbtToNbt.Filter filter : this.filters) {
            compoundTag = filter.apply(fileName, compoundTag);
        }

        return compoundTag;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path path = this.output.getOutputFolder();
        List<CompletableFuture<?>> list = Lists.newArrayList();

        for (Path path2 : this.inputFolders) {
            list.add(CompletableFuture.<CompletableFuture<Void>>supplyAsync(() -> {
                try (Stream<Path> stream = Files.walk(path2)) {
                    return CompletableFuture.allOf(stream.filter(pathxx -> pathxx.toString().endsWith(".snbt")).map(path3 -> CompletableFuture.runAsync(() -> {
                        SnbtToNbt.TaskResult taskResult = this.readStructure(path3, this.getName(path2, path3));
                        this.storeStructureIfChanged(output, taskResult, path);
                    }, Util.backgroundExecutor())).toArray(CompletableFuture[]::new));
                } catch (Exception exception) {
                    throw new RuntimeException("Failed to read structure input directory, aborting", exception);
                }
            }, Util.backgroundExecutor()).thenCompose(completableFuture -> (CompletionStage<Void>)completableFuture));
        }

        return Util.sequenceFailFast(list);
    }

    @Override
    public final String getName() {
        return "SNBT -> NBT";
    }

    /**
     * Gets the name of the given SNBT file, based on its path and the input directory. The result does not have the ".snbt" extension.
     */
    private String getName(Path inputFolder, Path file) {
        String string = inputFolder.relativize(file).toString().replaceAll("\\\\", "/");
        return string.substring(0, string.length() - ".snbt".length());
    }

    private SnbtToNbt.TaskResult readStructure(Path filePath, String fileName) {
        try (BufferedReader bufferedReader = Files.newBufferedReader(filePath)) {
            String string = IOUtils.toString(bufferedReader);
            CompoundTag compoundTag = this.applyFilters(fileName, NbtUtils.snbtToStructure(string));
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);
            NbtIo.writeCompressed(compoundTag, hashingOutputStream);
            byte[] bs = byteArrayOutputStream.toByteArray();
            HashCode hashCode = hashingOutputStream.hash();
            return new SnbtToNbt.TaskResult(fileName, bs, hashCode);
        } catch (Throwable throwable) {
            throw new SnbtToNbt.StructureConversionException(filePath, throwable);
        }
    }

    private void storeStructureIfChanged(CachedOutput output, SnbtToNbt.TaskResult taskResult, Path directoryPath) {
        Path path = directoryPath.resolve(taskResult.name + ".nbt");

        try {
            output.writeIfNeeded(path, taskResult.payload, taskResult.hash);
        } catch (IOException iOException) {
            LOGGER.error("Couldn't write structure {} at {}", taskResult.name, path, iOException);
        }
    }

    @FunctionalInterface
    public interface Filter {
        CompoundTag apply(String var1, CompoundTag var2);
    }

    /**
     * Wraps exceptions thrown while reading structures to include the path of the structure in the exception message.
     */
    static class StructureConversionException extends RuntimeException {
        public StructureConversionException(Path path, Throwable cause) {
            super(path.toAbsolutePath().toString(), cause);
        }
    }

    record TaskResult(String name, byte[] payload, HashCode hash) {
    }
}
