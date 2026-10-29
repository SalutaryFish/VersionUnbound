package net.salutaryfish.versionunbound.testmod.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.LivingEntityInvoker;

/**
 * Server-side player checks. When a player logs in, the scenario waits until its client has loaded the level, then
 * {@value #DELAY} more ticks, and then drives the server's
 * own code paths for that player, the ones the network handler calls: clicking and breaking blocks, using blocks and
 * items, interacting with and attacking entities, a canceled death and the death drops, picking up and tossing items,
 * items breaking or used up,
 * teleporting within the overworld, to the nether and back, a fishing hook and ender pearls, and finally
 * respawning as if from the End. The listeners below cancel or redirect some of these by position or target.
 */
final class PlayerScenario {
    private static final int DELAY = 20;

    private final Checks checks;
    private final Map<Entity, Integer> joins;
    private final Map<Entity, Integer> leaves;
    private final DamageScenario damage;
    private final ItemScenario items;
    private final DestroyItemScenario destroyedItems;
    private final ProjectileScenario projectiles;
    private final TeleportScenario teleports;
    private ServerPlayer player;
    private int ticksUntilRun = -1;

    private int loggedIn;
    private int loggedOut;
    private Player loggedOutPlayer;

    private BlockPos breakPos;
    private BlockPos deniedBreakPos;
    private BlockPos notePos;
    private BlockPos noBlockUsePos;
    private BlockPos forcedBlockUsePos;
    private BlockPos canceledUsePos;
    private Entity canceledInteractTarget;
    private Entity canceledAttackTarget;
    private boolean cancelRightClickItem;
    private boolean cancelDeath;
    private boolean deathCanceled;
    private boolean cancelPlayerDrops;
    private List<ItemEntity> playerDrops;

    private final List<BlockPos> leftClicks = new ArrayList<>();
    private PlayerInteractEvent.LeftClickBlock.Action leftClickAction;
    private Direction leftClickFace;
    private final List<BlockPos> breaks = new ArrayList<>();
    private final List<BlockPos> rightClicks = new ArrayList<>();
    private int rightClickItems;
    private final List<Entity> interactions = new ArrayList<>();
    private final List<Entity> attacks = new ArrayList<>();
    private final List<ResourceKey<Level>> travels = new ArrayList<>();
    private final List<String> dimensionChanges = new ArrayList<>();
    private Player respawnPositionPlayer;
    private boolean respawnPositionFromEnd;
    private Player cloneOriginal;
    private Player cloneNew;
    private boolean cloneWasDeath = true;
    private Player respawned;
    private boolean respawnEndConquered;

    PlayerScenario(Checks checks, Map<Entity, Integer> joins, Map<Entity, Integer> leaves, DamageScenario damage, ItemScenario items,
            DestroyItemScenario destroyedItems, ProjectileScenario projectiles, TeleportScenario teleports) {
        this.checks = checks;
        this.joins = joins;
        this.leaves = leaves;
        this.damage = damage;
        this.items = items;
        this.destroyedItems = destroyedItems;
        this.projectiles = projectiles;
        this.teleports = teleports;
    }

    boolean played() {
        return loggedIn > 0;
    }

