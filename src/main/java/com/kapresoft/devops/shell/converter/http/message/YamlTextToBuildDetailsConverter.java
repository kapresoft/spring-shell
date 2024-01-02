package com.kapresoft.devops.shell.converter.http.message;

import com.kapresoft.devops.shell.config.KapresoftProjectProperties;
import com.kapresoft.devops.shell.pojo.BuildInfo;
import com.kapresoft.devops.shell.pojo.BuildInfoDetails;

import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

import static java.util.Optional.ofNullable;

@Component
public class YamlTextToBuildDetailsConverter implements Converter<String, BuildInfoDetails> {

    /**
     * /site/{buildVersion}/{project-name:Kapresoft-Articles}
     */
    public static final String KEY_PATH_FMT = "site/%s/%s";
    /**
     * s3://{bucketName}/{key-Path}
     */
    public static final String S3_URI_FMT = "s3://%s/%s";

    private final ApplicationContext ctx;
    private final YamlTextToBuildInfoConverter yamlTextToBuildInfoConverter;

    public YamlTextToBuildDetailsConverter(@NonNull ApplicationContext ctx, YamlTextToBuildInfoConverter yamlTextToBuildInfoConverter) {
        this.ctx = ctx;
        this.yamlTextToBuildInfoConverter = yamlTextToBuildInfoConverter;
    }

    @Override
    public BuildInfoDetails convert(@NonNull String yamlText) {
        final KapresoftProjectProperties projectConfig = ctx.getBean(KapresoftProjectProperties.class);

        Optional<BuildInfo> buildInfo = ofNullable(yamlTextToBuildInfoConverter.convert(yamlText));
        if (buildInfo.isEmpty()) {
            return null;
        }

        BuildInfo b = buildInfo.get();
        URI buildInfoFileURI = toBuildInfoFileURI(projectConfig);

        String version = getBuildVersion(b.getId());
        String keyPath = KEY_PATH_FMT.formatted(version, projectConfig.getName());
        String cdnPath = "/" + keyPath;
        URI s3URI = URI.create(S3_URI_FMT.formatted(projectConfig.getName(), keyPath));

        return BuildInfoDetails.builder()
                .buildInfo(b)
                .version(version)
                .keyPath(keyPath)
                .cdnPath(cdnPath)
                .s3URI(s3URI)
                .buildInfoFileURI(buildInfoFileURI)
                .build();
    }

    /**
     * @param buildID In the format {projectName}:{buildVersion}, i.e. <pre>Kapresoft-Articles:2c2eba60-7f8f-40df-b99c-95db50e7b3a7</pre>
     * @return Build Version. Example: 2c2eba60-7f8f-40df-b99c-95db50e7b3a7
     */
    private String getBuildVersion(@NonNull String buildID) {
        String[] parts = buildID.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid buildID: %s".formatted(buildID));
        }
        return parts[1];
    }

    private URI toBuildInfoFileURI(KapresoftProjectProperties projectConfig) {
        return UriComponentsBuilder.fromUri(projectConfig.getCdnURI())
                .path(projectConfig.getBuildInfoFile()).build()
                .toUri();
    }

}
