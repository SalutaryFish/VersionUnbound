package net.salutaryfish.versionunbound.testmod.registries;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge's extension interfaces on blocks, block states, items, stacks, block entities and levels, called the way a
 * mod calls them (the testmod class tweaker puts them on vanilla's classes), and the vanilla tools that ask
 * {@code getToolModifiedState}, with {@code BlockToolModificationEvent}. Runs at server started, at the top of the
 * world in chunk 0,0 (the events testmod's scenarios use x and z up to 16 there; these use x = -8).
 */
final class ExtensionChecks {
    private ExtensionChecks() {}

    private record ToolUse(BlockState state, ItemAbility ability, boolean simulate) {}

    private static final List<ToolUse> TOOL_USES = new ArrayList<>();
    private static boolean listening;
    private static boolean cancelToolUse;
    private static @Nullable BlockState toolUseResult;
    private static int coalBurnTime = -1;

    static void register() {
        NeoForge.EVENT_BUS.addListener(BlockEvent.BlockToolModificationEvent.class, event -> {
            if (!listening) return;
            TOOL_USES.add(new ToolUse(event.getState(), event.getItemAbility(), event.isSimulated()));
            if (cancelToolUse) event.setCanceled(true);
            if (toolUseResult != null) event.setFinalState(toolUseResult);
        });
        NeoForge.EVENT_BUS.addListener(FurnaceFuelBurnTimeEvent.class, event -> {
            if (coalBurnTime >= 0 && event.getItemStack().is(Items.CHARCOAL)) event.setBurnTime(coalBurnTime);
        });
    }

    static void checkWorld(Checks checks, ServerLevel level) {
        BlockPos pos = new BlockPos(-8, level.getMaxY() - 2, -8);
        try {
            listening = true;
            checkBlocks(checks, level, pos);
            checkTools(checks, level, pos);
            checkItems(checks, level);
            checkBlockEntities(checks, level, pos);
            checkLevel(checks, level, pos);
        } finally {
            listening = false;
            cancelToolUse = false;
            toolUseResult = null;
            coalBurnTime = -1;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }

    private static void checkBlocks(Checks checks, ServerLevel level, BlockPos pos) {
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        checks.check("block extension defaults: flammability and fire spread come from the fire block", planks.getFlammability(level, pos, Direction.UP) == 20
                && planks.getFireSpreadSpeed(level, pos, Direction.UP) == 5 && planks.isFlammable(level, pos, Direction.UP)
                && !Blocks.STONE.defaultBlockState().isFlammable(level, pos, Direction.UP));
        // Fabric API's FabricBlock and FabricBlockState declare the same default; without Layer A's own method on Block
        // and BlockState this call would throw IncompatibleClassChangeError.
        checks.check("getAppearance (NeoForge's and Fabric API's) on vanilla blocks", () -> {
            BlockState stone = Blocks.STONE.defaultBlockState();
            return stone.getAppearance(level, pos, Direction.UP, null, null) == stone
                    && Blocks.STONE.getAppearance(stone, level, pos, Direction.UP, null, null) == stone;
        });
        checks.check("NeoForge's tags and the tag key overloads", () -> {
            var create = MethodHandles.publicLookup().findStatic(BlockTags.class, "create", MethodType.methodType(TagKey.class, Identifier.class));
            TagKey<?> key = (TagKey<?>) create.invoke(Identifier.fromNamespaceAndPath("c", "ores"));
            // DyeColor's tags are members NeoForge's patch adds directly, which javac doesn't see here.
            var getTag = MethodHandles.publicLookup().findVirtual(DyeColor.class, "getTag", MethodType.methodType(TagKey.class));
            var getDyedTag = MethodHandles.publicLookup().findVirtual(DyeColor.class, "getDyedTag", MethodType.methodType(TagKey.class));
            return key == net.neoforged.neoforge.common.Tags.Blocks.ORES && ((TagKey<?>) getTag.invoke(DyeColor.RED)).location().toString().equals("c:dyes/red")
                    && ((TagKey<?>) getDyedTag.invoke(DyeColor.BLUE)).location().toString().equals("c:dyed/blue");
        });
        checks.check("DyeColor.getColor", () -> MethodHandles.publicLookup().findStatic(DyeColor.class, "getColor", MethodType.methodType(DyeColor.class, ItemStack.class))
                .invoke(new ItemStack(Items.DYE.red())) == DyeColor.RED);
    }

    private static InteractionResult use(ServerLevel level, BlockPos pos, ItemStack tool) {
        // The off hand: the axe asks the player's off hand otherwise, and these uses have no player.
        return tool.useOn(new UseOnContext(level, null, InteractionHand.OFF_HAND, tool, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    private static void checkTools(Checks checks, ServerLevel level, BlockPos pos) {
        checks.check("vanilla tools' item abilities", new ItemStack(Items.IRON_AXE).canPerformAction(ItemAbilities.AXE_STRIP)
                && new ItemStack(Items.SHEARS).canPerformAction(ItemAbilities.SHEARS_DIG) && !new ItemStack(Items.STICK).canPerformAction(ItemAbilities.AXE_STRIP)
                && new ItemStack(Items.IRON_SWORD).canPerformAction(ItemAbilities.SWORD_SWEEP));

        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        level.setBlock(pos, log, 2 | 16);
        TOOL_USES.clear();
        InteractionResult stripped = use(level, pos, new ItemStack(Items.IRON_AXE));
        checks.check("an axe strips a log through getToolModifiedState, firing BlockToolModificationEvent", stripped.consumesAction()
                && level.getBlockState(pos).is(Blocks.STRIPPED_OAK_LOG) && TOOL_USES.equals(List.of(new ToolUse(log, ItemAbilities.AXE_STRIP, false))));

        level.setBlock(pos, log, 2 | 16);
        cancelToolUse = true;
        TOOL_USES.clear();
        InteractionResult canceled = use(level, pos, new ItemStack(Items.IRON_AXE));
        cancelToolUse = false;
        // Canceled, each of the axe's three abilities gets nothing.
        checks.check("canceling BlockToolModificationEvent keeps the axe from stripping", !canceled.consumesAction() && level.getBlockState(pos) == log
                && TOOL_USES.stream().map(ToolUse::ability).toList().equals(List.of(ItemAbilities.AXE_STRIP, ItemAbilities.AXE_SCRAPE, ItemAbilities.AXE_WAX_OFF)));

        toolUseResult = Blocks.GOLD_BLOCK.defaultBlockState();
        use(level, pos, new ItemStack(Items.IRON_AXE));
        toolUseResult = null;
        checks.check("BlockToolModificationEvent's final state replaces the result", level.getBlockState(pos).is(Blocks.GOLD_BLOCK));

        level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2 | 16);
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
        use(level, pos, new ItemStack(Items.IRON_SHOVEL));
        boolean path = level.getBlockState(pos).is(Blocks.DIRT_PATH);
        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2 | 16);
        use(level, pos, new ItemStack(Items.IRON_HOE));
        boolean farmland = level.getBlockState(pos).is(Blocks.FARMLAND);
        level.setBlock(pos, Blocks.CAMPFIRE.defaultBlockState(), 2 | 16);
        use(level, pos, new ItemStack(Items.IRON_SHOVEL));
        boolean doused = !level.getBlockState(pos).getValue(CampfireBlock.LIT);
        checks.check("a shovel flattens and douses, a hoe tills, through getToolModifiedState", path && farmland && doused);

        BlockState simulated = Blocks.OAK_LOG.defaultBlockState().getToolModifiedState(
                new UseOnContext(level, null, InteractionHand.OFF_HAND, new ItemStack(Items.IRON_AXE), new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)),
                ItemAbilities.AXE_STRIP, true);
        checks.check("getToolModifiedState simulated", simulated != null && simulated.is(Blocks.STRIPPED_OAK_LOG) && TOOL_USES.getLast().simulate());
    }

    private static void checkItems(Checks checks, ServerLevel level) {
        checks.check("stack burn times, and FurnaceFuelBurnTimeEvent", () -> {
            int coal = new ItemStack(Items.COAL).getBurnTime(RecipeType.SMELTING, level.fuelValues());
            coalBurnTime = 7;
            int charcoal = new ItemStack(Items.CHARCOAL).getBurnTime(RecipeType.SMELTING, level.fuelValues());
            coalBurnTime = -1;
            return coal == 1600 && charcoal == 7;
        });
        checks.check("combine repairs: damageable items only", new ItemStack(Items.IRON_SWORD).isCombineRepairable() && !new ItemStack(Items.STICK).isCombineRepairable());
        checks.check("the creator mod id of an item", () -> RegistriesMod.PROBE_ITEM.toStack().getItem().getCreatorModId(level.registryAccess(), RegistriesMod.PROBE_ITEM.toStack())
                .equals(RegistriesMod.MOD_ID) && new ItemStack(Items.STONE).getItem().getCreatorModId(level.registryAccess(), new ItemStack(Items.STONE)).equals("minecraft"));
    }

    private static void checkBlockEntities(Checks checks, ServerLevel level, BlockPos pos) {
        checks.check("a block entity's persistent data is saved as NeoForgeData and loaded", () -> {
            BlockState chest = Blocks.CHEST.defaultBlockState();
            var blockEntity = new ChestBlockEntity(pos, chest);
            blockEntity.getPersistentData().putInt("answer", 42);
            CompoundTag saved = blockEntity.saveWithFullMetadata(level.registryAccess());
            BlockEntity loaded = BlockEntity.loadStatic(pos, chest, saved, level.registryAccess());
            return saved.getCompoundOrEmpty("NeoForgeData").getIntOr("answer", 0) == 42 && loaded != null
                    && loaded.getPersistentData().getIntOr("answer", 0) == 42 && new ChestBlockEntity(pos, chest).getPersistentData().isEmpty();
        });
    }

    private static void checkLevel(Checks checks, ServerLevel level, BlockPos pos) {
        checks.check("the level's entity radius", level.getMaxEntityRadius() == 2.0);
        checks.check("capability listeners hear invalidations of their position until they ask to be removed", () -> {
            var invalidations = new AtomicInteger();
            ICapabilityInvalidationListener listener = () -> {
                invalidations.incrementAndGet();
                return false; // removes the listener
            };
            // A member NeoForge's patch adds to ServerLevel directly, which javac doesn't see here.
            MethodHandles.publicLookup().findVirtual(ServerLevel.class, "registerCapabilityListener",
                    MethodType.methodType(void.class, BlockPos.class, ICapabilityInvalidationListener.class)).invoke(level, pos, listener);
            level.invalidateCapabilities(pos);
            level.invalidateCapabilities(pos);
            return invalidations.get() == 1;
        });
        checks.check("a level's description", level.getDescriptionKey().equals("dimension.minecraft.overworld"));
    }
}
