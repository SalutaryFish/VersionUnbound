package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<WallBlock> CODEC = simpleCodec(WallBlock::new);
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final EnumProperty<WallSide> EAST_WALL = BlockStateProperties.EAST_WALL;
    public static final EnumProperty<WallSide> NORTH_WALL = BlockStateProperties.NORTH_WALL;
    public static final EnumProperty<WallSide> SOUTH_WALL = BlockStateProperties.SOUTH_WALL;
    public static final EnumProperty<WallSide> WEST_WALL = BlockStateProperties.WEST_WALL;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private final Map<BlockState, VoxelShape> shapeByIndex;
    private final Map<BlockState, VoxelShape> collisionShapeByIndex;
    private static final int WALL_WIDTH = 3;
    private static final int WALL_HEIGHT = 14;
    private static final int POST_WIDTH = 4;
    private static final int POST_COVER_WIDTH = 1;
    private static final int WALL_COVER_START = 7;
    private static final int WALL_COVER_END = 9;
    private static final VoxelShape POST_TEST = Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0);
    private static final VoxelShape NORTH_TEST = Block.box(7.0, 0.0, 0.0, 9.0, 16.0, 9.0);
    private static final VoxelShape SOUTH_TEST = Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 16.0);
    private static final VoxelShape WEST_TEST = Block.box(0.0, 0.0, 7.0, 9.0, 16.0, 9.0);
    private static final VoxelShape EAST_TEST = Block.box(7.0, 0.0, 7.0, 16.0, 16.0, 9.0);

    @Override
    public MapCodec<WallBlock> codec() {
        return CODEC;
    }

    public WallBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(UP, true)
                .setValue(NORTH_WALL, WallSide.NONE)
                .setValue(EAST_WALL, WallSide.NONE)
                .setValue(SOUTH_WALL, WallSide.NONE)
                .setValue(WEST_WALL, WallSide.NONE)
                .setValue(WATERLOGGED, false)
        );
        this.shapeByIndex = this.makeShapes(4.0F, 3.0F, 16.0F, 0.0F, 14.0F, 16.0F);
        this.collisionShapeByIndex = this.makeShapes(4.0F, 3.0F, 24.0F, 0.0F, 24.0F, 24.0F);
    }

    private static VoxelShape applyWallShape(VoxelShape baseShape, WallSide height, VoxelShape lowShape, VoxelShape tallShape) {
        if (height == WallSide.TALL) {
            return Shapes.or(baseShape, tallShape);
        } else {
            return height == WallSide.LOW ? Shapes.or(baseShape, lowShape) : baseShape;
        }
    }

    private Map<BlockState, VoxelShape> makeShapes(float width, float depth, float wallPostHeight, float wallMinY, float wallLowHeight, float wallTallHeight) {
        float f = 8.0F - width;
        float g = 8.0F + width;
        float h = 8.0F - depth;
        float i = 8.0F + depth;
        VoxelShape voxelShape = Block.box(f, 0.0, f, g, wallPostHeight, g);
        VoxelShape voxelShape2 = Block.box(h, wallMinY, 0.0, i, wallLowHeight, i);
        VoxelShape voxelShape3 = Block.box(h, wallMinY, h, i, wallLowHeight, 16.0);
        VoxelShape voxelShape4 = Block.box(0.0, wallMinY, h, i, wallLowHeight, i);
        VoxelShape voxelShape5 = Block.box(h, wallMinY, h, 16.0, wallLowHeight, i);
        VoxelShape voxelShape6 = Block.box(h, wallMinY, 0.0, i, wallTallHeight, i);
        VoxelShape voxelShape7 = Block.box(h, wallMinY, h, i, wallTallHeight, 16.0);
        VoxelShape voxelShape8 = Block.box(0.0, wallMinY, h, i, wallTallHeight, i);
        VoxelShape voxelShape9 = Block.box(h, wallMinY, h, 16.0, wallTallHeight, i);
        Builder<BlockState, VoxelShape> builder = ImmutableMap.builder();

        for (Boolean boolean_ : UP.getPossibleValues()) {
            for (WallSide wallSide : EAST_WALL.getPossibleValues()) {
                for (WallSide wallSide2 : NORTH_WALL.getPossibleValues()) {
                    for (WallSide wallSide3 : WEST_WALL.getPossibleValues()) {
                        for (WallSide wallSide4 : SOUTH_WALL.getPossibleValues()) {
                            VoxelShape voxelShape10 = Shapes.empty();
                            voxelShape10 = applyWallShape(voxelShape10, wallSide, voxelShape5, voxelShape9);
                            voxelShape10 = applyWallShape(voxelShape10, wallSide3, voxelShape4, voxelShape8);
                            voxelShape10 = applyWallShape(voxelShape10, wallSide2, voxelShape2, voxelShape6);
                            voxelShape10 = applyWallShape(voxelShape10, wallSide4, voxelShape3, voxelShape7);
                            if (boolean_) {
                                voxelShape10 = Shapes.or(voxelShape10, voxelShape);
                            }

                            BlockState blockState = this.defaultBlockState()
                                .setValue(UP, boolean_)
                                .setValue(EAST_WALL, wallSide)
                                .setValue(WEST_WALL, wallSide3)
                                .setValue(NORTH_WALL, wallSide2)
                                .setValue(SOUTH_WALL, wallSide4);
                            builder.put(blockState.setValue(WATERLOGGED, false), voxelShape10);
                            builder.put(blockState.setValue(WATERLOGGED, true), voxelShape10);
                        }
                    }
                }
            }
        }

        return builder.build();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapeByIndex.get(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.collisionShapeByIndex.get(state);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    private boolean connectsTo(BlockState state, boolean sideSolid, Direction direction) {
        Block block = state.getBlock();
        boolean bl = block instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(state, direction);
        return state.is(BlockTags.WALLS) || !isExceptionForConnection(state) && sideSolid || block instanceof IronBarsBlock || bl;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader levelReader = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        BlockPos blockPos2 = blockPos.north();
        BlockPos blockPos3 = blockPos.east();
        BlockPos blockPos4 = blockPos.south();
        BlockPos blockPos5 = blockPos.west();
        BlockPos blockPos6 = blockPos.above();
        BlockState blockState = levelReader.getBlockState(blockPos2);
        BlockState blockState2 = levelReader.getBlockState(blockPos3);
        BlockState blockState3 = levelReader.getBlockState(blockPos4);
        BlockState blockState4 = levelReader.getBlockState(blockPos5);
        BlockState blockState5 = levelReader.getBlockState(blockPos6);
        boolean bl = this.connectsTo(blockState, blockState.isFaceSturdy(levelReader, blockPos2, Direction.SOUTH), Direction.SOUTH);
        boolean bl2 = this.connectsTo(blockState2, blockState2.isFaceSturdy(levelReader, blockPos3, Direction.WEST), Direction.WEST);
        boolean bl3 = this.connectsTo(blockState3, blockState3.isFaceSturdy(levelReader, blockPos4, Direction.NORTH), Direction.NORTH);
        boolean bl4 = this.connectsTo(blockState4, blockState4.isFaceSturdy(levelReader, blockPos5, Direction.EAST), Direction.EAST);
        BlockState blockState6 = this.defaultBlockState().setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
        return this.updateShape(levelReader, blockState6, blockPos6, blockState5, bl, bl2, bl3, bl4);
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        if (facing == Direction.DOWN) {
            return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
        } else {
            return facing == Direction.UP
                ? this.topUpdate(level, state, facingPos, facingState)
                : this.sideUpdate(level, currentPos, state, facingPos, facingState, facing);
        }
    }

    private static boolean isConnected(BlockState state, Property<WallSide> heightProperty) {
        return state.getValue(heightProperty) != WallSide.NONE;
    }

    private static boolean isCovered(VoxelShape firstShape, VoxelShape secondShape) {
        return !Shapes.joinIsNotEmpty(secondShape, firstShape, BooleanOp.ONLY_FIRST);
    }

    private BlockState topUpdate(LevelReader level, BlockState state, BlockPos pos, BlockState secondState) {
        boolean bl = isConnected(state, NORTH_WALL);
        boolean bl2 = isConnected(state, EAST_WALL);
        boolean bl3 = isConnected(state, SOUTH_WALL);
        boolean bl4 = isConnected(state, WEST_WALL);
        return this.updateShape(level, state, pos, secondState, bl, bl2, bl3, bl4);
    }

    private BlockState sideUpdate(LevelReader level, BlockPos firstPos, BlockState firstState, BlockPos secondPos, BlockState secondState, Direction dir) {
        Direction direction = dir.getOpposite();
        boolean bl = dir == Direction.NORTH
            ? this.connectsTo(secondState, secondState.isFaceSturdy(level, secondPos, direction), direction)
            : isConnected(firstState, NORTH_WALL);
        boolean bl2 = dir == Direction.EAST
            ? this.connectsTo(secondState, secondState.isFaceSturdy(level, secondPos, direction), direction)
            : isConnected(firstState, EAST_WALL);
        boolean bl3 = dir == Direction.SOUTH
            ? this.connectsTo(secondState, secondState.isFaceSturdy(level, secondPos, direction), direction)
            : isConnected(firstState, SOUTH_WALL);
        boolean bl4 = dir == Direction.WEST
            ? this.connectsTo(secondState, secondState.isFaceSturdy(level, secondPos, direction), direction)
            : isConnected(firstState, WEST_WALL);
        BlockPos blockPos = firstPos.above();
        BlockState blockState = level.getBlockState(blockPos);
        return this.updateShape(level, firstState, blockPos, blockState, bl, bl2, bl3, bl4);
    }

    private BlockState updateShape(
        LevelReader level,
        BlockState state,
        BlockPos pos,
        BlockState neighbour,
        boolean northConnection,
        boolean eastConnection,
        boolean southConnection,
        boolean westConnection
    ) {
        VoxelShape voxelShape = neighbour.getCollisionShape(level, pos).getFaceShape(Direction.DOWN);
        BlockState blockState = this.updateSides(state, northConnection, eastConnection, southConnection, westConnection, voxelShape);
        return blockState.setValue(UP, this.shouldRaisePost(blockState, neighbour, voxelShape));
    }

    private boolean shouldRaisePost(BlockState state, BlockState neighbour, VoxelShape shape) {
        boolean bl = neighbour.getBlock() instanceof WallBlock && neighbour.getValue(UP);
        if (bl) {
            return true;
        }

        WallSide wallSide = state.getValue(NORTH_WALL);
        WallSide wallSide2 = state.getValue(SOUTH_WALL);
        WallSide wallSide3 = state.getValue(EAST_WALL);
        WallSide wallSide4 = state.getValue(WEST_WALL);
        boolean bl2 = wallSide2 == WallSide.NONE;
        boolean bl3 = wallSide4 == WallSide.NONE;
        boolean bl4 = wallSide3 == WallSide.NONE;
        boolean bl5 = wallSide == WallSide.NONE;
        boolean bl6 = bl5 && bl2 && bl3 && bl4 || bl5 != bl2 || bl3 != bl4;
        if (bl6) {
            return true;
        }

        boolean bl7 = wallSide == WallSide.TALL && wallSide2 == WallSide.TALL || wallSide3 == WallSide.TALL && wallSide4 == WallSide.TALL;
        return bl7 ? false : neighbour.is(BlockTags.WALL_POST_OVERRIDE) || isCovered(shape, POST_TEST);
    }

    private BlockState updateSides(
        BlockState state, boolean northConnection, boolean eastConnection, boolean southConnection, boolean westConnection, VoxelShape wallShape
    ) {
        return state.setValue(NORTH_WALL, this.makeWallState(northConnection, wallShape, NORTH_TEST))
            .setValue(EAST_WALL, this.makeWallState(eastConnection, wallShape, EAST_TEST))
            .setValue(SOUTH_WALL, this.makeWallState(southConnection, wallShape, SOUTH_TEST))
            .setValue(WEST_WALL, this.makeWallState(westConnection, wallShape, WEST_TEST));
    }

    private WallSide makeWallState(boolean allowConnection, VoxelShape shape, VoxelShape neighbourShape) {
        if (allowConnection) {
            return isCovered(shape, neighbourShape) ? WallSide.TALL : WallSide.LOW;
        } else {
            return WallSide.NONE;
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return !state.getValue(WATERLOGGED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, NORTH_WALL, EAST_WALL, WEST_WALL, SOUTH_WALL, WATERLOGGED);
    }

    /**
     * Returns the blockstate with the given rotation from the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_180:
                return state.setValue(NORTH_WALL, state.getValue(SOUTH_WALL))
                    .setValue(EAST_WALL, state.getValue(WEST_WALL))
                    .setValue(SOUTH_WALL, state.getValue(NORTH_WALL))
                    .setValue(WEST_WALL, state.getValue(EAST_WALL));
            case COUNTERCLOCKWISE_90:
                return state.setValue(NORTH_WALL, state.getValue(EAST_WALL))
                    .setValue(EAST_WALL, state.getValue(SOUTH_WALL))
                    .setValue(SOUTH_WALL, state.getValue(WEST_WALL))
                    .setValue(WEST_WALL, state.getValue(NORTH_WALL));
            case CLOCKWISE_90:
                return state.setValue(NORTH_WALL, state.getValue(WEST_WALL))
                    .setValue(EAST_WALL, state.getValue(NORTH_WALL))
                    .setValue(SOUTH_WALL, state.getValue(EAST_WALL))
                    .setValue(WEST_WALL, state.getValue(SOUTH_WALL));
            default:
                return state;
        }
    }

    /**
     * Returns the blockstate with the given mirror of the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        switch (mirror) {
            case LEFT_RIGHT:
                return state.setValue(NORTH_WALL, state.getValue(SOUTH_WALL)).setValue(SOUTH_WALL, state.getValue(NORTH_WALL));
            case FRONT_BACK:
                return state.setValue(EAST_WALL, state.getValue(WEST_WALL)).setValue(WEST_WALL, state.getValue(EAST_WALL));
            default:
                return super.mirror(state, mirror);
        }
    }
}
