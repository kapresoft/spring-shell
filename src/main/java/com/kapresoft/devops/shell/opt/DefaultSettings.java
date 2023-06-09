package com.kapresoft.devops.shell.opt;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DefaultSettings {

    @Getter
    String distributionID;

    public DefaultSettings(@Value("#{environment.AWS_CLOUDFRONT_DIST_ID}") String distributionID) {
        this.distributionID = distributionID;
    }

}
