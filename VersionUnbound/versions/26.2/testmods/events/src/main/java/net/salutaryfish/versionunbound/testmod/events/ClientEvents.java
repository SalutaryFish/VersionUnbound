package net.salutaryfish.versionunbound.testmod.events;

import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.MinecraftAccessor;

/**
 * Client-side checks, for a client that joins a world (singleplayer or a server). After {@value #ACT_AT} client ticks in
 * the level (once the server's player scenario, with its respawn, is over), the client drives its own interaction code:
 * right click on a block and with the hand, an item used up, left click on a block, a block break, and clicks at
 * nothing; some of these canceled. At {@value #LEAVE_AT} ticks the first checks run (including a chunk the client is made to drop, as when the
 * server forgets it) and the client leaves the world; the logging-out and unload checks follow on the next tick, then
 * the summary. Every listener runs on the render thread; events for the integrated
 * server's levels and entities are filtered out.
 */
final class ClientEvents {
    private static final int ACT_AT = 60;
    private static final int LEAVE_AT = 100;
    private static final Checks CHECKS = new Checks("client checks");

    private static int clientTicksPre;
    private static int clientTicksPost;
    private static boolean clientTicksAlternate = true;
    private static ClientLevel loadedLevel;
    private static ClientLevel unloadedLevel;
    private static int levelTicksPre;
    private static int levelTicksPost;
    private static boolean levelTicksAlternate = true;
    private static int localPlayerEntityTicksPre;
    private static int localPlayerEntityTicksPost;
    private static int localPlayerTicksPre;
    private static int localPlayerTicksPost;
    private static boolean playerTickInsideEntityTick = true;
    private static boolean inLocalPlayerEntityTick;

    private static int loggingIn;
    private static ClientPlayerNetworkEvent.Clone clone;
    private static ClientPlayerNetworkEvent.LoggingOut loggingOut;
    private static BlockPos rightClickBlockPos;
    private static int rightClickItems;
    private static PlayerInteractEvent.LeftClickBlock.Action leftClickAction;
    private static BlockPos breakPos;
    private static int rightClickEmpty;
    private static int leftClickEmpty;
    private static boolean cancelClicks;
    private static boolean wrongSide;
    private static int chunkLoads;
    private static boolean chunkLoadsConsistent = true;
    private static ChunkEvent.Unload chunkUnload;
    private static PlayerDestroyItemEvent destroyedItem;
    private static BlockPos neighborNotifyPos;
    private static BlockEvent.NeighborNotifyEvent neighborNotify;

    private static final Set<LocalPlayer> joinedPlayers = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<LocalPlayer> leftPlayers = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int ticksInLevel;
    private static int phase;

    private ClientEvents() {}

