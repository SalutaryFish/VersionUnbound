package net.salutaryfish.versionunbound.testmod.hooks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.FlintAndSteelDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Fire, TNT and the fire starters: NeoForge's patches to FireBlock, TntBlock, the flint and steel dispenser, flint and
 * steel, fire charges and shears ask the block extension (isFlammable, isFireSource, onCaughtFire) and
 * getToolModifiedState.
 */
final class FireChecks {
    private FireChecks() {}

    private static final List<ItemAbility> TOOL_ABILITIES = new ArrayList<>();
    private static boolean listening;
    private static boolean cancelToolUse;
    private static final List<PrimedTnt> PRIMED = new ArrayList<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(BlockEvent.BlockToolModificationEvent.class, event -> {
            if (!listening) return;
            TOOL_ABILITIES.add(event.getItemAbility());
            if (cancelToolUse) event.setCanceled(true);
        });
        // Primed TNT is counted as it joins: at server started the chunk's entities aren't visible to getEntities yet.
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, event -> {
            if (listening && event.getEntity() instanceof PrimedTnt tnt) PRIMED.add(tnt);
        });
    }

    static void check(Checks checks, ServerLevel level, BlockPos pos) {
        int fireRadius = level.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
        try {
            listening = true;
            checkFire(checks, level, pos);
            checkTnt(checks, level, pos);
            checkFireStarters(checks, level, pos);
            checkShears(checks, level, pos);
        } finally {
            listening = false;
            cancelToolUse = false;
            level.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, fireRadius, level.getServer());
            HooksMod.clear(level, pos);
            PRIMED.forEach(PrimedTnt::discard);
            PRIMED.clear();
        }
    }

    private static void checkFire(Checks checks, ServerLevel level, BlockPos pos) {
        HooksMod.clear(level, pos);
        level.setBlock(pos.north(), HooksMod.KINDLING.get().defaultBlockState(), 2 | 16);
        checks.check("FireBlock.canCatchFire asks the block's isFlammable", () -> {
            // A method NeoForge's patch adds to FireBlock directly, which javac doesn't see here.
            MethodHandle canCatchFire = MethodHandles.publicLookup().findVirtual(FireBlock.class, "canCatchFire",
                    MethodType.methodType(boolean.class, BlockGetter.class, BlockPos.class, Direction.class));
            return (boolean) canCatchFire.invoke((FireBlock) Blocks.FIRE, level, pos.north(), Direction.SOUTH)
                    && !(boolean) canCatchFire.invoke((FireBlock) Blocks.FIRE, level, pos.south(), Direction.NORTH);
        });
        BlockState fire = BaseFireBlock.getState(level, pos);
        checks.check("fire placed next to a mod's flammable block burns on that side", fire.is(Blocks.FIRE)
                && fire.getValue(FireBlock.NORTH) && !fire.getValue(FireBlock.SOUTH));
        checks.check("fire survives next to a mod's flammable block", Blocks.FIRE.defaultBlockState().canSurvive(level, pos));

        // On a block that isn't flammable, with nothing flammable around, a fire older than 3 goes out on its tick,
        // unless the block below is a fire source.
        level.getGameRules().set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, -1, level.getServer());
        HooksMod.clear(level, pos);
        level.setBlock(pos.below(), HooksMod.EMBER_STONE.get().defaultBlockState(), 2 | 16);
        level.setBlock(pos, Blocks.FIRE.defaultBlockState().setValue(FireBlock.AGE, 5), 2 | 16);
        level.getBlockState(pos).tick(level, pos, level.getRandom());
        boolean burning = level.getBlockState(pos).is(Blocks.FIRE);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2 | 16);
        level.setBlock(pos, Blocks.FIRE.defaultBlockState().setValue(FireBlock.AGE, 5), 2 | 16);
        level.getBlockState(pos).tick(level, pos, level.getRandom());
        boolean out = !level.getBlockState(pos).is(Blocks.FIRE);
        checks.check("a fire on a mod's fire source keeps burning (isFireSource), on stone it goes out", burning && out);
    }

    private static int primedTnt(BlockPos pos) {
        return (int) PRIMED.stream().filter(tnt -> tnt.blockPosition().equals(pos)).count();
    }

    private static void checkTnt(Checks checks, ServerLevel level, BlockPos pos) {
        HooksMod.clear(level, pos);
        level.setBlock(pos, Blocks.TNT.defaultBlockState(), 2 | 16);
        checks.check("TNT's onCaughtFire primes it", Blocks.TNT.defaultBlockState().onCaughtFire(level, pos, null, null) && primedTnt(pos) == 1);
        HooksMod.clear(level, pos);
        PRIMED.forEach(PrimedTnt::discard);
        PRIMED.clear();

        HookBlocks.CAUGHT_FIRE.clear();
        level.setBlock(pos, HooksMod.DUD_TNT.get().defaultBlockState(), 2 | 16);
        level.setBlock(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        checks.check("powered, a mod's TNT is asked onCaughtFire and stays when it refuses", level.getBlockState(pos).is(HooksMod.DUD_TNT.get())
                && HookBlocks.CAUGHT_FIRE.equals(List.of(new HookBlocks.CaughtFire(pos, null))) && primedTnt(pos) == 0);
        level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 2 | 16);
    }

    private static ItemStack dispense(ServerLevel level, BlockPos dispenser) {
        BlockState state = Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST);
        level.setBlock(dispenser, state, 2 | 16);
        return new FlintAndSteelDispenseItemBehavior().dispense(new BlockSource(level, dispenser, state, null), new ItemStack(Items.FLINT_AND_STEEL));
    }

    private static UseOnContext useOn(ServerLevel level, BlockPos pos, ItemStack stack) {
        return new UseOnContext(level, null, InteractionHand.OFF_HAND, stack, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static void checkFireStarters(Checks checks, ServerLevel level, BlockPos pos) {
        BlockPos dispenser = pos.west();
        HooksMod.clear(level, pos);
        level.setBlock(pos, Blocks.CANDLE.defaultBlockState(), 2 | 16);
        TOOL_ABILITIES.clear();
        dispense(level, dispenser);
        checks.check("the flint and steel dispenser lights a candle through getToolModifiedState", level.getBlockState(pos).getValue(CandleBlock.LIT)
                && TOOL_ABILITIES.equals(List.of(ItemAbilities.FIRESTARTER_LIGHT)));

        level.setBlock(pos, HooksMod.KINDLING.get().defaultBlockState(), 2 | 16);
        HookBlocks.CAUGHT_FIRE.clear();
        dispense(level, dispenser);
        checks.check("the flint and steel dispenser sets a mod's flammable block on fire from its side", level.getBlockState(pos).is(HooksMod.KINDLING.get())
                && HookBlocks.CAUGHT_FIRE.equals(List.of(new HookBlocks.CaughtFire(pos, Direction.WEST))));

        level.setBlock(pos, Blocks.TNT.defaultBlockState(), 2 | 16);
        dispense(level, dispenser);
        checks.check("the flint and steel dispenser primes TNT through onCaughtFire", level.getBlockState(pos).isAir() && primedTnt(pos) == 1);
        level.setBlock(dispenser, Blocks.AIR.defaultBlockState(), 2 | 16);

        BlockState unlit = Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false);
        level.setBlock(pos, unlit, 2 | 16);
        TOOL_ABILITIES.clear();
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        flint.useOn(useOn(level, pos, flint));
        boolean lit = level.getBlockState(pos).getValue(CampfireBlock.LIT) && TOOL_ABILITIES.equals(List.of(ItemAbilities.FIRESTARTER_LIGHT));
        level.setBlock(pos, unlit, 2 | 16);
        cancelToolUse = true;
        flint.useOn(useOn(level, pos, flint));
        cancelToolUse = false;
        checks.check("flint and steel lights a campfire through getToolModifiedState, and not when the event is canceled", lit
                && !level.getBlockState(pos).getValue(CampfireBlock.LIT));

        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2 | 16);
        level.setBlock(pos, Blocks.CANDLE.defaultBlockState(), 2 | 16);
        TOOL_ABILITIES.clear();
        ItemStack charge = new ItemStack(Items.FIRE_CHARGE, 2);
        charge.useOn(useOn(level, pos, charge));
        checks.check("a fire charge lights a candle through getToolModifiedState", level.getBlockState(pos).getValue(CandleBlock.LIT)
                && TOOL_ABILITIES.equals(List.of(ItemAbilities.FIRESTARTER_LIGHT)) && charge.getCount() == 1);
    }

    private static void checkShears(Checks checks, ServerLevel level, BlockPos pos) {
        HooksMod.clear(level, pos);
        level.setBlock(pos, Blocks.WEEPING_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, 0), 2 | 16);
        TOOL_ABILITIES.clear();
        ItemStack shears = new ItemStack(Items.SHEARS);
        shears.useOn(useOn(level, pos, shears));
        checks.check("shears trim a growing plant through getToolModifiedState", level.getBlockState(pos).getValue(GrowingPlantHeadBlock.AGE) == GrowingPlantHeadBlock.MAX_AGE
                && TOOL_ABILITIES.equals(List.of(ItemAbilities.SHEARS_TRIM)));
    }
}
