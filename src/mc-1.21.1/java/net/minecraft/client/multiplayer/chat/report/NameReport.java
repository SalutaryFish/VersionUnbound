package net.minecraft.client.multiplayer.chat.report;

import com.mojang.authlib.minecraft.report.AbuseReport;
import com.mojang.authlib.minecraft.report.AbuseReportLimits;
import com.mojang.authlib.minecraft.report.ReportedEntity;
import com.mojang.datafixers.util.Either;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.reporting.NameReportScreen;
import org.apache.commons.lang3.StringUtils;

public class NameReport extends Report {
    private final String reportedName;

    NameReport(UUID reportId, Instant createdAt, UUID reportedProfileId, String reportedName) {
        super(reportId, createdAt, reportedProfileId);
        this.reportedName = reportedName;
    }

    public String getReportedName() {
        return this.reportedName;
    }

    public NameReport copy() {
        NameReport nameReport = new NameReport(this.reportId, this.createdAt, this.reportedProfileId, this.reportedName);
        nameReport.comments = this.comments;
        nameReport.attested = this.attested;
        return nameReport;
    }

    @Override
    public Screen createScreen(Screen lastScreen, ReportingContext reportingContext) {
        return new NameReportScreen(lastScreen, reportingContext, this);
    }

    public static class Builder extends Report.Builder<NameReport> {
        public Builder(NameReport report, AbuseReportLimits limits) {
            super(report, limits);
        }

        public Builder(UUID reportedProfileId, String reportedName, AbuseReportLimits limits) {
            super(new NameReport(UUID.randomUUID(), Instant.now(), reportedProfileId, reportedName), limits);
        }

        @Override
        public boolean hasContent() {
            return StringUtils.isNotEmpty(this.comments());
        }

        @Nullable
        @Override
        public Report.CannotBuildReason checkBuildable() {
            return this.report.comments.length() > this.limits.maxOpinionCommentsLength() ? Report.CannotBuildReason.COMMENT_TOO_LONG : super.checkBuildable();
        }

        @Override
        public Either<Report.Result, Report.CannotBuildReason> build(ReportingContext reportingContext) {
            Report.CannotBuildReason cannotBuildReason = this.checkBuildable();
            if (cannotBuildReason != null) {
                return Either.right(cannotBuildReason);
            }

            ReportedEntity reportedEntity = new ReportedEntity(this.report.reportedProfileId);
            AbuseReport abuseReport = AbuseReport.name(this.report.comments, reportedEntity, this.report.createdAt);
            return Either.left(new Report.Result(this.report.reportId, ReportType.USERNAME, abuseReport));
        }
    }
}
