package com.kapresoft.devops.shell.converter.http.message;

import com.kapresoft.devops.shell.config.KapresoftProjectProperties;
import com.kapresoft.devops.shell.pojo.BuildInfoDetails;

import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;

import java.util.Optional;

// TODO: Delete me (Unused).
@SuppressWarnings("unused")
public class YamlTextToBuildDetailsConverter implements BuildInfoConverter<String, BuildInfoDetails> {

    private final ApplicationContext ctx;

    public YamlTextToBuildDetailsConverter(@NonNull ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public BuildInfoDetails convert(@NonNull String yamlText) {
        final KapresoftProjectProperties projectConfig = ctx.getBean(KapresoftProjectProperties.class);

        Optional<BuildInfoDetails> buildInfo = toBuildInfoDetails(yamlText, null, projectConfig);
        if (buildInfo.isEmpty()) {
            return null;
        }
        return buildInfo.orElse(null);
    }

}
