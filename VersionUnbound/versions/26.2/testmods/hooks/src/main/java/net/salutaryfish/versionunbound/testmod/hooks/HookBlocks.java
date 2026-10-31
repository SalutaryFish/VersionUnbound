package net.salutaryfish.versionunbound.testmod.hooks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.redstone.Orientation;
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

    /** A removal onDestroyedByPlayer heard. */
    record DestroyedByPlayer(BlockPos pos, ItemStack tool, boolean willHarvest, boolean clientSide) {}

    static final List<DestroyedByPlayer> DESTROYED_BY_PLAYER = new ArrayList<>();

    /** Refuses to be removed by a player. */
    static final class StubbornBlock extends Block {
        StubbornBlock(Properties properties) {
            super(properties);
        }

        @Override
        public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, boolean willHarvest, FluidState fluid) {
            DESTROYED_BY_PLAYER.add(new DestroyedByPlayer(pos.immutable(), toolStack, willHarvest, level.isClientSide()));
            return false;
        }
    }

    /** What the block extension's other hooks heard, in order. */
    static final List<String> HEARD = new ArrayList<>();

    /** Asked whether it drops when exploded (no), and told when it's exploded. */
    static final class BlastBlock extends Block {
        BlastBlock(Properties properties) {
            super(properties);
        }

        @Override
        public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
            HEARD.add("canDropFromExplosion " + pos.toShortString());
            return false;
        }

        @Override
        public void onBlockExploded(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion) {
            HEARD.add("onBlockExploded " + pos.toShortString());
            super.onBlockExploded(state, level, pos, explosion);
        }
    }

    /** Sticky like slime, but not to honey. */
    static final class GlueBlock extends Block {
        GlueBlock(Properties properties) {
            super(properties);
        }

        @Override
        public boolean isStickyBlock(BlockState state) {
            return true;
        }

        @Override
        public boolean canStickTo(BlockState state, BlockState other) {
            return !other.is(Blocks.HONEY_BLOCK);
        }
    }

    /** Fifteen bookshelves' worth of enchanting power, outside vanilla's power provider tag. */
    static final class RuneShelf extends Block {
        RuneShelf(Properties properties) {
            super(properties);
        }

        @Override
        public float getEnchantPowerBonus(BlockState state, BlockGetter level, BlockPos pos) {
            return 15.0F;
        }
    }

    /** A ladder outside the climbable tag, that also makes an open trapdoor above it climbable. */
    static final class Rope extends Block {
        Rope(Properties properties) {
            super(properties);
        }

        @Override
        public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
            return true;
        }

        @Override
        public boolean makesOpenTrapdoorAboveClimbable(BlockState state, LevelReader level, BlockPos pos, BlockState trapdoorState) {
            return true;
        }
    }

    /** Damaging to walk into, and sticky like honey to walk next to. */
    static final class Thorns extends Block {
        Thorns(Properties properties) {
            super(properties);
        }

        @Override
        public @Nullable PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
            return PathType.DAMAGING;
        }

        @Override
        public @Nullable PathType getAdjacentBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob, PathType originalType) {
            return PathType.STICKY_HONEY;
        }
    }

    /** Hears neighbour changes, weak changes and its own state changes. */
    static final class Listener extends Block {
        Listener(Properties properties) {
            super(properties);
        }

        @Override
        public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
            HEARD.add("onNeighborChange " + pos.toShortString() + " from " + neighbor.toShortString());
        }

        @Override
        public boolean getWeakChanges(BlockState state, LevelReader level, BlockPos pos) {
            return true;
        }

        @Override
        protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
            HEARD.add("neighborChanged " + pos.toShortString());
        }

        @Override
        public void onBlockStateChange(LevelReader level, BlockPos pos, BlockState oldState, BlockState newState) {
            HEARD.add("onBlockStateChange " + pos.toShortString() + " was " + oldState.getBlock().getDescriptionId());
        }
    }
}
