package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Column;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.LargeDripstoneConfiguration;
import net.minecraft.world.phys.Vec3;

public class LargeDripstoneFeature extends Feature<LargeDripstoneConfiguration> {
    public LargeDripstoneFeature(Codec<LargeDripstoneConfiguration> codec) {
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
    public boolean place(FeaturePlaceContext<LargeDripstoneConfiguration> context) {
        WorldGenLevel worldGenLevel = context.level();
        BlockPos blockPos = context.origin();
        LargeDripstoneConfiguration largeDripstoneConfiguration = context.config();
        RandomSource randomSource = context.random();
        if (!DripstoneUtils.isEmptyOrWater(worldGenLevel, blockPos)) {
            return false;
        }

        Optional<Column> optional = Column.scan(
            worldGenLevel,
            blockPos,
            largeDripstoneConfiguration.floorToCeilingSearchRange,
            DripstoneUtils::isEmptyOrWater,
            DripstoneUtils::isDripstoneBaseOrLava
        );
        if (!optional.isEmpty() && optional.get() instanceof Column.Range) {
            Column.Range range = (Column.Range)optional.get();
            if (range.height() < 4) {
                return false;
            }

            int i = (int)(range.height() * largeDripstoneConfiguration.maxColumnRadiusToCaveHeightRatio);
            int j = Mth.clamp(i, largeDripstoneConfiguration.columnRadius.getMinValue(), largeDripstoneConfiguration.columnRadius.getMaxValue());
            int k = Mth.randomBetweenInclusive(randomSource, largeDripstoneConfiguration.columnRadius.getMinValue(), j);
            LargeDripstoneFeature.LargeDripstone largeDripstone = makeDripstone(
                blockPos.atY(range.ceiling() - 1),
                false,
                randomSource,
                k,
                largeDripstoneConfiguration.stalactiteBluntness,
                largeDripstoneConfiguration.heightScale
            );
            LargeDripstoneFeature.LargeDripstone largeDripstone2 = makeDripstone(
                blockPos.atY(range.floor() + 1),
                true,
                randomSource,
                k,
                largeDripstoneConfiguration.stalagmiteBluntness,
                largeDripstoneConfiguration.heightScale
            );
            LargeDripstoneFeature.WindOffsetter windOffsetter;
            if (largeDripstone.isSuitableForWind(largeDripstoneConfiguration) && largeDripstone2.isSuitableForWind(largeDripstoneConfiguration)) {
                windOffsetter = new LargeDripstoneFeature.WindOffsetter(blockPos.getY(), randomSource, largeDripstoneConfiguration.windSpeed);
            } else {
                windOffsetter = LargeDripstoneFeature.WindOffsetter.noWind();
            }

            boolean bl = largeDripstone.moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(worldGenLevel, windOffsetter);
            boolean bl2 = largeDripstone2.moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(worldGenLevel, windOffsetter);
            if (bl) {
                largeDripstone.placeBlocks(worldGenLevel, randomSource, windOffsetter);
            }

            if (bl2) {
                largeDripstone2.placeBlocks(worldGenLevel, randomSource, windOffsetter);
            }

            return true;
        } else {
            return false;
        }
    }

    private static LargeDripstoneFeature.LargeDripstone makeDripstone(
        BlockPos root, boolean pointingUp, RandomSource random, int radius, FloatProvider bluntnessBase, FloatProvider scaleBase
    ) {
        return new LargeDripstoneFeature.LargeDripstone(root, pointingUp, radius, bluntnessBase.sample(random), scaleBase.sample(random));
    }

    private void placeDebugMarkers(WorldGenLevel level, BlockPos pos, Column.Range range, LargeDripstoneFeature.WindOffsetter windOffsetter) {
        level.setBlock(windOffsetter.offset(pos.atY(range.ceiling() - 1)), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
        level.setBlock(windOffsetter.offset(pos.atY(range.floor() + 1)), Blocks.GOLD_BLOCK.defaultBlockState(), 2);

        for (BlockPos.MutableBlockPos mutableBlockPos = pos.atY(range.floor() + 2).mutable();
            mutableBlockPos.getY() < range.ceiling() - 1;
            mutableBlockPos.move(Direction.UP)
        ) {
            BlockPos blockPos = windOffsetter.offset(mutableBlockPos);
            if (DripstoneUtils.isEmptyOrWater(level, blockPos) || level.getBlockState(blockPos).is(Blocks.DRIPSTONE_BLOCK)) {
                level.setBlock(blockPos, Blocks.CREEPER_HEAD.defaultBlockState(), 2);
            }
        }
    }

    static final class LargeDripstone {
        private BlockPos root;
        private final boolean pointingUp;
        private int radius;
        private final double bluntness;
        private final double scale;

        LargeDripstone(BlockPos root, boolean pointingUp, int radius, double bluntness, double scale) {
            this.root = root;
            this.pointingUp = pointingUp;
            this.radius = radius;
            this.bluntness = bluntness;
            this.scale = scale;
        }

        private int getHeight() {
            return this.getHeightAtRadius(0.0F);
        }

        private int getMinY() {
            return this.pointingUp ? this.root.getY() : this.root.getY() - this.getHeight();
        }

        private int getMaxY() {
            return !this.pointingUp ? this.root.getY() : this.root.getY() + this.getHeight();
        }

        boolean moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(WorldGenLevel level, LargeDripstoneFeature.WindOffsetter windOffsetter) {
            while (this.radius > 1) {
                BlockPos.MutableBlockPos mutableBlockPos = this.root.mutable();
                int i = Math.min(10, this.getHeight());

                for (int j = 0; j < i; j++) {
                    if (level.getBlockState(mutableBlockPos).is(Blocks.LAVA)) {
                        return false;
                    }

                    if (DripstoneUtils.isCircleMostlyEmbeddedInStone(level, windOffsetter.offset(mutableBlockPos), this.radius)) {
                        this.root = mutableBlockPos;
                        return true;
                    }

                    mutableBlockPos.move(this.pointingUp ? Direction.DOWN : Direction.UP);
                }

                this.radius /= 2;
            }

            return false;
        }

        private int getHeightAtRadius(float radius) {
            return (int)DripstoneUtils.getDripstoneHeight(radius, this.radius, this.scale, this.bluntness);
        }

        void placeBlocks(WorldGenLevel level, RandomSource random, LargeDripstoneFeature.WindOffsetter windOffsetter) {
            for (int i = -this.radius; i <= this.radius; i++) {
                for (int j = -this.radius; j <= this.radius; j++) {
                    float f = Mth.sqrt(i * i + j * j);
                    if (!(f > this.radius)) {
                        int k = this.getHeightAtRadius(f);
                        if (k > 0) {
                            if (random.nextFloat() < 0.2) {
                                k = (int)(k * Mth.randomBetween(random, 0.8F, 1.0F));
                            }

                            BlockPos.MutableBlockPos mutableBlockPos = this.root.offset(i, 0, j).mutable();
                            boolean bl = false;
                            int l = this.pointingUp
                                ? level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, mutableBlockPos.getX(), mutableBlockPos.getZ())
                                : Integer.MAX_VALUE;

                            for (int m = 0; m < k && mutableBlockPos.getY() < l; m++) {
                                BlockPos blockPos = windOffsetter.offset(mutableBlockPos);
                                if (DripstoneUtils.isEmptyOrWaterOrLava(level, blockPos)) {
                                    bl = true;
                                    Block block = Blocks.DRIPSTONE_BLOCK;
                                    level.setBlock(blockPos, block.defaultBlockState(), 2);
                                } else if (bl && level.getBlockState(blockPos).is(BlockTags.BASE_STONE_OVERWORLD)) {
                                    break;
                                }

                                mutableBlockPos.move(this.pointingUp ? Direction.UP : Direction.DOWN);
                            }
                        }
                    }
                }
            }
        }

        boolean isSuitableForWind(LargeDripstoneConfiguration config) {
            return this.radius >= config.minRadiusForWind && this.bluntness >= config.minBluntnessForWind;
        }
    }

    static final class WindOffsetter {
        private final int originY;
        @Nullable
        private final Vec3 windSpeed;

        WindOffsetter(int originY, RandomSource random, FloatProvider magnitude) {
            this.originY = originY;
            float f = magnitude.sample(random);
            float g = Mth.randomBetween(random, 0.0F, (float) Math.PI);
            this.windSpeed = new Vec3(Mth.cos(g) * f, 0.0, Mth.sin(g) * f);
        }

        private WindOffsetter() {
            this.originY = 0;
            this.windSpeed = null;
        }

        static LargeDripstoneFeature.WindOffsetter noWind() {
            return new LargeDripstoneFeature.WindOffsetter();
        }

        BlockPos offset(BlockPos pos) {
            if (this.windSpeed == null) {
                return pos;
            }

            int i = this.originY - pos.getY();
            Vec3 vec3 = this.windSpeed.scale(i);
            return pos.offset(Mth.floor(vec3.x), 0, Mth.floor(vec3.z));
        }
    }
}
