package com.kapresoft.devops.shell.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;

import com.kapresoft.devops.shell.pojo.S3Bucket;
import com.kapresoft.devops.shell.validator.CustomValidator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;

import java.util.Locale;
import java.util.Optional;

import static java.util.Optional.*;

@ConfigurationProperties(prefix = "spring.application")
public class S3BucketProperties {

    @Getter
    final S3Bucket s3Bucket;

    @Autowired
    private CustomValidator validator;

    @PostConstruct
    void postConstruct() {
        validator.validateOrThrow(this.s3Bucket, "s3Bucket");
    }

    public S3BucketProperties(@NonNull String s3Bucket) {
        this.s3Bucket = createBucket(s3Bucket).orElse(null);
    }

    private Optional<S3Bucket> createBucket(String bucketUri) {
        String uri = ofNullable(bucketUri).orElse("");
        if (uri.isEmpty()) {
            return empty();
        }
        if (uri.endsWith("/")) {
            uri = uri.substring(0, uri.length()-1);
        }
        String bucketName = uri.toLowerCase(Locale.getDefault())
                .replaceFirst("s3://", "");
        var bucket = new S3Bucket(uri, bucketName);
        return of(bucket);
    }
}
