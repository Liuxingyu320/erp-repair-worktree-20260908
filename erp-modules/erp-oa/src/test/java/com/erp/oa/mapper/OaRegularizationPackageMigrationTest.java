package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.Introspector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("转正签约包快照迁移")
class OaRegularizationPackageMigrationTest
{
    private static final String MIGRATION = "sql/erp_oa_sign_regularization_20260712.sql";
    private static final String DOCKER_MIGRATION =
            "docker/mysql/db/erp_oa_sign_regularization_20260712.sql";

    @Test
    @DisplayName("双份幂等迁移为签约包新增实际转正日快照")
    void shouldShipMirroredIdempotentRegularizationDateMigration() throws Exception
    {
        String sql = readRepoFile(MIGRATION);
        String dockerSql = readRepoFile(DOCKER_MIGRATION);

        assertThat(dockerSql).isEqualTo(sql);
        assertThat(sql)
                .contains("TABLE_NAME = 'oa_sign_package'")
                .contains("COLUMN_NAME = 'actual_regularization_date'")
                .contains("ADD COLUMN actual_regularization_date varchar(10) DEFAULT NULL")
                .contains("COMMENT ''实际转正日期''")
                .contains("'DO 0'")
                .doesNotContain("DROP COLUMN", "UPDATE oa_sign_package");
    }

    @Test
    @DisplayName("领域对象和Mapper读写实际转正日快照")
    void shouldBindActualRegularizationDateAcrossDomainAndMapper() throws Exception
    {
        Set<String> properties = Arrays.stream(Introspector.getBeanInfo(
                        Class.forName("com.erp.oa.domain.OaSignPackage"))
                        .getPropertyDescriptors())
                .map(descriptor -> descriptor.getName())
                .collect(Collectors.toSet());
        String mapper = readRepoFile(
                "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml");

        assertThat(properties).contains("actualRegularizationDate");
        assertThat(mapper)
                .contains("property=\"actualRegularizationDate\" column=\"actual_regularization_date\"")
                .contains("actual_regularization_date")
                .contains("#{actualRegularizationDate}")
                .contains("actual_regularization_date = #{actualRegularizationDate}");
    }

    private String readRepoFile(String relativePath) throws Exception
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
        {
            path = base.resolve("../..").resolve(relativePath).normalize();
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
