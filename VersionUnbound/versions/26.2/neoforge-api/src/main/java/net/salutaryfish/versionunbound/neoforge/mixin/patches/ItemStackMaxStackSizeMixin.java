package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NeoForge's override of {@code ItemStack.getMaxStackSize()} (vanilla's is {@code ItemInstance}'s default, the stack's
 * max stack size component): it asks the item's stack-aware {@code getMaxStackSize(ItemStack)}, whose default reads the
 * same component, so a mod's item can decide per stack. Every vanilla caller (containers, merging, slots,
 * {@code isStackable}, the stack codec's check) reads it through this method.
 */
@Mixin(ItemStack.class)
abstract class ItemStackMaxStackSizeMixin {
    public int getMaxStackSize() {
        ItemStack self = (ItemStack) (Object) this;
        return self.getItem().getMaxStackSize(self);
    }
}
