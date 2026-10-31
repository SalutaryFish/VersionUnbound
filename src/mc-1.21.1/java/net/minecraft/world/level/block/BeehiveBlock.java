package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BeehiveBlock extends BaseEntityBlock {
    public static final MapCodec<BeehiveBlock> CODEC = simpleCodec(BeehiveBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty HONEY_LEVEL = BlockStateProperties.LEVEL_HONEY;
    public static final int MAX_HONEY_LEVELS = 5;
    private static final int SHEARED_HONEYCOMB_COUNT = 3;

    @Override
    public MapCodec<BeehiveBlock> codec() {
        return CODEC;
    }

    public BeehiveBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HONEY_LEVEL, 0).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /**
     * Returns the analog signal this block emits. This is the signal a comparator can read from it.
     *
     */
    @Override
    protected int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos pos) {
        return blockState.getValue(HONEY_LEVEL);
    }

    /**
     * Called after a player has successfully harvested this block. This method will only be called if the player has used the correct tool and drops should be spawned.
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity te, ItemStack stack) {
        super.playerDestroy(level, player, pos, state, te, stack);
        if (!level.isClientSide && te instanceof BeehiveBlockEntity beehiveBlockEntity) {
            if (!EnchantmentHelper.hasTag(stack, EnchantmentTags.PREVENTS_BEE_SPAWNS_WHEN_MINING)) {
                beehiveBlockEntity.emptyAllLivingFromHive(player, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
                level.updateNeighbourForOutputSignal(pos, this);
                this.angerNearbyBees(level, pos);
            }

            CriteriaTriggers.BEE_NEST_DESTROYED.trigger((ServerPlayer)player, state, stack, beehiveBlockEntity.getOccupantCount());
        }
    }

    private void angerNearbyBees(Level level, BlockPos pos) {
        AABB aABB = new AABB(pos).inflate(8.0, 6.0, 8.0);
        List<Bee> list = level.getEntitiesOfClass(Bee.class, aABB);
        if (!list.isEmpty()) {
            List<Player> list2 = level.getEntitiesOfClass(Player.class, aABB);
            if (list2.isEmpty()) {
                return;
            }

            for (Bee bee : list) {
                if (bee.getTarget() == null) {
                    Player player = Util.getRandom(list2, level.random);
                    bee.setTarget(player);
                }
            }
        }
    }

    public static void dropHoneycomb(Level level, BlockPos pos) {
        popResource(level, pos, new ItemStack(Items.HONEYCOMB, 3));
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        int i = state.getValue(HONEY_LEVEL);
        boolean bl = false;
        if (i >= 5) {
            Item item = stack.getItem();
            if (stack.is(Items.SHEARS)) {
                level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
                dropHoneycomb(level, pos);
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                bl = true;
                level.gameEvent(player, GameEvent.SHEAR, pos);
            } else if (stack.is(Items.GLASS_BOTTLE)) {
                stack.shrink(1);
                level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (stack.isEmpty()) {
                    player.setItemInHand(hand, new ItemStack(Items.HONEY_BOTTLE));
                } else if (!player.getInventory().add(new ItemStack(Items.HONEY_BOTTLE))) {
                    player.drop(new ItemStack(Items.HONEY_BOTTLE), false);
                }

                bl = true;
                level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
            }

            if (!level.isClientSide() && bl) {
                player.awardStat(Stats.ITEM_USED.get(item));
            }
        }

        if (bl) {
            if (!CampfireBlock.isSmokeyPos(level, pos)) {
                if (this.hiveContainsBees(level, pos)) {
                    this.angerNearbyBees(level, pos);
                }

                this.releaseBeesAndResetHoneyLevel(level, state, pos, player, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            } else {
                this.resetHoneyLevel(level, state, pos);
            }

            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        } else {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
    }

    private boolean hiveContainsBees(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BeehiveBlockEntity beehiveBlockEntity ? !beehiveBlockEntity.isEmpty() : false;
    }

    public void releaseBeesAndResetHoneyLevel(
        Level level, BlockState state, BlockPos pos, @Nullable Player player, BeehiveBlockEntity.BeeReleaseStatus beeReleaseStatus
    ) {
        this.resetHoneyLevel(level, state, pos);
        if (level.getBlockEntity(pos) instanceof BeehiveBlockEntity beehiveBlockEntity) {
            beehiveBlockEntity.emptyAllLivingFromHive(player, state, beeReleaseStatus);
        }
    }

    public void resetHoneyLevel(Level level, BlockState state, BlockPos pos) {
        level.setBlock(pos, state.setValue(HONEY_LEVEL, 0), 3);
    }

    /**
     * Called periodically clientside on blocks near the player to show effects (like furnace fire particles).
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HONEY_LEVEL) >= 5) {
            for (int i = 0; i < random.nextInt(1) + 1; i++) {
                this.trySpawnDripParticles(level, pos, state);
            }
        }
    }

    private void trySpawnDripParticles(Level level, BlockPos pos, BlockState state) {
        if (state.getFluidState().isEmpty() && !(level.random.nextFloat() < 0.3F)) {
            VoxelShape voxelShape = state.getCollisionShape(level, pos);
            double d = voxelShape.max(Direction.Axis.Y);
            if (d >= 1.0 && !state.is(BlockTags.IMPERMEABLE)) {
                double e = voxelShape.min(Direction.Axis.Y);
                if (e > 0.0) {
                    this.spawnParticle(level, pos, voxelShape, pos.getY() + e - 0.05);
                } else {
                    BlockPos blockPos = pos.below();
                    BlockState blockState = level.getBlockState(blockPos);
                    VoxelShape voxelShape2 = blockState.getCollisionShape(level, blockPos);
                    double f = voxelShape2.max(Direction.Axis.Y);
                    if ((f < 1.0 || !blockState.isCollisionShapeFullBlock(level, blockPos)) && blockState.getFluidState().isEmpty()) {
                        this.spawnParticle(level, pos, voxelShape, pos.getY() - 0.05);
                    }
                }
            }
        }
    }

    private void spawnParticle(Level level, BlockPos pos, VoxelShape shape, double y) {
        this.spawnFluidParticle(
            level,
            pos.getX() + shape.min(Direction.Axis.X),
            pos.getX() + shape.max(Direction.Axis.X),
            pos.getZ() + shape.min(Direction.Axis.Z),
            pos.getZ() + shape.max(Direction.Axis.Z),
            y
        );
    }

    private void spawnFluidParticle(Level particleData, double x1, double x2, double z1, double z2, double y) {
        particleData.addParticle(
            ParticleTypes.DRIPPING_HONEY,
            Mth.lerp(particleData.random.nextDouble(), x1, x2),
            y,
            Mth.lerp(particleData.random.nextDouble(), z1, z2),
            0.0,
            0.0,
            0.0
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HONEY_LEVEL, FACING);
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeehiveBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null : createTickerHelper(blockEntityType, BlockEntityType.BEEHIVE, BeehiveBlockEntity::serverTick);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide
            && player.isCreative()
            && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
            && level.getBlockEntity(pos) instanceof BeehiveBlockEntity beehiveBlockEntity) {
            int i = state.getValue(HONEY_LEVEL);
            boolean bl = !beehiveBlockEntity.isEmpty();
            if (bl || i > 0) {
                ItemStack itemStack = new ItemStack(this);
                itemStack.applyComponents(beehiveBlockEntity.collectComponents());
                itemStack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(HONEY_LEVEL, i));
                ItemEntity itemEntity = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), itemStack);
                itemEntity.setDefaultPickUpDelay();
                level.addFreshEntity(itemEntity);
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Entity entity = params.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (entity instanceof PrimedTnt
            || entity instanceof Creeper
            || entity instanceof WitherSkull
            || entity instanceof WitherBoss
            || entity instanceof MinecartTNT) {
            BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
            if (blockEntity instanceof BeehiveBlockEntity beehiveBlockEntity) {
                beehiveBlockEntity.emptyAllLivingFromHive(null, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            }
        }

        return super.getDrops(state, params);
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (level.getBlockState(facingPos).getBlock() instanceof FireBlock && level.getBlockEntity(currentPos) instanceof BeehiveBlockEntity beehiveBlockEntity
            )
         {
            beehiveBlockEntity.emptyAllLivingFromHive(null, state, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        }

        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
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
