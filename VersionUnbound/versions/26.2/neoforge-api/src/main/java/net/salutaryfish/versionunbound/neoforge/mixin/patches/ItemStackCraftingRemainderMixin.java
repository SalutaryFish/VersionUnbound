package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * {@code ItemStack.getCraftingRemainder()} declared on the class: Fabric API's {@code FabricItemStack} (injected into
 * {@code ItemStack}) and NeoForge's {@code ItemInstanceExtension} (injected into {@code ItemInstance}, which
 * {@code ItemStack} implements) both have it as a default, and two unrelated defaults make every call throw
 * {@code IncompatibleClassChangeError}. It asks the item's stack-aware remainder, Fabric API's, which answers
 * NeoForge's (ItemCraftingRemainderMixin): the same answer as either default.
 */
@Mixin(ItemStack.class)
abstract class ItemStackCraftingRemainderMixin {
    public @Nullable ItemStackTemplate getCraftingRemainder() {
        ItemStack self = (ItemStack) (Object) this;
        return self.getItem().getCraftingRemainder(self);
    }
}
