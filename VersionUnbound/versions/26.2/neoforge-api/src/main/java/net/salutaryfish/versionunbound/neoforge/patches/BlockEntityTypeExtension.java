package net.salutaryfish.versionunbound.neoforge.patches;

import java.util.Collections;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * NeoForge's patch to {@link BlockEntityType}, injected into it by the class tweaker (which opens {@code validBlocks}).
 * NeoForge's three added constructors ({@code (supplier, Set, boolean)}, {@code (supplier, Block...)} and
 * {@code (supplier, boolean, Block...)}) are added by {@code AddedStaticMethods}: each calls vanilla's
 * {@code (supplier, Set)} constructor (NeoForge's all end up in its three-argument one, which vanilla lacks), with the
 * set from {@link #validBlocks} for the varargs ones, then the ones taking {@code onlyOpCanSetNbt} run {@link #init};
 * {@code BlockEntityTypeFieldsMixin} keeps the flag and answers {@code onlyOpCanSetNbt()} with it.
 */
public interface BlockEntityTypeExtension {
    /**
     * Neo: Add getter for an immutable view of the set of valid blocks.
     */
    default Set<Block> getValidBlocks() {
        return Collections.unmodifiableSet(((BlockEntityType<?>) this).validBlocks);
    }

    /**
     * The valid blocks NeoForge's varargs constructors pass on: {@code Set.of(validBlocks)}, refusing an empty array.
     * VersionUnbound: NeoForge checks the length after its constructor call, so the type it refuses has already made
     * its intrusive registry holder; here the check comes first. Either way the constructor throws.
     */
    static Set<Block> validBlocks(Block[] validBlocks) {
        if (validBlocks.length == 0) {
            throw new IllegalArgumentException("Block entity type instantiated without valid blocks. If this is intentional, pass Set.of() instead of an empty varag.");
        }
        return Set.of(validBlocks);
    }

    /** The rest of NeoForge's {@code BlockEntityType(supplier, Set, boolean)}, after vanilla's constructor ran. */
    static void init(BlockEntityType<?> type, BlockEntityType.BlockEntitySupplier<?> factory, Set<Block> validBlocks, boolean onlyOpCanSetNbt) {
        ((Fields) type).versionunbound$setOnlyOpCanSetNbt(onlyOpCanSetNbt);
    }

    /** The rest of NeoForge's {@code BlockEntityType(supplier, boolean, Block...)}, after vanilla's constructor ran. */
    static void init(BlockEntityType<?> type, BlockEntityType.BlockEntitySupplier<?> factory, boolean onlyOpCanSetNbt, Block[] validBlocks) {
        ((Fields) type).versionunbound$setOnlyOpCanSetNbt(onlyOpCanSetNbt);
    }

    /** NeoForge's {@code onlyOpCanSetNbt} field, implemented by {@code BlockEntityTypeFieldsMixin}. */
    interface Fields {
        void versionunbound$setOnlyOpCanSetNbt(boolean onlyOpCanSetNbt);
    }
}
