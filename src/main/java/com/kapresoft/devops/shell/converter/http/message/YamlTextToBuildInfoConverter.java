package com.kapresoft.devops.shell.converter.http.message;

import lombok.extern.log4j.Log4j2;

import com.kapresoft.devops.shell.pojo.BuildInfo;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

import static java.util.Optional.of;

@Log4j2
@Component
public class YamlTextToBuildInfoConverter implements Converter<String, BuildInfo> {

    @Override
    public BuildInfo convert(@NonNull String yamlText) {
        return of(yamlText)
                .filter(StringUtils::hasLength)
                .map(YamlTextToBuildInfoConverter::fromText)
                .orElse(null);
    }

    public static BuildInfo fromText(String yamlText) {
        final Yaml yaml = new Yaml();
        return fromMap(yaml.load(yamlText));
    }

    public static BuildInfo fromMap(Map<String, Object> map) {
        return BuildInfo.builder()
                .id((String) map.get("id"))
                .date((String) map.get("date"))
                .commitHash((String) map.get("commit-hash"))
                .build();
    }
}
