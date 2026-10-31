package net.salutaryfish.versionunbound.neoforge.client.mixin.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code MultiPlayerGameMode}, for the events the client fires itself before it tells the server:
 * block break, left click block (creative and survival starts, and every tick while mining), right click block and right
 * click item, with the same cancellation and use-block/use-item results as on the server
 * ({@code ServerPlayerGameModeMixin}).
 *
 * <p>Several of these sit in the lambdas vanilla hands to {@code startPrediction}, which runs them right away; they are
 * targeted by their names in the 26.2 jar. Since step 5, the extension methods: {@code destroyBlock} removes the block
 * through the state's {@code onDestroyedByPlayer} (never harvesting, as in NeoForge) and hands {@code destroy} the state
 * {@code playerWillDestroy} answered; {@code performUseItemOn} asks the stack's {@code onItemUseFirst} before the block,
 * and decides whether there's something in the hands by {@code doesSneakBypassUse} (NeoForge's client patch; the server's
 * differs). Not yet: {@code shouldCauseBlockBreakReset} and the client block extensions' hit sound.
 * {@code PlayerDestroyItemEvent} fires when using an item (not on a block) leaves the hand empty, as in NeoForge.
 */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private GameType localPlayerMode;

    // Survival's left click, fired in startDestroyBlock and applied in its prediction lambda.
    @Unique
    private PlayerInteractEvent.@Nullable LeftClickBlock versionunbound$leftClickBlock;

    // Neo: Fire the BlockBreakEvent. Unlike the server path, ignoring ItemStack#canDestroyBlock is done below because the
    // vanilla code checks things in a different order here.
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void versionunbound$breakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = this.minecraft.level.getBlockState(pos);
        var event = CommonHooks.fireBlockBreak(this.minecraft.level, this.localPlayerMode, this.minecraft.player, pos, state);
        if (event.isCanceled()) {
            cir.setReturnValue(false);
        }
    }

    // Neo: Ignore this condition, as it is handled by fireBlockBreak and the event is expected to be able to override it.
    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;canDestroyBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z"))
    private boolean versionunbound$ignoreCanDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, Operation<Boolean> original) {
        return true;
    }

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;playerWillDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState versionunbound$rememberRemovedState(Block block, Level level, BlockPos pos, BlockState state, Player player, Operation<BlockState> original,
            @Share("removedState") LocalRef<BlockState> removedState) {
        BlockState removed = original.call(block, level, pos, state, player);
        removedState.set(removed);
        return removed;
    }

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean versionunbound$destroyedByPlayer(Level level, BlockPos pos, BlockState fluidBlock, int flags, Operation<Boolean> original,
            @Local(name = "oldState") BlockState oldState, @Local(name = "fluidState") FluidState fluidState) {
        return oldState.onDestroyedByPlayer(level, pos, this.minecraft.player, this.minecraft.player.getMainHandItem().copy(), false, fluidState);
    }

    @ModifyArg(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;destroy(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"), index = 2)
    private BlockState versionunbound$destroyRemovedState(BlockState oldState, @Share("removedState") LocalRef<BlockState> removedState) {
        return removedState.get();
    }

    // Creative: the block breaks right away unless the left click is canceled, in startDestroyBlock and in
    // continueDestroyBlock.
    @WrapOperation(method = { "lambda$startDestroyBlock$0", "lambda$continueDestroyBlock$0" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyBlock(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean versionunbound$creativeLeftClick(MultiPlayerGameMode gameMode, BlockPos pos, Operation<Boolean> original, @Local(argsOnly = true) Direction direction) {
        if (CommonHooks.onLeftClickBlock(this.minecraft.player, pos, direction, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK).isCanceled()) {
            return false;
        }
        return original.call(gameMode, pos);
    }

    // Survival: once any old break is aborted, before the block state is read.
    @Inject(method = "startDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", ordinal = 2))
    private void versionunbound$leftClickBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        this.versionunbound$leftClickBlock = CommonHooks.onLeftClickBlock(this.minecraft.player, pos, direction, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK);
    }

    @WrapWithCondition(method = "lambda$startDestroyBlock$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;attack(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V"))
    private boolean versionunbound$attackUnlessDenied(BlockState state, Level level, BlockPos pos, Player player) {
        return this.versionunbound$leftClickBlock == null || this.versionunbound$leftClickBlock.getUseBlock() != TriState.FALSE;
    }

    // Right after the block's attack: with use-item FALSE, only the start packet is sent, and no breaking starts. The
    // second IFEQ is the "if (notAir && ...)" that every path reaches from there.
    @Inject(method = "lambda$startDestroyBlock$1", at = @At(value = "JUMP", opcode = Opcodes.IFEQ, ordinal = 1), cancellable = true)
    private void versionunbound$startUnlessItemDenied(BlockState state, BlockPos pos, Direction direction, int sequence, CallbackInfoReturnable<Packet<?>> cir) {
        if (this.versionunbound$leftClickBlock != null && this.versionunbound$leftClickBlock.getUseItem().isFalse()) {
            cir.setReturnValue(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction, sequence));
        }
    }

    // Every tick while mining, before the progress is checked.
    @Inject(method = "continueDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/tutorial/Tutorial;onDestroyBlock(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;F)V", ordinal = 1, shift = At.Shift.AFTER), cancellable = true)
    private void versionunbound$clientMineHold(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (CommonHooks.onClientMineHold(this.minecraft.player, pos, direction).getUseItem().isFalse()) {
            cir.setReturnValue(true);
        }
    }

    // Before the spectator check; the three injectors after it work as on the server.
    @Inject(method = "performUseItemOn", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;localPlayerMode:Lnet/minecraft/world/level/GameType;", opcode = Opcodes.GETFIELD, ordinal = 0), cancellable = true)
    private void versionunbound$rightClickBlock(LocalPlayer player, InteractionHand hand, BlockHitResult blockHit, CallbackInfoReturnable<InteractionResult> cir,
            @Share("rightClickBlock") LocalRef<PlayerInteractEvent.RightClickBlock> event) {
        event.set(CommonHooks.onRightClickBlock(player, hand, blockHit.getBlockPos(), blockHit));
        if (event.get().isCanceled()) {
            cir.setReturnValue(event.get().getCancellationResult());
        }
    }

    // Neo: the stack's onItemUseFirst before the block, unless the event denies the item; anything but PASS ends the
    // use. After the spectator check, at the first read of the main hand.
    @Inject(method = "performUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;", ordinal = 0), cancellable = true)
    private void versionunbound$itemUseFirst(LocalPlayer player, InteractionHand hand, BlockHitResult blockHit, CallbackInfoReturnable<InteractionResult> cir,
            @Local(ordinal = 0) ItemStack itemStack, @Share("rightClickBlock") LocalRef<PlayerInteractEvent.RightClickBlock> event) {
        if (event.get().getUseItem() != TriState.FALSE) {
            InteractionResult result = itemStack.onItemUseFirst(new UseOnContext(player, hand, blockHit));
            if (result != InteractionResult.PASS) cir.setReturnValue(result);
        }
    }

    // Neo: something is in the hands unless its item lets sneaking bypass (an empty hand does); vanilla's isEmpty().
    @WrapOperation(method = "performUseItemOn", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 0),
            @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 1) })
    private boolean versionunbound$sneakBypassUse(ItemStack stack, Operation<Boolean> original, @Local(argsOnly = true) LocalPlayer player,
            @Local(argsOnly = true) BlockHitResult blockHit) {
        return stack.doesSneakBypassUse(player.level(), blockHit.getBlockPos(), player);
    }

    @ModifyVariable(method = "performUseItemOn", at = @At("STORE"), ordinal = 1)
    private boolean versionunbound$useBlock(boolean suppressUsingBlock, @Share("rightClickBlock") LocalRef<PlayerInteractEvent.RightClickBlock> event) {
        TriState useBlock = event.get().getUseBlock();
        return useBlock.isDefault() ? suppressUsingBlock : useBlock.isFalse();
    }

    @ModifyExpressionValue(method = "performUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 2))
    private boolean versionunbound$useItemIfNotEmpty(boolean isEmpty, @Share("rightClickBlock") LocalRef<PlayerInteractEvent.RightClickBlock> event) {
        TriState useItem = event.get().getUseItem();
        return useItem.isDefault() ? isEmpty : useItem.isFalse();
    }

    @ModifyExpressionValue(method = "performUseItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemCooldowns;isOnCooldown(Lnet/minecraft/world/item/ItemStack;)Z", ordinal = 0))
    private boolean versionunbound$useItemIfNotOnCooldown(boolean onCooldown, @Share("rightClickBlock") LocalRef<PlayerInteractEvent.RightClickBlock> event) {
        return !event.get().getUseItem().isTrue() && onCooldown;
    }

    // In useItem's prediction lambda, after the cooldown check and before the item is used.
    @Inject(method = "lambda$useItem$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"), cancellable = true)
    private void versionunbound$rightClickItem(InteractionHand hand, Player player, MutableObject<InteractionResult> interactionResult, int sequence,
            CallbackInfoReturnable<Packet<?>> cir, @Local ServerboundUseItemPacket packet) {
        InteractionResult cancelResult = CommonHooks.onItemRightClick(player, hand);
        if (cancelResult != null) {
            interactionResult.setValue(cancelResult);
            cir.setReturnValue(packet);
        }
    }

    // The item's use replaced the stack in the hand with an empty one.
    @Inject(method = "lambda$useItem$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V", shift = At.Shift.AFTER))
    private void versionunbound$destroyItemOnUse(InteractionHand hand, Player player, MutableObject<InteractionResult> interactionResult, int sequence,
            CallbackInfoReturnable<Packet<?>> cir, @Local(ordinal = 0) ItemStack itemStack, @Local(ordinal = 1) ItemStack result) {
        if (result.isEmpty()) EventHooks.onPlayerDestroyItem(player, itemStack, hand);
    }
}
