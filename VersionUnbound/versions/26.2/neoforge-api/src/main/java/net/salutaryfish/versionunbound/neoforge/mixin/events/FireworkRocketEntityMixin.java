package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NeoForge's patch to {@code FireworkRocketEntity}: an {@code onHit} override that fires {@code ProjectileImpactEvent}
 * and skips the hit (the rocket's explosion) when the event is canceled.
 */
@Mixin(FireworkRocketEntity.class)
abstract class FireworkRocketEntityMixin extends Projectile {
    private FireworkRocketEntityMixin(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == HitResult.Type.MISS || !EventHooks.onProjectileImpact(this, result)) {
            super.onHit(result);
        }
    }
}
