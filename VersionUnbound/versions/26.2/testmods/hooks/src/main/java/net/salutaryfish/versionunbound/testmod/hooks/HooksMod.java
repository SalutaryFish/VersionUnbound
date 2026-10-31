package net.salutaryfish.versionunbound.testmod.hooks;

import com.mojang.logging.LogUtils;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Checks the vanilla call sites of NeoForge's extension methods (step 5): this mod's blocks and items override them,
 * and vanilla's code has to ask. The world checks run at server started, near the top of the world in chunk -1,-1 (the
 * registries testmod's checks use x = z = -8 there; these use z = -12); the player checks (PlayerChecks, ClientChecks)
 * with the first player that joins.
 */
@Mod(HooksMod.MOD_ID)
public final class HooksMod {
    public static final String MOD_ID = "hooks";
    static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    /** Flammable from every side (vanilla's fire tables don't know it), and tells when it catches fire. */
    static final DeferredBlock<HookBlocks.Kindling> KINDLING = BLOCKS.registerBlock("kindling", HookBlocks.Kindling::new);
    /** Not flammable, but a fire source, as netherrack is through the infiniburn tag. */
    static final DeferredBlock<HookBlocks.EmberStone> EMBER_STONE = BLOCKS.registerBlock("ember_stone", HookBlocks.EmberStone::new);
    /** TNT whose onCaughtFire refuses to prime. */
    static final DeferredBlock<HookBlocks.DudTnt> DUD_TNT = BLOCKS.registerBlock("dud_tnt", HookBlocks.DudTnt::new);

    /** Refuses to be removed by a player (onDestroyedByPlayer). */
    static final DeferredBlock<HookBlocks.StubbornBlock> STUBBORN_BLOCK = BLOCKS.registerBlock("stubborn_block", HookBlocks.StubbornBlock::new);

    static final DeferredBlock<HookBlocks.BlastBlock> BLAST_BLOCK = BLOCKS.registerBlock("blast_block", HookBlocks.BlastBlock::new);
    static final DeferredBlock<HookBlocks.GlueBlock> GLUE = BLOCKS.registerBlock("glue", HookBlocks.GlueBlock::new);
    static final DeferredBlock<HookBlocks.RuneShelf> RUNE_SHELF = BLOCKS.registerBlock("rune_shelf", HookBlocks.RuneShelf::new);
    static final DeferredBlock<HookBlocks.Rope> ROPE = BLOCKS.registerBlock("rope", HookBlocks.Rope::new, properties -> properties.noCollision());
    static final DeferredBlock<HookBlocks.Thorns> THORNS = BLOCKS.registerBlock("thorns", HookBlocks.Thorns::new, properties -> properties.noCollision());
    static final DeferredBlock<HookBlocks.Listener> LISTENER = BLOCKS.registerBlock("listener", HookBlocks.Listener::new);
    static final DeferredBlock<HookBlocks.Terminal> TERMINAL = BLOCKS.registerBlock("terminal", HookBlocks.Terminal::new);
    static final DeferredBlock<HookBlocks.Anchor> ANCHOR = BLOCKS.registerBlock("anchor", HookBlocks.Anchor::new);
    static final DeferredBlock<HookBlocks.Cushion> CUSHION = BLOCKS.registerBlock("cushion", HookBlocks.Cushion::new);

    /** A block entity that records the block entity extension's calls (HookBlockEntities). */
    static final DeferredBlock<HookBlockEntities.RecorderBlock> RECORDER = BLOCKS.registerBlock("recorder", HookBlockEntities.RecorderBlock::new);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HookBlockEntities.Recorder>> RECORDER_TYPE = BLOCK_ENTITY_TYPES.register("recorder",
            () -> new BlockEntityType<>(HookBlockEntities.Recorder::new, Set.of(RECORDER.get())));

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    /** Not shears, but can carve pumpkins and trim plants. */
    static final DeferredItem<HookItems.CarvingKnife> CARVING_KNIFE = ITEMS.registerItem("carving_knife", HookItems.CarvingKnife::new);

    /** Uses itself before the block (onItemUseFirst). */
    static final DeferredItem<HookItems.FirstUser> FIRST_USER = ITEMS.registerItem("first_user", HookItems.FirstUser::new);
    /** Lets a sneaking player use blocks (doesSneakBypassUse). */
    static final DeferredItem<HookItems.SneakPass> SNEAK_PASS = ITEMS.registerItem("sneak_pass", HookItems.SneakPass::new);

    static final DeferredItem<HookItems.Ember> EMBER = ITEMS.registerItem("ember", HookItems.Ember::new);
    static final DeferredItem<HookItems.Wand> WAND = ITEMS.registerItem("wand", HookItems.Wand::new);
    static final DeferredItem<HookItems.Pouch> POUCH = ITEMS.registerItem("pouch", HookItems.Pouch::new);

    private static final Checks SERVER_CHECKS = new Checks("server checks");

    public HooksMod(IEventBus modBus, Dist dist) {
        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        FireChecks.register();
        PlayerChecks.register();
        if (dist.isClient()) {
            ClientChecks.register();
        }
        // The world checks run at server started; the block entity checks finish on the first server tick (onLoad),
        // and the group summarizes then.
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> {
            ServerLevel level = event.getServer().overworld();
            BlockPos origin = new BlockPos(-8, level.getMaxY() - 8, -12);
            FireChecks.check(SERVER_CHECKS, level, origin);
            BlockChecks.check(SERVER_CHECKS, level, origin);
            ItemChecks.check(SERVER_CHECKS, level, origin);
            BlockEntityChecks.start(SERVER_CHECKS, level, origin);
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> {
            if (BlockEntityChecks.finish(SERVER_CHECKS, event.getServer().overworld())) SERVER_CHECKS.summarize();
        });
    }

    static void clear(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3))) {
            if (!level.getBlockState(pos).isAir()) level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }
}
