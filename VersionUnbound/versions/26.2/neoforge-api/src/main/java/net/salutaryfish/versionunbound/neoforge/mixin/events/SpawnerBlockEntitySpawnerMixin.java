package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.mojang.datafixers.util.Either;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code SpawnerBlockEntity}'s spawner: its owner, for {@code FinalizeSpawnEvent.getSpawner()}.
 * javac leaves out the unused reference to the block entity in this class, so the constructor's argument is kept.
 */
@Mixin(targets = "net.minecraft.world.level.block.entity.SpawnerBlockEntity$1")
abstract class SpawnerBlockEntitySpawnerMixin {
    @Unique
    private SpawnerBlockEntity versionunbound$blockEntity;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void versionunbound$keepOwner(SpawnerBlockEntity blockEntity, CallbackInfo ci) {
        this.versionunbound$blockEntity = blockEntity;
    }

    public Either<BlockEntity, Entity> getOwner() {
        return Either.left(this.versionunbound$blockEntity);
    }
}
