package com.kapresoft.devops.shell.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;

import com.kapresoft.devops.shell.pojo.S3Bucket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;

import java.net.URI;

@ConfigurationProperties(prefix = "spring.application.code-build-project.kapresoft-articles")
public class KapresoftProjectProperties {

    @NonNull
    @Getter
    private final String name;
    @NonNull
    @Getter
    private final String buildInfoFile;
    @NonNull
    @Getter
    private final URI cdnURI;
    @NonNull
    @Getter
    private S3Bucket s3Bucket;

    @Autowired
    private ApplicationContext ctx;

    @PostConstruct
    void after() {
        // late-binding to avoid circular reference
        s3Bucket = ctx.getBean(S3BucketProperties.class).getS3Bucket();
    }

    public KapresoftProjectProperties(@NonNull String name,
                                      @NonNull String buildInfoFile,
                                      @NonNull URI cdn) {
        this.name = name;
        this.buildInfoFile = buildInfoFile;
        this.cdnURI = cdn;
    }
}
