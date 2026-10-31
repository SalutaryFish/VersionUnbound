package net.minecraft.world.level.storage;

import com.google.common.collect.Maps;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Map;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.FastBufferedInputStream;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class DimensionDataStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, SavedData> cache = Maps.newHashMap();
    private final DataFixer fixerUpper;
    private final HolderLookup.Provider registries;
    private final File dataFolder;

    public DimensionDataStorage(File dataFolder, DataFixer fixerUpper, HolderLookup.Provider registries) {
        this.fixerUpper = fixerUpper;
        this.dataFolder = dataFolder;
        this.registries = registries;
    }

    private File getDataFile(String name) {
        return new File(this.dataFolder, name + ".dat");
    }

    public <T extends SavedData> T computeIfAbsent(SavedData.Factory<T> factory, String name) {
        T savedData = this.get(factory, name);
        if (savedData != null) {
            return savedData;
        }

        T savedData2 = (T)factory.constructor().get();
        this.set(name, savedData2);
        return savedData2;
    }

    @Nullable
    public <T extends SavedData> T get(SavedData.Factory<T> factory, String name) {
        SavedData savedData = this.cache.get(name);
        if (savedData == null && !this.cache.containsKey(name)) {
            savedData = this.readSavedData(factory.deserializer(), factory.type(), name);
            this.cache.put(name, savedData);
        }

        return (T)savedData;
    }

    @Nullable
    private <T extends SavedData> T readSavedData(BiFunction<CompoundTag, HolderLookup.Provider, T> reader, DataFixTypes dataFixType, String filename) {
        try {
            File file = this.getDataFile(filename);
            if (file.exists()) {
                CompoundTag compoundTag = this.readTagFromDisk(filename, dataFixType, SharedConstants.getCurrentVersion().getDataVersion().getVersion());
                return reader.apply(compoundTag.getCompound("data"), this.registries);
            }
        } catch (Exception exception) {
            LOGGER.error("Error loading saved data: {}", filename, exception);
        }

        return null;
    }

    public void set(String name, SavedData savedData) {
        this.cache.put(name, savedData);
    }

    public CompoundTag readTagFromDisk(String filename, DataFixTypes dataFixType, int version) throws IOException {
        File file = this.getDataFile(filename);

        try (
            InputStream inputStream = new FileInputStream(file);
            PushbackInputStream pushbackInputStream = new PushbackInputStream(new FastBufferedInputStream(inputStream), 2);
        ) {
            CompoundTag compoundTag;
            if (this.isGzip(pushbackInputStream)) {
                compoundTag = NbtIo.readCompressed(pushbackInputStream, NbtAccounter.unlimitedHeap());
            } else {
                try (DataInputStream dataInputStream = new DataInputStream(pushbackInputStream)) {
                    compoundTag = NbtIo.read(dataInputStream);
                }
            }

            int i = NbtUtils.getDataVersion(compoundTag, 1343);
            return dataFixType.update(this.fixerUpper, compoundTag, i, version);
        }
    }

    private boolean isGzip(PushbackInputStream inputStream) throws IOException {
        byte[] bs = new byte[2];
        boolean bl = false;
        int i = inputStream.read(bs, 0, 2);
        if (i == 2) {
            int j = (bs[1] & 255) << 8 | bs[0] & 255;
            if (j == 35615) {
                bl = true;
            }
        }

        if (i != 0) {
            inputStream.unread(bs, 0, i);
        }

        return bl;
    }

    public void save() {
        this.cache.forEach((name, savedData) -> {
            if (savedData != null) {
                savedData.save(this.getDataFile(name), this.registries);
            }
        });
    }
}
