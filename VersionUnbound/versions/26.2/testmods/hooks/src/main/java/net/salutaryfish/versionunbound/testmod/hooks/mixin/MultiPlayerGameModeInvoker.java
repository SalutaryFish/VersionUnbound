package net.salutaryfish.versionunbound.testmod.hooks.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The client's use of an item on a block without the packet useItemOn sends (the server's hand isn't the client's). */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeInvoker {
    @Invoker("performUseItemOn")
    InteractionResult hooks$performUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult blockHit);
}
