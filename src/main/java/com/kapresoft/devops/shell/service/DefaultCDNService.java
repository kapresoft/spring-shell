package com.kapresoft.devops.shell.service;

import lombok.extern.log4j.Log4j2;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.model.GetDistributionConfigRequest;
import com.amazonaws.services.cloudfront.model.GetDistributionConfigResult;
import com.kapresoft.devops.shell.exception.service.AmazonServiceCallException;
import com.kapresoft.devops.shell.opt.DefaultSettings;
import com.kapresoft.devops.shell.pojo.DistributionConfigData;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

@Log4j2
@Service
public class DefaultCDNService implements CDNService {

    private static final String INVALID_DIST_ID_MSG = "CDN with distID[%s] failed with: %s%n  code=[%s] status=[%s]";

    private final AmazonCloudFront cloudFront;
    private final DefaultSettings defaultSettings;

    public DefaultCDNService(AmazonCloudFront cloudFront, DefaultSettings defaultSettings) {
        this.cloudFront = cloudFront;
        this.defaultSettings = defaultSettings;
    }

    @NonNull
    @Override
    public DistributionConfigData getDistributionConfig(String id) throws AmazonServiceCallException {
        String distID = resolveDistID(id);
        final GetDistributionConfigRequest request = new GetDistributionConfigRequest()
                .withId(distID);

        try {
            GetDistributionConfigResult distConfigResult = cloudFront.getDistributionConfig(request);
            return DistributionConfigData.builder()
                    .distID(distID)
                    .awsDistConfigResult(distConfigResult)
                    .build();
        } catch (AmazonServiceException e) {
            String msg = format(INVALID_DIST_ID_MSG,
                    distID, e.getErrorMessage(), e.getErrorCode(), e.getStatusCode());
            throw (new AmazonServiceCallException(msg, e));
        }
    }

    @Override
    public DistributionConfigData getDistributionConfig() throws AmazonServiceCallException {
        return getDistributionConfig(null);
    }

    /**
     * @param distID The CloudFront Distribution-ID
     * @return String Resolves to the DefaultSettings Distribution-ID if {@code distID} is empty.
     */
    private String resolveDistID(String distID) {
        var resolvedDistID = ofNullable(distID).filter(id -> !id.trim().isBlank())
                .orElse(defaultSettings.getDistributionID());
        log.info("DistID: {}", resolvedDistID);
        return resolvedDistID;
    }
}
