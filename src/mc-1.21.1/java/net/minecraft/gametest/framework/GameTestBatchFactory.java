package net.minecraft.gametest.framework;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;

public class GameTestBatchFactory {
    private static final int MAX_TESTS_PER_BATCH = 50;

    public static Collection<GameTestBatch> fromTestFunction(Collection<TestFunction> testFunctions, ServerLevel level) {
        Map<String, List<TestFunction>> map = testFunctions.stream().collect(Collectors.groupingBy(TestFunction::batchName));
        return map.entrySet()
            .stream()
            .flatMap(
                entry -> {
                    String string = entry.getKey();
                    List<TestFunction> list = entry.getValue();
                    return Streams.mapWithIndex(
                        Lists.partition(list, 50).stream(),
                        (listx, l) -> toGameTestBatch(listx.stream().map(testFunction -> toGameTestInfo(testFunction, 0, level)).toList(), string, l)
                    );
                }
            )
            .toList();
    }

    public static GameTestInfo toGameTestInfo(TestFunction testFunction, int rotationSteps, ServerLevel level) {
        return new GameTestInfo(testFunction, StructureUtils.getRotationForRotationSteps(rotationSteps), level, RetryOptions.noRetries());
    }

    public static GameTestRunner.GameTestBatcher fromGameTestInfo() {
        return fromGameTestInfo(50);
    }

    public static GameTestRunner.GameTestBatcher fromGameTestInfo(int maxTests) {
        return collection -> {
            Map<String, List<GameTestInfo>> map = collection.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(gameTestInfo -> gameTestInfo.getTestFunction().batchName()));
            return map.entrySet().stream().flatMap(entry -> {
                String string = entry.getKey();
                List<GameTestInfo> list = entry.getValue();
                return Streams.mapWithIndex(Lists.partition(list, maxTests).stream(), (listx, l) -> toGameTestBatch(List.copyOf(listx), string, l));
            }).toList();
        };
    }

    public static GameTestBatch toGameTestBatch(Collection<GameTestInfo> gameTestInfos, String functionName, long index) {
        Consumer<ServerLevel> consumer = GameTestRegistry.getBeforeBatchFunction(functionName);
        Consumer<ServerLevel> consumer2 = GameTestRegistry.getAfterBatchFunction(functionName);
        return new GameTestBatch(functionName + ":" + index, gameTestInfos, consumer, consumer2);
    }
}
