package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code WitherBoss}: {@code EntityMobGriefingEvent} before a hurt wither breaks the blocks around
 * it, and for each block the block's {@code canEntityDestroy} (in place of {@code canDestroy}, which its default asks)
 * and {@code LivingDestroyBlockEvent} (cancelable: the block stays). Not ported from this patch:
 * {@code MobDespawnEvent} in {@code checkDespawn}.
 */
@Mixin(WitherBoss.class)
abstract class WitherBossMixin {
    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "customServerAiStep", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$canEntityGrief(Object mobGriefing, @Local(argsOnly = true) ServerLevel level) {
        return EventHooks.canEntityGrief(level, (WitherBoss) (Object) this);
    }

    @WrapOperation(method = "customServerAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/boss/wither/WitherBoss;canDestroy(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean versionunbound$canEntityDestroy(BlockState state, Operation<Boolean> original, @Local(name = "blockPos") BlockPos blockPos) {
        WitherBoss wither = (WitherBoss) (Object) this;
        return state.canEntityDestroy(wither.level(), blockPos, wither) && EventHooks.onEntityDestroyBlock(wither, blockPos, state);
    }
}
