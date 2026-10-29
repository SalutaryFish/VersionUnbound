package net.salutaryfish.versionunbound.testmod.events;

import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import org.slf4j.Logger;

/**
 * Item entity checks. On the server, once it has started: two items about to despawn, one given extra life by
 * {@code ItemExpireEvent} (checked when the server stops, with NeoForge's {@code lifespan} field and its saved value) and
 * one left to expire, and NeoForge's {@code getTarget}. With a player ({@link #runWithPlayer}): pickups (allowed, refused
 * and forced past a pickup delay by {@code ItemEntityPickupEvent.Pre}, with the {@code Post} event) and tosses (from a
 * stack and the selected slot, and a canceled one). NeoForge's added members aren't in vanilla's classes this testmod
 * compiles against; method handles resolve them as a mod's {@code getfield} and {@code invokevirtual} do.
 */
final class ItemScenario {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Another vanilla lifetime, so the item doesn't come up for expiry again while the server runs.
    private static final int EXTRA_LIFE = 6000;
    // Null if missing, which fails the checks that use them.
    private static final MethodHandle LIFESPAN = find(() -> MethodHandles.publicLookup().findGetter(ItemEntity.class, "lifespan", int.class));
    private static final MethodHandle GET_TARGET = find(() -> MethodHandles.publicLookup().findVirtual(ItemEntity.class, "getTarget", MethodType.methodType(UUID.class)));

    private final Checks checks;
    private final Checks playerChecks;
    private final Map<Entity, Integer> joins;
    private final Map<Entity, Integer> expiries = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, ItemEntityPickupEvent.Pre> pickupPres = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, ItemEntityPickupEvent.Post> pickupPosts = Collections.synchronizedMap(new IdentityHashMap<>());
    // Whether the item's stack was empty during Post. Vanilla puts the count back on the discarded item's stack after a
    // full pickup (for the pickup animation), so it can't be read afterwards.
    private final Map<Entity, Boolean> emptyAtPost = Collections.synchronizedMap(new IdentityHashMap<>());
    private final List<ItemTossEvent> tosses = Collections.synchronizedList(new ArrayList<>());
    private final List<Entity> spawned = new ArrayList<>();
    private ItemEntity extended;
    private ItemEntity expiring;
    private Entity refusedPickup;
    private Entity forcedPickup;
    private boolean cancelToss;

    ItemScenario(Checks checks, Checks playerChecks, Map<Entity, Integer> joins) {
        this.checks = checks;
        this.playerChecks = playerChecks;
        this.joins = joins;
    }

    void expire(ItemExpireEvent event) {
        int count = expiries.merge(event.getEntity(), 1, Integer::sum);
        if (event.getEntity() == extended && count == 1) event.addExtraLife(EXTRA_LIFE);
    }

    void pickupPre(ItemEntityPickupEvent.Pre event) {
        pickupPres.put(event.getItemEntity(), event);
        if (event.getItemEntity() == refusedPickup) event.setCanPickup(TriState.FALSE);
        if (event.getItemEntity() == forcedPickup) event.setCanPickup(TriState.TRUE);
    }

    void pickupPost(ItemEntityPickupEvent.Post event) {
        pickupPosts.put(event.getItemEntity(), event);
        emptyAtPost.put(event.getItemEntity(), event.getCurrentStack().isEmpty());
    }

    void toss(ItemTossEvent event) {
        tosses.add(event);
        if (cancelToss) event.setCanceled(true);
    }

    void run(ServerLevel level, double x, double y, double z) {
        // makeFakeItem sets the age to one tick short of vanilla's lifespan, so both despawn on their next tick.
        extended = spawn(level, new ItemStack(Items.STONE), new Vec3(x, y, z));
        expiring = spawn(level, new ItemStack(Items.STONE), new Vec3(x, y, z));
        checks.check("an item entity has NeoForge's lifespan field, vanilla's 6000 ticks", () -> lifespan(extended) == 6000);
        extended.makeFakeItem();
        expiring.makeFakeItem();

        UUID owner = UUID.randomUUID();
        expiring.setTarget(owner);
        checks.check("ItemEntity.getTarget returns the item's target", () -> owner.equals((UUID) GET_TARGET.invoke(expiring)));
    }

    /** When the server stops: one item lives on with the extra life, which it saves and loads; the other expired. */
    void finish(ServerLevel level) {
        checks.check("ItemExpireEvent fires when an item despawns, and its extra life extends the lifespan", () ->
                expiries.containsKey(extended) && !extended.isRemoved() && lifespan(extended) == 6000 + EXTRA_LIFE);
        checks.check("an item expires when no listener extends it", expiries.containsKey(expiring) && expiring.isRemoved());
        checks.check("the lifespan is saved and loaded with the item", () -> {
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            extended.saveWithoutId(output);
            CompoundTag saved = output.buildResult();
            ItemEntity loaded = EntityTypes.ITEM.create(level, EntitySpawnReason.LOAD);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
            return saved.getIntOr("Lifespan", -1) == 6000 + EXTRA_LIFE && lifespan(loaded) == 6000 + EXTRA_LIFE;
        });
        spawned.forEach(Entity::discard);
    }

