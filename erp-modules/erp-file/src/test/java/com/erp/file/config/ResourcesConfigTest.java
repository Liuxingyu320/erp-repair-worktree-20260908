package com.erp.file.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("文件静态资源映射")
class ResourcesConfigTest
{
    @Test
    @DisplayName("文件服务只匿名映射明确public目录")
    void staticFileMappingShouldOnlyExposePublicDirectory() throws Exception
    {
        String source = Files.readString(Paths.get("src/main/java/com/erp/file/config/ResourcesConfig.java"),
                StandardCharsets.UTF_8);

        assertThat(source).contains("localFilePrefix + \"/public/**\"");
        assertThat(source).doesNotContain("localFilePrefix + \"/**\"");
    }
}
