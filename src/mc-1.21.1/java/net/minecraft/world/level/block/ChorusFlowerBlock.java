package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ChorusFlowerBlock extends Block {
    public static final MapCodec<ChorusFlowerBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("plant").forGetter(chorusFlowerBlock -> chorusFlowerBlock.plant), propertiesCodec()
            )
            .apply(instance, ChorusFlowerBlock::new)
    );
    public static final int DEAD_AGE = 5;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_5;
    protected static final VoxelShape BLOCK_SUPPORT_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0);
    private final Block plant;

    @Override
    public MapCodec<ChorusFlowerBlock> codec() {
        return CODEC;
    }

    protected ChorusFlowerBlock(Block plant, BlockBehaviour.Properties properties) {
        super(properties);
        this.plant = plant;
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    /**
     * Returns whether this block is of a type that needs random ticking. Called for ref-counting purposes by {@code ExtendedBlockStorage} in order to broadly cull a chunk from the random chunk update list for efficiency's sake.
     */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < 5;
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return BLOCK_SUPPORT_SHAPE;
    }

    /**
     * Performs a random tick on a block.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos blockPos = pos.above();
        if (level.isEmptyBlock(blockPos) && blockPos.getY() < level.getMaxBuildHeight()) {
            int i = state.getValue(AGE);
            if (i < 5) {
                boolean bl = false;
                boolean bl2 = false;
                BlockState blockState = level.getBlockState(pos.below());
                if (blockState.is(Blocks.END_STONE)) {
                    bl = true;
                } else if (blockState.is(this.plant)) {
                    int j = 1;

                    for (int k = 0; k < 4; k++) {
                        BlockState blockState2 = level.getBlockState(pos.below(j + 1));
                        if (!blockState2.is(this.plant)) {
                            if (blockState2.is(Blocks.END_STONE)) {
                                bl2 = true;
                            }
                            break;
                        }

                        j++;
                    }

                    if (j < 2 || j <= random.nextInt(bl2 ? 5 : 4)) {
                        bl = true;
                    }
                } else if (blockState.isAir()) {
                    bl = true;
                }

                if (bl && allNeighborsEmpty(level, blockPos, null) && level.isEmptyBlock(pos.above(2))) {
                    level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, this.plant.defaultBlockState()), 2);
                    this.placeGrownFlower(level, blockPos, i);
                } else if (i < 4) {
                    int j = random.nextInt(4);
                    if (bl2) {
                        j++;
                    }

                    boolean bl3 = false;

                    for (int l = 0; l < j; l++) {
                        Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                        BlockPos blockPos2 = pos.relative(direction);
                        if (level.isEmptyBlock(blockPos2)
                            && level.isEmptyBlock(blockPos2.below())
                            && allNeighborsEmpty(level, blockPos2, direction.getOpposite())) {
                            this.placeGrownFlower(level, blockPos2, i + 1);
                            bl3 = true;
                        }
                    }

                    if (bl3) {
                        level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, this.plant.defaultBlockState()), 2);
                    } else {
                        this.placeDeadFlower(level, pos);
                    }
                } else {
                    this.placeDeadFlower(level, pos);
                }
            }
        }
    }

    private void placeGrownFlower(Level level, BlockPos pos, int age) {
        level.setBlock(pos, this.defaultBlockState().setValue(AGE, age), 2);
        level.levelEvent(1033, pos, 0);
    }

    private void placeDeadFlower(Level level, BlockPos pos) {
        level.setBlock(pos, this.defaultBlockState().setValue(AGE, 5), 2);
        level.levelEvent(1034, pos, 0);
    }

    private static boolean allNeighborsEmpty(LevelReader level, BlockPos pos, @Nullable Direction excludingSide) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction != excludingSide && !level.isEmptyBlock(pos.relative(direction))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (facing != Direction.UP && !state.canSurvive(level, currentPos)) {
            level.scheduleTick(currentPos, this, 1);
        }

        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos.below());
        if (!blockState.is(this.plant) && !blockState.is(Blocks.END_STONE)) {
            if (!blockState.isAir()) {
                return false;
            }

            boolean bl = false;

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockState blockState2 = level.getBlockState(pos.relative(direction));
                if (blockState2.is(this.plant)) {
                    if (bl) {
                        return false;
                    }

                    bl = true;
                } else if (!blockState2.isAir()) {
                    return false;
                }
            }

            return bl;
        } else {
            return true;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    public static void generatePlant(LevelAccessor level, BlockPos pos, RandomSource random, int maxHorizontalDistance) {
        level.setBlock(pos, ChorusPlantBlock.getStateWithConnections(level, pos, Blocks.CHORUS_PLANT.defaultBlockState()), 2);
        growTreeRecursive(level, pos, random, pos, maxHorizontalDistance, 0);
    }

    private static void growTreeRecursive(
        LevelAccessor level, BlockPos branchPos, RandomSource random, BlockPos originalBranchPos, int maxHorizontalDistance, int iterations
    ) {
        Block block = Blocks.CHORUS_PLANT;
        int i = random.nextInt(4) + 1;
        if (iterations == 0) {
            i++;
        }

        for (int j = 0; j < i; j++) {
            BlockPos blockPos = branchPos.above(j + 1);
            if (!allNeighborsEmpty(level, blockPos, null)) {
                return;
            }

            level.setBlock(blockPos, ChorusPlantBlock.getStateWithConnections(level, blockPos, block.defaultBlockState()), 2);
            level.setBlock(blockPos.below(), ChorusPlantBlock.getStateWithConnections(level, blockPos.below(), block.defaultBlockState()), 2);
        }

        boolean bl = false;
        if (iterations < 4) {
            int k = random.nextInt(4);
            if (iterations == 0) {
                k++;
            }

            for (int l = 0; l < k; l++) {
                Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                BlockPos blockPos2 = branchPos.above(i).relative(direction);
                if (Math.abs(blockPos2.getX() - originalBranchPos.getX()) < maxHorizontalDistance
                    && Math.abs(blockPos2.getZ() - originalBranchPos.getZ()) < maxHorizontalDistance
                    && level.isEmptyBlock(blockPos2)
                    && level.isEmptyBlock(blockPos2.below())
                    && allNeighborsEmpty(level, blockPos2, direction.getOpposite())) {
                    bl = true;
                    level.setBlock(blockPos2, ChorusPlantBlock.getStateWithConnections(level, blockPos2, block.defaultBlockState()), 2);
                    level.setBlock(
                        blockPos2.relative(direction.getOpposite()),
                        ChorusPlantBlock.getStateWithConnections(level, blockPos2.relative(direction.getOpposite()), block.defaultBlockState()),
                        2
                    );
                    growTreeRecursive(level, blockPos2, random, originalBranchPos, maxHorizontalDistance, iterations + 1);
                }
            }
        }

        if (!bl) {
            level.setBlock(branchPos.above(i), Blocks.CHORUS_FLOWER.defaultBlockState().setValue(AGE, 5), 2);
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos blockPos = hit.getBlockPos();
        if (!level.isClientSide && projectile.mayInteract(level, blockPos) && projectile.mayBreak(level)) {
            level.destroyBlock(blockPos, true, projectile);
        }
    }
}
