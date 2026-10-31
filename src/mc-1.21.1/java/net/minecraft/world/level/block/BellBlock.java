package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BellBlock extends BaseEntityBlock {
    public static final MapCodec<BellBlock> CODEC = simpleCodec(BellBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<BellAttachType> ATTACHMENT = BlockStateProperties.BELL_ATTACHMENT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape NORTH_SOUTH_FLOOR_SHAPE = Block.box(0.0, 0.0, 4.0, 16.0, 16.0, 12.0);
    private static final VoxelShape EAST_WEST_FLOOR_SHAPE = Block.box(4.0, 0.0, 0.0, 12.0, 16.0, 16.0);
    private static final VoxelShape BELL_TOP_SHAPE = Block.box(5.0, 6.0, 5.0, 11.0, 13.0, 11.0);
    private static final VoxelShape BELL_BOTTOM_SHAPE = Block.box(4.0, 4.0, 4.0, 12.0, 6.0, 12.0);
    private static final VoxelShape BELL_SHAPE = Shapes.or(BELL_BOTTOM_SHAPE, BELL_TOP_SHAPE);
    private static final VoxelShape NORTH_SOUTH_BETWEEN = Shapes.or(BELL_SHAPE, Block.box(7.0, 13.0, 0.0, 9.0, 15.0, 16.0));
    private static final VoxelShape EAST_WEST_BETWEEN = Shapes.or(BELL_SHAPE, Block.box(0.0, 13.0, 7.0, 16.0, 15.0, 9.0));
    private static final VoxelShape TO_WEST = Shapes.or(BELL_SHAPE, Block.box(0.0, 13.0, 7.0, 13.0, 15.0, 9.0));
    private static final VoxelShape TO_EAST = Shapes.or(BELL_SHAPE, Block.box(3.0, 13.0, 7.0, 16.0, 15.0, 9.0));
    private static final VoxelShape TO_NORTH = Shapes.or(BELL_SHAPE, Block.box(7.0, 13.0, 0.0, 9.0, 15.0, 13.0));
    private static final VoxelShape TO_SOUTH = Shapes.or(BELL_SHAPE, Block.box(7.0, 13.0, 3.0, 9.0, 15.0, 16.0));
    private static final VoxelShape CEILING_SHAPE = Shapes.or(BELL_SHAPE, Block.box(7.0, 13.0, 7.0, 9.0, 16.0, 9.0));
    public static final int EVENT_BELL_RING = 1;

    @Override
    public MapCodec<BellBlock> codec() {
        return CODEC;
    }

    public BellBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ATTACHMENT, BellAttachType.FLOOR).setValue(POWERED, false)
        );
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        boolean bl = level.hasNeighborSignal(pos);
        if (bl != state.getValue(POWERED)) {
            if (bl) {
                this.attemptToRing(level, pos, null);
            }

            level.setBlock(pos, state.setValue(POWERED, bl), 3);
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        Entity entity = projectile.getOwner();
        Player player = entity instanceof Player ? (Player)entity : null;
        this.onHit(level, state, hit, player, true);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return this.onHit(level, state, hitResult, player, true) ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    public boolean onHit(Level level, BlockState state, BlockHitResult result, @Nullable Player player, boolean canRingBell) {
        Direction direction = result.getDirection();
        BlockPos blockPos = result.getBlockPos();
        boolean bl = !canRingBell || this.isProperHit(state, direction, result.getLocation().y - blockPos.getY());
        if (bl) {
            boolean bl2 = this.attemptToRing(player, level, blockPos, direction);
            if (bl2 && player != null) {
                player.awardStat(Stats.BELL_RING);
            }

            return true;
        } else {
            return false;
        }
    }

    /**
     * @return true if the bell can be rung from the given side and vertical position. For example, bells attached to their northern neighbor cannot be rung from the south face, since it can't swing north-south.
     */
    private boolean isProperHit(BlockState pos, Direction direction, double distanceY) {
        if (direction.getAxis() != Direction.Axis.Y && !(distanceY > 0.8124F)) {
            Direction direction2 = pos.getValue(FACING);
            BellAttachType bellAttachType = pos.getValue(ATTACHMENT);
            switch (bellAttachType) {
                case FLOOR:
                    return direction2.getAxis() == direction.getAxis();
                case SINGLE_WALL:
                case DOUBLE_WALL:
                    return direction2.getAxis() != direction.getAxis();
                case CEILING:
                    return true;
                default:
                    return false;
            }
        } else {
            return false;
        }
    }

    public boolean attemptToRing(Level level, BlockPos pos, @Nullable Direction direction) {
        return this.attemptToRing(null, level, pos, direction);
    }

    public boolean attemptToRing(@Nullable Entity entity, Level level, BlockPos pos, @Nullable Direction direction) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!level.isClientSide && blockEntity instanceof BellBlockEntity) {
            if (direction == null) {
                direction = level.getBlockState(pos).getValue(FACING);
            }

            ((BellBlockEntity)blockEntity).onHit(direction);
            level.playSound((Player)null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 1.0F);
            level.gameEvent(entity, GameEvent.BLOCK_CHANGE, pos);
            return true;
        } else {
            return false;
        }
    }

    private VoxelShape getVoxelShape(BlockState state) {
        Direction direction = state.getValue(FACING);
        BellAttachType bellAttachType = state.getValue(ATTACHMENT);
        if (bellAttachType == BellAttachType.FLOOR) {
            return direction != Direction.NORTH && direction != Direction.SOUTH ? EAST_WEST_FLOOR_SHAPE : NORTH_SOUTH_FLOOR_SHAPE;
        } else if (bellAttachType == BellAttachType.CEILING) {
            return CEILING_SHAPE;
        } else if (bellAttachType == BellAttachType.DOUBLE_WALL) {
            return direction != Direction.NORTH && direction != Direction.SOUTH ? EAST_WEST_BETWEEN : NORTH_SOUTH_BETWEEN;
        } else if (direction == Direction.NORTH) {
            return TO_NORTH;
        } else if (direction == Direction.SOUTH) {
            return TO_SOUTH;
        } else {
            return direction == Direction.EAST ? TO_EAST : TO_WEST;
        }
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getVoxelShape(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getVoxelShape(state);
    }

    /**
     * The type of render function called. MODEL for mixed tesr and static model, MODELBLOCK_ANIMATED for TESR-only, LIQUID for vanilla liquids, INVISIBLE to skip all rendering
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getClickedFace();
        BlockPos blockPos = context.getClickedPos();
        Level level = context.getLevel();
        Direction.Axis axis = direction.getAxis();
        if (axis == Direction.Axis.Y) {
            BlockState blockState = this.defaultBlockState()
                .setValue(ATTACHMENT, direction == Direction.DOWN ? BellAttachType.CEILING : BellAttachType.FLOOR)
                .setValue(FACING, context.getHorizontalDirection());
            if (blockState.canSurvive(context.getLevel(), blockPos)) {
                return blockState;
            }
        } else {
            boolean bl = axis == Direction.Axis.X
                    && level.getBlockState(blockPos.west()).isFaceSturdy(level, blockPos.west(), Direction.EAST)
                    && level.getBlockState(blockPos.east()).isFaceSturdy(level, blockPos.east(), Direction.WEST)
                || axis == Direction.Axis.Z
                    && level.getBlockState(blockPos.north()).isFaceSturdy(level, blockPos.north(), Direction.SOUTH)
                    && level.getBlockState(blockPos.south()).isFaceSturdy(level, blockPos.south(), Direction.NORTH);
            BlockState blockState = this.defaultBlockState()
                .setValue(FACING, direction.getOpposite())
                .setValue(ATTACHMENT, bl ? BellAttachType.DOUBLE_WALL : BellAttachType.SINGLE_WALL);
            if (blockState.canSurvive(context.getLevel(), context.getClickedPos())) {
                return blockState;
            }

            boolean bl2 = level.getBlockState(blockPos.below()).isFaceSturdy(level, blockPos.below(), Direction.UP);
            blockState = blockState.setValue(ATTACHMENT, bl2 ? BellAttachType.FLOOR : BellAttachType.CEILING);
            if (blockState.canSurvive(context.getLevel(), context.getClickedPos())) {
                return blockState;
            }
        }

        return null;
    }

    @Override
    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (explosion.canTriggerBlocks()) {
            this.attemptToRing(level, pos, null);
        }

        super.onExplosionHit(state, level, pos, explosion, dropConsumer);
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        BellAttachType bellAttachType = state.getValue(ATTACHMENT);
        Direction direction = getConnectedDirection(state).getOpposite();
        if (direction == facing && !state.canSurvive(level, currentPos) && bellAttachType != BellAttachType.DOUBLE_WALL) {
            return Blocks.AIR.defaultBlockState();
        }

        if (facing.getAxis() == state.getValue(FACING).getAxis()) {
            if (bellAttachType == BellAttachType.DOUBLE_WALL && !facingState.isFaceSturdy(level, facingPos, facing)) {
                return state.setValue(ATTACHMENT, BellAttachType.SINGLE_WALL).setValue(FACING, facing.getOpposite());
            }

            if (bellAttachType == BellAttachType.SINGLE_WALL
                && direction.getOpposite() == facing
                && facingState.isFaceSturdy(level, facingPos, state.getValue(FACING))) {
                return state.setValue(ATTACHMENT, BellAttachType.DOUBLE_WALL);
            }
        }

        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction direction = getConnectedDirection(state).getOpposite();
        return direction == Direction.UP
            ? Block.canSupportCenter(level, pos.above(), Direction.DOWN)
            : FaceAttachedHorizontalDirectionalBlock.canAttach(level, pos, direction);
    }

    private static Direction getConnectedDirection(BlockState state) {
        switch ((BellAttachType)state.getValue(ATTACHMENT)) {
            case FLOOR:
                return Direction.UP;
            case CEILING:
                return Direction.DOWN;
            default:
                return state.getValue(FACING).getOpposite();
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ATTACHMENT, POWERED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BellBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, BlockEntityType.BELL, level.isClientSide ? BellBlockEntity::clientTick : BellBlockEntity::serverTick);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    /**
     * Returns the blockstate with the given rotation from the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /**
     * Returns the blockstate with the given mirror of the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
