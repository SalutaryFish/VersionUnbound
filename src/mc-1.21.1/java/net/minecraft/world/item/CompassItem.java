package net.minecraft.world.item;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class CompassItem extends Item {
    public CompassItem(Item.Properties properties) {
        super(properties);
    }

    @Nullable
    public static GlobalPos getSpawnPosition(Level level) {
        return level.dimensionType().natural() ? GlobalPos.of(level.dimension(), level.getSharedSpawnPos()) : null;
    }

    /**
     * Returns {@code true} if this item has an enchantment glint. By default, this returns <code>stack.isItemEnchanted()</code>, but other items can override it (for instance, written books always return true).
     *
     * Note that if you override this method, you generally want to also call the super version (on {@link Item}) to get the glint for enchanted items. Of course, that is unnecessary if the overwritten version always returns true.
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(DataComponents.LODESTONE_TRACKER) || super.isFoil(stack);
    }

    /**
     * Called each tick as long the item is in a player's inventory. Used by maps to check if it's in a player's hand and update its contents.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int itemSlot, boolean isSelected) {
        if (level instanceof ServerLevel serverLevel) {
            LodestoneTracker lodestoneTracker = stack.get(DataComponents.LODESTONE_TRACKER);
            if (lodestoneTracker != null) {
                LodestoneTracker lodestoneTracker2 = lodestoneTracker.tick(serverLevel);
                if (lodestoneTracker2 != lodestoneTracker) {
                    stack.set(DataComponents.LODESTONE_TRACKER, lodestoneTracker2);
                }
            }
        }
    }

    /**
     * Called when this item is used when targeting a Block
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos blockPos = context.getClickedPos();
        Level level = context.getLevel();
        if (!level.getBlockState(blockPos).is(Blocks.LODESTONE)) {
            return super.useOn(context);
        }

        level.playSound((Player)null, blockPos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        Player player = context.getPlayer();
        ItemStack itemStack = context.getItemInHand();
        boolean bl = !player.hasInfiniteMaterials() && itemStack.getCount() == 1;
        LodestoneTracker lodestoneTracker = new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), blockPos)), true);
        if (bl) {
            itemStack.set(DataComponents.LODESTONE_TRACKER, lodestoneTracker);
        } else {
            ItemStack itemStack2 = itemStack.transmuteCopy(Items.COMPASS, 1);
            itemStack.consume(1, player);
            itemStack2.set(DataComponents.LODESTONE_TRACKER, lodestoneTracker);
            if (!player.getInventory().add(itemStack2)) {
                player.drop(itemStack2, false);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Returns the unlocalized name of this item. This version accepts an ItemStack so different stacks can have different names based on their damage or NBT.
     */
    @Override
    public String getDescriptionId(ItemStack stack) {
        return stack.has(DataComponents.LODESTONE_TRACKER) ? "item.minecraft.lodestone_compass" : super.getDescriptionId(stack);
    }
}
