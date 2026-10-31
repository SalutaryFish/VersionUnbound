package net.minecraft.data.models.model;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModelLocationUtils {
    @Deprecated
    public static ResourceLocation decorateBlockModelLocation(String blockModelLocation) {
        return ResourceLocation.withDefaultNamespace("block/" + blockModelLocation);
    }

    public static ResourceLocation decorateItemModelLocation(String itemModelLocation) {
        return ResourceLocation.withDefaultNamespace("item/" + itemModelLocation);
    }

    public static ResourceLocation getModelLocation(Block block, String modelLocationSuffix) {
        ResourceLocation resourceLocation = BuiltInRegistries.BLOCK.getKey(block);
        return resourceLocation.withPath(string2 -> "block/" + string2 + modelLocationSuffix);
    }

    public static ResourceLocation getModelLocation(Block block) {
        ResourceLocation resourceLocation = BuiltInRegistries.BLOCK.getKey(block);
        return resourceLocation.withPrefix("block/");
    }

    public static ResourceLocation getModelLocation(Item item) {
        ResourceLocation resourceLocation = BuiltInRegistries.ITEM.getKey(item);
        return resourceLocation.withPrefix("item/");
    }

    public static ResourceLocation getModelLocation(Item item, String modelLocationSuffix) {
        ResourceLocation resourceLocation = BuiltInRegistries.ITEM.getKey(item);
        return resourceLocation.withPath(string2 -> "item/" + string2 + modelLocationSuffix);
    }
}
