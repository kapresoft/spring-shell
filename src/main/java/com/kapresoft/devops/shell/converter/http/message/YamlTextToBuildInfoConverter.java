package com.kapresoft.devops.shell.converter.http.message;

import com.kapresoft.devops.shell.pojo.BuildInfo;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.Date;
import java.util.Map;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

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

    public static BuildInfo fromMap(final Map<String, Object> map) {
        final Date buildDateAmericaLosAngeles = parseBuildDate(map);
        return BuildInfo.builder()
                .id((String) map.get("id"))
                .date((String) map.get("date"))
                .buildDate(buildDateAmericaLosAngeles)
                // initially set lastModified as buildDate (close estimation)
                .lastModified(buildDateAmericaLosAngeles)
                .commitHash(parseValue(map, "commit-hash"))
                .buildNumber(parseValue(map, "build-number"))
                .deployKey(parseValue(map, "deploy-key"))
                .build();
    }

    private static <T> T parseValue(final Map<String, Object> map, String key) {
        //noinspection unchecked
        return (T) ofNullable(map).flatMap(m -> ofNullable(map.get(key)))
                .orElse(null);
    }

    private static Date parseBuildDate(final Map<String, Object> map) {
        return ofNullable(map).flatMap(m -> ofNullable(m.get("date-long")))
                .map(v -> new Date(Long.parseLong(v.toString()) * 1000L)).orElse(null);
    }
}
