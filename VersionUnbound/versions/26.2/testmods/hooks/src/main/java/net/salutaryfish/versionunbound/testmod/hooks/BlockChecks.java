package net.salutaryfish.versionunbound.testmod.hooks;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * The block extension's other hooks: explosions ({@code canDropFromExplosion}, {@code onBlockExploded}), pistons
 * ({@code isStickyBlock}, {@code canStickTo}), enchanting power ({@code getEnchantPowerBonus}, through Fabric API's
 * enchanting power), ladders ({@code isLadder}, {@code makesOpenTrapdoorAboveClimbable}), path types
 * ({@code getBlockPathType}, {@code getAdjacentBlockPathType}) and neighbour changes ({@code onNeighborChange},
 * {@code getWeakChanges}, {@code onBlockStateChange}).
 */
final class BlockChecks {
    private BlockChecks() {}

    private static final int FLAGS = 2 | 16;

    static void check(Checks checks, ServerLevel level, BlockPos pos) {
        try {
            checkExplosion(checks, level, pos);
            checkPistons(checks, level, pos);
            checkEnchanting(checks, level, pos);
            checkLadders(checks, level, pos);
            checkPathTypes(checks, level, pos);
            checkNeighbors(checks, level, pos);
        } finally {
            HooksMod.clear(level, pos);
        }
    }