    void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        loggedIn++;
        if (player == null && event.getEntity() instanceof ServerPlayer serverPlayer) {
            player = serverPlayer;
            ticksUntilRun = DELAY;
        }
    }

    void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        loggedOut++;
        loggedOutPlayer = event.getEntity();
    }

    void serverTickEnd() {
        // The countdown starts once the client has loaded the level: until then the player takes no damage
        // (ServerPlayer.isInvulnerableTo), so a slow client would make the kill and the hit below do nothing.
        if (ticksUntilRun > 0 && player.connection.hasClientLoaded() && --ticksUntilRun == 0) {
            run();
        }
    }

    void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        leftClicks.add(event.getPos().immutable());
        leftClickAction = event.getAction();
        leftClickFace = event.getFace();
        if (event.getPos().equals(deniedBreakPos)) event.setCanceled(true);
    }

    void breakBlock(BreakBlockEvent event) {
        breaks.add(event.getPos().immutable());
        if (event.getPos().equals(deniedBreakPos)) event.setCanceled(true);
    }

    void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        rightClicks.add(event.getPos().immutable());
        if (event.getPos().equals(noBlockUsePos)) {
            event.setUseBlock(TriState.FALSE);
        } else if (event.getPos().equals(forcedBlockUsePos)) {
            event.setUseBlock(TriState.TRUE);
            event.setUseItem(TriState.FALSE);
        } else if (event.getPos().equals(canceledUsePos)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        rightClickItems++;
        if (cancelRightClickItem) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    void entityInteract(PlayerInteractEvent.EntityInteract event) {
        interactions.add(event.getTarget());
        if (event.getTarget() == canceledInteractTarget) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    void livingDeath(LivingDeathEvent event) {
        if (cancelDeath && event.getEntity() == player) {
            event.setCanceled(true);
            player.setHealth(1.0F);
            deathCanceled = true;
        }
    }

    void livingDrops(LivingDropsEvent event) {
        if (event.getEntity() != player) return;
        playerDrops = List.copyOf(event.getDrops());
        if (cancelPlayerDrops) event.setCanceled(true);
    }

    void attack(AttackEntityEvent event) {
        attacks.add(event.getTarget());
        if (event.getTarget() == canceledAttackTarget) event.setCanceled(true);
    }

    void travelToDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() == player) travels.add(event.getDimension());
    }

    void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        dimensionChanges.add(event.getFrom().identifier().getPath() + ">" + event.getTo().identifier().getPath());
    }

    void respawnPosition(PlayerRespawnPositionEvent event) {
        respawnPositionPlayer = event.getEntity();
        respawnPositionFromEnd = event.isFromEndFight();
    }

    void cloned(PlayerEvent.Clone event) {
        cloneOriginal = event.getOriginal();
        cloneNew = event.getEntity();
        cloneWasDeath = event.isWasDeath();
    }

    void respawned(PlayerEvent.PlayerRespawnEvent event) {
        respawned = event.getEntity();
        respawnEndConquered = event.isEndConquered();
    }

    private void run() {
        ServerLevel level = player.level();
        checks.check("EntityJoinLevelEvent fired once when the player joined", joins.getOrDefault(player, 0), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // A row of blocks in the air next to the player.
        BlockPos base = player.blockPosition().above(3);
        breakPos = base.offset(-3, 0, 2);
        deniedBreakPos = base.offset(-2, 0, 2);
        notePos = base.offset(-1, 0, 2);
        noBlockUsePos = base.offset(0, 0, 2);
        forcedBlockUsePos = base.offset(1, 0, 2);
        canceledUsePos = base.offset(2, 0, 2);
        level.setBlock(breakPos, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(deniedBreakPos, Blocks.STONE.defaultBlockState(), 3);
        for (BlockPos pos : List.of(notePos, noBlockUsePos, forcedBlockUsePos, canceledUsePos)) {
            level.setBlock(pos, Blocks.NOTE_BLOCK.defaultBlockState(), 3);
        }

        // Left click (START) and breaking, then both canceled. In creative, the left click already breaks the block.
        player.gameMode.handleBlockBreakAction(breakPos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, level.getMaxY(), 0);
        checks.check("LeftClickBlock fires on the server with the action and face",
                leftClicks.contains(breakPos) && leftClickAction == PlayerInteractEvent.LeftClickBlock.Action.START && leftClickFace == Direction.UP);
        boolean broken = player.gameMode.destroyBlock(breakPos);
        checks.check("BreakBlockEvent fires and the block breaks", breaks.contains(breakPos) && broken && level.getBlockState(breakPos).isAir());
        player.gameMode.handleBlockBreakAction(deniedBreakPos, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, Direction.UP, level.getMaxY(), 0);
        boolean deniedBroken = player.gameMode.destroyBlock(deniedBreakPos);
        checks.check("canceling LeftClickBlock and BreakBlockEvent keeps the block",
                leftClicks.contains(deniedBreakPos) && breaks.contains(deniedBreakPos) && !deniedBroken && level.getBlockState(deniedBreakPos).is(Blocks.STONE));

        // Right click on note blocks: used, not used (use-block FALSE), used although sneaking with an item (use-block
        // TRUE, use-item FALSE), and canceled with a result.
        InteractionResult used = useOn(level, notePos);
        checks.check("RightClickBlock fires and the block is used", rightClicks.contains(notePos) && note(level, notePos) == 1 && used.consumesAction());
        InteractionResult notUsed = useOn(level, noBlockUsePos);
        checks.check("RightClickBlock's use-block FALSE skips the block", rightClicks.contains(noBlockUsePos) && note(level, noBlockUsePos) == 0 && notUsed == InteractionResult.PASS);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        player.setShiftKeyDown(true);
        useOn(level, forcedBlockUsePos);
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        checks.check("RightClickBlock's use-block TRUE uses the block although the player sneaks with an item", note(level, forcedBlockUsePos) == 1);
        InteractionResult canceled = useOn(level, canceledUsePos);
        checks.check("canceling RightClickBlock returns its cancellation result", note(level, canceledUsePos) == 0 && canceled == InteractionResult.CONSUME);

        // Right click with the (empty) hand, then canceled.
        player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        checks.check("RightClickItem fires", rightClickItems == 1);
        cancelRightClickItem = true;
        InteractionResult canceledItem = player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        cancelRightClickItem = false;
        checks.check("canceling RightClickItem returns its cancellation result", rightClickItems == 2 && canceledItem == InteractionResult.FAIL);

        // Entities next to the player: interaction, then canceled; an attack canceled, then one that lands.
        Entity stand = spawn(level, EntityTypes.ARMOR_STAND, base);
        Entity canceledStand = spawn(level, EntityTypes.ARMOR_STAND, base);
        canceledInteractTarget = canceledStand;
        player.interactOn(stand, InteractionHand.MAIN_HAND, Vec3.ZERO);
        InteractionResult canceledInteraction = player.interactOn(canceledStand, InteractionHand.MAIN_HAND, Vec3.ZERO);
        checks.check("EntityInteract fires, and canceling it returns its cancellation result",
                interactions.contains(stand) && interactions.contains(canceledStand) && canceledInteraction == InteractionResult.CONSUME);
        LivingEntity hit = (LivingEntity) spawn(level, EntityTypes.CHICKEN, base);
        LivingEntity spared = (LivingEntity) spawn(level, EntityTypes.CHICKEN, base);
        canceledAttackTarget = spared;
        player.attack(spared);
        player.attack(hit);
        checks.check("AttackEntityEvent fires, and canceling it stops the attack",
                attacks.contains(spared) && attacks.contains(hit) && spared.getHealth() == spared.getMaxHealth() && hit.getHealth() < hit.getMaxHealth());
        for (Entity entity : List.of(stand, canceledStand, hit, spared)) {
            entity.discard();
        }
        for (BlockPos pos : List.of(breakPos, deniedBreakPos, notePos, noBlockUsePos, forcedBlockUsePos, canceledUsePos)) {
            level.removeBlock(pos, false);
        }

        // Picking up and tossing items (ItemScenario), and items breaking or being used up (DestroyItemScenario).
        items.runWithPlayer(player, level, base);
        destroyedItems.runWithPlayer(player, level, base);

        // A death the listener cancels (ServerPlayer overrides LivingEntity.die).
        cancelDeath = true;
        player.kill(level);
        cancelDeath = false;
        checks.check("LivingDeathEvent fires for a player, and canceling it keeps the player alive", deathCanceled && player.isAlive());
        player.setHealth(player.getMaxHealth());
        deathDrops(level);

        // A hit the player takes (Player has its own actuallyHurt); the canceled death left invulnerability ticks.
        if (!player.isCreative() && !player.isSpectator()) {
            player.invulnerableTime = 0;
            player.hurtServer(level, level.damageSources().cactus(), 2.0F);
            checks.check("LivingDamageEvent.Post fires for a player, and the player loses that health",
                    damage.posted(player) && damage.healthDamage(player) > 0.0F && player.getHealth() == player.getMaxHealth() - damage.healthDamage(player));
            player.setHealth(player.getMaxHealth());
        }

        // Teleports: within the overworld (travel event only), to the nether and back.
        Vec3 home = player.position();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        player.teleport(new TeleportTransition(level, home.add(0, 1, 0), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        checks.check("a teleport within a level fires EntityTravelToDimensionEvent but not PlayerChangedDimensionEvent",
                travels.equals(List.of(Level.OVERWORLD)) && dimensionChanges.isEmpty());
        player.teleport(new TeleportTransition(nether, new Vec3(0.5, 64, 0.5), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        player.teleport(new TeleportTransition(level, home, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        checks.check("teleporting to the nether and back fires the travel and changed dimension events",
                travels.equals(List.of(Level.OVERWORLD, Level.NETHER, Level.OVERWORLD))
                        && dimensionChanges.equals(List.of("overworld>the_nether", "the_nether>overworld")));
        checks.check("each dimension change has the player leave one level and join the other",
                joins.getOrDefault(player, 0) == 3 && leaves.getOrDefault(player, 0) == 2);

        // A fishing hook (ProjectileScenario) and ender pearls (TeleportScenario), after the teleport checks above:
        // pearls teleport the player too.
        projectiles.runWithPlayer(player, level, base);
        teleports.runWithPlayer(player, level, base);

        // Respawn as when leaving the End: the old player is replaced by a new one that keeps everything.
        ServerPlayer old = player;
        player = level.getServer().getPlayerList().respawn(old, true, Entity.RemovalReason.CHANGED_DIMENSION);
        // As the packet handler does with respawn's result, so the connection now belongs to the new player.
        player.connection.player = player;
        checks.check("PlayerRespawnPositionEvent fires for the old player", respawnPositionPlayer == old && respawnPositionFromEnd);
        checks.check("PlayerEvent.Clone fires with the old and the new player", cloneOriginal == old && cloneNew == player && !cloneWasDeath);
        checks.check("PlayerRespawnEvent fires for the new player", respawned == player && respawnEndConquered);
        checks.check("on respawn, the old player leaves the level and the new one joins it",
                leaves.getOrDefault(old, 0) == 3 && joins.getOrDefault(player, 0) == 1);
    }

    /**
     * The death drops as {@code die} makes them ({@code dropAllDeathLoot}), from a known inventory: the player's inventory
     * drops through {@code LivingEntity.drop}, which NeoForge captures for {@code LivingDropsEvent}. Once with the drops
     * canceled, then landing in the level. The player's own inventory, experience level (it would drop as orbs) and
     * {@code keepInventory} are put back afterwards.
     */
    private void deathDrops(ServerLevel level) {
        Inventory inventory = player.getInventory();
        List<ItemStack> savedInventory = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            savedInventory.add(inventory.getItem(i).copy());
        }
        int savedLevel = player.experienceLevel;
        boolean keepInventory = level.getGameRules().get(GameRules.KEEP_INVENTORY);
        if (keepInventory) level.getGameRules().set(GameRules.KEEP_INVENTORY, false, level.getServer());
        player.experienceLevel = 0;
        try {
            deathInventory(inventory);
            cancelPlayerDrops = true;
            ((LivingEntityInvoker) player).events$dropAllDeathLoot(level, level.damageSources().generic());
            cancelPlayerDrops = false;
            List<ItemEntity> withheld = playerDrops;
            checks.check("a player's inventory goes through LivingDropsEvent on death, and canceling keeps it out of the level",
                    isDeathInventory(withheld) && withheld.stream().allMatch(item -> joins.getOrDefault(item, 0) == 0) && inventory.isEmpty());

            deathInventory(inventory);
            playerDrops = null;
            ((LivingEntityInvoker) player).events$dropAllDeathLoot(level, level.damageSources().generic());
            List<ItemEntity> dropped = playerDrops;
            checks.check("a player's death drops land in the level when LivingDropsEvent isn't canceled",
                    isDeathInventory(dropped) && dropped.stream().allMatch(item -> joins.getOrDefault(item, 0) == 1) && inventory.isEmpty());
            if (dropped != null) dropped.forEach(Entity::discard);
        } finally {
            cancelPlayerDrops = false;
            inventory.clearContent();
            for (int i = 0; i < savedInventory.size(); i++) {
                inventory.setItem(i, savedInventory.get(i));
            }
            player.experienceLevel = savedLevel;
            if (keepInventory) level.getGameRules().set(GameRules.KEEP_INVENTORY, true, level.getServer());
        }
    }

    private void deathInventory(Inventory inventory) {
        inventory.clearContent();
        inventory.setItem(0, new ItemStack(Items.EMERALD, 3));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
    }

    /** Exactly the inventory {@link #deathInventory} made, as item entities: the stack in a slot and the helmet. */
    private static boolean isDeathInventory(Collection<ItemEntity> drops) {
        return drops != null && drops.size() == 2
                && drops.stream().anyMatch(item -> item.getItem().is(Items.EMERALD) && item.getItem().getCount() == 3)
                && drops.stream().anyMatch(item -> item.getItem().is(Items.DIAMOND_HELMET));
    }

    private InteractionResult useOn(ServerLevel level, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static int note(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getValue(NoteBlock.NOTE);
    }

    private static Entity spawn(ServerLevel level, EntityType<?> type, BlockPos pos) {
        Entity entity = type.create(level, EntitySpawnReason.COMMAND);
        entity.setPos(Vec3.atBottomCenterOf(pos));
        level.addFreshEntity(entity);
        return entity;
    }

    /** Checks made when the server stops: the player logged in once and logged out (the latest player object) once. */
    void finish() {
        checks.check("PlayerLoggedInEvent fired once", loggedIn, 1);
        checks.check("the scenario ran", player != null && ticksUntilRun == 0);
        checks.check("PlayerLoggedOutEvent fired once, for the current player", loggedOut == 1 && loggedOutPlayer == player);
    }
}
