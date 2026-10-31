package net.salutaryfish.versionunbound.testmod.hooks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** This mod's blocks, each overriding block extension methods vanilla's code should ask. */
final class HookBlocks {
    private HookBlocks() {}

    /** A face that caught fire, as onCaughtFire heard it. */
    record CaughtFire(BlockPos pos, @Nullable Direction face) {}

    static final List<CaughtFire> CAUGHT_FIRE = new ArrayList<>();

    static final class Kindling extends Block {
        Kindling(Properties properties) {
            super(properties);
        }

        @Override
        public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
            return true;
        }

        @Override
        public boolean onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction direction, @Nullable LivingEntity igniter) {
            CAUGHT_FIRE.add(new CaughtFire(pos.immutable(), direction));
            return true;
        }
    }

    static final class EmberStone extends Block {
        EmberStone(Properties properties) {
            super(properties);
        }

        @Override
        public boolean isFireSource(BlockState state, LevelReader level, BlockPos pos, Direction direction) {
            return direction == Direction.UP;
        }
    }

    static final class DudTnt extends TntBlock {
        DudTnt(Properties properties) {
            super(properties);
        }

        @Override
        public boolean onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction direction, @Nullable LivingEntity igniter) {
            CAUGHT_FIRE.add(new CaughtFire(pos.immutable(), direction));
            return false;
        }
    }
}
