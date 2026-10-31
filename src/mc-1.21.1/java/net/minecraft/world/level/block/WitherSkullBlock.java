package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;

public class WitherSkullBlock extends SkullBlock {
    public static final MapCodec<WitherSkullBlock> CODEC = simpleCodec(WitherSkullBlock::new);
    @Nullable
    private static BlockPattern witherPatternFull;
    @Nullable
    private static BlockPattern witherPatternBase;

    @Override
    public MapCodec<WitherSkullBlock> codec() {
        return CODEC;
    }

    protected WitherSkullBlock(BlockBehaviour.Properties properties) {
        super(SkullBlock.Types.WITHER_SKELETON, properties);
    }

    /**
     * Called by BlockItem after this block has been placed.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        checkSpawn(level, pos);
    }

    public static void checkSpawn(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SkullBlockEntity skullBlockEntity) {
            checkSpawn(level, pos, skullBlockEntity);
        }
    }

    public static void checkSpawn(Level level, BlockPos pos, SkullBlockEntity blockEntity) {
        if (!level.isClientSide) {
            BlockState blockState = blockEntity.getBlockState();
            boolean bl = blockState.is(Blocks.WITHER_SKELETON_SKULL) || blockState.is(Blocks.WITHER_SKELETON_WALL_SKULL);
            if (bl && pos.getY() >= level.getMinBuildHeight() && level.getDifficulty() != Difficulty.PEACEFUL) {
                BlockPattern.BlockPatternMatch blockPatternMatch = getOrCreateWitherFull().find(level, pos);
                if (blockPatternMatch != null) {
                    WitherBoss witherBoss = EntityType.WITHER.create(level);
                    if (witherBoss != null) {
                        CarvedPumpkinBlock.clearPatternBlocks(level, blockPatternMatch);
                        BlockPos blockPos = blockPatternMatch.getBlock(1, 2, 0).getPos();
                        witherBoss.moveTo(
                            blockPos.getX() + 0.5,
                            blockPos.getY() + 0.55,
                            blockPos.getZ() + 0.5,
                            blockPatternMatch.getForwards().getAxis() == Direction.Axis.X ? 0.0F : 90.0F,
                            0.0F
                        );
                        witherBoss.yBodyRot = blockPatternMatch.getForwards().getAxis() == Direction.Axis.X ? 0.0F : 90.0F;
                        witherBoss.makeInvulnerable();

                        for (ServerPlayer serverPlayer : level.getEntitiesOfClass(ServerPlayer.class, witherBoss.getBoundingBox().inflate(50.0))) {
                            CriteriaTriggers.SUMMONED_ENTITY.trigger(serverPlayer, witherBoss);
                        }

                        level.addFreshEntity(witherBoss);
                        CarvedPumpkinBlock.updatePatternBlocks(level, blockPatternMatch);
                    }
                }
            }
        }
    }

    public static boolean canSpawnMob(Level level, BlockPos pos, ItemStack stack) {
        return stack.is(Items.WITHER_SKELETON_SKULL)
                && pos.getY() >= level.getMinBuildHeight() + 2
                && level.getDifficulty() != Difficulty.PEACEFUL
                && !level.isClientSide
            ? getOrCreateWitherBase().find(level, pos) != null
            : false;
    }

    private static BlockPattern getOrCreateWitherFull() {
        if (witherPatternFull == null) {
            witherPatternFull = BlockPatternBuilder.start()
                .aisle("^^^", "###", "~#~")
                .where('#', block -> block.getState().is(BlockTags.WITHER_SUMMON_BASE_BLOCKS))
                .where(
                    '^',
                    BlockInWorld.hasState(
                        BlockStatePredicate.forBlock(Blocks.WITHER_SKELETON_SKULL).or(BlockStatePredicate.forBlock(Blocks.WITHER_SKELETON_WALL_SKULL))
                    )
                )
                .where('~', block -> block.getState().isAir())
                .build();
        }

        return witherPatternFull;
    }

    private static BlockPattern getOrCreateWitherBase() {
        if (witherPatternBase == null) {
            witherPatternBase = BlockPatternBuilder.start()
                .aisle("   ", "###", "~#~")
                .where('#', block -> block.getState().is(BlockTags.WITHER_SUMMON_BASE_BLOCKS))
                .where('~', block -> block.getState().isAir())
                .build();
        }

        return witherPatternBase;
    }
}
