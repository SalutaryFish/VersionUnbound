package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class DarkOakTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<DarkOakTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance).apply(instance, DarkOakTrunkPlacer::new)
    );

    public DarkOakTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.DARK_OAK_TRUNK_PLACER;
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
        List<FoliagePlacer.FoliageAttachment> list = Lists.newArrayList();
        BlockPos blockPos = pos.below();
        setDirtAt(level, blockSetter, random, blockPos, config);
        setDirtAt(level, blockSetter, random, blockPos.east(), config);
        setDirtAt(level, blockSetter, random, blockPos.south(), config);
        setDirtAt(level, blockSetter, random, blockPos.south().east(), config);
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int i = freeTreeHeight - random.nextInt(4);
        int j = 2 - random.nextInt(3);
        int k = pos.getX();
        int l = pos.getY();
        int m = pos.getZ();
        int n = k;
        int o = m;
        int p = l + freeTreeHeight - 1;

        for (int q = 0; q < freeTreeHeight; q++) {
            if (q >= i && j > 0) {
                n += direction.getStepX();
                o += direction.getStepZ();
                j--;
            }

            int r = l + q;
            BlockPos blockPos2 = new BlockPos(n, r, o);
            if (TreeFeature.isAirOrLeaves(level, blockPos2)) {
                this.placeLog(level, blockSetter, random, blockPos2, config);
                this.placeLog(level, blockSetter, random, blockPos2.east(), config);
                this.placeLog(level, blockSetter, random, blockPos2.south(), config);
                this.placeLog(level, blockSetter, random, blockPos2.east().south(), config);
            }
        }

        list.add(new FoliagePlacer.FoliageAttachment(new BlockPos(n, p, o), 0, true));

        for (int q = -1; q <= 2; q++) {
            for (int r = -1; r <= 2; r++) {
                if ((q < 0 || q > 1 || r < 0 || r > 1) && random.nextInt(3) <= 0) {
                    int s = random.nextInt(3) + 2;

                    for (int t = 0; t < s; t++) {
                        this.placeLog(level, blockSetter, random, new BlockPos(k + q, p - t - 1, m + r), config);
                    }

                    list.add(new FoliagePlacer.FoliageAttachment(new BlockPos(n + q, p, o + r), 0, false));
                }
            }
        }

        return list;
    }
}
