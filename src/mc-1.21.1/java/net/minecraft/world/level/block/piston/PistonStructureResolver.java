package net.minecraft.world.level.block.piston;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

public class PistonStructureResolver {
    public static final int MAX_PUSH_DEPTH = 12;
    private final Level level;
    private final BlockPos pistonPos;
    private final boolean extending;
    private final BlockPos startPos;
    private final Direction pushDirection;
    /**
     * All block positions to be moved by the piston
     */
    private final List<BlockPos> toPush = Lists.newArrayList();
    /**
     * All blocks to be destroyed by the piston
     */
    private final List<BlockPos> toDestroy = Lists.newArrayList();
    private final Direction pistonDirection;

    public PistonStructureResolver(Level level, BlockPos pistonPos, Direction pistonDirection, boolean extending) {
        this.level = level;
        this.pistonPos = pistonPos;
        this.pistonDirection = pistonDirection;
        this.extending = extending;
        if (extending) {
            this.pushDirection = pistonDirection;
            this.startPos = pistonPos.relative(pistonDirection);
        } else {
            this.pushDirection = pistonDirection.getOpposite();
            this.startPos = pistonPos.relative(pistonDirection, 2);
        }
    }

    public boolean resolve() {
        this.toPush.clear();
        this.toDestroy.clear();
        BlockState blockState = this.level.getBlockState(this.startPos);
        if (!PistonBaseBlock.isPushable(blockState, this.level, this.startPos, this.pushDirection, false, this.pistonDirection)) {
            if (this.extending && blockState.getPistonPushReaction() == PushReaction.DESTROY) {
                this.toDestroy.add(this.startPos);
                return true;
            } else {
                return false;
            }
        } else {
            if (!this.addBlockLine(this.startPos, this.pushDirection)) {
                return false;
            }

            for (int i = 0; i < this.toPush.size(); i++) {
                BlockPos blockPos = this.toPush.get(i);
                if (isSticky(this.level.getBlockState(blockPos)) && !this.addBranchingBlocks(blockPos)) {
                    return false;
                }
            }

            return true;
        }
    }

    private static boolean isSticky(BlockState state) {
        return state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK);
    }

    private static boolean canStickToEachOther(BlockState state1, BlockState state2) {
        if (state1.is(Blocks.HONEY_BLOCK) && state2.is(Blocks.SLIME_BLOCK)) {
            return false;
        } else {
            return state1.is(Blocks.SLIME_BLOCK) && state2.is(Blocks.HONEY_BLOCK) ? false : isSticky(state1) || isSticky(state2);
        }
    }

    private boolean addBlockLine(BlockPos originPos, Direction direction) {
        BlockState blockState = this.level.getBlockState(originPos);
        if (blockState.isAir()) {
            return true;
        }

        if (!PistonBaseBlock.isPushable(blockState, this.level, originPos, this.pushDirection, false, direction)) {
            return true;
        }

        if (originPos.equals(this.pistonPos)) {
            return true;
        }

        if (this.toPush.contains(originPos)) {
            return true;
        }

        int i = 1;
        if (i + this.toPush.size() > 12) {
            return false;
        }

        while (isSticky(blockState)) {
            BlockPos blockPos = originPos.relative(this.pushDirection.getOpposite(), i);
            BlockState blockState2 = blockState;
            blockState = this.level.getBlockState(blockPos);
            if (blockState.isAir()
                || !canStickToEachOther(blockState2, blockState)
                || !PistonBaseBlock.isPushable(blockState, this.level, blockPos, this.pushDirection, false, this.pushDirection.getOpposite())
                || blockPos.equals(this.pistonPos)) {
                break;
            }

            if (++i + this.toPush.size() > 12) {
                return false;
            }
        }

        int j = 0;

        for (int k = i - 1; k >= 0; k--) {
            this.toPush.add(originPos.relative(this.pushDirection.getOpposite(), k));
            j++;
        }

        int k = 1;

        while (true) {
            BlockPos blockPos2 = originPos.relative(this.pushDirection, k);
            int l = this.toPush.indexOf(blockPos2);
            if (l > -1) {
                this.reorderListAtCollision(j, l);

                for (int m = 0; m <= l + j; m++) {
                    BlockPos blockPos3 = this.toPush.get(m);
                    if (isSticky(this.level.getBlockState(blockPos3)) && !this.addBranchingBlocks(blockPos3)) {
                        return false;
                    }
                }

                return true;
            }

            blockState = this.level.getBlockState(blockPos2);
            if (blockState.isAir()) {
                return true;
            }

            if (!PistonBaseBlock.isPushable(blockState, this.level, blockPos2, this.pushDirection, true, this.pushDirection)
                || blockPos2.equals(this.pistonPos)) {
                return false;
            }

            if (blockState.getPistonPushReaction() == PushReaction.DESTROY) {
                this.toDestroy.add(blockPos2);
                return true;
            }

            if (this.toPush.size() >= 12) {
                return false;
            }

            this.toPush.add(blockPos2);
            j++;
            k++;
        }
    }

    private void reorderListAtCollision(int offsets, int index) {
        List<BlockPos> list = Lists.newArrayList();
        List<BlockPos> list2 = Lists.newArrayList();
        List<BlockPos> list3 = Lists.newArrayList();
        list.addAll(this.toPush.subList(0, index));
        list2.addAll(this.toPush.subList(this.toPush.size() - offsets, this.toPush.size()));
        list3.addAll(this.toPush.subList(index, this.toPush.size() - offsets));
        this.toPush.clear();
        this.toPush.addAll(list);
        this.toPush.addAll(list2);
        this.toPush.addAll(list3);
    }

    private boolean addBranchingBlocks(BlockPos fromPos) {
        BlockState blockState = this.level.getBlockState(fromPos);

        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != this.pushDirection.getAxis()) {
                BlockPos blockPos = fromPos.relative(direction);
                BlockState blockState2 = this.level.getBlockState(blockPos);
                if (canStickToEachOther(blockState2, blockState) && !this.addBlockLine(blockPos, direction)) {
                    return false;
                }
            }
        }

        return true;
    }

    public Direction getPushDirection() {
        return this.pushDirection;
    }

    public List<BlockPos> getToPush() {
        return this.toPush;
    }

    public List<BlockPos> getToDestroy() {
        return this.toDestroy;
    }
}
