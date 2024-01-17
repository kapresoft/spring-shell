package com.kapresoft.devops.shell.cmd;

import jakarta.validation.ValidationException;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.model.CreateInvalidationRequest;
import com.amazonaws.services.cloudfront.model.DistributionConfig;
import com.amazonaws.services.cloudfront.model.GetDistributionConfigRequest;
import com.amazonaws.services.cloudfront.model.GetDistributionConfigResult;
import com.amazonaws.services.cloudfront.model.InvalidationBatch;
import com.amazonaws.services.cloudfront.model.Origin;
import com.amazonaws.services.cloudfront.model.Paths;
import com.amazonaws.services.cloudfront.model.UpdateDistributionRequest;
import com.amazonaws.services.cloudfront.model.UpdateDistributionResult;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapresoft.devops.shell.config.KapresoftProjectProperties;
import com.kapresoft.devops.shell.decorator.BuildInfoCLIOutputDecorator;
import com.kapresoft.devops.shell.opt.DefaultSettings;
import com.kapresoft.devops.shell.pojo.BuildInfoDetails;
import com.kapresoft.devops.shell.pojo.DistributionConfigData;
import com.kapresoft.devops.shell.pojo.S3Bucket;
import com.kapresoft.devops.shell.service.CDNService;
import com.kapresoft.devops.shell.service.S3RepositoryService;

import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.hasLength;

@Log4j2
@ShellComponent
public class CDNCommands {

    private static final String INVALID_CLOUD_FRONT_DISTRIBUTION_CONFIG_MSG = "Invalid CloudFront distribution config returned: %s";
    private static final String UPDATE_PATH_HELP = """
            The full S3 URI to the new site or S3 sub-path. The end path slash(/) is not needed.
            Full Paths:
               s3://kapresoft/site/build-2024-Jan-16
               s3://kapresoft/site/76430603-5232-4722-ab25-74d975af8199/63b48b9203e1482acfeb7bb5df1c4791ba58b522
            or SubPaths:
               site/76430603-5232-4722-ab25-74d975af8199/63b48b9203e1482acfeb7bb5df1c4791ba58b522 or\s
               site/build-2024-Jan-16""";
    private static final String INVALIDATE_PATH_HELP = "The CDN web path to invalidate, i.e. '/docs/*' or '/images/*', or '/*', etc...";
    private static final String DIST_HELP = """
            The CloudFront distribution ID, i.e. 'E1OAOW8NPJ78SQ' (Optional).
            Defaults to user env var AWS_CLOUDFRONT_DIST_ID.
            """;
    private static final String RELEASE_VERSION_HELP = """
            The build version in s3://{s3-bucket}/site/{version}.
            Example:
            release 2e641ee8-9226-45d4-ab8c-7850e731d675
            release --version 2e641ee8-9226-45d4-ab8c-7850e731d675
            """;
    private static final String SITE_PATH_NAME = "site";
    private static final String INVALIDATE_MESSAGE = "Don't forget to invalidate-path on /*";
    private static final String CDN_ORIGINS_URL_FORMAT = "https://us-east-1.console.aws.amazon.com/cloudfront/v4/home?region=us-east-1#/distributions/%s/origins";
    private static final String S3_URL_FORMAT = "https://s3.console.aws.amazon.com/s3/buckets/kapresoft%s/";

    private final ObjectMapper objectMapper;
    private final DefaultSettings defaultSettings;
    private final S3RepositoryService s3RepositoryService;
    private final CDNService cdnService;
    private final AmazonCloudFront cloudFrontClient;

    private final S3Bucket s3Bucket;
    private final String buildInfoFile;

