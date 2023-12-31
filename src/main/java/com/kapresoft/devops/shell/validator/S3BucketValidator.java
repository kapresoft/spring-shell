package com.kapresoft.devops.shell.validator;


import com.kapresoft.devops.shell.pojo.S3Bucket;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

@Component
public class S3BucketValidator implements Validator {

    @Override
    public boolean supports(Class<?> clazz) {
        return clazz.isAssignableFrom(S3Bucket.class);
    }

    @Override
    public void validate(@NonNull Object target, @NonNull Errors errors) {
        S3Bucket bucket = (S3Bucket) target;

        ValidationUtils.rejectIfEmpty(errors, "uri", "uri.empty");
        ValidationUtils.rejectIfEmpty(errors, "name", "name.empty");
        if (errors.hasErrors()) {
            return;
        }

        if (!bucket.uri().startsWith("s3://")) {
            errors.reject("uri", "Invalid URI Protocol. Must be in the form s3://{bucketName}");
        }
        if (errors.hasErrors()) {
            return;
        }

        if (bucket.name().contains("/")) {
            errors.reject("name", "Invalid bucket name: %s".formatted(bucket.name()));
        }
    }

}
