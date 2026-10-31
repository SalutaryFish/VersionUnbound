package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BubbleColumnBlock extends Block implements BucketPickup {
    public static final MapCodec<BubbleColumnBlock> CODEC = simpleCodec(BubbleColumnBlock::new);
    public static final BooleanProperty DRAG_DOWN = BlockStateProperties.DRAG;
    private static final int CHECK_PERIOD = 5;

    @Override
    public MapCodec<BubbleColumnBlock> codec() {
        return CODEC;
    }

    public BubbleColumnBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(DRAG_DOWN, true));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        BlockState blockState = level.getBlockState(pos.above());
        if (blockState.isAir()) {
            entity.onAboveBubbleCol(state.getValue(DRAG_DOWN));
            if (!level.isClientSide) {
                ServerLevel serverLevel = (ServerLevel)level;

                for (int i = 0; i < 2; i++) {
                    serverLevel.sendParticles(
                        ParticleTypes.SPLASH,
                        pos.getX() + level.random.nextDouble(),
                        pos.getY() + 1,
                        pos.getZ() + level.random.nextDouble(),
                        1,
                        0.0,
                        0.0,
                        0.0,
                        1.0
                    );
                    serverLevel.sendParticles(
                        ParticleTypes.BUBBLE,
                        pos.getX() + level.random.nextDouble(),
                        pos.getY() + 1,
                        pos.getZ() + level.random.nextDouble(),
                        1,
                        0.0,
                        0.01,
                        0.0,
                        0.2
                    );
                }
            }
        } else {
            entity.onInsideBubbleColumn(state.getValue(DRAG_DOWN));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        updateColumn(level, pos, state, level.getBlockState(pos.below()));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    public static void updateColumn(LevelAccessor level, BlockPos pos, BlockState state) {
        updateColumn(level, pos, level.getBlockState(pos), state);
    }

    public static void updateColumn(LevelAccessor level, BlockPos pos, BlockState fluid, BlockState state) {
        if (canExistIn(fluid)) {
            BlockState blockState = getColumnState(state);
            level.setBlock(pos, blockState, 2);
            BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.UP);

            while (canExistIn(level.getBlockState(mutableBlockPos))) {
                if (!level.setBlock(mutableBlockPos, blockState, 2)) {
                    return;
                }

                mutableBlockPos.move(Direction.UP);
            }
        }
    }

    private static boolean canExistIn(BlockState blockState) {
        return blockState.is(Blocks.BUBBLE_COLUMN)
            || blockState.is(Blocks.WATER) && blockState.getFluidState().getAmount() >= 8 && blockState.getFluidState().isSource();
    }

    private static BlockState getColumnState(BlockState blockState) {
        if (blockState.is(Blocks.BUBBLE_COLUMN)) {
            return blockState;
        } else if (blockState.is(Blocks.SOUL_SAND)) {
            return Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(DRAG_DOWN, false);
        } else {
            return blockState.is(Blocks.MAGMA_BLOCK) ? Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(DRAG_DOWN, true) : Blocks.WATER.defaultBlockState();
        }
    }

    /**
     * Called periodically clientside on blocks near the player to show effects (like furnace fire particles).
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double d = pos.getX();
        double e = pos.getY();
        double f = pos.getZ();
        if (state.getValue(DRAG_DOWN)) {
            level.addAlwaysVisibleParticle(ParticleTypes.CURRENT_DOWN, d + 0.5, e + 0.8, f, 0.0, 0.0, 0.0);
            if (random.nextInt(200) == 0) {
                level.playLocalSound(
                    d,
                    e,
                    f,
                    SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,
                    SoundSource.BLOCKS,
                    0.2F + random.nextFloat() * 0.2F,
                    0.9F + random.nextFloat() * 0.15F,
                    false
                );
            }
        } else {
            level.addAlwaysVisibleParticle(ParticleTypes.BUBBLE_COLUMN_UP, d + 0.5, e, f + 0.5, 0.0, 0.04, 0.0);
            level.addAlwaysVisibleParticle(
                ParticleTypes.BUBBLE_COLUMN_UP, d + random.nextFloat(), e + random.nextFloat(), f + random.nextFloat(), 0.0, 0.04, 0.0
            );
            if (random.nextInt(200) == 0) {
                level.playLocalSound(
                    d,
                    e,
                    f,
                    SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                    SoundSource.BLOCKS,
                    0.2F + random.nextFloat() * 0.2F,
                    0.9F + random.nextFloat() * 0.15F,
                    false
                );
            }
        }
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (!state.canSurvive(level, currentPos)
            || facing == Direction.DOWN
            || facing == Direction.UP && !facingState.is(Blocks.BUBBLE_COLUMN) && canExistIn(facingState)) {
            level.scheduleTick(currentPos, this, 5);
        }

        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos.below());
        return blockState.is(Blocks.BUBBLE_COLUMN) || blockState.is(Blocks.MAGMA_BLOCK) || blockState.is(Blocks.SOUL_SAND);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * The type of render function called. MODEL for mixed tesr and static model, MODELBLOCK_ANIMATED for TESR-only, LIQUID for vanilla liquids, INVISIBLE to skip all rendering
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DRAG_DOWN);
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        return new ItemStack(Items.WATER_BUCKET);
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Fluids.WATER.getPickupSound();
    }
}
