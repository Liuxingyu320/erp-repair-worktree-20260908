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

@DisplayName("单HR签约任务中心迁移")
class OaSignTaskMigrationTest
{
    @Test
    @DisplayName("双份迁移一致并建立任务事件通知事实表")
    void shouldShipCompatibleTaskCenterMigration() throws Exception
    {
        String sql = readRepoFile("sql/erp_oa_sign_task_center_20260711.sql");
        String dockerSql = readRepoFile("docker/mysql/db/erp_oa_sign_task_center_20260711.sql");

        assertThat(dockerSql).isEqualTo(sql);
        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS oa_sign_task")
                .contains("assigned_hr_user_id bigint NOT NULL")
                .contains("source_event_version varchar(64) NOT NULL")
                .contains("dedupe_key varchar(180) NOT NULL")
                .contains("automation_level varchar(20) NOT NULL DEFAULT 'MANUAL'")
                .contains("version bigint NOT NULL DEFAULT 0")
                .contains("UNIQUE KEY uk_oa_sign_task_no (task_no)")
                .contains("UNIQUE KEY uk_oa_sign_task_dedupe (dedupe_key)")
                .contains("KEY idx_oa_sign_task_hr_status (assigned_hr_user_id, status, created_time)")
                .contains("CREATE TABLE IF NOT EXISTS oa_sign_task_event")
                .contains("request_id varchar(64)")
                .contains("UNIQUE KEY uk_oa_sign_task_event_request (request_id)")
                .contains("prev_event_hash varchar(64)")
                .contains("event_hash varchar(64) NOT NULL")
                .contains("CREATE TABLE IF NOT EXISTS oa_sign_notification_outbox")
                .contains("UNIQUE KEY uk_oa_sign_notification_business (channel, recipient_user_id, business_key)")
                .contains("payload_json json NOT NULL")
                .contains("ADD COLUMN task_id bigint DEFAULT NULL")
                .contains("ADD COLUMN confirm_status varchar(32) DEFAULT NULL")
                .contains("ADD COLUMN plan_version_id bigint DEFAULT NULL")
                .contains("UNIQUE KEY uk_oa_sign_package_task (task_id)")
                .doesNotContain("INSERT INTO oa_sign_task SELECT")
                .doesNotContain("UPDATE oa_sign_package SET task_id");
    }

    @Test
    @DisplayName("更换唯一HR时只迁移非终态任务并留下可查询审计")
    void shouldMigrateInFlightTasksWhenConfiguredHrChanges() throws Exception
    {
        String sql = readRepoFile("sql/erp_oa_sign_task_center_20260711.sql");

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS sys_sign_hr_state")
                .contains("CREATE TABLE IF NOT EXISTS oa_sign_task_hr_reassignment")
                .contains("old_hr_user_id")
                .contains("new_hr_user_id")
                .contains("task_status")
                .contains("reassigned_time")
                .contains("INSERT INTO oa_sign_task_hr_reassignment")
                .contains("UPDATE oa_sign_task")
                .contains("assigned_hr_user_id = current_hr_user_id")
                .contains("version = version + 1")
                .contains("status NOT IN ('SIGNED', 'REFUSED', 'EXPIRED', 'CANCELLED', 'NO_ACTION')")
                .contains("IF current_hr_user_id IS NOT NULL THEN")
                .contains("ON DUPLICATE KEY UPDATE hr_user_id = current_hr_user_id");
    }

    @Test
    @DisplayName("权限同步先锁常驻单例并只接受有效系统用户配置")
    void shouldLockPersistentStateBeforeResolvingValidConfiguredHr() throws Exception
    {
        String sql = readRepoFile("sql/erp_oa_sign_task_center_20260711.sql");
        String procedure = sql.substring(sql.indexOf("CREATE PROCEDURE sync_sign_hr_permissions()"));

        assertThat(sql)
                .contains("hr_user_id bigint DEFAULT NULL")
                .contains("ALTER TABLE sys_sign_hr_state MODIFY COLUMN hr_user_id bigint DEFAULT NULL")
                .contains("INSERT IGNORE INTO sys_sign_hr_state (state_id, hr_user_id, managed_role_id)")
                .contains("VALUES (1, NULL, NULL)");
        int stateLock = procedure.indexOf("FROM sys_sign_hr_state");
        int configLock = procedure.indexOf("FROM sys_config");
        int userLock = procedure.indexOf("FROM sys_user");
        assertThat(stateLock).isGreaterThanOrEqualTo(0);
        assertThat(configLock).isGreaterThan(stateLock);
        assertThat(userLock).isGreaterThan(configLock);
        assertThat(procedure.substring(stateLock, configLock)).contains("FOR UPDATE");
        assertThat(procedure.substring(configLock, userLock)).contains("FOR UPDATE");
        assertThat(procedure.substring(userLock)).contains("FOR UPDATE");
        assertThat(procedure)
                .contains("current_hr_config_value")
                .contains("configured_hr.status = '0'")
                .contains("configured_hr.del_flag = '0'");
    }

