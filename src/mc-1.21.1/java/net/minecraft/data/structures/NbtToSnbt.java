package net.minecraft.data.structures;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.FastBufferedInputStream;
import org.slf4j.Logger;

public class NbtToSnbt implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Iterable<Path> inputFolders;
    private final PackOutput output;

    public NbtToSnbt(PackOutput output, Collection<Path> inputFolders) {
        this.inputFolders = inputFolders;
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path path = this.output.getOutputFolder();
        List<CompletableFuture<?>> list = new ArrayList<>();

        for (Path path2 : this.inputFolders) {
            list.add(
                CompletableFuture.<CompletableFuture<Void>>supplyAsync(
                        () -> {
                            try (Stream<Path> stream = Files.walk(path2)) {
                                return CompletableFuture.allOf(
                                    stream.filter(pathxx -> pathxx.toString().endsWith(".nbt"))
                                        .map(
                                            path3 -> CompletableFuture.runAsync(
                                                () -> convertStructure(output, path3, getName(path2, path3), path), Util.ioPool()
                                            )
                                        )
                                        .toArray(CompletableFuture[]::new)
                                );
                            } catch (IOException iOException) {
                                LOGGER.error("Failed to read structure input directory", iOException);
                                return CompletableFuture.completedFuture(null);
                            }
                        },
                        Util.backgroundExecutor()
                    )
                    .thenCompose(completableFuture -> (CompletionStage<Void>)completableFuture)
            );
        }

        return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
    }

    @Override
    public final String getName() {
        return "NBT -> SNBT";
    }

    /**
     * Gets the name of the given NBT file, based on its path and the input directory. The result does not have the ".nbt" extension.
     */
    private static String getName(Path inputFolder, Path nbtPath) {
        String string = inputFolder.relativize(nbtPath).toString().replaceAll("\\\\", "/");
        return string.substring(0, string.length() - ".nbt".length());
    }

    @Nullable
    public static Path convertStructure(CachedOutput output, Path nbtPath, String name, Path directoryPath) {
        try (
            InputStream inputStream = Files.newInputStream(nbtPath);
            InputStream inputStream2 = new FastBufferedInputStream(inputStream);
        ) {
            Path path = directoryPath.resolve(name + ".snbt");
            writeSnbt(output, path, NbtUtils.structureToSnbt(NbtIo.readCompressed(inputStream2, NbtAccounter.unlimitedHeap())));
            LOGGER.info("Converted {} from NBT to SNBT", name);
            return path;
        } catch (IOException iOException) {
            LOGGER.error("Couldn't convert {} from NBT to SNBT at {}", name, nbtPath, iOException);
            return null;
        }
    }

    public static void writeSnbt(CachedOutput output, Path path, String contents) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);
        hashingOutputStream.write(contents.getBytes(StandardCharsets.UTF_8));
        hashingOutputStream.write(10);
        output.writeIfNeeded(path, byteArrayOutputStream.toByteArray(), hashingOutputStream.hash());
    }
}
