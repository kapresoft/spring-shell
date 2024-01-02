package com.kapresoft.devops.shell.converter.http.message;

import lombok.extern.log4j.Log4j2;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.kapresoft.devops.shell.decorator.BuildInfoCLIOutputDecorator;

import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class S3ObjectSummaryToBuildDecoratorDetailsConverter implements Converter<S3ObjectSummary, BuildInfoCLIOutputDecorator> {

    private final ApplicationContext ctx;

    public S3ObjectSummaryToBuildDecoratorDetailsConverter(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public BuildInfoCLIOutputDecorator convert(@NonNull S3ObjectSummary s3o) {
        final S3ObjectSummaryToBuildInfoDetailsConverter converter = ctx.getBean(S3ObjectSummaryToBuildInfoDetailsConverter.class);
        var buildInfo = converter.convert(s3o);
        return BuildInfoCLIOutputDecorator.builder()
                .summary(s3o)
                .buildInfo(buildInfo)
                .live(false)
                .build();
    }

}
