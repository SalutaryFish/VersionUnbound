package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code HarvestFarmland}: {@code EntityMobGriefingEvent} before a farmer starts harvesting. Not
 * ported from this patch: NeoForge's villager farmlands tag and its {@code SpecialPlantable} items.
 */
@Mixin(HarvestFarmland.class)
abstract class HarvestFarmlandMixin {
    // Not the bridge method, which only calls this one.
    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;)Z", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$canEntityGrief(Object mobGriefing, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) Villager body) {
        return EventHooks.canEntityGrief(level, body);
    }
}
