package com.kapresoft.devops.shell.validator;

import com.kapresoft.devops.shell.pojo.S3Bucket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;

import static org.assertj.core.api.Assertions.assertThat;

class S3BucketValidatorTest {


    private S3BucketValidator validator;

    @BeforeEach
    void setUp() {
        validator = new S3BucketValidator();
    }

    @Test
    void uriIsNull_ShouldFail() {
        S3Bucket bucket = new S3Bucket(null, "bucket-name");
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(bucket, "s3Bucket");
        validator.validate(bucket, result);
        assertThat(result.getErrorCount()).as("ErrorCount")
                .isEqualTo(1);
        assertThat(result.getFieldError("uri")).isNotNull()
                .satisfies(e -> assertThat(e.getCode()).as("Error Code")
                .isEqualTo("uri.empty"));
    }
    @Test
    void uriIsBlank_ShouldFail() {
        S3Bucket bucket = new S3Bucket("", "bucket-name");
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(bucket, "s3Bucket");
        validator.validate(bucket, result);
        assertThat(result.getErrorCount()).as("ErrorCount")
                .isEqualTo(1);
        assertThat(result.getFieldError("uri")).isNotNull()
                .satisfies(e -> assertThat(e.getCode()).as("Error Code")
                .isEqualTo("uri.empty"));
    }

    @Test
    void nameIsNull_ShouldFail() {
        S3Bucket bucket = new S3Bucket("s3://bucket-name", null);
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(bucket, "s3Bucket");
        validator.validate(bucket, result);
        assertThat(result.getErrorCount()).as("ErrorCount")
                .isEqualTo(1);
        assertThat(result.getFieldError("name")).isNotNull()
                .satisfies(e -> assertThat(e.getCode()).as("Error Code")
                        .isEqualTo("name.empty"));
    }

    @Test
    void nameIsBlank_ShouldFail() {
        S3Bucket bucket = new S3Bucket("s3://bucket-name", "");
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(bucket, "s3Bucket");
        validator.validate(bucket, result);
        assertThat(result.getErrorCount()).as("ErrorCount")
                .isEqualTo(1);
        assertThat(result.getFieldError("name")).isNotNull()
                .satisfies(e -> assertThat(e.getCode()).as("Error Code")
                        .isEqualTo("name.empty"));
    }
}
