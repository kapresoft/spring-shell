package com.kapresoft.devops.shell.cmd;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClientBuilder;
import com.amazonaws.services.cloudfront.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapresoft.devops.shell.opt.DefaultSettings;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.Calendar;
import java.util.Optional;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.hasLength;

@Log4j2
@ShellComponent
public class CDNCommands {

    private static final String INVALID_CLOUD_FRONT_DISTRIBUTION_CONFIG_MSG = "Invalid CloudFront distribution config returned: %s";
    private static final String UPDATE_PATH_HELP = "The CloudFront origin path, i.e. '/live-2023-05-25'";
    private static final String INVALIDATE_PATH_HELP = "The CDN web path to invalidate, i.e. '/docs/*' or '/images/*', or '/*', etc...";
    private static final String DIST_HELP = "The CloudFront distribution ID, i.e. 'E1OAOW8NPJ78SQ' (Optional)." +
            " Defaults to user env var AWS_CLOUDFRONT_DIST_ID.";
    private static final String INVALID_DIST_ID_MSG = "CDN with distID[%s] failed with: %s%n  code=[%s] status=[%s]";

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
     * @return String Resolves to the DefaultSettings Distribution-ID if {@code distID} is empty.
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
     * <b>Usage:</b> get --dist {@code <Distribution-ID>};
     * <pre>{@code
     * shell:> get E1ODOX7NPJ77SQ
     * shell:> get --dist E1ODOX7NPJ77SQ
     * }</pre>
     *
     * @param optionalDistID The CloudFront Distribution ID
     * @return String The command status message; if any.
     */
    @SneakyThrows
    @ShellMethod(value = "Get CloudFront Distribution Config", key = "config")
    public String getConfig(
            @ShellOption(value = "dist", help = DIST_HELP, defaultValue = "") String optionalDistID) {

        var distID = resolveDistID(optionalDistID);
        if (!hasLength(distID)) {
            return "DistID was not resolved.";
        }

        DistributionConfig result;
        try {
            result = getDistributionConfig(distID).getDistributionConfig();
        } catch (AccessDeniedException | NoSuchDistributionException e) {
            return format(INVALID_DIST_ID_MSG,
                    distID, e.getErrorMessage(), e.getErrorCode(), e.getStatusCode());
        }

        return objectMapper.writeValueAsString(result);
    }

    /**
     * <b>Usage:</b> update-path {@code <path> [distID]}
     * <pre>{@code
     * shell:> update-path [path] [distID]
     * shell:> update-path /live-2023-May-17-01 E1ODOX7NPJ77SQ
     * }</pre>
     *
     * @param optionalDistID The CloudFront Distribution ID. Usually stored in env.
     * @param newPath The new path to set, i.e. '/new-path'
     * @return String The command status message; if any.
     */
    @SneakyThrows
    @ShellMethod(value = "Update the CloudFront distribution origin path", key = "update-path")
    public String updatePath(
            @ShellOption(value = "path", help = UPDATE_PATH_HELP) String newPath,
            @ShellOption(value = "dist", help = DIST_HELP, defaultValue = "") String optionalDistID) {

        var distID = resolveDistID(optionalDistID);
        GetDistributionConfigResult distConfigResult = getDistributionConfig(distID);

        DistributionConfig distConfig = distConfigResult.getDistributionConfig();
        Optional<Origin> origin = distConfig.getOrigins().getItems().stream().findFirst();
        if (origin.isEmpty()) {
            return format(INVALID_CLOUD_FRONT_DISTRIBUTION_CONFIG_MSG,
                    objectMapper.writeValueAsString(distConfig));
        }

        origin.get().setOriginPath(newPath);
        UpdateDistributionRequest request = new UpdateDistributionRequest()
                .withId(distID)
                .withIfMatch(distConfigResult.getETag())
                .withDistributionConfig(distConfig);
        cloudFrontClient.updateDistribution(request);

        return "Success";
    }

    /**
     * <b>Usage:</b> invalidate-path {@code [distID] <path>}
     * <pre>{@code
     * shell:> invalidate-path  [path] [distID]
     * shell:> invalidate-path  --path [path] --dist [distID]
     * shell:> invalidate-path  E1ODOX7NPJ77SQ /*
     * shell:> invalidate-path  E1ODOX7NPJ77SQ /docs/*
     * shell:> invalidate-path  E1ODOX7NPJ77SQ /img/*.jpg
     * }</pre>
     *
     * @param optionalDistID The CloudFront Distribution ID
     * @param path The CDN web path to invalidate, i.e. '/docs/*' or '/images/*', or '/*', etc...
     * @return String The command status message; if any.
     */
    @SneakyThrows
    @ShellMethod(value = "Invalidate a CloudFront distribution web path", key = "invalidate-path")
    public String invalidatePath(
            @ShellOption(value = "path", help = INVALIDATE_PATH_HELP) String path,
            @ShellOption(value = "dist", help = DIST_HELP, defaultValue = "") String optionalDistID) {

        var distID = resolveDistID(optionalDistID);

        GetDistributionConfigResult distConfigResult = getDistributionConfig(distID);

        DistributionConfig distConfig = distConfigResult.getDistributionConfig();
        Optional<Origin> origin = distConfig.getOrigins().getItems().stream().findFirst();
        if (origin.isEmpty()) {
            return format(INVALID_CLOUD_FRONT_DISTRIBUTION_CONFIG_MSG,
                    objectMapper.writeValueAsString(distConfig));
        }

        String callerReference = "spring-shell-aws-" + Calendar.getInstance().getTimeInMillis();
        log.info("Caller Reference: {}", callerReference);
        InvalidationBatch batch = new InvalidationBatch()
                .withPaths(new Paths().withItems(path)
                        .withQuantity(1))
                .withCallerReference(callerReference);
        CreateInvalidationRequest request = new CreateInvalidationRequest()
                .withDistributionId(distID)
                .withInvalidationBatch(batch);
        cloudFrontClient.createInvalidation(request);

        return "Success";
    }

}
