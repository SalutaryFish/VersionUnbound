package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge's patch to {@code AxeItem}: stripping, scraping and removing wax ask the block state's
 * {@code getToolModifiedState} (which fires {@code BlockToolModificationEvent}, then asks the block) instead of vanilla's
 * maps. NeoForge gives {@code evaluateNewBlockState} the context as a fifth argument; here useOn's call of vanilla's
 * four-argument one is replaced with NeoForge's body, which gets the context from useOn. Fabric API's custom stripping
 * transformers ({@code StrippableBlockRegistry}, through vanilla's {@code getStripped}) no longer apply; its plain
 * registrations go into {@code STRIPPABLES}, which the block extension's default still reads.
 */
@Mixin(AxeItem.class)
abstract class AxeItemMixin {
    @Shadow
    private static void spawnSoundAndParticle(Level level, BlockPos pos, @Nullable Player player, BlockState oldState, SoundEvent soundEvent, int particle) {
        throw new AssertionError();
    }

    @WrapOperation(method = "useOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/AxeItem;evaluateNewBlockState(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/state/BlockState;)Ljava/util/Optional;"))
    private Optional<BlockState> versionunbound$evaluateNewBlockState(AxeItem self, Level level, BlockPos pos, @Nullable Player player, BlockState oldState,
            Operation<Optional<BlockState>> original, @Local(argsOnly = true) UseOnContext context) {
        Optional<BlockState> strippedBlock = Optional.ofNullable(oldState.getToolModifiedState(context, ItemAbilities.AXE_STRIP, false));
        if (strippedBlock.isPresent()) {
            level.playSound(player, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return strippedBlock;
        } else {
            Optional<BlockState> scrapedBlock = Optional.ofNullable(oldState.getToolModifiedState(context, ItemAbilities.AXE_SCRAPE, false));
            if (scrapedBlock.isPresent()) {
                spawnSoundAndParticle(level, pos, player, oldState, SoundEvents.AXE_SCRAPE, 3005);
                return scrapedBlock;
            } else {
                Optional<BlockState> waxoffBlock = Optional.ofNullable(oldState.getToolModifiedState(context, ItemAbilities.AXE_WAX_OFF, false));
                if (waxoffBlock.isPresent()) {
                    spawnSoundAndParticle(level, pos, player, oldState, SoundEvents.AXE_WAX_OFF, 3004);
                    return waxoffBlock;
                } else {
                    return Optional.empty();
                }
            }
        }
    }
}
