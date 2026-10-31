package net.minecraft.client.renderer.block;

import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockModelShaper {
    private Map<BlockState, BakedModel> modelByStateCache = Map.of();
    private final ModelManager modelManager;

    public BlockModelShaper(ModelManager modelManager) {
        this.modelManager = modelManager;
    }

    public TextureAtlasSprite getParticleIcon(BlockState state) {
        return this.getBlockModel(state).getParticleIcon();
    }

    public BakedModel getBlockModel(BlockState state) {
        BakedModel bakedModel = this.modelByStateCache.get(state);
        if (bakedModel == null) {
            bakedModel = this.modelManager.getMissingModel();
        }

        return bakedModel;
    }

    public ModelManager getModelManager() {
        return this.modelManager;
    }

    public void replaceCache(Map<BlockState, BakedModel> modelByStateCache) {
        this.modelByStateCache = modelByStateCache;
    }

    public static ModelResourceLocation stateToModelLocation(BlockState state) {
        return stateToModelLocation(BuiltInRegistries.BLOCK.getKey(state.getBlock()), state);
    }

    public static ModelResourceLocation stateToModelLocation(ResourceLocation location, BlockState state) {
        return new ModelResourceLocation(location, statePropertiesToString(state.getValues()));
    }

    public static String statePropertiesToString(Map<Property<?>, Comparable<?>> propertyValues) {
        StringBuilder stringBuilder = new StringBuilder();

        for (Entry<Property<?>, Comparable<?>> entry : propertyValues.entrySet()) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append(',');
            }

            Property<?> property = entry.getKey();
            stringBuilder.append(property.getName());
            stringBuilder.append('=');
            stringBuilder.append(getValue(property, entry.getValue()));
        }

        return stringBuilder.toString();
    }

    private static <T extends Comparable<T>> String getValue(Property<T> property, Comparable<?> value) {
        return property.getName((T)value);
    }
}
