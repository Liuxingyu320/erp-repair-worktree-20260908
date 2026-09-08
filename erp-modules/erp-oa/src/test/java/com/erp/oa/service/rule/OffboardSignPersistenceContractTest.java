package com.erp.oa.service.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("离职签约持久化契约")
class OffboardSignPersistenceContractTest
{
    @Test
    @DisplayName("双份 MySQL 5.7 迁移逐字一致并增加唯一 HR 权限和六个离职列")
    void definesDualIdempotentMigration() throws Exception
    {
        Path root = repoFile("sql/erp_hr_offboarding_automation_20260713.sql");
        Path docker = repoFile("docker/mysql/db/erp_hr_offboarding_automation_20260713.sql");
        assertThat(root).exists();
        assertThat(docker).exists();
        String sql = Files.readString(root, StandardCharsets.UTF_8);
        assertThat(sql).isEqualTo(Files.readString(docker, StandardCharsets.UTF_8));
        assertThat(sql).contains(
                "hr:employee:offboard",
                "sync_sign_hr_permissions_with_offboarding",
                "CALL sync_sign_hr_permissions_with_transfer()",
                "CALL sync_sign_hr_permissions_with_offboarding()",
                "offboarding_type varchar(32)",
                "salary_settlement_status varchar(16)",
                "asset_handover_status varchar(16)",
                "non_compete_decision varchar(32)",
                "compensation_amount decimal(16,2)",
                "compensation_note varchar(500)",
                "information_schema.COLUMNS", "PREPARE stmt FROM @sql")
                .doesNotContain("ADD COLUMN IF NOT EXISTS");
    }

    @Test
    @DisplayName("运行时签约权限由普通角色管理且兼容同步钩子不再改授权")
    void runtimePermissionSyncIsCompatibilityNoOp() throws Exception
    {
        String mapper = Files.readString(repoFile(
                "erp-modules/erp-system/src/main/resources/mapper/system/SysConfigMapper.xml"),
                StandardCharsets.UTF_8);
        assertThat(mapper)
                .contains("signing grants are now managed only through ordinary roles",
                        "update sys_role", "where 1 = 0")
                .doesNotContain("{CALL sync_sign_hr_permissions_with_offboarding()}");
    }

    @Test
    @DisplayName("签约包领域与mapper所有读写路径冻结六个离职字段")
    void persistsEveryOffboardingSnapshotFieldOnEveryMapperPath() throws Exception
    {
        String domain = Files.readString(repoFile(
                "erp-modules/erp-oa/src/main/java/com/erp/oa/domain/OaSignPackage.java"),
                StandardCharsets.UTF_8);
        String mapper = Files.readString(repoFile(
                "erp-modules/erp-oa/src/main/resources/mapper/oa/OaSignPackageMapper.xml"),
                StandardCharsets.UTF_8);
        for (String property : new String[] { "offboardingType", "salarySettlementStatus",
                "assetHandoverStatus", "nonCompeteDecision", "compensationAmount",
                "compensationNote" })
        {
            assertThat(domain).contains("get" + Character.toUpperCase(property.charAt(0))
                    + property.substring(1) + "()", "set"
                    + Character.toUpperCase(property.charAt(0)) + property.substring(1) + "(");
            assertThat(mapper).contains("property=\"" + property + "\"");
        }
        for (String column : new String[] { "offboarding_type",
                "salary_settlement_status", "asset_handover_status",
                "non_compete_decision", "compensation_amount", "compensation_note" })
        {
            assertThat(count(mapper, column)).as(column).isGreaterThanOrEqualTo(5);
        }
        assertThat(mapper).contains(
                "#{offboardingType}", "#{salarySettlementStatus}",
                "#{assetHandoverStatus}", "#{nonCompeteDecision}",
                "#{compensationAmount}", "#{compensationNote}");
    }

    private static int count(String text, String value)
    {
        int count = 0;
        for (int index = 0; (index = text.indexOf(value, index)) >= 0;
                index += value.length()) count++;
        return count;
    }

    private static Path repoFile(String relativePath)
    {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 6 && current != null; i++, current = current.getParent())
        {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)
                    || (Files.isDirectory(current.resolve("erp-modules"))
                        && Files.isDirectory(current.resolve("erp-ui"))))
                return candidate;
        }
        return Paths.get(relativePath).toAbsolutePath().normalize();
    }
}
