package net.minecraft.client.particle;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public class ParticleDescription {
    private final List<ResourceLocation> textures;

    private ParticleDescription(List<ResourceLocation> textures) {
        this.textures = textures;
    }

    public List<ResourceLocation> getTextures() {
        return this.textures;
    }

    public static ParticleDescription fromJson(JsonObject json) {
        JsonArray jsonArray = GsonHelper.getAsJsonArray(json, "textures", null);
        if (jsonArray == null) {
            return new ParticleDescription(List.of());
        }

        List<ResourceLocation> list = Streams.stream(jsonArray)
            .map(jsonElement -> GsonHelper.convertToString(jsonElement, "texture"))
            .map(ResourceLocation::parse)
            .collect(ImmutableList.toImmutableList());
        return new ParticleDescription(list);
    }
}
