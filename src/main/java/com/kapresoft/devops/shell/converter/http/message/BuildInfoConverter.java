package com.kapresoft.devops.shell.converter.http.message;

import com.kapresoft.devops.shell.config.KapresoftProjectProperties;
import com.kapresoft.devops.shell.decorator.BuildInfoCLIOutputDecorator;
import com.kapresoft.devops.shell.pojo.BuildInfo;
import com.kapresoft.devops.shell.pojo.BuildInfoDetails;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.yaml.snakeyaml.Yaml;

import java.net.URI;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
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
                .map(s -> fromText(s).toBuilder().lastModified(lastModified).build());
    }

    default Optional<BuildInfoDetails> toBuildInfoDetails(@NonNull String yamlText, Date lastModified, KapresoftProjectProperties projectConfig) {

        Optional<BuildInfo> buildInfo = toBuildInfo(yamlText, lastModified);
        if (buildInfo.isEmpty()) {
            return empty();
        }

        BuildInfo b = buildInfo.get();
        URI buildInfoFileURI = toBuildInfoFileURI(projectConfig);

        String version = getBuildVersion(b.getId());
        String keyPath = KEY_PATH_FMT.formatted(version, projectConfig.getName());
        String cdnPath = "/" + keyPath;
        URI s3URI = URI.create(S3_URI_FMT.formatted(projectConfig.getName(), keyPath));

        return of(BuildInfoDetails.builder()
                .buildInfo(b)
                .version(version)
                .keyPath(keyPath)
                .cdnPath(cdnPath)
                .s3URI(s3URI)
                .buildInfoFileURI(buildInfoFileURI)
                .build());
    }

    default BuildInfo fromText(String yamlText) {
        final Yaml yaml = new Yaml();
        return fromMap(yaml.load(yamlText));
    }

    default BuildInfo fromMap(Map<String, Object> map) {
        return BuildInfo.builder()
                .id((String) map.get("id"))
                .date((String) map.get("date"))
                .commitHash((String) map.get("commit-hash"))
                .build();
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
