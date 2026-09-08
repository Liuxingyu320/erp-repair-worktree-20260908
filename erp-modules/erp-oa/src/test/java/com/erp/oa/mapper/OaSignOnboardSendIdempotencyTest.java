package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("入职签约补资料发送持久幂等")
class OaSignOnboardSendIdempotencyTest
{
    private static final String MIGRATION =
            "erp_oa_sign_onboard_send_idempotency_20260720.sql";

    @Test
    void mapperUsesOneAtomicMySql57CompatibleClaim() throws Exception
    {
        Configuration configuration = new Configuration();
        String resource = "mapper/oa/OaSignOnboardSendRequestMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }

        assertThat(configuration.hasStatement(
                OaSignOnboardSendRequestMapper.class.getName() + ".claim")).isTrue();
        assertThat(configuration.hasStatement(
                OaSignOnboardSendRequestMapper.class.getName() + ".selectByRequestId")).isTrue();
        String sql = configuration.getMappedStatement(
                OaSignOnboardSendRequestMapper.class.getName() + ".claim")
                .getBoundSql(Map.of()).getSql().replaceAll("\\s+", " ").trim();
        assertThat(sql)
                .contains("on duplicate key update")
                .contains("operation_id = last_insert_id(operation_id)")
                .contains("replay_count = replay_count + 1");
    }

    @Test
    void migrationIsIdenticalAndBootstrappedAfterOnboardSchema() throws Exception
    {
        String sql = read("sql/" + MIGRATION);
        assertThat(read("docker/mysql/db/" + MIGRATION)).isEqualTo(sql);
        assertThat(read("erp-modules/erp-oa/src/main/resources/db/migration/" + MIGRATION))
                .isEqualTo(sql);
        String bootstrap = read("docker/mysql/bootstrap-files.list");
        assertThat(bootstrap.lines().filter(MIGRATION::equals).count()).isEqualTo(1L);
        assertThat(bootstrap.indexOf(MIGRATION))
                .isGreaterThan(bootstrap.indexOf("erp_oa_sign_onboard_import_20260718.sql"));
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS oa_sign_onboard_send_request")
                .contains("UNIQUE KEY uk_oa_sign_onboard_send_request (request_id)")
                .contains("payload_hash char(64) NOT NULL")
                .doesNotContain("FOREIGN KEY");
    }

    private static String read(String relativePath) throws Exception
    {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path path = base.resolve(relativePath);
        if (!Files.exists(path))
            path = base.resolve("../..").resolve(relativePath).normalize();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
