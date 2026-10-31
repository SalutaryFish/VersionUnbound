package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code EnchantingTableBlock.isValidBookShelf}: a bookshelf is any block with enchanting power,
 * not only the power provider tag's (asked through Fabric API's {@code getProvidedEnchantmentPower}, which answers
 * NeoForge's {@code getEnchantPowerBonus}: EnchantPowerMixin).
 */
@Mixin(EnchantingTableBlock.class)
abstract class EnchantingTableBlockMixin {
    @WrapOperation(method = "isValidBookShelf", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z", ordinal = 0))
    private static boolean versionunbound$hasEnchantPower(BlockState state, TagKey<?> provider, Operation<Boolean> original, Level level, BlockPos pos, BlockPos offset) {
        return state.getProvidedEnchantmentPower(level, pos.offset(offset)) != 0;
    }
}
