package com.kapresoft.devops.shell.opt;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.shell.standard.commands.Version;
import org.springframework.stereotype.Component;

import static org.springframework.util.StringUtils.hasLength;

@Log4j2
@Order(0)
@Component
public class VersionInfoConfig implements ApplicationRunner {

    private final Environment env;
    private final Version version;

    public VersionInfoConfig(Environment env, Version version) {
        this.env = env;
        this.version = version;
    }

    @Override
    public void run(ApplicationArguments args) {
        String term = env.getProperty("TERM");
        if (hasLength(term)) {
            log.info("TERM is: {}", term);
        }

        version.setShowBuildVersion(true);
        version.setShowBuildGroup(true);
        version.setShowBuildArtifact(true);
        version.setShowBuildName(true);
    }
}
