package net.salutaryfish.versionunbound.testmod.events;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerFlyableFallEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.LivingEntityInvoker;

/**
 * Blocking, armor durability and flying players' falls. At server started (mobs not added to the level; the listeners
 * only record and act on this scenario's own entities): chickens (no armor, 4 health) raising a shield, hit for 3 by a
 * zombie standing in front of them: {@code LivingShieldBlockEvent} as vanilla decides (all blocked), not blocked, with
 * less blocked damage, for cactus damage (which bypasses shields: the event's original block is false) left as is and
 * blocked by the listener, and no event for a chicken that holds a shield without raising it. {@code ArmorHurtEvent}
 * through {@code doHurtEquipment} (an invoker; vanilla calls it for players, horses and wolves) on a zombie wearing iron
 * armor: the worn pieces with vanilla's durability damage, which they take; a new damage; canceled; and a netherite
 * piece that fire can't hurt (0). With the player, in survival (game mode, hands, helmet, health, abilities, rotation
 * and position put back afterwards; mob attack damage from two blocks south with no mob, which doesn't scale with the
 * difficulty as a mob's attack on a player does): its shield's vanilla durability damage, the event's shield damage, a shield
 * breaking ({@code PlayerDestroyItemEvent} for the off hand, and the player stops using it, NeoForge's fix for
 * MC-168573), {@code ArmorHurtEvent} for the player's helmet through {@code hurtServer}, and
 * {@code PlayerFlyableFallEvent} for a player who may fly (with the fall distance and the damage multiplier) and not for
 * one who can't.
 */
final class EquipmentScenario {
    private final Checks checks;
    private final Checks playerChecks;
    private final Map<Entity, LivingShieldBlockEvent> shieldBlocks = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Consumer<LivingShieldBlockEvent>> shieldActions = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, ArmorHurtEvent> armorHurts = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Consumer<ArmorHurtEvent>> armorActions = Collections.synchronizedMap(new IdentityHashMap<>());
    private final List<PlayerDestroyItemEvent> destroyedItems = new CopyOnWriteArrayList<>();
    private final List<PlayerFlyableFallEvent> flyableFalls = new CopyOnWriteArrayList<>();
    private volatile ServerPlayer player;

    EquipmentScenario(Checks checks, Checks playerChecks) {
        this.checks = checks;
        this.playerChecks = playerChecks;
    }

    void shieldBlock(LivingShieldBlockEvent event) {
        Consumer<LivingShieldBlockEvent> action = shieldActions.get(event.getEntity());
        if (action == null) return;
        shieldBlocks.put(event.getEntity(), event);
        action.accept(event);
    }

    void armorHurt(ArmorHurtEvent event) {
        Consumer<ArmorHurtEvent> action = armorActions.get(event.getEntity());
        if (action == null) return;
        armorHurts.put(event.getEntity(), event);
        action.accept(event);
    }

    void destroyItem(PlayerDestroyItemEvent event) {
        if (player != null && event.getEntity() == player) destroyedItems.add(event);
    }

    void flyableFall(PlayerFlyableFallEvent event) {
        if (player != null && event.getEntity() == player) flyableFalls.add(event);
    }

