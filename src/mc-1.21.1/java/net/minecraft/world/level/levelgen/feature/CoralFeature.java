package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public abstract class CoralFeature extends Feature<NoneFeatureConfiguration> {
    public CoralFeature(Codec<NoneFeatureConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        RandomSource randomSource = context.random();
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        Optional<Block> optional = BuiltInRegistries.BLOCK.getRandomElementOf(BlockTags.CORAL_BLOCKS, randomSource).map(Holder::value);
        return optional.isEmpty() ? false : this.placeFeature(worldGenLevel, randomSource, blockPos, optional.get().defaultBlockState());
    }

    protected abstract boolean placeFeature(LevelAccessor var1, RandomSource var2, BlockPos var3, BlockState var4);

    protected boolean placeCoralBlock(LevelAccessor level, RandomSource random, BlockPos pos, BlockState state) {
        BlockPos blockPos = pos.above();
        BlockState blockState = level.getBlockState(pos);
        if ((blockState.is(Blocks.WATER) || blockState.is(BlockTags.CORALS)) && level.getBlockState(blockPos).is(Blocks.WATER)) {
            level.setBlock(pos, state, 3);
            if (random.nextFloat() < 0.25F) {
                BuiltInRegistries.BLOCK
                    .getRandomElementOf(BlockTags.CORALS, random)
                    .map(Holder::value)
                    .ifPresent(block -> level.setBlock(blockPos, block.defaultBlockState(), 2));
            } else if (random.nextFloat() < 0.05F) {
                level.setBlock(blockPos, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, random.nextInt(4) + 1), 2);
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (random.nextFloat() < 0.2F) {
                    BlockPos blockPos2 = pos.relative(direction);
                    if (level.getBlockState(blockPos2).is(Blocks.WATER)) {
                        BuiltInRegistries.BLOCK.getRandomElementOf(BlockTags.WALL_CORALS, random).map(Holder::value).ifPresent(block -> {
                            BlockState blockStatex = block.defaultBlockState();
                            if (blockStatex.hasProperty(BaseCoralWallFanBlock.FACING)) {
                                blockStatex = blockStatex.setValue(BaseCoralWallFanBlock.FACING, direction);
                            }

                            level.setBlock(blockPos2, blockStatex, 2);
                        });
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }
}
