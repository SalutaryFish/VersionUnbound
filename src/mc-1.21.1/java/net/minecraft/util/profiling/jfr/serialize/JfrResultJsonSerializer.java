package net.minecraft.util.profiling.jfr.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.LongSerializationPolicy;
import com.mojang.datafixers.util.Pair;
import java.time.Duration;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.stream.DoubleStream;
import net.minecraft.Util;
import net.minecraft.util.profiling.jfr.Percentiles;
import net.minecraft.util.profiling.jfr.parse.JfrStatsResult;
import net.minecraft.util.profiling.jfr.stats.ChunkGenStat;
import net.minecraft.util.profiling.jfr.stats.ChunkIdentification;
import net.minecraft.util.profiling.jfr.stats.CpuLoadStat;
import net.minecraft.util.profiling.jfr.stats.FileIOStat;
import net.minecraft.util.profiling.jfr.stats.GcHeapStat;
import net.minecraft.util.profiling.jfr.stats.IoSummary;
import net.minecraft.util.profiling.jfr.stats.PacketIdentification;
import net.minecraft.util.profiling.jfr.stats.ThreadAllocationStat;
import net.minecraft.util.profiling.jfr.stats.TickTimeStat;
import net.minecraft.util.profiling.jfr.stats.TimedStatSummary;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class JfrResultJsonSerializer {
    private static final String BYTES_PER_SECOND = "bytesPerSecond";
    private static final String COUNT = "count";
    private static final String DURATION_NANOS_TOTAL = "durationNanosTotal";
    private static final String TOTAL_BYTES = "totalBytes";
    private static final String COUNT_PER_SECOND = "countPerSecond";
    final Gson gson = new GsonBuilder().setPrettyPrinting().setLongSerializationPolicy(LongSerializationPolicy.DEFAULT).create();

    private static void serializePacketId(PacketIdentification packetIdentification, JsonObject json) {
        json.addProperty("protocolId", packetIdentification.protocolId());
        json.addProperty("packetId", packetIdentification.packetId());
    }

    private static void serializeChunkId(ChunkIdentification chunkIndentification, JsonObject json) {
        json.addProperty("level", chunkIndentification.level());
        json.addProperty("dimension", chunkIndentification.dimension());
        json.addProperty("x", chunkIndentification.x());
        json.addProperty("z", chunkIndentification.z());
    }

    public String format(JfrStatsResult result) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("startedEpoch", result.recordingStarted().toEpochMilli());
        jsonObject.addProperty("endedEpoch", result.recordingEnded().toEpochMilli());
        jsonObject.addProperty("durationMs", result.recordingDuration().toMillis());
        Duration duration = result.worldCreationDuration();
        if (duration != null) {
            jsonObject.addProperty("worldGenDurationMs", duration.toMillis());
        }

        jsonObject.add("heap", this.heap(result.heapSummary()));
        jsonObject.add("cpuPercent", this.cpu(result.cpuLoadStats()));
        jsonObject.add("network", this.network(result));
        jsonObject.add("fileIO", this.fileIO(result));
        jsonObject.add("serverTick", this.serverTicks(result.tickTimes()));
        jsonObject.add("threadAllocation", this.threadAllocations(result.threadAllocationSummary()));
        jsonObject.add("chunkGen", this.chunkGen(result.chunkGenSummary()));
        return this.gson.toJson(jsonObject);
    }

    private JsonElement heap(GcHeapStat.Summary summary) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("allocationRateBytesPerSecond", summary.allocationRateBytesPerSecond());
        jsonObject.addProperty("gcCount", summary.totalGCs());
        jsonObject.addProperty("gcOverHeadPercent", summary.gcOverHead());
        jsonObject.addProperty("gcTotalDurationMs", summary.gcTotalDuration().toMillis());
        return jsonObject;
    }

    private JsonElement chunkGen(List<Pair<ChunkStatus, TimedStatSummary<ChunkGenStat>>> summary) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty(
            "durationNanosTotal", summary.stream().mapToDouble(pairx -> ((TimedStatSummary)pairx.getSecond()).totalDuration().toNanos()).sum()
        );
        JsonArray jsonArray = Util.make(new JsonArray(), jsonArrayx -> jsonObject.add("status", jsonArrayx));

        for (Pair<ChunkStatus, TimedStatSummary<ChunkGenStat>> pair : summary) {
            TimedStatSummary<ChunkGenStat> timedStatSummary = pair.getSecond();
            JsonObject jsonObject2 = Util.make(new JsonObject(), jsonArray::add);
            jsonObject2.addProperty("state", pair.getFirst().toString());
            jsonObject2.addProperty("count", timedStatSummary.count());
            jsonObject2.addProperty("durationNanosTotal", timedStatSummary.totalDuration().toNanos());
            jsonObject2.addProperty("durationNanosAvg", timedStatSummary.totalDuration().toNanos() / timedStatSummary.count());
            JsonObject jsonObject3 = Util.make(new JsonObject(), jsonObject2x -> jsonObject2.add("durationNanosPercentiles", jsonObject2x));
            timedStatSummary.percentilesNanos().forEach((integer, double_) -> jsonObject3.addProperty("p" + integer, double_));
            Function<ChunkGenStat, JsonElement> function = chunkGenStat -> {
                JsonObject jsonObjectx = new JsonObject();
                jsonObjectx.addProperty("durationNanos", chunkGenStat.duration().toNanos());
                jsonObjectx.addProperty("level", chunkGenStat.level());
                jsonObjectx.addProperty("chunkPosX", chunkGenStat.chunkPos().x);
                jsonObjectx.addProperty("chunkPosZ", chunkGenStat.chunkPos().z);
                jsonObjectx.addProperty("worldPosX", chunkGenStat.worldPos().x());
                jsonObjectx.addProperty("worldPosZ", chunkGenStat.worldPos().z());
                return jsonObjectx;
            };
            jsonObject2.add("fastest", function.apply(timedStatSummary.fastest()));
            jsonObject2.add("slowest", function.apply(timedStatSummary.slowest()));
            jsonObject2.add("secondSlowest", timedStatSummary.secondSlowest() != null ? function.apply(timedStatSummary.secondSlowest()) : JsonNull.INSTANCE);
        }

        return jsonObject;
    }

    private JsonElement threadAllocations(ThreadAllocationStat.Summary summary) {
        JsonArray jsonArray = new JsonArray();
        summary.allocationsPerSecondByThread().forEach((string, double_) -> jsonArray.add(Util.make(new JsonObject(), jsonObject -> {
            jsonObject.addProperty("thread", string);
            jsonObject.addProperty("bytesPerSecond", double_);
        })));
        return jsonArray;
    }

    private JsonElement serverTicks(List<TickTimeStat> stats) {
        if (stats.isEmpty()) {
            return JsonNull.INSTANCE;
        }

        JsonObject jsonObject = new JsonObject();
        double[] ds = stats.stream().mapToDouble(tickTimeStat -> tickTimeStat.currentAverage().toNanos() / 1000000.0).toArray();
        DoubleSummaryStatistics doubleSummaryStatistics = DoubleStream.of(ds).summaryStatistics();
        jsonObject.addProperty("minMs", doubleSummaryStatistics.getMin());
        jsonObject.addProperty("averageMs", doubleSummaryStatistics.getAverage());
        jsonObject.addProperty("maxMs", doubleSummaryStatistics.getMax());
        Map<Integer, Double> map = Percentiles.evaluate(ds);
        map.forEach((integer, double_) -> jsonObject.addProperty("p" + integer, double_));
        return jsonObject;
    }

    private JsonElement fileIO(JfrStatsResult result) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("write", this.fileIoSummary(result.fileWrites()));
        jsonObject.add("read", this.fileIoSummary(result.fileReads()));
        jsonObject.add("chunksRead", this.ioSummary(result.readChunks(), JfrResultJsonSerializer::serializeChunkId));
        jsonObject.add("chunksWritten", this.ioSummary(result.writtenChunks(), JfrResultJsonSerializer::serializeChunkId));
        return jsonObject;
    }

    private JsonElement fileIoSummary(FileIOStat.Summary summary) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("totalBytes", summary.totalBytes());
        jsonObject.addProperty("count", summary.counts());
        jsonObject.addProperty("bytesPerSecond", summary.bytesPerSecond());
        jsonObject.addProperty("countPerSecond", summary.countsPerSecond());
        JsonArray jsonArray = new JsonArray();
        jsonObject.add("topContributors", jsonArray);
        summary.topTenContributorsByTotalBytes().forEach(pair -> {
            JsonObject jsonObjectx = new JsonObject();
            jsonArray.add(jsonObjectx);
            jsonObjectx.addProperty("path", pair.getFirst());
            jsonObjectx.addProperty("totalBytes", pair.getSecond());
        });
        return jsonObject;
    }

    private JsonElement network(JfrStatsResult result) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.add("sent", this.ioSummary(result.sentPacketsSummary(), JfrResultJsonSerializer::serializePacketId));
        jsonObject.add("received", this.ioSummary(result.receivedPacketsSummary(), JfrResultJsonSerializer::serializePacketId));
        return jsonObject;
    }

    private <T> JsonElement ioSummary(IoSummary<T> ioSummary, BiConsumer<T, JsonObject> serializer) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("totalBytes", ioSummary.getTotalSize());
        jsonObject.addProperty("count", ioSummary.getTotalCount());
        jsonObject.addProperty("bytesPerSecond", ioSummary.getSizePerSecond());
        jsonObject.addProperty("countPerSecond", ioSummary.getCountsPerSecond());
        JsonArray jsonArray = new JsonArray();
        jsonObject.add("topContributors", jsonArray);
        ioSummary.largestSizeContributors().forEach(pair -> {
            JsonObject jsonObjectx = new JsonObject();
            jsonArray.add(jsonObjectx);
            T object = pair.getFirst();
            IoSummary.CountAndSize countAndSize = pair.getSecond();
            serializer.accept(object, jsonObjectx);
            jsonObjectx.addProperty("totalBytes", countAndSize.totalSize());
            jsonObjectx.addProperty("count", countAndSize.totalCount());
            jsonObjectx.addProperty("averageSize", countAndSize.averageSize());
        });
        return jsonObject;
    }

    private JsonElement cpu(List<CpuLoadStat> stats) {
        JsonObject jsonObject = new JsonObject();
        BiFunction<List<CpuLoadStat>, ToDoubleFunction<CpuLoadStat>, JsonObject> biFunction = (list, toDoubleFunction) -> {
            JsonObject jsonObjectx = new JsonObject();
            DoubleSummaryStatistics doubleSummaryStatistics = list.stream().mapToDouble(toDoubleFunction).summaryStatistics();
            jsonObjectx.addProperty("min", doubleSummaryStatistics.getMin());
            jsonObjectx.addProperty("average", doubleSummaryStatistics.getAverage());
            jsonObjectx.addProperty("max", doubleSummaryStatistics.getMax());
            return jsonObjectx;
        };
        jsonObject.add("jvm", biFunction.apply(stats, CpuLoadStat::jvm));
        jsonObject.add("userJvm", biFunction.apply(stats, CpuLoadStat::userJvm));
        jsonObject.add("system", biFunction.apply(stats, CpuLoadStat::system));
        return jsonObject;
    }
}
