package com.kapresoft.devops.shell.service;

import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.kapresoft.devops.shell.pojo.BuildInfo;

import org.springframework.lang.NonNull;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface S3RepositoryService {

    @NonNull
    URI getCdnURL();

    Optional<BuildInfo> getLiveBuildInfo();

    Optional<S3ObjectSummary> find(@NonNull Predicate<S3ObjectSummary> predicate, @NonNull Supplier<ListObjectsV2Request> requestSupplier);

    List<S3ObjectSummary> findAll(@NonNull Predicate<S3ObjectSummary> predicate,
                                  @NonNull Supplier<ListObjectsV2Request> requestSupplier);

    void findAll(@NonNull Predicate<S3ObjectSummary> predicate,
                 @NonNull Supplier<ListObjectsV2Request> requestSupplier,
                 @NonNull Consumer<Stream<S3ObjectSummary>> streamConsumer);

}
