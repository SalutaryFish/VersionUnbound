package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Ravager;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code Ravager}: {@code EntityMobGriefingEvent} before a ravager breaks the leaves it walks into,
 * and when it roars (which decides whom the roar hurts).
 */
@Mixin(Ravager.class)
abstract class RavagerMixin {
    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "aiStep", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$breakLeavesGriefing(Object mobGriefing) {
        Ravager ravager = (Ravager) (Object) this;
        return EventHooks.canEntityGrief((ServerLevel) ravager.level(), ravager);
    }

    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "roar", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$roarGriefing(Object mobGriefing) {
        Ravager ravager = (Ravager) (Object) this;
        return EventHooks.canEntityGrief((ServerLevel) ravager.level(), ravager);
    }
}
