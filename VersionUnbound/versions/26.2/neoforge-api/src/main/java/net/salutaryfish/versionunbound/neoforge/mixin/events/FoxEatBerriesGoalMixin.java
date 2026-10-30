package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.fox.Fox;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code Fox.FoxEatBerriesGoal}: {@code EntityMobGriefingEvent} before a fox picks berries.
 */
@Mixin(targets = "net.minecraft.world.entity.animal.fox.Fox$FoxEatBerriesGoal")
abstract class FoxEatBerriesGoalMixin {
    @Shadow
    @Final
    Fox this$0;

    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "onReachedTarget", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$canEntityGrief(Object mobGriefing) {
        return EventHooks.canEntityGrief((ServerLevel) this.this$0.level(), this.this$0);
    }
}
