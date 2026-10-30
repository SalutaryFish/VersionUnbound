package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.EatBlockGoal;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code EatBlockGoal}: {@code EntityMobGriefingEvent} before a sheep eats grass, both short grass
 * and a grass block (the sheep eats either way, as in vanilla).
 */
@Mixin(EatBlockGoal.class)
abstract class EatBlockGoalMixin {
    @Shadow
    @Final
    private Mob mob;

    @Shadow
    @Final
    private Level level;

    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "tick", at = @At("MIXINEXTRAS:EXPRESSION"), require = 2)
    private Object versionunbound$canEntityGrief(Object mobGriefing) {
        return EventHooks.canEntityGrief((ServerLevel) this.level, this.mob);
    }
}
