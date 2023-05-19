package com.kapresoft.devops.shell;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.shell.jline.PromptProvider;

import java.util.Properties;

@Configuration
public class ShellConfig {

    @Bean
    public PromptProvider promptProvider() {
        return () -> new AttributedString("cdn:> ", AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
    }

    @Bean
    BuildProperties buildProperties() {
        Properties p = new Properties();
        p.setProperty("group", "com.kapresoft.devops.shell");
        p.setProperty("version", "0.0.1-SNAPSHOT");
        p.setProperty("name", "shell");
        p.setProperty("artifact", "shell-0.0.1-SNAPSHOT.jar");

        return new BuildProperties(p);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }
}
