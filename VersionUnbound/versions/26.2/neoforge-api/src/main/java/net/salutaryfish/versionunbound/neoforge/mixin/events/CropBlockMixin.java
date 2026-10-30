package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code CropBlock}: the crop growth events in {@code randomTick} ({@code CropGrowEvent.Pre} decides
 * with the growth roll as the default, {@code Post} after the crop grew), the soil checks in {@code getGrowthSpeed}
 * (the block extension's {@code canSustainPlant} and {@code isFertile}; see {@code CropBlockPatch} for why they go into
 * vanilla's {@code getGrowthSpeed(Block, ...)}), and {@code EntityMobGriefingEvent} before a ravager tramples a crop.
 * (NeoForge doesn't patch {@code PitcherCropBlock}'s copy of this check, which reads the game rule.) Not ported from
 * this patch: the soil check in {@code canSurvive} and the loaded area check in {@code randomTick} (both need
 * extension methods that come with Phase 2 step 5).
 */
@Mixin(CropBlock.class)
abstract class CropBlockMixin {
    private static final String GET_GROWTH_SPEED = "getGrowthSpeed(Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F";

    @Definition(id = "nextInt", method = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    @Expression("?.nextInt(?) == 0")
    @ModifyExpressionValue(method = "randomTick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$canCropGrow(boolean grows, @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) ServerLevel level,
            @Local(argsOnly = true) BlockPos pos) {
        return CommonHooks.canCropGrow(level, pos, state, grows);
    }

    @Inject(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            shift = At.Shift.AFTER))
    private void versionunbound$fireCropGrowPost(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        CommonHooks.fireCropGrowPost(level, pos, state);
    }

    // NeoForge asks the soil block first; its default (DEFAULT) leaves the tag check to decide. NeoForge passes the soil's
    // state as the plant.
    @Definition(id = "is", method = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z")
    @Definition(id = "GROWS_CROPS", field = "Lnet/minecraft/tags/BlockTags;GROWS_CROPS:Lnet/minecraft/tags/TagKey;")
    @Expression("?.is(GROWS_CROPS)")
    @ModifyExpressionValue(method = GET_GROWTH_SPEED, at = @At("MIXINEXTRAS:EXPRESSION"))
    private static boolean versionunbound$canSustainPlant(boolean growsCrops, @Local(argsOnly = true) BlockGetter level, @Local(name = "below") BlockPos below,
            @Local(name = "xx") int xx, @Local(name = "zz") int zz, @Local(name = "blockState") BlockState blockState) {
        TriState soilDecision = blockState.canSustainPlant(level, below.offset(xx, 0, zz), Direction.UP, blockState);
        return soilDecision.isDefault() ? growsCrops : soilDecision.isTrue();
    }

    // NeoForge's isFertile in place of vanilla's moisture check (its default asks farmland for its moisture). NeoForge
    // passes the position next to the crop, not the soil's.
    @Definition(id = "getValueOrElse", method = "Lnet/minecraft/world/level/block/state/BlockState;getValueOrElse(Lnet/minecraft/world/level/block/state/properties/Property;Ljava/lang/Comparable;)Ljava/lang/Comparable;")
    @Definition(id = "MOISTURE", field = "Lnet/minecraft/world/level/block/FarmlandBlock;MOISTURE:Lnet/minecraft/world/level/block/state/properties/IntegerProperty;")
    @Definition(id = "Integer", type = Integer.class)
    @Expression("(Integer) ?.getValueOrElse(MOISTURE, ?) > 0")
    @ModifyExpressionValue(method = GET_GROWTH_SPEED, at = @At("MIXINEXTRAS:EXPRESSION"))
    private static boolean versionunbound$isFertile(boolean moist, @Local(argsOnly = true) BlockGetter level, @Local(argsOnly = true) BlockPos pos,
            @Local(name = "xx") int xx, @Local(name = "zz") int zz, @Local(name = "blockState") BlockState blockState) {
        return blockState.isFertile(level, pos.offset(xx, 0, zz));
    }

    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "entityInside", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$canEntityGrief(Object mobGriefing, @Local(argsOnly = true) Level level, @Local(argsOnly = true) Entity entity) {
        return EventHooks.canEntityGrief((ServerLevel) level, entity);
    }
}
