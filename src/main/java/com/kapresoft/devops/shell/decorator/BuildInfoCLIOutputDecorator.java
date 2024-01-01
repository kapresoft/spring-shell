package com.kapresoft.devops.shell.decorator;

import lombok.Builder;
import lombok.Value;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.kapresoft.devops.shell.pojo.BuildInfo;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.nio.file.Path;

import static java.util.Optional.ofNullable;

@Value
public class BuildInfoCLIOutputDecorator {

    S3ObjectSummary summary;
    BuildInfo buildInfo;
    BuildInfo buildInfoLive;

    @Builder
    public BuildInfoCLIOutputDecorator(@NonNull S3ObjectSummary summary,
                                       @NonNull BuildInfo buildInfo,
                                       @Nullable BuildInfo buildInfoLive) {
        this.summary = summary;
        this.buildInfo = buildInfo;
        this.buildInfoLive = buildInfoLive;
    }

    @Override
    public String toString() {
        Path keyPath = Path.of(summary.getKey());
        String buildPath = keyPath.getParent().toString();
        String buildVersion = buildInfo.getId().replace("%s:".formatted("Kapresoft-Articles"), "");
        String s3URI = "s3://%s/%s".formatted(summary.getBucketName(), buildPath);
        String liveText = ofNullable(buildInfoLive).filter(live -> live.getId().equalsIgnoreCase(buildInfo.getId()))
                .map(b -> " [LIVE]").orElse("");
        return """
                ## /%s%s
                • ID: %s
                • Build-Version: %s
                • Date: %s
                • Git-Commit: %s
                • S3-URI: %s
                """.formatted(
                buildPath, liveText, buildInfo.getId(), buildVersion,
                buildInfo.getDate(), buildInfo.getCommitHash(), s3URI);
    }
}
