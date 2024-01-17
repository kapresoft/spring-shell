package com.kapresoft.devops.shell.pojo;

import lombok.Builder;
import lombok.Value;

import com.amazonaws.services.cloudfront.model.Aliases;
import com.amazonaws.services.cloudfront.model.DistributionConfig;
import com.amazonaws.services.cloudfront.model.GetDistributionConfigResult;
import com.amazonaws.services.cloudfront.model.Origin;
import com.amazonaws.services.cloudfront.model.Origins;

import org.springframework.lang.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.of;

@Value
public class DistributionConfigData {

    @NonNull
    String distID;
    @NonNull
    DistributionConfig distConfig;
    String eTag;
    List<Origin> origins;

    @Builder
    public DistributionConfigData(@NonNull String distID, @NonNull GetDistributionConfigResult awsDistConfigResult) {
        this.distID = distID;
        this.distConfig = awsDistConfigResult.getDistributionConfig();
        eTag = awsDistConfigResult.getETag();
        origins = Optional.ofNullable(this.distConfig.getOrigins())
                .map(Origins::getItems)
                .orElse(Collections.emptyList());
    }

    public Optional<Origin> getFirstOrigin() {
        return origins.isEmpty() ? Optional.empty() : of(origins.get(0));
    }

    public Optional<String> getFirstOriginPath() {
        return getFirstOrigin().map(Origin::getOriginPath);
    }

    public Optional<String> getS3Key() {
        return getFirstOriginPath().map(o -> {
            if (o.startsWith("/")) {
                return o.substring(1);
            }
            return o;
        });
    }

    public List<String> getAliases() {
        return Optional.ofNullable(distConfig.getAliases())
                .map(Aliases::getItems)
                .orElse(Collections.emptyList());
    }



}
