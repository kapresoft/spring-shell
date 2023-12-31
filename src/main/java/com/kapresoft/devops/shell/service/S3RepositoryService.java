package com.kapresoft.devops.shell.service;

import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface S3RepositoryService {

    List<S3ObjectSummary> findAll(Predicate<S3ObjectSummary> predicate,
                                  Supplier<ListObjectsV2Request> requestSupplier);

    void findAll(Predicate<S3ObjectSummary> predicate,
                 Supplier<ListObjectsV2Request> requestSupplier,
                 Consumer<Stream<S3ObjectSummary>> streamConsumer);

}
