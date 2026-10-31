package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BaseRailBlock extends Block implements SimpleWaterloggedBlock {
    protected static final VoxelShape FLAT_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);
    protected static final VoxelShape HALF_BLOCK_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private final boolean isStraight;

    public static boolean isRail(Level level, BlockPos pos) {
        return isRail(level.getBlockState(pos));
    }

    public static boolean isRail(BlockState state) {
        return state.is(BlockTags.RAILS) && state.getBlock() instanceof BaseRailBlock;
    }

    protected BaseRailBlock(boolean isStraight, BlockBehaviour.Properties properties) {
        super(properties);
        this.isStraight = isStraight;
    }

    @Override
    protected abstract MapCodec<? extends BaseRailBlock> codec();

    public boolean isStraight() {
        return this.isStraight;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        RailShape railShape = state.is(this) ? state.getValue(this.getShapeProperty()) : null;
        return railShape != null && railShape.isAscending() ? HALF_BLOCK_AABB : FLAT_AABB;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return canSupportRigidBlock(level, pos.below());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            this.updateState(state, level, pos, isMoving);
        }
    }

    protected BlockState updateState(BlockState state, Level level, BlockPos pos, boolean movedByPiston) {
        state = this.updateDir(level, pos, state, true);
        if (this.isStraight) {
            level.neighborChanged(state, pos, this, pos, movedByPiston);
        }

        return state;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && level.getBlockState(pos).is(this)) {
            RailShape railShape = state.getValue(this.getShapeProperty());
            if (shouldBeRemoved(pos, level, railShape)) {
                dropResources(state, level, pos);
                level.removeBlock(pos, isMoving);
            } else {
                this.updateState(state, level, pos, block);
            }
        }
    }

    private static boolean shouldBeRemoved(BlockPos pos, Level level, RailShape shape) {
        if (!canSupportRigidBlock(level, pos.below())) {
            return true;
        }

        switch (shape) {
            case ASCENDING_EAST:
                return !canSupportRigidBlock(level, pos.east());
            case ASCENDING_WEST:
                return !canSupportRigidBlock(level, pos.west());
            case ASCENDING_NORTH:
                return !canSupportRigidBlock(level, pos.north());
            case ASCENDING_SOUTH:
                return !canSupportRigidBlock(level, pos.south());
            default:
                return false;
        }
    }

    protected void updateState(BlockState state, Level level, BlockPos pos, Block neighborBlock) {
    }

    protected BlockState updateDir(Level level, BlockPos pos, BlockState state, boolean alwaysPlace) {
        if (level.isClientSide) {
            return state;
        }

        RailShape railShape = state.getValue(this.getShapeProperty());
        return new RailState(level, pos, state).place(level.hasNeighborSignal(pos), alwaysPlace, railShape).getState();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving) {
            super.onRemove(state, level, pos, newState, isMoving);
            if (state.getValue(this.getShapeProperty()).isAscending()) {
                level.updateNeighborsAt(pos.above(), this);
            }

            if (this.isStraight) {
                level.updateNeighborsAt(pos, this);
                level.updateNeighborsAt(pos.below(), this);
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        boolean bl = fluidState.getType() == Fluids.WATER;
        BlockState blockState = super.defaultBlockState();
        Direction direction = context.getHorizontalDirection();
        boolean bl2 = direction == Direction.EAST || direction == Direction.WEST;
        return blockState.setValue(this.getShapeProperty(), bl2 ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH).setValue(WATERLOGGED, bl);
    }

    public abstract Property<RailShape> getShapeProperty();

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
}
