package net.minecraft.world.item;

import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

public class StandingAndWallBlockItem extends BlockItem {
    protected final Block wallBlock;
    private final Direction attachmentDirection;

    public StandingAndWallBlockItem(Block block, Block wallBlock, Item.Properties properties, Direction attachmentDirection) {
        super(block, properties);
        this.wallBlock = wallBlock;
        this.attachmentDirection = attachmentDirection;
    }

    protected boolean canPlace(LevelReader level, BlockState state, BlockPos pos) {
        return state.canSurvive(level, pos);
    }

    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState blockState = this.wallBlock.getStateForPlacement(context);
        BlockState blockState2 = null;
        LevelReader levelReader = context.getLevel();
        BlockPos blockPos = context.getClickedPos();

        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction != this.attachmentDirection.getOpposite()) {
                BlockState blockState3 = direction == this.attachmentDirection ? this.getBlock().getStateForPlacement(context) : blockState;
                if (blockState3 != null && this.canPlace(levelReader, blockState3, blockPos)) {
                    blockState2 = blockState3;
                    break;
                }
            }
        }

        return blockState2 != null && levelReader.isUnobstructed(blockState2, blockPos, CollisionContext.empty()) ? blockState2 : null;
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItemMap, Item item) {
        super.registerBlocks(blockToItemMap, item);
        blockToItemMap.put(this.wallBlock, item);
    }
}
