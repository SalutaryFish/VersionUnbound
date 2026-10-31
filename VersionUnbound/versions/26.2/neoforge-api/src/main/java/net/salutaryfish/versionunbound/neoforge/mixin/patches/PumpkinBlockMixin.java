package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.PumpkinBlock;
import net.neoforged.neoforge.common.ItemAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code PumpkinBlock}: carving asks the stack's {@code canPerformAction} with
 * {@code SHEARS_CARVE} instead of checking for shears, so a mod's shears carve too.
 */
@Mixin(PumpkinBlock.class)
abstract class PumpkinBlockMixin {
    @WrapOperation(
            method = "useItemOn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean versionunbound$canCarve(final ItemStack stack, final Object shears, final Operation<Boolean> is) {
        return stack.canPerformAction(ItemAbilities.SHEARS_CARVE);
    }
}