    @Test
    @DisplayName("唯一HR业务菜单只授予功能专用角色且技术证据权限保持独立")
    void shouldManageDedicatedSingleHrRoleWithoutTouchingSharedRoles() throws Exception
    {
        String sql = readRepoFile("sql/erp_oa_sign_task_center_20260711.sql");
        String procedure = sql.substring(sql.indexOf("CREATE PROCEDURE sync_sign_hr_permissions()"));

        assertThat(sql)
                .contains("managed_role_id bigint")
                .contains("'唯一HR签约'")
                .contains("'sign_single_hr'")
                .contains("data_scope")
                .contains("'5'")
                .contains("INSERT INTO sys_role")
                .contains("managed.menu_id <> 4607")
                .contains("DELETE FROM sys_user_role")
                .contains("INSERT IGNORE INTO sys_user_role")
                .contains("DELETE FROM sys_role_menu")
                .contains("INSERT IGNORE INTO sys_role_menu");
        assertThat(procedure)
                .contains("task_menu.perms IN")
                .doesNotContain("hr_role.role_id")
                .doesNotContain("'oa:signTask:technicalEvidence'");
    }

    @Test
    @DisplayName("前向迁移把签约菜单移出人事冲突区并重建最小权限")
    void shouldRepairConflictingSigningMenuIdsWithForwardMigration() throws Exception
    {
        String sql = readRepoFile("sql/erp_oa_sign_menu_permission_repair_20260716.sql");
        String dockerSql = readRepoFile(
                "docker/mysql/db/erp_oa_sign_menu_permission_repair_20260716.sql");

        assertThat(dockerSql).isEqualTo(sql);
        assertThat(sql)
                .contains("SIGNAL SQLSTATE '45000'")
                .contains("menu_id BETWEEN 9650 AND 9669")
                .contains("AND NOT COALESCE((")
                .contains("BINARY path = BINARY 'sign-task'")
                .contains("BINARY route_name = BINARY 'OaSignTask'")
                .contains("menu_type = 'C'")
                .contains("COALESCE(path, '') = ''")
                .contains("component IS NULL")
                .contains("menu_type = 'F'")
                .contains("(9650, '合同签约中心'")
                .contains("(9656, '签约技术证据'")
                .contains("(9660, '签约包发送'")
                .contains("(9661, '签约包撤回'")
                .contains("(9666, '公司主体查看'")
                .contains("(9669, '合同印章维护'")
                .contains("old_menu.menu_id = 4600")
                .contains("BINARY old_menu.component = BINARY 'oa/signTask/index'")
                .contains("BINARY old_menu.perms = BINARY 'oa:signTask:list'")
                .contains("DROP PROCEDURE IF EXISTS sync_sign_hr_permissions")
                .contains("CREATE PROCEDURE sync_sign_hr_permissions()")
                .contains("CREATE PROCEDURE sync_sign_hr_permissions_with_plan()")
                .contains("CREATE PROCEDURE sync_sign_hr_permissions_with_transfer()")
                .contains("CREATE PROCEDURE sync_sign_hr_permissions_with_offboarding()")
                .contains("CALL sync_sign_hr_permissions_with_offboarding()")
                .contains("tmp_sign_eligible_role_20260716")
                .contains("evidence_menu.menu_id IN (4520, 4521, 4522, 4523, 4524, 4525,",
                        "4605, 4606, 4608, 4609, 4610)")
                .contains("JOIN tmp_sign_eligible_role_20260716 eligible")
                .contains("JOIN sys_menu old_entry ON old_entry.menu_id = role_menu.menu_id",
                        "old_entry.menu_id = 4600",
                        "BINARY old_entry.perms = BINARY 'oa:signTask:list'")
                .contains("old_menu.menu_id = 4607",
                        "old_menu.perms = 'oa:signTask:technicalEvidence'")
                .contains("old_menu.menu_id = 4603",
                        "BINARY old_menu.perms = BINARY 'oa:signTask:confirm'")
                .contains("query = VALUES(query)",
                        "is_frame = VALUES(is_frame)",
                        "is_cache = VALUES(is_cache)")
                .contains("WHERE BINARY role_key = BINARY 'sign_single_hr'")
                .doesNotContain("INSERT INTO sys_menu SELECT")
                .doesNotContain("SELECT eligible.role_id, 9650");

        String legacySnapshot = sql.substring(
                sql.indexOf("INSERT IGNORE INTO tmp_sign_role_permission_20260716 (role_id, perms)"),
                sql.indexOf("INSERT IGNORE INTO tmp_sign_role_permission_20260716 (role_id, perms)",
                        sql.indexOf("INSERT IGNORE INTO tmp_sign_role_permission_20260716 (role_id, perms)") + 1));
        assertThat(legacySnapshot)
                .contains("old_menu.menu_id = 4600", "old_menu.menu_type = 'C'");

        String legacyPackageReachability = sql.substring(
                sql.indexOf("-- Keep the legacy sign-package C route independently reachable"),
                sql.indexOf("-- Migrate the task-center entry only"));
        assertThat(legacyPackageReachability)
                .contains("SELECT package_role.role_id, oa_root.menu_id",
                        "package_root.menu_id = 4520", "package_root.parent_id = 3000",
                        "BINARY package_root.path = BINARY 'sign-package'",
                        "BINARY package_root.component = BINARY 'oa/signPackage/index'",
                        "BINARY package_root.route_name = BINARY 'OaSignPackage'",
                        "package_root.menu_type = 'C'",
                        "BINARY package_root.perms = BINARY 'oa:signPackage:list'",
                        "oa_root.menu_id = 3000", "oa_root.parent_id = 0",
                        "BINARY oa_root.path = BINARY 'oa'",
                        "BINARY oa_root.route_name = BINARY 'OaRoot'")
                .doesNotContain("9650");

        String taskCenterEntryMigration = sql.substring(
                sql.indexOf("-- Migrate the task-center entry only"),
                sql.indexOf("-- Remove links only from exact legacy signing identities"));
        assertThat(taskCenterEntryMigration)
                .contains("SELECT role_menu.role_id, 9650", "old_entry.menu_id = 4600",
                        "BINARY old_entry.component = BINARY 'oa/signTask/index'",
                        "BINARY old_entry.perms = BINARY 'oa:signTask:list'")
                .doesNotContain("4520", "package_root", "package_role");

        String legacyCleanup = sql.substring(
                sql.indexOf("DELETE role_menu"),
                sql.indexOf("-- Disable only the legacy task root"));
        String legacyRootDisable = sql.substring(
                sql.indexOf("-- Disable only the legacy task root"),
                sql.indexOf("-- The one-time confirmation action"));
        assertThat(legacyCleanup)
                .contains("old_menu.menu_id = 4600", "old_menu.menu_type IN ('C', 'M')")
                .doesNotContain("4520", "4521", "4522", "4523", "4524", "4525", "4526");
        assertThat(legacyRootDisable)
                .contains("old_menu.menu_id = 4600", "old_menu.menu_type IN ('C', 'M')")
                .doesNotContain("4520",
                        "BINARY old_menu.path = BINARY 'sign-package'",
                        "BINARY old_menu.route_name = BINARY 'OaSignPackage'");

        String procedure = sql.substring(sql.indexOf("CREATE PROCEDURE sync_sign_hr_permissions()"),
                sql.indexOf("DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_plan"));
        assertThat(procedure)
                .contains("task_menu.menu_id = 9650 OR task_menu.parent_id = 9650")
                .contains("'oa:signPackage:send'", "'oa:signPackage:void'",
                        "'oa:signPackage:add'", "'oa:signTask:resolveRefusal'",
                        "'oa:signTask:resolveExpiry'")
                .contains("INSERT INTO sys_sign_hr_menu_grant")
                .contains("managed.menu_id IN (")
                .contains("managed_role_count", "managed_role_key")
                .contains("COLUMN_NAME = 'resolution_status'")
                .contains("status IN ('REFUSED', 'EXPIRED') AND resolution_status = 'OPEN'")
                .doesNotContain("'oa:signTask:confirm'")
                .doesNotContain("'oa:signTask:technicalEvidence'")
                .doesNotContain("DELETE FROM sys_sign_hr_menu_grant;")
                .doesNotContain("AND task_menu.menu_id <> 9656")
                .doesNotContain("DELETE FROM sys_role_menu\n         WHERE role_id = managed_sign_role_id;");

        String ownershipPreflight = sql.substring(
                sql.indexOf("CREATE PROCEDURE assert_sign_menu_id_ownership_20260716()"),
                sql.indexOf("CALL assert_sign_menu_id_ownership_20260716()"));
        assertThat(ownershipPreflight)
                .contains("'sys_sign_hr_state', 'sys_sign_hr_menu_grant'",
                        "'oa_sign_task', 'oa_sign_task_hr_reassignment'",
                        "'oa_sign_notification_outbox'",
                        "IF signing_table_count <> 5 THEN",
                        "'task_id', 'assigned_hr_user_id', 'status', 'version'",
                        "'task_id', 'old_hr_user_id', 'new_hr_user_id'",
                        "'task_status', 'reassigned_time'",
                        "'state_id', 'hr_user_id', 'managed_role_id', 'updated_time'",
                        "'role_id', 'menu_id', 'hr_user_id', 'created_time'",
                        "IF signing_support_column_count <> 17 THEN",
                        "'outbox_id', 'channel', 'recipient_user_id', 'business_key'",
                        "'payload_json', 'status', 'retry_count', 'next_retry_time'",
                        "'last_result', 'last_error', 'version', 'created_time'",
                        "IF outbox_column_count <> 13 OR NOT EXISTS",
                        "COLUMN_NAME = 'payload_json'",
                        "DATA_TYPE = 'json'",
                        "INDEX_NAME = 'uk_oa_sign_notification_business'",
                        "BINARY outbox_index.index_columns = BINARY 'channel,recipient_user_id,business_key'",
                        "outbox_index.non_unique = 0",
                        "IF outbox_unique_count <> 1 THEN")
                .contains("menu_id = 3000", "parent_id = 0",
                        "BINARY path = BINARY 'oa'", "COALESCE(component, '') = ''",
                        "BINARY route_name = BINARY 'OaRoot'", "menu_type = 'M'",
                        "COALESCE(perms, '') = ''", "is_frame = 1",
                        "visible = '0'", "status = '0'");

        int oaParentGrantStart = procedure.indexOf(
                "INSERT IGNORE INTO sys_role_menu (role_id, menu_id)",
                procedure.indexOf("The RuoYi router builder"));
        int businessGrantStart = procedure.indexOf(
                "INSERT IGNORE INTO sys_role_menu (role_id, menu_id)",
                oaParentGrantStart + 1);
        String oaParentGrant = procedure.substring(oaParentGrantStart, businessGrantStart);
        assertThat(oaParentGrant)
                .contains("task_menu.menu_id = 3000", "task_menu.parent_id = 0",
                        "BINARY task_menu.path = BINARY 'oa'",
                        "COALESCE(task_menu.component, '') = ''",
                        "BINARY task_menu.route_name = BINARY 'OaRoot'",
                        "task_menu.menu_type = 'M'", "COALESCE(task_menu.perms, '') = ''",
                        "task_menu.is_frame = 1", "task_menu.visible = '0'",
                        "task_menu.status = '0'");

        int oaParentTrackingStart = procedure.indexOf("INSERT INTO sys_sign_hr_menu_grant");
        int businessTrackingStart = procedure.indexOf("INSERT INTO sys_sign_hr_menu_grant",
                oaParentTrackingStart + 1);
        String oaParentTracking = procedure.substring(oaParentTrackingStart, businessTrackingStart);
        assertThat(oaParentTracking)
                .contains("task_menu.menu_id = 3000", "task_menu.parent_id = 0",
                        "BINARY task_menu.path = BINARY 'oa'",
                        "COALESCE(task_menu.component, '') = ''",
                        "BINARY task_menu.route_name = BINARY 'OaRoot'",
                        "task_menu.menu_type = 'M'", "COALESCE(task_menu.perms, '') = ''",
                        "task_menu.is_frame = 1", "task_menu.visible = '0'",
                        "task_menu.status = '0'");

        String managedCleanup = procedure.substring(0,
                procedure.indexOf("IF current_hr_user_id IS NOT NULL THEN"));
        assertThat(managedCleanup.split("3000, 9650", -1))
                .as("OA parent must be removed by all three managed cleanup statements")
                .hasSize(4);

        String reassignment = procedure.substring(
                procedure.indexOf("DROP TEMPORARY TABLE IF EXISTS tmp_sign_hr_reassignment_task_20260716"),
                procedure.indexOf("INSERT INTO sys_sign_hr_state", procedure.indexOf(
                        "DROP TEMPORARY TABLE IF EXISTS tmp_sign_hr_reassignment_task_20260716")));
        assertThat(reassignment)
                .contains(
                        "CREATE TEMPORARY TABLE tmp_sign_hr_reassignment_task_20260716",
                        "INSERT IGNORE INTO tmp_sign_hr_reassignment_task_20260716",
                        "status NOT IN ('SIGNED', 'REFUSED', 'EXPIRED', 'CANCELLED', 'NO_ACTION')",
                        "status IN ('REFUSED', 'EXPIRED') AND resolution_status = 'OPEN'",
                        "INSERT INTO oa_sign_task_hr_reassignment",
                        "UPDATE oa_sign_notification_outbox notification",
                        "'$.routeType')) = 'OA_SIGN_HR_TASK'",
                        "notification.recipient_user_id <> current_hr_user_id",
                        "notification.status IN ('PENDING', 'SENDING', 'RETRY', 'DEAD')",
                        "notification.last_result = 'HR_REASSIGNED'",
                        "notification.recipient_user_id = current_hr_user_id",
                        "notification.status IN ('PENDING', 'RETRY', 'DEAD')",
                        "notification.payload_json = JSON_SET",
                        "INSERT IGNORE INTO oa_sign_notification_outbox",
                        "current_hr_user_id,",
                        "old_notification.status = 'DEAD'",
                        "old_notification.last_result = 'HR_REASSIGNED'",
                        "SET task.assigned_hr_user_id = current_hr_user_id",
                        "task.version = task.version + 1")
                .doesNotContain("'OA_SIGN_PACKAGE_SIGN'", "notification.status IN ('SENT'",
                        "old_notification.status = 'SENT'");
        assertThat(reassignment.indexOf("UPDATE oa_sign_notification_outbox notification"))
                .isLessThan(reassignment.indexOf("INSERT IGNORE INTO oa_sign_notification_outbox"));
        assertThat(reassignment.indexOf("INSERT IGNORE INTO oa_sign_notification_outbox"))
                .isLessThan(reassignment.indexOf("UPDATE oa_sign_task task"));
        assertThat(sql)
                .contains("START TRANSACTION;\nCALL sync_sign_hr_permissions_with_offboarding();\nCOMMIT;");

        String transferProcedure = sql.substring(
                sql.indexOf("CREATE PROCEDURE sync_sign_hr_permissions_with_transfer()"),
                sql.indexOf("DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_offboarding"));
        String offboardingProcedure = sql.substring(
                sql.indexOf("CREATE PROCEDURE sync_sign_hr_permissions_with_offboarding()"));
        assertThat(transferProcedure)
                .contains("SELECT COUNT(*), MIN(menu_id)",
                        "IF transfer_menu_count <> 1 THEN", "SIGNAL SQLSTATE '45000'");
        assertThat(offboardingProcedure)
                .contains("SELECT COUNT(*), MIN(menu_id)",
                        "IF offboard_menu_count <> 1 THEN", "SIGNAL SQLSTATE '45000'");
    }

