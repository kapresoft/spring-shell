package com.kapresoft.devops.shell;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.web.client.RestTemplate;

import java.util.Properties;
import java.util.Set;

@Configuration
public class ShellConfig {

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

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
    RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * We are setting up our own {@code ConversionServiceFactoryBean} because {@code ConversionService}
     * is only available in web environments.
     * @param converters Application custom converters
     * @return ConversionServiceFactoryBean This FactoryBean can be autowired to a {@code ConversionService}
     */
    @Bean
    ConversionServiceFactoryBean conversionService(Set<Converter> converters) {
        ConversionServiceFactoryBean svc = new ConversionServiceFactoryBean();
        svc.setConverters(converters);
        svc.afterPropertiesSet();
        return svc;
    }

}
