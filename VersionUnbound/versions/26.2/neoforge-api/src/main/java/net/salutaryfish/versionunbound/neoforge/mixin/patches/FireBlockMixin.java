package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.salutaryfish.versionunbound.neoforge.patches.FireBlockExtension;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * NeoForge's patch to {@code FireBlock}: the fire asks the block extension's side-aware {@code isFlammable} (through
 * {@code canCatchFire}), {@code getFlammability}, {@code getFireSpreadSpeed} and {@code isFireSource} instead of its
 * own tables and the dimension's infiniburn tag, and a burning block hears {@code onCaughtFire} (TNT primes there,
 * TntBlockMixin, instead of in {@code checkBurnOut}). The extension's defaults read the fire's tables, where Fabric
 * API's flammable block registry answers. {@code @Overwrite}s with NeoForge's patched bodies, so that mods' mixins into
 * them find NeoForge's shape; {@code checkBurnOut} gets NeoForge's six-argument form. It doesn't extend {@code Block}:
 * javac rejects a class inheriting the two {@code getAppearance} defaults (see GrowingPlantHeadBlockMixin).
 */
@Mixin(FireBlock.class)
abstract class FireBlockMixin {
    @Shadow @Final private static Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION;
    @Shadow @Final public static IntegerProperty AGE;

    @Shadow
    protected abstract boolean isNearRain(Level level, BlockPos testPos);

    @Shadow
    protected abstract BlockState getStateWithAge(LevelReader level, BlockPos pos, int age);

    @Shadow
    private static int getFireTickDelay(final RandomSource random) {
        throw new AssertionError();
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    protected BlockState getStateForPlacement(final BlockGetter level, final BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (!this.versionunbound$canCatchFire(level, below, Direction.UP) && !belowState.isFaceSturdy(level, below, Direction.UP)) {
            BlockState result = ((Block) (Object) this).defaultBlockState();

            for (Direction direction : Direction.values()) {
                BooleanProperty property = PROPERTY_BY_DIRECTION.get(direction);
                if (property != null) {
                    result = result.setValue(property, Boolean.valueOf(this.versionunbound$canCatchFire(level, pos.relative(direction), direction.getOpposite())));
                }
            }

            return result;
        } else {
            return ((Block) (Object) this).defaultBlockState();
        }
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    protected void tick(BlockState state, final ServerLevel level, final BlockPos pos, final RandomSource random) {
        level.scheduleTick(pos, (Block) (Object) this, getFireTickDelay(level.getRandom()));
        if (level.canSpreadFireAround(pos)) {
            if (!state.canSurvive(level, pos)) {
                level.removeBlock(pos, false);
            }

            BlockState belowState = level.getBlockState(pos.below());
            boolean infiniBurn = belowState.isFireSource(level, pos.below(), Direction.UP);
            int age = state.getValue(AGE);
            if (!infiniBurn && level.isRaining() && this.isNearRain(level, pos) && random.nextFloat() < 0.2F + age * 0.03F) {
                level.removeBlock(pos, false);
            } else {
                int newAge = Math.min(15, age + random.nextInt(3) / 2);
                if (age != newAge) {
                    state = state.setValue(AGE, newAge);
                    level.setBlock(pos, state, 260);
                }

                if (!infiniBurn) {
                    if (!this.isValidFireLocation(level, pos)) {
                        BlockPos below = pos.below();
                        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || age > 3) {
                            level.removeBlock(pos, false);
                        }

                        return;
                    }

                    if (age == 15 && random.nextInt(4) == 0 && !this.versionunbound$canCatchFire(level, pos.below(), Direction.UP)) {
                        level.removeBlock(pos, false);
                        return;
                    }
                }

                boolean increasedBurnout = level.environmentAttributes().getValue(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, pos);
                int extra = increasedBurnout ? -50 : 0;
                this.checkBurnOut(level, pos.east(), 300 + extra, random, age, Direction.WEST);
                this.checkBurnOut(level, pos.west(), 300 + extra, random, age, Direction.EAST);
                this.checkBurnOut(level, pos.below(), 250 + extra, random, age, Direction.UP);
                this.checkBurnOut(level, pos.above(), 250 + extra, random, age, Direction.DOWN);
                this.checkBurnOut(level, pos.north(), 300 + extra, random, age, Direction.SOUTH);
                this.checkBurnOut(level, pos.south(), 300 + extra, random, age, Direction.NORTH);
                BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos();

                for (int xx = -1; xx <= 1; xx++) {
                    for (int zz = -1; zz <= 1; zz++) {
                        for (int yy = -1; yy <= 4; yy++) {
                            if (xx != 0 || yy != 0 || zz != 0) {
                                int rate = 100;
                                if (yy > 1) {
                                    rate += (yy - 1) * 100;
                                }

                                testPos.setWithOffset(pos, xx, yy, zz);
                                int igniteOdds = this.getIgniteOdds(level, testPos);
                                if (igniteOdds > 0) {
                                    int odds = (igniteOdds + 40 + level.getDifficulty().getId() * 7) / (age + 30);
                                    if (increasedBurnout) {
                                        odds /= 2;
                                    }

                                    if (odds > 0 && random.nextInt(rate) <= odds && (!level.isRaining() || !this.isNearRain(level, testPos))) {
                                        int spreadAge = Math.min(15, age + random.nextInt(5) / 4);
                                        level.setBlock(testPos, this.getStateWithAge(level, testPos, spreadAge), 3);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // NeoForge's six-argument form; vanilla's five-argument one stays (unused).
    private void checkBurnOut(Level level, BlockPos pos, int chance, RandomSource random, int age, Direction face) {
        int odds = level.getBlockState(pos).getFlammability(level, pos, face);
        if (random.nextInt(chance) < odds) {
            BlockState oldState = level.getBlockState(pos);
            oldState.onCaughtFire(level, pos, face, null);

            if (random.nextInt(age + 10) < 5 && !level.isRainingAt(pos)) {
                int newAge = Math.min(age + random.nextInt(5) / 4, 15);
                level.setBlock(pos, this.getStateWithAge(level, pos, newAge), 3);
            } else {
                level.removeBlock(pos, false);
            }
        }
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    private boolean isValidFireLocation(final BlockGetter level, final BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (this.versionunbound$canCatchFire(level, pos.relative(direction), direction.getOpposite())) {
                return true;
            }
        }

        return false;
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    private int getIgniteOdds(final LevelReader level, final BlockPos pos) {
        if (!level.isEmptyBlock(pos)) {
            return 0;
        }

        int odds = 0;

        for (Direction direction : Direction.values()) {
            BlockState blockState = level.getBlockState(pos.relative(direction));
            odds = Math.max(blockState.getFireSpreadSpeed(level, pos.relative(direction), direction.getOpposite()), odds);
        }

        return odds;
    }

    @Inject(method = "setFlammable", at = @At("HEAD"))
    private void versionunbound$noFlammableAir(final Block block, final int igniteOdds, final int burnOdds, final CallbackInfo ci) {
        if (block == Blocks.AIR) throw new IllegalArgumentException("Tried to set air on fire... This is bad.");
    }

    @Unique
    private boolean versionunbound$canCatchFire(final BlockGetter level, final BlockPos pos, final Direction face) {
        return ((FireBlockExtension) (Object) this).canCatchFire(level, pos, face);
    }
}
