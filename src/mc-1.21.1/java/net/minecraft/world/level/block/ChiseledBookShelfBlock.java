package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class ChiseledBookShelfBlock extends BaseEntityBlock {
    public static final MapCodec<ChiseledBookShelfBlock> CODEC = simpleCodec(ChiseledBookShelfBlock::new);
    private static final int MAX_BOOKS_IN_STORAGE = 6;
    public static final int BOOKS_PER_ROW = 3;
    public static final List<BooleanProperty> SLOT_OCCUPIED_PROPERTIES = List.of(
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_0_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_1_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_2_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_3_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_4_OCCUPIED,
        BlockStateProperties.CHISELED_BOOKSHELF_SLOT_5_OCCUPIED
    );

    @Override
    public MapCodec<ChiseledBookShelfBlock> codec() {
        return CODEC;
    }

    public ChiseledBookShelfBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState blockState = this.stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);

        for (BooleanProperty booleanProperty : SLOT_OCCUPIED_PROPERTIES) {
            blockState = blockState.setValue(booleanProperty, false);
        }

        this.registerDefaultState(blockState);
    }

    /**
     * The type of render function called. MODEL for mixed tesr and static model, MODELBLOCK_ANIMATED for TESR-only, LIQUID for vanilla liquids, INVISIBLE to skip all rendering
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity) {
            if (!stack.is(ItemTags.BOOKSHELF_BOOKS)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }

            OptionalInt optionalInt = this.getHitSlot(hitResult, state);
            if (optionalInt.isEmpty()) {
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            }

            if (state.getValue(SLOT_OCCUPIED_PROPERTIES.get(optionalInt.getAsInt()))) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }

            addBook(level, pos, player, chiseledBookShelfBlockEntity, stack, optionalInt.getAsInt());
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        } else {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity) {
            OptionalInt optionalInt = this.getHitSlot(hitResult, state);
            if (optionalInt.isEmpty()) {
                return InteractionResult.PASS;
            }

            if (!state.getValue(SLOT_OCCUPIED_PROPERTIES.get(optionalInt.getAsInt()))) {
                return InteractionResult.CONSUME;
            }

            removeBook(level, pos, player, chiseledBookShelfBlockEntity, optionalInt.getAsInt());
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    private OptionalInt getHitSlot(BlockHitResult hitReselt, BlockState state) {
        return getRelativeHitCoordinatesForBlockFace(hitReselt, state.getValue(HorizontalDirectionalBlock.FACING)).map(vec2 -> {
            int i = vec2.y >= 0.5F ? 0 : 1;
            int j = getSection(vec2.x);
            return OptionalInt.of(j + i * 3);
        }).orElseGet(OptionalInt::empty);
    }

    private static Optional<Vec2> getRelativeHitCoordinatesForBlockFace(BlockHitResult hitResult, Direction face) {
        Direction direction = hitResult.getDirection();
        if (face != direction) {
            return Optional.empty();
        }

        BlockPos blockPos = hitResult.getBlockPos().relative(direction);
        Vec3 vec3 = hitResult.getLocation().subtract(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        double d = vec3.x();
        double e = vec3.y();
        double f = vec3.z();

        return switch (direction) {
            case NORTH -> Optional.of(new Vec2((float)(1.0 - d), (float)e));
            case SOUTH -> Optional.of(new Vec2((float)d, (float)e));
            case WEST -> Optional.of(new Vec2((float)f, (float)e));
            case EAST -> Optional.of(new Vec2((float)(1.0 - f), (float)e));
            case DOWN, UP -> Optional.empty();
        };
    }

    private static int getSection(float x) {
        float f = 0.0625F;
        float g = 0.375F;
        if (x < 0.375F) {
            return 0;
        }

        float h = 0.6875F;
        return x < 0.6875F ? 1 : 2;
    }

    private static void addBook(Level level, BlockPos pos, Player player, ChiseledBookShelfBlockEntity blockEntity, ItemStack bookStack, int slot) {
        if (!level.isClientSide) {
            player.awardStat(Stats.ITEM_USED.get(bookStack.getItem()));
            SoundEvent soundEvent = bookStack.is(Items.ENCHANTED_BOOK)
                ? SoundEvents.CHISELED_BOOKSHELF_INSERT_ENCHANTED
                : SoundEvents.CHISELED_BOOKSHELF_INSERT;
            blockEntity.setItem(slot, bookStack.consumeAndReturn(1, player));
            level.playSound((Player)null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private static void removeBook(Level level, BlockPos pos, Player player, ChiseledBookShelfBlockEntity blockEntity, int slot) {
        if (!level.isClientSide) {
            ItemStack itemStack = blockEntity.removeItem(slot, 1);
            SoundEvent soundEvent = itemStack.is(Items.ENCHANTED_BOOK)
                ? SoundEvents.CHISELED_BOOKSHELF_PICKUP_ENCHANTED
                : SoundEvents.CHISELED_BOOKSHELF_PICKUP;
            level.playSound((Player)null, pos, soundEvent, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getInventory().add(itemStack)) {
                player.drop(itemStack, false);
            }

            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChiseledBookShelfBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING);
        SLOT_OCCUPIED_PROPERTIES.forEach(property -> builder.add(property));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            boolean bl;
            if (level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity && !chiseledBookShelfBlockEntity.isEmpty()) {
                for (int i = 0; i < 6; i++) {
                    ItemStack itemStack = chiseledBookShelfBlockEntity.getItem(i);
                    if (!itemStack.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), itemStack);
                    }
                }

                chiseledBookShelfBlockEntity.clearContent();
                bl = true;
            } else {
                bl = false;
            }

            super.onRemove(state, level, pos, newState, movedByPiston);
            if (bl) {
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Returns the blockstate with the given rotation from the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
    }

    /**
     * Returns the blockstate with the given mirror of the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
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
        if (level.isClientSide()) {
            return 0;
        } else {
            return level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity chiseledBookShelfBlockEntity
                ? chiseledBookShelfBlockEntity.getLastInteractedSlot() + 1
                : 0;
        }
    }
}
