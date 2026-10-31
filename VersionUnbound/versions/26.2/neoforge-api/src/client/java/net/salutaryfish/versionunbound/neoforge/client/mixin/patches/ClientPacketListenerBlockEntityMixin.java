package net.salutaryfish.versionunbound.neoforge.client.mixin.patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code ClientPacketListener.handleBlockEntityData}: a block entity's data packet loads through
 * the block entity extension's {@code onDataPacket} (whose default is vanilla's {@code loadWithComponents}).
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerBlockEntityMixin {
    @WrapOperation(method = "lambda$handleBlockEntityData$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;loadWithComponents(Lnet/minecraft/world/level/storage/ValueInput;)V"))
    private void versionunbound$onDataPacket(BlockEntity blockEntity, ValueInput input, Operation<Void> original) {
        blockEntity.onDataPacket(((ClientPacketListener) (Object) this).getConnection(), input);
    }
}
