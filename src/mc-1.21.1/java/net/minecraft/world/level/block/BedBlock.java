package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.ArrayUtils;

public class BedBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<BedBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(DyeColor.CODEC.fieldOf("color").forGetter(BedBlock::getColor), propertiesCodec()).apply(instance, BedBlock::new)
    );
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty OCCUPIED = BlockStateProperties.OCCUPIED;
    protected static final int HEIGHT = 9;
    protected static final VoxelShape BASE = Block.box(0.0, 3.0, 0.0, 16.0, 9.0, 16.0);
    private static final int LEG_WIDTH = 3;
    protected static final VoxelShape LEG_NORTH_WEST = Block.box(0.0, 0.0, 0.0, 3.0, 3.0, 3.0);
    protected static final VoxelShape LEG_SOUTH_WEST = Block.box(0.0, 0.0, 13.0, 3.0, 3.0, 16.0);
    protected static final VoxelShape LEG_NORTH_EAST = Block.box(13.0, 0.0, 0.0, 16.0, 3.0, 3.0);
    protected static final VoxelShape LEG_SOUTH_EAST = Block.box(13.0, 0.0, 13.0, 16.0, 3.0, 16.0);
    protected static final VoxelShape NORTH_SHAPE = Shapes.or(BASE, LEG_NORTH_WEST, LEG_NORTH_EAST);
    protected static final VoxelShape SOUTH_SHAPE = Shapes.or(BASE, LEG_SOUTH_WEST, LEG_SOUTH_EAST);
    protected static final VoxelShape WEST_SHAPE = Shapes.or(BASE, LEG_NORTH_WEST, LEG_SOUTH_WEST);
    protected static final VoxelShape EAST_SHAPE = Shapes.or(BASE, LEG_NORTH_EAST, LEG_SOUTH_EAST);
    private final DyeColor color;

    @Override
    public MapCodec<BedBlock> codec() {
        return CODEC;
    }

    public BedBlock(DyeColor color, BlockBehaviour.Properties properties) {
        super(properties);
        this.color = color;
        this.registerDefaultState(this.stateDefinition.any().setValue(PART, BedPart.FOOT).setValue(OCCUPIED, false));
    }

    @Nullable
    public static Direction getBedOrientation(BlockGetter level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.getBlock() instanceof BedBlock ? blockState.getValue(FACING) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.CONSUME;
        }

        if (state.getValue(PART) != BedPart.HEAD) {
            pos = pos.relative(state.getValue(FACING));
            state = level.getBlockState(pos);
            if (!state.is(this)) {
                return InteractionResult.CONSUME;
            }
        }

        if (!canSetSpawn(level)) {
            level.removeBlock(pos, false);
            BlockPos blockPos = pos.relative(state.getValue(FACING).getOpposite());
            if (level.getBlockState(blockPos).is(this)) {
                level.removeBlock(blockPos, false);
            }

            Vec3 vec3 = pos.getCenter();
            level.explode(null, level.damageSources().badRespawnPointExplosion(vec3), null, vec3, 5.0F, true, Level.ExplosionInteraction.BLOCK);
            return InteractionResult.SUCCESS;
        } else if (state.getValue(OCCUPIED)) {
            if (!this.kickVillagerOutOfBed(level, pos)) {
                player.displayClientMessage(Component.translatable("block.minecraft.bed.occupied"), true);
            }

            return InteractionResult.SUCCESS;
        } else {
            player.startSleepInBed(pos).ifLeft(bedSleepingProblem -> {
                if (bedSleepingProblem.getMessage() != null) {
                    player.displayClientMessage(bedSleepingProblem.getMessage(), true);
                }
            });
            return InteractionResult.SUCCESS;
        }
    }

    public static boolean canSetSpawn(Level level) {
        return level.dimensionType().bedWorks();
    }

    private boolean kickVillagerOutOfBed(Level level, BlockPos pos) {
        List<Villager> list = level.getEntitiesOfClass(Villager.class, new AABB(pos), LivingEntity::isSleeping);
        if (list.isEmpty()) {
            return false;
        }

        list.get(0).stopSleeping();
        return true;
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        super.fallOn(level, state, pos, entity, fallDistance * 0.5F);
    }

    /**
     * Called when an Entity lands on this Block.
     * This method is responsible for doing any modification on the motion of the entity that should result from the landing.
     */
    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(level, entity);
        } else {
            this.bounceUp(entity);
        }
    }

    private void bounceUp(Entity entity) {
        Vec3 vec3 = entity.getDeltaMovement();
        if (vec3.y < 0.0) {
            double d = entity instanceof LivingEntity ? 1.0 : 0.8;
            entity.setDeltaMovement(vec3.x, -vec3.y * 0.66F * d, vec3.z);
        }
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (facing == getNeighbourDirection(state.getValue(PART), state.getValue(FACING))) {
            return facingState.is(this) && facingState.getValue(PART) != state.getValue(PART)
                ? state.setValue(OCCUPIED, facingState.getValue(OCCUPIED))
                : Blocks.AIR.defaultBlockState();
        } else {
            return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
        }
    }

    /**
     * Given a bed part and the direction it's facing, find the direction to move to get the other bed part
     */
    private static Direction getNeighbourDirection(BedPart part, Direction direction) {
        return part == BedPart.FOOT ? direction : direction.getOpposite();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BedPart bedPart = state.getValue(PART);
            if (bedPart == BedPart.FOOT) {
                BlockPos blockPos = pos.relative(getNeighbourDirection(bedPart, state.getValue(FACING)));
                BlockState blockState = level.getBlockState(blockPos);
                if (blockState.is(this) && blockState.getValue(PART) == BedPart.HEAD) {
                    level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, blockPos, Block.getId(blockState));
                }
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getHorizontalDirection();
        BlockPos blockPos = context.getClickedPos();
        BlockPos blockPos2 = blockPos.relative(direction);
        Level level = context.getLevel();
        return level.getBlockState(blockPos2).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(blockPos2)
            ? this.defaultBlockState().setValue(FACING, direction)
            : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction direction = getConnectedDirection(state).getOpposite();
        switch (direction) {
            case NORTH:
                return NORTH_SHAPE;
            case SOUTH:
                return SOUTH_SHAPE;
            case WEST:
                return WEST_SHAPE;
            default:
                return EAST_SHAPE;
        }
    }

    public static Direction getConnectedDirection(BlockState state) {
        Direction direction = state.getValue(FACING);
        return state.getValue(PART) == BedPart.HEAD ? direction.getOpposite() : direction;
    }

    public static DoubleBlockCombiner.BlockType getBlockType(BlockState state) {
        BedPart bedPart = state.getValue(PART);
        return bedPart == BedPart.HEAD ? DoubleBlockCombiner.BlockType.FIRST : DoubleBlockCombiner.BlockType.SECOND;
    }

    private static boolean isBunkBed(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof BedBlock;
    }

    public static Optional<Vec3> findStandUpPosition(EntityType<?> entityType, CollisionGetter collisionGetter, BlockPos pos, Direction direction, float yRot) {
        Direction direction2 = direction.getClockWise();
        Direction direction3 = direction2.isFacingAngle(yRot) ? direction2.getOpposite() : direction2;
        if (isBunkBed(collisionGetter, pos)) {
            return findBunkBedStandUpPosition(entityType, collisionGetter, pos, direction, direction3);
        }

        int[][] is = bedStandUpOffsets(direction, direction3);
        Optional<Vec3> optional = findStandUpPositionAtOffset(entityType, collisionGetter, pos, is, true);
        return optional.isPresent() ? optional : findStandUpPositionAtOffset(entityType, collisionGetter, pos, is, false);
    }

    private static Optional<Vec3> findBunkBedStandUpPosition(
        EntityType<?> entityType, CollisionGetter collisionGetter, BlockPos pos, Direction stateFacing, Direction entityFacing
    ) {
        int[][] is = bedSurroundStandUpOffsets(stateFacing, entityFacing);
        Optional<Vec3> optional = findStandUpPositionAtOffset(entityType, collisionGetter, pos, is, true);
        if (optional.isPresent()) {
            return optional;
        }

        BlockPos blockPos = pos.below();
        Optional<Vec3> optional2 = findStandUpPositionAtOffset(entityType, collisionGetter, blockPos, is, true);
        if (optional2.isPresent()) {
            return optional2;
        }

        int[][] js = bedAboveStandUpOffsets(stateFacing);
        Optional<Vec3> optional3 = findStandUpPositionAtOffset(entityType, collisionGetter, pos, js, true);
        if (optional3.isPresent()) {
            return optional3;
        }

        Optional<Vec3> optional4 = findStandUpPositionAtOffset(entityType, collisionGetter, pos, is, false);
        if (optional4.isPresent()) {
            return optional4;
        }

        Optional<Vec3> optional5 = findStandUpPositionAtOffset(entityType, collisionGetter, blockPos, is, false);
        return optional5.isPresent() ? optional5 : findStandUpPositionAtOffset(entityType, collisionGetter, pos, js, false);
    }

    private static Optional<Vec3> findStandUpPositionAtOffset(
        EntityType<?> entityType, CollisionGetter collisionGetter, BlockPos pos, int[][] offsets, boolean simulate
    ) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int[] is : offsets) {
            mutableBlockPos.set(pos.getX() + is[0], pos.getY(), pos.getZ() + is[1]);
            Vec3 vec3 = DismountHelper.findSafeDismountLocation(entityType, collisionGetter, mutableBlockPos, simulate);
            if (vec3 != null) {
                return Optional.of(vec3);
            }
        }

        return Optional.empty();
    }

    /**
     * The type of render function called. MODEL for mixed tesr and static model, MODELBLOCK_ANIMATED for TESR-only, LIQUID for vanilla liquids, INVISIBLE to skip all rendering
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OCCUPIED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BedBlockEntity(pos, state, this.color);
    }

    /**
     * Called by BlockItem after this block has been placed.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BlockPos blockPos = pos.relative(state.getValue(FACING));
            level.setBlock(blockPos, state.setValue(PART, BedPart.HEAD), 3);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
        }
    }

    public DyeColor getColor() {
        return this.color;
    }

    /**
     * Return a random long to be passed to {@link net.minecraft.client.resources.model.BakedModel#getQuads}, used for random model rotations
     */
    @Override
    protected long getSeed(BlockState state, BlockPos pos) {
        BlockPos blockPos = pos.relative(state.getValue(FACING), state.getValue(PART) == BedPart.HEAD ? 0 : 1);
        return Mth.getSeed(blockPos.getX(), pos.getY(), blockPos.getZ());
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    private static int[][] bedStandUpOffsets(Direction firstDir, Direction secondDir) {
        return ArrayUtils.addAll(bedSurroundStandUpOffsets(firstDir, secondDir), bedAboveStandUpOffsets(firstDir));
    }

    private static int[][] bedSurroundStandUpOffsets(Direction firstDir, Direction secondDir) {
        return new int[][]{
            {secondDir.getStepX(), secondDir.getStepZ()},
            {secondDir.getStepX() - firstDir.getStepX(), secondDir.getStepZ() - firstDir.getStepZ()},
            {secondDir.getStepX() - firstDir.getStepX() * 2, secondDir.getStepZ() - firstDir.getStepZ() * 2},
            {-firstDir.getStepX() * 2, -firstDir.getStepZ() * 2},
            {-secondDir.getStepX() - firstDir.getStepX() * 2, -secondDir.getStepZ() - firstDir.getStepZ() * 2},
            {-secondDir.getStepX() - firstDir.getStepX(), -secondDir.getStepZ() - firstDir.getStepZ()},
            {-secondDir.getStepX(), -secondDir.getStepZ()},
            {-secondDir.getStepX() + firstDir.getStepX(), -secondDir.getStepZ() + firstDir.getStepZ()},
            {firstDir.getStepX(), firstDir.getStepZ()},
            {secondDir.getStepX() + firstDir.getStepX(), secondDir.getStepZ() + firstDir.getStepZ()}
        };
    }

    private static int[][] bedAboveStandUpOffsets(Direction dir) {
        return new int[][]{{0, 0}, {-dir.getStepX(), -dir.getStepZ()}};
    }
}
