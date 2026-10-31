package net.salutaryfish.versionunbound.testmod.hooks;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * The block entity extension's {@code onLoad} on the server: a block entity added to a loaded chunk hears it once
 * block entities next tick, not at once. (The client's {@code onLoad} and {@code onDataPacket}: ClientChecks, with the
 * recorder PlayerChecks places near the player.)
 */
final class BlockEntityChecks {
    private BlockEntityChecks() {}

    private static @Nullable BlockPos placed;
    private static boolean heardAtOnce;

    static void start(Checks checks, ServerLevel level, BlockPos origin) {
        BlockPos pos = origin.above(2);
        HookBlockEntities.HEARD.clear();
        level.setBlock(pos, HooksMod.RECORDER.get().defaultBlockState(), 2 | 16);
        heardAtOnce = !HookBlockEntities.HEARD.isEmpty();
        placed = pos;
    }

    /** On the first server tick after start: true once it has checked. */
    static boolean finish(Checks checks, ServerLevel level) {
        BlockPos pos = placed;
        if (pos == null) return false;
        placed = null;
        checks.check("a block entity added to a loaded chunk hears onLoad when block entities next tick, not at once",
                !heardAtOnce && HookBlockEntities.HEARD.equals(List.of("onLoad server")));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        return true;
    }
}
