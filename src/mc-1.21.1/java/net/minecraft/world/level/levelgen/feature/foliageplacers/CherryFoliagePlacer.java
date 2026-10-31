package net.minecraft.world.level.levelgen.feature.foliageplacers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;

public class CherryFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<CherryFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
        instance -> foliagePlacerParts(instance)
            .and(
                instance.group(
                    IntProvider.codec(4, 16).fieldOf("height").forGetter(cherryFoliagePlacer -> cherryFoliagePlacer.height),
                    Codec.floatRange(0.0F, 1.0F)
                        .fieldOf("wide_bottom_layer_hole_chance")
                        .forGetter(cherryFoliagePlacer -> cherryFoliagePlacer.wideBottomLayerHoleChance),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("corner_hole_chance").forGetter(cherryFoliagePlacer -> cherryFoliagePlacer.wideBottomLayerHoleChance),
                    Codec.floatRange(0.0F, 1.0F).fieldOf("hanging_leaves_chance").forGetter(cherryFoliagePlacer -> cherryFoliagePlacer.hangingLeavesChance),
                    Codec.floatRange(0.0F, 1.0F)
                        .fieldOf("hanging_leaves_extension_chance")
                        .forGetter(cherryFoliagePlacer -> cherryFoliagePlacer.hangingLeavesExtensionChance)
                )
            )
            .apply(instance, CherryFoliagePlacer::new)
    );
    private final IntProvider height;
    private final float wideBottomLayerHoleChance;
    private final float cornerHoleChance;
    private final float hangingLeavesChance;
    private final float hangingLeavesExtensionChance;

    public CherryFoliagePlacer(
        IntProvider radius,
        IntProvider offset,
        IntProvider height,
        float wideBottomLayerHoleChance,
        float cornerHoleChance,
        float hangingLeavesChance,
        float hangingLeavesExtensionChance
    ) {
        super(radius, offset);
        this.height = height;
        this.wideBottomLayerHoleChance = wideBottomLayerHoleChance;
        this.cornerHoleChance = cornerHoleChance;
        this.hangingLeavesChance = hangingLeavesChance;
        this.hangingLeavesExtensionChance = hangingLeavesExtensionChance;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return FoliagePlacerType.CHERRY_FOLIAGE_PLACER;
    }

    @Override
    protected void createFoliage(
        LevelSimulatedReader level,
        FoliagePlacer.FoliageSetter blockSetter,
        RandomSource random,
        TreeConfiguration config,
        int maxFreeTreeHeight,
        FoliagePlacer.FoliageAttachment attachment,
        int foliageHeight,
        int foliageRadius,
        int offset
    ) {
        boolean bl = attachment.doubleTrunk();
        BlockPos blockPos = attachment.pos().above(offset);
        int i = foliageRadius + attachment.radiusOffset() - 1;
        this.placeLeavesRow(level, blockSetter, random, config, blockPos, i - 2, foliageHeight - 3, bl);
        this.placeLeavesRow(level, blockSetter, random, config, blockPos, i - 1, foliageHeight - 4, bl);

        for (int j = foliageHeight - 5; j >= 0; j--) {
            this.placeLeavesRow(level, blockSetter, random, config, blockPos, i, j, bl);
        }

        this.placeLeavesRowWithHangingLeavesBelow(
            level, blockSetter, random, config, blockPos, i, -1, bl, this.hangingLeavesChance, this.hangingLeavesExtensionChance
        );
        this.placeLeavesRowWithHangingLeavesBelow(
            level, blockSetter, random, config, blockPos, i - 1, -2, bl, this.hangingLeavesChance, this.hangingLeavesExtensionChance
        );
    }

    @Override
    public int foliageHeight(RandomSource random, int height, TreeConfiguration config) {
        return this.height.sample(random);
    }

    /**
     * Skips certain positions based on the provided shape, such as rounding corners randomly.
     * The coordinates are passed in as absolute value, and should be within [0, {@code range}].
     */
    @Override
    protected boolean shouldSkipLocation(RandomSource random, int localX, int localY, int localZ, int range, boolean large) {
        if (localY == -1 && (localX == range || localZ == range) && random.nextFloat() < this.wideBottomLayerHoleChance) {
            return true;
        }

        boolean bl = localX == range && localZ == range;
        boolean bl2 = range > 2;
        return bl2 ? bl || localX + localZ > range * 2 - 2 && random.nextFloat() < this.cornerHoleChance : bl && random.nextFloat() < this.cornerHoleChance;
    }
}
