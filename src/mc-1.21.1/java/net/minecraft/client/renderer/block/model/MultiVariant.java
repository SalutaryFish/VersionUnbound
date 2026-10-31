package net.minecraft.client.renderer.block.model;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.resources.ResourceLocation;

public class MultiVariant implements UnbakedModel {
    private final List<Variant> variants;

    public MultiVariant(List<Variant> variants) {
        this.variants = variants;
    }

    public List<Variant> getVariants() {
        return this.variants;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return other instanceof MultiVariant multiVariant ? this.variants.equals(multiVariant.variants) : false;
        }
    }

    @Override
    public int hashCode() {
        return this.variants.hashCode();
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return this.getVariants().stream().map(Variant::getModelLocation).collect(Collectors.toSet());
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {
        this.getVariants()
            .stream()
            .map(Variant::getModelLocation)
            .distinct()
            .forEach(resourceLocation -> resolver.apply(resourceLocation).resolveParents(resolver));
    }

    @Nullable
    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
        if (this.getVariants().isEmpty()) {
            return null;
        }

        WeightedBakedModel.Builder builder = new WeightedBakedModel.Builder();

        for (Variant variant : this.getVariants()) {
            BakedModel bakedModel = baker.bake(variant.getModelLocation(), variant);
            builder.add(bakedModel, variant.getWeight());
        }

        return builder.build();
    }

    public static class Deserializer implements JsonDeserializer<MultiVariant> {
        public MultiVariant deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
            List<Variant> list = Lists.newArrayList();
            if (json.isJsonArray()) {
                JsonArray jsonArray = json.getAsJsonArray();
                if (jsonArray.size() == 0) {
                    throw new JsonParseException("Empty variant array");
                }

                for (JsonElement jsonElement : jsonArray) {
                    list.add(context.deserialize(jsonElement, Variant.class));
                }
            } else {
                list.add(context.deserialize(json, Variant.class));
            }

            return new MultiVariant(list);
        }
    }
}
