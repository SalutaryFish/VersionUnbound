package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code AbstractFurnaceBlockEntity}, the burn time parts: a fuel's burn time is the stack's
 * {@code getBurnTime} for the furnace's recipe type (the item's, then {@code FurnaceFuelBurnTimeEvent}; the item's
 * default is the level's fuel values, where Fabric API's fuel registry answers), both for burning and for what the
 * fuel slot accepts. The fuel's crafting remainder is Fabric API's mixin's (see ItemCraftingRemainderMixin). Not yet:
 * the int burn times in the save data and the short-safe data slots, and the transaction parts.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
abstract class AbstractFurnaceBlockEntityMixin {
    @Unique
    private RecipeType<? extends AbstractCookingRecipe> versionunbound$recipeType;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void versionunbound$rememberRecipeType(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState,
            RecipeType<? extends AbstractCookingRecipe> recipeType, CallbackInfo ci) {
        this.versionunbound$recipeType = recipeType;
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    protected int getBurnDuration(final FuelValues fuelValues, final ItemStack itemStack) {
        return itemStack.getBurnTime(this.versionunbound$recipeType, fuelValues);
    }

    @WrapOperation(method = "canPlaceItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/FuelValues;isFuel(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean versionunbound$isFuel(FuelValues fuelValues, ItemStack itemStack, Operation<Boolean> original) {
        return itemStack.getBurnTime(this.versionunbound$recipeType, fuelValues) > 0;
    }
}
