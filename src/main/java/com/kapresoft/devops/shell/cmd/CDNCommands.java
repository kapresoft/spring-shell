package com.kapresoft.devops.shell.cmd;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClientBuilder;
import com.amazonaws.services.cloudfront.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kapresoft.devops.shell.config.S3BucketProperties;
import com.kapresoft.devops.shell.opt.DefaultSettings;
import com.kapresoft.devops.shell.pojo.S3Bucket;
import com.kapresoft.devops.shell.service.S3RepositoryService;

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
            Examples Paths:
               /live-2023-05-25 or\s
               s3://kapresoft/site/e97ef8e8-e5cb-43f8-af68-20b70c140119""";
    private static final String INVALIDATE_PATH_HELP = "The CDN web path to invalidate, i.e. '/docs/*' or '/images/*', or '/*', etc...";
    private static final String DIST_HELP = """
            The CloudFront distribution ID, i.e. 'E1OAOW8NPJ78SQ' (Optional). 
            Defaults to user env var AWS_CLOUDFRONT_DIST_ID.
            """;
    private static final String INVALID_DIST_ID_MSG = "CDN with distID[%s] failed with: %s%n  code=[%s] status=[%s]";

    private final ObjectMapper objectMapper;
    private final DefaultSettings defaultSettings;
    private final S3RepositoryService s3RepositoryService;
    //private final CustomValidator validator;
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
    @ShellMethod(value = "Get CloudFront Distribution Config", key = "config")
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
     * <b>Usage:</b> update-path {@code <path> [distID]}
     * <pre>{@code
     * shell:> update-path [path] [distID]
     * shell:> update-path /live-2023-May-17-01 E1ODOX7NPJ77SQ
     * }</pre>
     *
     * @param optionalDistID The CloudFront Distribution ID. Usually stored in env.
     * @param newPath        The new path to set, i.e. '/new-path'
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
    @ShellMethod(value = "List valid sites", key = "ls-sites")
    public String listSites() {
        final List<S3ObjectSummary> sites = s3RepositoryService.findAll(
                s3 -> s3.getKey().endsWith("build.txt"), () -> new ListObjectsV2Request()
                        .withBucketName(s3Bucket.name()).withPrefix("site"));
        log.info("sites: {}", sites.size());
        return StringUtils.collectionToDelimitedString(sites, System.lineSeparator());
    }

    private void validatePath(String path) {
        ListObjectsV2Request request = new ListObjectsV2Request().withBucketName(s3Bucket.name());
        ListObjectsV2Result response = s3Client.listObjectsV2(request);

        response.getObjectSummaries().stream().filter(p -> {
            var key = p.getKey();
            return key.startsWith("site") && key.endsWith("build.txt");
        }).forEach(s -> {
            System.out.printf("%s: %s%n", s.getKey(), s.getLastModified());
        });
        // Iterate through the object summaries and print their names
        //for (S3ObjectSummary objectSummary : response.getObjectSummaries()) {
        //    System.out.println("Object Key: " + objectSummary.getKey());
        //}
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
        if (!p.startsWith("/")) {
            p = "/" + p;
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
