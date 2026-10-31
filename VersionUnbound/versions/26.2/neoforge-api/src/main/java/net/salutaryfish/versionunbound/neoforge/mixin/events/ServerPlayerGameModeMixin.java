package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
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
 * use-item results override vanilla's choices), and in {@code destroyBlock} the block state's {@code canHarvestBlock}
 * (a stand-in for the block extension's, whose default asks {@code PlayerEvent.HarvestCheck}) for the drops.
 *
 * <p>Since step 5, the extension methods: {@code useItemOn} asks the stack's {@code onItemUseFirst} (which fires
 * {@code UseItemOnBlockEvent} in its {@code ITEM_BEFORE_BLOCK} phase) before the block, and both hands'
 * {@code doesSneakBypassUse} when sneaking; {@code destroyBlock} removes the block through the state's
 * {@code onDestroyedByPlayer}, with whether the player harvests it. <b>Deviation:</b> NeoForge moves the removal after the
 * tool's {@code mineBlock}; here it stays where vanilla removes the block (before the tool is used), because Fabric API
 * injects its {@code PlayerBlockBreakEvents} at vanilla's {@code playerWillDestroy} and {@code destroy} calls there. The
 * block still gets the tool as it was before the break, as in NeoForge, and {@code canHarvestBlock} is asked once (its
 * answer is shared with the drops check). Not yet: NeoForge's change to {@code setGameModeForPlayer} that keeps a player
 * flying.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @Shadow
    private GameType gameModeForPlayer;

    @Shadow
    protected ServerLevel level;

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

    // Neo: removal through onDestroyedByPlayer (NeoForge's removeBlock), told whether the player will harvest the block;
    // with preventsBlockDrops (creative) it won't. The tool is the main hand before the break.
    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean versionunbound$destroyedByPlayer(ServerLevel level, BlockPos pos, boolean movedByPiston, Operation<Boolean> original,
            @Local(name = "adjustedState") BlockState adjustedState, @Share("canHarvest") LocalBooleanRef canHarvest) {
        canHarvest.set(!this.player.preventsBlockDrops() && adjustedState.canHarvestBlock(level, pos, this.player));
        return adjustedState.onDestroyedByPlayer(level, pos, this.player, this.player.getMainHandItem().copy(), canHarvest.get(), level.getFluidState(pos));
    }

    // Neo: the block decides whether the player gets the drops (previously player.hasCorrectToolForDrops(blockstate)); the
    // block extension's default asks PlayerEvent.HarvestCheck. Asked once, at the removal above.
    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean versionunbound$canHarvestBlock(ServerPlayer player, BlockState adjustedState, Operation<Boolean> original, @Share("canHarvest") LocalBooleanRef canHarvest) {
        return canHarvest.get();
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

    // Neo: the stack's onItemUseFirst (UseItemOnBlockEvent's ITEM_BEFORE_BLOCK phase, then the item) comes before the
    // block, unless the event denies the item; anything but PASS ends the use. After the spectator branch, at the
    // first read of the main hand.
    @Inject(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;", ordinal = 0), cancellable = true)
    private void versionunbound$itemUseFirst(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand, BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir, @Share("rightClickBlock") LocalRef<PlayerInteractEvent.RightClickBlock> event) {
        if (event.get().getUseItem() != TriState.FALSE) {
            InteractionResult result = itemStack.onItemUseFirst(new UseOnContext(player, hand, hitResult));
            if (result != InteractionResult.PASS) cir.setReturnValue(result);
        }
    }

    // Neo: sneaking doesn't suppress the block when both hands' items let sneaking bypass.
    @ModifyExpressionValue(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSecondaryUseActive()Z"))
    private boolean versionunbound$sneakBypassUse(boolean secondaryUse, @Local(argsOnly = true) ServerPlayer player, @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) BlockHitResult hitResult) {
        BlockPos pos = hitResult.getBlockPos();
        return secondaryUse && !(player.getMainHandItem().doesSneakBypassUse(level, pos, player) && player.getOffhandItem().doesSneakBypassUse(level, pos, player));
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
