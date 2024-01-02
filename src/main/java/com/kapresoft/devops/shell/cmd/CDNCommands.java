package com.kapresoft.devops.shell.cmd;

import jakarta.validation.ValidationException;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClientBuilder;
import com.amazonaws.services.cloudfront.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapresoft.devops.shell.config.S3BucketProperties;
import com.kapresoft.devops.shell.decorator.BuildInfoCLIOutputDecorator;
import com.kapresoft.devops.shell.opt.DefaultSettings;
import com.kapresoft.devops.shell.pojo.BuildInfoDetails;
import com.kapresoft.devops.shell.pojo.S3Bucket;
import com.kapresoft.devops.shell.service.S3RepositoryService;

import org.apache.commons.io.IOUtils;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.util.StringUtils;

import java.io.BufferedInputStream;
import java.io.InputStreamReader;
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
            Examples Paths:
               /live-2023-05-25 or\s
               s3://kapresoft/site/e97ef8e8-e5cb-43f8-af68-20b70c140119""";
    private static final String INVALIDATE_PATH_HELP = "The CDN web path to invalidate, i.e. '/docs/*' or '/images/*', or '/*', etc...";
    private static final String DIST_HELP = """
            The CloudFront distribution ID, i.e. 'E1OAOW8NPJ78SQ' (Optional).
            Defaults to user env var AWS_CLOUDFRONT_DIST_ID.
            """;
    private static final String INVALID_DIST_ID_MSG = "CDN with distID[%s] failed with: %s%n  code=[%s] status=[%s]";
    private static final String RELEASE_VERSION_HELP = """
            The build version in s3://{s3-bucket}/site/{version}.
            Example:
            release 2e641ee8-9226-45d4-ab8c-7850e731d675
            release --version 2e641ee8-9226-45d4-ab8c-7850e731d675
            """;
    private static final String KAPRESOFT_ARTICLES = "Kapresoft-Articles";
    private static final String BUILD_INFO_FILE_NAME = "build.yml";
    private static final String SITE_PATH_NAME = "site";

    private final ObjectMapper objectMapper;
    private final DefaultSettings defaultSettings;
    private final S3RepositoryService s3RepositoryService;
    private final AmazonCloudFront cloudFrontClient;

    private final S3Bucket s3Bucket;
    private final AmazonS3 s3Client;

    public CDNCommands(ObjectMapper objectMapper, DefaultSettings defaultSettings,
                       S3RepositoryService s3RepositoryService,
                       AmazonS3 s3Client, S3BucketProperties s3BucketProperties) {
        this.objectMapper = objectMapper;
        this.defaultSettings = defaultSettings;
        this.s3RepositoryService = s3RepositoryService;
        this.cloudFrontClient = AmazonCloudFrontClientBuilder.defaultClient();
        this.s3Client = s3Client;
        this.s3Bucket = s3BucketProperties.getS3Bucket();
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
    @ShellMethod(value = "Get CloudFront Distribution Config", key = {"config", "conf"})
    public String getConfig(
            @ShellOption(value = "dist", help = DIST_HELP, defaultValue = "") String optionalDistID,
            @ShellOption(value = "json", help = "Option to print full JSON config") boolean entireConfig) {

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

        if (entireConfig) {
            return objectMapper.writeValueAsString(result);
        }
        if (result.getOrigins().getQuantity() < 0) {
            return objectMapper.writeValueAsString(result);
        }

        Origin origin = result.getOrigins().getItems().get(0);

        var b = new ArrayList<String>();
        b.add(" ID: %s".formatted(origin.getId()));
        b.add(" path: %s".formatted(origin.getOriginPath()));
        b.add(" S3 Origin Config: %s".formatted(origin.getS3OriginConfig()));
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
            @ShellOption(value = "dist", help = DIST_HELP, defaultValue = "") String optionalDistID) {

        final BuildInfoDetails buildInfo = findBuildInfoOrThrow(buildVersion);
        String pathPrefix = buildInfo.getCdnPath();
        log.info("CDN Path found: {}. Version is a valid candidate for release.", pathPrefix);

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

        return "Success; etag=%s".formatted(result.getETag());
    }

    /**
     * @param buildVersion The build version generated by CodeBuild, i.e. "2c2eba60-7f8f-40df-b99c-95db50e7b3a7"
     * @return String site/{build-version}/{project-name}
     */
    private BuildInfoDetails findBuildInfoOrThrow(String buildVersion) {
        String basePath = "site/%s/%s".formatted(buildVersion, KAPRESOFT_ARTICLES);
        String comparePath = "%s/%s".formatted(basePath, BUILD_INFO_FILE_NAME);
        Optional<S3ObjectSummary> found = s3RepositoryService.find(
                s3o -> s3o.getKey().equalsIgnoreCase(comparePath),
                () -> new ListObjectsV2Request().withBucketName(s3Bucket.name()).withPrefix(SITE_PATH_NAME));
        return found.map(s3RepositoryService::toBuildInfo)
                .orElseThrow(() -> new ValidationException("Invalid build version: %s".formatted(buildVersion)));
    }

    /**
     * @param newPath        The new path to set, i.e. '/new-path'
     * @param optionalDistID The CloudFront Distribution ID. Usually stored in env.
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

        String actualPath = resolvePath(newPath);
        validatePath(actualPath);
        log.info("Path resolved is: {}", actualPath);

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
        final List<S3ObjectSummary> sites = s3RepositoryService.findAll(
                s3 -> s3.getKey().endsWith(BUILD_INFO_FILE_NAME), () -> new ListObjectsV2Request()
                        .withBucketName(s3Bucket.name()).withPrefix(SITE_PATH_NAME));
        log.info("sites: {}", sites.size());
        List<BuildInfoCLIOutputDecorator> output = new ArrayList<>(sites.size());
        Optional<BuildInfoDetails> liveBuildInfo = s3RepositoryService.getLiveBuildInfo();
        log.debug("live: {}", liveBuildInfo);

        sites.forEach(s3o -> {
            try (S3ObjectInputStream is = s3Client.getObject(s3o.getBucketName(), s3o.getKey()).getObjectContent()) {
                String content = IOUtils.toString(new InputStreamReader(new BufferedInputStream(is)));
                var buildInfo = s3RepositoryService.toBuildInfoDecorator(s3o, content, liveBuildInfo.orElse(null));
                output.add(buildInfo);
            } catch (Exception e) {
                log.error("Failed to read {}", s3o.getKey(), e);
            }
        });
        String response = "";
        if (!sites.isEmpty()) {
            response += System.lineSeparator();
            response += "CDN: %s%s".formatted(s3RepositoryService.getCdnURI(), System.lineSeparator());
            response += System.lineSeparator();
        }
        response += StringUtils.collectionToDelimitedString(output, System.lineSeparator());
        return AnsiOutput.toString(AnsiColor.BRIGHT_WHITE, response);
    }

    private void validatePath(String path) {
        String comparePath = "%s/build.txt".formatted(path);
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
        log.debug("Caller Reference: {}", callerReference);
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
