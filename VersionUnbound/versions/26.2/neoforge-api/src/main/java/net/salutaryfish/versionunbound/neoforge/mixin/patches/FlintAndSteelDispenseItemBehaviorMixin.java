package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.FlintAndSteelDispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * NeoForge's patch to {@code FlintAndSteelDispenseItemBehavior}: lighting a block asks {@code getToolModifiedState}
 * with {@code FIRESTARTER_LIGHT} (campfires, candles and candle cakes answer it, and {@code BlockToolModificationEvent}
 * fires), and a flammable block hears {@code onCaughtFire} (TNT primes; only TNT is removed). An {@code @Overwrite} with
 * NeoForge's patched body.
 */
@Mixin(FlintAndSteelDispenseItemBehavior.class)
abstract class FlintAndSteelDispenseItemBehaviorMixin extends OptionalDispenseItemBehavior {
    @Shadow
    private static boolean tryIgniteExplosiveEntities(final ServerLevel level, final BlockPos pos) {
        throw new AssertionError();
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    protected ItemStack execute(final BlockSource source, final ItemStack dispensed) {
        ServerLevel level = source.level();
        this.setSuccess(true);
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        BlockPos targetPos = source.pos().relative(facing);
        BlockState target = level.getBlockState(targetPos);
        if (!tryIgniteExplosiveEntities(level, targetPos)) {
            if (BaseFireBlock.canBePlacedAt(level, targetPos, facing)) {
                level.setBlockAndUpdate(targetPos, BaseFireBlock.getState(level, targetPos));
                level.gameEvent(null, GameEvent.BLOCK_PLACE, targetPos);
            } else if (target.getToolModifiedState(new UseOnContext(level, null, InteractionHand.MAIN_HAND, dispensed, new BlockHitResult(Vec3.atCenterOf(targetPos), facing.getOpposite(), targetPos, false)), ItemAbilities.FIRESTARTER_LIGHT, false) instanceof BlockState blockstate2) {
                level.setBlockAndUpdate(targetPos, blockstate2);
                level.gameEvent(null, GameEvent.BLOCK_CHANGE, targetPos);
            } else if (target.isFlammable(level, targetPos, source.state().getValue(DispenserBlock.FACING).getOpposite())) {
                if (target.onCaughtFire(level, targetPos, source.state().getValue(DispenserBlock.FACING).getOpposite(), null)) {
                    if (target.getBlock() instanceof TntBlock)
                        level.removeBlock(targetPos, false);
                } else {
                    this.setSuccess(false);
                }
            }
        }

        if (this.isSuccess()) {
            dispensed.hurtAndBreak(1, level, null, item -> {});
        }

        return dispensed;
    }
}
