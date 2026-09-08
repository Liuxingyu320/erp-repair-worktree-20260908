package com.erp.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("云盘网关路由边界")
class FileDriveRouteConfigTest
{
    @Test
    @DisplayName("鉴权云盘路由位于旧静态文件路由之前并移除 file 前缀")
    void shouldDeclareAuthenticatedDriveRouteBeforeStaticRoute() throws Exception
    {
        String yaml = Files.readString(Path.of("src/main/resources/bootstrap.yml"));
        String routeId = "- id: erp-file-drive-api";
        int driveRoute = yaml.indexOf(routeId);
        int staticRoute = yaml.indexOf("- id: erp-file-static");

        assertThat(driveRoute).isGreaterThanOrEqualTo(0);
        assertThat(staticRoute).isGreaterThan(driveRoute);
        String block = yaml.substring(driveRoute, staticRoute);
        assertThat(block)
                .contains("uri: http://127.0.0.1:9300")
                .contains("Path=/file/drive/**")
                .contains("StripPrefix=1")
                .doesNotContain("AddResponseHeader=Access-Control-Allow-Origin,*");
    }

    @Test
    @DisplayName("旧上传路由和匿名白名单不被扩大到云盘")
    void shouldNotWidenLegacyOrAnonymousRules() throws Exception
    {
        String yaml = Files.readString(Path.of("src/main/resources/bootstrap.yml"));
        int legacyRoute = yaml.indexOf("- id: erp-file-api");
        int driveRoute = yaml.indexOf("- id: erp-file-drive-api");
        assertThat(legacyRoute).isGreaterThanOrEqualTo(0);
        assertThat(driveRoute).isGreaterThan(legacyRoute);
        String legacyBlock = yaml.substring(legacyRoute, driveRoute);
        String securityBlock = yaml.substring(yaml.indexOf("security:"));

        assertThat(legacyBlock).contains("Path=/file/upload,/file/delete")
                .doesNotContain("/file/drive");
        assertThat(securityBlock).doesNotContain("/file/drive");
    }
}
