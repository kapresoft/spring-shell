package com.kapresoft.devops.shell.pojo;

import lombok.Builder;
import lombok.Value;

import com.kapresoft.devops.shell.config.KapresoftProjectProperties;

import org.springframework.lang.NonNull;

import java.net.URI;
import java.util.Date;

@Value
@SuppressWarnings("unused")
public class BuildInfoDetails {

    KapresoftProjectProperties projectConfig;
    BuildInfo buildInfo;
    String version;
    String keyPath;
    String cdnPath;
    URI s3URI;
    URI buildInfoFileURI;

    @Builder
    public BuildInfoDetails(@NonNull KapresoftProjectProperties projectConfig,
                            @NonNull BuildInfo buildInfo,
                            @NonNull String version,
                            @NonNull String keyPath,
                            @NonNull String cdnPath,
                            @NonNull URI s3URI,
                            @NonNull URI buildInfoFileURI) {
        this.buildInfo = buildInfo;
        this.projectConfig = projectConfig;
        this.version = version;
        this.keyPath = keyPath;
        this.s3URI = s3URI;
        this.cdnPath = cdnPath;
        this.buildInfoFileURI = buildInfoFileURI;
    }

    public Date getLastModified() {
        return getBuildInfo().getLastModified();
    }

    @NonNull
    public String getProjectName() {
        return getProjectConfig().getName();
    }

    @NonNull
    public URI getCdnURI() {
        return projectConfig.getCdnURI();
    }

    @NonNull
    public S3Bucket getS3Bucket() {
        return projectConfig.getS3Bucket();
    }

    public String getId() {
        return getBuildInfo().getId();
    }

    public String getDate() {
        return getBuildInfo().getDate();
    }

    public String getCommitHash() {
        return getBuildInfo().getCommitHash();
    }

    public Date getBuildDate() {
        return getBuildInfo().getBuildDate();
    }

    public String getBuildNumber() {
        return getBuildInfo().getBuildNumber();
    }

    public String getDeployKey() {
        return getBuildInfo().getDeployKey();
    }
}
