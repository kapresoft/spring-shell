package com.kapresoft.devops.shell.util.aws;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import static java.util.Optional.ofNullable;


public class AmazonS3BeanFactory extends AbstractFactoryBean<AmazonS3> {

    /**
     * Defaults to {@code Regions.US_EAST_1}
     */
    private final Regions region;

    public AmazonS3BeanFactory() {
        this(null);
    }

    public AmazonS3BeanFactory(@Nullable Regions region) {
        this.region = ofNullable(region).orElse(Regions.US_EAST_1);
    }

    @Override
    public Class<?> getObjectType() {
        return AmazonS3.class;
    }

    @Override
    @NonNull
    protected AmazonS3 createInstance() {
        return AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .build();
    }
}
