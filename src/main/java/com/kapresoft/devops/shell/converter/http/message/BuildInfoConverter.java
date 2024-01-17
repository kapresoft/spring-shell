package com.kapresoft.devops.shell.converter.http.message;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.kapresoft.devops.shell.config.KapresoftProjectProperties;
import com.kapresoft.devops.shell.decorator.BuildInfoCLIOutputDecorator;
import com.kapresoft.devops.shell.pojo.BuildInfo;
import com.kapresoft.devops.shell.pojo.BuildInfoDetails;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Comparator;
import java.util.Date;
import java.util.Optional;

import static java.util.Optional.*;

public interface BuildInfoConverter<S, T> extends Converter<S, T> {

    /**
     * /site/{buildVersion}/{project-name:Kapresoft-Articles}
     */
    String KEY_PATH_FMT = "site/%s/%s";
    /**
     * s3://{bucketName}/{key-Path}
     */
    String S3_URI_FMT = "s3://%s/%s";

    Comparator<BuildInfoCLIOutputDecorator> ASCENDING_ORDER_COMPARATOR = (o1, o2) -> ofNullable(o1.getLastModified())
            .map(m1 -> m1.compareTo(o2.getLastModified()))
            .orElse(0);

    default Optional<BuildInfo> toBuildInfo(@NonNull String yamlText, Date lastModified) {
        return of(yamlText)
                .filter(StringUtils::hasLength)
                .map(s -> {
                    BuildInfo.BuildInfoBuilder builder = YamlTextToBuildInfoConverter.fromText(s)
                            .toBuilder();
                    if (ofNullable(lastModified).isPresent()) {
                        builder.lastModified(lastModified);
                    }
                    if (builder.build().getBuildNumber().equalsIgnoreCase("manual")) {
                        builder.manualBuild(true);
                    }
                    return builder.build();
                });
    }

    default Optional<BuildInfoDetails> toBuildInfoDetails(@NonNull String yamlText, @Nullable S3ObjectSummary s3o, KapresoftProjectProperties projectConfig) {
        Date lastModified = ofNullable(s3o).map(S3ObjectSummary::getLastModified).orElse(null);
        Optional<BuildInfo> buildInfo = toBuildInfo(yamlText, lastModified);
        if (buildInfo.isEmpty()) {
            return empty();
        }

        BuildInfo b = buildInfo.get();
        URI buildInfoFileURI = toBuildInfoFileURI(projectConfig);

        String buildFile = "/" + projectConfig.getBuildInfoFile();
        String version = getBuildVersion(b.getId());
        String keyPath = ofNullable(s3o)
                .map( s -> s.getKey().replaceFirst(buildFile, ""))
                .orElse(KEY_PATH_FMT.formatted(version, projectConfig.getName()));
        String cdnPath = "/" + keyPath;
        URI s3URI = URI.create(S3_URI_FMT.formatted(projectConfig.getS3Bucket().name(), keyPath));
        if (b.isManualBuild()) {
            version = keyPath.replaceFirst("site/", "");
        }

        return of(BuildInfoDetails.builder()
                .buildInfo(b)
                .version(version)
                .keyPath(keyPath)
                .cdnPath(cdnPath)
                .s3URI(s3URI)
                .buildInfoFileURI(buildInfoFileURI)
                .build());
    }

    default URI toBuildInfoFileURI(KapresoftProjectProperties projectConfig) {
        return UriComponentsBuilder.fromUri(projectConfig.getCdnURI())
                .path(projectConfig.getBuildInfoFile()).build()
                .toUri();
    }

    default String getBuildVersion(@NonNull String buildID) {
        String[] parts = buildID.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid buildID: %s".formatted(buildID));
        }
        return parts[1];
    }

}
