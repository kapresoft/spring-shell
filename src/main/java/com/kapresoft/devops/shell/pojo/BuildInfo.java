package com.kapresoft.devops.shell.pojo;

import lombok.Builder;
import lombok.Value;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Date;

@Value
public class BuildInfo {

    /**
     * Format: [projectName:Kapresoft-Articles]:[guid:a8e62b5b-1dd4-4da5-ba55-958c935bca52]
     */
    String id;
    String date;
    Date buildDate;
    String commitHash;
    String buildNumber;
    String deployKey;
    Date lastModified;
    boolean manualBuild;

    @Builder(toBuilder = true)
    public BuildInfo(@NonNull String id,
                     @NonNull String date,
                     @NonNull String commitHash,
                     @Nullable String buildNumber,
                     @Nullable String deployKey,
                     @Nullable Date buildDate,
                     @Nullable Date lastModified,
                     @Nullable boolean manualBuild) {
        this.date = date;
        this.id = id;
        this.commitHash = commitHash;
        this.buildNumber = buildNumber;
        this.deployKey = deployKey;

        this.buildDate = buildDate;
        this.lastModified = lastModified;
        this.manualBuild = manualBuild;
    }

}
