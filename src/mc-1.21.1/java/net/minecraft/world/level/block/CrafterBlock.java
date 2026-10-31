package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CrafterBlock extends BaseEntityBlock {
    public static final MapCodec<CrafterBlock> CODEC = simpleCodec(CrafterBlock::new);
    public static final BooleanProperty CRAFTING = BlockStateProperties.CRAFTING;
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    private static final EnumProperty<FrontAndTop> ORIENTATION = BlockStateProperties.ORIENTATION;
    private static final int MAX_CRAFTING_TICKS = 6;
    private static final int CRAFTING_TICK_DELAY = 4;
    private static final RecipeCache RECIPE_CACHE = new RecipeCache(10);
    private static final int CRAFTER_ADVANCEMENT_DIAMETER = 17;

    public CrafterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ORIENTATION, FrontAndTop.NORTH_UP).setValue(TRIGGERED, false).setValue(CRAFTING, false));
    }

    @Override
    protected MapCodec<CrafterBlock> codec() {
        return CODEC;
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
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CrafterBlockEntity crafterBlockEntity ? crafterBlockEntity.getRedstoneSignal() : 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        boolean bl = level.hasNeighborSignal(pos);
        boolean bl2 = state.getValue(TRIGGERED);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (bl && !bl2) {
            level.scheduleTick(pos, this, 4);
            level.setBlock(pos, state.setValue(TRIGGERED, true), 2);
            this.setBlockEntityTriggered(blockEntity, true);
        } else if (!bl && bl2) {
            level.setBlock(pos, state.setValue(TRIGGERED, false).setValue(CRAFTING, false), 2);
            this.setBlockEntityTriggered(blockEntity, false);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        this.dispenseFrom(state, level, pos);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null : createTickerHelper(blockEntityType, BlockEntityType.CRAFTER, CrafterBlockEntity::serverTick);
    }

    private void setBlockEntityTriggered(@Nullable BlockEntity blockEntity, boolean triggered) {
        if (blockEntity instanceof CrafterBlockEntity crafterBlockEntity) {
            crafterBlockEntity.setTriggered(triggered);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        CrafterBlockEntity crafterBlockEntity = new CrafterBlockEntity(pos, state);
        crafterBlockEntity.setTriggered(state.hasProperty(TRIGGERED) && state.getValue(TRIGGERED));
        return crafterBlockEntity;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getNearestLookingDirection().getOpposite();

        Direction direction2 = switch (direction) {
            case DOWN -> context.getHorizontalDirection().getOpposite();
            case UP -> context.getHorizontalDirection();
            case NORTH, SOUTH, WEST, EAST -> Direction.UP;
        };
        return this.defaultBlockState()
            .setValue(ORIENTATION, FrontAndTop.fromFrontAndTop(direction, direction2))
            .setValue(TRIGGERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /**
     * Called by BlockItem after this block has been placed.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (state.getValue(TRIGGERED)) {
            level.scheduleTick(pos, this, 4);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof CrafterBlockEntity) {
            player.openMenu((CrafterBlockEntity)blockEntity);
        }

        return InteractionResult.CONSUME;
    }

    protected void dispenseFrom(BlockState state, ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CrafterBlockEntity crafterBlockEntity) {
            CraftingInput var11 = crafterBlockEntity.asCraftInput();
            Optional<RecipeHolder<CraftingRecipe>> optional = getPotentialResults(level, var11);
            if (optional.isEmpty()) {
                level.levelEvent(1050, pos, 0);
            } else {
                RecipeHolder<CraftingRecipe> recipeHolder = optional.get();
                ItemStack itemStack = recipeHolder.value().assemble(var11, level.registryAccess());
                if (itemStack.isEmpty()) {
                    level.levelEvent(1050, pos, 0);
                } else {
                    crafterBlockEntity.setCraftingTicksRemaining(6);
                    level.setBlock(pos, state.setValue(CRAFTING, true), 2);
                    itemStack.onCraftedBySystem(level);
                    this.dispenseItem(level, pos, crafterBlockEntity, itemStack, state, recipeHolder);

                    for (ItemStack itemStack2 : recipeHolder.value().getRemainingItems(var11)) {
                        if (!itemStack2.isEmpty()) {
                            this.dispenseItem(level, pos, crafterBlockEntity, itemStack2, state, recipeHolder);
                        }
                    }

                    crafterBlockEntity.getItems().forEach(itemStackx -> {
                        if (!itemStackx.isEmpty()) {
                            itemStackx.shrink(1);
                        }
                    });
                    crafterBlockEntity.setChanged();
                }
            }
        }
    }

    public static Optional<RecipeHolder<CraftingRecipe>> getPotentialResults(Level level, CraftingInput input) {
        return RECIPE_CACHE.get(level, input);
    }

    private void dispenseItem(
        ServerLevel level, BlockPos pos, CrafterBlockEntity crafter, ItemStack stack, BlockState state, RecipeHolder<CraftingRecipe> recipe
    ) {
        Direction direction = state.getValue(ORIENTATION).front();
        Container container = HopperBlockEntity.getContainerAt(level, pos.relative(direction));
        ItemStack itemStack = stack.copy();
        if (container != null && (container instanceof CrafterBlockEntity || stack.getCount() > container.getMaxStackSize(stack))) {
            while (!itemStack.isEmpty()) {
                ItemStack itemStack2 = itemStack.copyWithCount(1);
                ItemStack itemStack3 = HopperBlockEntity.addItem(crafter, container, itemStack2, direction.getOpposite());
                if (!itemStack3.isEmpty()) {
                    break;
                }

                itemStack.shrink(1);
            }
        } else if (container != null) {
            while (!itemStack.isEmpty()) {
                int i = itemStack.getCount();
                itemStack = HopperBlockEntity.addItem(crafter, container, itemStack, direction.getOpposite());
                if (i == itemStack.getCount()) {
                    break;
                }
            }
        }

        if (!itemStack.isEmpty()) {
            Vec3 vec3 = Vec3.atCenterOf(pos);
            Vec3 vec32 = vec3.relative(direction, 0.7);
            DefaultDispenseItemBehavior.spawnItem(level, itemStack, 6, direction, vec32);

            for (ServerPlayer serverPlayer : level.getEntitiesOfClass(ServerPlayer.class, AABB.ofSize(vec3, 17.0, 17.0, 17.0))) {
                CriteriaTriggers.CRAFTER_RECIPE_CRAFTED.trigger(serverPlayer, recipe.id(), crafter.getItems());
            }

            level.levelEvent(1049, pos, 0);
            level.levelEvent(2010, pos, direction.get3DDataValue());
        }
    }

    /**
     * The type of render function called. MODEL for mixed tesr and static model, MODELBLOCK_ANIMATED for TESR-only, LIQUID for vanilla liquids, INVISIBLE to skip all rendering
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * Returns the blockstate with the given rotation from the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ORIENTATION, rotation.rotation().rotate(state.getValue(ORIENTATION)));
    }

    /**
     * Returns the blockstate with the given mirror of the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ORIENTATION, mirror.rotation().rotate(state.getValue(ORIENTATION)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ORIENTATION, TRIGGERED, CRAFTING);
    }
}
