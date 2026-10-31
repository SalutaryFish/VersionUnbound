package net.minecraft.world.level.redstone;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public interface NeighborUpdater {
    Direction[] UPDATE_ORDER = new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH};

    void shapeUpdate(Direction var1, BlockState var2, BlockPos var3, BlockPos var4, int var5, int var6);

    void neighborChanged(BlockPos var1, Block var2, BlockPos var3);

    void neighborChanged(BlockState var1, BlockPos var2, Block var3, BlockPos var4, boolean var5);

    default void updateNeighborsAtExceptFromFacing(BlockPos pos, Block block, @Nullable Direction facing) {
        for (Direction direction : UPDATE_ORDER) {
            if (direction != facing) {
                this.neighborChanged(pos.relative(direction), block, pos);
            }
        }
    }

    static void executeShapeUpdate(
        LevelAccessor level, Direction direction, BlockState state, BlockPos pos, BlockPos neighborPos, int flags, int recursionLevel
    ) {
        BlockState blockState = level.getBlockState(pos);
        BlockState blockState2 = blockState.updateShape(direction, state, level, pos, neighborPos);
        Block.updateOrDestroy(blockState, blockState2, level, pos, flags, recursionLevel);
    }

    static void executeUpdate(Level level, BlockState state, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        try {
            state.handleNeighborChanged(level, pos, neighborBlock, neighborPos, movedByPiston);
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Exception while updating neighbours");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Block being updated");
            crashReportCategory.setDetail(
                "Source block type",
                () -> {
                    try {
                        return String.format(
                            Locale.ROOT,
                            "ID #%s (%s // %s)",
                            BuiltInRegistries.BLOCK.getKey(neighborBlock),
                            neighborBlock.getDescriptionId(),
                            neighborBlock.getClass().getCanonicalName()
                        );
                    } catch (Throwable throwablex) {
                        return "ID #" + BuiltInRegistries.BLOCK.getKey(neighborBlock);
                    }
                }
            );
            CrashReportCategory.populateBlockDetails(crashReportCategory, level, pos, state);
            throw new ReportedException(crashReport);
        }
    }
}
