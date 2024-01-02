package com.kapresoft.devops.shell.service;

import jakarta.annotation.Nonnull;
import lombok.extern.log4j.Log4j2;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapresoft.devops.shell.config.S3BucketProperties;
import com.kapresoft.devops.shell.decorator.BuildInfoCLIOutputDecorator;
import com.kapresoft.devops.shell.exception.service.NonUniqueResultException;
import com.kapresoft.devops.shell.pojo.BuildInfoDetails;
import com.kapresoft.devops.shell.pojo.S3Bucket;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
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
    private final ObjectMapper objectMapper;
    @NonNull
    private final URI cdnURI;
    @NonNull
    private final String buildInfoFile;

    public DefaultS3RepositoryService(@NonNull AmazonS3 amazonS3,
                                      @NonNull S3BucketProperties s3BucketProperties,
                                      @Nonnull RestTemplate restTemplate,
                                      @NonNull ConversionService conversionService,
                                      @NonNull ObjectMapper objectMapper,
                                      @NonNull @Value("${spring.application.code-build-project.kapresoft-articles.cdn}") URI cdnURL,
                                      @NonNull @Value("${spring.application.code-build-project.kapresoft-articles.build-info-file:build.yml}") String buildInfoFile) {
        this.amazonS3 = amazonS3;
        this.s3Bucket = s3BucketProperties.getS3Bucket();
        this.restTemplate = restTemplate;
        this.conversionService = conversionService;
        this.objectMapper = objectMapper;
        this.cdnURI = cdnURL;
        this.buildInfoFile = buildInfoFile;
    }

    @NonNull
    @Override
    public URI getCdnURI() {
        return cdnURI;
    }

    // I don't think this is needed: updateReleaseInfo
    @Override
    public void updateReleaseInfo(@NonNull BuildInfoDetails buildInfo) {
        S3Object s3Info;
        String prefix = buildInfo.getKeyPath() + "/build.yml";
        try {
            s3Info = amazonS3.getObject(s3Bucket.name(), prefix);
        } catch (SdkClientException e) {
            String message = "Failed to get Object: %s".formatted(prefix);
            log.error(message);
            throw new RuntimeException(message, e);
        }
        ObjectMetadata existingMetadata = s3Info.getObjectMetadata();
        // Create a new object metadata with updated values
        existingMetadata.addUserMetadata("git-hash", buildInfo.getCommitHash());
        existingMetadata.addUserMetadata("release-date", Calendar.getInstance().getTime().toString());
        // Add more metadata attributes to update as needed

        // Copy the object with updated metadata
        CopyObjectRequest copyRequest = new CopyObjectRequest(buildInfo.getS3Bucket().name(), prefix, buildInfo.getS3Bucket().name(), prefix)
                .withNewObjectMetadata(existingMetadata);

        amazonS3.copyObject(copyRequest);

        try {
            log.info("meta-data: {}", objectMapper.writeValueAsString(existingMetadata.getUserMetadata()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    @Override
    public Optional<BuildInfoDetails> getLiveBuildInfo() {
        final URI buildInfoUri = UriComponentsBuilder.fromUri(cdnURI)
                .path(buildInfoFile).build()
                .toUri();
        return ofNullable(restTemplate.getForObject(buildInfoUri, String.class))
                .map(s -> conversionService.convert(s, BuildInfoDetails.class));
    }

    @Override
    public BuildInfoCLIOutputDecorator toBuildInfoDecorator(S3ObjectSummary summary, String yamlText, BuildInfoDetails buildInfoLive) {
        var buildInfo = ofNullable(conversionService.convert(yamlText, BuildInfoDetails.class))
                .orElseThrow(() -> new IllegalArgumentException("Failed to convert yaml text: %s".formatted(yamlText)));
        return BuildInfoCLIOutputDecorator.builder()
                .summary(summary)
                .buildInfo(buildInfo)
                .buildInfoLive(buildInfoLive)
                .build();
    }

    @Override
    public BuildInfoDetails toBuildInfo(S3ObjectSummary summary) {
        BuildInfoDetails buildInfo = null;
        try (S3ObjectInputStream is = amazonS3.getObject(summary.getBucketName(), summary.getKey()).getObjectContent()) {
            String content = IOUtils.toString(new InputStreamReader(new BufferedInputStream(is)));
            buildInfo = ofNullable(conversionService.convert(content, BuildInfoDetails.class))
                    .orElseThrow(() -> new IllegalArgumentException("Failed to convert yaml text: %s".formatted(content)));
        } catch (Exception e) {
            log.error("Failed to read {}", summary.getKey(), e);
        }

        return buildInfo;
    }

    @NonNull
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

    @NonNull
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

}
