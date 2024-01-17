package com.kapresoft.devops.shell;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClientBuilder;
import com.kapresoft.devops.shell.util.aws.AmazonS3BeanFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AWSConfig {

    @Bean
    AmazonCloudFront cloudFront() {
        return AmazonCloudFrontClientBuilder.defaultClient();
    }

    @Bean
    AmazonS3BeanFactory s3() throws Exception {
        AmazonS3BeanFactory f = new AmazonS3BeanFactory();
        f.setSingleton(true);
        f.afterPropertiesSet();
        return f;
    }

}
