package net.salutaryfish.versionunbound.neoforge.mixin.events;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.salutaryfish.versionunbound.neoforge.patches.LevelBlockSnapshots;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Block}, for {@code BlockEvent.EntityPlaceEvent}: while the level restores block snapshots
 * (a canceled placement put back), blocks drop no items or experience. NeoForge adds {@code !level.restoringBlockSnapshots}
 * to the condition both methods wrap their whole body in; returning at the head does the same. Not ported from this
 * patch: the drop capture behind {@code BlockDropsEvent}, the block extension's methods, and the rest (step 5 and later).
 */
@Mixin(Block.class)
abstract class BlockMixin {
    @Inject(method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
    private static void versionunbound$noDropsWhileRestoring(Level level, Supplier<ItemEntity> entityFactory, ItemStack itemStack, CallbackInfo ci) {
        if (((LevelBlockSnapshots) level).versionunbound$restoringBlockSnapshots()) ci.cancel();
    }

    @Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
    private void versionunbound$noExperienceWhileRestoring(ServerLevel level, BlockPos pos, int amount, CallbackInfo ci) {
        if (((LevelBlockSnapshots) level).versionunbound$restoringBlockSnapshots()) ci.cancel();
    }
}
