package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Implements {@code IItemExtension.isCombineRepairable}, which NeoForge's patch implements on {@link Item}. NeoForge
 * reads {@code canCombineRepair} from the item's properties ({@code Item.Properties.setNoCombineRepair}, part of
 * {@code IItemPropertiesExtensions}, not ported yet), so here every item may be combine-repaired, as by default there.
 */
@Mixin(Item.class)
abstract class ItemCombineRepairMixin {
    @Unique
    protected final boolean canCombineRepair = true;

    public boolean isCombineRepairable(ItemStack stack) {
        return canCombineRepair && ((Item) (Object) this).isDamageable(stack);
    }
}
