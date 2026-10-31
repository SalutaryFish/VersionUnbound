package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class VineBlock extends Block {
    public static final MapCodec<VineBlock> CODEC = simpleCodec(VineBlock::new);
    public static final BooleanProperty UP = PipeBlock.UP;
    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION
        .entrySet()
        .stream()
        .filter(entry -> entry.getKey() != Direction.DOWN)
        .collect(Util.toMap());
    protected static final float AABB_OFFSET = 1.0F;
    private static final VoxelShape UP_AABB = Block.box(0.0, 15.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape WEST_AABB = Block.box(0.0, 0.0, 0.0, 1.0, 16.0, 16.0);
    private static final VoxelShape EAST_AABB = Block.box(15.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape NORTH_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 1.0);
    private static final VoxelShape SOUTH_AABB = Block.box(0.0, 0.0, 15.0, 16.0, 16.0, 16.0);
    private final Map<BlockState, VoxelShape> shapesCache;

    @Override
    public MapCodec<VineBlock> codec() {
        return CODEC;
    }

    public VineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(UP, false).setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
        );
        this.shapesCache = ImmutableMap.copyOf(
            this.stateDefinition.getPossibleStates().stream().collect(Collectors.toMap(Function.identity(), VineBlock::calculateShape))
        );
    }

    private static VoxelShape calculateShape(BlockState state) {
        VoxelShape voxelShape = Shapes.empty();
        if (state.getValue(UP)) {
            voxelShape = UP_AABB;
        }

        if (state.getValue(NORTH)) {
            voxelShape = Shapes.or(voxelShape, NORTH_AABB);
        }

        if (state.getValue(SOUTH)) {
            voxelShape = Shapes.or(voxelShape, SOUTH_AABB);
        }

        if (state.getValue(EAST)) {
            voxelShape = Shapes.or(voxelShape, EAST_AABB);
        }

        if (state.getValue(WEST)) {
            voxelShape = Shapes.or(voxelShape, WEST_AABB);
        }

        return voxelShape.isEmpty() ? Shapes.block() : voxelShape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapesCache.get(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return this.hasFaces(this.getUpdatedState(state, level, pos));
    }

    private boolean hasFaces(BlockState state) {
        return this.countFaces(state) > 0;
    }

    private int countFaces(BlockState state) {
        int i = 0;

        for (BooleanProperty booleanProperty : PROPERTY_BY_DIRECTION.values()) {
            if (state.getValue(booleanProperty)) {
                i++;
            }
        }

        return i;
    }

    private boolean canSupportAtFace(BlockGetter level, BlockPos pos, Direction direction) {
        if (direction == Direction.DOWN) {
            return false;
        }

        BlockPos blockPos = pos.relative(direction);
        if (isAcceptableNeighbour(level, blockPos, direction)) {
            return true;
        }

        if (direction.getAxis() == Direction.Axis.Y) {
            return false;
        }

        BooleanProperty booleanProperty = PROPERTY_BY_DIRECTION.get(direction);
        BlockState blockState = level.getBlockState(pos.above());
        return blockState.is(this) && blockState.getValue(booleanProperty);
    }

    public static boolean isAcceptableNeighbour(BlockGetter blockReader, BlockPos neighborPos, Direction attachedFace) {
        return MultifaceBlock.canAttachTo(blockReader, attachedFace, neighborPos, blockReader.getBlockState(neighborPos));
    }

    private BlockState getUpdatedState(BlockState state, BlockGetter level, BlockPos pos) {
        BlockPos blockPos = pos.above();
        if (state.getValue(UP)) {
            state = state.setValue(UP, isAcceptableNeighbour(level, blockPos, Direction.DOWN));
        }

        BlockState blockState = null;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BooleanProperty booleanProperty = getPropertyForFace(direction);
            if (state.getValue(booleanProperty)) {
                boolean bl = this.canSupportAtFace(level, pos, direction);
                if (!bl) {
                    if (blockState == null) {
                        blockState = level.getBlockState(blockPos);
                    }

                    bl = blockState.is(this) && blockState.getValue(booleanProperty);
                }

                state = state.setValue(booleanProperty, bl);
            }
        }

        return state;
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (facing == Direction.DOWN) {
            return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
        }

        BlockState blockState = this.getUpdatedState(state, level, currentPos);
        return !this.hasFaces(blockState) ? Blocks.AIR.defaultBlockState() : blockState;
    }

    /**
     * Performs a random tick on a block.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getGameRules().getBoolean(GameRules.RULE_DO_VINES_SPREAD)) {
            if (random.nextInt(4) == 0) {
                Direction direction = Direction.getRandom(random);
                BlockPos blockPos = pos.above();
                if (direction.getAxis().isHorizontal() && !state.getValue(getPropertyForFace(direction))) {
                    if (this.canSpread(level, pos)) {
                        BlockPos blockPos2 = pos.relative(direction);
                        BlockState blockState = level.getBlockState(blockPos2);
                        if (blockState.isAir()) {
                            Direction direction2 = direction.getClockWise();
                            Direction direction3 = direction.getCounterClockWise();
                            boolean bl = state.getValue(getPropertyForFace(direction2));
                            boolean bl2 = state.getValue(getPropertyForFace(direction3));
                            BlockPos blockPos3 = blockPos2.relative(direction2);
                            BlockPos blockPos4 = blockPos2.relative(direction3);
                            if (bl && isAcceptableNeighbour(level, blockPos3, direction2)) {
                                level.setBlock(blockPos2, this.defaultBlockState().setValue(getPropertyForFace(direction2), true), 2);
                            } else if (bl2 && isAcceptableNeighbour(level, blockPos4, direction3)) {
                                level.setBlock(blockPos2, this.defaultBlockState().setValue(getPropertyForFace(direction3), true), 2);
                            } else {
                                Direction direction4 = direction.getOpposite();
                                if (bl && level.isEmptyBlock(blockPos3) && isAcceptableNeighbour(level, pos.relative(direction2), direction4)) {
                                    level.setBlock(blockPos3, this.defaultBlockState().setValue(getPropertyForFace(direction4), true), 2);
                                } else if (bl2 && level.isEmptyBlock(blockPos4) && isAcceptableNeighbour(level, pos.relative(direction3), direction4)) {
                                    level.setBlock(blockPos4, this.defaultBlockState().setValue(getPropertyForFace(direction4), true), 2);
                                } else if (random.nextFloat() < 0.05 && isAcceptableNeighbour(level, blockPos2.above(), Direction.UP)) {
                                    level.setBlock(blockPos2, this.defaultBlockState().setValue(UP, true), 2);
                                }
                            }
                        } else if (isAcceptableNeighbour(level, blockPos2, direction)) {
                            level.setBlock(pos, state.setValue(getPropertyForFace(direction), true), 2);
                        }
                    }
                } else {
                    if (direction == Direction.UP && pos.getY() < level.getMaxBuildHeight() - 1) {
                        if (this.canSupportAtFace(level, pos, direction)) {
                            level.setBlock(pos, state.setValue(UP, true), 2);
                            return;
                        }

                        if (level.isEmptyBlock(blockPos)) {
                            if (!this.canSpread(level, pos)) {
                                return;
                            }

                            BlockState blockState2 = state;

                            for (Direction direction2 : Direction.Plane.HORIZONTAL) {
                                if (random.nextBoolean() || !isAcceptableNeighbour(level, blockPos.relative(direction2), direction2)) {
                                    blockState2 = blockState2.setValue(getPropertyForFace(direction2), false);
                                }
                            }

                            if (this.hasHorizontalConnection(blockState2)) {
                                level.setBlock(blockPos, blockState2, 2);
                            }

                            return;
                        }
                    }

                    if (pos.getY() > level.getMinBuildHeight()) {
                        BlockPos blockPos2 = pos.below();
                        BlockState blockState = level.getBlockState(blockPos2);
                        if (blockState.isAir() || blockState.is(this)) {
                            BlockState blockState3 = blockState.isAir() ? this.defaultBlockState() : blockState;
                            BlockState blockState4 = this.copyRandomFaces(state, blockState3, random);
                            if (blockState3 != blockState4 && this.hasHorizontalConnection(blockState4)) {
                                level.setBlock(blockPos2, blockState4, 2);
                            }
                        }
                    }
                }
            }
        }
    }

    private BlockState copyRandomFaces(BlockState sourceState, BlockState spreadState, RandomSource random) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (random.nextBoolean()) {
                BooleanProperty booleanProperty = getPropertyForFace(direction);
                if (sourceState.getValue(booleanProperty)) {
                    spreadState = spreadState.setValue(booleanProperty, true);
                }
            }
        }

        return spreadState;
    }

    private boolean hasHorizontalConnection(BlockState state) {
        return state.getValue(NORTH) || state.getValue(EAST) || state.getValue(SOUTH) || state.getValue(WEST);
    }

    private boolean canSpread(BlockGetter blockReader, BlockPos pos) {
        int i = 4;
        Iterable<BlockPos> iterable = BlockPos.betweenClosed(pos.getX() - 4, pos.getY() - 1, pos.getZ() - 4, pos.getX() + 4, pos.getY() + 1, pos.getZ() + 4);
        int j = 5;

        for (BlockPos blockPos : iterable) {
            if (blockReader.getBlockState(blockPos).is(this)) {
                if (--j <= 0) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        BlockState blockState = useContext.getLevel().getBlockState(useContext.getClickedPos());
        return blockState.is(this) ? this.countFaces(blockState) < PROPERTY_BY_DIRECTION.size() : super.canBeReplaced(state, useContext);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
        boolean bl = blockState.is(this);
        BlockState blockState2 = bl ? blockState : this.defaultBlockState();

        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction != Direction.DOWN) {
                BooleanProperty booleanProperty = getPropertyForFace(direction);
                boolean bl2 = bl && blockState.getValue(booleanProperty);
                if (!bl2 && this.canSupportAtFace(context.getLevel(), context.getClickedPos(), direction)) {
                    return blockState2.setValue(booleanProperty, true);
                }
            }
        }

        return bl ? blockState2 : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, NORTH, EAST, SOUTH, WEST);
    }

    /**
     * Returns the blockstate with the given rotation from the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotate) {
        switch (rotate) {
            case CLOCKWISE_180:
                return state.setValue(NORTH, state.getValue(SOUTH))
                    .setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH))
                    .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90:
                return state.setValue(NORTH, state.getValue(EAST))
                    .setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST))
                    .setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90:
                return state.setValue(NORTH, state.getValue(WEST))
                    .setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST))
                    .setValue(WEST, state.getValue(SOUTH));
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
                return state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK:
                return state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default:
                return super.mirror(state, mirror);
        }
    }

    public static BooleanProperty getPropertyForFace(Direction face) {
        return PROPERTY_BY_DIRECTION.get(face);
    }
}