    public CDNCommands(ObjectMapper objectMapper,
                       DefaultSettings defaultSettings,
                       S3RepositoryService s3RepositoryService,
                       CDNService cdnService,
                       AmazonCloudFront cloudFrontClient,
                       KapresoftProjectProperties projectConf) {
        this.objectMapper = objectMapper;
        this.defaultSettings = defaultSettings;
        this.s3RepositoryService = s3RepositoryService;
        this.cdnService = cdnService;
        this.cloudFrontClient = cloudFrontClient;
        this.s3Bucket = projectConf.getS3Bucket();
        this.buildInfoFile = projectConf.getBuildInfoFile();
        log.info("S3 Bucket is: {}", this.s3Bucket);
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
    @ShellMethod(value = "Get CloudFront Distribution Config", key = { "config", "conf", "c" })
    public String getConfig(
            @ShellOption(value = "dist", help = DIST_HELP, defaultValue = "") String optionalDistID,
            @ShellOption(value = "json", help = "Option to print full JSON config") boolean entireConfig) {

        DistributionConfigData configData = cdnService.getDistributionConfig(optionalDistID);
        Optional<Origin> firstOrigin = configData.getFirstOrigin();
        if (entireConfig || firstOrigin.isEmpty()) {
            return objectMapper.writeValueAsString(configData.getDistConfig());
        }

        final Origin origin = firstOrigin.get();

        var b = new ArrayList<String>();
        b.add("  %-15s : %s".formatted("cdn-path", origin.getOriginPath()));
        b.add("  %-15s : %s".formatted("s3", origin.getDomainName()));
        b.add("  %-15s : %s".formatted("domain-aliases", configData.getAliases()));

        String distID = configData.getDistID();
        String cdnUrl = CDN_ORIGINS_URL_FORMAT.formatted(distID);
        String s3Url = S3_URL_FORMAT.formatted(origin.getOriginPath());
        b.add("  %-15s : %s".formatted("dist-id", distID));
        b.add("  %-15s : %s".formatted("cdn-link", cdnUrl));
        b.add("  %-15s : %s".formatted("s3-link", s3Url));

        return StringUtils.collectionToDelimitedString(b, System.lineSeparator());
    }

    /**
     * @param buildVersion   The build version
     * @param optionalDistID The CloudFront Distribution ID. Usually stored in env.
     * @return String The command status message; if any.
     */
    @SneakyThrows
    @ShellMethod(value = "Release a build version", key = {"release", "rel"})
    public String releaseVersion(
            @ShellOption(value = "version", help = RELEASE_VERSION_HELP) String buildVersion,
            @ShellOption(value = "dist", help = DIST_HELP, defaultValue = "") String optionalDistID,
            @ShellOption(value = "dryRun", help = "Dry Run, no executions", defaultValue = "true") boolean isDryRun) {

        final BuildInfoDetails buildInfo = findBuildInfoOrThrow(buildVersion);
        String pathPrefix = buildInfo.getCdnPath();
        log.info("CDN Path found: {}. Version is a valid candidate for release.", pathPrefix);

        if (isDryRun) {
            log.info("PathPrefix to deploy: {}", pathPrefix);
            return "Success; DryRun=true";
        }

        var distID = resolveDistID(optionalDistID);

        GetDistributionConfigResult distConfigResult = getDistributionConfig(distID);

        DistributionConfig distConfig = distConfigResult.getDistributionConfig();
        Optional<Origin> origin = distConfig.getOrigins().getItems().stream().findFirst();
        if (origin.isEmpty()) {
            return format(INVALID_CLOUD_FRONT_DISTRIBUTION_CONFIG_MSG,
                    objectMapper.writeValueAsString(distConfig));
        }
        origin.get().setOriginPath(pathPrefix);
        UpdateDistributionRequest request = new UpdateDistributionRequest()
                .withId(distID)
                .withIfMatch(distConfigResult.getETag())
                .withDistributionConfig(distConfig);
        UpdateDistributionResult result = cloudFrontClient.updateDistribution(request);

        return "Success; etag=%s %s".formatted(result.getETag(), INVALIDATE_MESSAGE);
    }

    /**
     * @param buildVersion The build version generated by CodeBuild, i.e. "2c2eba60-7f8f-40df-b99c-95db50e7b3a7"
     * @return String site/{build-version}/{project-name}
     */
    private BuildInfoDetails findBuildInfoOrThrow(String buildVersion) {
        String basePath = "site/%s/".formatted(buildVersion);
        Optional<S3ObjectSummary> found = s3RepositoryService.find(
                s3o -> s3o.getKey().startsWith(basePath) && s3o.getKey().endsWith(buildInfoFile),
                () -> new ListObjectsV2Request().withBucketName(s3Bucket.name()).withPrefix(SITE_PATH_NAME));
        return found.flatMap(s3RepositoryService::toBuildInfo)
                .orElseThrow(() -> new ValidationException("Invalid build version: %s".formatted(buildVersion)));
    }

    /**
     * @param newPath        The new path to set, i.e. '/new-path'
     * @param optionalDistID The CloudFront Distribution ID. Usually stored in env.
     * @return String The command status message; if any.
     */
    @SneakyThrows
    @ShellMethod(value = "Update the CloudFront distribution origin path", key = {"update-path", "up"})
    public String updatePath(
            @ShellOption(value = "path", help = UPDATE_PATH_HELP) String newPath,
            @ShellOption(value = "dist", help = DIST_HELP, defaultValue = "") String optionalDistID,
            @ShellOption(value = "dryRun", help = "Dry Run, no executions", defaultValue = "true") boolean isDryRun) {

        var distID = resolveDistID(optionalDistID);
        GetDistributionConfigResult distConfigResult = getDistributionConfig(distID);

        DistributionConfig distConfig = distConfigResult.getDistributionConfig();
        Optional<Origin> origin = distConfig.getOrigins().getItems().stream().findFirst();
        if (origin.isEmpty()) {
            return format(INVALID_CLOUD_FRONT_DISTRIBUTION_CONFIG_MSG,
                    objectMapper.writeValueAsString(distConfig));
        }

        String actualPath = resolvePath(newPath);
        validatePath(actualPath);
        log.info("Path resolved is: {}", actualPath);
        if (isDryRun) {
            return "Success; etag=none; dryRun=true";
        }

        origin.get().setOriginPath(actualPath);

        UpdateDistributionRequest request = new UpdateDistributionRequest()
                .withId(distID)
                .withIfMatch(distConfigResult.getETag())
                .withDistributionConfig(distConfig);
        UpdateDistributionResult result = cloudFrontClient.updateDistribution(request);

        return "Success; etag=%s".formatted(result.getETag());
    }

    @SneakyThrows
    @ShellMethod(value = "List valid sites", key = {"ls", "list"})
    public String listSites() {
        List<BuildInfoCLIOutputDecorator> output = new ArrayList<>();

        DistributionConfigData cdnConfig = cdnService.getDistributionConfig();
        String deployedCDNS3Key = cdnConfig.getS3Key().orElse("");

        s3RepositoryService.findAllBuildsAsDecorators(b -> {
            boolean isLive = s3RepositoryService.isLive(b.getBuildInfo(), deployedCDNS3Key);
            b.setLive(isLive);
            output.add(b);
        });

        final StringBuilder response = new StringBuilder();
        if (!output.isEmpty()) {
            response.append(System.lineSeparator());
            response.append("CDN: %s%s".formatted(s3RepositoryService.getCdnURI(), System.lineSeparator()));
            response.append(System.lineSeparator());
        }
        response.append(StringUtils.collectionToDelimitedString(output, System.lineSeparator()));
        return AnsiOutput.toString(AnsiColor.BRIGHT_WHITE, response.toString());
    }

    private void validatePath(String path) {
        String comparePath = "%s/build.yml".formatted(path);
        Optional<S3ObjectSummary> found = s3RepositoryService.find(
                s3o -> s3o.getKey().equalsIgnoreCase(comparePath),
                () -> new ListObjectsV2Request().withBucketName(s3Bucket.name()).withPrefix(SITE_PATH_NAME));
        found.ifPresent(s3ObjectSummary -> log.debug("Found match: {}", s3ObjectSummary));

        if (found.isEmpty()) {
            throw new ValidationException("Invalid site path: %s".formatted(path));
        }
    }

    /**
     * Start or end slash are optional
     * Valid Paths:<br>
     * <ul>
     * <li>s3://kapresoft/site/e97ef8e8-e5cb-43f8-af68-20b70c140119/Kapresoft-Articles/</li>
     * <li>s3://kapresoft/site/e97ef8e8-e5cb-43f8-af68-20b70c140119/Kapresoft-Articles</li>
     * <li>/site/e97ef8e8-e5cb-43f8-af68-20b70c140119/Kapresoft-Articles/</li>
     * <li>/site/e97ef8e8-e5cb-43f8-af68-20b70c140119/Kapresoft-Articles</li>
     * <li>site/e97ef8e8-e5cb-43f8-af68-20b70c140119/Kapresoft-Articles/</li>
     * <li>site/e97ef8e8-e5cb-43f8-af68-20b70c140119/Kapresoft-Articles</li>
     * </ul>
     *
     * @param path The s3 bucket prefix path
     * @return The resolved path
     */
    private String resolvePath(String path) {
        var p = path.replaceFirst(s3Bucket.uri(), "");
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.endsWith("/")) {
            p = p.substring(0, p.lastIndexOf("/"));
        }
        return p;
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
     * @param path           The CDN web path to invalidate, i.e. '/docs/*' or '/images/*', or '/*', etc...
     * @return String The command status message; if any.
     */
    @SneakyThrows
    @ShellMethod(value = "Invalidate a CloudFront distribution web path",
            key = { "invalidate-path", "inv" })
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
        log.debug("Caller Reference: {}", callerReference);
        InvalidationBatch batch = new InvalidationBatch()
                .withPaths(new Paths().withItems(path)
                        .withQuantity(1))
                .withCallerReference(callerReference);
        CreateInvalidationRequest request = new CreateInvalidationRequest()
                .withDistributionId(distID)
                .withInvalidationBatch(batch);
        cloudFrontClient.createInvalidation(request);

        return "Success; cache-invalidated=%s".formatted(path);
    }

}
