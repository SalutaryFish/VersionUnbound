package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code TurtleEggBlock}: {@code EntityMobGriefingEvent} before a mob (not a player) breaks turtle
 * eggs by stepping or falling on them.
 */
@Mixin(TurtleEggBlock.class)
abstract class TurtleEggBlockMixin {
    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "canDestroyEgg", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$canEntityGrief(Object mobGriefing, @Local(argsOnly = true) ServerLevel level, @Local(argsOnly = true) Entity entity) {
        return EventHooks.canEntityGrief(level, entity);
    }
}
