package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code FarmlandBlock}: an entity falling on farmland tramples it when its {@code canTrample} says
 * so (vanilla's condition, moved to the entity) and {@code BlockEvent.FarmlandTrampleEvent} isn't canceled. Not ported
 * from this patch: {@code isNearWater}'s hydration check through the block state extension ({@code canBeHydrated}, step
 * 5) and {@code FarmlandWaterManager}'s water tickets.
 */
@Mixin(FarmlandBlock.class)
abstract class FarmlandBlockMixin {
    // Neo: Move logic to Entity#canTrample. NeoForge's condition, `level instanceof ServerLevel serverLevel &&
    // onFarmlandTrample(...)`, replaces all of vanilla's. Vanilla's starts with the same type check, so that check runs
    // NeoForge's condition (and the trampling it leads to) and answers false, which skips the rest of vanilla's.
    @Definition(id = "level", local = @Local(type = Level.class, argsOnly = true))
    @Definition(id = "ServerLevel", type = ServerLevel.class)
    @Expression("level instanceof ServerLevel")
    @ModifyExpressionValue(method = "fallOn", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$farmlandTrample(boolean isServerLevel, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockState state,
            @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) Entity entity, @Local(argsOnly = true) double fallDistance) {
        if (isServerLevel && CommonHooks.onFarmlandTrample((ServerLevel) level, pos, Blocks.DIRT.defaultBlockState(), fallDistance, entity)) {
            FarmlandBlock.turnToDirt(entity, state, level, pos);
        }
        return false;
    }
}
