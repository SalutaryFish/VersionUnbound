package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.ReplaceSphereConfiguration;

public class ReplaceBlobsFeature extends Feature<ReplaceSphereConfiguration> {
    public ReplaceBlobsFeature(Codec<ReplaceSphereConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<ReplaceSphereConfiguration> context) {
        ReplaceSphereConfiguration replaceSphereConfiguration = context.config();
        WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        Block block = replaceSphereConfiguration.targetState.getBlock();
        BlockPos blockPos = findTarget(
            worldGenLevel,
            context.origin().mutable().clamp(Direction.Axis.Y, worldGenLevel.getMinBuildHeight() + 1, worldGenLevel.getMaxBuildHeight() - 1),
            block
        );
        if (blockPos == null) {
            return false;
        }

        int i = replaceSphereConfiguration.radius().sample(randomSource);
        int j = replaceSphereConfiguration.radius().sample(randomSource);
        int k = replaceSphereConfiguration.radius().sample(randomSource);
        int l = Math.max(i, Math.max(j, k));
        boolean bl = false;

        for (BlockPos blockPos2 : BlockPos.withinManhattan(blockPos, i, j, k)) {
            if (blockPos2.distManhattan(blockPos) > l) {
                break;
            }

            BlockState blockState = worldGenLevel.getBlockState(blockPos2);
            if (blockState.is(block)) {
                this.setBlock(worldGenLevel, blockPos2, replaceSphereConfiguration.replaceState);
                bl = true;
            }
        }

        return bl;
    }

    @Nullable
    private static BlockPos findTarget(LevelAccessor level, BlockPos.MutableBlockPos topPos, Block block) {
        while (topPos.getY() > level.getMinBuildHeight() + 1) {
            BlockState blockState = level.getBlockState(topPos);
            if (blockState.is(block)) {
                return topPos;
            }

            topPos.move(Direction.DOWN);
        }

        return null;
    }
}
