package net.minecraft.world.level.block;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StemBlock extends BushBlock implements BonemealableBlock {
    public static final MapCodec<StemBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                ResourceKey.codec(Registries.BLOCK).fieldOf("fruit").forGetter(stemBlock -> stemBlock.fruit),
                ResourceKey.codec(Registries.BLOCK).fieldOf("attached_stem").forGetter(stemBlock -> stemBlock.attachedStem),
                ResourceKey.codec(Registries.ITEM).fieldOf("seed").forGetter(stemBlock -> stemBlock.seed),
                propertiesCodec()
            )
            .apply(instance, StemBlock::new)
    );
    public static final int MAX_AGE = 7;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;
    protected static final float AABB_OFFSET = 1.0F;
    protected static final VoxelShape[] SHAPE_BY_AGE = new VoxelShape[]{
        Block.box(7.0, 0.0, 7.0, 9.0, 2.0, 9.0),
        Block.box(7.0, 0.0, 7.0, 9.0, 4.0, 9.0),
        Block.box(7.0, 0.0, 7.0, 9.0, 6.0, 9.0),
        Block.box(7.0, 0.0, 7.0, 9.0, 8.0, 9.0),
        Block.box(7.0, 0.0, 7.0, 9.0, 10.0, 9.0),
        Block.box(7.0, 0.0, 7.0, 9.0, 12.0, 9.0),
        Block.box(7.0, 0.0, 7.0, 9.0, 14.0, 9.0),
        Block.box(7.0, 0.0, 7.0, 9.0, 16.0, 9.0)
    };
    private final ResourceKey<Block> fruit;
    private final ResourceKey<Block> attachedStem;
    private final ResourceKey<Item> seed;

    @Override
    public MapCodec<StemBlock> codec() {
        return CODEC;
    }

    protected StemBlock(ResourceKey<Block> fruit, ResourceKey<Block> attachedStem, ResourceKey<Item> seed, BlockBehaviour.Properties properties) {
        super(properties);
        this.fruit = fruit;
        this.attachedStem = attachedStem;
        this.seed = seed;
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE_BY_AGE[state.getValue(AGE)];
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    /**
     * Performs a random tick on a block.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) >= 9) {
            float f = CropBlock.getGrowthSpeed(this, level, pos);
            if (random.nextInt((int)(25.0F / f) + 1) == 0) {
                int i = state.getValue(AGE);
                if (i < 7) {
                    state = state.setValue(AGE, i + 1);
                    level.setBlock(pos, state, 2);
                } else {
                    Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                    BlockPos blockPos = pos.relative(direction);
                    BlockState blockState = level.getBlockState(blockPos.below());
                    if (level.getBlockState(blockPos).isAir() && (blockState.is(Blocks.FARMLAND) || blockState.is(BlockTags.DIRT))) {
                        Registry<Block> registry = level.registryAccess().registryOrThrow(Registries.BLOCK);
                        Optional<Block> optional = registry.getOptional(this.fruit);
                        Optional<Block> optional2 = registry.getOptional(this.attachedStem);
                        if (optional.isPresent() && optional2.isPresent()) {
                            level.setBlockAndUpdate(blockPos, optional.get().defaultBlockState());
                            level.setBlockAndUpdate(pos, optional2.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, direction));
                        }
                    }
                }
            }
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(DataFixUtils.orElse(level.registryAccess().registryOrThrow(Registries.ITEM).getOptional(this.seed), this));
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(AGE) != 7;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        int i = Math.min(7, state.getValue(AGE) + Mth.nextInt(level.random, 2, 5));
        BlockState blockState = state.setValue(AGE, i);
        level.setBlock(pos, blockState, 2);
        if (i == 7) {
            blockState.randomTick(level, pos, level.random);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }
}
