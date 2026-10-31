package net.salutaryfish.versionunbound.testmod.events;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementProgressEvent.ProgressType;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

/**
 * Player events on the server, with the player (from {@code PlayerScenario}): {@code PlayerContainerEvent.Open} and
 * {@code Close} (a menu opened, another opened over it, closed; a tamed horse's inventory), {@code ItemCraftedEvent} and
 * {@code ItemSmeltedEvent} (a crafting result slot and a furnace result slot taken from by hand), advancement events (a
 * criterion granted and revoked: {@code AdvancementProgressEvent}, and {@code AdvancementEarnEvent} for an advancement
 * with a display but, as in NeoForge, not for a recipe advancement, which has none), {@code PlayerWakeUpEvent} (the
 * player asleep and woken), {@code BreakSpeed} and {@code HarvestCheck} (a block's destroy progress; obsidian's tool
 * modifier; stone broken in survival with an empty hand, which drops something only when the check allows it),
 * {@code BonemealEvent} (bone meal used on wheat; canceled, counting as used or not; vanilla's {@code growCrop} without a
 * player; NeoForge's static {@code applyBonemeal}), and {@code StartTracking}/{@code StopTracking} for an armor stand
 * added next to the player and discarded once the player tracks it (or after 90 ticks). The listeners only record events for the player while the scenario runs (the tracking
 * ones: for the stand).
 */
final class PlayerEventScenario {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Identifier MINE_STONE = Identifier.withDefaultNamespace("story/mine_stone");
    private static final Identifier CRAFTING_TABLE_RECIPE = Identifier.withDefaultNamespace("recipes/decorations/crafting_table");

    // The level pairs an entity with a player once the player's client has its chunk; the player scenario has just taken
    // the player to the nether and back, so that takes some ticks (a few on a fast machine, more than ten in a slower cloud
    // container). The stand is checked and removed once StartTracking came, or after this many ticks.
    private static final int TRACKING_TIMEOUT = 90;

    private final Checks playerChecks;
    private volatile ServerPlayer player;
    private final List<Object> events = new CopyOnWriteArrayList<>();
    // By UUID: the player scenario respawns the player right after this one, which replaces the player object.
    private volatile UUID trackingPlayer;
    private ArmorStand stand;
    private final List<Object> tracking = new CopyOnWriteArrayList<>();
    private int ticksSinceStand;
    // Breaking and bone meal: the positions the listeners look at, and their answers.
    private final Set<BlockPos> positions = Collections.synchronizedSet(new HashSet<>());
    private volatile Float speedFactor;
    private volatile boolean cancelSpeed;
    private volatile Boolean harvestAnswer;
    private volatile Boolean bonemealSuccess;

    PlayerEventScenario(Checks playerChecks) {
        this.playerChecks = playerChecks;
    }

