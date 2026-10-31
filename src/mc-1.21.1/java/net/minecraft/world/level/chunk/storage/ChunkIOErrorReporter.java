package net.minecraft.world.level.chunk.storage;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.world.level.ChunkPos;

public interface ChunkIOErrorReporter {
    void reportChunkLoadFailure(Throwable var1, RegionStorageInfo var2, ChunkPos var3);

    void reportChunkSaveFailure(Throwable var1, RegionStorageInfo var2, ChunkPos var3);

    static ReportedException createMisplacedChunkReport(ChunkPos pos, ChunkPos expectedPos) {
        CrashReport crashReport = CrashReport.forThrowable(
            new IllegalStateException("Retrieved chunk position " + pos + " does not match requested " + expectedPos), "Chunk found in invalid location"
        );
        CrashReportCategory crashReportCategory = crashReport.addCategory("Misplaced Chunk");
        crashReportCategory.setDetail("Stored Position", pos::toString);
        return new ReportedException(crashReport);
    }

    default void reportMisplacedChunk(ChunkPos pos, ChunkPos expectedPos, RegionStorageInfo regionStorageInfo) {
        this.reportChunkLoadFailure(createMisplacedChunkReport(pos, expectedPos), regionStorageInfo, expectedPos);
    }
}