    static void register() {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Pre.class, event -> {
            if (clientTicksPre != clientTicksPost) clientTicksAlternate = false;
            clientTicksPre++;
        });
        bus.addListener(ClientTickEvent.Post.class, event -> clientTickPost());
        bus.addListener(BlockEvent.NeighborNotifyEvent.class, event -> {
            if (event.getLevel().isClientSide() && event.getPos().equals(neighborNotifyPos)) neighborNotify = event;
        });
        bus.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel() instanceof ClientLevel level) loadedLevel = level;
        });
        bus.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel() instanceof ClientLevel level) unloadedLevel = level;
        });
        bus.addListener(LevelTickEvent.Pre.class, event -> {
            if (!event.getLevel().isClientSide()) return;
            if (levelTicksPre != levelTicksPost) levelTicksAlternate = false;
            levelTicksPre++;
        });
        bus.addListener(LevelTickEvent.Post.class, event -> {
            if (!event.getLevel().isClientSide()) return;
            levelTicksPost++;
            if (levelTicksPre != levelTicksPost) levelTicksAlternate = false;
        });
        bus.addListener(EntityTickEvent.Pre.class, event -> {
            if (event.getEntity() instanceof LocalPlayer) {
                localPlayerEntityTicksPre++;
                inLocalPlayerEntityTick = true;
            }
        });
        bus.addListener(EntityTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof LocalPlayer) {
                localPlayerEntityTicksPost++;
                inLocalPlayerEntityTick = false;
            }
        });
        bus.addListener(PlayerTickEvent.Pre.class, event -> {
            if (event.getEntity() instanceof LocalPlayer) {
                localPlayerTicksPre++;
                if (!inLocalPlayerEntityTick) playerTickInsideEntityTick = false;
            }
        });
        bus.addListener(PlayerTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof LocalPlayer) localPlayerTicksPost++;
        });

        bus.addListener(EntityJoinLevelEvent.class, event -> {
            if (event.getLevel().isClientSide() && event.getEntity() instanceof LocalPlayer player) joinedPlayers.add(player);
        });
        bus.addListener(EntityLeaveLevelEvent.class, event -> {
            if (event.getLevel().isClientSide() && event.getEntity() instanceof LocalPlayer player) leftPlayers.add(player);
        });
        bus.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> loggingIn++);
        bus.addListener(ClientPlayerNetworkEvent.Clone.class, event -> clone = event);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> loggingOut = event);
        bus.addListener(PlayerInteractEvent.RightClickBlock.class, event -> {
            if (!event.getLevel().isClientSide()) return;
            rightClickBlockPos = event.getPos();
            wrongSide |= event.getSide() != LogicalSide.CLIENT;
            if (cancelClicks) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener(PlayerInteractEvent.RightClickItem.class, event -> {
            if (!event.getLevel().isClientSide()) return;
            rightClickItems++;
            if (cancelClicks) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
        bus.addListener(PlayerInteractEvent.LeftClickBlock.class, event -> {
            if (event.getLevel().isClientSide()) leftClickAction = event.getAction();
        });
        bus.addListener(BreakBlockEvent.class, event -> {
            // The client would remove the block without telling the server, so keep it.
            if (!event.getLevel().isClientSide()) return;
            breakPos = event.getPos();
            event.setCanceled(true);
        });
        bus.addListener(ChunkEvent.Load.class, event -> {
            if (!(event.getLevel() instanceof ClientLevel level)) return;
            chunkLoads++;
            if (event.isNewChunk() || event.getChunk().getLevel() != level) chunkLoadsConsistent = false;
        });
        bus.addListener(ChunkEvent.Unload.class, event -> {
            if (event.getLevel() instanceof ClientLevel) chunkUnload = event;
        });
        bus.addListener(PlayerDestroyItemEvent.class, event -> {
            if (event.getEntity().level().isClientSide()) destroyedItem = event;
        });
        bus.addListener(PlayerInteractEvent.RightClickEmpty.class, event -> rightClickEmpty++);
        bus.addListener(PlayerInteractEvent.LeftClickEmpty.class, event -> leftClickEmpty++);
    }

    private static void clientTickPost() {
        // The loading overlay finishes the game load during a client tick, so on NeoForge too the first tick's Post comes
        // without a Pre (both check the same flag, at the start and at the end of the tick).
        if (clientTicksPre == 0) return;
        clientTicksPost++;
        if (clientTicksPre != clientTicksPost) clientTicksAlternate = false;

        Minecraft minecraft = Minecraft.getInstance();
        if (phase == 2) {
            leftWorld(minecraft);
            return;
        }
        if (phase > 2 || minecraft.level == null || minecraft.player == null) return;
        ticksInLevel++;
        if (ticksInLevel == ACT_AT) {
            act(minecraft);
        } else if (ticksInLevel == LEAVE_AT) {
            inWorld(minecraft);
            phase = 2;
            minecraft.disconnect(new TitleScreen(), false);
        }
    }

    private static void act(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        MultiPlayerGameMode gameMode = minecraft.gameMode;
        BlockPos ground = player.blockPosition().below();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false);

        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        CHECKS.check("RightClickBlock fires on the client", ground.equals(rightClickBlockPos) && !wrongSide);
        gameMode.useItem(player, InteractionHand.MAIN_HAND);
        CHECKS.check("RightClickItem fires on the client", rightClickItems, 1);
        cancelClicks = true;
        InteractionResult canceledBlock = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        InteractionResult canceledItem = gameMode.useItem(player, InteractionHand.MAIN_HAND);
        cancelClicks = false;
        CHECKS.check("canceling RightClickBlock and RightClickItem on the client returns their cancellation results",
                canceledBlock == InteractionResult.FAIL && canceledItem == InteractionResult.FAIL);

        // An item whose use leaves the hand empty, put there on the client only (the server's hand stays empty).
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EventsMod.VANISHING_ITEM.get()));
        gameMode.useItem(player, InteractionHand.MAIN_HAND);
        CHECKS.check("PlayerDestroyItemEvent fires on the client when using an item leaves the hand empty",
                destroyedItem != null && destroyedItem.getEntity() == player && destroyedItem.getHand() == InteractionHand.MAIN_HAND
                        && destroyedItem.getOriginal().is(EventsMod.VANISHING_ITEM.get()) && player.getMainHandItem().isEmpty());

        gameMode.startDestroyBlock(ground, Direction.UP);
        gameMode.stopDestroyBlock();
        CHECKS.check("LeftClickBlock fires on the client when breaking starts", leftClickAction, PlayerInteractEvent.LeftClickBlock.Action.START);
        boolean broken = gameMode.destroyBlock(ground);
        CHECKS.check("BreakBlockEvent fires on the client, and canceling it keeps the block",
                ground.equals(breakPos) && !broken && !minecraft.level.getBlockState(ground).isAir());

        // Clicks at nothing, with empty hands.
        minecraft.hitResult = BlockHitResult.miss(player.getEyePosition(), Direction.UP, player.blockPosition().above(5));
        ((MinecraftAccessor) minecraft).events$startUseItem();
        ((MinecraftAccessor) minecraft).events$startAttack();
        CHECKS.check("RightClickEmpty fires for each empty hand, LeftClickEmpty for an attack at nothing", rightClickEmpty == 2 && leftClickEmpty == 1);

        // NeoForge's Level patch fires NeighborNotifyEvent in the three-argument updateNeighborsAt, which on the client
        // only pistons call (they move blocks on both sides).
        neighborNotifyPos = player.blockPosition().above(6);
        minecraft.level.updateNeighborsAt(neighborNotifyPos, Blocks.STONE, null);
        CHECKS.check("NeighborNotifyEvent fires for the client level's three-argument updateNeighborsAt, with all six sides",
                neighborNotify != null && neighborNotify.getLevel() == minecraft.level && neighborNotify.getNotifiedSides().equals(EnumSet.allOf(Direction.class)));
    }

    private static void inWorld(Minecraft minecraft) {
        CHECKS.check("client ticks fired, Pre and Post in turn", clientTicksPre > LEAVE_AT && clientTicksAlternate);
        CHECKS.check("the client level fired LevelEvent.Load", loadedLevel == minecraft.level);
        CHECKS.check("client level ticks fired, Pre and Post in turn", levelTicksPre > 0 && levelTicksAlternate && levelTicksPre == levelTicksPost);
        CHECKS.check("the local player's entity ticks fired", localPlayerEntityTicksPre > 0 && localPlayerEntityTicksPre == localPlayerEntityTicksPost);
        CHECKS.check("the local player's player ticks fired, inside its entity ticks",
                localPlayerTicksPre > 0 && localPlayerTicksPre == localPlayerTicksPost && playerTickInsideEntityTick);
        CHECKS.check("ClientPlayerNetworkEvent.LoggingIn fired once", loggingIn, 1);
        // The server's player scenario ends with a respawn.
        CHECKS.check("ClientPlayerNetworkEvent.Clone fired on the respawn, with the new local player",
                clone != null && clone.getOldPlayer() != clone.getNewPlayer() && clone.getNewPlayer() == minecraft.player);
        CHECKS.check("the local player joined the client level, and the old one left it on the respawn",
                joinedPlayers.contains(minecraft.player) && clone != null && leftPlayers.contains(clone.getOldPlayer()));

        CHECKS.check("ChunkEvent.Load fires for the chunks the server sends, never as new chunks", chunkLoads > 0 && chunkLoadsConsistent);
        // A chunk two chunks east of the player, dropped as ClientboundForgetLevelChunkPacket's handler does.
        ClientLevel level = minecraft.level;
        ChunkPos center = minecraft.player.chunkPosition();
        ChunkPos east = new ChunkPos(center.x() + 2, center.z());
        LevelChunk dropped = level.getChunkSource().getChunk(east.x(), east.z(), ChunkStatus.FULL, false);
        level.getChunkSource().drop(east);
        CHECKS.check("ChunkEvent.Unload fires when the client drops a chunk",
                dropped != null && chunkUnload != null && chunkUnload.getChunk() == dropped && chunkUnload.getLevel() == level);
    }

    private static void leftWorld(Minecraft minecraft) {
        phase = 3;
        CHECKS.check("ClientPlayerNetworkEvent.LoggingOut fired on leaving, with the player", loggingOut != null && loggingOut.getPlayer() != null);
        CHECKS.check("the client level fired LevelEvent.Unload on leaving", unloadedLevel != null && unloadedLevel == loadedLevel && minecraft.level == null);
        CHECKS.summarize();
    }
}
