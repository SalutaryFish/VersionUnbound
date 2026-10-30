package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.stats.Stat;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code ItemStack.useOn}, for the events it fires: {@code UseItemOnBlockEvent} in its
 * {@code ITEM_AFTER_BLOCK} phase first (on both sides; canceled returns its result), then on the server
 * {@code CommonHooks.onPlaceItemIntoWorld}, which captures the blocks the item places and fires
 * {@code BlockEvent.EntityPlaceEvent} or {@code EntityMultiPlaceEvent} for them (and {@code PlayerDestroyItemEvent} when
 * the use empties the stack).
 *
 * <p>NeoForge's {@code useOn} returns the hook's result on the server instead of running vanilla's body. Here vanilla's
 * body stays and the hook wraps its {@code Item.useOn} call, because Fabric API wraps that call for its
 * {@code ItemEvents.USE_ON}, which would otherwise never fire on the server. Fabric's wrapper ends up outside this one
 * (seen in the exported class), so {@code USE_ON} decides first, and blocks a {@code USE_ON} listener places itself
 * fire no {@code EntityPlaceEvent}. Keeping vanilla's body runs its adventure mode check before the hook's own (the
 * same predicate, so it changes nothing), and the hook awards the item use stat on the server, as in NeoForge (for every
 * use that consumes the action, where vanilla only counts item interactions), so vanilla's award stays on the client.
 *
 * <p>Not ported from this patch: {@code onItemUseFirst}, which fires the {@code ITEM_BEFORE_BLOCK} phase (it overrides an
 * item stack extension method, Phase 2 step 5), and the other hunks (item extension methods, tooltips, attribute
 * modifiers, template comparisons).
 */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void versionunbound$useItemOnBlock(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        var e = NeoForge.EVENT_BUS.post(new UseItemOnBlockEvent(context, UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK));
        if (e.isCanceled()) cir.setReturnValue(e.getCancellationResult());
    }

    @WrapOperation(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/Item;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult versionunbound$placeItemIntoWorld(Item item, UseOnContext context, Operation<InteractionResult> original) {
        if (context.getLevel().isClientSide()) return original.call(item, context);
        return CommonHooks.onPlaceItemIntoWorld(context, c -> original.call(item, c));
    }

    @WrapWithCondition(method = "useOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;awardStat(Lnet/minecraft/stats/Stat;)V"))
    private boolean versionunbound$awardOnClientOnly(Player player, Stat<?> stat) {
        return player.level().isClientSide();
    }
}
