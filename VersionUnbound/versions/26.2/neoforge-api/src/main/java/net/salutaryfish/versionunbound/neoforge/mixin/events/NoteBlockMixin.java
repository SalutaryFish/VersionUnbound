package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.NoteBlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code NoteBlock}: {@code NoteBlockEvent.Change} when a player tunes it (canceled: the use fails
 * and the note stays; otherwise the event's note is set) and {@code NoteBlockEvent.Play} when it plays (on both sides,
 * as the block event runs there; canceled: no sound or particle; otherwise the event's note and instrument play).
 */
@Mixin(NoteBlock.class)
abstract class NoteBlockMixin {
    @WrapOperation(method = "useWithoutItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;cycle(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Object;"))
    private Object versionunbound$noteChange(BlockState state, Property<?> property, Operation<Object> original, @Local(argsOnly = true) Level level,
            @Local(argsOnly = true) BlockPos pos, @Cancellable CallbackInfoReturnable<InteractionResult> cir) {
        BlockState cycled = (BlockState) original.call(state, property);
        int _new = CommonHooks.onNoteChange(level, pos, state, state.getValue(NoteBlock.NOTE), cycled.getValue(NoteBlock.NOTE));
        if (_new == -1) {
            cir.setReturnValue(InteractionResult.FAIL);
            return state;
        }
        return state.setValue(NoteBlock.NOTE, _new);
    }

    @Inject(method = "triggerEvent", at = @At("HEAD"), cancellable = true)
    private void versionunbound$play(BlockState state, Level level, BlockPos pos, int b0, int b1, CallbackInfoReturnable<Boolean> cir,
            @Local(argsOnly = true) LocalRef<BlockState> stateRef) {
        NoteBlockEvent.Play e = new NoteBlockEvent.Play(level, pos, state, state.getValue(NoteBlock.NOTE), state.getValue(NoteBlock.INSTRUMENT));
        if (NeoForge.EVENT_BUS.post(e).isCanceled()) {
            cir.setReturnValue(false);
            return;
        }
        stateRef.set(state.setValue(NoteBlock.NOTE, e.getVanillaNoteId()).setValue(NoteBlock.INSTRUMENT, e.getInstrument()));
    }
}
