package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * NeoForge's patch to {@code LivingEntity.onClimbable}: climbing asks {@code CommonHooks.isLivingOnLadder}, that is the
 * block state's {@code isLadder} (whose default is the climbable tag; trapdoors override it, TrapDoorBlockMixin), in
 * place of vanilla's tag and trapdoor checks. An {@code @Overwrite} with NeoForge's patched body.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityClimbingMixin {
    @Shadow
    private Optional<BlockPos> lastClimbablePos;

    @Shadow
    public abstract boolean isFallFlying();

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    public boolean onClimbable() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isSpectator()) {
            return false;
        } else {
            BlockPos ladderCheckPos = self.blockPosition();
            BlockState state = self.getInBlockState();
            if (this.isFallFlying() && state.is(BlockTags.CAN_GLIDE_THROUGH))
                return false;
            Optional<BlockPos> ladderPos = CommonHooks.isLivingOnLadder(state, self.level(), ladderCheckPos, self);
            if (ladderPos.isPresent()) this.lastClimbablePos = ladderPos;
            return ladderPos.isPresent();
        }
    }
}
