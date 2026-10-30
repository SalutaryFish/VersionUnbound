package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Methods of NeoForge's {@code IBlockExtension} that its patches already call, injected into {@link Block} by the class
 * tweaker with NeoForge's default bodies; mods' blocks override them (Create's copycat blocks override
 * {@code canEntityDestroy}, Farmer's Delight's rich soil {@code canSustainPlant} and {@code isFertile}; {@code canHarvestBlock}
 * decides whether a player breaking the block gets its drops). {@link BlockStateExtension} has the block state's side. When the block extension is ported
 * (Phase 2 step 5), it declares the same methods.
 */
public interface BlockExtension {
    /**
     * Determines if this block either force allow or force disallow a plant from being placed on it. (Or pass and let the plant's decision win)
     * This will be called in plant's canSurvive method and/or mayPlace method.
     *
     * @param state        The current state
     * @param level        The current level
     * @param soilPosition The current position of the block that will sustain the plant
     * @param facing       The direction relative to the given position the plant wants to be, typically its UP
     * @param plant        The plant that is checking survivability
     * @return {@link TriState#TRUE} to allow the plant to be planted/stay. {@link TriState#FALSE} to disallow the plant from placing. {@link TriState#DEFAULT} to allow the plant to make the decision to stay or not.
     */
    default TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos soilPosition, Direction facing, BlockState plant) {
        return TriState.DEFAULT;
    }

    /**
     * Checks if this soil is fertile, typically this means that growth rates
     * of plants on this soil will be slightly sped up.
     * Only vanilla case is tilledField when it is within range of water.
     *
     * @param level The current level
     * @param pos   Block position in level
     * @return True if the soil should be considered fertile.
     */
    default boolean isFertile(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getBlock() instanceof FarmlandBlock)
            return state.getValue(FarmlandBlock.MOISTURE) > 0;

        return false;
    }

    /**
     * Determines if the player can harvest this block, obtaining it's drops when the block is destroyed.
     *
     * @param level  The current level
     * @param pos    The block's current position
     * @param player The player damaging the block
     * @return True to spawn the drops
     */
    default public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return EventHooks.doPlayerHarvestCheck(player, state, level, pos);
    }

    /**
     * Determines if this block is can be destroyed by the specified entities normal behavior.
     *
     * @param state The current state
     * @param level The current level
     * @param pos   Block position in level
     * @return True to allow the ender dragon to destroy this block
     */
    default boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        if (entity instanceof EnderDragon) {
            return !((Block) this).defaultBlockState().is(BlockTags.DRAGON_IMMUNE);
        } else if ((entity instanceof WitherBoss) ||
                (entity instanceof WitherSkull)) {
                    return state.isAir() || WitherBoss.canDestroy(state);
                }

        return true;
    }
}