    @Test
    @DisplayName("领域对象公开编排和幂等所需字段")
    void shouldExposeTaskCenterApplicationModel() throws Exception
    {
        assertProperties("com.erp.oa.domain.OaSignTask",
                "taskId", "taskNo", "scenario", "employeeId", "shopDeptId", "legalEntityId",
                "assignedHrUserId", "sourceType", "sourceBusinessId", "sourceEventVersion", "dedupeKey",
                "status", "automationLevel", "riskLevel", "planVersionId", "packageId", "confirmedBy",
                "confirmedTime", "confirmedSnapshotHash", "signDeadline", "failureCode", "failureDetail",
                "retryCount", "nextRetryTime", "version", "createdTime", "sentTime", "completedTime",
                "cancelledTime");
        assertProperties("com.erp.oa.domain.OaSignTaskEvent",
                "eventId", "taskId", "fromStatus", "toStatus", "operatorType", "operatorUserId",
                "reasonCode", "reasonDetail", "requestId", "ipAddress", "userAgent", "prevEventHash",
                "eventHash", "createdTime");
        assertProperties("com.erp.oa.domain.OaSignNotificationOutbox",
                "outboxId", "channel", "recipientUserId", "businessKey", "payloadJson", "status",
                "retryCount", "nextRetryTime", "lastResult", "lastError", "version", "createdTime",
                "updatedTime");
        assertProperties("com.erp.oa.domain.OaSignPackage", "taskId", "confirmStatus", "planVersionId");
    }

    private void assertProperties(String className, String... expectedProperties) throws Exception
    {
        Set<String> properties = Arrays.stream(Introspector.getBeanInfo(Class.forName(className))
                        .getPropertyDescriptors())
                .map(descriptor -> descriptor.getName())
                .collect(Collectors.toSet());
        assertThat(properties).contains(expectedProperties);
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
