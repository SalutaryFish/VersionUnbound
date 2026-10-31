package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class ChorusPlantBlock extends PipeBlock {
    public static final MapCodec<ChorusPlantBlock> CODEC = simpleCodec(ChorusPlantBlock::new);

    @Override
    public MapCodec<ChorusPlantBlock> codec() {
        return CODEC;
    }

    protected ChorusPlantBlock(BlockBehaviour.Properties properties) {
        super(0.3125F, properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getStateWithConnections(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

    public static BlockState getStateWithConnections(BlockGetter level, BlockPos pos, BlockState state) {
        BlockState blockState = level.getBlockState(pos.below());
        BlockState blockState2 = level.getBlockState(pos.above());
        BlockState blockState3 = level.getBlockState(pos.north());
        BlockState blockState4 = level.getBlockState(pos.east());
        BlockState blockState5 = level.getBlockState(pos.south());
        BlockState blockState6 = level.getBlockState(pos.west());
        Block block = state.getBlock();
        return state.trySetValue(DOWN, blockState.is(block) || blockState.is(Blocks.CHORUS_FLOWER) || blockState.is(Blocks.END_STONE))
            .trySetValue(UP, blockState2.is(block) || blockState2.is(Blocks.CHORUS_FLOWER))
            .trySetValue(NORTH, blockState3.is(block) || blockState3.is(Blocks.CHORUS_FLOWER))
            .trySetValue(EAST, blockState4.is(block) || blockState4.is(Blocks.CHORUS_FLOWER))
            .trySetValue(SOUTH, blockState5.is(block) || blockState5.is(Blocks.CHORUS_FLOWER))
            .trySetValue(WEST, blockState6.is(block) || blockState6.is(Blocks.CHORUS_FLOWER));
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (!state.canSurvive(level, currentPos)) {
            level.scheduleTick(currentPos, this, 1);
            return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
        } else {
            boolean bl = facingState.is(this) || facingState.is(Blocks.CHORUS_FLOWER) || facing == Direction.DOWN && facingState.is(Blocks.END_STONE);
            return state.setValue(PROPERTY_BY_DIRECTION.get(facing), bl);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos.below());
        boolean bl = !level.getBlockState(pos.above()).isAir() && !blockState.isAir();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = pos.relative(direction);
            BlockState blockState2 = level.getBlockState(blockPos);
            if (blockState2.is(this)) {
                if (bl) {
                    return false;
                }

                BlockState blockState3 = level.getBlockState(blockPos.below());
                if (blockState3.is(this) || blockState3.is(Blocks.END_STONE)) {
                    return true;
                }
            }
        }

        return blockState.is(this) || blockState.is(Blocks.END_STONE);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
