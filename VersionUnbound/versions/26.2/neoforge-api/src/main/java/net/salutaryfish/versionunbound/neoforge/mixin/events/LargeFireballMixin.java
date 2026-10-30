package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code LargeFireball}: {@code EntityMobGriefingEvent} for the owner (a ghast; without an owner, the
 * game rule) decides whether the explosion sets fires. The explosion then asks again, about the fireball, whether it
 * breaks blocks ({@code ServerLevelMixin}); NeoForge's patch notes that it fires twice.
 */
@Mixin(LargeFireball.class)
abstract class LargeFireballMixin {
    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "onHit", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$canEntityGrief(Object mobGriefing) {
        LargeFireball fireball = (LargeFireball) (Object) this;
        return EventHooks.canEntityGrief((ServerLevel) fireball.level(), fireball.getOwner());
    }
}
