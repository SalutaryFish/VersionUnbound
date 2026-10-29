package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code ServerPlayerGameMode}, for the events it fires: left click block (cancellable; its use-block
 * result can skip the block's attack), block break (in place of vanilla's {@code canDestroyBlock} check), the tool
 * breaking ({@code PlayerDestroyItemEvent}), right click item and right click block (cancellable; its use-block and
 * use-item results override vanilla's choices).
 *
 * <p>Not yet: the parts of the patch that call extension methods (Phase 2 step 5): {@code onItemUseFirst} and
 * {@code doesSneakBypassUse} in {@code useItemOn}, and in {@code destroyBlock} the removal through
 * {@code onDestroyedByPlayer} after the tool is used and {@code canHarvestBlock}. Vanilla's behavior stands in, which
 * is what the extension methods' defaults do. Nor NeoForge's change to
 * {@code setGameModeForPlayer} that keeps a player flying.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Shadow
    private GameType gameModeForPlayer;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
    private void versionunbound$leftClickBlock(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxY, int sequence,
            CallbackInfo ci, @Share("leftClickBlock") LocalRef<PlayerInteractEvent.LeftClickBlock> event) {
        event.set(CommonHooks.onLeftClickBlock(this.player, pos, direction, action));
        if (event.get().isCanceled()) {
            ci.cancel();
        }
    }

    @WrapWithCondition(method = "handleBlockBreakAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;attack(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"))
    private boolean versionunbound$attackUnlessDenied(BlockState state, Level level, BlockPos pos, Player player,
            @Share("leftClickBlock") LocalRef<PlayerInteractEvent.LeftClickBlock> event) {
        return event.get().getUseBlock() != TriState.FALSE;
    }

    // Neo: Fire the BlockBreakEvent, and ignore the original ItemStack#canDestroyBlock check since the break event manages
    // the status of it (fireBlockBreak runs that check to pre-cancel the event).
    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;canDestroyBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean versionunbound$breakBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, Operation<Boolean> original) {
        return !CommonHooks.fireBlockBreak(level, this.gameModeForPlayer, player, pos, state).isCanceled();
    }

    // Neo: Fire the PlayerDestroyItemEvent if the tool was broken at any point during the break process
    @Inject(method = "destroyBlock", at = @At("TAIL"))
    private void versionunbound$destroyItemOnBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir, @Local(ordinal = 0) ItemStack itemStack, @Local(ordinal = 1) ItemStack destroyedWith) {
        if (itemStack.isEmpty() && !destroyedWith.isEmpty()) {
            EventHooks.onPlayerDestroyItem(this.player, destroyedWith, InteractionHand.MAIN_HAND);
        }
    }

    // After the spectator and cooldown checks, before the item is used.
    @Inject(method = "useItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getCount()I", ordinal = 0), cancellable = true)
    private void versionunbound$rightClickItem(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult cancelResult = CommonHooks.onItemRightClick(player, hand);
        if (cancelResult != null) cir.setReturnValue(cancelResult);
    }

    // After the check that the block is enabled, before the spectator check.
    @Inject(method = "useItemOn", at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;gameModeForPlayer:Lnet/minecraft/world/level/GameType;", opcode = Opcodes.GETFIELD, ordinal = 0), cancellable = true)
    private void versionunbound$rightClickBlock(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand, BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir, @Share("rightClickBlock") LocalRef<PlayerInteractEvent.RightClickBlock> event) {
        event.set(CommonHooks.onRightClickBlock(player, hand, hitResult.getBlockPos(), hitResult));
        if (event.get().isCanceled()) cir.setReturnValue(event.get().getCancellationResult());
    }

    // NeoForge uses the block if its use-block result is TRUE, or DEFAULT and vanilla wouldn't suppress it; that is,
    // vanilla's suppressUsingBlock (the second boolean local) becomes "use-block is FALSE" unless it's DEFAULT.
    @ModifyVariable(method = "useItemOn", at = @At("STORE"), ordinal = 1)
    private boolean versionunbound$useBlock(boolean suppressUsingBlock, @Share("rightClickBlock") LocalRef<PlayerInteractEvent.RightClickBlock> event) {
        TriState useBlock = event.get().getUseBlock();
        return useBlock.isDefault() ? suppressUsingBlock : useBlock.isFalse();
    }

    // NeoForge uses the item if its use-item result is TRUE, or DEFAULT and the stack isn't empty or on cooldown, and
    // returns PASS when it's FALSE: vanilla's "!itemStack.isEmpty() && !onCooldown" with those two values overridden.
    @ModifyExpressionValue(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 2))
    private boolean versionunbound$useItemIfNotEmpty(boolean isEmpty, @Share("rightClickBlock") LocalRef<PlayerInteractEvent.RightClickBlock> event) {
        TriState useItem = event.get().getUseItem();
        return useItem.isDefault() ? isEmpty : useItem.isFalse();
    }

    @ModifyExpressionValue(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemCooldowns;isOnCooldown(Lnet/minecraft/world/item/ItemStack;)Z", ordinal = 0))
    private boolean versionunbound$useItemIfNotOnCooldown(boolean onCooldown, @Share("rightClickBlock") LocalRef<PlayerInteractEvent.RightClickBlock> event) {
        return !event.get().getUseItem().isTrue() && onCooldown;
    }
}
