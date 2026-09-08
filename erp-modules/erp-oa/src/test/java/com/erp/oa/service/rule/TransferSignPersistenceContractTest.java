package com.erp.oa.service.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignTask;

@DisplayName("调岗签约持久化契约")
class TransferSignPersistenceContractTest
{
    @Test
    @DisplayName("双份MySQL57迁移必须同步增加动作任务和草稿调岗审计列")
    void shouldDefineDualTransferMigration() throws Exception
    {
        Path root = repoFile("sql/erp_hr_transfer_effective_date_20260713.sql");
        Path docker = repoFile("docker/mysql/db/erp_hr_transfer_effective_date_20260713.sql");
        String sql = Files.readString(root, StandardCharsets.UTF_8);
        assertThat(sql).isEqualTo(Files.readString(docker, StandardCharsets.UTF_8));
        assertThat(sql).contains(
                "actual_confirm_time", "risk_confirmation_json", "historical_reason",
                "before_snapshot_json", "after_snapshot_json", "business_effective_date",
                "historical_supplement", "transfer_effective_date",
                "before_dept_name_snapshot", "before_post_name_snapshot");
    }

    @Test
    @DisplayName("任务和签约包Mapper必须完整读写冻结调岗上下文")
    void shouldMapFrozenTransferContext() throws Exception
    {
        assertThat(Arrays.stream(OaSignTask.class.getMethods()).map(method -> method.getName()))
                .contains("getBeforeSnapshotJson", "getAfterSnapshotJson",
                        "getBusinessEffectiveDate", "getHistoricalSupplement");
        assertThat(Arrays.stream(OaSignPackage.class.getMethods()).map(method -> method.getName()))
                .contains("getTransferEffectiveDate", "getHistoricalSupplement",
                        "getBeforeDeptNameSnapshot", "getBeforePostNameSnapshot");
        String taskMapper = new String(Resources.getResourceAsStream(
                "mapper/oa/OaSignTaskMapper.xml").readAllBytes(), StandardCharsets.UTF_8);
        assertThat(taskMapper).contains(
                "property=\"beforeSnapshotJson\"", "#{beforeSnapshotJson}",
                "property=\"afterSnapshotJson\"", "#{afterSnapshotJson}",
                "business_effective_date", "historical_supplement");
        String packageMapper = new String(Resources.getResourceAsStream(
                "mapper/oa/OaSignPackageMapper.xml").readAllBytes(), StandardCharsets.UTF_8);
        assertThat(packageMapper).contains(
                "transfer_effective_date", "before_dept_name_snapshot",
                "before_post_name_snapshot", "#{transferEffectiveDate}",
                "coalesce(#{historicalSupplement}, 0)");
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
            {
                return candidate;
            }
        }
        return Paths.get(relativePath).toAbsolutePath().normalize();
    }
}
