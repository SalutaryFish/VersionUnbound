package net.salutaryfish.versionunbound.testmod.events;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.LivingEntityInvoker;

/**
 * {@code PlayerDestroyItemEvent} on the server, at each of NeoForge's sites there: a pickaxe breaking on the block it
 * mines, a sword breaking in an attack, wheat used up feeding a cow (the cow's own interaction) and a name tag used up
 * on an armor stand (the item's interaction), a bow breaking as it shoots on release, and a fishing rod breaking as it
 * reels in. The player plays these in survival, since nothing wears out in creative; its game mode and inventory are
 * put back afterwards. The client's site is checked in {@code ClientEvents}.
 */
final class DestroyItemScenario {
    private final Checks checks;
    private final List<PlayerDestroyItemEvent> events = new CopyOnWriteArrayList<>();
    private ServerPlayer player;

    DestroyItemScenario(Checks checks) {
        this.checks = checks;
    }

    void destroyItem(PlayerDestroyItemEvent event) {
        events.add(event);
    }

    void runWithPlayer(ServerPlayer player, ServerLevel level, BlockPos base) {
        this.player = player;
        GameType gameType = player.gameMode.getGameModeForPlayer();
        Inventory inventory = player.getInventory();
        List<ItemStack> savedInventory = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            savedInventory.add(inventory.getItem(i).copy());
        }
        player.setGameMode(GameType.SURVIVAL);
        try {
            BlockPos glassPos = base.offset(0, 0, -2);
            expect("PlayerDestroyItemEvent fires when a tool breaks on the block it mines", Items.WOODEN_PICKAXE, () -> {
                // Glass, which drops nothing.
                level.setBlock(glassPos, Blocks.GLASS.defaultBlockState(), 3);
                player.setItemInHand(InteractionHand.MAIN_HAND, worn(Items.WOODEN_PICKAXE));
                player.gameMode.destroyBlock(glassPos);
            });
            level.removeBlock(glassPos, false);

            // A zombie, which survives a wooden sword's hit (a chicken would die and drop its loot).
            Entity target = spawn(level, EntityTypes.ZOMBIE, base);
            expect("PlayerDestroyItemEvent fires when a weapon breaks in an attack", Items.WOODEN_SWORD, () -> {
                player.setItemInHand(InteractionHand.MAIN_HAND, worn(Items.WOODEN_SWORD));
                player.attack(target);
            });
            target.discard();

            Entity cow = spawn(level, EntityTypes.COW, base);
            expect("PlayerDestroyItemEvent fires when an entity's interaction uses up the item", Items.WHEAT, () -> {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT));
                player.interactOn(cow, InteractionHand.MAIN_HAND, Vec3.ZERO);
            });
            cow.discard();

            Entity stand = spawn(level, EntityTypes.ARMOR_STAND, base);
            expect("PlayerDestroyItemEvent fires when an item's interaction with an entity uses it up", Items.NAME_TAG, () -> {
                ItemStack tag = new ItemStack(Items.NAME_TAG);
                tag.set(DataComponents.CUSTOM_NAME, Component.literal("events"));
                player.setItemInHand(InteractionHand.MAIN_HAND, tag);
                player.interactOn(stand, InteractionHand.MAIN_HAND, Vec3.ZERO);
            });
            stand.discard();

            expect("PlayerDestroyItemEvent fires when releasing a used item breaks it", Items.BOW, () -> {
                player.setItemInHand(InteractionHand.MAIN_HAND, worn(Items.BOW));
                inventory.setItem(9, new ItemStack(Items.ARROW));
                player.startUsingItem(InteractionHand.MAIN_HAND);
                // Drawn for a second, so the bow shoots (and takes its damage) on release.
                ((LivingEntityInvoker) player).events$setUseItemRemaining(player.getUseItem().getUseDuration(player) - 20);
                player.releaseUsingItem();
            });
            level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(8.0), arrow -> arrow.getOwner() == player).forEach(Entity::discard);

            expect("PlayerDestroyItemEvent fires when reeling in breaks a fishing rod", Items.FISHING_ROD, () -> {
                player.setItemInHand(InteractionHand.MAIN_HAND, worn(Items.FISHING_ROD));
                // A hook on the ground (never added to the level) costs the rod 2 durability when reeled in.
                FishingHook hook = new FishingHook(player, level, 0, 0);
                hook.setOnGround(true);
                player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
            });
        } finally {
            player.setGameMode(gameType);
            inventory.clearContent();
            for (int i = 0; i < savedInventory.size(); i++) {
                inventory.setItem(i, savedInventory.get(i));
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }

    /** Runs the action and expects exactly one event, for the player's main hand and a copy of the item before it went. */
    private void expect(String what, Item item, Runnable action) {
        checks.check(what, () -> {
            events.clear();
            action.run();
            if (events.size() != 1) return false;
            PlayerDestroyItemEvent event = events.get(0);
            return event.getEntity() == player && event.getHand() == InteractionHand.MAIN_HAND && event.getOriginal().is(item)
                    && player.getMainHandItem().isEmpty();
        });
    }

    /** A stack of the item with one use left. */
    private static ItemStack worn(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(stack.getMaxDamage() - 1);
        return stack;
    }

    private static Entity spawn(ServerLevel level, EntityType<?> type, BlockPos pos) {
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        entity.setPos(Vec3.atBottomCenterOf(pos));
        level.addFreshEntity(entity);
        return entity;
    }
}
