package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NeoForge's stack-aware crafting remainder ({@code IItemExtension.getCraftingRemainder(ItemInstance)}) through Fabric
 * API's ({@code FabricItem.getCraftingRemainder(ItemStack)}): {@code Item} answers Fabric API's with NeoForge's (both
 * defaults are the item's plain remainder), so the furnace, brewing stand and crafting recipe mixins Fabric API already
 * has (which ask the stack before it shrinks, as NeoForge's patches do) give NeoForge mods' remainders too, and a Fabric
 * mod's override still wins for its own item. ItemStackCraftingRemainderMixin settles the stack's own method.
 */
@Mixin(Item.class)
abstract class ItemCraftingRemainderMixin {
    // Overrides FabricItem's default.
    public @Nullable ItemStackTemplate getCraftingRemainder(ItemStack stack) {
        return ((Item) (Object) this).getCraftingRemainder((ItemInstance) stack);
    }
}
