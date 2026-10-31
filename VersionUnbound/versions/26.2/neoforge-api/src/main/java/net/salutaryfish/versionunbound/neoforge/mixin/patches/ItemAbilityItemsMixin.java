package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The {@code canPerformAction} overrides NeoForge's patches add to vanilla's tool items, each answering with its
 * {@code ItemAbilities.DEFAULT_*_ACTIONS} set. One mixin adds the same method to all ten classes; none of them extends
 * another, so {@code this} picks the set.
 */
@Mixin({ AxeItem.class, ShovelItem.class, HoeItem.class, ShearsItem.class, FlintAndSteelItem.class, FireChargeItem.class, BrushItem.class,
        TridentItem.class, FishingRodItem.class, SpyglassItem.class })
abstract class ItemAbilityItemsMixin {
    public boolean canPerformAction(ItemInstance stack, ItemAbility itemAbility) {
        Object self = this;
        if (self instanceof AxeItem) return ItemAbilities.DEFAULT_AXE_ACTIONS.contains(itemAbility);
        if (self instanceof ShovelItem) return ItemAbilities.DEFAULT_SHOVEL_ACTIONS.contains(itemAbility);
        if (self instanceof HoeItem) return ItemAbilities.DEFAULT_HOE_ACTIONS.contains(itemAbility);
        if (self instanceof ShearsItem) return ItemAbilities.DEFAULT_SHEARS_ACTIONS.contains(itemAbility);
        if (self instanceof FlintAndSteelItem) return ItemAbilities.DEFAULT_FLINT_ACTIONS.contains(itemAbility);
        if (self instanceof FireChargeItem) return ItemAbilities.DEFAULT_FIRECHARGE_ACTIONS.contains(itemAbility);
        if (self instanceof BrushItem) return ItemAbilities.DEFAULT_BRUSH_ACTIONS.contains(itemAbility);
        if (self instanceof TridentItem) return ItemAbilities.DEFAULT_TRIDENT_ACTIONS.contains(itemAbility);
        if (self instanceof FishingRodItem) return ItemAbilities.DEFAULT_FISHING_ROD_ACTIONS.contains(itemAbility);
        return ItemAbilities.DEFAULT_SPYGLASS_ACTIONS.contains(itemAbility);
    }
}
