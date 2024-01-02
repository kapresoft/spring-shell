package com.kapresoft.devops.shell.decorator;

import lombok.Builder;
import lombok.Value;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.kapresoft.devops.shell.pojo.BuildInfo;
import com.kapresoft.devops.shell.pojo.BuildInfoDetails;

import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.shell.style.FigureSettings;

import static java.util.Optional.ofNullable;

@Value
public class BuildInfoCLIOutputDecorator {

    private static final String LIVE_TEXT_FMT = " %s [LIVE]";
    S3ObjectSummary summary;
    BuildInfoDetails buildInfo;
    BuildInfoDetails buildInfoLive;
    String bulletChar;
    String rightPointing;
     String checkMark;

    @Builder
    public BuildInfoCLIOutputDecorator(@NonNull S3ObjectSummary summary,
                                       @NonNull BuildInfoDetails buildInfo,
                                       @Nullable BuildInfoDetails buildInfoLive) {
        this.summary = summary;
        this.buildInfo = buildInfo;
        this.buildInfoLive = buildInfoLive;
        FigureSettings fs = FigureSettings.defaults();
        this.bulletChar = fs.righwardsArror();
        this.rightPointing = fs.rightPointingQuotation();
        this.checkMark = fs.tick();
    }

    private String p(String label, Object text) {
        String formattedLabel = AnsiOutput.toString(AnsiColor.YELLOW, label);
        return "   %s %s: %s".formatted(bulletChar, formattedLabel, text);
    }
    private String live() {
        String liveText = ofNullable(buildInfoLive).filter(live -> live.getId().equalsIgnoreCase(buildInfo.getId()))
                .map(b -> AnsiOutput.toString(AnsiColor.BRIGHT_GREEN, LIVE_TEXT_FMT.formatted(checkMark)))
                .orElse("");
        return AnsiOutput.toString(AnsiColor.BRIGHT_GREEN, liveText);
    }

    private String header() {
        String bullet = rightPointing + rightPointing;
        String formattedLabel = AnsiOutput.toString(AnsiColor.BRIGHT_BLUE, bullet + " Build-Version");
        return "%s: %s%s".formatted(formattedLabel, buildInfo.getVersion(), live());
    }

    @Override
    public String toString() {
        String liveText = live();
        return """
                %s
                %s
                %s
                %s
                %s
                """.formatted(
                header(),
                p("ID", buildInfo.getId()),
                p("Date", buildInfo.getDate()), p("Git-Commit", buildInfo.getCommitHash()),
                p("S3-URI", buildInfo.getS3URI())
        );
    }
}
