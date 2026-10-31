package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class BoneMealItem extends Item {
    public static final int GRASS_SPREAD_WIDTH = 3;
    public static final int GRASS_SPREAD_HEIGHT = 1;
    public static final int GRASS_COUNT_MULTIPLIER = 3;

    public BoneMealItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Called when this item is used when targeting a Block
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos blockPos = context.getClickedPos();
        BlockPos blockPos2 = blockPos.relative(context.getClickedFace());
        if (growCrop(context.getItemInHand(), level, blockPos)) {
            if (!level.isClientSide) {
                context.getPlayer().gameEvent(GameEvent.ITEM_INTERACT_FINISH);
                level.levelEvent(1505, blockPos, 15);
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            BlockState blockState = level.getBlockState(blockPos);
            boolean bl = blockState.isFaceSturdy(level, blockPos, context.getClickedFace());
            if (bl && growWaterPlant(context.getItemInHand(), level, blockPos2, context.getClickedFace())) {
                if (!level.isClientSide) {
                    context.getPlayer().gameEvent(GameEvent.ITEM_INTERACT_FINISH);
                    level.levelEvent(1505, blockPos2, 15);
                }

                return InteractionResult.sidedSuccess(level.isClientSide);
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    public static boolean growCrop(ItemStack stack, Level level, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.getBlock() instanceof BonemealableBlock bonemealableBlock && bonemealableBlock.isValidBonemealTarget(level, pos, blockState)) {
            if (level instanceof ServerLevel) {
                if (bonemealableBlock.isBonemealSuccess(level, level.random, pos, blockState)) {
                    bonemealableBlock.performBonemeal((ServerLevel)level, level.random, pos, blockState);
                }

                stack.shrink(1);
            }

            return true;
        } else {
            return false;
        }
    }

    public static boolean growWaterPlant(ItemStack stack, Level level, BlockPos pos, @Nullable Direction clickedSide) {
        if (level.getBlockState(pos).is(Blocks.WATER) && level.getFluidState(pos).getAmount() == 8) {
            if (!(level instanceof ServerLevel)) {
                return true;
            }

            RandomSource randomSource = level.getRandom();

            label78:
            for (int i = 0; i < 128; i++) {
                BlockPos blockPos = pos;
                BlockState blockState = Blocks.SEAGRASS.defaultBlockState();

                for (int j = 0; j < i / 16; j++) {
                    blockPos = blockPos.offset(
                        randomSource.nextInt(3) - 1, (randomSource.nextInt(3) - 1) * randomSource.nextInt(3) / 2, randomSource.nextInt(3) - 1
                    );
                    if (level.getBlockState(blockPos).isCollisionShapeFullBlock(level, blockPos)) {
                        continue label78;
                    }
                }

                Holder<Biome> holder = level.getBiome(blockPos);
                if (holder.is(BiomeTags.PRODUCES_CORALS_FROM_BONEMEAL)) {
                    if (i == 0 && clickedSide != null && clickedSide.getAxis().isHorizontal()) {
                        blockState = BuiltInRegistries.BLOCK
                            .getRandomElementOf(BlockTags.WALL_CORALS, level.random)
                            .map(holderx -> ((Block)holderx.value()).defaultBlockState())
                            .orElse(blockState);
                        if (blockState.hasProperty(BaseCoralWallFanBlock.FACING)) {
                            blockState = blockState.setValue(BaseCoralWallFanBlock.FACING, clickedSide);
                        }
                    } else if (randomSource.nextInt(4) == 0) {
                        blockState = BuiltInRegistries.BLOCK
                            .getRandomElementOf(BlockTags.UNDERWATER_BONEMEALS, level.random)
                            .map(holderx -> ((Block)holderx.value()).defaultBlockState())
                            .orElse(blockState);
                    }
                }

                if (blockState.is(BlockTags.WALL_CORALS, blockStateBase -> blockStateBase.hasProperty(BaseCoralWallFanBlock.FACING))) {
                    for (int k = 0; !blockState.canSurvive(level, blockPos) && k < 4; k++) {
                        blockState = blockState.setValue(BaseCoralWallFanBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(randomSource));
                    }
                }

                if (blockState.canSurvive(level, blockPos)) {
                    BlockState blockState2 = level.getBlockState(blockPos);
                    if (blockState2.is(Blocks.WATER) && level.getFluidState(blockPos).getAmount() == 8) {
                        level.setBlock(blockPos, blockState, 3);
                    } else if (blockState2.is(Blocks.SEAGRASS) && randomSource.nextInt(10) == 0) {
                        ((BonemealableBlock)Blocks.SEAGRASS).performBonemeal((ServerLevel)level, randomSource, blockPos, blockState2);
                    }
                }
            }

            stack.shrink(1);
            return true;
        } else {
            return false;
        }
    }

    public static void addGrowthParticles(LevelAccessor level, BlockPos pos, int data) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.getBlock() instanceof BonemealableBlock bonemealableBlock) {
            BlockPos blockPos = bonemealableBlock.getParticlePos(pos);
            switch (bonemealableBlock.getType()) {
                case NEIGHBOR_SPREADER:
                    ParticleUtils.spawnParticles(level, blockPos, data * 3, 3.0, 1.0, false, ParticleTypes.HAPPY_VILLAGER);
                    break;
                case GROWER:
                    ParticleUtils.spawnParticleInBlock(level, blockPos, data, ParticleTypes.HAPPY_VILLAGER);
            }
        } else if (blockState.is(Blocks.WATER)) {
            ParticleUtils.spawnParticles(level, pos, data * 3, 3.0, 1.0, false, ParticleTypes.HAPPY_VILLAGER);
        }
    }
}
