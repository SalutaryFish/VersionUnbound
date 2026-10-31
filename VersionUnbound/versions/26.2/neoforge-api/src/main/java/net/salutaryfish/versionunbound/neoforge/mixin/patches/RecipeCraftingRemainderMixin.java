package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.BannerDuplicateRecipe;
import net.minecraft.world.item.crafting.BookCloningRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patches to {@code BookCloningRecipe} and {@code BannerDuplicateRecipe}: their remainders ask the stack
 * ({@code ItemStack.getCraftingRemainder()}, stack-aware) instead of the item. Fabric API covers the furnace, the
 * brewing stand and {@code CraftingRecipe}'s default.
 */
@Mixin({ BookCloningRecipe.class, BannerDuplicateRecipe.class })
abstract class RecipeCraftingRemainderMixin {
    @WrapOperation(method = "getRemainingItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;getCraftingRemainder()Lnet/minecraft/world/item/ItemStackTemplate;"))
    private ItemStackTemplate versionunbound$stackRemainder(Item item, Operation<ItemStackTemplate> original, @Local(name = "itemStack") ItemStack itemStack) {
        return itemStack.getCraftingRemainder();
    }
}
