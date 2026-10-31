package net.salutaryfish.versionunbound.testmod.hooks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * The server's game mode with the first player that joins (a few ticks after its client has loaded the level):
 * {@code useItemOn} asks the stack's {@code onItemUseFirst} (with {@code UseItemOnBlockEvent}'s {@code ITEM_BEFORE_BLOCK})
 * before the block and both hands' {@code doesSneakBypassUse} when sneaking, and {@code destroyBlock} removes the block
 * through {@code onDestroyedByPlayer}. The uses pass stacks to {@code useItemOn} rather than putting them in the hand,
 * except for the sneak check, which reads the hands (and puts them back). Then it places the recorder block entity near the
 * player (6 blocks back on x and z, 4 up) for ClientChecks (removed at server stopping).
 */
final class PlayerChecks {
    private PlayerChecks() {}

    private static final int DELAY = 5;
    private static final List<UseItemOnBlockEvent.UsePhase> PHASES = new ArrayList<>();
    private static @Nullable BlockPos watched;
    private static boolean cancelBeforeBlock;
    private static @Nullable ServerPlayer player;
    private static int ticksUntilRun = -1;
    private static @Nullable BlockPos recorderPos;

    static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (player == null && ticksUntilRun < 0 && event.getEntity() instanceof ServerPlayer serverPlayer) {
                player = serverPlayer;
                ticksUntilRun = DELAY;
            }
        });
        NeoForge.EVENT_BUS.addListener(UseItemOnBlockEvent.class, event -> {
            if (event.getLevel().isClientSide() || !event.getPos().equals(watched)) return;
            PHASES.add(event.getUsePhase());
            if (cancelBeforeBlock && event.getUsePhase() == UseItemOnBlockEvent.UsePhase.ITEM_BEFORE_BLOCK) event.cancelWithResult(InteractionResult.FAIL);
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            ServerPlayer target = player;
            if (target == null || ticksUntilRun <= 0 || !target.connection.hasClientLoaded() || --ticksUntilRun > 0) return;
            player = null;
            Checks checks = new Checks("server player checks");
            ServerLevel level = target.level();
            BlockPos pos = new BlockPos(-8, level.getMaxY() - 8, -16);
            try {
                check(checks, target, level, pos);
            } finally {
                watched = null;
                cancelBeforeBlock = false;
                HookItems.firstUseResult = InteractionResult.SUCCESS;
                HooksMod.clear(level, pos);
            }
            checks.summarize();
            // For ClientChecks: a block entity near the player, whose update the client hears (onLoad, onDataPacket).
            // Off the events testmod's player scenario, which builds from 3 blocks above the player towards +x and +z.
            BlockPos recorder = target.blockPosition().offset(-6, 4, -6);
            if (level.getBlockState(recorder).isAir()) {
                level.setBlock(recorder, HooksMod.RECORDER.get().defaultBlockState(), 3);
                recorderPos = recorder;
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> {
            BlockPos recorder = recorderPos;
            if (recorder != null) event.getServer().overworld().setBlock(recorder, Blocks.AIR.defaultBlockState(), 3);
        });
    }

    private static InteractionResult useOn(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
    }

    private static void check(Checks checks, ServerPlayer player, ServerLevel level, BlockPos pos) {
        HooksMod.clear(level, pos);
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2 | 16);
        watched = pos;

        PHASES.clear();
        HookItems.FIRST_USES.clear();
        InteractionResult first = useOn(player, level, pos, new ItemStack(HooksMod.FIRST_USER.get()));
        checks.check("useItemOn: UseItemOnBlockEvent ITEM_BEFORE_BLOCK, then the item's onItemUseFirst, whose SUCCESS ends the use", first == InteractionResult.SUCCESS
                && PHASES.equals(List.of(UseItemOnBlockEvent.UsePhase.ITEM_BEFORE_BLOCK)) && HookItems.FIRST_USES.equals(List.of(new HookItems.FirstUse(pos, false))));

        PHASES.clear();
        HookItems.FIRST_USES.clear();
        HookItems.firstUseResult = InteractionResult.PASS;
        useOn(player, level, pos, new ItemStack(HooksMod.FIRST_USER.get()));
        HookItems.firstUseResult = InteractionResult.SUCCESS;
        checks.check("useItemOn: onItemUseFirst's PASS goes on to the block and the item", PHASES, List.of(UseItemOnBlockEvent.UsePhase.ITEM_BEFORE_BLOCK,
                UseItemOnBlockEvent.UsePhase.BLOCK, UseItemOnBlockEvent.UsePhase.ITEM_AFTER_BLOCK));

        PHASES.clear();
        HookItems.FIRST_USES.clear();
        cancelBeforeBlock = true;
        InteractionResult canceled = useOn(player, level, pos, new ItemStack(HooksMod.FIRST_USER.get()));
        cancelBeforeBlock = false;
        checks.check("useItemOn: canceling ITEM_BEFORE_BLOCK returns its result without asking the item", canceled == InteractionResult.FAIL
                && HookItems.FIRST_USES.isEmpty());

        // Sneaking with an item suppresses the block, unless both hands' items bypass (an empty hand does).
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean shift = player.isShiftKeyDown();
        try {
            player.setShiftKeyDown(true);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            PHASES.clear();
            useOn(player, level, pos, player.getMainHandItem());
            boolean suppressed = !PHASES.contains(UseItemOnBlockEvent.UsePhase.BLOCK);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HooksMod.SNEAK_PASS.get()));
            PHASES.clear();
            useOn(player, level, pos, player.getMainHandItem());
            checks.check("useItemOn: sneaking with an item skips the block, unless the items' doesSneakBypassUse lets it through", suppressed
                    && PHASES.contains(UseItemOnBlockEvent.UsePhase.BLOCK));
        } finally {
            player.setShiftKeyDown(shift);
            player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
            player.setItemInHand(InteractionHand.OFF_HAND, offHand);
        }

        level.setBlock(pos, HooksMod.STUBBORN_BLOCK.get().defaultBlockState(), 2 | 16);
        HookBlocks.DESTROYED_BY_PLAYER.clear();
        ItemStack tool = player.getMainHandItem().copy();
        boolean destroyed = player.gameMode.destroyBlock(pos);
        checks.check("destroyBlock removes the block through onDestroyedByPlayer, with the tool and whether the player harvests it", destroyed
                && level.getBlockState(pos).is(HooksMod.STUBBORN_BLOCK.get()) && HookBlocks.DESTROYED_BY_PLAYER.size() == 1
                && HookBlocks.DESTROYED_BY_PLAYER.getFirst().pos().equals(pos) && ItemStack.matches(HookBlocks.DESTROYED_BY_PLAYER.getFirst().tool(), tool)
                && HookBlocks.DESTROYED_BY_PLAYER.getFirst().willHarvest() == !player.preventsBlockDrops() && !HookBlocks.DESTROYED_BY_PLAYER.getFirst().clientSide());
        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2 | 16);
        checks.check("destroyBlock: a vanilla block goes through onDestroyedByPlayer's default", player.gameMode.destroyBlock(pos) && level.getBlockState(pos).isAir());
    }
}
