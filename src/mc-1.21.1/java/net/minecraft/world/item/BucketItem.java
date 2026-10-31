package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class BucketItem extends Item implements DispensibleContainerItem {
    private final Fluid content;

    public BucketItem(Fluid content, Item.Properties properties) {
        super(properties);
        this.content = content;
    }

    /**
     * Called to trigger the item's "innate" right click behavior. To handle when this item is used on a Block, see {@link #onItemUse}.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        BlockHitResult blockHitResult = getPlayerPOVHitResult(
            level, player, this.content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE
        );
        if (blockHitResult.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(itemStack);
        }

        if (blockHitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(itemStack);
        }

        BlockPos blockPos = blockHitResult.getBlockPos();
        Direction direction = blockHitResult.getDirection();
        BlockPos blockPos2 = blockPos.relative(direction);
        if (!level.mayInteract(player, blockPos) || !player.mayUseItemAt(blockPos2, direction, itemStack)) {
            return InteractionResultHolder.fail(itemStack);
        }

        if (this.content == Fluids.EMPTY) {
            BlockState blockState = level.getBlockState(blockPos);
            if (blockState.getBlock() instanceof BucketPickup bucketPickup) {
                ItemStack itemStack2 = bucketPickup.pickupBlock(player, level, blockPos, blockState);
                if (!itemStack2.isEmpty()) {
                    player.awardStat(Stats.ITEM_USED.get(this));
                    bucketPickup.getPickupSound().ifPresent(soundEvent -> player.playSound(soundEvent, 1.0F, 1.0F));
                    level.gameEvent(player, GameEvent.FLUID_PICKUP, blockPos);
                    ItemStack itemStack3 = ItemUtils.createFilledResult(itemStack, player, itemStack2);
                    if (!level.isClientSide) {
                        CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayer)player, itemStack2);
                    }

                    return InteractionResultHolder.sidedSuccess(itemStack3, level.isClientSide());
                }
            }

            return InteractionResultHolder.fail(itemStack);
        } else {
            BlockState blockState = level.getBlockState(blockPos);
            BlockPos blockPos3 = blockState.getBlock() instanceof LiquidBlockContainer && this.content == Fluids.WATER ? blockPos : blockPos2;
            if (this.emptyContents(player, level, blockPos3, blockHitResult)) {
                this.checkExtraContent(player, level, itemStack, blockPos3);
                if (player instanceof ServerPlayer) {
                    CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer)player, blockPos3, itemStack);
                }

                player.awardStat(Stats.ITEM_USED.get(this));
                ItemStack itemStack2 = ItemUtils.createFilledResult(itemStack, player, getEmptySuccessItem(itemStack, player));
                return InteractionResultHolder.sidedSuccess(itemStack2, level.isClientSide());
            } else {
                return InteractionResultHolder.fail(itemStack);
            }
        }
    }

    public static ItemStack getEmptySuccessItem(ItemStack bucketStack, Player player) {
        return !player.hasInfiniteMaterials() ? new ItemStack(Items.BUCKET) : bucketStack;
    }

    @Override
    public void checkExtraContent(@Nullable Player player, Level level, ItemStack containerStack, BlockPos pos) {
    }

    @Override
    public boolean emptyContents(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult result) {
        if (!(this.content instanceof FlowingFluid flowingFluid)) {
            return false;
        } else {
            BlockState blockState = level.getBlockState(pos);
            Block block = blockState.getBlock();
            boolean bl = blockState.canBeReplaced(this.content);
            boolean bl2 = blockState.isAir()
                || bl
                || block instanceof LiquidBlockContainer liquidBlockContainer
                    && liquidBlockContainer.canPlaceLiquid(player, level, pos, blockState, this.content);
            if (!bl2) {
                return result != null && this.emptyContents(player, level, result.getBlockPos().relative(result.getDirection()), null);
            }

            if (level.dimensionType().ultraWarm() && this.content.is(FluidTags.WATER)) {
                int i = pos.getX();
                int j = pos.getY();
                int k = pos.getZ();
                level.playSound(
                    player, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.8F
                );

                for (int l = 0; l < 8; l++) {
                    level.addParticle(ParticleTypes.LARGE_SMOKE, i + Math.random(), j + Math.random(), k + Math.random(), 0.0, 0.0, 0.0);
                }

                return true;
            } else if (block instanceof LiquidBlockContainer liquidBlockContainer && this.content == Fluids.WATER) {
                liquidBlockContainer.placeLiquid(level, pos, blockState, flowingFluid.getSource(false));
                this.playEmptySound(player, level, pos);
                return true;
            } else {
                if (!level.isClientSide && bl && !blockState.liquid()) {
                    level.destroyBlock(pos, true);
                }

                if (!level.setBlock(pos, this.content.defaultFluidState().createLegacyBlock(), 11) && !blockState.getFluidState().isSource()) {
                    return false;
                }

                this.playEmptySound(player, level, pos);
                return true;
            }
        }
    }

    protected void playEmptySound(@Nullable Player player, LevelAccessor level, BlockPos pos) {
        SoundEvent soundEvent = this.content.is(FluidTags.LAVA) ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY;
        level.playSound(player, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
    }
}
