package com.kapresoft.devops.shell.service;

import com.kapresoft.devops.shell.exception.service.AmazonServiceCallException;
import com.kapresoft.devops.shell.pojo.DistributionConfigData;

public interface CDNService {

    /**
     * @param id The distribution ID
     */
    DistributionConfigData getDistributionConfig(String id) throws AmazonServiceCallException;

    /**
     * Retrieves the distribution ID from default settings
     */
    DistributionConfigData getDistributionConfig() throws AmazonServiceCallException;

}
