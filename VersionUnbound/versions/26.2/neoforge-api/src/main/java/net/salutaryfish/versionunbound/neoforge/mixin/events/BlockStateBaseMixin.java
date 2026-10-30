package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code BlockBehaviour.BlockStateBase}, for {@code UseItemOnBlockEvent} in its {@code BLOCK} phase,
 * before a block is used with an item (on both sides; canceled returns its result instead of the block's). Fabric API's
 * {@code BlockEvents.USE_ITEM_ON} also fires at the head of {@code useItemOn}, after this one (seen in the exported
 * class), so a canceled event keeps it from firing. Not ported from this patch: the block state extension's methods it
 * calls (Phase 2 step 5).
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void versionunbound$useItemOnBlock(ItemStack itemStack, Level level, Player player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        var useOnContext = new UseOnContext(level, player, hand, player.getItemInHand(hand).copy(), hitResult);
        var e = NeoForge.EVENT_BUS.post(new UseItemOnBlockEvent(useOnContext, UseItemOnBlockEvent.UsePhase.BLOCK));
        if (e.isCanceled()) cir.setReturnValue(e.getCancellationResult());
    }
}
