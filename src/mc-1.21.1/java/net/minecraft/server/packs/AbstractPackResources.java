package net.minecraft.server.packs;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

public abstract class AbstractPackResources implements PackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;

    protected AbstractPackResources(PackLocationInfo location) {
        this.location = location;
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) throws IOException {
        IoSupplier<InputStream> ioSupplier = this.getRootResource("pack.mcmeta");
        if (ioSupplier == null) {
            return null;
        }

        try (InputStream inputStream = ioSupplier.get()) {
            return getMetadataFromStream(deserializer, inputStream);
        }
    }

    @Nullable
    public static <T> T getMetadataFromStream(MetadataSectionSerializer<T> deserializer, InputStream inputStream) {
        JsonObject jsonObject;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            jsonObject = GsonHelper.parse(bufferedReader);
        } catch (Exception exception) {
            LOGGER.error("Couldn't load {} metadata", deserializer.getMetadataSectionName(), exception);
            return null;
        }

        if (!jsonObject.has(deserializer.getMetadataSectionName())) {
            return null;
        }

        try {
            return deserializer.fromJson(GsonHelper.getAsJsonObject(jsonObject, deserializer.getMetadataSectionName()));
        } catch (Exception exception) {
            LOGGER.error("Couldn't load {} metadata", deserializer.getMetadataSectionName(), exception);
            return null;
        }
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }
}
