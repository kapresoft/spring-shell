package com.kapresoft.devops.shell.pojo;

import lombok.Builder;
import lombok.Value;

@Value
public class BuildInfo {

    String id;
    String date;
    String commitHash;

    @Builder
    public BuildInfo(String id, String date, String commitHash) {
        this.date = date;
        this.id = id;
        this.commitHash = commitHash;
    }

}