    void containerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() == player) events.add(new Opened(event.getContainer()));
    }

    void containerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() == player) events.add(new Closed(event.getContainer()));
    }

    void itemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() == player) events.add(new Crafted(event.getCrafting(), event.getInventory()));
    }

    void itemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() == player) events.add(new Smelted(event.getSmelting(), event.getAmountRemoved()));
    }

    void advancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() == player) events.add(new Earned(event.getAdvancement().id()));
    }

    void advancementProgress(AdvancementEvent.AdvancementProgressEvent event) {
        if (event.getEntity() != player) return;
        events.add(new Progressed(event.getAdvancement().id(), event.getCriterionName(), event.getProgressType(), event.getAdvancementProgress().isDone()));
    }

    void wakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() == player) events.add(new WokeUp(event.wakeImmediately(), event.updateLevel(), event.getEntity().isSleeping()));
    }

    void breakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() != player || event.getPosition().filter(positions::contains).isEmpty() && !cancelSpeed) return;
        events.add(new Speed(event.getState(), event.getOriginalSpeed(), event.getPosition()));
        Float factor = speedFactor;
        if (factor != null) event.setNewSpeed(event.getOriginalSpeed() * factor);
        if (cancelSpeed) event.setCanceled(true);
    }

    void harvestCheck(PlayerEvent.HarvestCheck event) {
        if (event.getEntity() != player || !positions.contains(event.getPos())) return;
        events.add(new Harvest(event.getTargetBlock(), event.canHarvest(), event.getPos()));
        Boolean answer = harvestAnswer;
        if (answer != null) event.setCanHarvest(answer);
    }

    void bonemeal(BonemealEvent event) {
        if (event.getLevel().isClientSide() || !positions.contains(event.getPos())) return;
        events.add(new Bonemealed(event.getPlayer(), event.getPos(), event.getState(), event.getStack()));
        Boolean success = bonemealSuccess;
        if (success != null) {
            event.setSuccessful(success);
            event.setCanceled(true);
        }
    }

    void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity().getUUID().equals(trackingPlayer) && event.getTarget() == stand) tracking.add(new Tracked(event.getTarget(), true));
    }

    void stopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity().getUUID().equals(trackingPlayer) && event.getTarget() == stand) tracking.add(new Tracked(event.getTarget(), false));
    }

    void runWithPlayer(ServerPlayer player, ServerLevel level, BlockPos base) {
        this.player = player;
        try {
            containers(player, level, base);
            crafting(player);
            advancements(player, level);
            sleeping(player);
            breaking(player, level, base);
            bonemeal(player, level, base);
            tracking(player, level, base);
        } finally {
            this.player = null;
            events.clear();
            positions.clear();
        }
    }

    private void containers(ServerPlayer player, ServerLevel level, BlockPos base) {
        events.clear();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory), Component.literal("probe")));
        AbstractContainerMenu first = player.containerMenu;
        playerChecks.check("PlayerContainerEvent.Open fires once the player's menu is the new one",
                first instanceof ChestMenu && events.equals(List.of(new Opened(first))));
        events.clear();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory), Component.literal("probe")));
        AbstractContainerMenu second = player.containerMenu;
        playerChecks.check("opening a menu over another closes that one first: PlayerContainerEvent.Close, then Open",
                second != first && events.equals(List.of(new Closed(first), new Opened(second))));
        events.clear();
        player.closeContainer();
        playerChecks.check("PlayerContainerEvent.Close fires with the menu being closed", events.equals(List.of(new Closed(second))) && player.containerMenu == player.inventoryMenu);

        Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.COMMAND);
        horse.setPos(Vec3.atBottomCenterOf(base.offset(2, 0, 0)));
        horse.setTamed(true);
        horse.setNoAi(true);
        level.addFreshEntity(horse);
        try {
            events.clear();
            horse.openCustomInventoryScreen(player);
            AbstractContainerMenu horseMenu = player.containerMenu;
            player.closeContainer();
            playerChecks.check("PlayerContainerEvent.Open and Close fire for a horse's inventory",
                    horseMenu instanceof HorseInventoryMenu && events.equals(List.of(new Opened(horseMenu), new Closed(horseMenu))));
        } finally {
            horse.discard();
        }
    }

    private void crafting(ServerPlayer player) {
        // Result slots taken from by hand: a crafting grid's (empty, so nothing stays in it) and a furnace's.
        TransientCraftingContainer grid = new TransientCraftingContainer(player.inventoryMenu, 2, 2);
        ResultContainer result = new ResultContainer();
        ResultSlot resultSlot = new ResultSlot(player, grid, result, 0, 0, 0);
        result.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        events.clear();
        ItemStack crafted = resultSlot.remove(1);
        resultSlot.onTake(player, crafted);
        playerChecks.check("PlayerEvent.ItemCraftedEvent fires when a player takes a crafted item, with the item and the grid",
                events.size() == 1 && events.get(0) instanceof Crafted c && c.crafting() == crafted && c.inventory() == grid
                        && CommonHooks.getCraftingPlayer() == null);

        SimpleContainer furnace = new SimpleContainer(3);
        FurnaceResultSlot furnaceSlot = new FurnaceResultSlot(player, furnace, 2, 0, 0);
        furnace.setItem(2, new ItemStack(Items.IRON_INGOT, 3));
        events.clear();
        ItemStack smelted = furnaceSlot.remove(2);
        furnaceSlot.onTake(player, smelted);
        List<Object> taken = List.copyOf(events);
        events.clear();
        furnaceSlot.onTake(player, ItemStack.EMPTY);
        playerChecks.check("PlayerEvent.ItemSmeltedEvent fires when a player takes smelted items, with the amount, and not when nothing was taken",
                taken.size() == 1 && taken.get(0) instanceof Smelted s && s.smelting() == smelted && s.amount() == 2 && events.isEmpty());
    }

    private void advancements(ServerPlayer player, ServerLevel level) {
        PlayerAdvancements advancements = player.getAdvancements();
        AdvancementHolder mineStone = level.getServer().getAdvancements().get(MINE_STONE);
        AdvancementHolder recipe = level.getServer().getAdvancements().get(CRAFTING_TABLE_RECIPE);
        List<String> mineStoneDone = undo(advancements, mineStone);
        List<String> recipeDone = undo(advancements, recipe);
        try {
            events.clear();
            advancements.award(mineStone, "get_stone");
            playerChecks.check("granting the last criterion fires AdvancementProgressEvent (GRANT), then AdvancementEarnEvent",
                    events.equals(List.of(new Progressed(MINE_STONE, "get_stone", ProgressType.GRANT, true), new Earned(MINE_STONE))));
            events.clear();
            advancements.revoke(mineStone, "get_stone");
            playerChecks.check("revoking a criterion fires AdvancementProgressEvent (REVOKE)",
                    events.equals(List.of(new Progressed(MINE_STONE, "get_stone", ProgressType.REVOKE, false))));
            events.clear();
            advancements.award(recipe, "has_the_recipe");
            playerChecks.check("a recipe advancement, which has no display, fires no AdvancementEarnEvent when earned (as in NeoForge)",
                    events.equals(List.of(new Progressed(CRAFTING_TABLE_RECIPE, "has_the_recipe", ProgressType.GRANT, true))));
            advancements.revoke(recipe, "has_the_recipe");
        } finally {
            for (String criterion : mineStoneDone) advancements.award(mineStone, criterion);
            for (String criterion : recipeDone) advancements.award(recipe, criterion);
        }
    }

    /** Revokes what the player has of the advancement; returns the criteria to grant back. */
    private static List<String> undo(PlayerAdvancements advancements, AdvancementHolder holder) {
        AdvancementProgress progress = advancements.getOrStartProgress(holder);
        List<String> done = new ArrayList<>();
        progress.getCompletedCriteria().forEach(done::add);
        for (String criterion : done) advancements.revoke(holder, criterion);
        return done;
    }

    private void sleeping(ServerPlayer player) {
        Vec3 home = player.position();
        try {
            // Asleep where the player stands (no bed: nothing to vacate, and the player stands up in place).
            player.startSleeping(player.blockPosition());
            events.clear();
            player.stopSleepInBed(false, true);
            playerChecks.check("PlayerWakeUpEvent fires as a player wakes, before the player stops sleeping",
                    events.equals(List.of(new WokeUp(false, true, true))) && !player.isSleeping());
            events.clear();
            player.stopSleepInBed(true, false);
            playerChecks.check("PlayerWakeUpEvent carries whether the wake-up is immediate and whether the sleeping list updates",
                    events.equals(List.of(new WokeUp(true, false, false))));
        } finally {
            player.teleportTo(home.x, home.y, home.z);
        }
    }

    private void breaking(ServerPlayer player, ServerLevel level, BlockPos base) {
        BlockPos stonePos = base.offset(3, 2, 3);
        BlockPos obsidianPos = base.offset(4, 2, 3);
        BlockPos brokenPos = base.offset(5, 2, 3);
        List<BlockPos> placed = List.of(stonePos, obsidianPos, brokenPos);
        positions.addAll(placed);
        ItemStack held = player.getMainHandItem();
        GameType gameType = player.gameMode.getGameModeForPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        try {
            level.setBlock(stonePos, Blocks.STONE.defaultBlockState(), FLAGS);
            level.setBlock(obsidianPos, Blocks.OBSIDIAN.defaultBlockState(), FLAGS);
            BlockState stone = level.getBlockState(stonePos);
            events.clear();
            float plain = stone.getDestroyProgress(player, level, stonePos);
            List<Object> plainEvents = events.stream().filter(Speed.class::isInstance).toList();
            playerChecks.check("PlayerEvent.BreakSpeed fires when a block's destroy progress is worked out, with the state, vanilla's speed and the position",
                    plainEvents.size() == 1 && plainEvents.get(0) instanceof Speed s && s.state() == stone && s.original() > 0.0F && s.position().equals(Optional.of(stonePos)));
            speedFactor = 2.0F;
            float doubled = stone.getDestroyProgress(player, level, stonePos);
            speedFactor = null;
            playerChecks.check("the speed PlayerEvent.BreakSpeed sets is the one the destroy progress uses", Math.abs(doubled - 2.0F * plain) < 1.0E-6F && plain > 0.0F);
            events.clear();
            cancelSpeed = true;
            float canceled = player.getDestroySpeed(stone);
            cancelSpeed = false;
            playerChecks.check("canceling PlayerEvent.BreakSpeed makes the speed -1 (vanilla's getDestroySpeed: no position)",
                    canceled == -1.0F && events.size() == 1 && events.get(0) instanceof Speed s && s.position().isEmpty());

            // Obsidian needs a pickaxe: with an empty hand vanilla says no, and the destroy progress divides by 100, not 30.
            BlockState obsidian = level.getBlockState(obsidianPos);
            events.clear();
            float refused = obsidian.getDestroyProgress(player, level, obsidianPos);
            List<Object> harvests = events.stream().filter(Harvest.class::isInstance).toList();
            harvestAnswer = true;
            float allowed = obsidian.getDestroyProgress(player, level, obsidianPos);
            harvestAnswer = null;
            playerChecks.check("PlayerEvent.HarvestCheck fires when a block's destroy progress is worked out, with vanilla's answer and the position",
                    harvests.equals(List.of(new Harvest(obsidian, false, obsidianPos))));
            playerChecks.check("PlayerEvent.HarvestCheck's answer picks the destroy progress's tool modifier", Math.abs(allowed / refused - 100.0F / 30.0F) < 1.0E-4F);

            // Broken in survival with an empty hand: stone drops nothing, unless HarvestCheck (through the block's
            // canHarvestBlock) says the player may harvest it.
            player.setGameMode(GameType.SURVIVAL);
            boolean vanillaDrops = breakAndCollect(player, level, brokenPos);
            harvestAnswer = true;
            boolean forcedDrops = breakAndCollect(player, level, brokenPos);
            harvestAnswer = null;
            playerChecks.check("PlayerEvent.HarvestCheck decides whether a player breaking a block gets its drops", !vanillaDrops && forcedDrops);
        } finally {
            speedFactor = null;
            cancelSpeed = false;
            harvestAnswer = null;
            player.setGameMode(gameType);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            positions.removeAll(placed);
            for (BlockPos pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    /** Places stone, has the player break it; returns whether it dropped anything (the drops are discarded). */
    private static boolean breakAndCollect(ServerPlayer player, ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), FLAGS);
        player.gameMode.destroyBlock(pos);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5));
        drops.forEach(Entity::discard);
        return !drops.isEmpty();
    }

    private void bonemeal(ServerPlayer player, ServerLevel level, BlockPos base) {
        BlockPos crop = base.offset(3, 2, 5);
        positions.add(crop);
        ItemStack held = player.getMainHandItem();
        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL, 10);
        player.setItemInHand(InteractionHand.MAIN_HAND, boneMeal);
        try {
            level.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState(), FLAGS);
            BlockState seedling = Blocks.WHEAT.defaultBlockState();
            level.setBlock(crop, seedling, FLAGS);
            events.clear();
            InteractionResult used = Items.BONE_MEAL.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(crop), Direction.UP, crop, false)));
            playerChecks.check("BonemealEvent fires when a player uses bone meal, with the player, position, state and stack; the crop grows",
                    used.consumesAction() && age(level, crop) > 0 && boneMeal.getCount() == 9 && events.equals(List.of(new Bonemealed(player, crop, seedling, boneMeal))));
            level.setBlock(crop, seedling, FLAGS);
            bonemealSuccess = true;
            InteractionResult claimed = use(player, crop);
            bonemealSuccess = false;
            InteractionResult refused = use(player, crop);
            bonemealSuccess = null;
            playerChecks.check("canceling BonemealEvent skips vanilla's handling; its success says whether the bone meal counts as used",
                    claimed.consumesAction() && refused == InteractionResult.PASS && age(level, crop) == 0 && boneMeal.getCount() == 9);
            events.clear();
            boolean grown = BoneMealItem.growCrop(boneMeal, level, crop);
            playerChecks.check("BonemealEvent fires without a player for vanilla's other bone meal (BoneMealItem.growCrop: dispensers, farmers)",
                    grown && events.equals(List.of(new Bonemealed(null, crop, seedling, boneMeal))));
            level.setBlock(crop, seedling, FLAGS);
            events.clear();
            // NeoForge's static applyBonemeal, which javac doesn't see on vanilla's class: findStatic resolves like a mod's invokestatic.
            MethodHandle applyBonemeal = MethodHandles.lookup().findStatic(BoneMealItem.class, "applyBonemeal",
                    MethodType.methodType(boolean.class, ItemStack.class, Level.class, BlockPos.class, Player.class));
            boolean applied = (boolean) applyBonemeal.invoke(boneMeal, (Level) level, crop, (Player) player);
            playerChecks.check("NeoForge's BoneMealItem.applyBonemeal fires BonemealEvent with the player it's given, and applies the bone meal",
                    applied && age(level, crop) > 0 && events.equals(List.of(new Bonemealed(player, crop, seedling, boneMeal))));
        } catch (Throwable t) {
            playerChecks.check("the bone meal checks ran (" + t + ")", false);
        } finally {
            bonemealSuccess = null;
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            positions.remove(crop);
            level.setBlock(crop, Blocks.AIR.defaultBlockState(), FLAGS);
            level.setBlock(crop.below(), Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    private static InteractionResult use(ServerPlayer player, BlockPos pos) {
        return Items.BONE_MEAL.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    private static int age(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.WHEAT) ? state.getValue(CropBlock.AGE) : -1;
    }

    private void tracking(ServerPlayer player, ServerLevel level, BlockPos base) {
        ArmorStand added = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        added.setPos(Vec3.atBottomCenterOf(base.offset(-2, 0, 0)));
        added.setNoGravity(true);
        tracking.clear();
        trackingPlayer = player.getUUID();
        stand = added;
        ticksSinceStand = 0;
        level.addFreshEntity(added);
    }

    /** Each server tick (Post): the stand added for the tracking check. */
    void tick() {
        if (stand == null) return;
        ++ticksSinceStand;
        if (tracking.isEmpty() && ticksSinceStand < TRACKING_TIMEOUT) return;
        LogUtils.getLogger().info("[events] the tracking check's armor stand: {} ticks", ticksSinceStand);
        ArmorStand checked = stand;
        List<Object> started = List.copyOf(tracking);
        tracking.clear();
        checked.discard();
        playerChecks.check("PlayerEvent.StartTracking fires once a player is sent an entity, StopTracking once told to remove it",
                List.of(started, List.copyOf(tracking)), List.of(List.of(new Tracked(checked, true)), List.of(new Tracked(checked, false))));
        stand = null;
        trackingPlayer = null;
    }

    /** When the server stops: the stand goes, if the server stops before the check. */
    void stopping() {
        if (stand == null) return;
        playerChecks.check("the tracking check ran before the server stopped", false);
        stand.discard();
        stand = null;
    }

    private record Opened(AbstractContainerMenu menu) {}

    private record Closed(AbstractContainerMenu menu) {}

    private record Crafted(ItemStack crafting, Object inventory) {}

    private record Smelted(ItemStack smelting, int amount) {}

    private record Earned(Identifier advancement) {}

    private record Progressed(Identifier advancement, String criterion, ProgressType type, boolean done) {}

    private record WokeUp(boolean wakeImmediately, boolean updateLevel, boolean wasSleeping) {}

    private record Tracked(Entity target, boolean start) {}

    private record Speed(BlockState state, float original, Optional<BlockPos> position) {}

    private record Harvest(BlockState state, boolean canHarvest, BlockPos pos) {}

    private record Bonemealed(Player player, BlockPos pos, BlockState state, ItemStack stack) {}
}
