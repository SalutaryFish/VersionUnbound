package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

public class SculkBlock extends DropExperienceBlock implements SculkBehaviour {
    public static final MapCodec<SculkBlock> CODEC = simpleCodec(SculkBlock::new);

    @Override
    public MapCodec<SculkBlock> codec() {
        return CODEC;
    }

    public SculkBlock(BlockBehaviour.Properties properties) {
        super(ConstantInt.of(1), properties);
    }

    @Override
    public int attemptUseCharge(
        SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos pos, RandomSource random, SculkSpreader spreader, boolean shouldConvertBlocks
    ) {
        int i = cursor.getCharge();
        if (i != 0 && random.nextInt(spreader.chargeDecayRate()) == 0) {
            BlockPos blockPos = cursor.getPos();
            boolean bl = blockPos.closerThan(pos, spreader.noGrowthRadius());
            if (!bl && canPlaceGrowth(level, blockPos)) {
                int j = spreader.growthSpawnCost();
                if (random.nextInt(j) < i) {
                    BlockPos blockPos2 = blockPos.above();
                    BlockState blockState = this.getRandomGrowthState(level, blockPos2, random, spreader.isWorldGeneration());
                    level.setBlock(blockPos2, blockState, 3);
                    level.playSound(null, blockPos, blockState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                return Math.max(0, i - j);
            } else {
                return random.nextInt(spreader.additionalDecayRate()) != 0 ? i : i - (bl ? 1 : getDecayPenalty(spreader, blockPos, pos, i));
            }
        } else {
            return i;
        }
    }

    private static int getDecayPenalty(SculkSpreader spreader, BlockPos cursorPos, BlockPos rootPos, int charge) {
        int i = spreader.noGrowthRadius();
        float f = Mth.square((float)Math.sqrt(cursorPos.distSqr(rootPos)) - i);
        int j = Mth.square(24 - i);
        float g = Math.min(1.0F, f / j);
        return Math.max(1, (int)(charge * g * 0.5F));
    }

    private BlockState getRandomGrowthState(LevelAccessor level, BlockPos pos, RandomSource random, boolean isWorldGeneration) {
        BlockState blockState;
        if (random.nextInt(11) == 0) {
            blockState = Blocks.SCULK_SHRIEKER.defaultBlockState().setValue(SculkShriekerBlock.CAN_SUMMON, isWorldGeneration);
        } else {
            blockState = Blocks.SCULK_SENSOR.defaultBlockState();
        }

        return blockState.hasProperty(BlockStateProperties.WATERLOGGED) && !level.getFluidState(pos).isEmpty()
            ? blockState.setValue(BlockStateProperties.WATERLOGGED, true)
            : blockState;
    }

    private static boolean canPlaceGrowth(LevelAccessor level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos.above());
        if (blockState.isAir() || blockState.is(Blocks.WATER) && blockState.getFluidState().is(Fluids.WATER)) {
            int i = 0;

            for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 2, 4))) {
                BlockState blockState2 = level.getBlockState(blockPos);
                if (blockState2.is(Blocks.SCULK_SENSOR) || blockState2.is(Blocks.SCULK_SHRIEKER)) {
                    i++;
                }

                if (i > 2) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean canChangeBlockStateOnSpread() {
        return false;
    }
}
