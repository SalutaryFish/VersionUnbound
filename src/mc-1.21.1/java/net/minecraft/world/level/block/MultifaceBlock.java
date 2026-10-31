package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class MultifaceBlock extends Block {
    private static final float AABB_OFFSET = 1.0F;
    private static final VoxelShape UP_AABB = Block.box(0.0, 15.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape DOWN_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
    private static final VoxelShape WEST_AABB = Block.box(0.0, 0.0, 0.0, 1.0, 16.0, 16.0);
    private static final VoxelShape EAST_AABB = Block.box(15.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape NORTH_AABB = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 1.0);
    private static final VoxelShape SOUTH_AABB = Block.box(0.0, 0.0, 15.0, 16.0, 16.0, 16.0);
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final Map<Direction, VoxelShape> SHAPE_BY_DIRECTION = Util.make(Maps.newEnumMap(Direction.class), map -> {
        map.put(Direction.NORTH, NORTH_AABB);
        map.put(Direction.EAST, EAST_AABB);
        map.put(Direction.SOUTH, SOUTH_AABB);
        map.put(Direction.WEST, WEST_AABB);
        map.put(Direction.UP, UP_AABB);
        map.put(Direction.DOWN, DOWN_AABB);
    });
    protected static final Direction[] DIRECTIONS = Direction.values();
    private final ImmutableMap<BlockState, VoxelShape> shapesCache;
    private final boolean canRotate;
    private final boolean canMirrorX;
    private final boolean canMirrorZ;

    public MultifaceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(getDefaultMultifaceState(this.stateDefinition));
        this.shapesCache = this.getShapeForEachState(MultifaceBlock::calculateMultifaceShape);
        this.canRotate = Direction.Plane.HORIZONTAL.stream().allMatch(this::isFaceSupported);
        this.canMirrorX = Direction.Plane.HORIZONTAL.stream().filter(Direction.Axis.X).filter(this::isFaceSupported).count() % 2L == 0L;
        this.canMirrorZ = Direction.Plane.HORIZONTAL.stream().filter(Direction.Axis.Z).filter(this::isFaceSupported).count() % 2L == 0L;
    }

    @Override
    protected abstract MapCodec<? extends MultifaceBlock> codec();

    public static Set<Direction> availableFaces(BlockState state) {
        if (!(state.getBlock() instanceof MultifaceBlock)) {
            return Set.of();
        }

        Set<Direction> set = EnumSet.noneOf(Direction.class);

        for (Direction direction : Direction.values()) {
            if (hasFace(state, direction)) {
                set.add(direction);
            }
        }

        return set;
    }

    public static Set<Direction> unpack(byte packedDirections) {
        Set<Direction> set = EnumSet.noneOf(Direction.class);

        for (Direction direction : Direction.values()) {
            if ((packedDirections & (byte)(1 << direction.ordinal())) > 0) {
                set.add(direction);
            }
        }

        return set;
    }

    public static byte pack(Collection<Direction> directions) {
        byte b = 0;

        for (Direction direction : directions) {
            b = (byte)(b | 1 << direction.ordinal());
        }

        return b;
    }

    protected boolean isFaceSupported(Direction face) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction direction : DIRECTIONS) {
            if (this.isFaceSupported(direction)) {
                builder.add(getFaceProperty(direction));
            }
        }
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!hasAnyFace(state)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            return hasFace(state, direction) && !canAttachTo(level, direction, neighborPos, neighborState)
                ? removeFace(state, getFaceProperty(direction))
                : state;
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapesCache.get(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        boolean bl = false;

        for (Direction direction : DIRECTIONS) {
            if (hasFace(state, direction)) {
                BlockPos blockPos = pos.relative(direction);
                if (!canAttachTo(level, direction, blockPos, level.getBlockState(blockPos))) {
                    return false;
                }

                bl = true;
            }
        }

        return bl;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return hasAnyVacantFace(state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        BlockState blockState = level.getBlockState(blockPos);
        return Arrays.stream(context.getNearestLookingDirections())
            .map(lookingDirection -> this.getStateForPlacement(blockState, level, blockPos, lookingDirection))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    public boolean isValidStateForPlacement(BlockGetter level, BlockState state, BlockPos pos, Direction direction) {
        if (this.isFaceSupported(direction) && (!state.is(this) || !hasFace(state, direction))) {
            BlockPos blockPos = pos.relative(direction);
            return canAttachTo(level, direction, blockPos, level.getBlockState(blockPos));
        } else {
            return false;
        }
    }

    @Nullable
    public BlockState getStateForPlacement(BlockState currentState, BlockGetter level, BlockPos pos, Direction lookingDirection) {
        if (!this.isValidStateForPlacement(level, currentState, pos, lookingDirection)) {
            return null;
        }

        BlockState blockState;
        if (currentState.is(this)) {
            blockState = currentState;
        } else if (this.isWaterloggable() && currentState.getFluidState().isSourceOfType(Fluids.WATER)) {
            blockState = this.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        } else {
            blockState = this.defaultBlockState();
        }

        return blockState.setValue(getFaceProperty(lookingDirection), true);
    }

    /**
     * Returns the blockstate with the given rotation from the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return !this.canRotate ? state : this.mapDirections(state, rotation::rotate);
    }

    /**
     * Returns the blockstate with the given mirror of the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.FRONT_BACK && !this.canMirrorX) {
            return state;
        } else {
            return mirror == Mirror.LEFT_RIGHT && !this.canMirrorZ ? state : this.mapDirections(state, mirror::mirror);
        }
    }

    private BlockState mapDirections(BlockState state, Function<Direction, Direction> directionalFunction) {
        BlockState blockState = state;

        for (Direction direction : DIRECTIONS) {
            if (this.isFaceSupported(direction)) {
                blockState = blockState.setValue(getFaceProperty(directionalFunction.apply(direction)), state.getValue(getFaceProperty(direction)));
            }
        }

        return blockState;
    }

    public static boolean hasFace(BlockState state, Direction direction) {
        BooleanProperty booleanProperty = getFaceProperty(direction);
        return state.hasProperty(booleanProperty) && state.getValue(booleanProperty);
    }

    public static boolean canAttachTo(BlockGetter level, Direction direction, BlockPos pos, BlockState state) {
        return Block.isFaceFull(state.getBlockSupportShape(level, pos), direction.getOpposite())
            || Block.isFaceFull(state.getCollisionShape(level, pos), direction.getOpposite());
    }

    private boolean isWaterloggable() {
        return this.stateDefinition.getProperties().contains(BlockStateProperties.WATERLOGGED);
    }

    private static BlockState removeFace(BlockState state, BooleanProperty faceProp) {
        BlockState blockState = state.setValue(faceProp, false);
        return hasAnyFace(blockState) ? blockState : Blocks.AIR.defaultBlockState();
    }

    public static BooleanProperty getFaceProperty(Direction direction) {
        return PROPERTY_BY_DIRECTION.get(direction);
    }

    private static BlockState getDefaultMultifaceState(StateDefinition<Block, BlockState> stateDefinition) {
        BlockState blockState = stateDefinition.any();

        for (BooleanProperty booleanProperty : PROPERTY_BY_DIRECTION.values()) {
            if (blockState.hasProperty(booleanProperty)) {
                blockState = blockState.setValue(booleanProperty, false);
            }
        }

        return blockState;
    }

    private static VoxelShape calculateMultifaceShape(BlockState state) {
        VoxelShape voxelShape = Shapes.empty();

        for (Direction direction : DIRECTIONS) {
            if (hasFace(state, direction)) {
                voxelShape = Shapes.or(voxelShape, SHAPE_BY_DIRECTION.get(direction));
            }
        }

        return voxelShape.isEmpty() ? Shapes.block() : voxelShape;
    }

    protected static boolean hasAnyFace(BlockState state) {
        return Arrays.stream(DIRECTIONS).anyMatch(direction -> hasFace(state, direction));
    }

    private static boolean hasAnyVacantFace(BlockState state) {
        return Arrays.stream(DIRECTIONS).anyMatch(direction -> !hasFace(state, direction));
    }

    public abstract MultifaceSpreader getSpreader();
}
