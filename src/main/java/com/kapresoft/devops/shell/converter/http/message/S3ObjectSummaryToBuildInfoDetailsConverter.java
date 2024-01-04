package com.kapresoft.devops.shell.converter.http.message;

import lombok.extern.log4j.Log4j2;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.kapresoft.devops.shell.config.KapresoftProjectProperties;
import com.kapresoft.devops.shell.pojo.BuildInfoDetails;

import org.apache.commons.io.IOUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

@Log4j2
@Component
public class S3ObjectSummaryToBuildInfoDetailsConverter implements BuildInfoConverter<S3ObjectSummary, BuildInfoDetails> {

    private final ApplicationContext ctx;
    private final AmazonS3 amazonS3;

    public S3ObjectSummaryToBuildInfoDetailsConverter(ApplicationContext ctx, AmazonS3 amazonS3) {
        this.ctx = ctx;
        this.amazonS3 = amazonS3;
    }

    @Override
    public BuildInfoDetails convert(@NonNull S3ObjectSummary s3o) {
        KapresoftProjectProperties projConf = ctx.getBean(KapresoftProjectProperties.class);
        try (S3ObjectInputStream is = amazonS3.getObject(s3o.getBucketName(), s3o.getKey()).getObjectContent()) {
            String yamlText = IOUtils.toString(new InputStreamReader(new BufferedInputStream(is)));
            return toBuildInfoDetails(yamlText, s3o, projConf).orElse(null);
        } catch (AmazonS3Exception | IOException e) {
            log.error("Failed to read {}", s3o.getKey(), e);
            throw new IllegalStateException("Exception while reading build info[%s]: %s".formatted(s3o.getKey(), e.getMessage()), e);
        }
    }

}
