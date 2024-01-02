package com.kapresoft.devops.shell.pojo;

import lombok.Builder;
import lombok.Value;

import org.springframework.lang.NonNull;

@Value
public class BuildInfo {

    /**
     * Format: [projectName:Kapresoft-Articles]:[guid:a8e62b5b-1dd4-4da5-ba55-958c935bca52]
     */
    String id;
    String date;
    String commitHash;

    @Builder(toBuilder = true)
    public BuildInfo(@NonNull String id,
                     @NonNull String date,
                     @NonNull String commitHash) {
        this.date = date;
        this.id = id;
        this.commitHash = commitHash;
    }

}
