package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.allay.Allay;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code Allay}: {@code EntityMobGriefingEvent} before an allay picks up an item. NeoForge moves
 * the check to the end of {@code wantsToPickUp}'s condition, so the event fires only for an item the allay wants:
 * vanilla's check answers true, and the event decides after the rest.
 */
@Mixin(Allay.class)
abstract class AllayMixin {
    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "wantsToPickUp", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$skipGriefing(Object mobGriefing) {
        return true;
    }

    @ModifyReturnValue(method = "wantsToPickUp", at = @At("RETURN"))
    private boolean versionunbound$canEntityGrief(boolean wantsToPickUp, @Local(argsOnly = true) ServerLevel level) {
        return wantsToPickUp && EventHooks.canEntityGrief(level, (Allay) (Object) this);
    }
}
