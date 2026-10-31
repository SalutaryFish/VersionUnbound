package net.minecraft.client.resources.metadata.texture;

import com.google.gson.JsonObject;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.util.GsonHelper;

public class TextureMetadataSectionSerializer implements MetadataSectionSerializer<TextureMetadataSection> {
    public TextureMetadataSection fromJson(JsonObject json) {
        boolean bl = GsonHelper.getAsBoolean(json, "blur", false);
        boolean bl2 = GsonHelper.getAsBoolean(json, "clamp", false);
        return new TextureMetadataSection(bl, bl2);
    }

    @Override
    public String getMetadataSectionName() {
        return "texture";
    }
}
