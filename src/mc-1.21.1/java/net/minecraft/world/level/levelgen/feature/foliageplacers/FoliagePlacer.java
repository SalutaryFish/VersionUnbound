package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.datafixers.Products.P2;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.material.Fluids;

public abstract class FoliagePlacer {
    public static final Codec<FoliagePlacer> CODEC = BuiltInRegistries.FOLIAGE_PLACER_TYPE
        .byNameCodec()
        .dispatch(FoliagePlacer::type, FoliagePlacerType::codec);
    protected final IntProvider radius;
    protected final IntProvider offset;

    protected static <P extends FoliagePlacer> P2<Mu<P>, IntProvider, IntProvider> foliagePlacerParts(Instance<P> instance) {
        return instance.group(
            IntProvider.codec(0, 16).fieldOf("radius").forGetter(foliagePlacer -> foliagePlacer.radius),
            IntProvider.codec(0, 16).fieldOf("offset").forGetter(foliagePlacer -> foliagePlacer.offset)
        );
    }

    public FoliagePlacer(IntProvider radius, IntProvider offset) {
        this.radius = radius;
        this.offset = offset;
    }

    protected abstract FoliagePlacerType<?> type();

    public void createFoliage(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter blockSetter,
        RandomSource random,
        TreeConfiguration config,
        int maxFreeTreeHeight,
        FoliagePlacer.FoliageAttachment attachment,
        int foliageHeight,
        int foliageRadius
    ) {
        this.createFoliage(level, blockSetter, random, config, maxFreeTreeHeight, attachment, foliageHeight, foliageRadius, this.offset(random));
    }

    protected abstract void createFoliage(
        LevelSimulatedReader var1,
        FoliagePlacer.FoliageSetter var2,
        RandomSource var3,
        TreeConfiguration var4,
        int var5,
        FoliagePlacer.FoliageAttachment var6,
        int var7,
        int var8,
        int var9
    );

    public abstract int foliageHeight(RandomSource var1, int var2, TreeConfiguration var3);

    public int foliageRadius(RandomSource random, int radius) {
        return this.radius.sample(random);
    }

    private int offset(RandomSource random) {
        return this.offset.sample(random);
    }

    /**
     * Skips certain positions based on the provided shape, such as rounding corners randomly.
     * The coordinates are passed in as absolute value, and should be within [0, {@code range}].
     */
    protected abstract boolean shouldSkipLocation(RandomSource var1, int var2, int var3, int var4, int var5, boolean var6);

    protected boolean shouldSkipLocationSigned(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        int i;
        int j;
        if (large) {
            i = Math.min(Math.abs(localX), Math.abs(localX - 1));
            j = Math.min(Math.abs(localZ), Math.abs(localZ - 1));
        } else {
            i = Math.abs(localX);
            j = Math.abs(localZ);
        }

        return this.shouldSkipLocation(random, i, localY, j, range, large);
    }

    protected void placeLeavesRow(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter foliageSetter,
        RandomSource random,
        TreeConfiguration treeConfiguration,
        BlockPos pos,
        int range,
        int localY,
        boolean large
    ) {
        int i = large ? 1 : 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int j = -range; j <= range + i; j++) {
            for (int k = -range; k <= range + i; k++) {
                if (!this.shouldSkipLocationSigned(random, j, localY, k, range, large)) {
                    mutableBlockPos.setWithOffset(pos, j, localY, k);
                    tryPlaceLeaf(level, foliageSetter, random, treeConfiguration, mutableBlockPos);
                }
            }
        }
    }

    protected final void placeLeavesRowWithHangingLeavesBelow(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter foliageSetter,
        RandomSource random,
        TreeConfiguration treeConfiguration,
        BlockPos pos,
        int range,
        int localY,
        boolean large,
        float hangingLeavesChance,
        float hangingLeavesExtensionChance
    ) {
        this.placeLeavesRow(level, foliageSetter, random, treeConfiguration, pos, range, localY, large);
        int i = large ? 1 : 0;
        BlockPos blockPos = pos.below();
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Direction direction2 = direction.getClockWise();
            int j = direction2.getAxisDirection() == Direction.AxisDirection.POSITIVE ? range + i : range;
            mutableBlockPos.setWithOffset(pos, 0, localY - 1, 0).move(direction2, j).move(direction, -range);
            int k = -range;

            while (k < range + i) {
                boolean bl = foliageSetter.isSet(mutableBlockPos.move(Direction.UP));
                mutableBlockPos.move(Direction.DOWN);
                if (bl && tryPlaceExtension(level, foliageSetter, random, treeConfiguration, hangingLeavesChance, blockPos, mutableBlockPos)) {
                    mutableBlockPos.move(Direction.DOWN);
                    tryPlaceExtension(level, foliageSetter, random, treeConfiguration, hangingLeavesExtensionChance, blockPos, mutableBlockPos);
                    mutableBlockPos.move(Direction.UP);
                }

                k++;
                mutableBlockPos.move(direction);
            }
        }
    }

    private static boolean tryPlaceExtension(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter foliageSetter,
        RandomSource random,
        TreeConfiguration treeConfiguration,
        float extensionChance,
        BlockPos logPos,
        BlockPos.MutableBlockPos pos
    ) {
        if (pos.distManhattan(logPos) >= 7) {
            return false;
        } else {
            return random.nextFloat() > extensionChance ? false : tryPlaceLeaf(level, foliageSetter, random, treeConfiguration, pos);
        }
    }

    protected static boolean tryPlaceLeaf(
        LevelSimulatedReader level, FoliagePlacer.FoliageSetter foliageSetter, RandomSource random, TreeConfiguration treeConfiguration, BlockPos pos
    ) {
        if (!TreeFeature.validTreePos(level, pos)) {
            return false;
        }

        BlockState blockState = treeConfiguration.foliageProvider.getState(random, pos);
        if (blockState.hasProperty(BlockStateProperties.WATERLOGGED)) {
            blockState = blockState.setValue(
                BlockStateProperties.WATERLOGGED, level.isFluidAtPosition(pos, fluidState -> fluidState.isSourceOfType(Fluids.WATER))
            );
        }

        foliageSetter.set(pos, blockState);
        return true;
    }

    public static final class FoliageAttachment {
        private final BlockPos pos;
        private final int radiusOffset;
        private final boolean doubleTrunk;

        public FoliageAttachment(BlockPos pos, int radiusOffset, boolean doubleTrunk) {
            this.pos = pos;
            this.radiusOffset = radiusOffset;
            this.doubleTrunk = doubleTrunk;
        }

        public BlockPos pos() {
            return this.pos;
        }

        public int radiusOffset() {
            return this.radiusOffset;
        }

        public boolean doubleTrunk() {
            return this.doubleTrunk;
        }
    }

    public interface FoliageSetter {
        void set(BlockPos var1, BlockState var2);

        boolean isSet(BlockPos var1);
    }
}
