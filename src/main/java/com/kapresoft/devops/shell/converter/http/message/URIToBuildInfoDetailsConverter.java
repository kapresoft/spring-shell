package com.kapresoft.devops.shell.converter.http.message;

import com.kapresoft.devops.shell.config.KapresoftProjectProperties;
import com.kapresoft.devops.shell.pojo.BuildInfoDetails;

import org.springframework.context.ApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static java.util.Optional.ofNullable;

@Component
public class URIToBuildInfoDetailsConverter implements BuildInfoConverter<URI, BuildInfoDetails> {

    private final ApplicationContext ctx;

    public URIToBuildInfoDetailsConverter(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public BuildInfoDetails convert(@NonNull URI buildInfoUri) {
        final RestTemplate restTemplate = ctx.getBean(RestTemplate.class);
        final KapresoftProjectProperties projConf = ctx.getBean(KapresoftProjectProperties.class);

        return ofNullable(restTemplate.getForObject(buildInfoUri, String.class))
                .flatMap(s -> toBuildInfoDetails(s, null, projConf))
                .orElse(null);
    }

}
