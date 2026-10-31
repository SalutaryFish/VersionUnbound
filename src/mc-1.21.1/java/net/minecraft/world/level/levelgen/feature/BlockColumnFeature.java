package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.configurations.BlockColumnConfiguration;

public class BlockColumnFeature extends Feature<BlockColumnConfiguration> {
    public BlockColumnFeature(Codec<BlockColumnConfiguration> codec) {
        super(codec);
    }

    /**
     * Places the given feature at the given location.
     * During world generation, features are provided with a 3x3 region of chunks, centered on the chunk being generated, that they can safely generate into.
     *
     * @param context A context object with a reference to the level and the position
     *                the feature is being placed at
     */
    @Override
    public boolean place(FeaturePlaceContext<BlockColumnConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockColumnConfiguration blockColumnConfiguration = context.config();
        RandomSource randomSource = context.random();
        int i = blockColumnConfiguration.layers().size();
        int[] is = new int[i];
        int j = 0;

        for (int k = 0; k < i; k++) {
            is[k] = blockColumnConfiguration.layers().get(k).height().sample(randomSource);
            j += is[k];
        }

        if (j == 0) {
            return false;
        }

        BlockPos.MutableBlockPos mutableBlockPos = context.origin().mutable();
        BlockPos.MutableBlockPos mutableBlockPos2 = mutableBlockPos.mutable().move(blockColumnConfiguration.direction());

        for (int l = 0; l < j; l++) {
            if (!blockColumnConfiguration.allowedPlacement().test(worldGenLevel, mutableBlockPos2)) {
                truncate(is, j, l, blockColumnConfiguration.prioritizeTip());
                break;
            }

            mutableBlockPos2.move(blockColumnConfiguration.direction());
        }

        for (int l = 0; l < i; l++) {
            int m = is[l];
            if (m != 0) {
                BlockColumnConfiguration.Layer layer = blockColumnConfiguration.layers().get(l);

                for (int n = 0; n < m; n++) {
                    worldGenLevel.setBlock(mutableBlockPos, layer.state().getState(randomSource, mutableBlockPos), 2);
                    mutableBlockPos.move(blockColumnConfiguration.direction());
                }
            }
        }

        return true;
    }

    private static void truncate(int[] layerHeights, int totalHeight, int currentHeight, boolean prioritizeTip) {
        int i = totalHeight - currentHeight;
        int j = prioritizeTip ? 1 : -1;
        int k = prioritizeTip ? 0 : layerHeights.length - 1;
        int l = prioritizeTip ? layerHeights.length : -1;

        for (int m = k; m != l && i > 0; m += j) {
            int n = layerHeights[m];
            int o = Math.min(n, i);
            i -= o;
            layerHeights[m] -= o;
        }
    }
}
