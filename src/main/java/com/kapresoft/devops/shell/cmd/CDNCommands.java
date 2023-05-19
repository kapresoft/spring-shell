package com.kapresoft.devops.shell.cmd;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClientBuilder;
import com.amazonaws.services.cloudfront.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapresoft.devops.shell.DefaultSettings;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.Optional;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.hasLength;

@Log4j2
@ShellComponent
public class CDNCommands {


    private static final String INVALID_CLOUD_FRONT_DISTRIBUTION_CONFIG_MSG = "Invalid CloudFront distribution config returned: %s";

    private final ObjectMapper objectMapper;
    private final DefaultSettings defaultSettings;
    private final AmazonCloudFront cloudFrontClient;

    public CDNCommands(ObjectMapper objectMapper,
                       DefaultSettings defaultSettings) {
        this.objectMapper = objectMapper;
        this.defaultSettings = defaultSettings;
        this.cloudFrontClient = AmazonCloudFrontClientBuilder.defaultClient();
    }

    /**
     * @param distID The CloudFront Distribution-ID
     * @return String Resolves to the Distribution-ID if {@code distID} is empty.
     */
    private String resolveDistID(String distID) {
        var resolvedDistID = hasLength(distID) ? distID : defaultSettings.getDistributionID();
        log.info("DistID: {}", resolvedDistID);
        return resolvedDistID;
    }

    /**
     * @param distID The CloudFront Distribution-ID
     * @return DistributionConfig
     */
    private GetDistributionConfigResult getDistributionConfig(String distID) {
        final GetDistributionConfigRequest request = new GetDistributionConfigRequest()
                .withId(distID);
        return cloudFrontClient.getDistributionConfig(request);
    }


    /**
     * <b>Usage:</b> cdn-get --dist {@code <Distribution-ID>};
     * <pre>{@code
     * shell:> cdn-get E1ODOX7NPJ77SQ
     * shell:> cdn-get --dist E1ODOX7NPJ77SQ
     * }</pre>
     *
     * @param optionalDistID The CloudFront Distribution ID
     * @return String The command status message; if any.
     */
    @SneakyThrows
    @ShellMethod(value = "Get CloudFront Distribution Config", key = "cdn-get")
    public String cdnGetConfig(@ShellOption(value = "dist", defaultValue = "") String optionalDistID) {
        var distID = resolveDistID(optionalDistID);
        if (!hasLength(distID)) {
            return "DistID was not resolved.";
        }
        DistributionConfig result;
        try {
            result = getDistributionConfig(distID).getDistributionConfig();
        } catch (AccessDeniedException | NoSuchDistributionException e) {
            return format("CDN with distID[%s] failed with: %s%n  code=[%s] status=[%s]",
                    distID, e.getErrorMessage(), e.getErrorCode(), e.getStatusCode());
        }

        return objectMapper.writeValueAsString(result);
    }

    /**
     * <b>Usage:</b> cdn-update-path {@code <distID> <path>}
     * <pre>{@code
     * shell:> cdn-update-path [distID] [path]
     * shell:> cdn-update-path  E1ODOX7NPJ77SQ /live-2023-May-17-01
     * }</pre>
     *
     * @param distIDArg The CloudFront Distribution ID
     * @param newPath The new path to set, i.e. '/new-path'
     * @return String The command status message; if any.
     */
    @SneakyThrows
    @ShellMethod(value = "Update the CloudFront distribution origin path", key = "cdn-update-path")
    public String cdnUpdatePath(@ShellOption(value = "dist", defaultValue = "") String distIDArg,
                                @ShellOption(value = "path", help = "The new path value, i.e. '/live-123'") String newPath) {
        var distID = resolveDistID(distIDArg);
        GetDistributionConfigResult distConfigResult = getDistributionConfig(distID);

        DistributionConfig distConfig = distConfigResult.getDistributionConfig();
        Optional<Origin> origin = distConfig.getOrigins().getItems().stream().findFirst();
        if (origin.isEmpty()) {
            return format(INVALID_CLOUD_FRONT_DISTRIBUTION_CONFIG_MSG, objectMapper.writeValueAsString(distConfig));
        }
        origin.get().setOriginPath(newPath);
        UpdateDistributionRequest request = new UpdateDistributionRequest()
                .withId(distID)
                .withIfMatch(distConfigResult.getETag())
                .withDistributionConfig(distConfig);
        cloudFrontClient.updateDistribution(request);

        return "Success";
    }

}
