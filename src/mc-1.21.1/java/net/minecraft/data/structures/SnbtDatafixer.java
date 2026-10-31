package net.minecraft.data.structures;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.Bootstrap;

public class SnbtDatafixer {
    public static void main(String[] args) throws IOException {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();

        for (String string : args) {
            updateInDirectory(string);
        }
    }

    private static void updateInDirectory(String path) throws IOException {
        try (Stream<Path> stream = Files.walk(Paths.get(path))) {
            stream.filter(pathx -> pathx.toString().endsWith(".snbt")).forEach(pathx -> {
                try {
                    String string = Files.readString(pathx);
                    CompoundTag compoundTag = NbtUtils.snbtToStructure(string);
                    CompoundTag compoundTag2 = StructureUpdater.update(pathx.toString(), compoundTag);
                    NbtToSnbt.writeSnbt(CachedOutput.NO_CACHE, pathx, NbtUtils.structureToSnbt(compoundTag2));
                } catch (CommandSyntaxException | IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        }
    }
}