    void run(ServerLevel level, double x, double y, double z) {
        // The attacker stands two blocks south of the blockers, which face south (head rotation 0).
        LivingEntity attacker = create(level, EntityTypes.ZOMBIE, x, y, z + 2.0);
        DamageSource attack = level.damageSources().mobAttack(attacker);
        DamageSource cactus = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.CACTUS), attacker.position());

        LivingEntity blocking = blocker(level, x, y, z, event -> {});
        blocking.hurtServer(level, attack, 3.0F);
        LivingShieldBlockEvent blocked = shieldBlocks.get(blocking);
        checks.check("LivingShieldBlockEvent fires for a mob raising a shield, which blocks all the damage",
                blocked != null && blocked.getOriginalBlock() && blocked.getBlocked() && blocked.getOriginalBlockedDamage() == 3.0F
                        && blocked.getDamageContainer().getBlockedDamage() == 3.0F && blocking.getHealth() == blocking.getMaxHealth());

        LivingEntity unblocked = blocker(level, x, y, z, event -> event.setBlocked(false));
        unblocked.hurtServer(level, attack, 3.0F);
        checks.check("LivingShieldBlockEvent not blocking lets the damage through",
                shieldBlocks.containsKey(unblocked) && unblocked.getHealth() == unblocked.getMaxHealth() - 3.0F);

        LivingEntity partly = blocker(level, x, y, z, event -> event.setBlockedDamage(1.0F));
        partly.hurtServer(level, attack, 3.0F);
        checks.check("LivingShieldBlockEvent's blocked damage is what the shield takes off",
                shieldBlocks.containsKey(partly) && partly.getHealth() == partly.getMaxHealth() - 2.0F
                        && shieldBlocks.get(partly).getDamageContainer().getBlockedDamage() == 1.0F);

        LivingEntity bypassed = blocker(level, x, y, z, event -> {});
        bypassed.hurtServer(level, cactus, 3.0F);
        LivingShieldBlockEvent bypassedEvent = shieldBlocks.get(bypassed);
        checks.check("damage that bypasses shields reaches LivingShieldBlockEvent, which doesn't block it by default",
                bypassedEvent != null && !bypassedEvent.getOriginalBlock() && !bypassedEvent.getBlocked() && bypassed.getHealth() == bypassed.getMaxHealth() - 3.0F);

        LivingEntity forced = blocker(level, x, y, z, event -> event.setBlocked(true));
        forced.hurtServer(level, cactus, 3.0F);
        checks.check("LivingShieldBlockEvent can block damage that bypasses shields",
                shieldBlocks.containsKey(forced) && forced.getHealth() == forced.getMaxHealth());

        LivingEntity lowered = create(level, EntityTypes.CHICKEN, x, y, z);
        lowered.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        shieldActions.put(lowered, event -> {});
        lowered.hurtServer(level, attack, 3.0F);
        checks.check("LivingShieldBlockEvent doesn't fire for a mob that isn't raising a shield",
                !shieldBlocks.containsKey(lowered) && lowered.getHealth() == lowered.getMaxHealth() - 3.0F);

        // doHurtEquipment(source, 8): vanilla's durability damage is max(1, 8 / 4) = 2 for each piece.
        LivingEntity armored = armored(level, x, y, z, event -> {});
        ((LivingEntityInvoker) armored).events$doHurtEquipment(level.damageSources().generic(), 8.0F, new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.FEET});
        ArmorHurtEvent armorEvent = armorHurts.get(armored);
        checks.check("ArmorHurtEvent fires in doHurtEquipment with the worn pieces and vanilla's durability damage, which they take",
                armorEvent != null && armorEvent.getArmorMap().keySet().equals(Set.of(EquipmentSlot.CHEST, EquipmentSlot.FEET))
                        && armorEvent.getArmorItemStack(EquipmentSlot.CHEST) == armored.getItemBySlot(EquipmentSlot.CHEST)
                        && armorEvent.getOriginalDamage(EquipmentSlot.CHEST) == 2.0F && armorEvent.getOriginalDamage(EquipmentSlot.FEET) == 2.0F
                        && armored.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 2 && armored.getItemBySlot(EquipmentSlot.FEET).getDamageValue() == 2);

        LivingEntity reworn = armored(level, x, y, z, event -> event.setNewDamage(EquipmentSlot.CHEST, 5.0F));
        ((LivingEntityInvoker) reworn).events$doHurtEquipment(level.damageSources().generic(), 8.0F, new EquipmentSlot[] {EquipmentSlot.CHEST, EquipmentSlot.FEET});
        checks.check("ArmorHurtEvent's new damage is what the armor takes",
                armorHurts.containsKey(reworn) && reworn.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 5 && reworn.getItemBySlot(EquipmentSlot.FEET).getDamageValue() == 2);

        LivingEntity spared = armored(level, x, y, z, event -> event.setCanceled(true));
        ((LivingEntityInvoker) spared).events$doHurtEquipment(level.damageSources().generic(), 8.0F, new EquipmentSlot[] {EquipmentSlot.CHEST, EquipmentSlot.FEET});
        checks.check("canceling ArmorHurtEvent leaves the armor undamaged",
                armorHurts.containsKey(spared) && spared.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 0 && spared.getItemBySlot(EquipmentSlot.FEET).getDamageValue() == 0);

        LivingEntity fireproof = create(level, EntityTypes.ZOMBIE, x, y, z);
        fireproof.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        armorActions.put(fireproof, event -> {});
        ((LivingEntityInvoker) fireproof).events$doHurtEquipment(level.damageSources().inFire(), 8.0F, new EquipmentSlot[] {EquipmentSlot.CHEST});
        ArmorHurtEvent fireEvent = armorHurts.get(fireproof);
        checks.check("ArmorHurtEvent gives a piece the source can't hurt no durability damage",
                fireEvent != null && fireEvent.getArmorMap().containsKey(EquipmentSlot.CHEST) && fireEvent.getOriginalDamage(EquipmentSlot.CHEST) == 0.0F
                        && fireproof.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 0);

        for (LivingEntity entity : List.of(attacker, blocking, unblocked, partly, bypassed, forced, lowered, armored, reworn, spared, fireproof)) {
            shieldActions.remove(entity);
            armorActions.remove(entity);
            entity.discard();
        }
    }

    void runWithPlayer(ServerPlayer player, ServerLevel level) {
        this.player = player;
        GameType gameType = player.gameMode.getGameModeForPlayer();
        Inventory inventory = player.getInventory();
        ItemStack mainHand = player.getMainHandItem().copy();
        ItemStack offHand = player.getOffhandItem().copy();
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        Vec3 home = player.position();
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        float yHeadRot = player.getYHeadRot();
        player.setGameMode(GameType.SURVIVAL);
        boolean mayfly = player.getAbilities().mayfly;
        // A mob attack from two blocks south without the mob: with a mob as its cause it would scale with the difficulty
        // (Player.hurtServer). (Generic damage bypasses armor and shields.)
        DamageSource attack = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.MOB_ATTACK),
                player.position().add(0.0, 0.0, 2.0));
        try {
            player.setYHeadRot(0.0F);
            player.setHealth(player.getMaxHealth());

            // A hit for 3: the shield's item damage function gives floor(1 + 3) = 4.
            ItemStack shield = raiseShield(player, new ItemStack(Items.SHIELD));
            shieldActions.put(player, event -> {});
            player.hurtServer(level, attack, 3.0F);
            playerChecks.check("LivingShieldBlockEvent fires for a player's shield, which takes vanilla's durability damage",
                    List.of(shieldBlocks.get(player) != null && shieldBlocks.get(player).getBlocked(), shield.getDamageValue(), player.getHealth()),
                    List.of(true, 4, player.getMaxHealth()));

            shieldBlocks.remove(player);
            ItemStack fixed = raiseShield(player, new ItemStack(Items.SHIELD));
            shieldActions.put(player, event -> event.setShieldDamage(2));
            player.hurtServer(level, attack, 3.0F);
            playerChecks.check("LivingShieldBlockEvent's shield damage is what the player's shield takes",
                    shieldBlocks.containsKey(player) && fixed.getDamageValue() == 2);

            ItemStack worn = new ItemStack(Items.SHIELD);
            worn.setDamageValue(worn.getMaxDamage() - 1);
            raiseShield(player, worn);
            shieldActions.put(player, event -> {});
            destroyedItems.clear();
            player.hurtServer(level, attack, 3.0F);
            playerChecks.check("PlayerDestroyItemEvent fires when a player's shield breaks, and the player stops using it",
                    List.of(destroyedItems.size() == 1 && destroyedItems.get(0).getHand() == InteractionHand.OFF_HAND && destroyedItems.get(0).getOriginal().is(Items.SHIELD),
                            player.getOffhandItem().isEmpty(), player.isUsingItem(), player.getHealth()),
                    List.of(true, true, false, player.getMaxHealth()));
            shieldActions.remove(player);

            // Unblocked: Player.hurtArmor asks doHurtEquipment for its four armor slots, of which only the helmet is worn.
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            armorActions.put(player, event -> {});
            player.invulnerableTime = 0;
            player.hurtServer(level, attack, 8.0F);
            ArmorHurtEvent armorEvent = armorHurts.get(player);
            playerChecks.check("ArmorHurtEvent fires when a hit wears a player's armor",
                    List.of(armorEvent != null ? armorEvent.getArmorMap().keySet() : Set.of(), armorEvent != null ? armorEvent.getOriginalDamage(EquipmentSlot.HEAD) : -1.0F,
                            player.getItemBySlot(EquipmentSlot.HEAD).getDamageValue()),
                    List.of(Set.of(EquipmentSlot.HEAD), 2.0F, 2));
            armorActions.remove(player);

            player.getAbilities().mayfly = true;
            flyableFalls.clear();
            boolean hurt = player.causeFallDamage(10.0, 0.5F, level.damageSources().fall());
            playerChecks.check("PlayerFlyableFallEvent fires when a player who may fly lands, with the fall distance and multiplier",
                    !hurt && flyableFalls.size() == 1 && flyableFalls.get(0).getDistance() == 10.0F && flyableFalls.get(0).getMultiplier() == 0.5F);

            player.getAbilities().mayfly = false;
            flyableFalls.clear();
            // A multiplier of 0: no damage.
            player.causeFallDamage(10.0, 0.0F, level.damageSources().fall());
            playerChecks.check("PlayerFlyableFallEvent doesn't fire for a player who can't fly", flyableFalls.isEmpty());
        } finally {
            shieldActions.remove(player);
            armorActions.remove(player);
            player.stopUsingItem();
            player.setGameMode(gameType);
            player.getAbilities().mayfly = mayfly;
            player.onUpdateAbilities();
            player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
            player.setItemInHand(InteractionHand.OFF_HAND, offHand);
            player.setItemSlot(EquipmentSlot.HEAD, helmet);
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            // The unblocked hit knocks the player back. Not ServerPlayer.teleport, which fires EntityTravelToDimensionEvent
            // (PlayerScenario counts those).
            player.setDeltaMovement(Vec3.ZERO);
            player.connection.teleport(home.x, home.y, home.z, yRot, xRot);
            player.setYHeadRot(yHeadRot);
            inventory.setChanged();
            this.player = null;
        }
    }

    /** A chicken (no armor) raising a shield in its off hand, facing south, with the listener's action. */
    private LivingEntity blocker(ServerLevel level, double x, double y, double z, Consumer<LivingShieldBlockEvent> action) {
        LivingEntity chicken = create(level, EntityTypes.CHICKEN, x, y, z);
        raiseShield(chicken, new ItemStack(Items.SHIELD));
        shieldActions.put(chicken, action);
        return chicken;
    }

    /** A zombie in an iron chestplate and boots, with the listener's action. */
    private LivingEntity armored(ServerLevel level, double x, double y, double z, Consumer<ArmorHurtEvent> action) {
        LivingEntity zombie = create(level, EntityTypes.ZOMBIE, x, y, z);
        zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        armorActions.put(zombie, action);
        return zombie;
    }

    /**
     * Puts the shield in the entity's off hand and raises it, past the shield's block delay (a quarter second), facing
     * south; returns the stack in the hand.
     */
    private static ItemStack raiseShield(LivingEntity entity, ItemStack shield) {
        entity.stopUsingItem();
        entity.setItemInHand(InteractionHand.OFF_HAND, shield);
        entity.startUsingItem(InteractionHand.OFF_HAND);
        ((LivingEntityInvoker) entity).events$setUseItemRemaining(entity.getUseItem().getUseDuration(entity) - 10);
        entity.setYHeadRot(0.0F);
        entity.invulnerableTime = 0;
        return entity.getOffhandItem();
    }

    private static LivingEntity create(ServerLevel level, EntityType<? extends LivingEntity> type, double x, double y, double z) {
        LivingEntity entity = type.create(level, EntitySpawnReason.COMMAND);
        entity.setPos(x, y, z);
        return entity;
    }
}
