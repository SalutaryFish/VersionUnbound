package net.minecraft.client.multiplayer.chat.report;

import com.mojang.authlib.minecraft.report.AbuseReport;
import com.mojang.authlib.minecraft.report.AbuseReportLimits;
import com.mojang.authlib.minecraft.report.ReportedEntity;
import com.mojang.datafixers.util.Either;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.reporting.SkinReportScreen;
import net.minecraft.client.resources.PlayerSkin;
import org.apache.commons.lang3.StringUtils;

public class SkinReport extends Report {
    final Supplier<PlayerSkin> skinGetter;

    SkinReport(UUID reportId, Instant created, UUID reportedProfileId, Supplier<PlayerSkin> skinGetter) {
        super(reportId, created, reportedProfileId);
        this.skinGetter = skinGetter;
    }

    public Supplier<PlayerSkin> getSkinGetter() {
        return this.skinGetter;
    }

    public SkinReport copy() {
        SkinReport skinReport = new SkinReport(this.reportId, this.createdAt, this.reportedProfileId, this.skinGetter);
        skinReport.comments = this.comments;
        skinReport.reason = this.reason;
        skinReport.attested = this.attested;
        return skinReport;
    }

    @Override
    public Screen createScreen(Screen lastScreen, ReportingContext reportingContext) {
        return new SkinReportScreen(lastScreen, reportingContext, this);
    }

    public static class Builder extends Report.Builder<SkinReport> {
        public Builder(SkinReport report, AbuseReportLimits limits) {
            super(report, limits);
        }

        public Builder(UUID reportedPlayerId, Supplier<PlayerSkin> skinGetter, AbuseReportLimits limits) {
            super(new SkinReport(UUID.randomUUID(), Instant.now(), reportedPlayerId, skinGetter), limits);
        }

        @Override
        public boolean hasContent() {
            return StringUtils.isNotEmpty(this.comments()) || this.reason() != null;
        }

        @Nullable
        @Override
        public Report.CannotBuildReason checkBuildable() {
            if (this.report.reason == null) {
                return Report.CannotBuildReason.NO_REASON;
            } else {
                return this.report.comments.length() > this.limits.maxOpinionCommentsLength()
                    ? Report.CannotBuildReason.COMMENT_TOO_LONG
                    : super.checkBuildable();
            }
        }

        @Override
        public Either<Report.Result, Report.CannotBuildReason> build(ReportingContext reportingContext) {
            Report.CannotBuildReason cannotBuildReason = this.checkBuildable();
            if (cannotBuildReason != null) {
                return Either.right(cannotBuildReason);
            }

            String string = Objects.requireNonNull(this.report.reason).backendName();
            ReportedEntity reportedEntity = new ReportedEntity(this.report.reportedProfileId);
            PlayerSkin playerSkin = this.report.skinGetter.get();
            String string2 = playerSkin.textureUrl();
            AbuseReport abuseReport = AbuseReport.skin(this.report.comments, string, string2, reportedEntity, this.report.createdAt);
            return Either.left(new Report.Result(this.report.reportId, ReportType.SKIN, abuseReport));
        }
    }
}
