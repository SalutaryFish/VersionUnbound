package net.minecraft.client;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.server.packs.PackResources;
import org.slf4j.Logger;

public class ResourceLoadStateTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private ResourceLoadStateTracker.ReloadState reloadState;
    private int reloadCount;

    public void startReload(ResourceLoadStateTracker.ReloadReason reloadReason, List<PackResources> packs) {
        this.reloadCount++;
        if (this.reloadState != null && !this.reloadState.finished) {
            LOGGER.warn("Reload already ongoing, replacing");
        }

        this.reloadState = new ResourceLoadStateTracker.ReloadState(
            reloadReason, packs.stream().map(PackResources::packId).collect(ImmutableList.toImmutableList())
        );
    }

    public void startRecovery(Throwable error) {
        if (this.reloadState == null) {
            LOGGER.warn("Trying to signal reload recovery, but nothing was started");
            this.reloadState = new ResourceLoadStateTracker.ReloadState(ResourceLoadStateTracker.ReloadReason.UNKNOWN, ImmutableList.of());
        }

        this.reloadState.recoveryReloadInfo = new ResourceLoadStateTracker.RecoveryInfo(error);
    }

    public void finishReload() {
        if (this.reloadState == null) {
            LOGGER.warn("Trying to finish reload, but nothing was started");
        } else {
            this.reloadState.finished = true;
        }
    }

    public void fillCrashReport(CrashReport report) {
        CrashReportCategory crashReportCategory = report.addCategory("Last reload");
        crashReportCategory.setDetail("Reload number", this.reloadCount);
        if (this.reloadState != null) {
            this.reloadState.fillCrashInfo(crashReportCategory);
        }
    }

    static class RecoveryInfo {
        private final Throwable error;

        RecoveryInfo(Throwable error) {
            this.error = error;
        }

        public void fillCrashInfo(CrashReportCategory crash) {
            crash.setDetail("Recovery", "Yes");
            crash.setDetail("Recovery reason", () -> {
                StringWriter stringWriter = new StringWriter();
                this.error.printStackTrace(new PrintWriter(stringWriter));
                return stringWriter.toString();
            });
        }
    }

    public enum ReloadReason {
        INITIAL("initial"),
        MANUAL("manual"),
        UNKNOWN("unknown");

        final String name;

        ReloadReason(final String name) {
            this.name = name;
        }
    }

    static class ReloadState {
        private final ResourceLoadStateTracker.ReloadReason reloadReason;
        private final List<String> packs;
        @Nullable
        ResourceLoadStateTracker.RecoveryInfo recoveryReloadInfo;
        boolean finished;

        ReloadState(ResourceLoadStateTracker.ReloadReason reloadReason, List<String> packs) {
            this.reloadReason = reloadReason;
            this.packs = packs;
        }

        public void fillCrashInfo(CrashReportCategory crash) {
            crash.setDetail("Reload reason", this.reloadReason.name);
            crash.setDetail("Finished", this.finished ? "Yes" : "No");
            crash.setDetail("Packs", () -> String.join(", ", this.packs));
            if (this.recoveryReloadInfo != null) {
                this.recoveryReloadInfo.fillCrashInfo(crash);
            }
        }
    }
}
