package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public class FillCommand {
    private static final Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE = new Dynamic2CommandExceptionType(
        (maxBlocks, specifiedBlocks) -> Component.translatableEscape("commands.fill.toobig", maxBlocks, specifiedBlocks)
    );
    static final BlockInput HOLLOW_CORE = new BlockInput(Blocks.AIR.defaultBlockState(), Collections.emptySet(), null);
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.fill.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("fill")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("from", BlockPosArgument.blockPos())
                        .then(
                            Commands.argument("to", BlockPosArgument.blockPos())
                                .then(
                                    Commands.argument("block", BlockStateArgument.block(context))
                                        .executes(
                                            contextx -> fillBlocks(
                                                (CommandSourceStack)contextx.getSource(),
                                                BoundingBox.fromCorners(
                                                    BlockPosArgument.getLoadedBlockPos(contextx, "from"), BlockPosArgument.getLoadedBlockPos(contextx, "to")
                                                ),
                                                BlockStateArgument.getBlock(contextx, "block"),
                                                FillCommand.Mode.REPLACE,
                                                null
                                            )
                                        )
                                        .then(
                                            Commands.literal("replace")
                                                .executes(
                                                    contextx -> fillBlocks(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        BoundingBox.fromCorners(
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "from"),
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "to")
                                                        ),
                                                        BlockStateArgument.getBlock(contextx, "block"),
                                                        FillCommand.Mode.REPLACE,
                                                        null
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("filter", BlockPredicateArgument.blockPredicate(context))
                                                        .executes(
                                                            contextx -> fillBlocks(
                                                                (CommandSourceStack)contextx.getSource(),
                                                                BoundingBox.fromCorners(
                                                                    BlockPosArgument.getLoadedBlockPos(contextx, "from"),
                                                                    BlockPosArgument.getLoadedBlockPos(contextx, "to")
                                                                ),
                                                                BlockStateArgument.getBlock(contextx, "block"),
                                                                FillCommand.Mode.REPLACE,
                                                                BlockPredicateArgument.getBlockPredicate(contextx, "filter")
                                                            )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("keep")
                                                .executes(
                                                    contextx -> fillBlocks(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        BoundingBox.fromCorners(
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "from"),
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "to")
                                                        ),
                                                        BlockStateArgument.getBlock(contextx, "block"),
                                                        FillCommand.Mode.REPLACE,
                                                        blockInWorld -> blockInWorld.getLevel().isEmptyBlock(blockInWorld.getPos())
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("outline")
                                                .executes(
                                                    contextx -> fillBlocks(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        BoundingBox.fromCorners(
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "from"),
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "to")
                                                        ),
                                                        BlockStateArgument.getBlock(contextx, "block"),
                                                        FillCommand.Mode.OUTLINE,
                                                        null
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("hollow")
                                                .executes(
                                                    contextx -> fillBlocks(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        BoundingBox.fromCorners(
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "from"),
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "to")
                                                        ),
                                                        BlockStateArgument.getBlock(contextx, "block"),
                                                        FillCommand.Mode.HOLLOW,
                                                        null
                                                    )
                                                )
                                        )
                                        .then(
                                            Commands.literal("destroy")
                                                .executes(
                                                    contextx -> fillBlocks(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        BoundingBox.fromCorners(
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "from"),
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "to")
                                                        ),
                                                        BlockStateArgument.getBlock(contextx, "block"),
                                                        FillCommand.Mode.DESTROY,
                                                        null
                                                    )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int fillBlocks(
        CommandSourceStack source, BoundingBox area, BlockInput newBlock, FillCommand.Mode mode, @Nullable Predicate<BlockInWorld> replacingPredicate
    ) throws CommandSyntaxException {
        int i = area.getXSpan() * area.getYSpan() * area.getZSpan();
        int j = source.getLevel().getGameRules().getInt(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT);
        if (i > j) {
            throw ERROR_AREA_TOO_LARGE.create(j, i);
        }

        List<BlockPos> list = Lists.newArrayList();
        ServerLevel serverLevel = source.getLevel();
        int k = 0;

        for (BlockPos blockPos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
            if (replacingPredicate == null || replacingPredicate.test(new BlockInWorld(serverLevel, blockPos, true))) {
                BlockInput blockInput = mode.filter.filter(area, blockPos, newBlock, serverLevel);
                if (blockInput != null) {
                    BlockEntity blockEntity = serverLevel.getBlockEntity(blockPos);
                    Clearable.tryClear(blockEntity);
                    if (blockInput.place(serverLevel, blockPos, 2)) {
                        list.add(blockPos.immutable());
                        k++;
                    }
                }
            }
        }

        for (BlockPos blockPos : list) {
            Block block = serverLevel.getBlockState(blockPos).getBlock();
            serverLevel.blockUpdated(blockPos, block);
        }

        if (k == 0) {
            throw ERROR_FAILED.create();
        }

        int l = k;
        source.sendSuccess(() -> Component.translatable("commands.fill.success", l), true);
        return k;
    }

    enum Mode {
        REPLACE((area, blockPos, newBlock, level) -> newBlock),
        OUTLINE(
            (area, blockPos, newBlock, level) -> blockPos.getX() != area.minX()
                    && blockPos.getX() != area.maxX()
                    && blockPos.getY() != area.minY()
                    && blockPos.getY() != area.maxY()
                    && blockPos.getZ() != area.minZ()
                    && blockPos.getZ() != area.maxZ()
                ? null
                : newBlock
        ),
        HOLLOW(
            (area, blockPos, newBlock, level) -> blockPos.getX() != area.minX()
                    && blockPos.getX() != area.maxX()
                    && blockPos.getY() != area.minY()
                    && blockPos.getY() != area.maxY()
                    && blockPos.getZ() != area.minZ()
                    && blockPos.getZ() != area.maxZ()
                ? FillCommand.HOLLOW_CORE
                : newBlock
        ),
        DESTROY((area, blockPos, newBlock, level) -> {
            level.destroyBlock(blockPos, true);
            return newBlock;
        });

        public final SetBlockCommand.Filter filter;

        Mode(final SetBlockCommand.Filter filter) {
            this.filter = filter;
        }
    }
}
