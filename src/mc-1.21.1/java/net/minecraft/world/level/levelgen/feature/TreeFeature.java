package net.minecraft.world.level.levelgen.feature;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.LevelWriter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;

public class TreeFeature extends Feature<TreeConfiguration> {
    private static final int BLOCK_UPDATE_FLAGS = 19;

    public TreeFeature(Codec<TreeConfiguration> codec) {
        super(codec);
    }

    private static boolean isVine(LevelSimulatedReader level, BlockPos pos) {
        return level.isStateAtPosition(pos, state -> state.is(Blocks.VINE));
    }

    public static boolean isAirOrLeaves(LevelSimulatedReader level, BlockPos pos) {
        return level.isStateAtPosition(pos, state -> state.isAir() || state.is(BlockTags.LEAVES));
    }

    private static void setBlockKnownShape(LevelWriter level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 19);
    }

    public static boolean validTreePos(LevelSimulatedReader level, BlockPos pos) {
        return level.isStateAtPosition(pos, state -> state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES));
    }

    private boolean doPlace(
        WorldGenLevel level,
        RandomSource random,
        BlockPos pos,
        BiConsumer<BlockPos, BlockState> rootBlockSetter,
        BiConsumer<BlockPos, BlockState> trunkBlockSetter,
        FoliagePlacer.FoliageSetter foliageBlockSetter,
        TreeConfiguration config
    ) {
        int i = config.trunkPlacer.getTreeHeight(random);
        int j = config.foliagePlacer.foliageHeight(random, i, config);
        int k = i - j;
        int l = config.foliagePlacer.foliageRadius(random, k);
        BlockPos blockPos = config.rootPlacer.<BlockPos>map(placer -> placer.getTrunkOrigin(pos, random)).orElse(pos);
        int m = Math.min(pos.getY(), blockPos.getY());
        int n = Math.max(pos.getY(), blockPos.getY()) + i + 1;
        if (m >= level.getMinBuildHeight() + 1 && n <= level.getMaxBuildHeight()) {
            OptionalInt optionalInt = config.minimumSize.minClippedHeight();
            int o = this.getMaxFreeTreeHeight(level, i, blockPos, config);
            if (o >= i || !optionalInt.isEmpty() && o >= optionalInt.getAsInt()) {
                if (config.rootPlacer.isPresent() && !config.rootPlacer.get().placeRoots(level, rootBlockSetter, random, pos, blockPos, config)) {
                    return false;
                }

                List<FoliagePlacer.FoliageAttachment> list = config.trunkPlacer.placeTrunk(level, trunkBlockSetter, random, o, blockPos, config);
                list.forEach(attachment -> config.foliagePlacer.createFoliage(level, foliageBlockSetter, random, config, o, attachment, j, l));
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private int getMaxFreeTreeHeight(LevelSimulatedReader level, int trunkHeight, BlockPos topPosition, TreeConfiguration config) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int i = 0; i <= trunkHeight + 1; i++) {
            int j = config.minimumSize.getSizeAtHeight(trunkHeight, i);

            for (int k = -j; k <= j; k++) {
                for (int l = -j; l <= j; l++) {
                    mutableBlockPos.setWithOffset(topPosition, k, i, l);
                    if (!config.trunkPlacer.isFree(level, mutableBlockPos) || !config.ignoreVines && isVine(level, mutableBlockPos)) {
                        return i - 2;
                    }
                }
            }
        }

        return trunkHeight;
    }

    @Override
    protected void setBlock(LevelWriter level, BlockPos pos, BlockState state) {
        setBlockKnownShape(level, pos, state);
    }

    /**
     * Places the given feature at the given location.
     * During world generation, features are provided with a 3x3 region of chunks, centered on the chunk being generated, that they can safely generate into.
     *
     * @param context A context object with a reference to the level and the position
     *                the feature is being placed at
     */
    @Override
    public final boolean place(FeaturePlaceContext<TreeConfiguration> context) {
        final WorldGenLevel worldGenLevel = context.level();
        RandomSource randomSource = context.random();
        BlockPos blockPos = context.origin();
        TreeConfiguration treeConfiguration = context.config();
        Set<BlockPos> set = Sets.newHashSet();
        Set<BlockPos> set2 = Sets.newHashSet();
        final Set<BlockPos> set3 = Sets.newHashSet();
        Set<BlockPos> set4 = Sets.newHashSet();
        BiConsumer<BlockPos, BlockState> biConsumer = (pos, state) -> {
            set.add(pos.immutable());
            worldGenLevel.setBlock(pos, state, 19);
        };
        BiConsumer<BlockPos, BlockState> biConsumer2 = (pos, state) -> {
            set2.add(pos.immutable());
            worldGenLevel.setBlock(pos, state, 19);
        };
        FoliagePlacer.FoliageSetter foliageSetter = new FoliagePlacer.FoliageSetter() {
            @Override
            public void set(BlockPos blockPos, BlockState blockState) {
                set3.add(blockPos.immutable());
                worldGenLevel.setBlock(blockPos, blockState, 19);
            }

            @Override
            public boolean isSet(BlockPos blockPos) {
                return set3.contains(blockPos);
            }
        };
        BiConsumer<BlockPos, BlockState> biConsumer3 = (pos, state) -> {
            set4.add(pos.immutable());
            worldGenLevel.setBlock(pos, state, 19);
        };
        boolean bl = this.doPlace(worldGenLevel, randomSource, blockPos, biConsumer, biConsumer2, foliageSetter, treeConfiguration);
        if (bl && (!set2.isEmpty() || !set3.isEmpty())) {
            if (!treeConfiguration.decorators.isEmpty()) {
                TreeDecorator.Context context2 = new TreeDecorator.Context(worldGenLevel, biConsumer3, randomSource, set2, set3, set);
                treeConfiguration.decorators.forEach(decorator -> decorator.place(context2));
            }

            return BoundingBox.encapsulatingPositions(Iterables.concat(set, set2, set3, set4)).map(boundingBox -> {
                DiscreteVoxelShape discreteVoxelShape = updateLeaves(worldGenLevel, boundingBox, set2, set4, set);
                StructureTemplate.updateShapeAtEdge(worldGenLevel, 3, discreteVoxelShape, boundingBox.minX(), boundingBox.minY(), boundingBox.minZ());
                return true;
            }).orElse(false);
        } else {
            return false;
        }
    }

    private static DiscreteVoxelShape updateLeaves(
        LevelAccessor level, BoundingBox box, Set<BlockPos> rootPositions, Set<BlockPos> trunkPositions, Set<BlockPos> foliagePositions
    ) {
        DiscreteVoxelShape discreteVoxelShape = new BitSetDiscreteVoxelShape(box.getXSpan(), box.getYSpan(), box.getZSpan());
        int i = 7;
        List<Set<BlockPos>> list = Lists.newArrayList();

        for (int j = 0; j < 7; j++) {
            list.add(Sets.newHashSet());
        }

        for (BlockPos blockPos : Lists.newArrayList(Sets.union(trunkPositions, foliagePositions))) {
            if (box.isInside(blockPos)) {
                discreteVoxelShape.fill(blockPos.getX() - box.minX(), blockPos.getY() - box.minY(), blockPos.getZ() - box.minZ());
            }
        }

        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int k = 0;
        list.get(0).addAll(rootPositions);

        while (true) {
            while (k >= 7 || !list.get(k).isEmpty()) {
                if (k >= 7) {
                    return discreteVoxelShape;
                }

                Iterator<BlockPos> iterator = list.get(k).iterator();
                BlockPos blockPos2 = iterator.next();
                iterator.remove();
                if (box.isInside(blockPos2)) {
                    if (k != 0) {
                        BlockState blockState = level.getBlockState(blockPos2);
                        setBlockKnownShape(level, blockPos2, blockState.setValue(BlockStateProperties.DISTANCE, k));
                    }

                    discreteVoxelShape.fill(blockPos2.getX() - box.minX(), blockPos2.getY() - box.minY(), blockPos2.getZ() - box.minZ());

                    for (Direction direction : Direction.values()) {
                        mutableBlockPos.setWithOffset(blockPos2, direction);
                        if (box.isInside(mutableBlockPos)) {
                            int l = mutableBlockPos.getX() - box.minX();
                            int m = mutableBlockPos.getY() - box.minY();
                            int n = mutableBlockPos.getZ() - box.minZ();
                            if (!discreteVoxelShape.isFull(l, m, n)) {
                                BlockState blockState2 = level.getBlockState(mutableBlockPos);
                                OptionalInt optionalInt = LeavesBlock.getOptionalDistanceAt(blockState2);
                                if (!optionalInt.isEmpty()) {
                                    int o = Math.min(optionalInt.getAsInt(), k + 1);
                                    if (o < 7) {
                                        list.get(o).add(mutableBlockPos.immutable());
                                        k = Math.min(k, o);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            k++;
        }
    }
}
