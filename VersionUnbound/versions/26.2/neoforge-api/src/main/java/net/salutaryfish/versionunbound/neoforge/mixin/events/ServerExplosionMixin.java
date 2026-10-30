package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code ServerExplosion}: {@code ExplosionEvent.Detonate} with the entities in reach (mods remove
 * the ones the explosion spares) and the blocks it destroys, and {@code ExplosionKnockbackEvent} for each entity's
 * knockback. NeoForge adds a {@code hurtEntities(List<BlockPos>)} overload that {@code explode} calls with its blocks,
 * and makes the old method call it with an empty list; here the old method keeps its body (other mods' mixins target
 * it) and reads the blocks {@code explode} hands it through a field, empty if something else calls it.
 * {@code ExplosionEvent.Start} fires in {@code ServerLevel.explode} ({@code ServerLevelMixin}).
 */
@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Unique
    private List<BlockPos> versionunbound$blocks = List.of();

    @WrapOperation(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;hurtEntities()V"))
    private void versionunbound$hurtEntities(ServerExplosion explosion, Operation<Void> original, @Local List<BlockPos> toBlow) {
        List<BlockPos> previous = this.versionunbound$blocks;
        this.versionunbound$blocks = toBlow;
        try {
            original.call(explosion);
        } finally {
            this.versionunbound$blocks = previous;
        }
    }

    @ModifyExpressionValue(method = "hurtEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<Entity> versionunbound$detonate(List<Entity> list) {
        EventHooks.onExplosionDetonate(this.level, (ServerExplosion) (Object) this, list, this.versionunbound$blocks);
        return list;
    }

    // The knockback local is what vanilla pushes the entity with and remembers for a player's explode packet.
    @ModifyExpressionValue(method = "hurtEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 versionunbound$knockback(Vec3 knockback, @Local Entity entity) {
        return EventHooks.getExplosionKnockback(this.level, (ServerExplosion) (Object) this, entity, knockback, this.versionunbound$blocks);
    }
}
