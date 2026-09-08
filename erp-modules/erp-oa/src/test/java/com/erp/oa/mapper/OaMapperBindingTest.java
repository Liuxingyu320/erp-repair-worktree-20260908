package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.oa.domain.OaPurchase;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.OaSignTemplate;

@DisplayName("OA Mapper 绑定")
class OaMapperBindingTest
{
    @Test
    @DisplayName("采购更新必须使用业务行版本并递增版本")
    void shouldUseOptimisticLockForPurchaseUpdates() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias("OaPurchase", OaPurchase.class);
        parseMapper(configuration, "mapper/oa/OaPurchaseMapper.xml");

        OaPurchase update = new OaPurchase();
        update.setPurchaseId(900L);
        update.setStatus("pending");
        update.setRowVersion(3L);
        BoundSql boundSql = configuration.getMappedStatement(
                OaPurchaseMapper.class.getName() + ".updateOaPurchase")
                .getBoundSql(update);
        assertThat(normalizedSql(boundSql.getSql()))
                .contains("status = ?", "row_version = row_version + 1",
                        "where purchase_id = ? and row_version = ?");
        assertThat(boundSql.getParameterMappings())
                .extracting(mapping -> mapping.getProperty())
                .contains("purchaseId", "rowVersion");
    }

    @Test
    @DisplayName("OA门店范围SQL支持授权上级公司继承下级门店")
    void shouldResolveOaShopScopeFromAuthorizedAncestor() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, "mapper/oa/OaDeptScopeMapper.xml");

        String scopeMapperXml = resourceText("mapper/oa/OaDeptScopeMapper.xml");

        assertMapped(configuration, OaDeptScopeMapper.class, "countUserShopScope");
        assertMapped(configuration, OaDeptScopeMapper.class, "countActiveStoreDept");
        assertMapped(configuration, OaDeptScopeMapper.class, "selectDeptName");
        assertThat(scopeMapperXml)
                .contains(
                        "inner join sys_dept scope_dept on scope_dept.dept_id = us.dept_id",
                        "inner join sys_dept target_dept on target_dept.dept_id = #{deptId}",
                        "scope_dept.dept_type in ('GROUP', 'COMPANY', 'STORE', 'WAREHOUSE')",
                        "target_dept.dept_type = 'STORE'",
                        "find_in_set(scope_dept.dept_id, target_dept.ancestors)",
                        "select dept_name",
                        "where dept_id = #{deptId}")
                .doesNotContain("and us.dept_id = #{deptId}");
    }

    @Test
    @DisplayName("员工签约方案Mapper声明完整")
    void shouldBindSignPlanMappers() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, "mapper/oa/OaSignPlanMapper.xml");
        parseMapper(configuration, "mapper/oa/OaSignPackageMapper.xml");

        String planMapperXml = resourceText("mapper/oa/OaSignPlanMapper.xml");
        String packageMapperXml = resourceText("mapper/oa/OaSignPackageMapper.xml");
        String planMigrationSql = fileText("../../sql/erp_oa_sign_plan_20260706.sql");
        String renewalSnapshotMigration = fileText(
                "../../sql/erp_oa_sign_package_renewal_snapshot_20260714.sql");
        String scenarioTemplateMigration = fileText(
                "../../sql/erp_oa_sign_template_scenarios_20260714.sql");

        assertMapped(configuration, OaSignPlanMapper.class, "insertOaSignPlan");
        assertMapped(configuration, OaSignPlanMapper.class, "updateOaSignPlan");
        assertMapped(configuration, OaSignPlanMapper.class, "selectOaSignPlanById");
        assertMapped(configuration, OaSignPlanMapper.class, "selectOaSignPlanList");
        assertMapped(configuration, OaSignPlanMapper.class, "deletePlanTemplatesByPlanId");
        assertMapped(configuration, OaSignPlanMapper.class, "batchInsertPlanTemplates");
        assertMapped(configuration, OaSignPlanMapper.class, "selectPlanTemplatesByPlanId");
        assertMapped(configuration, OaSignPlanMapper.class, "selectActiveTemplatesByPlanId");
        assertThat(planMapperXml)
                .contains("and shop_dept_id = 0")
                .doesNotContain("params.scopeDeptIds");
        assertMapped(configuration, OaSignPackageMapper.class, "selectOpenPackageByEmployeeAndPlan");
        assertMapped(configuration, OaSignPackageMapper.class, "selectOpenPackageByEmployeeAndPlanForUpdate");
        assertMapped(configuration, OaSignPackageMapper.class,
                "selectLatestOnboardPackageFactsByEmployeeIds");
        assertMapped(configuration, OaSignPackageMapper.class, "updateDraftOaSignPackage");
        assertThat(configuration.getMappedStatement(OaSignPackageMapper.class.getName()
                + ".selectLatestOnboardPackageFactsByEmployeeIds").getResultMaps().get(0)
                .getResultMappings())
                .extracting(org.apache.ibatis.mapping.ResultMapping::getProperty)
                .contains("employeeId", "latestPackageId", "latestTaskId", "latestStatus",
                        "latestShopDeptId", "signedPackageId", "signedTaskId",
                        "signedShopDeptId", "signedEvidenceComplete");
        String draftUpdateSql = mappedSql(packageMapperXml, "updateDraftOaSignPackage");
        String openPackageSql = mappedSelectSql(packageMapperXml, "selectOpenPackageByEmployeeAndPlan");
        String openPackageForUpdateSql = mappedSelectSql(packageMapperXml, "selectOpenPackageByEmployeeAndPlanForUpdate");
        String packageByIdSql = normalizedSql(configuration.getMappedStatement(
                OaSignPackageMapper.class.getName() + ".selectOaSignPackageById")
                .getBoundSql(1L).getSql());
        assertThat(packageMapperXml)
                .contains(
                        "source_plan_id",
                        "source_plan_name",
                        "property=\"legalEntityIdSnapshot\" column=\"legal_entity_id_snapshot\"",
                        "property=\"legalEntityNameSnapshot\" column=\"legal_entity_name_snapshot\"",
                        "property=\"previousContractEndDate\" column=\"previous_contract_end_date\"",
                        "property=\"previousRenewalCount\" column=\"previous_renewal_count\"",
                        "property=\"renewalCount\" column=\"renewal_count\"",
                        "and source_plan_id = #{sourcePlanId}",
                        "owner_task.package_id = oa_sign_package.package_id",
                        "owner_task.assigned_hr_user_id = #{params.assignedHrUserId}");
        assertThat(openPackageSql).contains("and shop_dept_id = #{shopDeptId}");
        assertThat(openPackageForUpdateSql).contains("and shop_dept_id = #{shopDeptId}");
        assertThat(packageByIdSql).contains(
                "student_status_snapshot", "retirement_status_snapshot",
                "income_start_year_month");
        assertThat(draftUpdateSql)
                .contains(
                        "employee_address_snapshot = #{employeeAddressSnapshot}",
                        "legal_entity_name_snapshot = #{legalEntityNameSnapshot}",
                        "previous_contract_end_date = #{previousContractEndDate}",
                        "previous_renewal_count = #{previousRenewalCount}",
                        "renewal_count = #{renewalCount}",
                        "probation_start_date = #{probationStartDate}",
                        "actual_regularization_date = #{actualRegularizationDate}",
                        "base_salary = #{baseSalary}",
                        "document_version = null",
                        "remark = #{remark}",
                        "where package_id = #{packageId}",
                        "and status = 'draft'",
                        "t.package_id = oa_sign_package.package_id",
                        "oa_sign_package.task_id = t.task_id",
                        "t.assigned_hr_user_id = #{params.assignedHrUserId}",
                        "t.status in ('NEW', 'NEEDS_DATA', 'FAILED')",
                        "t.confirmed_by is null",
                        "t.confirmed_time is null",
                        "t.confirmed_snapshot_hash is null",
                        "upper(t.scenario) != 'REGULARIZE'",
                        "or oa_sign_package.actual_regularization_date",
                        "&lt;=> #{actualRegularizationDate}")
                .doesNotContain("<if");
        assertThat(planMigrationSql)
                .contains(
                        "TABLE_NAME = 'oa_sign_package'",
                        "idx_oa_sign_package_employee_plan_status",
                        "employee_id, source_plan_id, status, package_id");
        assertThat(renewalSnapshotMigration)
                .contains(
                        "legal_entity_id_snapshot", "legal_entity_code_snapshot",
                        "legal_entity_name_snapshot", "previous_contract_end_date",
                        "previous_employment_type", "previous_legal_entity_id_snapshot",
                        "previous_renewal_count", "renewal_count",
                        "information_schema.COLUMNS", "'DO 0'");
        assertThat(fileText(
                "../../docker/mysql/db/erp_oa_sign_package_renewal_snapshot_20260714.sql"))
                .isEqualTo(renewalSnapshotMigration);
        assertThat(scenarioTemplateMigration)
                .contains(
                        "tmp_oa_sign_template_scenarios_20260714",
                        "20260714-v3-draft", "20260714-draft",
                        "ONBOARD_LABOR_CONTRACT", "REGULARIZE_CONFIRMATION",
                        "TRANSFER_CONFIRMATION", "RENEWAL_LABOR_CONTRACT",
                        "OFFBOARD_LEAVE_CERTIFICATE",
                        "t.status = '1'", "v.sort_order, '1', 'system'",
                        "CAST(t.file_url AS BINARY) = CAST(v.file_url AS BINARY)")
                .doesNotContain("SET t.status = '0'", "v.sort_order, '0', 'system'");
        assertThat(fileText(
                "../../docker/mysql/db/erp_oa_sign_template_scenarios_20260714.sql"))
                .isEqualTo(scenarioTemplateMigration);
        assertThat(openPackageForUpdateSql)
                .contains(
                        "and source_plan_id = #{sourcePlanId}",
                        "status in ('draft', 'pending_sign', 'part_viewed', 'pending_company', 'pending_final_confirm')",
                        "for update");
    }

    @Test
    @DisplayName("签约文件证据Mapper和新增字段绑定完整")
    void shouldBindImmutableSignEvidenceMappers() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, "mapper/oa/OaSignFileEvidenceMapper.xml");

        assertMapped(configuration, OaSignFileEvidenceMapper.class, "insertOaSignFileEvidence");
        assertMapped(configuration, OaSignFileEvidenceMapper.class, "selectEvidenceByPackageId");
        assertMapped(configuration, OaSignFileEvidenceMapper.class, "selectEvidenceByDocumentVersion");
        assertMapped(configuration, OaSignFileEvidenceMapper.class,
                "deleteEvidenceByPackageIdAndDocumentVersion");
        assertThat(resourceText("mapper/oa/OaSignFileEvidenceMapper.xml"))
                .contains("<delete id=\"deleteEvidenceByPackageId\">");
        assertThat(resourceText("mapper/oa/OaSignFileEvidenceMapper.xml"))
                .contains("delete from oa_sign_file_evidence",
                        "where package_id = #{packageId}",
                        "and document_version = #{documentVersion}");

        assertThat(resourceText("mapper/oa/OaSignPackageMapper.xml"))
                .contains("property=\"documentVersion\" column=\"document_version\"")
                .contains("property=\"signDeadline\" column=\"sign_deadline\"")
                .contains("property=\"version\" column=\"version\"")
                .contains("id=\"updateStatusWithVersion\"",
                        "sign_deadline &gt; #{transitionTime}",
                        "sign_deadline &gt; #{confirmedTime}",
                        "deadline_days_snapshot &gt; 0");
        assertThat(resourceText("mapper/oa/OaSignPackageDocumentMapper.xml"))
                .contains("property=\"reviewPdfUrl\" column=\"review_pdf_url\"")
                .contains("property=\"reviewPdfHash\" column=\"review_pdf_hash\"")
                .contains("property=\"signedPdfUrl\" column=\"signed_pdf_url\"")
                .contains("property=\"signedPdfHash\" column=\"signed_pdf_hash\"")
                .contains("property=\"signatureHash\" column=\"signature_hash\"")
                .contains("property=\"certificateHash\" column=\"certificate_hash\"")
                .contains("property=\"documentVersion\" column=\"document_version\"")
                .contains("id=\"selectRequiredDocumentsByPackageId\"");
        assertThat(resourceText("mapper/oa/OaSignEventMapper.xml"))
                .contains("property=\"requestId\" column=\"request_id\"")
                .contains("id=\"selectEventByTypeAndRequestId\"");
    }

    @Test
    @DisplayName("单HR签约任务中心Mapper绑定完整")
    void shouldBindSignTaskCenterMappers() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, "mapper/oa/OaSignTaskMapper.xml");
        parseMapper(configuration, "mapper/oa/OaSignTaskEventMapper.xml");
        parseMapper(configuration, "mapper/oa/OaSignNotificationOutboxMapper.xml");

        assertMapped(configuration, "com.erp.oa.mapper.OaSignTaskMapper", "insertOaSignTask");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignTaskMapper", "selectOaSignTaskById");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignTaskMapper", "selectOaSignTaskByDedupeKey");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignTaskMapper",
                "selectCanonicalTaskBySourceEvent");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignTaskMapper",
                "selectExactTasksBySourceEvent");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignTaskMapper",
                "selectOaSignTasksBySourceEvents");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignTaskMapper", "selectOaSignTaskList");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignTaskMapper",
                "resumeEmployeeDeadlineAfterCompanyStage");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignTaskEventMapper", "insertOaSignTaskEvent");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignTaskEventMapper", "selectTaskEvents");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignTaskEventMapper", "selectEventByRequestId");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "insertOaSignNotificationOutbox");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "selectByBusinessKey");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "selectDueNotifications");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "claimForSending");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "markSent");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "markRetry");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "markDead");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "selectDeadNotificationsForHr");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "selectNotificationFailuresForHr");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "selectNotificationsForTaskBusinessKey");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "selectLatestNotificationForTask");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "lockCurrentReminderCandidate");
        assertMapped(configuration, "com.erp.oa.mapper.OaSignNotificationOutboxMapper",
                "requeueDead");

        String taskMapperXml = resourceText("mapper/oa/OaSignTaskMapper.xml");
        assertThat(taskMapperXml)
                .contains("property=\"assignedHrUserId\" column=\"assigned_hr_user_id\"")
                .contains("property=\"sourceEventVersion\" column=\"source_event_version\"")
                .contains("property=\"confirmedSnapshotHash\" column=\"confirmed_snapshot_hash\"")
                .contains("property=\"version\" column=\"version\"");
        assertThat(mappedSql(taskMapperXml, "resumeEmployeeDeadlineAfterCompanyStage"))
                .contains(
                        "status = #{expectedStatus}",
                        "#{expectedStatus} = 'PENDING_COMPANY'",
                        "version = #{version}",
                        "assigned_hr_user_id = #{assignedHrUserId}",
                        "sign_deadline = #{currentDeadline}",
                        "#{signDeadline} &gt; #{currentDeadline}",
                        "deadline_policy_source is not null",
                        "deadline_days_snapshot &gt; 0");
        assertThat(mappedSelectSql(taskMapperXml, "selectCanonicalTaskBySourceEvent"))
                .contains(
                        "scenario = #{scenario}",
                        "employee_id = #{employeeId}",
                        "source_type = #{sourceType}",
                        "source_business_id = #{sourceBusinessId}",
                        "source_event_version = #{sourceEventVersion}",
                        "order by case",
                        "when package_id is not null and plan_version_id is not null then 0",
                        "else 1",
                        "end asc, task_id asc",
                        "limit 1")
                .doesNotContain("dedupe_key");
        assertThat(mappedSelectSql(taskMapperXml, "selectExactTasksBySourceEvent"))
                .contains(
                        "cast(upper(scenario) as binary)",
                        "cast(upper(#{scenario}) as binary)",
                        "employee_id = #{employeeId}",
                        "cast(upper(source_type) as binary)",
                        "cast(source_business_id as binary)",
                        "cast(source_event_version as binary)",
                        "end asc, task_id asc",
                        "limit 2")
                .doesNotContain("trim(");
        assertThat(mappedSelectSql(taskMapperXml, "selectOaSignTasksBySourceEvents"))
                .contains(
                        "collection=\"sourceEvents\"",
                        "upper(trim(scenario)) = upper(trim(#{source.scenario}))",
                        "employee_id = #{source.employeeId}",
                        "upper(trim(source_type)) = upper(trim(#{source.sourceType}))",
                        "cast(trim(source_business_id) as binary)",
                        "cast(trim(#{source.sourceBusinessId}) as binary)",
                        "cast(trim(source_event_version) as binary)",
                        "cast(trim(#{source.sourceEventVersion}) as binary)",
                        "when package_id is not null and plan_version_id is not null then 0",
                        "end asc, task_id asc");
        assertThat(mappedSql(taskMapperXml, "updatePackageLink"))
                .contains(
                        "from oa_sign_package p",
                        "p.package_id = #{packageId}",
                        "p.task_id = oa_sign_task.task_id",
                        "assigned_hr_user_id = #{assignedHrUserId}",
                        "status in ('NEW', 'NEEDS_DATA', 'FAILED')",
                        "confirmed_by is null",
                        "confirmed_time is null",
                        "confirmed_snapshot_hash is null");
        assertThat(resourceText("mapper/oa/OaSignTaskEventMapper.xml"))
                .contains("property=\"requestId\" column=\"request_id\"")
                .contains("property=\"prevEventHash\" column=\"prev_event_hash\"")
                .contains("property=\"eventHash\" column=\"event_hash\"");
        assertThat(resourceText("mapper/oa/OaSignNotificationOutboxMapper.xml"))
                .contains("property=\"businessKey\" column=\"business_key\"")
                .contains("property=\"recipientUserId\" column=\"recipient_user_id\"")
                .contains("property=\"version\" column=\"version\"")
                .contains("insert ignore into oa_sign_notification_outbox")
                .contains("status = 'SENDING'")
                .contains("version = version + 1")
                .contains("updated_time &lt;= #{staleSendingBefore}")
                .contains("<foreach collection=\"scopeDeptIds\"")
                .contains("last_result = 'MANUAL_REQUEUE'");
        BoundSql dueNotifications = configuration.getMappedStatement(
                OaSignNotificationOutboxMapper.class.getName() + ".selectDueNotifications")
                .getBoundSql(java.util.Map.of(
                        "dueTime", new java.util.Date(),
                        "staleSendingBefore", new java.util.Date(),
                        "limit", 100));
        assertThat(normalizedSql(dueNotifications.getSql()))
                .contains(
                        "from oa_sign_notification_outbox o inner join oa_sign_task t",
                        "on t.task_id = cast(json_unquote(json_extract(o.payload_json, '$.taskId')) as unsigned)",
                        "o.status in ('PENDING', 'RETRY')",
                        "o.status = 'SENDING'",
                        "json_unquote(json_extract(o.payload_json, '$.routeType')) = 'OA_SIGN_HR_TASK'",
                        "o.recipient_user_id = t.assigned_hr_user_id",
                        "json_unquote(json_extract(o.payload_json, '$.routeType')) = 'OA_SIGN_PACKAGE_SIGN'",
                        "o.recipient_user_id = t.employee_id");
        BoundSql claimForSending = configuration.getMappedStatement(
                OaSignNotificationOutboxMapper.class.getName() + ".claimForSending")
                .getBoundSql(java.util.Map.of(
                        "outboxId", 8L,
                        "expectedStatus", "PENDING",
                        "version", 3L));
        assertThat(normalizedSql(claimForSending.getSql()))
                .contains(
                        "update oa_sign_notification_outbox o inner join oa_sign_task t",
                        "o.outbox_id = ?",
                        "o.status = ?",
                        "o.version = ?",
                        "o.recipient_user_id = t.assigned_hr_user_id",
                        "o.recipient_user_id = t.employee_id");
        BoundSql latestForTask = configuration.getMappedStatement(
                OaSignNotificationOutboxMapper.class.getName()
                        + ".selectLatestNotificationForTask")
                .getBoundSql(java.util.Map.of("taskId", 9L));
        assertThat(normalizedSql(latestForTask.getSql()))
                .contains(
                        "from oa_sign_notification_outbox o inner join oa_sign_task t",
                        "on t.task_id = cast(json_unquote(json_extract(o.payload_json, '$.taskId')) as unsigned)",
                        "and t.task_id = ?",
                        "o.recipient_user_id = t.assigned_hr_user_id",
                        "o.recipient_user_id = t.employee_id",
                        "order by o.updated_time desc, o.outbox_id desc",
                        "limit 1");
        com.erp.oa.domain.vo.OaSignReminderCandidate reminderCandidate =
                new com.erp.oa.domain.vo.OaSignReminderCandidate();
        reminderCandidate.setPackageId(90L);
        reminderCandidate.setTaskId(9L);
        reminderCandidate.setEmployeeId(201L);
        reminderCandidate.setAssignedHrUserId(101L);
        reminderCandidate.setShopDeptId(1171L);
        reminderCandidate.setPackageStatus("pending_sign");
        reminderCandidate.setReminderStage("INITIAL");
        reminderCandidate.setActiveDocumentVersion("SP-90-V1");
        reminderCandidate.setSignDeadline(new java.util.Date());
        BoundSql reminderLock = configuration.getMappedStatement(
                OaSignNotificationOutboxMapper.class.getName()
                        + ".lockCurrentReminderCandidate")
                .getBoundSql(java.util.Map.of(
                        "candidate", reminderCandidate,
                        "enqueueTime", new java.util.Date()));
        assertThat(normalizedSql(reminderLock.getSql()))
                .contains(
                        "p.package_id = ?",
                        "p.status = ?",
                        "p.sign_deadline = ?",
                        "t.sign_deadline = p.sign_deadline",
                        "p.sign_deadline > ?",
                        "BINARY p.document_version = BINARY ?",
                        "BINARY p.final_document_version = BINARY ?",
                        "limit 1 for update");
        BoundSql deadForHr = configuration.getMappedStatement(
                OaSignNotificationOutboxMapper.class.getName() + ".selectDeadNotificationsForHr")
                .getBoundSql(java.util.Map.of(
                        "hrUserId", 202L,
                        "scopeDeptIds", List.of(1171L),
                        "limit", 100));
        assertThat(normalizedSql(deadForHr.getSql()))
                .contains(
                        "select o.outbox_id as outbox_id",
                        "from oa_sign_notification_outbox o inner join oa_sign_task t",
                        "on t.task_id = cast(json_unquote(json_extract(o.payload_json, '$.taskId')) as unsigned)",
                        "where o.status = 'DEAD'",
                        "o.recipient_user_id = t.assigned_hr_user_id",
                        "o.recipient_user_id = t.employee_id",
                        "and t.shop_dept_id in ( ? )",
                        "order by o.updated_time desc, o.outbox_id desc",
                        "limit ?")
                .doesNotContain("$.hrUserId", "$.shopDeptId");
        BoundSql noScope = configuration.getMappedStatement(
                OaSignNotificationOutboxMapper.class.getName() + ".selectDeadNotificationsForHr")
                .getBoundSql(java.util.Map.of(
                        "hrUserId", 202L,
                        "scopeDeptIds", List.of(),
                        "limit", 100));
        assertThat(normalizedSql(noScope.getSql()))
                .contains("and 1 = 0")
                .doesNotContain("t.shop_dept_id in");
        BoundSql taskCenterFailures = configuration.getMappedStatement(
                OaSignNotificationOutboxMapper.class.getName()
                        + ".selectNotificationFailuresForHr")
                .getBoundSql(java.util.Map.of(
                        "hrUserId", 940L,
                        "scopeDeptIds", List.of(1157L, 1185L, 1186L),
                        "limit", 100));
        assertThat(normalizedSql(taskCenterFailures.getSql()))
                .contains(
                        "select t.task_id as task_id, o.business_key as notification_business_key",
                        "where dead.status = 'DEAD'",
                        "min(dead.outbox_id) as outbox_id",
                        "dead.recipient_user_id = current_task.assigned_hr_user_id",
                        "dead.recipient_user_id = current_task.employee_id",
                        "where 1 = 1",
                        "and t.shop_dept_id in ( ? , ? , ? )",
                        "limit ?")
                .doesNotContain("payload_json as", "last_error", "recipient_user_id as");
        BoundSql replayByKey = configuration.getMappedStatement(
                OaSignNotificationOutboxMapper.class.getName()
                        + ".selectNotificationsForTaskBusinessKey")
                .getBoundSql(java.util.Map.of(
                        "hrUserId", 202L,
                        "taskId", 9L,
                        "businessKey", "SIGN_SENT:90:SP-90-V1"));
        assertThat(normalizedSql(replayByKey.getSql()))
                .contains(
                        "from oa_sign_notification_outbox o inner join oa_sign_task t",
                        "on t.task_id = cast(json_unquote(json_extract(o.payload_json, '$.taskId')) as unsigned)",
                        "and t.task_id = ?",
                        "where o.business_key = ?",
                        "and t.assigned_hr_user_id = ?",
                        "o.recipient_user_id = t.assigned_hr_user_id",
                        "o.recipient_user_id = t.employee_id",
                        "order by o.outbox_id")
                .doesNotContain("$.hrUserId", "$.shopDeptId");
        BoundSql requeueDead = configuration.getMappedStatement(
                OaSignNotificationOutboxMapper.class.getName() + ".requeueDead")
                .getBoundSql(java.util.Map.of(
                        "outboxId", 8L,
                        "version", 3L,
                        "taskId", 9L,
                        "hrUserId", 202L));
        assertThat(normalizedSql(requeueDead.getSql()))
                .contains(
                        "update oa_sign_notification_outbox o inner join oa_sign_task t",
                        "and t.task_id = ?",
                        "where o.outbox_id = ?",
                        "and o.status = 'DEAD'",
                        "and o.version = ?",
                        "and t.assigned_hr_user_id = ?",
                        "o.recipient_user_id = t.assigned_hr_user_id",
                        "o.recipient_user_id = t.employee_id");
        assertThat(resourceText("mapper/oa/OaSignPackageMapper.xml"))
                .contains("property=\"taskId\" column=\"task_id\"")
                .contains("property=\"confirmStatus\" column=\"confirm_status\"")
                .contains("property=\"planVersionId\" column=\"plan_version_id\"")
                .contains("sign_deadline = #{signPackage.signDeadline}");
    }

    @Test
    @DisplayName("不可变签约方案版本Mapper与迁移绑定完整")
    void shouldBindImmutableSignPlanVersionMapper() throws Exception
    {
        Configuration configuration = new Configuration();
        parseMapper(configuration, "mapper/oa/OaSignPlanVersionMapper.xml");

        assertMapped(configuration, OaSignPlanVersionMapper.class, "lockPlanById");
        assertMapped(configuration, OaSignPlanVersionMapper.class, "lockPlanTemplateBindings");
        assertMapped(configuration, OaSignPlanVersionMapper.class, "lockTemplatesByIds");
        assertMapped(configuration, OaSignPlanVersionMapper.class, "selectByPlanIdAndVersionHash");
        assertMapped(configuration, OaSignPlanVersionMapper.class, "selectNextVersionNo");
        assertMapped(configuration, OaSignPlanVersionMapper.class, "insertPlanVersion");
        assertMapped(configuration, OaSignPlanVersionMapper.class, "batchInsertPlanVersionTemplates");
        assertMapped(configuration, OaSignPlanVersionMapper.class, "selectPlanVersionById");
        assertMapped(configuration, OaSignPlanVersionMapper.class, "lockPlanVersionById");
        assertMapped(configuration, OaSignPlanVersionMapper.class, "selectTemplatesByVersionId");
        assertMapped(configuration, OaSignPlanVersionMapper.class, "disableForNewMatching");
        assertMapped(configuration, OaSignPlanVersionMapper.class,
                "disableOtherPublishedMatchingVersions");

        String mapperXml = resourceText("mapper/oa/OaSignPlanVersionMapper.xml");
        assertThat(mappedSelectSql(mapperXml, "lockPlanById")).contains("for update");
        assertThat(mappedSelectSql(mapperXml, "lockPlanTemplateBindings"))
                .contains("oa_sign_plan_template", "order by id asc", "for update")
                .doesNotContain("oa_sign_template");
        assertThat(mappedSelectSql(mapperXml, "lockTemplatesByIds"))
                .contains("oa_sign_template", "order by template_id asc", "for update");
        assertThat(mappedSelectSql(mapperXml, "lockPlanVersionById")).contains("for update");
        assertThat(mappedSql(mapperXml, "disableOtherPublishedMatchingVersions"))
                .contains("plan_id = #{planId}", "version_id &lt;&gt; #{keepVersionId}",
                        "publish_status = 'PUBLISHED'",
                        "matching_status = 'ENABLED'", "matching_status = 'DISABLED'");
        assertThat(mapperXml)
                .contains("version_hash", "source_file_hash", "signature_position_json",
                        "company_seal_position_json", "match_condition_json")
                .doesNotContain("<update id=\"updatePlanVersion\"", "<delete");

        String migration = fileText("../../sql/erp_oa_sign_plan_version_20260711.sql");
        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS oa_sign_plan_version",
                        "CREATE TABLE IF NOT EXISTS oa_sign_plan_version_template",
                        "legal_entity_id", "legal_entity_name", "rule_json", "default_values_json",
                        "sign_deadline_days", "reminder_policy_json", "auto_send_condition_json",
                        "publish_status", "published_by_user_id", "published_time",
                        "source_file_hash", "employee_sign_required", "signature_position_json",
                        "company_seal_position_json", "match_condition_json",
                        "UNIQUE KEY uk_oa_sign_plan_version_no (plan_id, version_no)",
                        "UNIQUE KEY uk_oa_sign_plan_version_hash (plan_id, version_hash)",
                        "UNIQUE KEY uk_oa_sign_plan_version_template_type (plan_version_id, template_type)",
                        "SET @legacy_task_plan_version_max = 0",
                        "SET @legacy_package_plan_version_max = 0",
                        "MAX(plan_version_id)",
                        "GREATEST(1000000000",
                        "ALTER TABLE oa_sign_plan_version AUTO_INCREMENT = ",
                        "(4611, '签约方案配置'", "'oa:signPackage:template'",
                        "TABLE_NAME = 'sys_sign_hr_state'",
                        "TABLE_NAME = 'sys_role_menu'",
                        "TABLE_NAME = 'sys_sign_hr_menu_grant'",
                        "INSERT IGNORE INTO sys_role_menu",
                        "INSERT INTO sys_sign_hr_menu_grant",
                        "'DO 0'",
                        "DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_plan",
                        "CREATE PROCEDURE sync_sign_hr_permissions_with_plan()",
                        "CALL sync_sign_hr_permissions()")
                .doesNotContain("legal_entity_id = shop_dept_id",
                        "fk_oa_sign_task_plan_version", "fk_oa_sign_package_plan_version",
                        "DROP PROCEDURE IF EXISTS sync_sign_hr_permissions;",
                        "\nCALL sync_sign_hr_permissions_with_plan();");
        String wrapper = migration.substring(
                migration.indexOf("CREATE PROCEDURE sync_sign_hr_permissions_with_plan()"),
                migration.indexOf("DELIMITER ;",
                        migration.indexOf("CREATE PROCEDURE sync_sign_hr_permissions_with_plan()")));
        assertThat(wrapper)
                .contains("CALL sync_sign_hr_permissions()", "menu_id = 4611",
                        "INSERT IGNORE INTO sys_role_menu", "INSERT INTO sys_sign_hr_menu_grant");

        String systemConfigMapper = fileText(
                "../erp-system/src/main/resources/mapper/system/SysConfigMapper.xml");
        String transferMigration = fileText(
                "../../sql/erp_hr_transfer_effective_date_20260713.sql");
        assertThat(transferMigration)
                .contains("CREATE PROCEDURE sync_sign_hr_permissions_with_transfer()",
                        "CALL sync_sign_hr_permissions_with_plan();");
        assertThat(systemConfigMapper)
                .contains("signing grants are now managed only through ordinary roles",
                        "update sys_role", "where 1 = 0")
                .doesNotContain("{CALL sync_sign_hr_permissions_with_offboarding()}",
                        "{CALL sync_sign_hr_permissions_with_plan()}",
                        "{CALL sync_sign_hr_permissions_with_transfer()}",
                        "{CALL sync_sign_hr_permissions()}");

        assertThat(resourceText("mapper/oa/OaSignPlanMapper.xml"))
                .contains("property=\"legalEntityId\" column=\"legal_entity_id\"",
                        "property=\"legalEntityName\" column=\"legal_entity_name\"",
                        "#{legalEntityId}", "#{legalEntityName}", "#{ruleJson}",
                        "#{defaultValuesJson}", "#{signDeadlineDays}", "#{reminderPolicyJson}",
                        "#{autoSendConditionJson}");
        assertThat(resourceText("mapper/oa/OaSignTemplateMapper.xml"))
                .contains("property=\"signaturePositionJson\" column=\"signature_position_json\"",
                        "property=\"companySealPositionJson\" column=\"company_seal_position_json\"",
                        "property=\"matchConditionJson\" column=\"match_condition_json\"");
    }

    @Test
    @DisplayName("签约包生命周期迁移扩容状态并以显式截止策略失败关闭")
    void shouldBindSignPackageLifecycleMigration() throws Exception
    {
        String migration = fileText(
                "../../sql/erp_oa_sign_package_lifecycle_20260716.sql");
        String dockerMigration = fileText(
                "../../docker/mysql/db/erp_oa_sign_package_lifecycle_20260716.sql");

        assertThat(dockerMigration).isEqualTo(migration);
        assertThat(migration)
                .contains(
                        "CHARACTER_MAXIMUM_LENGTH < 32",
                        "ALTER TABLE oa_sign_package MODIFY COLUMN status varchar(32) NOT NULL DEFAULT ''draft''",
                        "ADD COLUMN deadline_policy_source varchar(32) DEFAULT NULL",
                        "ADD COLUMN deadline_days_snapshot int(11) DEFAULT NULL",
                        "ADD COLUMN terminal_time datetime DEFAULT NULL",
                        "ADD COLUMN terminal_reason_code varchar(64) DEFAULT NULL",
                        "ADD COLUMN terminal_reason_detail varchar(500) DEFAULT NULL",
                        "ADD COLUMN resolution_status varchar(20) DEFAULT NULL",
                        "ADD COLUMN resolved_by bigint(20) DEFAULT NULL",
                        "ADD COLUMN resolved_time datetime DEFAULT NULL",
                        "ADD COLUMN resolution_reason_code varchar(64) DEFAULT NULL",
                        "ADD COLUMN resolution_reason_detail varchar(500) DEFAULT NULL",
                        "ADD COLUMN reissue_of_task_id bigint(20) DEFAULT NULL",
                        "ADD COLUMN reissued_to_task_id bigint(20) DEFAULT NULL",
                        "ADD COLUMN reissue_of_package_id bigint(20) DEFAULT NULL",
                        "ADD COLUMN reissued_to_package_id bigint(20) DEFAULT NULL",
                        "UNIQUE INDEX uk_oa_sign_task_reissue_of (reissue_of_task_id)",
                        "UNIQUE INDEX uk_oa_sign_task_reissued_to (reissued_to_task_id)",
                        "UNIQUE INDEX uk_oa_sign_package_reissue_of (reissue_of_package_id)",
                        "UNIQUE INDEX uk_oa_sign_package_reissued_to (reissued_to_package_id)",
                        "INDEX idx_oa_sign_package_lifecycle_due (status, sign_deadline, package_id)",
                        "INDEX idx_oa_sign_task_lifecycle_due (status, sign_deadline, task_id)",
                        "INDEX idx_oa_sign_task_exception_resolution (assigned_hr_user_id, status, resolution_status, created_time, task_id)",
                        "INDEX idx_oa_sign_package_exception_resolution (status, resolution_status, package_id)",
                        "information_schema.COLUMNS",
                        "information_schema.STATISTICS",
                        "'DO 0'",
                        "CREATE PROCEDURE assert_oa_sign_package_lifecycle_ready_20260716()",
                        "SIGNAL SQLSTATE '45000'",
                        "AND COLUMN_NAME IN ('package_id', 'status', 'sent_time', 'sign_deadline',",
                        "'version', 'task_id', 'employee_id', 'shop_dept_id',",
                        "'plan_version_id')",
                        "IF v_count <> 9 THEN",
                        "'cancelled_time', 'employee_id', 'shop_dept_id',",
                        "IF v_count <> 12 THEN",
                        "active signing package lacks explicit deadline lifecycle policy",
                        "active signing task lacks explicit deadline lifecycle policy",
                        "active signing package must bind a managed task before lifecycle rollout",
                        "open terminal package lacks resolvable task evidence",
                        "open terminal task lacks resolvable package evidence",
                        "task/package signing deadline lifecycle policy mismatch",
                        "p.status IN ('pending_sign', 'part_viewed', 'pending_company', 'pending_final_confirm')",
                        "t.status IN ('PENDING_SIGN', 'VIEWED', 'PENDING_COMPANY', 'PENDING_FINAL_CONFIRM')",
                        "p.sign_deadline IS NULL",
                        "t.sign_deadline IS NULL",
                        "p.deadline_days_snapshot <= 0",
                        "t.deadline_days_snapshot <= 0",
                        "NOT (p.employee_id <=> t.employee_id)",
                        "NOT (p.shop_dept_id <=> t.shop_dept_id)",
                        "p.plan_version_id IS NULL",
                        "t.plan_version_id IS NULL",
                        "p.plan_version_id <> t.plan_version_id",
                        "active signing task/package binding mismatch",
                        "BINARY p.deadline_policy_source <> BINARY t.deadline_policy_source",
                        "CALL assert_oa_sign_package_lifecycle_ready_20260716()",
                        "DROP PROCEDURE IF EXISTS assert_oa_sign_package_lifecycle_ready_20260716")
                .doesNotContain(
                        "UPDATE oa_sign_package SET sign_deadline",
                        "UPDATE oa_sign_task SET sign_deadline",
                        "SET resolution_status = 'OPEN'");
        assertThat(occurrences(migration, "NOT (p.employee_id <=> t.employee_id)")).isEqualTo(4);
        assertThat(occurrences(migration, "NOT (p.shop_dept_id <=> t.shop_dept_id)")).isEqualTo(4);
        assertThat(occurrences(migration, "p.plan_version_id IS NULL")).isEqualTo(4);
        assertThat(occurrences(migration, "t.plan_version_id IS NULL")).isEqualTo(4);
        assertThat(occurrences(migration, "p.plan_version_id <> t.plan_version_id")).isEqualTo(4);
    }

    @Test
    @DisplayName("签约包文件冻结签名与印章策略且不伪造历史坐标")
    void shouldBindSignDocumentPolicySnapshot() throws Exception
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(
                "OaSignPackageDocument", OaSignPackageDocument.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "OaSignTemplate", OaSignTemplate.class);
        configuration.getTypeAliasRegistry().registerAlias(
                "OaSignPackage", OaSignPackage.class);
        parseMapper(configuration, "mapper/oa/OaSignPackageDocumentMapper.xml");
        parseMapper(configuration, "mapper/oa/OaSignTemplateMapper.xml");
        parseMapper(configuration, "mapper/oa/OaSignPlanMapper.xml");
        parseMapper(configuration, "mapper/oa/OaSignPlanVersionMapper.xml");

        assertMapped(configuration, OaSignPackageDocumentMapper.class,
                "insertOaSignPackageDocument");
        assertMapped(configuration, OaSignPackageDocumentMapper.class,
                "updateOaSignPackageDocument");
        assertMapped(configuration, OaSignPackageDocumentMapper.class,
                "selectOaSignPackageDocumentById");
        assertMapped(configuration, OaSignPackageDocumentMapper.class,
                "selectDocumentsByPackageId");

        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setSignaturePositionJson("{\"mode\":\"PLACED\"}");
        document.setCompanySealPositionJson("{\"mode\":\"PLACED\"}");
        document.setCompanySealRequired("Y");
        document.setDocumentPolicyMode("SNAPSHOT_V1");
        assertThat(document.getSignaturePositionJson()).contains("PLACED");
        assertThat(document.getCompanySealPositionJson()).contains("PLACED");
        assertThat(document.getCompanySealRequired()).isEqualTo("Y");
        assertThat(document.getDocumentPolicyMode()).isEqualTo("SNAPSHOT_V1");

        OaSignTemplate template = new OaSignTemplate();
        template.setCompanySealRequired("Y");
        OaSignPlanVersionTemplate versionTemplate = new OaSignPlanVersionTemplate();
        versionTemplate.setCompanySealRequired("N");
        assertThat(template.getCompanySealRequired()).isEqualTo("Y");
        assertThat(versionTemplate.getCompanySealRequired()).isEqualTo("N");

        String mapperXml = resourceText("mapper/oa/OaSignPackageDocumentMapper.xml");
        assertThat(mapperXml)
                .contains(
                        "property=\"signaturePositionJson\" column=\"signature_position_json\"",
                        "property=\"companySealPositionJson\" column=\"company_seal_position_json\"",
                        "property=\"companySealRequired\" column=\"company_seal_required\"",
                        "property=\"documentPolicyMode\" column=\"document_policy_mode\"",
                        "#{signaturePositionJson}",
                        "#{companySealPositionJson}",
                        "#{companySealRequired}",
                        "#{documentPolicyMode}");
        assertThat(mappedSql(mapperXml, "updateOaSignPackageDocument"))
                .doesNotContain(
                        "employee_visible",
                        "read_confirmation_required",
                        "employee_sign_required",
                        "signature_position_json",
                        "company_seal_position_json",
                        "company_seal_required",
                        "document_policy_mode");
        assertThat(resourceText("mapper/oa/OaSignTemplateMapper.xml"))
                .contains(
                        "property=\"companySealRequired\" column=\"company_seal_required\"",
                        "#{companySealRequired}",
                        "company_seal_required = #{companySealRequired}");
        assertThat(resourceText("mapper/oa/OaSignPlanMapper.xml"))
                .contains(
                        "property=\"companySealRequired\" column=\"company_seal_required\"",
                        "property=\"companySealRequired\" column=\"t_company_seal_required\"",
                        "t.company_seal_required as t_company_seal_required",
                        "t.company_seal_required");
        assertThat(resourceText("mapper/oa/OaSignPlanVersionMapper.xml"))
                .contains(
                        "property=\"companySealRequired\" column=\"company_seal_required\"",
                        "#{item.companySealRequired}",
                        "company_seal_required");

        String migration = fileText(
                "../../sql/erp_oa_sign_document_policy_snapshot_20260716.sql");
        String dockerMigration = fileText(
                "../../docker/mysql/db/erp_oa_sign_document_policy_snapshot_20260716.sql");
        assertThat(dockerMigration).isEqualTo(migration);
        assertThat(migration)
                .contains(
                        "ADD COLUMN signature_position_json longtext DEFAULT NULL",
                        "ADD COLUMN company_seal_position_json longtext DEFAULT NULL",
                        "ADD COLUMN company_seal_required char(1) DEFAULT NULL",
                        "oa_sign_template",
                        "oa_sign_plan_version_template",
                        "trg_oa_sign_template_policy_bi_20260716",
                        "trg_oa_sign_template_policy_bu_20260716",
                        "trg_oa_sign_plan_version_template_policy_bi_20260716",
                        "trg_oa_sign_plan_version_template_policy_bu_20260716",
                        "trg_oa_sign_package_document_policy_bi_20260716",
                        "trg_oa_sign_package_document_policy_bu_20260716",
                        "information_schema.TRIGGERS",
                        "JSON_VALID",
                        "ADD COLUMN document_policy_mode varchar(32) DEFAULT NULL",
                        "LEGACY_APPEND_ONLY",
                        "SNAPSHOT_V1",
                        "MODIFY COLUMN document_policy_mode varchar(32) NOT NULL",
                        "SIGNAL SQLSTATE '45000'",
                        "BINARY NEW.employee_visible <=> BINARY OLD.employee_visible",
                        "BINARY NEW.employee_sign_required <=> BINARY OLD.employee_sign_required",
                        "BINARY NEW.company_seal_required <=> BINARY OLD.company_seal_required",
                        "BINARY NEW.document_policy_mode <=> BINARY OLD.document_policy_mode",
                        "immutable signing document policy snapshot",
                        "historical signing document policy contains fabricated snapshot fields",
                        "signing document snapshot policy is incomplete")
                .doesNotContain(
                        "SET signature_position_json =",
                        "SET company_seal_position_json =",
                        "information_schema.CHECK_CONSTRAINTS",
                        "ADD CONSTRAINT chk_oa_sign_",
                        "UPDATE oa_sign_template",
                        "UPDATE oa_sign_plan_version_template",
                        "JOIN oa_sign_template",
                        "JOIN oa_sign_plan_version_template");
    }

    @Test
    @DisplayName("签约菜单修复迁移覆盖历史权限包装过程")
    void shouldBindForwardSigningMenuPermissionRepair() throws Exception
    {
        String migration = fileText(
                "../../sql/erp_oa_sign_menu_permission_repair_20260716.sql");
        String dockerMigration = fileText(
                "../../docker/mysql/db/erp_oa_sign_menu_permission_repair_20260716.sql");

        assertThat(dockerMigration).isEqualTo(migration);
        assertThat(migration)
                .contains("menu_id BETWEEN 9650 AND 9669",
                        "CREATE PROCEDURE sync_sign_hr_permissions()",
                        "CREATE PROCEDURE sync_sign_hr_permissions_with_plan()",
                        "CREATE PROCEDURE sync_sign_hr_permissions_with_transfer()",
                        "CREATE PROCEDURE sync_sign_hr_permissions_with_offboarding()",
                        "CALL sync_sign_hr_permissions_with_offboarding()",
                        "'oa:signPackage:send'", "'oa:signPackage:void'",
                        "'oa:signCompany:list'", "'oa:signSeal:list'",
                        "old_menu.menu_id = 4603",
                        "BINARY old_menu.perms = BINARY 'oa:signTask:confirm'",
                        "query = VALUES(query)",
                        "is_frame = VALUES(is_frame)",
                        "is_cache = VALUES(is_cache)");

        String baseProcedure = migration.substring(
                migration.indexOf("CREATE PROCEDURE sync_sign_hr_permissions()"),
                migration.indexOf("DROP PROCEDURE IF EXISTS sync_sign_hr_permissions_with_plan"));
        assertThat(baseProcedure).doesNotContain("'oa:signTask:confirm'");

        String systemConfigMapper = fileText(
                "../erp-system/src/main/resources/mapper/system/SysConfigMapper.xml");
        assertThat(systemConfigMapper)
                .contains("signing grants are now managed only through ordinary roles",
                        "update sys_role", "where 1 = 0")
                .doesNotContain("{CALL sync_sign_hr_permissions_with_offboarding()}",
                        "{CALL sync_sign_hr_permissions()}");
    }

    private static void parseMapper(Configuration configuration, String resource) throws Exception
    {
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource,
                    configuration.getSqlFragments());
            mapperParser.parse();
        }
    }

    private static String normalizedSql(String sql)
    {
        return sql.replaceAll("\\s+", " ").trim();
    }

    private static String resourceText(String resource) throws Exception
    {
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String fileText(String path) throws Exception
    {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
    }

    private static int occurrences(String text, String fragment)
    {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    private static String mappedSql(String mapperXml, String statementId)
    {
        String start = "<update id=\"" + statementId + "\"";
        int startIndex = mapperXml.indexOf(start);
        assertThat(startIndex).as("mapped sql %s exists", statementId).isGreaterThanOrEqualTo(0);
        int endIndex = mapperXml.indexOf("</update>", startIndex);
        assertThat(endIndex).as("mapped sql %s end exists", statementId).isGreaterThan(startIndex);
        return mapperXml.substring(startIndex, endIndex);
    }

    private static String mappedSelectSql(String mapperXml, String statementId)
    {
        String start = "<select id=\"" + statementId + "\"";
        int startIndex = mapperXml.indexOf(start);
        assertThat(startIndex).as("mapped sql %s exists", statementId).isGreaterThanOrEqualTo(0);
        int endIndex = mapperXml.indexOf("</select>", startIndex);
        assertThat(endIndex).as("mapped sql %s end exists", statementId).isGreaterThan(startIndex);
        return mapperXml.substring(startIndex, endIndex);
    }

    private static void assertMapped(Configuration configuration, Class<?> mapperType, String statementId)
    {
        assertThat(configuration.hasStatement(mapperType.getName() + "." + statementId))
                .as("mapped statement %s.%s", mapperType.getName(), statementId)
                .isTrue();
    }

    private static void assertMapped(Configuration configuration, String mapperType, String statementId)
    {
        assertThat(configuration.hasStatement(mapperType + "." + statementId))
                .as("mapped statement %s.%s", mapperType, statementId)
                .isTrue();
    }
}
