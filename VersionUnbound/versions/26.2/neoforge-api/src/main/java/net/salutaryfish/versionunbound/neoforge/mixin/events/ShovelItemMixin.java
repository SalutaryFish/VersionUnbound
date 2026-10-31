package net.salutaryfish.versionunbound.neoforge.mixin.events;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * NeoForge's patch to {@code ShovelItem}: flattening and dousing ask the block state's {@code getToolModifiedState}
 * (which fires {@code BlockToolModificationEvent}, then asks the block; dousing a campfire happens there). An
 * {@code @Overwrite} with NeoForge's patched body, so that mods' mixins into it find NeoForge's shape.
 */
@Mixin(ShovelItem.class)
abstract class ShovelItemMixin {
    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    public InteractionResult useOn(final UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState blockState = level.getBlockState(pos);
        if (context.getClickedFace() == Direction.DOWN) {
            return InteractionResult.PASS;
        }

        Player player = context.getPlayer();
        BlockState newState = blockState.getToolModifiedState(context, ItemAbilities.SHOVEL_FLATTEN, false);
        BlockState updatedState = null;
        if (newState != null && level.getBlockState(pos.above()).isAir()) {
            level.playSound(player, pos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0F, 1.0F);
            updatedState = newState;
        } else if ((updatedState = blockState.getToolModifiedState(context, ItemAbilities.SHOVEL_DOUSE, false)) != null) {
            if (!level.isClientSide()) {
                level.levelEvent(null, 1009, pos, 0);
            }
        }

        if (updatedState != null) {
            if (!level.isClientSide()) {
                level.setBlock(pos, updatedState, 11);
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, updatedState));
                if (player != null) {
                    context.getItemInHand().hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
                }
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }
}
