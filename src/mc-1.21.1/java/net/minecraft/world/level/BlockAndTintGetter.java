package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.lighting.LevelLightEngine;

public interface BlockAndTintGetter extends BlockGetter {
    float getShade(Direction var1, boolean var2);

    LevelLightEngine getLightEngine();

    int getBlockTint(BlockPos var1, ColorResolver var2);

    default int getBrightness(LightLayer lightType, BlockPos blockPos) {
        return this.getLightEngine().getLayerListener(lightType).getLightValue(blockPos);
    }

    default int getRawBrightness(BlockPos blockPos, int amount) {
        return this.getLightEngine().getRawBrightness(blockPos, amount);
    }

    default boolean canSeeSky(BlockPos blockPos) {
        return this.getBrightness(LightLayer.SKY, blockPos) >= this.getMaxLightLevel();
    }
}
