package com.kapresoft.devops.shell.converter.http.message;

import com.kapresoft.devops.shell.pojo.BuildInfo;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

class YamlTextToBuildInfoConverterTest {

    private final ClassPathResource yamlResource = new ClassPathResource("build.yml");
    private final YamlTextToBuildInfoConverter converter = new YamlTextToBuildInfoConverter();
    private String yamlText;

    @BeforeEach
    void setUp() throws IOException {
        yamlText = IOUtils.toString(yamlResource.getInputStream(), Charset.defaultCharset());
        assertThat(yamlText).withFailMessage("Could not find build.yml in the classpath.")
                .isNotEmpty();
    }

    @Test
    void convert() {
        BuildInfo buildInfo = converter.convert(yamlText);
        assertThat(buildInfo).isNotNull().satisfies( b -> {
            assertThat(b.getId()).as("id")
                    .isEqualTo("Kapresoft-Articles:b9ed4a9e-cc0f-4612-9f18-30f065a6543a");
            assertThat(b.getDate()).as("date")
                    .hasSizeGreaterThan(10);
            assertThat(b.getCommitHash()).as("commit-hash")
                    .isEqualTo("31df8236bd2ee88ebad66601c48f7c895461af9a");
            assertThat(b.getBuildDate()).as("date-long")
                    .isNotNull();
            assertThat(b.getBuildNumber()).as("build-number")
                    .isEqualTo("b9ed4a9e-cc0f-4612-9f18-30f065a6543a");
            assertThat(b.getDeployKey()).as("deploy-key")
                    .isEqualTo("site/b9ed4a9e-cc0f-4612-9f18-30f065a6543a/31df8236bd2ee88ebad66601c48f7c895461af9a");
        });
    }

}
