package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

public class CommandBlock extends BaseEntityBlock implements GameMasterBlock {
    public static final MapCodec<CommandBlock> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(Codec.BOOL.fieldOf("automatic").forGetter(commandBlock -> commandBlock.automatic), propertiesCodec())
            .apply(instance, CommandBlock::new)
    );
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DirectionProperty FACING = DirectionalBlock.FACING;
    public static final BooleanProperty CONDITIONAL = BlockStateProperties.CONDITIONAL;
    private final boolean automatic;

    @Override
    public MapCodec<CommandBlock> codec() {
        return CODEC;
    }

    public CommandBlock(boolean automatic, BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CONDITIONAL, false));
        this.automatic = automatic;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        CommandBlockEntity commandBlockEntity = new CommandBlockEntity(pos, state);
        commandBlockEntity.setAutomatic(this.automatic);
        return commandBlockEntity;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof CommandBlockEntity commandBlockEntity) {
                boolean bl = level.hasNeighborSignal(pos);
                boolean bl2 = commandBlockEntity.isPowered();
                commandBlockEntity.setPowered(bl);
                if (!bl2 && !commandBlockEntity.isAutomatic() && commandBlockEntity.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
                    if (bl) {
                        commandBlockEntity.markConditionMet();
                        level.scheduleTick(pos, this, 1);
                    }
                }
            }
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof CommandBlockEntity commandBlockEntity) {
            BaseCommandBlock baseCommandBlock = commandBlockEntity.getCommandBlock();
            boolean bl = !StringUtil.isNullOrEmpty(baseCommandBlock.getCommand());
            CommandBlockEntity.Mode mode = commandBlockEntity.getMode();
            boolean bl2 = commandBlockEntity.wasConditionMet();
            if (mode == CommandBlockEntity.Mode.AUTO) {
                commandBlockEntity.markConditionMet();
                if (bl2) {
                    this.execute(state, level, pos, baseCommandBlock, bl);
                } else if (commandBlockEntity.isConditional()) {
                    baseCommandBlock.setSuccessCount(0);
                }

                if (commandBlockEntity.isPowered() || commandBlockEntity.isAutomatic()) {
                    level.scheduleTick(pos, this, 1);
                }
            } else if (mode == CommandBlockEntity.Mode.REDSTONE) {
                if (bl2) {
                    this.execute(state, level, pos, baseCommandBlock, bl);
                } else if (commandBlockEntity.isConditional()) {
                    baseCommandBlock.setSuccessCount(0);
                }
            }

            level.updateNeighbourForOutputSignal(pos, this);
        }
    }

    private void execute(BlockState state, Level level, BlockPos pos, BaseCommandBlock logic, boolean canTrigger) {
        if (canTrigger) {
            logic.performCommand(level);
        } else {
            logic.setSuccessCount(0);
        }

        executeChain(level, pos, state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof CommandBlockEntity && player.canUseGameMasterBlocks()) {
            player.openCommandBlock((CommandBlockEntity)blockEntity);
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
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
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof CommandBlockEntity ? ((CommandBlockEntity)blockEntity).getCommandBlock().getSuccessCount() : 0;
    }

    /**
     * Called by BlockItem after this block has been placed.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof CommandBlockEntity commandBlockEntity) {
            BaseCommandBlock baseCommandBlock = commandBlockEntity.getCommandBlock();
            if (!level.isClientSide) {
                if (!stack.has(DataComponents.BLOCK_ENTITY_DATA)) {
                    baseCommandBlock.setTrackOutput(level.getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK));
                    commandBlockEntity.setAutomatic(this.automatic);
                }

                boolean bl = level.hasNeighborSignal(pos);
                commandBlockEntity.setPowered(bl);
            }
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
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /**
     * Returns the blockstate with the given mirror of the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONDITIONAL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    private static void executeChain(Level level, BlockPos pos, Direction direction) {
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();
        GameRules gameRules = level.getGameRules();
        int i = gameRules.getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH);

        while (i-- > 0) {
            mutableBlockPos.move(direction);
            BlockState blockState = level.getBlockState(mutableBlockPos);
            Block block = blockState.getBlock();
            if (!blockState.is(Blocks.CHAIN_COMMAND_BLOCK)
                || !(level.getBlockEntity(mutableBlockPos) instanceof CommandBlockEntity commandBlockEntity)
                || commandBlockEntity.getMode() != CommandBlockEntity.Mode.SEQUENCE) {
                break;
            }

            if (commandBlockEntity.isPowered() || commandBlockEntity.isAutomatic()) {
                BaseCommandBlock baseCommandBlock = commandBlockEntity.getCommandBlock();
                if (commandBlockEntity.markConditionMet()) {
                    if (!baseCommandBlock.performCommand(level)) {
                        break;
                    }

                    level.updateNeighbourForOutputSignal(mutableBlockPos, block);
                } else if (commandBlockEntity.isConditional()) {
                    baseCommandBlock.setSuccessCount(0);
                }
            }

            direction = blockState.getValue(FACING);
        }

        if (i <= 0) {
            int j = Math.max(gameRules.getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH), 0);
            LOGGER.warn("Command Block chain tried to execute more than {} steps!", j);
        }
    }
}
