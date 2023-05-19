package com.kapresoft.devops.shell.cmd;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClientBuilder;
import com.amazonaws.services.cloudfront.model.AccessDeniedException;
import com.amazonaws.services.cloudfront.model.DistributionConfig;
import com.amazonaws.services.cloudfront.model.GetDistributionConfigRequest;
import com.amazonaws.services.cloudfront.model.GetDistributionConfigResult;
import com.amazonaws.services.cloudfront.model.NoSuchDistributionException;
import com.amazonaws.services.cloudfront.model.Origin;
import com.amazonaws.services.cloudfront.model.UpdateDistributionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapresoft.devops.shell.DefaultSettings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.Environment;
import org.springframework.shell.CompletionContext;
import org.springframework.shell.CompletionProposal;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.shell.standard.ValueProvider;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.hasLength;

@Log4j2
@ShellComponent
public class CDNCommands {


    private final Environment env;
    private final ObjectMapper objectMapper;
    private final DefaultSettings defaultSettings;
    private final AmazonCloudFront cloudFrontClient;

    public CDNCommands(Environment env,
                       ObjectMapper objectMapper,
                       DefaultSettings defaultSettings) {
        this.env = env;
        this.objectMapper = objectMapper;
        this.defaultSettings = defaultSettings;
        this.cloudFrontClient = AmazonCloudFrontClientBuilder.defaultClient();
    }

    private String resolveDistID(String distID) {
        var resolvedDistID = hasLength(distID) ? distID : defaultSettings.getDistributionID();
        log.info("DistID: {}", resolvedDistID);
        return resolvedDistID;
    }


    /**
     * @param distID DistributionID
     * @return DistributionConfig
     */
    private GetDistributionConfigResult getDistributionConfig(String distID) {
        final GetDistributionConfigRequest request = new GetDistributionConfigRequest()
                .withId(distID);
        return cloudFrontClient.getDistributionConfig(request);
    }


    /**
     * <code>
     * cdn E1ODOX7NPJ77SQ
     * </code>
     *
     * @param distIDArg
     * @return String - Command output message
     */
    @SneakyThrows
    @ShellMethod(value = "Upload file to CDN", key = "cdn")
    public String cdnGetConfig(@ShellOption(value = "dist", defaultValue = "") String distIDArg) {
        var distID = resolveDistID(distIDArg);
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
     * <code>
     * cdn-update [distID] [path]
     * cdn-update  E1ODOX7NPJ77SQ /live-2023-May-17-01
     * </code>
     *
     * @param distIDArg
     * @param newPath
     * @return
     */
    @SneakyThrows
    @ShellMethod(value = "Upload file to CDN", key = "cdn-update")
    public String cdnUpdatePath(@ShellOption(value = "dist", defaultValue = "E1ODOX7NPJ77SQ") String distIDArg,
                                @ShellOption(value = "path", help = "The new path value, i.e. '/live-123'") String newPath) {
        var distID = resolveDistID(distIDArg);
        GetDistributionConfigResult distConfigResult = getDistributionConfig(distID);

        DistributionConfig distConfig = distConfigResult.getDistributionConfig();
        Optional<Origin> origin = distConfig.getOrigins().getItems().stream().findFirst();
        if (origin.isEmpty()) {
            return format("Invalid distribution config returned: %s", objectMapper.writeValueAsString(distConfig));
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
