package net.minecraft.client.renderer;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;

public class BiomeColors {
    public static final ColorResolver GRASS_COLOR_RESOLVER = Biome::getGrassColor;
    public static final ColorResolver FOLIAGE_COLOR_RESOLVER = (biome, x, z) -> biome.getFoliageColor();
    public static final ColorResolver WATER_COLOR_RESOLVER = (biome, x, z) -> biome.getWaterColor();

    private static int getAverageColor(BlockAndTintGetter level, BlockPos blockPos, ColorResolver colorResolver) {
        return level.getBlockTint(blockPos, colorResolver);
    }

    public static int getAverageGrassColor(BlockAndTintGetter level, BlockPos blockPos) {
        return getAverageColor(level, blockPos, GRASS_COLOR_RESOLVER);
    }

    public static int getAverageFoliageColor(BlockAndTintGetter level, BlockPos blockPos) {
        return getAverageColor(level, blockPos, FOLIAGE_COLOR_RESOLVER);
    }

    public static int getAverageWaterColor(BlockAndTintGetter level, BlockPos blockPos) {
        return getAverageColor(level, blockPos, WATER_COLOR_RESOLVER);
    }
}
