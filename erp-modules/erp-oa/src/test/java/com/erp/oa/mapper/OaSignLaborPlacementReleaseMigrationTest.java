package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("劳动合同正文签章版本迁移发布契约")
class OaSignLaborPlacementReleaseMigrationTest
{
    private static final String MIGRATION =
            "erp_oa_sign_labor_contract_placement_v7_20260809.sql";

    @Test
    @DisplayName("三份可跟踪迁移完全一致且只新增不可变v7方案版本")
    void shouldShipIdenticalFailClosedImmutablePublication() throws Exception
    {
        String root = readRepoFile("sql/" + MIGRATION);
        assertThat(readRepoFile("docker/mysql/db/" + MIGRATION)).isEqualTo(root);
        assertThat(readRepoFile("erp-modules/erp-oa/src/main/resources/db/migration/"
                + MIGRATION)).isEqualTo(root);
        assertThat(root)
                .contains(
                        "START TRANSACTION",
                        "RESIGNAL",
                        "COMMIT",
                        "template_id = 92",
                        "20260721-v7",
                        "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558",
                        "bd07d71829535d12a26f5a4637a5e4f73fe49e0ad1340287fa92959cd0b85396",
                        "eda0fcd21b70b302ca1edd43facb0d5c4b03e7a593b9a5f8a82d5147f0c87d40",
                        "non-terminal v4 packages require void-and-restart review",
                        "candidate plan snapshot fields are inconsistent",
                        "unknown enabled labor plan/template binding exists",
                        "enabled labor plan binding is ambiguous",
                        "reviewed enabled labor plan set is incomplete or ambiguous",
                        "enabled labor binding count must equal four",
                        "active labor plan handbook set is incomplete or ambiguous",
                        "post-switch active labor plan handbook set is invalid",
                        "active plan required document set is invalid",
                        "post-switch active plan required document set is invalid",
                        "ONBOARD_COMMITMENT",
                        "ONBOARD_SALARY_CONFIRM",
                        "ONBOARD_CONFIDENTIAL_NONCOMPETE",
                        "COUNT(*) <> IF(pv.plan_id = 66, 5, 4)",
                        "no active legal entity is available for signing",
                        "active legal entity evidence is incomplete",
                        "post-switch active legal entity evidence is incomplete",
                        "handbook.employee_visible, 'N'",
                        "('ONBOARD_HANDBOOK', 'ONBOARD_HANDBOOK_RECEIPT')",
                        "NULLIF(TRIM(legal_representative), '') IS NULL",
                        "NULLIF(TRIM(registered_address), '') IS NULL",
                        "post-switch reviewed labor plan set is incomplete",
                        "SELECT 35 AS plan_id UNION ALL SELECT 36",
                        "UNION ALL SELECT 49 UNION ALL SELECT 66",
                        "post-switch enabled labor binding is not exact-v7",
                        "pv.plan_id IN (35, 36, 49, 66)",
                        "target.plan_name <=> source.plan_name",
                        "target.rule_json <=> source.rule_json",
                        "target.default_values_json <=> source.default_values_json",
                        "target.reminder_policy_json <=> source.reminder_policy_json",
                        "target.auto_send_condition_json <=> source.auto_send_condition_json",
                        "target.matching_status <> IF(v_already_applied = 1, 'ENABLED', 'DISABLED')",
                        "source.template_type = 'ONBOARD_LABOR_CONTRACT'",
                        "SET matching_status = 'DISABLED'",
                        "SET matching_status = 'ENABLED'")
                .doesNotContain(
                        "UPDATE oa_sign_package ",
                        "UPDATE oa_sign_package_document ",
                        "DELETE FROM oa_sign_",
                        "DROP TABLE",
                        "TRUNCATE");
    }

    @Test
    @DisplayName("预检只读且回滚在候选已被业务引用后失败关闭")
    void shouldProvideReadOnlyPreflightAndConditionalRollback() throws Exception
    {
        String preflight = readRepoFile(
                "scripts/sign-original-placement-export-preflight-20260809.sql");
        String rollback = readRepoFile(
                "scripts/sign-original-placement-export-rollback-20260809.sql");

        assertThat(preflight)
                .contains(
                        "expected_0_nonterminal_v4_packages",
                        "expected_2_reviewed_v4_labor_bindings",
                        "source_enabled_count",
                        "candidate_enabled_count",
                        "expected_0_other_enabled_count",
                        "expected_0_candidate_plan_field_mismatches",
                        "expected_4_enabled_labor_bindings",
                        "expected_0_missing_or_ambiguous_reviewed_labor_plans",
                        "expected_0_unknown_enabled_labor_bindings",
                        "expected_0_active_plan_handbook_mismatches",
                        "employee_visible_handbook_count",
                        "expected_0_active_plan_required_document_set_mismatches",
                        "employee_visible_count",
                        "ONBOARD_COMMITMENT",
                        "ONBOARD_SALARY_CONFIRM",
                        "ONBOARD_CONFIDENTIAL_NONCOMPETE",
                        "active_legal_entity_count",
                        "expected_0_incomplete_active_legal_entities",
                        "PII-free historical inventory only",
                        "oa_sign_final_confirmation",
                        "p.final_document_root_hash",
                        "p.final_archive_root_hash",
                        "ONBOARD_HANDBOOK_RECEIPT",
                        "d.template_version_snapshot = '20260718-v4-draft'",
                        "ORDER BY plan_id, version_no, version_id")
                .doesNotContain("UPDATE ", "INSERT ", "DELETE ", "ALTER ",
                        "DROP ", "TRUNCATE ");
        assertThat(rollback)
                .contains(
                        "WHERE plan_version_id IN (v_candidate_49, v_candidate_36)",
                        "rollback blocked because candidate packages exist",
                        "rollback matching state is unsafe or ambiguous",
                        "v_source_enabled = 0 AND v_candidate_enabled = 2",
                        "v_source_enabled = 2 AND v_candidate_enabled = 0",
                        "v_source_enabled <> 2 OR v_candidate_enabled <> 0",
                        "v_other_enabled <> 0",
                        "SET matching_status = 'DISABLED'",
                        "SET matching_status = 'ENABLED'")
                .doesNotContain("DELETE FROM", "DROP TABLE", "TRUNCATE");
    }

