package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NeoForge's enchanting power ({@code getEnchantPowerBonus}) through Fabric API's: Fabric API's {@code EnchantmentMenu}
 * mixin sums {@code FabricBlock.getProvidedEnchantmentPower} over the bookshelves, so {@code Block} answers it with the
 * block extension's {@code getEnchantPowerBonus} (both defaults are the power provider tag). A NeoForge mod's block that
 * overrides {@code getEnchantPowerBonus} then counts in the menu, and a Fabric mod's override of Fabric API's method
 * still wins for its own block. NeoForge's own sum (a float, truncated) stands aside for Fabric API's (rounded).
 * EnchantingTableBlockMixin makes the bookshelf check ask the same power.
 */
@Mixin(Block.class)
abstract class EnchantPowerMixin {
    // Overrides FabricBlock's default.
    public float getProvidedEnchantmentPower(BlockState state, BlockGetter level, BlockPos pos) {
        return ((Block) (Object) this).getEnchantPowerBonus(state, level, pos);
    }
}
