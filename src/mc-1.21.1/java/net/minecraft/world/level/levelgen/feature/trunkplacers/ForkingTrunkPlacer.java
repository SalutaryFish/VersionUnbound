package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class ForkingTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<ForkingTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> trunkPlacerParts(instance).apply(instance, ForkingTrunkPlacer::new)
    );

    public ForkingTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return TrunkPlacerType.FORKING_TRUNK_PLACER;
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
        setDirtAt(level, blockSetter, random, pos.below(), config);
        List<FoliagePlacer.FoliageAttachment> list = Lists.newArrayList();
        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int i = freeTreeHeight - random.nextInt(4) - 1;
        int j = 3 - random.nextInt(3);
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int k = pos.getX();
        int l = pos.getZ();
        OptionalInt optionalInt = OptionalInt.empty();

        for (int m = 0; m < freeTreeHeight; m++) {
            int n = pos.getY() + m;
            if (m >= i && j > 0) {
                k += direction.getStepX();
                l += direction.getStepZ();
                j--;
            }

            if (this.placeLog(level, blockSetter, random, mutableBlockPos.set(k, n, l), config)) {
                optionalInt = OptionalInt.of(n + 1);
            }
        }

        if (optionalInt.isPresent()) {
            list.add(new FoliagePlacer.FoliageAttachment(new BlockPos(k, optionalInt.getAsInt(), l), 1, false));
        }

        k = pos.getX();
        l = pos.getZ();
        Direction direction2 = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        if (direction2 != direction) {
            int n = i - random.nextInt(2) - 1;
            int o = 1 + random.nextInt(3);
            optionalInt = OptionalInt.empty();

            for (int p = n; p < freeTreeHeight && o > 0; o--) {
                if (p >= 1) {
                    int q = pos.getY() + p;
                    k += direction2.getStepX();
                    l += direction2.getStepZ();
                    if (this.placeLog(level, blockSetter, random, mutableBlockPos.set(k, q, l), config)) {
                        optionalInt = OptionalInt.of(q + 1);
                    }
                }

                p++;
            }

            if (optionalInt.isPresent()) {
                list.add(new FoliagePlacer.FoliageAttachment(new BlockPos(k, optionalInt.getAsInt(), l), 0, false));
            }
        }

        return list;
    }
}
