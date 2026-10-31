package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * NeoForge's patch to {@code ShearsItem.useOn}: trimming a growing plant asks {@code getToolModifiedState} with
 * {@code SHEARS_TRIM} (the block extension plays the sound and answers the plant's max age state). An
 * {@code @Overwrite} with NeoForge's patched body. Not yet: NeoForge's {@code interactLivingEntity} for
 * {@code IShearable} (with the entity batch). (Its {@code canPerformAction} is in ItemAbilityItemsMixin.)
 */
@Mixin(ShearsItem.class)
abstract class ShearsItemMixin extends Item {
    private ShearsItemMixin(final Properties properties) {
        super(properties);
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    @Override
    public InteractionResult useOn(final UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        BlockState newState = state.getToolModifiedState(context, ItemAbilities.SHEARS_TRIM, false);
        if (newState != null) {
            Player player = context.getPlayer();
            ItemStack itemInHand = context.getItemInHand();
            if (player instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, itemInHand);
            }

            level.setBlockAndUpdate(pos, newState);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(context.getPlayer(), newState));
            if (player != null) {
                itemInHand.hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
            }

            return InteractionResult.SUCCESS;
        } else {
            return super.useOn(context);
        }
    }
}