    @Test
    @DisplayName("预检与Java门禁统一可见手册口径且隐藏主合同失败关闭")
    void shouldKeepEmployeeVisibleHandbookCoverageSemanticsAligned() throws Exception
    {
        String preflight = readRepoFile(
                "scripts/sign-original-placement-export-preflight-20260809.sql");
        String verifier = readRepoFile(
                "erp-modules/erp-oa/src/test/java/com/erp/oa/service/impl/"
                        + "OaSignLaborPlacementDatabaseVerifierTest.java");

        String inFlight = section(preflight,
                "-- PII-free inventory of every non-terminal",
                "SELECT plan_id, version_id, version_no");
        String signed = section(preflight,
                "-- PII-free historical inventory only.", null);
        String visibleDocument = "UPPER(TRIM(COALESCE(d.employee_visible, 'N'))) = 'Y'";
        String handbookPredicate = "WHERE sibling.package_id = p.package_id "
                + "AND UPPER(TRIM(COALESCE(sibling.employee_visible, 'N'))) = 'Y' "
                + "AND sibling.template_type IN "
                + "('ONBOARD_HANDBOOK', 'ONBOARD_HANDBOOK_RECEIPT')";
        String unfilteredSignedWhere = "WHERE LOWER(TRIM(p.status)) = 'signed' "
                + "AND UPPER(TRIM(COALESCE(p.final_confirmation_status, ''))) = "
                + "'CONFIRMED' AND d.template_type = 'ONBOARD_LABOR_CONTRACT'";

        assertThat(normalizeWhitespace(inFlight))
                .contains(visibleDocument, handbookPredicate,
                        "employee_visible_handbook_state",
                        "expected_0_unsupported_nonterminal_labor_packages",
                        "final_confirmation_status", "signature_evidence_state",
                        "final_candidate_state",
                        "company_frozen_time", "seal_image_hash_snapshot",
                        "visible.final_document_version <> p.final_document_version")
                .doesNotContain(
                        "WHERE d.template_version_snapshot = '20260721-v7'",
                        "WHERE p.signing_sequence = 'COMPANY_FIRST'");
        assertThat(normalizeWhitespace(signed))
                .contains(visibleDocument, handbookPredicate,
                        "employee_visibility_state", unfilteredSignedWhere,
                        "THEN 'ROOT_AND_CONFIRMATION_PRESENT' ELSE 'INCOMPLETE' END "
                                + "AS root_state");
        assertThat(normalizeWhitespace(verifier))
                .contains(visibleDocument, handbookPredicate, unfilteredSignedWhere,
                        "THEN 'INCLUDED' ELSE 'ABSENT' END AS handbook_state",
                        "verifyEveryInFlightLaborContinuation",
                        "verifyFrozenCompanyAndCandidate",
                        "employee_visible_handbook_count",
                        "loadActivePlanTemplateInventory",
                        "assertExactReviewedTemplateSets",
                        "reviewedActivePlanTemplateCounts",
                        "assertActiveLegalEntitySummary",
                        "legal_representative), '') IS NULL",
                        "registered_address), '') IS NULL",
                        "validateAndFreezeGeneratedPositions",
                        "validateFinalDocuments", "finalDocumentRootHashFromHashes",
                        "signatures.expanded().size() == 4",
                        "seal.expanded().size() == 1");
    }

    private String section(String source, String startMarker, String endMarker)
    {
        int start = source.indexOf(startMarker);
        assertThat(start).as("section start %s", startMarker).isGreaterThanOrEqualTo(0);
        int end = endMarker == null ? source.length() : source.indexOf(endMarker, start);
        assertThat(end).as("section end %s", endMarker).isGreaterThan(start);
        return source.substring(start, end);
    }

    private String normalizeWhitespace(String value)
    {
        return value.replaceAll("\\s+", " ")
                .replaceAll("\\( ", "(")
                .replaceAll(" \\)", ")")
                .trim();
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
