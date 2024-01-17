package com.kapresoft.devops.shell.decorator;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.kapresoft.devops.shell.pojo.BuildInfoDetails;

import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.shell.style.FigureSettings;

import java.util.Date;

import static java.util.Optional.ofNullable;

@Getter
public class BuildInfoCLIOutputDecorator {

    private static final String LIVE_TEXT_FMT = " %s [LIVE]";

    private final S3ObjectSummary summary;
    private final BuildInfoDetails buildInfo;
    private final String bulletChar;
    private final String rightPointing;
    private final String checkMark;

    @Setter
    private boolean live;

    @Builder
    public BuildInfoCLIOutputDecorator(@NonNull S3ObjectSummary summary,
                                       @NonNull BuildInfoDetails buildInfo,
                                       @Nullable boolean live) {
        this.summary = summary;
        this.buildInfo = buildInfo;
        this.live = live;

        final FigureSettings fs = FigureSettings.defaults();
        this.bulletChar = fs.righwardsArror();
        this.rightPointing = fs.rightPointingQuotation();
        this.checkMark = fs.tick();
    }

    private String p(String label, Object text, boolean... noBulletChar) {
        String formattedLabel = AnsiOutput.toString(AnsiColor.YELLOW, label);
        boolean withoutBullet = ofNullable(noBulletChar).filter(vararg -> vararg.length > 0)
                .map(v -> noBulletChar[0]).orElse(false);
        if (!withoutBullet) {
            formattedLabel = "%s %s".formatted(bulletChar, formattedLabel);
        }
        return "  %-23s: %s".formatted(formattedLabel, text);
    }

    private String live() {
        String liveText = isLive() ? AnsiOutput.toString(AnsiColor.BRIGHT_GREEN, LIVE_TEXT_FMT.formatted(checkMark)) : "";
        return AnsiOutput.toString(AnsiColor.BRIGHT_GREEN, liveText);
    }

    private String header() {
        String bullet = rightPointing + rightPointing;
        String formattedLabel = AnsiOutput.toString(AnsiColor.BRIGHT_BLUE, bullet + " Build-Version");
        String manualBuildText = "";
        if (buildInfo.getBuildInfo().isManualBuild()) {
            manualBuildText = " (Local Build)";
        }
        return "%s: %s%s%s".formatted(formattedLabel, buildInfo.getVersion(), manualBuildText, live());
    }

    @Override
    public String toString() {
        var buildDate = ofNullable(buildInfo.getBuildDate())
                .map(d -> buildInfo.getBuildDate().toString())
                .orElse("Undetermined");
        return """
                %s
                %s %s
                %s
                """.formatted(
                header(),
                p("Git-Hash", buildInfo.getCommitHash()), p("Build-Date", buildDate, true),
                p("S3-URI", buildInfo.getS3URI())
        );
    }

    public Date getLastModified() {
        return getBuildInfo().getLastModified();
    }
}
