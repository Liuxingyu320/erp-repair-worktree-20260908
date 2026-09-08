package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.oa.domain.OaSignOnboardImportRow;

class OaSignOnboardImportMapperBindingTest
{
    @Test
    void firstClaimFreezesTheCurrentFactVersionAndRetriesKeepIt() throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportRowMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.claimGeneration")
                .getBoundSql(Map.of("rowId", 9L, "requestId", "req-1", "expectedVersion", 7L));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("source_event_version = coalesce(source_event_version, version)")
                .contains("version = version + 1")
                .doesNotContain("source_event_version = coalesce(source_event_version, version + 1)");
    }

    @Test
    void migratedTaskOnlyBindingCanBeClaimedWithoutDroppingItsCanonicalTask()
            throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportRowMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.claimTaskOnlyGeneration")
                .getBoundSql(Map.of("rowId", 1876L, "taskId", 198L,
                        "requestId", "repair-957", "expectedVersion", 7L));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("task_id = ? and package_id is null")
                .contains("status in ('READY_TO_GENERATE', 'GENERATE_FAILED')")
                .contains("generation_request_id = ?")
                .contains("source_event_version = coalesce(source_event_version, version)")
                .contains("version = version + 1");
    }

    @Test
    void companyWorkRequestBindingUsesVersionDecisionAndReadyStateCompareAndSet()
            throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportRowMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.bindCompanyWorkGenerationRequest")
                .getBoundSql(Map.of("rowId", 9L, "requestId", "OCW:request",
                        "legalEntityId", 4L, "sealId", 8L, "expectedVersion", 7L));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("generation_request_id = ?", "version = version + 1")
                .contains("row_id = ? and version = ?")
                .contains("task_id is null and package_id is null")
                .contains("status = 'READY_TO_GENERATE'")
                .contains("matched_legal_entity_id = ?")
                .contains("recommended_seal_id = ?");
    }

    @Test
    void stagedPackageLinkClaimsAllThreeReferencesAtomically() throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportRowMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.linkStagedPackage")
                .getBoundSql(Map.of("rowId", 9L, "dataRequestId", 19L,
                        "taskId", 29L, "packageId", 39L,
                        "sourceEventVersion", 7L,
                        "status", "WAITING_EMPLOYEE_DATA", "expectedVersion", 7L));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("data_request_id = ?", "task_id = ?", "package_id = ?")
                .contains("source_event_version = coalesce(source_event_version, ?)")
                .contains("row_id = ? and version = ?")
                .contains("(data_request_id is null or data_request_id = ?)")
                .contains("(source_event_version is null or source_event_version = ?)")
                .contains("task_id is null and package_id is null")
                .contains("status not in ('GENERATING', 'GENERATED', 'SENT', 'PARTIAL_SENT')")
                .contains("version = version + 1");
    }

    @Test
    void stagedCompanyWorkAndGenerationClaimsRequireTheExactTaskPackageBinding()
            throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportRowMapper.xml");
        BoundSql companyWork = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.bindStagedCompanyWorkGenerationRequest")
                .getBoundSql(Map.of("rowId", 9L, "taskId", 29L, "packageId", 39L,
                        "requestId", "OCW:request", "legalEntityId", 4L,
                        "sealId", 8L, "expectedVersion", 7L));
        BoundSql generation = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.claimStagedGeneration")
                .getBoundSql(Map.of("rowId", 9L, "taskId", 29L, "packageId", 39L,
                        "requestId", "generate:request", "expectedVersion", 8L));
        String companyWorkSql = companyWork.getSql().replaceAll("\\s+", " ").trim();
        String generationSql = generation.getSql().replaceAll("\\s+", " ").trim();

        assertThat(companyWorkSql)
                .contains("task_id = ? and package_id = ?")
                .contains("data_request_id is not null and status = 'READY_TO_GENERATE'")
                .contains("matched_legal_entity_id = ?", "recommended_seal_id = ?")
                .contains("version = version + 1");
        assertThat(generationSql)
                .contains("task_id = ? and package_id = ?")
                .contains("data_request_id is not null")
                .contains("status in ('READY_TO_GENERATE', 'GENERATE_FAILED')")
                .contains("source_event_version = coalesce(source_event_version, version)")
                .contains("version = version + 1");
    }

    @Test
    void stagedFirstStageRowTerminalCasRequiresCancelledRequestAndExactAuditBinding()
            throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportRowMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.terminalizeStagedFirstStage")
                .getBoundSql(Map.of("taskId", 29L, "packageId", 39L, "employeeId", 49L,
                        "terminalTaskStatus", "REFUSED",
                        "terminalPackageStatus", "refused",
                        "rowTerminalStatus", "REFUSED"));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("d.request_id = r.data_request_id")
                .contains("d.row_id = r.row_id", "d.batch_id = r.batch_id")
                .contains("d.employee_id = r.employee_id")
                .contains("t.task_id = r.task_id", "t.package_id = r.package_id")
                .contains("upper(trim(t.source_type)) = 'MANUAL_SIGN_EXCEL_IMPORT'")
                .contains("t.source_business_id = cast(r.row_id as char)")
                .contains("t.source_event_version = cast(r.source_event_version as char)")
                .contains("p.package_id = r.package_id", "p.task_id = r.task_id")
                .contains("upper(trim(p.signing_sequence)) = 'SIGNATURE_FIRST'")
                .contains("set r.status = ?")
                .contains("d.status = 'CANCELLED'")
                .contains("r.status in ( 'WAITING_EMPLOYEE_DATA', 'PENDING_HR_REVIEW', 'PROFILE_SYNC_APPLYING', 'PROFILE_SYNC_FAILED', 'READY_TO_GENERATE', 'GENERATE_FAILED' )")
                .contains("(? = 'REFUSED' and ? = 'refused' and ? = 'REFUSED')")
                .contains("(? = 'EXPIRED' and ? = 'expired' and ? = 'EXPIRED')");
    }

    @Test
    void stagedFirstStageRequestCancelCasPreservesEvidenceAndRequiresExactAuditBinding()
            throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardDataRequestMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardDataRequestMapper.cancelStagedFirstStageByTerminal")
                .getBoundSql(Map.of("taskId", 29L, "packageId", 39L, "employeeId", 49L,
                        "terminalTaskStatus", "REFUSED",
                        "terminalPackageStatus", "refused"));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("r.data_request_id = d.request_id")
                .contains("r.row_id = d.row_id", "r.batch_id = d.batch_id")
                .contains("r.employee_id = d.employee_id")
                .contains("t.task_id = r.task_id", "t.package_id = r.package_id")
                .contains("upper(trim(t.source_type)) = 'MANUAL_SIGN_EXCEL_IMPORT'")
                .contains("t.source_business_id = cast(r.row_id as char)")
                .contains("t.source_event_version = cast(r.source_event_version as char)")
                .contains("p.package_id = r.package_id", "p.task_id = r.task_id")
                .contains("upper(trim(p.signing_sequence)) = 'SIGNATURE_FIRST'")
                .contains("set d.status = 'CANCELLED'")
                .contains("d.profile_sync_status = 'CANCELLED'")
                .contains("d.status in ( 'PENDING_EMPLOYEE', 'REJECTED', 'SUBMITTED', 'APPROVED', 'PROFILE_SYNC_FAILED', 'COMPLETED' )")
                .contains("r.status in ( 'WAITING_EMPLOYEE_DATA', 'PENDING_HR_REVIEW', 'PROFILE_SYNC_APPLYING', 'PROFILE_SYNC_FAILED', 'READY_TO_GENERATE', 'GENERATE_FAILED' )")
                .contains("(? = 'REFUSED' and ? = 'refused')")
                .contains("(? = 'EXPIRED' and ? = 'expired')")
                .doesNotContain("signature_sample_bytes =", "signature_sample_hash =");
    }

    @Test
    void stagedFirstStagePreflightAcceptsOnlyPendingEmployeePackagePairs()
            throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardDataRequestMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardDataRequestMapper.countActiveStagedFirstStageLink")
                .getBoundSql(Map.of("taskId", 29L, "packageId", 39L, "employeeId", 49L,
                        "currentTaskStatus", "PENDING_SIGN",
                        "currentPackageStatus", "pending_sign"));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("upper(trim(t.source_type)) = 'MANUAL_SIGN_EXCEL_IMPORT'")
                .contains("upper(trim(p.signing_sequence)) = 'SIGNATURE_FIRST'")
                .contains("(? = 'PENDING_SIGN' and ? = 'pending_sign')")
                .contains("(? = 'VIEWED' and ? = 'part_viewed')")
                .doesNotContain("PENDING_FINAL_CONFIRM", "pending_final_confirm");
    }

    @Test
    void terminalOnboardTaskDoesNotBlockAReplacementExcelBatch()
            throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignTaskMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignTaskMapper.selectOpenOnboardTaskByEmployeeId")
                .getBoundSql(Map.of("employeeId", 49L));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("upper(trim(scenario)) = 'ONBOARD'")
                .contains("status not in ('SIGNED', 'REFUSED', 'EXPIRED', 'CANCELLED', 'NO_ACTION')");
    }

    @Test
    void generationConfirmationWriteAllowsOnlyUnboundOrFullyStagedRows()
            throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportRowMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.applyGenerationConfirmations")
                .getBoundSql(Map.of("rowId", 9L,
                        "noExternalContractConfirmed", Boolean.TRUE,
                        "historicalSupplement", Boolean.TRUE,
                        "historicalReason", "历史补签",
                        "warningConfirmed", Boolean.TRUE,
                        "warningReason", "风险已核验",
                        "expectedVersion", 7L));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("row_id = ? and version = ?")
                .contains("task_id is null or ( task_id is not null and package_id is not null and data_request_id is not null")
                .contains("status in ('READY_TO_GENERATE', 'GENERATE_FAILED')")
                .contains("status not in ('GENERATING', 'GENERATED')");
    }

    @Test
    void rePreviewDoesNotReuseAPlanOrProfileConflictSnapshot() throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportBatchMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportBatchMapper.selectReusable")
                .getBoundSql(Map.of("shopDeptId", 1L, "createdByUserId", 2L,
                        "selectionHash", "a", "fileSha256", "b", "now", new Date()));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("not exists")
                .contains("'PLAN_CHANGED_REPREVIEW'")
                .contains("'PROFILE_CHANGED_REPREVIEW'")
                .contains("'PROFILE_SYNC_FAILED'")
                .contains("b.status in ('PREVIEW_READY', 'PARTIAL_GENERATED', 'GENERATED')");
    }

    @Test
    void companyWorkQueryRequiresAssignedTaskOrUnboundLegacyEvidenceAndScope() throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportRowMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper"
                        + ".selectSignatureFirstCompanyWorkRows")
                .getBoundSql(Map.of("assignedHrUserId", 7L,
                        "scopeDeptIds", List.of(1171L, 1172L),
                        "maxRows", 100));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .doesNotContain("b.created_by_user_id = ?")
                .contains("r.task_id is null or t.assigned_hr_user_id = ?")
                .contains("b.shop_dept_id in ( ? , ? )")
                .contains("d.request_id = r.data_request_id")
                .contains("upper(trim(d.signing_sequence)) = 'SIGNATURE_FIRST'")
                .contains("d.status = 'COMPLETED'")
                .contains("d.signature_request_id is not null")
                .contains("d.signature_sample_hash is not null")
                .contains("d.signature_sample_time is not null")
                .contains("d.signature_sample_bytes is not null")
                .contains("r.task_id is null", "r.package_id is null")
                .contains("r.status not in ('GENERATING', 'GENERATED', 'SENT', 'PARTIAL_SENT')")
                .contains("limit ?");
    }

    @Test
    void generationClaimCanTakeOverAnInterruptedBatch() throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportBatchMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportBatchMapper.claimGeneration")
                .getBoundSql(Map.of("batchId", 3L, "expectedVersion", 8L,
                        "staleBefore", new Date()));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("version = ?")
                .contains("status in ('PREVIEW_READY', 'PARTIAL_GENERATED', 'GENERATED')")
                .contains("or (status = 'GENERATING' and update_time <= ?)");
    }

    @Test
    void interruptedRowRecoveryBindsOnlyTheExactReadySourceAndPackage() throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportRowMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.recoverCompletedGeneration")
                .getBoundSql(Map.of("rowId", 9L, "batchId", 3L, "taskId", 21L,
                        "packageId", 31L, "shopDeptId", 41L,
                        "generationRequestId", "owner-old", "expectedVersion", 8L));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized).contains(
                "t.employee_id = r.employee_id",
                "t.shop_dept_id = ?",
                "upper(trim(t.source_type)) = 'MANUAL_SIGN_EXCEL_IMPORT'",
                "t.source_business_id = cast(r.row_id as char)",
                "t.source_event_version = cast(r.source_event_version as char)",
                "t.status = 'READY_TO_SEND'",
                "t.plan_version_id = r.plan_version_id",
                "t.package_id = ?",
                "p.task_id = t.task_id",
                "p.employee_id = r.employee_id",
                "p.status = 'draft'",
                "r.status = 'GENERATING'",
                "r.generation_request_id = ?",
                "(r.task_id is null or r.task_id = ?)",
                "r.package_id is null");
    }

    @Test
    void failedGenerationCompareAndSetIncludesTheGenerationOwner() throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportRowMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.failGeneration")
                .getBoundSql(Map.of("rowId", 9L, "status", "GENERATE_FAILED",
                        "errorCodesJson", "[]", "generationRequestId", "owner-old",
                        "expectedVersion", 8L));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("version = ? and status = 'GENERATING'")
                .contains("generation_request_id = ?");
    }

    @Test
    void completedGenerationCompareAndSetIncludesTheGenerationOwner() throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportRowMapper.xml");
        BoundSql sql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.completeGeneration")
                .getBoundSql(Map.of("rowId", 9L, "taskId", 21L, "packageId", 31L,
                        "generationRequestId", "owner-old", "expectedVersion", 8L));
        String normalized = sql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("version = ? and status = 'GENERATING'")
                .contains("generation_request_id = ?");
    }

    @Test
    void dataRequestMapperUsesLatestRoundAndRefreshesOnlyEmployeeActionableStates() throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardDataRequestMapper.xml");
        String latest = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardDataRequestMapper.selectByRowId")
                .getBoundSql(Map.of("rowId", 71L)).getSql().replaceAll("\\s+", " ").trim();
        String mine = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardDataRequestMapper.selectMine")
                .getBoundSql(Map.of("employeeId", 81L)).getSql()
                .replaceAll("\\s+", " ").trim();
        String refresh = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardDataRequestMapper.refreshEmployeeFields")
                .getBoundSql(Map.of("requestId", 91L,
                        "allowedFieldsJson", "[\"currentAddress\"]",
                        "signingSequence", "SIGNATURE_FIRST",
                        "factSnapshotJson", "{}",
                        "confirmationSnapshotVersion", "ONBOARD_CONFIRM_V1",
                        "confirmationSnapshotHash", "a".repeat(64),
                        "profileSyncRequestId", "OPS:71:9",
                        "profileBeforeHash", "hash", "expectedVersion", 4L))
                .getSql().replaceAll("\\s+", " ").trim();
        String reopen = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardDataRequestMapper.reopenRejected")
                .getBoundSql(Map.of("requestId", 91L, "expectedVersion", 4L))
                .getSql().replaceAll("\\s+", " ").trim();
        String preservedResubmit = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardDataRequestMapper.resubmitPreservingSignature")
                .getBoundSql(Map.of("requestId", 91L, "employeeId", 81L,
                        "submittedValuesJson", "{\"currentAddress\":\"new\"}",
                        "signaturePayloadHash", "b".repeat(64),
                        "expectedSignaturePayloadHash", "a".repeat(64),
                        "targetStatus", "SUBMITTED",
                        "profileSyncStatus", "NOT_STARTED", "expectedVersion", 4L))
                .getSql().replaceAll("\\s+", " ").trim();

        assertThat(latest).contains("where row_id = ? order by request_id desc limit 1");
        assertThat(mine)
                .contains("inner join oa_sign_onboard_import_row ir",
                        "ir.data_request_id = r.request_id",
                        "ir.batch_id = r.batch_id",
                        "ir.employee_id = r.employee_id",
                        "left join oa_sign_package sp",
                        "r.status = 'COMPLETED'",
                        "upper(trim(r.signing_sequence)) = 'SIGNATURE_FIRST'",
                        "sp.status = 'draft'",
                        "null as signature_sample_bytes")
                .doesNotContain("sp.status in ('pending_final_confirm'",
                        "sp.status in ('pending_sign'");
        assertThat(refresh)
                .contains("allowed_fields_json = ?", "status = 'PENDING_EMPLOYEE'")
                .contains("signing_sequence = ?", "fact_snapshot_json = ?")
                .contains("confirmation_snapshot_version = ?",
                        "confirmation_snapshot_hash = ?")
                .contains("submitted_values_json = null", "approved_hr_values_json = null")
                .contains("signature_payload_hash = null")
                .contains("profile_sync_request_id = ?", "profile_before_hash = ?")
                .contains("status in ('PENDING_EMPLOYEE', 'REJECTED')")
                .doesNotContain("'SUBMITTED'", "'APPROVED'", "'PROFILE_SYNC_FAILED'");
        assertThat(reopen)
                .contains("status = 'PENDING_EMPLOYEE'", "status = 'REJECTED'")
                .doesNotContain("signature_request_id = null",
                        "signature_sample_bytes = null", "signature_sample_hash = null");
        assertThat(preservedResubmit)
                .contains("submitted_values_json = ?", "status = ?",
                        "signature_payload_hash = ?",
                        "upper(trim(signing_sequence)) = 'SIGNATURE_FIRST'",
                        "signature_request_id is not null",
                        "signature_sample_bytes is not null",
                        "signature_sample_hash is not null")
                .doesNotContain("signature_request_id = ?",
                        "signature_sample_bytes = ?", "signature_sample_hash = ?");

        Set<String> properties = configuration.getResultMap(
                "com.erp.oa.mapper.OaSignOnboardDataRequestMapper.RequestResult")
                .getResultMappings().stream().map(mapping -> mapping.getProperty())
                .collect(Collectors.toSet());
        assertThat(properties).contains("confirmationSnapshotVersion",
                "confirmationSnapshotHash", "signaturePayloadHash");
    }

    @Test
    void companyMatchingAndSealFactsAreBoundForReadInsertAndEditableUpdate() throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignOnboardImportRowMapper.xml");
        Set<String> properties = configuration.getResultMap(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.RowResult")
                .getResultMappings().stream().map(mapping -> mapping.getProperty())
                .collect(Collectors.toSet());
        assertThat(properties).contains(
                "matchedLegalEntityId", "companyMatchMode", "companyMatchScore",
                "companySecondScore", "companyMatchPolicyVersion", "companyMasterVersion",
                "deptLegalEntityId", "companyDeptConflict", "companyCandidatesJson",
                "recommendedSealId", "sealRecommendationMode", "sealCandidatesJson");

        OaSignOnboardImportRow row = new OaSignOnboardImportRow();
        row.setBatchId(1L);
        row.setSourceRowNumber(2);
        row.setRowHash("a".repeat(64));
        row.setVersion(1L);
        BoundSql insert = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.insertRows")
                .getBoundSql(Map.of("rows", List.of(row)));
        String insertSql = insert.getSql().replaceAll("\\s+", " ").trim();
        assertThat(insertSql).contains(
                "matched_legal_entity_id", "company_match_mode", "company_match_score",
                "company_second_score", "company_match_policy_version",
                "company_master_version", "dept_legal_entity_id", "company_dept_conflict",
                "company_candidates_json", "recommended_seal_id",
                "seal_recommendation_mode", "seal_candidates_json");

        String updateSql = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignOnboardImportRowMapper.updateEditableWithVersion")
                .getBoundSql(row).getSql().replaceAll("\\s+", " ").trim();
        assertThat(updateSql).contains(
                "matched_legal_entity_id = ?", "company_match_mode = ?",
                "company_master_version = ?", "recommended_seal_id = ?",
                "seal_recommendation_mode = ?");
    }

    private Configuration configuration(String resource) throws Exception
    {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
}
