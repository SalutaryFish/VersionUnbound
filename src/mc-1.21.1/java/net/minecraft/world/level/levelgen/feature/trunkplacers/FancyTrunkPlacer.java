package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class FancyTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<FancyTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance).apply(instance, FancyTrunkPlacer::new)
    );
    private static final double TRUNK_HEIGHT_SCALE = 0.618;
    private static final double CLUSTER_DENSITY_MAGIC = 1.382;
    private static final double BRANCH_SLOPE = 0.381;
    private static final double BRANCH_LENGTH_MAGIC = 0.328;

    public FancyTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.FANCY_TRUNK_PLACER;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        int freeTreeHeight,
        BlockPos pos,
        TreeConfiguration config
    ) {
        int i = 5;
        int j = freeTreeHeight + 2;
        int k = Mth.floor(j * 0.618);
        setDirtAt(level, blockSetter, random, pos.below(), config);
        double d = 1.0;
        int l = Math.min(1, Mth.floor(1.382 + Math.pow(1.0 * j / 13.0, 2.0)));
        int m = pos.getY() + k;
        int n = j - 5;
        List<FancyTrunkPlacer.FoliageCoords> list = Lists.newArrayList();
        list.add(new FancyTrunkPlacer.FoliageCoords(pos.above(n), m));

        for (; n >= 0; n--) {
            float f = treeShape(j, n);
            if (!(f < 0.0F)) {
                for (int o = 0; o < l; o++) {
                    double e = 1.0;
                    double g = 1.0 * f * (random.nextFloat() + 0.328);
                    double h = random.nextFloat() * 2.0F * Math.PI;
                    double p = g * Math.sin(h) + 0.5;
                    double q = g * Math.cos(h) + 0.5;
                    BlockPos blockPos = pos.offset(Mth.floor(p), n - 1, Mth.floor(q));
                    BlockPos blockPos2 = blockPos.above(5);
                    if (this.makeLimb(level, blockSetter, random, blockPos, blockPos2, false, config)) {
                        int r = pos.getX() - blockPos.getX();
                        int s = pos.getZ() - blockPos.getZ();
                        double t = blockPos.getY() - Math.sqrt(r * r + s * s) * 0.381;
                        int u = t > m ? m : (int)t;
                        BlockPos blockPos3 = new BlockPos(pos.getX(), u, pos.getZ());
                        if (this.makeLimb(level, blockSetter, random, blockPos3, blockPos, false, config)) {
                            list.add(new FancyTrunkPlacer.FoliageCoords(blockPos, blockPos3.getY()));
                        }
                    }
                }
            }
        }

        this.makeLimb(level, blockSetter, random, pos, pos.above(k), true, config);
        this.makeBranches(level, blockSetter, random, j, pos, list, config);
        List<FoliagePlacer.FoliageAttachment> list2 = Lists.newArrayList();

        for (FancyTrunkPlacer.FoliageCoords foliageCoords : list) {
            if (this.trimBranches(j, foliageCoords.getBranchBase() - pos.getY())) {
                list2.add(foliageCoords.attachment);
            }
        }

        return list2;
    }

    private boolean makeLimb(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        BlockPos basePos,
        BlockPos offsetPos,
        boolean modifyWorld,
        TreeConfiguration config
    ) {
        if (!modifyWorld && Objects.equals(basePos, offsetPos)) {
            return true;
        }

        BlockPos blockPos = offsetPos.offset(-basePos.getX(), -basePos.getY(), -basePos.getZ());
        int i = this.getSteps(blockPos);
        float f = (float)blockPos.getX() / i;
        float g = (float)blockPos.getY() / i;
        float h = (float)blockPos.getZ() / i;

        for (int j = 0; j <= i; j++) {
            BlockPos blockPos2 = basePos.offset(Mth.floor(0.5F + j * f), Mth.floor(0.5F + j * g), Mth.floor(0.5F + j * h));
            if (modifyWorld) {
                this.placeLog(
                    level,
                    blockSetter,
                    random,
                    blockPos2,
                    config,
                    blockState -> blockState.trySetValue(RotatedPillarBlock.AXIS, this.getLogAxis(basePos, blockPos2))
                );
            } else if (!this.isFree(level, blockPos2)) {
                return false;
            }
        }

        return true;
    }

    private int getSteps(BlockPos pos) {
        int i = Mth.abs(pos.getX());
        int j = Mth.abs(pos.getY());
        int k = Mth.abs(pos.getZ());
        return Math.max(i, Math.max(j, k));
    }

    private Direction.Axis getLogAxis(BlockPos pos, BlockPos otherPos) {
        Direction.Axis axis = Direction.Axis.Y;
        int i = Math.abs(otherPos.getX() - pos.getX());
        int j = Math.abs(otherPos.getZ() - pos.getZ());
        int k = Math.max(i, j);
        if (k > 0) {
            if (i == k) {
                axis = Direction.Axis.X;
            } else {
                axis = Direction.Axis.Z;
            }
        }

        return axis;
    }

    private boolean trimBranches(int maxHeight, int currentHeight) {
        return currentHeight >= maxHeight * 0.2;
    }

    private void makeBranches(
        LevelSimulatedReader level,
        BiConsumer<BlockPos, BlockState> blockSetter,
        RandomSource random,
        int maxHeight,
        BlockPos pos,
        List<FancyTrunkPlacer.FoliageCoords> foliageCoords,
        TreeConfiguration config
    ) {
        for (FancyTrunkPlacer.FoliageCoords foliageCoords2 : foliageCoords) {
            int i = foliageCoords2.getBranchBase();
            BlockPos blockPos = new BlockPos(pos.getX(), i, pos.getZ());
            if (!blockPos.equals(foliageCoords2.attachment.pos()) && this.trimBranches(maxHeight, i - pos.getY())) {
                this.makeLimb(level, blockSetter, random, blockPos, foliageCoords2.attachment.pos(), true, config);
            }
        }
    }

    private static float treeShape(int height, int currentY) {
        if (currentY < height * 0.3F) {
            return -1.0F;
        }

        float f = height / 2.0F;
        float g = f - currentY;
        float h = Mth.sqrt(f * f - g * g);
        if (g == 0.0F) {
            h = f;
        } else if (Math.abs(g) >= f) {
            return 0.0F;
        }

        return h * 0.5F;
    }

    static class FoliageCoords {
        final FoliagePlacer.FoliageAttachment attachment;
        private final int branchBase;

        public FoliageCoords(BlockPos attachmentPos, int branchBase) {
            this.attachment = new FoliagePlacer.FoliageAttachment(attachmentPos, 0, false);
            this.branchBase = branchBase;
        }

        public int getBranchBase() {
            return this.branchBase;
        }
    }
}