    private static void checkExplosion(Checks checks, ServerLevel level, BlockPos pos) {
        HooksMod.clear(level, pos);
        level.setBlock(pos, HooksMod.BLAST_BLOCK.get().defaultBlockState(), FLAGS);
        HookBlocks.HEARD.clear();
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.0F, Level.ExplosionInteraction.BLOCK);
        String at = pos.toShortString();
        checks.check("an explosion asks canDropFromExplosion and removes the block through onBlockExploded", HookBlocks.HEARD,
                List.of("canDropFromExplosion " + at, "onBlockExploded " + at));
        checks.check("onBlockExploded's default removes the block", level.getBlockState(pos).isAir());
    }

    private static void checkPistons(Checks checks, ServerLevel level, BlockPos pos) {
        HooksMod.clear(level, pos);
        BlockPos glue = pos.east();
        level.setBlock(glue, HooksMod.GLUE.get().defaultBlockState(), FLAGS);
        level.setBlock(glue.north(), Blocks.STONE.defaultBlockState(), FLAGS);
        level.setBlock(glue.south(), Blocks.HONEY_BLOCK.defaultBlockState(), FLAGS);
        PistonStructureResolver resolver = new PistonStructureResolver(level, pos, Direction.EAST, true);
        checks.check("a piston pushing a mod's sticky block (isStickyBlock) takes the stone beside it, not the honey it won't stick to (canStickTo)",
                resolver.resolve() && resolver.getToPush().contains(glue) && resolver.getToPush().contains(glue.north())
                        && !resolver.getToPush().contains(glue.south()));
    }

    private static void checkEnchanting(Checks checks, ServerLevel level, BlockPos pos) {
        HooksMod.clear(level, pos);
        BlockPos offset = new BlockPos(2, 0, 0);
        level.setBlock(pos.offset(offset), HooksMod.RUNE_SHELF.get().defaultBlockState(), FLAGS);
        checks.check("a block with enchanting power (getEnchantPowerBonus) outside the tag is a bookshelf", EnchantingTableBlock.isValidBookShelf(level, pos, offset)
                && !EnchantingTableBlock.isValidBookShelf(level, pos, new BlockPos(-2, 0, 0)));
        checks.check("Fabric API's enchanting power answers getEnchantPowerBonus", () -> {
            // FabricBlockState's method, which a NeoForge mod's compiler doesn't see.
            var power = MethodHandles.publicLookup().findVirtual(BlockState.class, "getProvidedEnchantmentPower",
                    MethodType.methodType(float.class, BlockGetter.class, BlockPos.class));
            return (float) power.invoke(level.getBlockState(pos.offset(offset)), level, pos.offset(offset)) == 15.0F
                    && (float) power.invoke(Blocks.BOOKSHELF.defaultBlockState(), level, pos) == 1.0F;
        });
    }

    private static Zombie zombieAt(ServerLevel level, BlockPos pos) {
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        zombie.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return zombie;
    }

    private static void checkLadders(Checks checks, ServerLevel level, BlockPos pos) {
        HooksMod.clear(level, pos);
        level.setBlock(pos, HooksMod.ROPE.get().defaultBlockState(), FLAGS);
        boolean onRope = zombieAt(level, pos).onClimbable();
        level.setBlock(pos.above(), Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true), FLAGS);
        boolean onTrapdoor = zombieAt(level, pos.above()).onClimbable();
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), FLAGS);
        boolean onTrapdoorOverStone = zombieAt(level, pos.above()).onClimbable();
        level.setBlock(pos, Blocks.LADDER.defaultBlockState(), FLAGS);
        boolean onLadder = zombieAt(level, pos).onClimbable();
        checks.check("a mob climbs a mod's ladder (isLadder) and vanilla's, and an open trapdoor over the mod's ladder (makesOpenTrapdoorAboveClimbable)",
                onRope && onTrapdoor && !onTrapdoorOverStone && onLadder);
    }

    private static void checkPathTypes(Checks checks, ServerLevel level, BlockPos pos) {
        HooksMod.clear(level, pos);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), FLAGS);
        level.setBlock(pos.below().east(), Blocks.STONE.defaultBlockState(), FLAGS);
        level.setBlock(pos.east(), HooksMod.THORNS.get().defaultBlockState(), FLAGS);
        Zombie zombie = zombieAt(level, pos);
        checks.check("a mod's block has its own path type (getBlockPathType)", WalkNodeEvaluator.getPathTypeStatic(zombie, pos.east()), PathType.DAMAGING);
        checks.check("next to a mod's block, its getAdjacentBlockPathType decides", WalkNodeEvaluator.getPathTypeStatic(zombie, pos), PathType.STICKY_HONEY);
    }

    private static void checkNeighbors(Checks checks, ServerLevel level, BlockPos pos) {
        HooksMod.clear(level, pos);
        level.setBlock(pos.east(), HooksMod.LISTENER.get().defaultBlockState(), FLAGS);
        level.setBlock(pos.above(), HooksMod.LISTENER.get().defaultBlockState(), FLAGS);
        level.setBlock(pos.west(), Blocks.STONE.defaultBlockState(), FLAGS);
        level.setBlock(pos.west(2), HooksMod.LISTENER.get().defaultBlockState(), FLAGS);
        HookBlocks.HEARD.clear();
        // A composter has an analog output signal: placing it with block updates tells the blocks around it.
        level.setBlock(pos, Blocks.COMPOSTER.defaultBlockState(), 3);
        List<String> heard = List.copyOf(HookBlocks.HEARD);
        String at = pos.toShortString();
        checks.check("a change of a block with an analog output tells its neighbours, vertical ones too (onNeighborChange)",
                heard.contains("onNeighborChange " + pos.east().toShortString() + " from " + at)
                        && heard.contains("onNeighborChange " + pos.above().toShortString() + " from " + at));
        checks.check("and the block past a conductor that wants weak changes (getWeakChanges)", heard.contains("neighborChanged " + pos.west(2).toShortString()));

        HookBlocks.HEARD.clear();
        level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), FLAGS);
        level.setBlock(pos.below(), HooksMod.LISTENER.get().defaultBlockState(), FLAGS);
        checks.check("a block placed in the level hears onBlockStateChange with the state it replaced",
                HookBlocks.HEARD.contains("onBlockStateChange " + pos.below().toShortString() + " was " + Blocks.DIRT.getDescriptionId()));
    }
}
