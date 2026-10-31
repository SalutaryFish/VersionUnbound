package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.TripWireBlock;
import net.neoforged.neoforge.common.ItemAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code TripWireBlock}: disarming asks the main hand stack's {@code canPerformAction} with
 * {@code SHEARS_DISARM} instead of checking for shears, so a mod's shears disarm too.
 */
@Mixin(TripWireBlock.class)
abstract class TripWireBlockMixin {
    @WrapOperation(
            method = "playerWillDestroy",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean versionunbound$canDisarm(final ItemStack stack, final Object shears, final Operation<Boolean> is) {
        return stack.canPerformAction(ItemAbilities.SHEARS_DISARM);
    }
}
