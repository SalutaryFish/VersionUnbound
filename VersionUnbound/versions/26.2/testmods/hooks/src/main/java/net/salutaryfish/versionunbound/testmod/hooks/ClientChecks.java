package net.salutaryfish.versionunbound.testmod.hooks;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.salutaryfish.versionunbound.testmod.hooks.mixin.MultiPlayerGameModeInvoker;

/**
 * The client's game mode, {@value #RUN_AT} client ticks after the respawn that ends the server's player scenario (the
 * events testmod's; its {@code Clone}), before the events testmod's client checks act (60 ticks after it), so neither
 * changes the player meanwhile; or after {@value #TIMEOUT} ticks in the level if no respawn comes. {@code useItemOn} asks the
 * stack's {@code onItemUseFirst} before the block (called as its prediction runs it, without the packet that would use the
 * server's hand), and {@code destroyBlock} removes the block through
 * {@code onDestroyedByPlayer} (never harvesting). The item is put in the hand on the client only, and the block placed
 * in the client's level only, above the player; both are put back.
 */
final class ClientChecks {
    private ClientChecks() {}

    private static final int RUN_AT = 20;
    private static final int TIMEOUT = 600;
    private static boolean cloned;
    private static boolean done;
    private static int ticksInLevel;
    private static int ticksAfterClone;

    static void register() {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.Clone.class, event -> cloned = true);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (done || minecraft.level == null || minecraft.player == null) return;
            ticksInLevel++;
            if (cloned) ticksAfterClone++;
            if (ticksAfterClone == RUN_AT || ticksInLevel == TIMEOUT) {
                done = true;
                run(minecraft);
            }
        });
    }

    private static void run(Minecraft minecraft) {
        Checks checks = new Checks("client checks");
        LocalPlayer player = minecraft.player;
        BlockPos ground = player.blockPosition().below();
        ItemStack mainHand = player.getMainHandItem();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HooksMod.FIRST_USER.get()));
            HookItems.FIRST_USES.clear();
            // performUseItemOn is what useItemOn's prediction runs, without the packet.
            InteractionResult used = ((MultiPlayerGameModeInvoker) minecraft.gameMode).hooks$performUseItemOn(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(ground), Direction.UP, ground, false));
            checks.check("the client's useItemOn asks the item's onItemUseFirst first", used == InteractionResult.SUCCESS
                    && HookItems.FIRST_USES.equals(List.of(new HookItems.FirstUse(ground, true))));
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
        }

        BlockPos above = player.blockPosition().above(3);
        BlockState old = minecraft.level.getBlockState(above);
        try {
            minecraft.level.setBlock(above, HooksMod.STUBBORN_BLOCK.get().defaultBlockState(), 2 | 16);
            HookBlocks.DESTROYED_BY_PLAYER.clear();
            boolean destroyed = minecraft.gameMode.destroyBlock(above);
            checks.check("the client's destroyBlock removes the block through onDestroyedByPlayer, never harvesting", !destroyed
                    && minecraft.level.getBlockState(above).is(HooksMod.STUBBORN_BLOCK.get()) && HookBlocks.DESTROYED_BY_PLAYER.size() == 1
                    && !HookBlocks.DESTROYED_BY_PLAYER.getFirst().willHarvest() && HookBlocks.DESTROYED_BY_PLAYER.getFirst().clientSide());
        } finally {
            minecraft.level.setBlock(above, old.isAir() ? Blocks.AIR.defaultBlockState() : old, 2 | 16);
        }
        checks.summarize();
    }
}
