package net.minecraft.world.level.chunk.storage;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.minecraft.util.FastBufferedInputStream;
import org.slf4j.Logger;

/**
 * A decorator for input and output streams used to read and write the chunk data from region files. This exists as there are different ways of compressing the chunk data inside a region file.
 * @see net.minecraft.world.level.chunk.storage.RegionFileVersion#VERSION_GZIP
 * @see net.minecraft.world.level.chunk.storage.RegionFileVersion#VERSION_DEFLATE
 * @see net.minecraft.world.level.chunk.storage.RegionFileVersion#VERSION_NONE
 */
public class RegionFileVersion {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Int2ObjectMap<RegionFileVersion> VERSIONS = new Int2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<String, RegionFileVersion> VERSIONS_BY_NAME = new Object2ObjectOpenHashMap<>();
    /**
     * Used to store the chunk data in gzip format. Unused in practice.
     */
    public static final RegionFileVersion VERSION_GZIP = register(
        new RegionFileVersion(
            1,
            null,
            inputWrapper -> new FastBufferedInputStream(new GZIPInputStream(inputWrapper)),
            outputWrapper -> new BufferedOutputStream(new GZIPOutputStream(outputWrapper))
        )
    );
    /**
     * Used to store the chunk data in zlib format. This is the default.
     */
    public static final RegionFileVersion VERSION_DEFLATE = register(
        new RegionFileVersion(
            2,
            "deflate",
            inputWrapper -> new FastBufferedInputStream(new InflaterInputStream(inputWrapper)),
            outputWrapper -> new BufferedOutputStream(new DeflaterOutputStream(outputWrapper))
        )
    );
    /**
     * Used to keep the chunk data uncompressed. Unused in practice.
     */
    public static final RegionFileVersion VERSION_NONE = register(new RegionFileVersion(3, "none", FastBufferedInputStream::new, BufferedOutputStream::new));
    /**
     * Used to store the chunk data in lz4 format. Used when region-file-compression is set to 1z4 in server.properties.
     */
    public static final RegionFileVersion VERSION_LZ4 = register(
        new RegionFileVersion(
            4,
            "lz4",
            inputStream -> new FastBufferedInputStream(new LZ4BlockInputStream(inputStream)),
            outputStream -> new BufferedOutputStream(new LZ4BlockOutputStream(outputStream))
        )
    );
    public static final RegionFileVersion VERSION_CUSTOM = register(new RegionFileVersion(127, null, inputWrapper -> {
        throw new UnsupportedOperationException();
    }, outputWrapper -> {
        throw new UnsupportedOperationException();
    }));
    public static final RegionFileVersion DEFAULT = VERSION_DEFLATE;
    private static volatile RegionFileVersion selected = DEFAULT;
    private final int id;
    @Nullable
    private final String optionName;
    private final RegionFileVersion.StreamWrapper<InputStream> inputWrapper;
    private final RegionFileVersion.StreamWrapper<OutputStream> outputWrapper;

    private RegionFileVersion(
        int id,
        @Nullable String optionName,
        RegionFileVersion.StreamWrapper<InputStream> inputWrapper,
        RegionFileVersion.StreamWrapper<OutputStream> outputWrapper
    ) {
        this.id = id;
        this.optionName = optionName;
        this.inputWrapper = inputWrapper;
        this.outputWrapper = outputWrapper;
    }

    private static RegionFileVersion register(RegionFileVersion fileVersion) {
        VERSIONS.put(fileVersion.id, fileVersion);
        if (fileVersion.optionName != null) {
            VERSIONS_BY_NAME.put(fileVersion.optionName, fileVersion);
        }

        return fileVersion;
    }

    @Nullable
    public static RegionFileVersion fromId(int id) {
        return VERSIONS.get(id);
    }

    public static void configure(String optionValue) {
        RegionFileVersion regionFileVersion = VERSIONS_BY_NAME.get(optionValue);
        if (regionFileVersion != null) {
            selected = regionFileVersion;
        } else {
            LOGGER.error(
                "Invalid `region-file-compression` value `{}` in server.properties. Please use one of: {}",
                optionValue,
                String.join(", ", VERSIONS_BY_NAME.keySet())
            );
        }
    }

    public static RegionFileVersion getSelected() {
        return selected;
    }

    public static boolean isValidVersion(int id) {
        return VERSIONS.containsKey(id);
    }

    public int getId() {
        return this.id;
    }

    public OutputStream wrap(OutputStream outputStream) throws IOException {
        return this.outputWrapper.wrap(outputStream);
    }

    public InputStream wrap(InputStream inputStream) throws IOException {
        return this.inputWrapper.wrap(inputStream);
    }

    @FunctionalInterface
    interface StreamWrapper<O> {
        O wrap(O var1) throws IOException;
    }
}
