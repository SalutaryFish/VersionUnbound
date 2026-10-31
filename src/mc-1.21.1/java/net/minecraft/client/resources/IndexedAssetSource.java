package net.minecraft.client.resources;

import com.google.common.base.Splitter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map.Entry;
import net.minecraft.server.packs.linkfs.LinkFileSystem;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

public class IndexedAssetSource {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Splitter PATH_SPLITTER = Splitter.on('/');

    public static Path createIndexFs(Path assetDirectory, String assetIndex) {
        Path path = assetDirectory.resolve("objects");
        LinkFileSystem.Builder builder = LinkFileSystem.builder();
        Path path2 = assetDirectory.resolve("indexes/" + assetIndex + ".json");

        try (BufferedReader bufferedReader = Files.newBufferedReader(path2, StandardCharsets.UTF_8)) {
            JsonObject jsonObject = GsonHelper.parse(bufferedReader);
            JsonObject jsonObject2 = GsonHelper.getAsJsonObject(jsonObject, "objects", null);
            if (jsonObject2 != null) {
                for (Entry<String, JsonElement> entry : jsonObject2.entrySet()) {
                    JsonObject jsonObject3 = (JsonObject)entry.getValue();
                    String string = entry.getKey();
                    List<String> list = PATH_SPLITTER.splitToList(string);
                    String string2 = GsonHelper.getAsString(jsonObject3, "hash");
                    Path path3 = path.resolve(string2.substring(0, 2) + "/" + string2);
                    builder.put(list, path3);
                }
            }
        } catch (JsonParseException jsonParseException) {
            LOGGER.error("Unable to parse resource index file: {}", path2);
        } catch (IOException iOException) {
            LOGGER.error("Can't open the resource index file: {}", path2);
        }

        return builder.build("index-" + assetIndex).getPath("/");
    }
}
