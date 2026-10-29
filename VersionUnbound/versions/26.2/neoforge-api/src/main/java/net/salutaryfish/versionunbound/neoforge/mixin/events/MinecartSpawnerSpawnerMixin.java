package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.mojang.datafixers.util.Either;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.MinecartSpawner;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * NeoForge's patch to {@code MinecartSpawner}'s spawner: its owner, for {@code FinalizeSpawnEvent.getSpawner()}.
 */
@Mixin(targets = "net.minecraft.world.entity.vehicle.minecart.MinecartSpawner$1")
abstract class MinecartSpawnerSpawnerMixin {
    @Shadow
    @Final
    MinecartSpawner this$0;

    public Either<BlockEntity, Entity> getOwner() {
        return Either.right(this.this$0);
    }
}
