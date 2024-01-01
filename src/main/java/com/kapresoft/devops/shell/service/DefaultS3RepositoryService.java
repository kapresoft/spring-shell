package com.kapresoft.devops.shell.service;

import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.kapresoft.devops.shell.config.S3BucketProperties;
import com.kapresoft.devops.shell.exception.service.NonUniqueResultException;
import com.kapresoft.devops.shell.pojo.BuildInfo;
import com.kapresoft.devops.shell.pojo.S3Bucket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.Optional.*;

@Log4j2
@Repository
public class DefaultS3RepositoryService implements S3RepositoryService {

    @Nonnull
    private final AmazonS3 amazonS3;
    @Nonnull
    private final S3Bucket s3Bucket;
    @Nonnull
    private final RestTemplate restTemplate;
    @NonNull
    private final ConversionService conversionService;
    @NonNull
    private final URI cdnURL;
    @NonNull
    private final String buildInfoFile;

    public DefaultS3RepositoryService(@NonNull AmazonS3 amazonS3,
                                      @NonNull S3BucketProperties s3BucketProperties,
                                      @Nonnull RestTemplate restTemplate,
                                      @NonNull ConversionService conversionService,
                                      @NonNull @Value("${spring.application.code-build-project.kapresoft-articles.cdn}") URI cdnURL,
                                      @NonNull @Value("${spring.application.code-build-project.kapresoft-articles.build-info-file:build.yml}") String buildInfoFile) {
        this.amazonS3 = amazonS3;
        this.s3Bucket = s3BucketProperties.getS3Bucket();
        this.restTemplate = restTemplate;
        this.conversionService = conversionService;
        this.cdnURL = cdnURL;
        this.buildInfoFile = buildInfoFile;
    }

    @NonNull
    @Override
    public URI getCdnURL() {
        return cdnURL;
    }

    @Override
    public Optional<BuildInfo> getLiveBuildInfo() {
        final URI buildInfoUri = UriComponentsBuilder.fromUri(cdnURL)
                .path(buildInfoFile).build()
                .toUri();
        return ofNullable(restTemplate.getForObject(buildInfoUri, String.class))
                .map(s -> {
                    return conversionService.convert(s, BuildInfo.class);
                });
    }

    @Override
    public Optional<S3ObjectSummary> find(@NonNull Predicate<S3ObjectSummary> predicate, @NonNull Supplier<ListObjectsV2Request> requestSupplier) {
        final List<S3ObjectSummary> results = findAll(predicate, requestSupplier);
        if (results.size() == 1) {
            return of(results.get(0));
        }
        if (results.size() > 1) {
            throw new NonUniqueResultException("S3ObjectSummary");
        }
        return empty();
    }

    @Override
    public List<S3ObjectSummary> findAll(@NonNull Predicate<S3ObjectSummary> predicate,
                                         @NonNull Supplier<ListObjectsV2Request> requestSupplier) {
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
    public void findAll(@NonNull Predicate<S3ObjectSummary> predicate,
                         @NonNull Supplier<ListObjectsV2Request> requestSupplier,
                         @NonNull Consumer<Stream<S3ObjectSummary>> streamConsumer) {
        ListObjectsV2Request request = requestSupplier.get();

        ListObjectsV2Result response;
        do {
            response = amazonS3.listObjectsV2(request);
            Stream<S3ObjectSummary> localStream = response.getObjectSummaries().stream().filter(predicate);
            streamConsumer.accept(localStream);
            request = requestSupplier.get()
                    .withContinuationToken(response.getNextContinuationToken());
            log.debug("Truncated={} ContinuationToken={}", response.isTruncated(), response.getNextContinuationToken());
        } while (response.isTruncated());
    }

    String getVersionInfo(String siteBucketPath) {
        return "";
    }

}
