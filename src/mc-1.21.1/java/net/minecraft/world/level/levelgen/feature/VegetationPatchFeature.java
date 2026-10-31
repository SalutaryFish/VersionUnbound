package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.configurations.VegetationPatchConfiguration;

public class VegetationPatchFeature extends Feature<VegetationPatchConfiguration> {
    public VegetationPatchFeature(Codec<VegetationPatchConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<VegetationPatchConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        VegetationPatchConfiguration vegetationPatchConfiguration = context.config();
        RandomSource randomSource = context.random();
        BlockPos blockPos = context.origin();
        Predicate<BlockState> predicate = blockState -> blockState.is(vegetationPatchConfiguration.replaceable);
        int i = vegetationPatchConfiguration.xzRadius.sample(randomSource) + 1;
        int j = vegetationPatchConfiguration.xzRadius.sample(randomSource) + 1;
        Set<BlockPos> set = this.placeGroundPatch(worldGenLevel, vegetationPatchConfiguration, randomSource, blockPos, predicate, i, j);
        this.distributeVegetation(context, worldGenLevel, vegetationPatchConfiguration, randomSource, set, i, j);
        return !set.isEmpty();
    }

    protected Set<BlockPos> placeGroundPatch(
        WorldGenLevel level, VegetationPatchConfiguration config, RandomSource random, BlockPos pos, Predicate<BlockState> state, int xRadius, int zRadius
    ) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
        BlockPos.MutableBlockPos mutableBlockPos2 = mutableBlockPos.mutable();
        Direction direction = config.surface.getDirection();
        Direction direction2 = direction.getOpposite();
        Set<BlockPos> set = new HashSet<>();

        for (int i = -xRadius; i <= xRadius; i++) {
            boolean bl = i == -xRadius || i == xRadius;

            for (int j = -zRadius; j <= zRadius; j++) {
                boolean bl2 = j == -zRadius || j == zRadius;
                boolean bl3 = bl || bl2;
                boolean bl4 = bl && bl2;
                boolean bl5 = bl3 && !bl4;
                if (!bl4 && (!bl5 || config.extraEdgeColumnChance != 0.0F && !(random.nextFloat() > config.extraEdgeColumnChance))) {
                    mutableBlockPos.setWithOffset(pos, i, 0, j);

                    for (int k = 0; level.isStateAtPosition(mutableBlockPos, BlockBehaviour.BlockStateBase::isAir) && k < config.verticalRange; k++) {
                        mutableBlockPos.move(direction);
                    }

                    for (int var25 = 0; level.isStateAtPosition(mutableBlockPos, blockStatex -> !blockStatex.isAir()) && var25 < config.verticalRange; var25++) {
                        mutableBlockPos.move(direction2);
                    }

                    mutableBlockPos2.setWithOffset(mutableBlockPos, config.surface.getDirection());
                    BlockState blockState = level.getBlockState(mutableBlockPos2);
                    if (level.isEmptyBlock(mutableBlockPos) && blockState.isFaceSturdy(level, mutableBlockPos2, config.surface.getDirection().getOpposite())) {
                        int l = config.depth.sample(random)
                            + (config.extraBottomBlockChance > 0.0F && random.nextFloat() < config.extraBottomBlockChance ? 1 : 0);
                        BlockPos blockPos = mutableBlockPos2.immutable();
                        boolean bl6 = this.placeGround(level, config, state, random, mutableBlockPos2, l);
                        if (bl6) {
                            set.add(blockPos);
                        }
                    }
                }
            }
        }

        return set;
    }

    protected void distributeVegetation(
        FeaturePlaceContext<VegetationPatchConfiguration> context,
        WorldGenLevel level,
        VegetationPatchConfiguration config,
        RandomSource random,
        Set<BlockPos> possiblePositions,
        int xRadius,
        int zRadius
    ) {
        for (BlockPos blockPos : possiblePositions) {
            if (config.vegetationChance > 0.0F && random.nextFloat() < config.vegetationChance) {
                this.placeVegetation(level, config, context.chunkGenerator(), random, blockPos);
            }
        }
    }

    protected boolean placeVegetation(
        WorldGenLevel level, VegetationPatchConfiguration config, ChunkGenerator chunkGenerator, RandomSource random, BlockPos pos
    ) {
        return config.vegetationFeature.value().place(level, chunkGenerator, random, pos.relative(config.surface.getDirection().getOpposite()));
    }

    protected boolean placeGround(
        WorldGenLevel level,
        VegetationPatchConfiguration config,
        Predicate<BlockState> replaceableblocks,
        RandomSource random,
        BlockPos.MutableBlockPos mutablePos,
        int maxDistance
    ) {
        for (int i = 0; i < maxDistance; i++) {
            BlockState blockState = config.groundState.getState(random, mutablePos);
            BlockState blockState2 = level.getBlockState(mutablePos);
            if (!blockState.is(blockState2.getBlock())) {
                if (!replaceableblocks.test(blockState2)) {
                    return i != 0;
                }

                level.setBlock(mutablePos, blockState, 2);
                mutablePos.move(config.surface.getDirection());
            }
        }

        return true;
    }
}
