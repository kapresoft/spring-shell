package com.kapresoft.devops.shell.service;

import lombok.extern.log4j.Log4j2;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.kapresoft.devops.shell.config.S3BucketProperties;
import com.kapresoft.devops.shell.pojo.S3Bucket;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Log4j2
@Repository
public class DefaultS3RepositoryService implements S3RepositoryService {

    private final AmazonS3 amazonS3;
    private final S3Bucket s3Bucket;

    public DefaultS3RepositoryService(@NonNull AmazonS3 amazonS3, @NonNull S3BucketProperties s3BucketProperties) {
        this.amazonS3 = amazonS3;
        this.s3Bucket = s3BucketProperties.getS3Bucket();
    }

    @Override
    public List<S3ObjectSummary> findAll(Predicate<S3ObjectSummary> predicate,
                                         Supplier<ListObjectsV2Request> requestSupplier) {
        List<S3ObjectSummary> results = new ArrayList<>();
        findAll(predicate, requestSupplier, stream -> {
            List<S3ObjectSummary> found = stream.toList();
            if (!found.isEmpty()) {
                results.addAll(found);
            }
        });
        return results;
    }

        @Override
    public void findAll(Predicate<S3ObjectSummary> predicate,
                         Supplier<ListObjectsV2Request> requestSupplier,
                         Consumer<Stream<S3ObjectSummary>> streamConsumer) {
        ListObjectsV2Request request = requestSupplier.get();

        ListObjectsV2Result response;
        do {
            response = amazonS3.listObjectsV2(request);
            Stream<S3ObjectSummary> localStream = response.getObjectSummaries().stream().filter(predicate);
            streamConsumer.accept(localStream);
            request = requestSupplier.get()
                    .withContinuationToken(response.getNextContinuationToken());
            log.info("Truncated={} ContinuationToken={}", response.isTruncated(), response.getNextContinuationToken());
        } while (response.isTruncated());
    }

    String getVersionInfo(String siteBucketPath) {
        return "";
    }

}
