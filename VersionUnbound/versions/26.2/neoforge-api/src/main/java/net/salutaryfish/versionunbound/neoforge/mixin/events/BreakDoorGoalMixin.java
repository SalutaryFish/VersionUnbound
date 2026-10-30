package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.DoorInteractGoal;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code BreakDoorGoal}: {@code CommonHooks.canEntityDestroy} decides whether the mob may break the
 * door ({@code EntityMobGriefingEvent}, the block's {@code canEntityDestroy}, {@code LivingDestroyBlockEvent}) in place
 * of the mob griefing game rule. (The mixin extends {@code DoorInteractGoal} to reach the mob and the door.)
 */
@Mixin(BreakDoorGoal.class)
abstract class BreakDoorGoalMixin extends DoorInteractGoal {
    private BreakDoorGoalMixin(Mob mob) {
        super(mob);
    }

    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "canUse", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$canEntityDestroy(Object mobGriefing) {
        return CommonHooks.canEntityDestroy((ServerLevel) this.mob.level(), this.doorPos, this.mob);
    }
}
