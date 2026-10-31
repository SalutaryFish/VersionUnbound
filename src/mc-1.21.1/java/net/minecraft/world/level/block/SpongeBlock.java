package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class SpongeBlock extends Block {
    public static final MapCodec<SpongeBlock> CODEC = simpleCodec(SpongeBlock::new);
    public static final int MAX_DEPTH = 6;
    public static final int MAX_COUNT = 64;
    private static final Direction[] ALL_DIRECTIONS = Direction.values();

    @Override
    public MapCodec<SpongeBlock> codec() {
        return CODEC;
    }

    protected SpongeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            this.tryAbsorbWater(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        this.tryAbsorbWater(level, pos);
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    protected void tryAbsorbWater(Level level, BlockPos pos) {
        if (this.removeWaterBreadthFirstSearch(level, pos)) {
            level.setBlock(pos, Blocks.WET_SPONGE.defaultBlockState(), 2);
            level.playSound((Player)null, pos, SoundEvents.SPONGE_ABSORB, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private boolean removeWaterBreadthFirstSearch(Level level, BlockPos pos) {
        return BlockPos.breadthFirstTraversal(
                pos,
                6,
                65,
                (validPos, queueAdder) -> {
                    for (Direction direction : ALL_DIRECTIONS) {
                        queueAdder.accept(validPos.relative(direction));
                    }
                },
                currentPos -> {
                    if (currentPos.equals(pos)) {
                        return true;
                    } else {
                        BlockState blockState = level.getBlockState(currentPos);
                        FluidState fluidState = level.getFluidState(currentPos);
                        if (!fluidState.is(FluidTags.WATER)) {
                            return false;
                        } else if (blockState.getBlock() instanceof BucketPickup bucketPickup
                            && !bucketPickup.pickupBlock(null, level, currentPos, blockState).isEmpty()) {
                            return true;
                        } else {
                            if (blockState.getBlock() instanceof LiquidBlock) {
                                level.setBlock(currentPos, Blocks.AIR.defaultBlockState(), 3);
                            } else {
                                if (!blockState.is(Blocks.KELP)
                                    && !blockState.is(Blocks.KELP_PLANT)
                                    && !blockState.is(Blocks.SEAGRASS)
                                    && !blockState.is(Blocks.TALL_SEAGRASS)) {
                                    return false;
                                }

                                BlockEntity blockEntity = blockState.hasBlockEntity() ? level.getBlockEntity(currentPos) : null;
                                dropResources(blockState, level, currentPos, blockEntity);
                                level.setBlock(currentPos, Blocks.AIR.defaultBlockState(), 3);
                            }

                            return true;
                        }
                    }
                }
            )
            > 1;
    }
}
