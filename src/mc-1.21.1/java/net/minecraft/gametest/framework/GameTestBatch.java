package net.minecraft.gametest.framework;

import java.util.Collection;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;

public record GameTestBatch(
    String name, Collection<GameTestInfo> gameTestInfos, Consumer<ServerLevel> beforeBatchFunction, Consumer<ServerLevel> afterBatchFunction
) {
    public static final String DEFAULT_BATCH_NAME = "defaultBatch";

    public GameTestBatch {
        if (gameTestInfos.isEmpty()) {
            throw new IllegalArgumentException("A GameTestBatch must include at least one GameTestInfo!");
        }
    }
}
