package net.salutaryfish.versionunbound.testmod.hooks;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * The item extension's hooks: crafting remainders ({@code getCraftingRemainder}, on the stack: Fabric API's and
 * NeoForge's defaults meet there), furnace burn times ({@code getBurnTime}), an item entity's tick
 * ({@code onEntityItemUpdate}), a swing ({@code onEntitySwing}) and a stack's max stack size ({@code getMaxStackSize}).
 */
final class ItemChecks {
    private ItemChecks() {}

    static void check(Checks checks, ServerLevel level, BlockPos pos) {
        try {
            checkRemainders(checks);
            checkFurnace(checks, level, pos);
            checkEntities(checks, level, pos);
            checkStackSize(checks);
        } finally {
            HooksMod.clear(level, pos);
        }
    }

    private static boolean isTemplate(ItemStackTemplate template, net.minecraft.world.item.Item item) {
        return template != null && template.item().value() == item;
    }

    private static void checkRemainders(Checks checks) {
        checks.check("a stack's crafting remainder: vanilla's, and a mod item's stack-aware one", () ->
                isTemplate(new ItemStack(Items.LAVA_BUCKET).getCraftingRemainder(), Items.BUCKET)
                        && isTemplate(new ItemStack(HooksMod.EMBER.get()).getCraftingRemainder(), Items.STICK)
                        && new ItemStack(Items.STONE).getCraftingRemainder() == null);
        checks.check("a crafting recipe's remainders ask the stack", () -> {
            var remaining = CraftingRecipe.defaultCraftingReminder(CraftingInput.of(1, 1, List.of(new ItemStack(HooksMod.EMBER.get()))));
            return remaining.size() == 1 && remaining.getFirst().is(Items.STICK);
        });
    }

    private static void checkFurnace(Checks checks, ServerLevel level, BlockPos pos) {
        HooksMod.clear(level, pos);
        level.setBlock(pos, Blocks.FURNACE.defaultBlockState(), 2 | 16);
        if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
            checks.check("a furnace block entity", false);
            return;
        }
        checks.check("a furnace's fuel slot takes a mod item with a burn time (getBurnTime), not one without", furnace.canPlaceItem(1, new ItemStack(HooksMod.EMBER.get()))
                && !furnace.canPlaceItem(1, new ItemStack(Items.STONE)) && furnace.canPlaceItem(1, new ItemStack(Items.COAL)));
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(HooksMod.EMBER.get()));
        AbstractFurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), furnace);
        CompoundTag saved = furnace.saveWithoutMetadata(level.registryAccess());
        checks.check("a furnace burns a mod item for its getBurnTime, and leaves its stack-aware remainder", saved.getIntOr("lit_total_time", 0) == 1234
                && furnace.getItem(1).is(Items.STICK));
        furnace.clearContent();
    }

    private static void checkEntities(Checks checks, ServerLevel level, BlockPos pos) {
        HookItems.HEARD.clear();
        ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, new ItemStack(HooksMod.WAND.get()));
        item.tick();
        checks.check("an item entity's tick asks onEntityItemUpdate first, which can skip the rest", item.getAge() == 0
                && HookItems.HEARD.equals(List.of("onEntityItemUpdate")));

        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        zombie.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HooksMod.WAND.get()));
        HookItems.HEARD.clear();
        zombie.swing(InteractionHand.MAIN_HAND);
        boolean canceled = !zombie.swinging && HookItems.HEARD.equals(List.of("onEntitySwing " + InteractionHand.MAIN_HAND));
        zombie.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        zombie.swing(InteractionHand.MAIN_HAND);
        checks.check("a swing asks the stack's onEntitySwing, which can cancel it", canceled && zombie.swinging);
    }

    private static void checkStackSize(Checks checks) {
        ItemStack plain = new ItemStack(HooksMod.POUCH.get());
        ItemStack named = new ItemStack(HooksMod.POUCH.get());
        named.set(DataComponents.CUSTOM_NAME, Component.literal("hooks"));
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, named.copyWithCount(64));
        checks.check("a stack's max stack size asks the item's stack-aware getMaxStackSize, and a container holds no more",
                plain.getMaxStackSize() == 64 && named.getMaxStackSize() == 16 && named.isStackable() && container.getItem(0).getCount() == 16
                        && new ItemStack(Items.ENDER_PEARL).getMaxStackSize() == 16);
    }
}