    void runWithPlayer(ServerPlayer player, ServerLevel level, BlockPos pos) {
        Inventory inventory = player.getInventory();
        Vec3 at = Vec3.atBottomCenterOf(pos);

        int before = inventory.countItem(Items.EMERALD);
        ItemEntity pickedUp = spawn(level, new ItemStack(Items.EMERALD, 3), at);
        pickedUp.playerTouch(player);
        ItemEntityPickupEvent.Post post = pickupPosts.get(pickedUp);
        playerChecks.check("ItemEntityPickupEvent.Pre and Post fire, and the item is picked up",
                pickupPres.containsKey(pickedUp) && post != null && post.getPlayer() == player && post.getOriginalStack().getCount() == 3
                        && emptyAtPost.getOrDefault(pickedUp, false) && pickedUp.isRemoved() && inventory.countItem(Items.EMERALD) == before + 3);

        ItemEntity refused = spawn(level, new ItemStack(Items.EMERALD, 3), at);
        refusedPickup = refused;
        refused.playerTouch(player);
        playerChecks.check("Pre's FALSE refuses the pickup, without a Post",
                pickupPres.containsKey(refused) && !pickupPosts.containsKey(refused) && !refused.isRemoved() && inventory.countItem(Items.EMERALD) == before + 3);

        ItemEntity delayed = spawn(level, new ItemStack(Items.EMERALD, 2), at);
        delayed.setPickUpDelay(40);
        forcedPickup = delayed;
        delayed.playerTouch(player);
        playerChecks.check("Pre's TRUE picks the item up despite its pickup delay",
                pickupPosts.containsKey(delayed) && delayed.isRemoved() && inventory.countItem(Items.EMERALD) == before + 5);
        inventory.clearOrCountMatchingItems(stack -> stack.is(Items.EMERALD), 5, player.inventoryMenu.getCraftSlots());

        // Player.drop(stack, thrownFromHand), as for a creative drop or a carried stack when a menu closes.
        ItemEntity tossed = player.drop(new ItemStack(Items.EMERALD, 2), false);
        ItemTossEvent toss = tosses.isEmpty() ? null : tosses.getLast();
        playerChecks.check("ItemTossEvent fires for a dropped stack, and the item joins the level once",
                tossed != null && toss != null && toss.getEntity() == tossed && toss.getPlayer() == player && joins.getOrDefault(tossed, 0) == 1);
        cancelToss = true;
        int tossCount = tosses.size();
        ItemEntity canceled = player.drop(new ItemStack(Items.EMERALD, 2), false);
        cancelToss = false;
        ItemEntity canceledEntity = tosses.size() > tossCount ? tosses.getLast().getEntity() : null;
        playerChecks.check("canceling ItemTossEvent keeps the item out of the level",
                canceled == null && canceledEntity != null && joins.getOrDefault(canceledEntity, 0) == 0);

        // ServerPlayer.drop(all), the drop key: one item of the selected stack.
        int slot = inventory.getSelectedSlot();
        ItemStack held = inventory.getItem(slot);
        inventory.setItem(slot, new ItemStack(Items.EMERALD, 2));
        tossCount = tosses.size();
        player.drop(false);
        ItemTossEvent selectedToss = tosses.size() > tossCount ? tosses.getLast() : null;
        playerChecks.check("ItemTossEvent fires when the selected item is dropped",
                selectedToss != null && selectedToss.getEntity().getItem().getCount() == 1 && inventory.getItem(slot).getCount() == 1
                        && joins.getOrDefault(selectedToss.getEntity(), 0) == 1);
        inventory.setItem(slot, held);

        for (ItemTossEvent event : tosses) {
            event.getEntity().discard();
        }
        refused.discard();
    }

    private ItemEntity spawn(ServerLevel level, ItemStack stack, Vec3 at) {
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack);
        level.addFreshEntity(item);
        spawned.add(item);
        return item;
    }

    private static MethodHandle find(Lookup lookup) {
        try {
            return lookup.find();
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[events] a member NeoForge adds to ItemEntity is missing", e);
            return null;
        }
    }

    @FunctionalInterface
    private interface Lookup {
        MethodHandle find() throws ReflectiveOperationException;
    }

    private static int lifespan(ItemEntity item) {
        try {
            return (int) LIFESPAN.invoke(item);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
