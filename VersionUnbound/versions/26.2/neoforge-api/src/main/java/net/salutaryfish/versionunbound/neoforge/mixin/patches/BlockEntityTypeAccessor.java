package net.salutaryfish.versionunbound.neoforge.mixin.patches;

import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** NeoForge's {@code net.neoforged.neoforge.mixins.BlockEntityTypeAccessor}, verbatim but for the package. */
@Mixin(BlockEntityType.class)
public interface BlockEntityTypeAccessor {
    @Mutable
    @Accessor("validBlocks")
    void neoforge$setValidBlocks(Set<Block> validBlocks);
}
