package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * NeoForge's patch to {@code FireChargeItem}: lighting a block asks {@code getToolModifiedState} with
 * {@code FIRESTARTER_LIGHT}, as for flint and steel. An {@code @Overwrite} with NeoForge's patched body. (Its
 * {@code canPerformAction} is in ItemAbilityItemsMixin.)
 */
@Mixin(FireChargeItem.class)
abstract class FireChargeItemMixin {
    @Shadow
    protected abstract void playSound(Level level, BlockPos pos);

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    public InteractionResult useOn(final UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState blockState = level.getBlockState(pos);
        boolean used = false;
        BlockState blockstate2 = blockState.getToolModifiedState(context, ItemAbilities.FIRESTARTER_LIGHT, false);
        if (blockstate2 == null) {
            pos = pos.relative(context.getClickedFace());
            if (BaseFireBlock.canBePlacedAt(level, pos, context.getHorizontalDirection())) {
                this.playSound(level, pos);
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
                level.gameEvent(context.getPlayer(), GameEvent.BLOCK_PLACE, pos);
                used = true;
            }
        } else {
            this.playSound(level, pos);
            level.setBlockAndUpdate(pos, blockstate2);
            level.gameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, pos);
            used = true;
        }

        if (used) {
            context.getItemInHand().shrink(1);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.FAIL;
        }
    }
}
