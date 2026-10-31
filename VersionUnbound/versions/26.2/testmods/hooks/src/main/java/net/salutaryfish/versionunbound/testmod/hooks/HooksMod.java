package net.salutaryfish.versionunbound.testmod.hooks;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Checks the vanilla call sites of NeoForge's extension methods (step 5): this mod's blocks and items override them,
 * and vanilla's code has to ask. The checks run at server started, near the top of the world in chunk -1,-1 (the
 * registries testmod's checks use x = z = -8 there; these use z = -12).
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

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    /** Not shears, but can carve pumpkins and trim plants. */
    static final DeferredItem<HookItems.CarvingKnife> CARVING_KNIFE = ITEMS.registerItem("carving_knife", HookItems.CarvingKnife::new);

    public HooksMod(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FireChecks.register();
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> {
            ServerLevel level = event.getServer().overworld();
            Checks checks = new Checks("server checks");
            BlockPos origin = new BlockPos(-8, level.getMaxY() - 8, -12);
            FireChecks.check(checks, level, origin);
            checks.summarize();
        });
    }

    static void clear(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3))) {
            if (!level.getBlockState(pos).isAir()) level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }
}
