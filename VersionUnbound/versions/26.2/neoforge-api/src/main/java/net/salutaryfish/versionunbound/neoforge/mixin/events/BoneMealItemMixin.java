package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.salutaryfish.versionunbound.neoforge.patches.BoneMealItemPatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code BoneMealItem}: {@code BonemealEvent} before bone meal is applied (cancelable; then the
 * event says whether it counts as used). Using the item applies it with the player, on both sides
 * ({@code applyBonemeal}, see {@link BoneMealItemPatch}); vanilla's other callers of {@code growCrop} (dispensers,
 * farmer villagers) apply it without a player, on the server, and a {@code growCrop} call on the client does nothing,
 * as NeoForge's deprecated {@code growCrop} does.
 */
@Mixin(BoneMealItem.class)
abstract class BoneMealItemMixin {
    @WrapOperation(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BoneMealItem;growCrop(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean versionunbound$applyBonemeal(ItemStack itemStack, Level level, BlockPos pos, Operation<Boolean> original, UseOnContext context) {
        return BoneMealItemPatch.withPlayer(context.getPlayer(), () -> original.call(itemStack, level, pos));
    }

    @Inject(method = "growCrop", at = @At("HEAD"), cancellable = true)
    private static void versionunbound$bonemeal(ItemStack itemStack, Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BoneMealItemPatch.Call call = BoneMealItemPatch.CALL.get();
        if (call == null && !(level instanceof ServerLevel)) {
            cir.setReturnValue(false);
            return;
        }
        // A growCrop call made while this one runs (a block's own bone meal handling) is a direct call again.
        BoneMealItemPatch.CALL.remove();
        Player player = call == null ? null : call.player();
        BonemealEvent event = EventHooks.fireBonemealEvent(player, level, pos, level.getBlockState(pos), itemStack);
        if (event.isCanceled()) cir.setReturnValue(event.isSuccessful());
    }
}
