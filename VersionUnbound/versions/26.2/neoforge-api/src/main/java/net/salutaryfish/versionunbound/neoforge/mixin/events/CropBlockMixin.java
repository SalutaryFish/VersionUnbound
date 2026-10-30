package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code CropBlock}: {@code EntityMobGriefingEvent} before a ravager tramples a crop. (NeoForge
 * doesn't patch {@code PitcherCropBlock}'s copy of this check, which reads the game rule.) Not ported from this patch:
 * the crop growth events in {@code randomTick}, the soil checks in {@code getGrowthSpeed} and {@code canSurvive}, and
 * the loaded area check in {@code randomTick}.
 */
@Mixin(CropBlock.class)
abstract class CropBlockMixin {
    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "entityInside", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$canEntityGrief(Object mobGriefing, @Local(argsOnly = true) Level level, @Local(argsOnly = true) Entity entity) {
        return EventHooks.canEntityGrief((ServerLevel) level, entity);
    }
}
