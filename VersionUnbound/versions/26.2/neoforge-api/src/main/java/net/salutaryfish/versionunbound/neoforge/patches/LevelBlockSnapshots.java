package net.salutaryfish.versionunbound.neoforge.patches;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge's block snapshot capture on {@link Level}: its public fields {@code captureBlockSnapshots},
 * {@code capturedBlockSnapshots} and {@code restoringBlockSnapshots}, and its {@code markAndNotifyBlock}, which
 * {@code LevelMixin} adds under those names (Sable sets the first, Create, Dynamic Trees and KaleidoscopeCookery read
 * the last, Create and Sable call {@code markAndNotifyBlock}). While {@code captureBlockSnapshots} is set, the server's
 * {@code setBlock} records a snapshot of each block it changes and holds back what follows the change (the block's
 * {@code onPlace}, the client update, neighbor and shape updates), which {@code markAndNotifyBlock} sends later; while
 * {@code restoringBlockSnapshots} is set, blocks drop no items or experience. Our code reaches the fields through this
 * interface, which the mixin implements.
 */
public interface LevelBlockSnapshots {
    boolean versionunbound$captureBlockSnapshots();

    void versionunbound$setCaptureBlockSnapshots(boolean capture);

    ArrayList<BlockSnapshot> versionunbound$capturedBlockSnapshots();

    boolean versionunbound$restoringBlockSnapshots();

    void versionunbound$setRestoringBlockSnapshots(boolean restoring);

    /** NeoForge's method, split off from {@code setBlock}: what vanilla does after the chunk took the new state. */
    void markAndNotifyBlock(BlockPos pos, @Nullable LevelChunk chunk, BlockState oldState, BlockState blockState, int updateFlags, int updateLimit);
}
