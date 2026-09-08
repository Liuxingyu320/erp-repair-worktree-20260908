package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.oa.constant.OaSignTemplateType;
import com.erp.oa.config.OaSignFileProperties;
import com.erp.oa.domain.OaSignFinalConfirmation;
import com.erp.oa.domain.OaSignFinalConfirmationDocument;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.OaSignPlanVersion;
import com.erp.oa.domain.OaSignPlanVersionTemplate;
import com.erp.oa.domain.vo.OaSignPackageFile;
import com.erp.oa.mapper.OaSignFinalConfirmationMapper;
import com.erp.oa.mapper.OaSignPackageDocumentMapper;

/**
 * Explicitly enabled, read-only release verifier. It is intentionally skipped by ordinary test
 * runs. The release script supplies a read-only database account and executes it both before and
 * after the migration. No employee identity or file contents are emitted.
 */
@DisplayName("劳动合同正文落位数据库发布门禁")
class OaSignLaborPlacementDatabaseVerifierTest
{
    private static final String ENABLE = "ERP_SIGN_PLACEMENT_DB_VERIFY";
    private static final String EXACT_V7_HASH =
            "1226728ec0e703d513efda83dc5578ee4cdd65cb3e7d0e7cb5afee813f466558";
    private static final String BLOCKED_V4_HASH =
            "fdbe2df12eb3704370126e09f2c26fd7cd09e0a06db1e0fe960f42f9513f2118";
    private static final Set<Long> REVIEWED_LABOR_PLAN_IDS = Set.of(35L, 36L, 49L, 66L);
    private static final Map<Long, String> SOURCE_HASHES = Map.of(
            1000000002L,
            "f79d4fb655ae6b4ea186fb13609c0e06cd3bd89a40f0abbac7e91cd4243db0b7",
            1000000005L,
            "d115f4088e103587d395a73e09fc4cef93550e19b2b91dc870a6abc3f231e605");
    private static final Map<Long, String> CANDIDATE_HASHES = Map.of(
            49L,
            "bd07d71829535d12a26f5a4637a5e4f73fe49e0ad1340287fa92959cd0b85396",
            36L,
            "eda0fcd21b70b302ca1edd43facb0d5c4b03e7a593b9a5f8a82d5147f0c87d40");
    private static final String SIGNED_COVERAGE_SQL = """
            SELECT p.package_id, d.document_id,
                   COALESCE(d.template_version_snapshot, '<MISSING>') AS template_version,
                   COALESCE(pvt.source_file_hash, '<MISSING>') AS source_hash,
                   COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
                       d.signature_position_json, '$.mode')),
                       d.document_policy_mode, '<MISSING>') AS policy_mode,
                   p.legal_entity_id_snapshot, p.legal_entity_name_snapshot,
                   p.legal_representative_snapshot, p.final_confirmed_time,
                   CASE WHEN UPPER(TRIM(COALESCE(d.employee_visible, 'N'))) = 'Y'
                              AND d.review_pdf_url IS NOT NULL
                              AND d.review_pdf_hash REGEXP '^[0-9a-fA-F]{64}$'
                              AND d.final_archive_pdf_url IS NOT NULL
                              AND d.final_archive_pdf_hash REGEXP '^[0-9a-fA-F]{64}$'
                              AND d.final_content_hash REGEXP '^[0-9a-fA-F]{64}$'
                              AND p.final_document_root_hash REGEXP '^[0-9a-fA-F]{64}$'
                              AND p.final_archive_root_hash REGEXP '^[0-9a-fA-F]{64}$'
                              AND ((d.signature_file_url IS NOT NULL
                                    AND d.signature_hash REGEXP '^[0-9a-fA-F]{64}$')
                                   OR (p.signature_sample_file_url IS NOT NULL
                                    AND p.signature_sample_hash REGEXP
                                        '^[0-9a-fA-F]{64}$'))
                              AND p.seal_image_url_snapshot IS NOT NULL
                              AND p.seal_image_hash_snapshot REGEXP '^[0-9a-fA-F]{64}$'
                              AND p.final_confirmed_time IS NOT NULL
                              AND EXISTS (
                                  SELECT 1 FROM oa_sign_final_confirmation c
                                   WHERE c.package_id = p.package_id
                                     AND c.employee_id = p.employee_id
                                     AND c.final_document_version = p.final_document_version
                                     AND LOWER(c.document_root_hash) =
                                         LOWER(p.final_document_root_hash)
                                     AND UNIX_TIMESTAMP(c.confirmed_time) =
                                         UNIX_TIMESTAMP(p.final_confirmed_time))
                              AND EXISTS (
                                  SELECT 1 FROM oa_sign_package_document visible
                                   WHERE visible.package_id = p.package_id
                                     AND UPPER(TRIM(COALESCE(
                                         visible.employee_visible, 'N'))) = 'Y')
                              AND NOT EXISTS (
                                  SELECT 1 FROM oa_sign_package_document visible
                                   WHERE visible.package_id = p.package_id
                                     AND UPPER(TRIM(COALESCE(
                                         visible.employee_visible, 'N'))) = 'Y'
                                     AND (visible.final_document_version IS NULL
                                          OR p.final_document_version IS NULL
                                          OR visible.final_document_version <>
                                              p.final_document_version
                                          OR visible.final_pdf_hash IS NULL
                                          OR visible.final_pdf_hash NOT REGEXP
                                              '^[0-9a-fA-F]{64}$'
                                          OR visible.final_content_hash IS NULL
                                          OR visible.final_content_hash NOT REGEXP
                                              '^[0-9a-fA-F]{64}$'
                                          OR visible.final_archive_pdf_url IS NULL
                                          OR visible.final_archive_pdf_hash IS NULL
                                          OR visible.final_archive_pdf_hash NOT REGEXP
                                              '^[0-9a-fA-F]{64}$'))
                              AND NOT EXISTS (
                                  SELECT 1
                                    FROM oa_sign_package_document visible
                                    LEFT JOIN oa_sign_final_confirmation_document cd
                                      ON cd.package_id = p.package_id
                                     AND cd.document_id = visible.document_id
                                     AND cd.final_document_version =
                                         p.final_document_version
                                     AND LOWER(cd.final_pdf_hash) =
                                         LOWER(visible.final_pdf_hash)
                                   WHERE visible.package_id = p.package_id
                                     AND UPPER(TRIM(COALESCE(
                                         visible.employee_visible, 'N'))) = 'Y'
                                     AND cd.confirmation_document_id IS NULL)
                        THEN 'COMPLETE' ELSE 'MISSING' END AS evidence_state,
                   CASE WHEN EXISTS (
                              SELECT 1 FROM oa_sign_package_document sibling
                               WHERE sibling.package_id = p.package_id
                                 AND UPPER(TRIM(COALESCE(
                                     sibling.employee_visible, 'N'))) = 'Y'
                                 AND sibling.template_type IN
                                     ('ONBOARD_HANDBOOK',
                                      'ONBOARD_HANDBOOK_RECEIPT'))
                        THEN 'INCLUDED' ELSE 'ABSENT' END AS handbook_state
              FROM oa_sign_package p
              JOIN oa_sign_package_document d ON d.package_id = p.package_id
              LEFT JOIN oa_sign_plan_version_template pvt
                ON pvt.plan_version_id = p.plan_version_id
               AND pvt.template_type = d.template_type
             WHERE LOWER(TRIM(p.status)) = 'signed'
               AND UPPER(TRIM(COALESCE(p.final_confirmation_status, ''))) = 'CONFIRMED'
               AND d.template_type = 'ONBOARD_LABOR_CONTRACT'
             ORDER BY p.package_id, d.document_id
            """;
    private static final String IN_FLIGHT_COVERAGE_SQL = """
            SELECT p.package_id, d.document_id, LOWER(TRIM(p.status)) AS package_status,
                   UPPER(TRIM(COALESCE(p.signing_sequence, '<MISSING>')))
                       AS signing_sequence,
                   COALESCE(d.template_version_snapshot, '<MISSING>') AS template_version,
                   LOWER(COALESCE(pvt.source_file_hash, '<MISSING>')) AS source_hash,
                   d.document_policy_mode, d.source_file_url_snapshot,
                   d.review_pdf_url, d.review_pdf_hash,
                   d.employee_sign_required, d.company_seal_required,
                   d.signature_position_json, d.company_seal_position_json,
                   p.legal_entity_id_snapshot, p.legal_entity_name_snapshot,
                   p.legal_representative_snapshot, p.company_frozen_time,
                   p.seal_image_url_snapshot, p.seal_image_hash_snapshot,
                   p.final_document_version, p.final_document_root_hash,
                   p.final_generated_time, p.final_confirmation_status,
                   CASE WHEN (NULLIF(TRIM(p.signature_sample_file_url), '') IS NOT NULL
                                   AND p.signature_sample_hash REGEXP '^[0-9a-fA-F]{64}$'
                                   AND p.signature_sample_time IS NOT NULL)
                                  OR (COALESCE(p.initial_signed_time, p.signed_time) IS NOT NULL
                                   AND EXISTS (
                                       SELECT 1 FROM oa_sign_package_document required_doc
                                        WHERE required_doc.package_id = p.package_id
                                          AND UPPER(TRIM(COALESCE(
                                              required_doc.employee_visible, 'N'))) = 'Y'
                                          AND UPPER(TRIM(COALESCE(
                                              required_doc.employee_sign_required, 'N'))) = 'Y')
                                   AND NOT EXISTS (
                                       SELECT 1 FROM oa_sign_package_document required_doc
                                        WHERE required_doc.package_id = p.package_id
                                          AND UPPER(TRIM(COALESCE(
                                              required_doc.employee_visible, 'N'))) = 'Y'
                                          AND UPPER(TRIM(COALESCE(
                                              required_doc.employee_sign_required, 'N'))) = 'Y'
                                          AND (NULLIF(TRIM(
                                              required_doc.signature_file_url), '') IS NULL
                                               OR required_doc.signature_hash NOT REGEXP
                                                   '^[0-9a-fA-F]{64}$')))
                        THEN 'COMPLETE' ELSE 'INCOMPLETE' END
                       AS signature_evidence_state,
                   CASE WHEN p.legal_entity_id_snapshot IS NOT NULL
                              AND NULLIF(TRIM(p.legal_entity_name_snapshot), '') IS NOT NULL
                              AND NULLIF(TRIM(p.legal_representative_snapshot), '') IS NOT NULL
                              AND p.company_frozen_time IS NOT NULL
                              AND NULLIF(TRIM(p.seal_image_url_snapshot), '') IS NOT NULL
                              AND p.seal_image_hash_snapshot REGEXP '^[0-9a-fA-F]{64}$'
                              AND NULLIF(TRIM(p.final_document_version), '') IS NOT NULL
                              AND p.final_document_root_hash REGEXP '^[0-9a-fA-F]{64}$'
                              AND p.final_generated_time IS NOT NULL
                              AND EXISTS (
                                  SELECT 1 FROM oa_sign_package_document visible
                                   WHERE visible.package_id = p.package_id
                                     AND UPPER(TRIM(COALESCE(
                                         visible.employee_visible, 'N'))) = 'Y')
                              AND NOT EXISTS (
                                  SELECT 1 FROM oa_sign_package_document visible
                                   WHERE visible.package_id = p.package_id
                                     AND UPPER(TRIM(COALESCE(
                                         visible.employee_visible, 'N'))) = 'Y'
                                     AND (visible.final_document_version IS NULL
                                          OR visible.final_document_version <>
                                              p.final_document_version
                                          OR NULLIF(TRIM(visible.final_pdf_url), '') IS NULL
                                          OR visible.final_pdf_hash NOT REGEXP
                                              '^[0-9a-fA-F]{64}$'
                                          OR visible.final_content_hash NOT REGEXP
                                              '^[0-9a-fA-F]{64}$'))
                        THEN 'COMPLETE' ELSE 'INCOMPLETE' END
                       AS final_candidate_state,
                   CASE WHEN EXISTS (
                              SELECT 1 FROM oa_sign_package_document sibling
                               WHERE sibling.package_id = p.package_id
                                 AND UPPER(TRIM(COALESCE(
                                     sibling.employee_visible, 'N'))) = 'Y'
                                 AND sibling.template_type IN
                                     ('ONBOARD_HANDBOOK',
                                      'ONBOARD_HANDBOOK_RECEIPT'))
                        THEN 'INCLUDED' ELSE 'ABSENT' END AS handbook_state
              FROM oa_sign_package p
              JOIN oa_sign_package_document d
                ON d.package_id = p.package_id
               AND UPPER(TRIM(COALESCE(d.employee_visible, 'N'))) = 'Y'
               AND d.template_type = 'ONBOARD_LABOR_CONTRACT'
              LEFT JOIN oa_sign_plan_version_template pvt
                ON pvt.plan_version_id = p.plan_version_id
               AND pvt.template_type = d.template_type
             WHERE LOWER(TRIM(p.status)) NOT IN
                   ('signed', 'voided', 'refused', 'expired')
             ORDER BY p.package_id, d.document_id
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OaSignPlanVersionFingerprint fingerprint =
            new OaSignPlanVersionFingerprint();

    @Test
    @DisplayName("迁移前后均按正式算法重算完整方案快照并盘点全部已签劳动合同")
    void shouldRecomputeCompleteSnapshotsAndFailClosedUnknownHistoricalGroups()
            throws Exception
    {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv(ENABLE)),
                () -> ENABLE + " was not explicitly enabled");
        String jdbcUrl = requiredEnv("ERP_SIGN_PLACEMENT_JDBC_URL");
        String user = requiredEnv("ERP_SIGN_PLACEMENT_JDBC_USER");
        String password = System.getenv().getOrDefault(
                "ERP_SIGN_PLACEMENT_JDBC_PASSWORD", "");
        String stage = requiredEnv("ERP_SIGN_PLACEMENT_VERIFY_STAGE")
                .trim().toUpperCase(Locale.ROOT);
        assertThat(stage).isIn("PRE", "POST");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, password))
        {
            connection.setReadOnly(true);
            DatabaseIdentity databaseIdentity = loadDatabaseIdentity(connection);
            OaSignPlanVersionTemplate exactV7 = loadExactV7(connection);
            Map<Long, String> recomputedCandidates = new LinkedHashMap<>();
            for (Map.Entry<Long, String> sourceEntry : SOURCE_HASHES.entrySet())
            {
                OaSignPlanVersion source = loadVersion(connection, sourceEntry.getKey());
                assertThat(fingerprint.calculate(source))
                        .as("source version %s formal hash", sourceEntry.getKey())
                        .isEqualTo(sourceEntry.getValue())
                        .isEqualTo(source.getVersionHash());

                OaSignPlanVersion theoretical = replaceLabor(source, exactV7);
                String expectedCandidate = CANDIDATE_HASHES.get(source.getPlanId());
                String recomputed = fingerprint.calculate(theoretical);
                assertThat(recomputed)
                        .as("plan %s theoretical candidate formal hash", source.getPlanId())
                        .isEqualTo(expectedCandidate);
                recomputedCandidates.put(source.getPlanId(), recomputed);

                List<OaSignPlanVersion> persisted = loadCandidates(
                        connection, source.getPlanId(), expectedCandidate);
                assertThat(persisted).hasSizeLessThanOrEqualTo(1);
                if ("POST".equals(stage))
                {
                    assertThat(persisted)
                            .as("post-migration candidate for plan %s", source.getPlanId())
                            .hasSize(1);
                }
                if (!persisted.isEmpty())
                {
                    OaSignPlanVersion actual = persisted.get(0);
                    assertThat(fingerprint.calculate(actual))
                            .as("persisted plan %s complete snapshot hash", source.getPlanId())
                            .isEqualTo(expectedCandidate)
                            .isEqualTo(actual.getVersionHash());
                    assertThat(actual.getPublishStatus()).isEqualTo("PUBLISHED");
                    assertThat(actual.getMatchingStatus())
                            .isEqualTo("POST".equals(stage) ? "ENABLED" : "DISABLED");
                }
            }

            if ("POST".equals(stage))
            {
                assertMatchingState(connection);
            }
            List<EnabledLaborBinding> enabledLabor = loadEnabledLaborBindings(connection);
            assertAllEnabledLaborBindings(stage, enabledLabor, connection);
            Map<Long, List<ActivePlanTemplateCount>> activePlanTemplates =
                    loadActivePlanTemplateInventory(connection);
            assertExactReviewedTemplateSets(activePlanTemplates);
            ActiveLegalEntitySummary legalEntities = loadActiveLegalEntitySummary(connection);
            assertActiveLegalEntitySummary(legalEntities);
            assertExactV7RuntimeBytes(enabledLabor);
            assertThat(nonTerminalV4Count(connection))
                    .as("non-terminal packages still bound to v4")
                    .isZero();
            List<InFlightRow> inFlightRows = loadInFlightRows(connection);
            InFlightDryRunEvidence inFlightDryRun =
                    verifyEveryInFlightLaborContinuation(connection, inFlightRows);
            List<InFlightGroup> inFlightCoverage = aggregateInFlight(
                    inFlightRows, inFlightDryRun.verifiedDocumentIds());
            assertThat(inFlightRows)
                    .as("every non-terminal employee-visible labor contract has a safe "
                            + "continuation policy")
                    .allMatch(row -> inFlightSupported(row)
                            && inFlightDryRun.verifiedDocumentIds()
                                    .contains(row.documentId()));

            List<CoverageRow> coverageRows = loadSignedCoverageRows(connection);
            DryRunEvidence dryRun = verifyEverySignedLaborExport(
                    connection, coverageRows);
            List<CoverageGroup> coverage = aggregateCoverage(
                    coverageRows, dryRun.verifiedDocumentIds());
            long unsupported = coverage.stream().filter(group -> !group.supported()).count();
            assertThat(unsupported)
                    .as("every SIGNED+CONFIRMED labor-contract evidence group must be supported")
                    .isZero();
            writeEvidence(stage, recomputedCandidates, enabledLabor, activePlanTemplates,
                    legalEntities, databaseIdentity,
                    inFlightCoverage, inFlightDryRun, coverage, dryRun);
        }
    }

    @Test
    @DisplayName("聚合报表缺手册、缺代表、缺双root或未核准LAST_PAGE均不得冒充可导出")
    void shouldFailClosedIncompleteCoverageWithoutTreatingModeAsAuthority()
    {
        Date confirmed = new Date(1_700_000_000_000L);
        CoverageRow complete = coverageRow("APPENDED_CONFIRMATION_PAGE",
                "COMPLETE", "FROZEN", "INCLUDED", confirmed);
        assertThat(preliminarySupported(complete)).isTrue();
        assertThat(preliminarySupported(complete.withHandbookState("ABSENT"))).isFalse();
        assertThat(preliminarySupported(complete.withRepresentativeState("MISSING"))).isFalse();
        assertThat(preliminarySupported(complete.withEvidenceState("MISSING"))).isFalse();
        assertThat(preliminarySupported(complete.withDocumentPolicyMode("LAST_PAGE_MULTI")))
                .isFalse();
        assertThat(preliminarySupported(complete.withDocumentPolicyMode("PLACED_MULTI")))
                .as("PLACED_MULTI only reaches the mandatory formal dry-run")
                .isTrue();
    }

    @Test
    @DisplayName("隐藏手册不计入员工可见签约包且活动方案和法律主体门禁失败关闭")
    void shouldRejectHiddenHandbookIncompletePlansAndIncompleteLegalEntities()
    {
        assertThat(SIGNED_COVERAGE_SQL)
                .contains(
                        "sibling.employee_visible, 'N'))) = 'Y'",
                        "d.employee_visible, 'N'))) = 'Y'");
        Date confirmed = new Date(1_700_000_000_000L);
        CoverageRow hiddenHandbook = coverageRow("APPENDED_CONFIRMATION_PAGE",
                "COMPLETE", "FROZEN", "ABSENT", confirmed);
        assertThat(preliminarySupported(hiddenHandbook))
                .as("an employee-hidden handbook is classified as ABSENT")
                .isFalse();

        List<EnabledLaborBinding> complete = List.of(
                binding(35L), binding(36L), binding(49L), binding(66L));
        assertExactReviewedBindingCardinality(complete);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                assertExactReviewedBindingCardinality(List.of(
                        binding(35L), binding(36L), binding(49L))))
                .isInstanceOf(AssertionError.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                assertExactReviewedBindingCardinality(List.of(
                        binding(35L), binding(36L), binding(49L), binding(66L),
                        binding(77L))))
                .isInstanceOf(AssertionError.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                assertExactReviewedBindingCardinality(List.of(
                        binding(35L).withEmployeeVisibleHandbookCount(0),
                        binding(36L), binding(49L), binding(66L))))
                .isInstanceOf(AssertionError.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                assertExactReviewedBindingCardinality(List.of(
                        binding(35L).withEmployeeVisibleHandbookCount(2),
                        binding(36L), binding(49L), binding(66L))))
                .isInstanceOf(AssertionError.class);

        Map<Long, List<ActivePlanTemplateCount>> templateInventory =
                reviewedTemplateInventory();
        assertExactReviewedTemplateSets(templateInventory);
        Map<Long, List<ActivePlanTemplateCount>> withLegitimateServicePlans = new TreeMap<>(
                templateInventory);
        withLegitimateServicePlans.put(37L, List.of(
                new ActivePlanTemplateCount("ONBOARD_COMMITMENT", 1L, 1L),
                new ActivePlanTemplateCount("ONBOARD_SERVICE_CONTRACT", 1L, 1L),
                new ActivePlanTemplateCount("ONBOARD_SERVICE_RECEIPT", 1L, 1L)));
        withLegitimateServicePlans.put(45L, List.of(
                new ActivePlanTemplateCount("ONBOARD_COMMITMENT", 1L, 1L),
                new ActivePlanTemplateCount("ONBOARD_SERVICE_CONTRACT", 1L, 1L),
                new ActivePlanTemplateCount("ONBOARD_SERVICE_RECEIPT", 1L, 1L),
                new ActivePlanTemplateCount("ONBOARD_CONFIDENTIAL_NONCOMPETE", 1L, 1L)));
        assertExactReviewedTemplateSets(withLegitimateServicePlans);
        for (ActivePlanTemplateCount invalid : List.of(
                new ActivePlanTemplateCount("ONBOARD_SALARY_CONFIRM", 1L, 0L),
                new ActivePlanTemplateCount("ONBOARD_SALARY_CONFIRM", 2L, 2L)))
        {
            Map<Long, List<ActivePlanTemplateCount>> drifted =
                    replaceTemplateCount(templateInventory, 35L, invalid);
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    assertExactReviewedTemplateSets(drifted))
                    .isInstanceOf(AssertionError.class);
        }
        Map<Long, List<ActivePlanTemplateCount>> missingSalary = new TreeMap<>(
                templateInventory);
        missingSalary.put(35L, templateInventory.get(35L).stream()
                .filter(value -> !"ONBOARD_SALARY_CONFIRM".equals(value.templateType()))
                .toList());
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                assertExactReviewedTemplateSets(missingSalary))
                .isInstanceOf(AssertionError.class);
        Map<Long, List<ActivePlanTemplateCount>> unknownExtra = new TreeMap<>(
                templateInventory);
        List<ActivePlanTemplateCount> plan35WithExtra = new ArrayList<>(
                templateInventory.get(35L));
        plan35WithExtra.add(new ActivePlanTemplateCount("UNREVIEWED_TEMPLATE", 1L, 1L));
        unknownExtra.put(35L, List.copyOf(plan35WithExtra));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                assertExactReviewedTemplateSets(unknownExtra))
                .isInstanceOf(AssertionError.class);

        assertActiveLegalEntitySummary(new ActiveLegalEntitySummary(4L, 0L));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                assertActiveLegalEntitySummary(new ActiveLegalEntitySummary(0L, 0L)))
                .isInstanceOf(AssertionError.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                assertActiveLegalEntitySummary(new ActiveLegalEntitySummary(4L, 1L)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("在途劳动合同全量门禁只放行exact-v7旧APPENDED公司先行或顺序一致MULTI")
    void shouldFailClosedUnsupportedInFlightLaborPolicies()
    {
        InFlightRow appendedCompanyFirst = inFlightRow(
                "COMPANY_FIRST", appendedPolicy(), appendedPolicy());
        assertThat(inFlightSupported(appendedCompanyFirst)).isTrue();

        InFlightRow placedCompanyFirst = inFlightRow("COMPANY_FIRST",
                multiSignaturePolicy("COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT"),
                multiSealPolicy("COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT"));
        InFlightRow placedSignatureFirst = inFlightRow("SIGNATURE_FIRST",
                multiSignaturePolicy("SIGNATURE_FIRST_BODY_PLACEMENT"),
                multiSealPolicy("SIGNATURE_FIRST_BODY_PLACEMENT"))
                .withLifecycle("pending_sign", "<MISSING>", "INCOMPLETE",
                        "INCOMPLETE");
        assertThat(inFlightSupported(placedCompanyFirst)).isTrue();
        assertThat(inFlightSupported(placedSignatureFirst)).isTrue();

        assertThat(inFlightSupported(appendedCompanyFirst.withTemplateVersion(
                "20260718-v4-draft"))).isFalse();
        assertThat(inFlightSupported(appendedCompanyFirst.withSourceHash("<MISSING>")))
                .isFalse();
        assertThat(inFlightSupported(appendedCompanyFirst.withHandbookState("ABSENT")))
                .isFalse();
        assertThat(inFlightSupported(inFlightRow("SIGNATURE_FIRST",
                multiSignaturePolicy(null), multiSealPolicy(null)))).isFalse();
        assertThat(inFlightSupported(inFlightRow("COMPANY_FIRST",
                multiSignaturePolicy("SIGNATURE_FIRST_BODY_PLACEMENT"),
                multiSealPolicy("SIGNATURE_FIRST_BODY_PLACEMENT")))).isFalse();
        assertThat(inFlightSupported(placedCompanyFirst.withLifecycle(
                "pending_sign", "PREPARED_NOT_SENT", "COMPLETE", "INCOMPLETE")))
                .as("company-first MULTI requires frozen company and full candidate root")
                .isFalse();
        InFlightRow signatureWaitingCompany = placedSignatureFirst.withLifecycle(
                "pending_company", "WAITING_COMPANY", "COMPLETE", "INCOMPLETE");
        assertThat(inFlightSupported(signatureWaitingCompany)).isTrue();
        assertThat(inFlightSupported(signatureWaitingCompany.withLifecycle(
                "pending_company", "WAITING_COMPANY", "COMPLETE", "COMPLETE")))
                .as("waiting-company must not hide an already-generated candidate")
                .isFalse();
        assertThat(inFlightSupported(signatureWaitingCompany.withLifecycle(
                "pending_company", "WAITING_COMPANY", "INCOMPLETE", "INCOMPLETE")))
                .as("pending-company signature-first requires immutable signature evidence")
                .isFalse();
        assertThat(inFlightSupported(placedSignatureFirst.withLifecycle(
                "pending_final_confirm", "PENDING", "COMPLETE", "COMPLETE"))).isTrue();
        assertThat(inFlightSupported(placedSignatureFirst.withLifecycle(
                "pending_final_confirm", "PENDING", "COMPLETE", "INCOMPLETE")))
                .as("pending-final-confirm requires the matching complete candidate")
                .isFalse();
        assertThat(inFlightSupported(placedSignatureFirst.withLifecycle(
                "failed", "WAITING_COMPANY", "COMPLETE", "COMPLETE"))).isFalse();
        assertThat(IN_FLIGHT_COVERAGE_SQL)
                .contains("sibling.employee_visible, 'N'))) = 'Y'")
                .doesNotContain("d.template_version_snapshot = '20260721-v7'",
                        "pvt.source_file_hash = '" + EXACT_V7_HASH + "'",
                        "p.signing_sequence, ''))) = 'COMPANY_FIRST'");
    }

    private EnabledLaborBinding binding(long planId)
    {
        return new EnabledLaborBinding(planId, planId * 10, "hash-" + planId,
                92L, "20260721-v7", "/profile/exact-v7.docx", EXACT_V7_HASH, 1L);
    }

    private Map<Long, List<ActivePlanTemplateCount>> reviewedTemplateInventory()
    {
        List<ActivePlanTemplateCount> base = List.of(
                new ActivePlanTemplateCount("ONBOARD_COMMITMENT", 1L, 1L),
                new ActivePlanTemplateCount("ONBOARD_LABOR_CONTRACT", 1L, 1L),
                new ActivePlanTemplateCount("ONBOARD_HANDBOOK_RECEIPT", 1L, 1L),
                new ActivePlanTemplateCount("ONBOARD_SALARY_CONFIRM", 1L, 1L));
        List<ActivePlanTemplateCount> plan66 = new ArrayList<>(base);
        plan66.add(new ActivePlanTemplateCount(
                "ONBOARD_CONFIDENTIAL_NONCOMPETE", 1L, 1L));
        return Map.of(35L, base, 36L, base, 49L, base, 66L, List.copyOf(plan66));
    }

    private Map<Long, List<ActivePlanTemplateCount>> replaceTemplateCount(
            Map<Long, List<ActivePlanTemplateCount>> source, long planId,
            ActivePlanTemplateCount replacement)
    {
        Map<Long, List<ActivePlanTemplateCount>> result = new TreeMap<>(source);
        result.put(planId, source.get(planId).stream()
                .map(value -> value.templateType().equals(replacement.templateType())
                        ? replacement : value)
                .toList());
        return result;
    }

    private OaSignPlanVersion loadVersion(Connection connection, long versionId)
            throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT version_id, plan_id, plan_name, version_no, scenario, shop_dept_id,
                       legal_entity_id, legal_entity_name, rule_json, default_values_json,
                       sign_deadline_days, reminder_policy_json, auto_send_condition_json,
                       publish_status, matching_status, published_by_user_id, published_by,
                       published_time, version_hash
                  FROM oa_sign_plan_version
                 WHERE version_id = ?
                """))
        {
            statement.setLong(1, versionId);
            try (ResultSet result = statement.executeQuery())
            {
                assertThat(result.next()).as("plan version %s exists", versionId).isTrue();
                OaSignPlanVersion version = mapVersion(result);
                assertThat(result.next()).isFalse();
                version.setTemplates(loadTemplates(connection, versionId));
                return version;
            }
        }
    }

    private List<OaSignPlanVersion> loadCandidates(Connection connection, long planId,
            String versionHash) throws Exception
    {
        List<OaSignPlanVersion> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT version_id, plan_id, plan_name, version_no, scenario, shop_dept_id,
                       legal_entity_id, legal_entity_name, rule_json, default_values_json,
                       sign_deadline_days, reminder_policy_json, auto_send_condition_json,
                       publish_status, matching_status, published_by_user_id, published_by,
                       published_time, version_hash
                  FROM oa_sign_plan_version
                 WHERE plan_id = ? AND version_hash = ?
                 ORDER BY version_id
                """))
        {
            statement.setLong(1, planId);
            statement.setString(2, versionHash);
            try (ResultSet result = statement.executeQuery())
            {
                while (result.next())
                {
                    OaSignPlanVersion value = mapVersion(result);
                    value.setTemplates(loadTemplates(connection, value.getVersionId()));
                    values.add(value);
                }
            }
        }
        return values;
    }

    private OaSignPlanVersion mapVersion(ResultSet result) throws Exception
    {
        OaSignPlanVersion value = new OaSignPlanVersion();
        value.setVersionId(nullableLong(result, "version_id"));
        value.setPlanId(nullableLong(result, "plan_id"));
        value.setPlanName(result.getString("plan_name"));
        int versionNo = result.getInt("version_no");
        value.setVersionNo(result.wasNull() ? null : versionNo);
        value.setScenario(result.getString("scenario"));
        value.setShopDeptId(nullableLong(result, "shop_dept_id"));
        value.setLegalEntityId(nullableLong(result, "legal_entity_id"));
        value.setLegalEntityName(result.getString("legal_entity_name"));
        value.setRuleJson(result.getString("rule_json"));
        value.setDefaultValuesJson(result.getString("default_values_json"));
        int deadline = result.getInt("sign_deadline_days");
        value.setSignDeadlineDays(result.wasNull() ? null : deadline);
        value.setReminderPolicyJson(result.getString("reminder_policy_json"));
        value.setAutoSendConditionJson(result.getString("auto_send_condition_json"));
        value.setPublishStatus(result.getString("publish_status"));
        value.setMatchingStatus(result.getString("matching_status"));
        value.setPublishedByUserId(nullableLong(result, "published_by_user_id"));
        value.setPublishedBy(result.getString("published_by"));
        value.setPublishedTime(result.getTimestamp("published_time"));
        value.setVersionHash(result.getString("version_hash"));
        return value;
    }

    private List<OaSignPlanVersionTemplate> loadTemplates(Connection connection,
            long versionId) throws Exception
    {
        List<OaSignPlanVersionTemplate> templates = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, plan_version_id, template_id, template_version, template_type,
                       template_name, source_file_url, source_file_hash,
                       required_placeholders, sort_order, employee_visible,
                       read_confirmation_required, employee_sign_required,
                       signature_position_json, company_seal_position_json,
                       company_seal_required, match_condition_json
                  FROM oa_sign_plan_version_template
                 WHERE plan_version_id = ?
                 ORDER BY sort_order, template_type, id
                """))
        {
            statement.setLong(1, versionId);
            try (ResultSet result = statement.executeQuery())
            {
                while (result.next())
                {
                    templates.add(mapTemplate(result));
                }
            }
        }
        return templates;
    }

    private OaSignPlanVersionTemplate loadExactV7(Connection connection) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT template_id, template_version, template_type, template_name,
                       file_url AS source_file_url, file_hash AS source_file_hash,
                       required_placeholders, employee_visible, read_confirmation_required,
                       employee_sign_required, signature_position_json,
                       company_seal_position_json, company_seal_required
                  FROM oa_sign_template
                 WHERE template_id = 92 AND template_type = 'ONBOARD_LABOR_CONTRACT'
                   AND template_version = '20260721-v7' AND file_hash = ? AND status = '0'
                """))
        {
            statement.setString(1, EXACT_V7_HASH);
            try (ResultSet result = statement.executeQuery())
            {
                assertThat(result.next()).as("exact-v7 template 92").isTrue();
                OaSignPlanVersionTemplate value = mapTemplate(result);
                assertThat(result.next()).isFalse();
                return value;
            }
        }
    }

    private OaSignPlanVersionTemplate mapTemplate(ResultSet result) throws Exception
    {
        OaSignPlanVersionTemplate value = new OaSignPlanVersionTemplate();
        value.setId(hasColumn(result, "id") ? nullableLong(result, "id") : null);
        value.setPlanVersionId(hasColumn(result, "plan_version_id")
                ? nullableLong(result, "plan_version_id") : null);
        value.setTemplateId(nullableLong(result, "template_id"));
        value.setTemplateVersion(result.getString("template_version"));
        value.setTemplateType(result.getString("template_type"));
        value.setTemplateName(result.getString("template_name"));
        value.setSourceFileUrl(result.getString("source_file_url"));
        value.setSourceFileHash(result.getString("source_file_hash"));
        value.setRequiredPlaceholders(result.getString("required_placeholders"));
        if (hasColumn(result, "sort_order"))
        {
            int sortOrder = result.getInt("sort_order");
            value.setSortOrder(result.wasNull() ? null : sortOrder);
        }
        value.setEmployeeVisible(result.getString("employee_visible"));
        value.setReadConfirmationRequired(result.getString("read_confirmation_required"));
        value.setEmployeeSignRequired(result.getString("employee_sign_required"));
        value.setSignaturePositionJson(result.getString("signature_position_json"));
        value.setCompanySealPositionJson(result.getString("company_seal_position_json"));
        value.setCompanySealRequired(result.getString("company_seal_required"));
        value.setMatchConditionJson(hasColumn(result, "match_condition_json")
                ? result.getString("match_condition_json") : null);
        return value;
    }

    private OaSignPlanVersion replaceLabor(OaSignPlanVersion source,
            OaSignPlanVersionTemplate replacement)
    {
        OaSignPlanVersion target = objectMapper.convertValue(source, OaSignPlanVersion.class);
        List<OaSignPlanVersionTemplate> templates = new ArrayList<>();
        for (OaSignPlanVersionTemplate current : source.getTemplates())
        {
            if (OaSignTemplateType.ONBOARD_LABOR_CONTRACT.equals(current.getTemplateType()))
            {
                OaSignPlanVersionTemplate labor = objectMapper.convertValue(
                        replacement, OaSignPlanVersionTemplate.class);
                labor.setSortOrder(current.getSortOrder());
                labor.setMatchConditionJson(current.getMatchConditionJson());
                templates.add(labor);
            }
            else
            {
                templates.add(objectMapper.convertValue(
                        current, OaSignPlanVersionTemplate.class));
            }
        }
        target.setTemplates(templates);
        return target;
    }

    private void assertMatchingState(Connection connection) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT SUM(version_id IN (1000000002, 1000000005)
                           AND matching_status = 'ENABLED') AS source_enabled,
                       SUM(version_hash IN (?, ?) AND publish_status = 'PUBLISHED'
                           AND matching_status = 'ENABLED') AS candidate_enabled
                  FROM oa_sign_plan_version
                 WHERE plan_id IN (49, 36)
                """))
        {
            statement.setString(1, CANDIDATE_HASHES.get(49L));
            statement.setString(2, CANDIDATE_HASHES.get(36L));
            try (ResultSet result = statement.executeQuery())
            {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong("source_enabled")).isZero();
                assertThat(result.getLong("candidate_enabled")).isEqualTo(2);
            }
        }
    }

    private long nonTerminalV4Count(Connection connection) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(DISTINCT p.package_id)
                  FROM oa_sign_package p
                  LEFT JOIN oa_sign_package_document d ON d.package_id = p.package_id
                 WHERE (p.plan_version_id IN (1000000002, 1000000005)
                        OR (d.template_type = 'ONBOARD_LABOR_CONTRACT'
                            AND d.template_version_snapshot = '20260718-v4-draft'))
                   AND LOWER(TRIM(p.status)) NOT IN
                       ('signed', 'voided', 'refused', 'expired')
                """))
        {
            try (ResultSet result = statement.executeQuery())
            {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private List<EnabledLaborBinding> loadEnabledLaborBindings(Connection connection)
            throws Exception
    {
        List<EnabledLaborBinding> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pv.plan_id, pv.version_id, pv.version_hash,
                       pvt.template_id, pvt.template_version,
                       pvt.source_file_url, pvt.source_file_hash,
                       (SELECT COUNT(*)
                          FROM oa_sign_plan_version_template handbook
                         WHERE handbook.plan_version_id = pv.version_id
                           AND UPPER(TRIM(COALESCE(
                               handbook.employee_visible, 'N'))) = 'Y'
                           AND handbook.template_type IN
                               ('ONBOARD_HANDBOOK', 'ONBOARD_HANDBOOK_RECEIPT'))
                           AS employee_visible_handbook_count
                  FROM oa_sign_plan_version pv
                  JOIN oa_sign_plan_version_template pvt
                    ON pvt.plan_version_id = pv.version_id
                   AND pvt.template_type = 'ONBOARD_LABOR_CONTRACT'
                 WHERE pv.publish_status = 'PUBLISHED'
                   AND pv.matching_status = 'ENABLED'
                 ORDER BY pv.plan_id, pv.version_id, pvt.id
                """))
        {
            try (ResultSet result = statement.executeQuery())
            {
                while (result.next())
                {
                    values.add(new EnabledLaborBinding(
                            result.getLong("plan_id"), result.getLong("version_id"),
                            result.getString("version_hash"),
                            result.getLong("template_id"),
                            result.getString("template_version"),
                            result.getString("source_file_url"),
                            result.getString("source_file_hash"),
                            result.getLong("employee_visible_handbook_count")));
                }
            }
        }
        return values;
    }

    private void assertAllEnabledLaborBindings(String stage,
            List<EnabledLaborBinding> bindings, Connection connection) throws Exception
    {
        assertExactReviewedBindingCardinality(bindings);
        for (EnabledLaborBinding binding : bindings)
        {
            assertThat(REVIEWED_LABOR_PLAN_IDS)
                    .as("reviewed enabled labor plan allow-list")
                    .contains(binding.planId());
            boolean exactV7 = binding.templateId() == 92L
                    && "20260721-v7".equals(binding.templateVersion())
                    && EXACT_V7_HASH.equalsIgnoreCase(binding.sourceFileHash());
            boolean reviewedPreMigrationV4 = "PRE".equals(stage)
                    && Set.of(36L, 49L).contains(binding.planId())
                    && binding.templateId() == 81L
                    && "20260718-v4-draft".equals(binding.templateVersion())
                    && BLOCKED_V4_HASH.equalsIgnoreCase(binding.sourceFileHash());
            assertThat(exactV7 || reviewedPreMigrationV4)
                    .as("enabled labor plan %s must use exact-v7%s", binding.planId(),
                            "PRE".equals(stage) ? " or one reviewed v4 source" : "")
                    .isTrue();

            OaSignPlanVersion version = loadVersion(connection, binding.versionId());
            assertThat(fingerprint.calculate(version))
                    .as("enabled plan %s formal persisted hash", binding.planId())
                    .isEqualTo(binding.versionHash());
        }
        if ("POST".equals(stage))
        {
            assertThat(bindings).allMatch(binding -> binding.templateId() == 92L
                    && "20260721-v7".equals(binding.templateVersion())
                    && EXACT_V7_HASH.equalsIgnoreCase(binding.sourceFileHash()));
        }
    }

    private void assertExactReviewedBindingCardinality(
            List<EnabledLaborBinding> bindings)
    {
        Map<Long, Long> counts = new TreeMap<>();
        bindings.forEach(binding -> counts.merge(binding.planId(), 1L, Long::sum));
        assertThat(counts.keySet())
                .as("enabled labor plan set")
                .containsExactlyInAnyOrderElementsOf(REVIEWED_LABOR_PLAN_IDS);
        assertThat(counts.values())
                .as("exactly one enabled labor binding per reviewed plan")
                .allMatch(count -> count == 1L);
        assertThat(bindings).hasSize(REVIEWED_LABOR_PLAN_IDS.size());
        assertThat(bindings)
                .as("each reviewed active plan has exactly one employee-visible handbook")
                .allMatch(binding -> binding.employeeVisibleHandbookCount() == 1L);
    }

    private Map<Long, List<ActivePlanTemplateCount>> loadActivePlanTemplateInventory(
            Connection connection) throws Exception
    {
        Map<Long, List<ActivePlanTemplateCount>> values = new TreeMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pv.plan_id, pvt.template_type, COUNT(*) AS total_count,
                       SUM(CASE WHEN UPPER(TRIM(COALESCE(
                               pvt.employee_visible, 'N'))) = 'Y' THEN 1 ELSE 0 END)
                           AS employee_visible_count
                  FROM oa_sign_plan_version pv
                  JOIN oa_sign_plan_version_template pvt
                    ON pvt.plan_version_id = pv.version_id
                 WHERE pv.publish_status = 'PUBLISHED'
                   AND pv.matching_status = 'ENABLED'
                   AND pv.plan_id IN (35, 36, 49, 66)
                 GROUP BY pv.plan_id, pvt.template_type
                 ORDER BY pv.plan_id, pvt.template_type
                """))
        {
            try (ResultSet result = statement.executeQuery())
            {
                while (result.next())
                {
                    values.computeIfAbsent(result.getLong("plan_id"), ignored ->
                            new ArrayList<>()).add(new ActivePlanTemplateCount(
                                    result.getString("template_type"),
                                    result.getLong("total_count"),
                                    result.getLong("employee_visible_count")));
                }
            }
        }
        values.replaceAll((ignored, counts) -> List.copyOf(counts));
        return Map.copyOf(values);
    }

    private void assertExactReviewedTemplateSets(
            Map<Long, List<ActivePlanTemplateCount>> inventory)
    {
        Map<Long, List<ActivePlanTemplateCount>> reviewedInventory = inventory.entrySet()
                .stream()
                .filter(entry -> REVIEWED_LABOR_PLAN_IDS.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, TreeMap::new));
        Set<String> base = Set.of(
                "ONBOARD_COMMITMENT",
                "ONBOARD_LABOR_CONTRACT",
                "ONBOARD_HANDBOOK_RECEIPT",
                "ONBOARD_SALARY_CONFIRM");
        Set<String> plan66 = new java.util.HashSet<>(base);
        plan66.add("ONBOARD_CONFIDENTIAL_NONCOMPETE");
        Map<Long, Set<String>> expected = Map.of(
                35L, base,
                36L, base,
                49L, base,
                66L, Set.copyOf(plan66));
        assertThat(reviewedInventory.keySet())
                .as("reviewed active plan template inventory")
                .containsExactlyInAnyOrderElementsOf(expected.keySet());
        for (Map.Entry<Long, Set<String>> reviewed : expected.entrySet())
        {
            List<ActivePlanTemplateCount> actual = reviewedInventory.get(reviewed.getKey());
            assertThat(actual).isNotNull();
            assertThat(actual).extracting(ActivePlanTemplateCount::templateType)
                    .as("plan %s reviewed template types", reviewed.getKey())
                    .containsExactlyInAnyOrderElementsOf(reviewed.getValue());
            assertThat(actual)
                    .as("plan %s has exactly one employee-visible row per reviewed type",
                            reviewed.getKey())
                    .allMatch(value -> value.totalCount() == 1L
                            && value.employeeVisibleCount() == 1L);
        }
    }

    private ActiveLegalEntitySummary loadActiveLegalEntitySummary(Connection connection)
            throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) AS active_count,
                       SUM(CASE WHEN NULLIF(TRIM(legal_entity_name), '') IS NULL
                                      OR NULLIF(TRIM(legal_representative), '') IS NULL
                                      OR NULLIF(TRIM(registered_address), '') IS NULL
                                THEN 1 ELSE 0 END) AS incomplete_count
                  FROM sys_legal_entity
                 WHERE status = '0'
                """))
        {
            try (ResultSet result = statement.executeQuery())
            {
                assertThat(result.next()).isTrue();
                return new ActiveLegalEntitySummary(result.getLong("active_count"),
                        result.getLong("incomplete_count"));
            }
        }
    }

    private void assertActiveLegalEntitySummary(ActiveLegalEntitySummary summary)
    {
        assertThat(summary.activeCount())
                .as("at least one active legal entity is available for signing")
                .isPositive();
        assertThat(summary.incompleteCount())
                .as("active legal entities have name, representative and registered address")
                .isZero();
    }

    private void assertExactV7RuntimeBytes(List<EnabledLaborBinding> bindings)
            throws Exception
    {
        String rootValue = requiredEnv("ERP_SIGN_PLACEMENT_FILE_ROOT");
        Path root = Path.of(rootValue).toAbsolutePath().normalize();
        assertThat(root).isDirectory();
        for (EnabledLaborBinding binding : bindings)
        {
            if (!EXACT_V7_HASH.equalsIgnoreCase(binding.sourceFileHash()))
            {
                continue;
            }
            String prefix = "/profile/";
            assertThat(binding.sourceFileUrl()).startsWith(prefix);
            Path source = root.resolve(binding.sourceFileUrl().substring(prefix.length()))
                    .normalize();
            assertThat(source).startsWith(root).isRegularFile();
            assertThat(sha256(source))
                    .as("enabled plan %s actual template bytes", binding.planId())
                    .isEqualTo(EXACT_V7_HASH);
        }
    }

    private List<InFlightRow> loadInFlightRows(Connection connection) throws Exception
    {
        List<InFlightRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(IN_FLIGHT_COVERAGE_SQL);
                ResultSet result = statement.executeQuery())
        {
            while (result.next())
            {
                rows.add(new InFlightRow(
                        result.getLong("package_id"), result.getLong("document_id"),
                        result.getString("package_status"),
                        result.getString("signing_sequence"),
                        result.getString("template_version"),
                        result.getString("source_hash"),
                        result.getString("document_policy_mode"),
                        result.getString("source_file_url_snapshot"),
                        result.getString("review_pdf_url"),
                        result.getString("review_pdf_hash"),
                        result.getString("employee_sign_required"),
                        result.getString("company_seal_required"),
                        result.getString("signature_position_json"),
                        result.getString("company_seal_position_json"),
                        nullableLong(result, "legal_entity_id_snapshot"),
                        result.getString("legal_entity_name_snapshot"),
                        result.getString("legal_representative_snapshot"),
                        result.getTimestamp("company_frozen_time"),
                        result.getString("seal_image_url_snapshot"),
                        result.getString("seal_image_hash_snapshot"),
                        result.getString("final_document_version"),
                        result.getString("final_document_root_hash"),
                        result.getTimestamp("final_generated_time"),
                        result.getString("final_confirmation_status"),
                        result.getString("signature_evidence_state"),
                        result.getString("final_candidate_state"),
                        result.getString("handbook_state")));
            }
        }
        return rows;
    }

    private List<InFlightGroup> aggregateInFlight(List<InFlightRow> rows,
            Set<Long> verifiedDocumentIds)
    {
        Map<InFlightKey, Long> counts = new TreeMap<>();
        for (InFlightRow row : rows)
        {
            InFlightKey key = new InFlightKey(row.packageStatus(), row.signingSequence(),
                    row.templateVersion(), row.sourceFileHash(),
                    jsonText(row.signaturePositionJson(), "mode"),
                    jsonText(row.companySealPositionJson(), "mode"),
                    jsonText(row.signaturePositionJson(), "signingSequencePolicy"),
                    jsonText(row.companySealPositionJson(), "signingSequencePolicy"),
                    inFlightEvidenceState(row), row.finalConfirmationStatus(),
                    row.signatureEvidenceState(), row.finalCandidateState(),
                    row.handbookState(), inFlightSupported(row)
                            && verifiedDocumentIds.contains(row.documentId()));
            counts.merge(key, 1L, Long::sum);
        }
        List<InFlightGroup> groups = new ArrayList<>();
        counts.forEach((key, count) -> groups.add(new InFlightGroup(
                key.packageStatus(), key.signingSequence(), key.templateVersion(),
                key.sourceFileHash(), key.signaturePolicyMode(), key.sealPolicyMode(),
                key.signatureSequencePolicy(), key.sealSequencePolicy(),
                key.evidenceState(), key.finalConfirmationStatus(),
                key.signatureEvidenceState(), key.finalCandidateState(),
                key.handbookState(), count, key.supported())));
        return groups;
    }

    private boolean inFlightSupported(InFlightRow row)
    {
        if (!"20260721-v7".equals(row.templateVersion())
                || !EXACT_V7_HASH.equalsIgnoreCase(row.sourceFileHash())
                || !"SNAPSHOT_V1".equals(row.documentPolicyMode())
                || !"COMPLETE".equals(inFlightEvidenceState(row))
                || !"INCLUDED".equals(row.handbookState()))
        {
            return false;
        }
        String signatureMode = jsonText(row.signaturePositionJson(), "mode");
        String sealMode = jsonText(row.companySealPositionJson(), "mode");
        if ("APPENDED_CONFIRMATION_PAGE".equals(signatureMode)
                && "APPENDED_CONFIRMATION_PAGE".equals(sealMode))
        {
            return "COMPANY_FIRST".equals(row.signingSequence())
                    && lifecycleEvidenceSupported(row);
        }
        if (!"PLACED_MULTI".equals(signatureMode) || !"PLACED_MULTI".equals(sealMode))
        {
            return false;
        }
        String expectedSequencePolicy = switch (row.signingSequence())
        {
            case "COMPANY_FIRST" -> "COMPANY_FIRST_STABLE_BODY_DISPLAY_EXPORT";
            case "SIGNATURE_FIRST" -> "SIGNATURE_FIRST_BODY_PLACEMENT";
            default -> null;
        };
        String signatureSequencePolicy = jsonText(
                row.signaturePositionJson(), "signingSequencePolicy");
        String sealSequencePolicy = jsonText(
                row.companySealPositionJson(), "signingSequencePolicy");
        if (expectedSequencePolicy == null
                || !expectedSequencePolicy.equals(signatureSequencePolicy)
                || !signatureSequencePolicy.equals(sealSequencePolicy))
        {
            return false;
        }
        try
        {
            OaSignPackage signPackage = new OaSignPackage();
            signPackage.setSigningSequence(row.signingSequence());
            OaSignPackageDocument document = new OaSignPackageDocument();
            document.setDocumentId(row.documentId());
            document.setTemplateType(OaSignTemplateType.ONBOARD_LABOR_CONTRACT);
            document.setTemplateVersionSnapshot(row.templateVersion());
            document.setReviewPdfHash(row.reviewPdfHash());
            document.setEmployeeSignRequired(row.employeeSignRequired());
            document.setCompanySealRequired(row.companySealRequired());
            document.setDocumentPolicyMode(row.documentPolicyMode());
            document.setSignaturePositionJson(row.signaturePositionJson());
            document.setCompanySealPositionJson(row.companySealPositionJson());
            OaSignPlacementPolicyService placement = new OaSignPlacementPolicyService();
            placement.assertSigningSequenceMatches(signPackage, document);
            OaSignedPdfService.PdfImagePlacement signatures =
                    placement.resolveFinalExportSignaturePlacement(document);
            OaSignedPdfService.PdfImagePlacement seal =
                    placement.resolveFinalExportCompanySealPlacement(document);
            Integer expectedPages = placement.resolveFinalExportExpectedBodyPageCount(document);
            return signatures != null && signatures.expanded().size() == 4
                    && seal != null && seal.expanded().size() == 1
                    && expectedPages != null && expectedPages > 0
                    && signatures.expanded().stream().allMatch(value ->
                            !value.getProtectedRegions().isEmpty())
                    && seal.expanded().stream().allMatch(value ->
                            !value.getProtectedRegions().isEmpty())
                    && placement.resolveDisplayTextPlacements(document).isEmpty()
                    && lifecycleEvidenceSupported(row);
        }
        catch (RuntimeException failure)
        {
            return false;
        }
    }

    private boolean lifecycleEvidenceSupported(InFlightRow row)
    {
        String status = row.packageStatus() == null ? ""
                : row.packageStatus().trim().toLowerCase(Locale.ROOT);
        if ("COMPANY_FIRST".equals(row.signingSequence()))
        {
            return Set.of("draft", "pending_sign", "part_viewed").contains(status)
                    && "PREPARED_NOT_SENT".equalsIgnoreCase(
                            String.valueOf(row.finalConfirmationStatus()))
                    && hasFrozenCompanyAndCandidate(row);
        }
        if (!"SIGNATURE_FIRST".equals(row.signingSequence()))
        {
            return false;
        }
        if (Set.of("draft", "pending_sign", "part_viewed").contains(status))
        {
            return isMissingLifecycleValue(row.finalConfirmationStatus())
                    && "INCOMPLETE".equals(row.signatureEvidenceState())
                    && "INCOMPLETE".equals(row.finalCandidateState());
        }
        if ("pending_company".equals(status))
        {
            if (!"COMPLETE".equals(row.signatureEvidenceState()))
            {
                return false;
            }
            if ("WAITING_COMPANY".equalsIgnoreCase(row.finalConfirmationStatus()))
            {
                return "INCOMPLETE".equals(row.finalCandidateState());
            }
            return "PREPARED_NOT_SENT".equalsIgnoreCase(row.finalConfirmationStatus())
                    && hasFrozenCompanyAndCandidate(row);
        }
        return "pending_final_confirm".equals(status)
                && "COMPLETE".equals(row.signatureEvidenceState())
                && "PENDING".equalsIgnoreCase(row.finalConfirmationStatus())
                && hasFrozenCompanyAndCandidate(row);
    }

    private boolean hasFrozenCompanyAndCandidate(InFlightRow row)
    {
        return "COMPLETE".equals(row.finalCandidateState())
                && row.legalEntityIdSnapshot() != null
                && isNotBlank(row.legalEntityNameSnapshot())
                && isNotBlank(row.legalRepresentativeSnapshot())
                && row.companyFrozenTime() != null
                && isNotBlank(row.sealImageUrlSnapshot())
                && isSha256(row.sealImageHashSnapshot())
                && isNotBlank(row.finalDocumentVersion())
                && isSha256(row.finalDocumentRootHash())
                && row.finalGeneratedTime() != null;
    }

    private boolean isMissingLifecycleValue(String value)
    {
        return value == null || value.isBlank() || "<MISSING>".equalsIgnoreCase(value);
    }

    private String inFlightEvidenceState(InFlightRow row)
    {
        return isNotBlank(row.sourceFileUrlSnapshot())
                && isNotBlank(row.reviewPdfUrl())
                && isSha256(row.reviewPdfHash())
                && "Y".equalsIgnoreCase(row.employeeSignRequired())
                && "Y".equalsIgnoreCase(row.companySealRequired())
                        ? "COMPLETE" : "INCOMPLETE";
    }

    /**
     * SQL deliberately remains a PII-free aggregate pre-filter.  This application gate is the
     * authority: it opens the exact source/review/final-candidate files, re-runs the approved
     * anchor resolver and validates the immutable candidate root without advancing a package.
     */
    private InFlightDryRunEvidence verifyEveryInFlightLaborContinuation(
            Connection connection, List<InFlightRow> rows) throws Exception
    {
        BusinessFingerprints before = businessFingerprints(connection);
        if (rows.isEmpty())
        {
            return new InFlightDryRunEvidence(Set.of(), 0, before,
                    businessFingerprints(connection));
        }

        Path fileRoot = Path.of(requiredEnv("ERP_SIGN_PLACEMENT_FILE_ROOT"))
                .toAbsolutePath().normalize();
        Path dryRunTemp = Path.of(requiredEnv("ERP_SIGN_PLACEMENT_DRY_RUN_TEMP"))
                .toAbsolutePath().normalize();
        assertThat(fileRoot).as("in-flight production file root").isDirectory();
        Files.createDirectories(dryRunTemp);
        ReadOnlyFileContext files = readOnlyFileContext(fileRoot, dryRunTemp);
        OaSignedPdfService signedPdfService = new OaSignedPdfService(files.storage());
        OaSignPackageFileIntegrity integrity = new OaSignPackageFileIntegrity(
                files.documentService(), signedPdfService);
        OaSignPlacementPolicyService placement = new OaSignPlacementPolicyService();
        OaSignLaborAnchorPlacementResolver historicalResolver =
                new OaSignLaborAnchorPlacementResolver();

        Map<Long, List<InFlightRow>> byPackage = new TreeMap<>();
        rows.forEach(row -> byPackage.computeIfAbsent(row.packageId(), ignored ->
                new ArrayList<>()).add(row));
        Set<Long> verified = new HashSet<>();
        int actualFileChecks = 0;
        for (Map.Entry<Long, List<InFlightRow>> entry : byPackage.entrySet())
        {
            OaSignPackage signPackage = loadPackageForDryRun(connection, entry.getKey());
            List<OaSignPackageDocument> allDocuments = loadDocumentsForDryRun(
                    connection, entry.getKey());
            List<OaSignPackageDocument> visibleDocuments = allDocuments.stream()
                    .filter(document -> "Y".equalsIgnoreCase(document.getEmployeeVisible()))
                    .toList();
            assertThat(visibleDocuments).isNotEmpty();
            for (InFlightRow row : entry.getValue())
            {
                assertThat(inFlightSupported(row))
                        .as("aggregate continuation preconditions for document %s",
                                row.documentId())
                        .isTrue();
                OaSignPackageDocument document = visibleDocuments.stream()
                        .filter(value -> Objects.equals(value.getDocumentId(), row.documentId()))
                        .findFirst().orElseThrow(() -> new AssertionError(
                                "labor document missing from employee-visible package set"));
                Path source = resolveReadOnlyFile(files, row.sourceFileUrlSnapshot());
                assertThat(sha256(source)).as("actual exact-v7 source bytes")
                        .isEqualToIgnoringCase(row.sourceFileHash())
                        .isEqualTo(EXACT_V7_HASH);
                Path review = resolveReadOnlyFile(files, row.reviewPdfUrl());
                assertThat(sha256(review)).as("actual generated review PDF bytes")
                        .isEqualToIgnoringCase(row.reviewPdfHash());
                actualFileChecks += 2;

                String signatureMode = jsonText(row.signaturePositionJson(), "mode");
                if ("APPENDED_CONFIRMATION_PAGE".equals(signatureMode))
                {
                    verifyLegacyCompanyFirstContinuation(signPackage, visibleDocuments,
                            review, row, historicalResolver, files, integrity);
                    actualFileChecks += visibleDocuments.size() + 1;
                }
                else
                {
                    verifyFrozenMultiContinuation(signPackage, document, review, row,
                            placement, visibleDocuments, files, integrity);
                    actualFileChecks++;
                }
                verified.add(row.documentId());
            }
        }
        BusinessFingerprints after = businessFingerprints(connection);
        assertThat(after).as("in-flight continuation verifier is database read-only")
                .isEqualTo(before);
        return new InFlightDryRunEvidence(Set.copyOf(verified), actualFileChecks,
                before, after);
    }

    private void verifyLegacyCompanyFirstContinuation(OaSignPackage signPackage,
            List<OaSignPackageDocument> visibleDocuments, Path review, InFlightRow row,
            OaSignLaborAnchorPlacementResolver resolver, ReadOnlyFileContext files,
            OaSignPackageFileIntegrity integrity) throws Exception
    {
        assertThat(signPackage.getSigningSequence()).isEqualTo("COMPANY_FIRST");
        assertThat(signPackage.getLegalEntityIdSnapshot()).isNotNull();
        assertThat(signPackage.getLegalEntityNameSnapshot()).isNotBlank();
        assertThat(signPackage.getLegalRepresentativeSnapshot()).isNotBlank();
        assertThat(signPackage.getCompanyFrozenTime()).isNotNull();
        assertThat(signPackage.getSealImageUrlSnapshot()).isNotBlank();
        assertThat(signPackage.getSealImageHashSnapshot())
                .matches("[0-9a-fA-F]{64}");
        assertThat(signPackage.getFinalDocumentVersion()).isNotBlank();
        assertThat(signPackage.getFinalDocumentRootHash())
                .matches("[0-9a-fA-F]{64}");
        assertThat(signPackage.getFinalGeneratedTime()).isNotNull();

        // Legacy exact-v7 reviews may predate in-body representative rendering.  The
        // historical resolver therefore verifies the same unique anchors/protected regions
        // while producing audited display-only text repairs; it never mutates this package.
        OaSignLaborPlacementProfileRegistry.PlacementProfile profile = resolver.resolve(
                review, false, null, null, true);
        assertHistoricalGeometry(profile);
        assertThat(profile.textOverlays().stream()
                .map(OaSignLaborPlacementProfileRegistry.TextRect::field).toList())
                .containsExactlyInAnyOrder("companyLegalRepresentative",
                        "attachmentHandbookMark", "archiveEvidenceNotice");
        verifyFrozenCompanyAndCandidate(signPackage, visibleDocuments, files, integrity);
        assertThat(row.finalCandidateState()).isEqualTo("COMPLETE");
    }

    private void verifyFrozenMultiContinuation(OaSignPackage signPackage,
            OaSignPackageDocument document, Path review, InFlightRow row,
            OaSignPlacementPolicyService placement,
            List<OaSignPackageDocument> visibleDocuments, ReadOnlyFileContext files,
            OaSignPackageFileIntegrity integrity) throws Exception
    {
        OaSignPlacementPolicyService.GeneratedPlacementPolicies expected =
                placement.validateAndFreezeGeneratedPositions(
                        OaSignTemplateType.ONBOARD_LABOR_CONTRACT,
                        row.templateVersion(), row.sourceFileHash(),
                        OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE,
                        OaSignPlacementPolicyService.APPENDED_CONFIRMATION_PAGE,
                        review, row.reviewPdfHash(), true, true,
                        signPackage.getLegalRepresentativeSnapshot(), true,
                        signPackage.getSigningSequence());
        assertThat(objectMapper.readTree(row.signaturePositionJson()))
                .as("frozen signature policy equals actual anchor resolution")
                .isEqualTo(objectMapper.readTree(expected.signaturePositionJson()));
        assertThat(objectMapper.readTree(row.companySealPositionJson()))
                .as("frozen seal policy equals actual anchor resolution")
                .isEqualTo(objectMapper.readTree(expected.companySealPositionJson()));
        placement.assertSigningSequenceMatches(signPackage, document);
        OaSignedPdfService.PdfImagePlacement signatures =
                placement.resolveFinalExportSignaturePlacement(document);
        OaSignedPdfService.PdfImagePlacement seal =
                placement.resolveFinalExportCompanySealPlacement(document);
        Integer expectedPages = placement.resolveFinalExportExpectedBodyPageCount(document);
        assertThat(signatures.expanded()).hasSize(4);
        assertThat(seal.expanded()).hasSize(1);
        assertProtectedGeometry(signatures.expanded(), expectedPages);
        assertProtectedGeometry(seal.expanded(), expectedPages);
        try (var pdf = Loader.loadPDF(review.toFile()))
        {
            assertThat(pdf.getNumberOfPages()).isEqualTo(expectedPages);
        }
        assertThat(placement.resolveDisplayTextPlacements(document)).isEmpty();
        verifyLifecycleFiles(signPackage, visibleDocuments, files, integrity);
    }

    private void verifyLifecycleFiles(OaSignPackage signPackage,
            List<OaSignPackageDocument> visibleDocuments, ReadOnlyFileContext files,
            OaSignPackageFileIntegrity integrity) throws Exception
    {
        String sequence = signPackage.getSigningSequence();
        String status = signPackage.getStatus() == null ? ""
                : signPackage.getStatus().trim().toLowerCase(Locale.ROOT);
        if ("COMPANY_FIRST".equals(sequence))
        {
            verifyFrozenCompanyAndCandidate(signPackage, visibleDocuments, files, integrity);
            return;
        }
        if (!"SIGNATURE_FIRST".equals(sequence))
        {
            throw new AssertionError("unknown in-flight signing sequence");
        }
        if (Set.of("draft", "pending_sign", "part_viewed").contains(status))
        {
            assertThat(signPackage.getSignatureSampleFileUrl()).isBlank();
            assertThat(signPackage.getSignatureSampleHash()).isBlank();
            assertThat(signPackage.getFinalDocumentVersion()).isBlank();
            assertThat(signPackage.getFinalDocumentRootHash()).isBlank();
            assertThat(signPackage.getFinalGeneratedTime()).isNull();
            assertThat(signPackage.getFinalConfirmationStatus()).isBlank();
            return;
        }
        verifyFrozenSignatureEvidence(signPackage, visibleDocuments, files);
        if ("pending_company".equals(status)
                && "WAITING_COMPANY".equalsIgnoreCase(
                        signPackage.getFinalConfirmationStatus()))
        {
            assertThat(signPackage.getFinalDocumentVersion()).isBlank();
            assertThat(signPackage.getFinalDocumentRootHash()).isBlank();
            assertThat(signPackage.getFinalGeneratedTime()).isNull();
            return;
        }
        if (("pending_company".equals(status)
                    && "PREPARED_NOT_SENT".equalsIgnoreCase(
                            signPackage.getFinalConfirmationStatus()))
                || ("pending_final_confirm".equals(status)
                    && "PENDING".equalsIgnoreCase(
                            signPackage.getFinalConfirmationStatus())))
        {
            verifyFrozenCompanyAndCandidate(signPackage, visibleDocuments, files, integrity);
            return;
        }
        throw new AssertionError("unsupported signature-first lifecycle evidence combination");
    }

    private void verifyFrozenSignatureEvidence(OaSignPackage signPackage,
            List<OaSignPackageDocument> visibleDocuments, ReadOnlyFileContext files)
            throws Exception
    {
        if (isNotBlank(signPackage.getSignatureSampleFileUrl())
                && isSha256(signPackage.getSignatureSampleHash())
                && signPackage.getSignatureSampleTime() != null)
        {
            assertThat(sha256(resolveReadOnlyFile(
                    files, signPackage.getSignatureSampleFileUrl())))
                    .isEqualToIgnoringCase(signPackage.getSignatureSampleHash());
            return;
        }
        assertThat(signPackage.getInitialSignedTime() == null
                ? signPackage.getSignedTime() : signPackage.getInitialSignedTime())
                .as("signature-first employee signature time")
                .isNotNull();
        List<OaSignPackageDocument> required = visibleDocuments.stream()
                .filter(document -> "Y".equalsIgnoreCase(
                        document.getEmployeeSignRequired())).toList();
        assertThat(required).isNotEmpty();
        for (OaSignPackageDocument document : required)
        {
            assertThat(document.getSignatureFileUrl()).isNotBlank();
            assertThat(document.getSignatureHash()).matches("[0-9a-fA-F]{64}");
            assertThat(sha256(resolveReadOnlyFile(files, document.getSignatureFileUrl())))
                    .isEqualToIgnoringCase(document.getSignatureHash());
        }
    }

    private void verifyFrozenCompanyAndCandidate(OaSignPackage signPackage,
            List<OaSignPackageDocument> visibleDocuments, ReadOnlyFileContext files,
            OaSignPackageFileIntegrity integrity)
    {
        assertThat(signPackage.getLegalEntityIdSnapshot()).isNotNull();
        assertThat(signPackage.getLegalEntityNameSnapshot()).isNotBlank();
        assertThat(signPackage.getLegalRepresentativeSnapshot()).isNotBlank();
        assertThat(signPackage.getCompanyFrozenTime()).isNotNull();
        assertThat(signPackage.getSealImageUrlSnapshot()).isNotBlank();
        assertThat(signPackage.getSealImageHashSnapshot()).matches("[0-9a-fA-F]{64}");
        assertThat(signPackage.getFinalDocumentVersion()).isNotBlank();
        assertThat(signPackage.getFinalDocumentRootHash()).matches("[0-9a-fA-F]{64}");
        assertThat(signPackage.getFinalGeneratedTime()).isNotNull();
        byte[] sealBytes = files.documentService().readConfiguredFileBytes(
                signPackage.getSealImageUrlSnapshot());
        assertThat(integrity.sha256(sealBytes)).as("frozen company seal bytes")
                .isEqualToIgnoringCase(signPackage.getSealImageHashSnapshot());
        Map<Long, String> hashes = integrity.validateFinalDocuments(
                signPackage, visibleDocuments);
        assertThat(integrity.finalDocumentRootHashFromHashes(hashes))
                .as("all employee-visible frozen final candidates")
                .isEqualToIgnoringCase(signPackage.getFinalDocumentRootHash());
    }

    private void assertHistoricalGeometry(
            OaSignLaborPlacementProfileRegistry.PlacementProfile profile)
    {
        assertThat(profile.signaturePlacements()).hasSize(4);
        assertThat(profile.sealPlacements()).hasSize(1);
        assertThat(profile.protectedRegions()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(profile.expectedBodyPageCount()).isPositive();
        for (OaSignLaborPlacementProfileRegistry.Rect target :
                java.util.stream.Stream.concat(profile.signaturePlacements().stream(),
                        profile.sealPlacements().stream()).toList())
        {
            assertThat(profile.protectedRegions().stream()
                    .noneMatch(region -> intersects(target.pageNumber(), target.x(), target.y(),
                            target.width(), target.height(), region.pageNumber(), region.x(),
                            region.y(), region.width(), region.height())))
                    .as("historical anchor target does not intersect protected identity/date")
                    .isTrue();
        }
    }

    private void assertProtectedGeometry(
            List<OaSignedPdfService.PdfImagePlacement> placements, Integer expectedPages)
    {
        assertThat(expectedPages).isPositive();
        for (OaSignedPdfService.PdfImagePlacement target : placements)
        {
            assertThat(target.getPageNumber()).isBetween(1, expectedPages);
            assertThat(target.getProtectedRegions()).hasSizeGreaterThanOrEqualTo(3);
            assertThat(target.getProtectedRegions().stream().noneMatch(region ->
                    intersects(target.getPageNumber(), target.getX(), target.getY(),
                            target.getWidth(), target.getHeight(), region.pageNumber(),
                            region.x(), region.y(), region.width(), region.height())))
                    .isTrue();
        }
    }

    private boolean intersects(int leftPage, float leftX, float leftY,
            float leftWidth, float leftHeight, int rightPage, float rightX, float rightY,
            float rightWidth, float rightHeight)
    {
        return leftPage == rightPage
                && Math.min(leftX + leftWidth, rightX + rightWidth)
                        > Math.max(leftX, rightX)
                && Math.min(leftY + leftHeight, rightY + rightHeight)
                        > Math.max(leftY, rightY);
    }

    private String jsonText(String json, String field)
    {
        if (!isNotBlank(json))
        {
            return "<MISSING>";
        }
        try
        {
            String value = objectMapper.readTree(json).path(field).asText(null);
            return isNotBlank(value) ? value.trim().toUpperCase(Locale.ROOT) : "<MISSING>";
        }
        catch (Exception failure)
        {
            return "<INVALID>";
        }
    }

    private boolean isNotBlank(String value)
    {
        return value != null && !value.isBlank();
    }

    private boolean isSha256(String value)
    {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    private List<CoverageRow> loadSignedCoverageRows(Connection connection) throws Exception
    {
        List<CoverageRow> rows = new ArrayList<>();
        OaSignLaborPlacementProfileRegistry registry =
                new OaSignLaborPlacementProfileRegistry();
        try (PreparedStatement statement = connection.prepareStatement(SIGNED_COVERAGE_SQL);
                ResultSet result = statement.executeQuery())
        {
            while (result.next())
            {
                Date confirmedTime = result.getTimestamp("final_confirmed_time");
                String frozenRepresentative = result.getString(
                        "legal_representative_snapshot");
                String representativeState;
                if (frozenRepresentative != null && !frozenRepresentative.isBlank())
                {
                    representativeState = "FROZEN";
                }
                else
                {
                    var repair = registry.historicalRepresentativeRepair(
                            nullableLong(result, "legal_entity_id_snapshot"),
                            result.getString("legal_entity_name_snapshot"), confirmedTime);
                    representativeState = repair == null ? "MISSING" : "AUDITED_REPAIR";
                }
                rows.add(new CoverageRow(
                        result.getLong("package_id"), result.getLong("document_id"),
                        result.getString("template_version"),
                        result.getString("source_hash"), result.getString("policy_mode"),
                        result.getString("evidence_state"), representativeState,
                        result.getString("handbook_state"), confirmedTime));
            }
        }
        return rows;
    }

    private List<CoverageGroup> aggregateCoverage(List<CoverageRow> rows,
            Set<Long> verifiedDocumentIds)
    {
        Map<CoverageKey, Long> counts = new TreeMap<>();
        Map<CoverageKey, Boolean> supported = new HashMap<>();
        for (CoverageRow row : rows)
        {
            CoverageKey key = new CoverageKey(row.templateVersion(), row.sourceFileHash(),
                    row.documentPolicyMode(), row.evidenceState(),
                    row.legalRepresentativeState(), row.handbookState());
            counts.merge(key, 1L, Long::sum);
            supported.merge(key, preliminarySupported(row)
                    && verifiedDocumentIds.contains(row.documentId()), Boolean::logicalAnd);
        }
        List<CoverageGroup> groups = new ArrayList<>();
        counts.forEach((key, count) -> groups.add(new CoverageGroup(
                key.templateVersion(), key.sourceFileHash(), key.documentPolicyMode(),
                key.evidenceState(), key.legalRepresentativeState(), key.handbookState(),
                count, Boolean.TRUE.equals(supported.get(key)))));
        return groups;
    }

    private boolean preliminarySupported(CoverageRow row)
    {
        return "20260721-v7".equals(row.templateVersion())
                && EXACT_V7_HASH.equalsIgnoreCase(row.sourceFileHash())
                && "COMPLETE".equals(row.evidenceState())
                && List.of("FROZEN", "AUDITED_REPAIR")
                        .contains(row.legalRepresentativeState())
                && "INCLUDED".equals(row.handbookState())
                && List.of("APPENDED_CONFIRMATION_PAGE", "PLACED_MULTI")
                        .contains(row.documentPolicyMode());
    }

    private CoverageRow coverageRow(String policy, String evidence,
            String representative, String handbook, Date confirmed)
    {
        return new CoverageRow(1L, 2L, "20260721-v7", EXACT_V7_HASH,
                policy, evidence, representative, handbook, confirmed);
    }

    private InFlightRow inFlightRow(String signingSequence, String signaturePolicy,
            String sealPolicy)
    {
        return new InFlightRow(11L, 12L, "pending_sign", signingSequence,
                "20260721-v7", EXACT_V7_HASH, "SNAPSHOT_V1",
                "/profile/exact-v7.docx", "/profile/review.pdf", "a".repeat(64),
                "Y", "Y", signaturePolicy, sealPolicy,
                21L, "冻结法律主体", "冻结代表", new Date(1_700_000_000_000L),
                "/profile/company-seal.png", "b".repeat(64), "final-v1",
                "c".repeat(64), new Date(1_700_000_100_000L), "PREPARED_NOT_SENT",
                "COMPLETE", "COMPLETE", "INCLUDED");
    }

    private String appendedPolicy()
    {
        return "{\"mode\":\"APPENDED_CONFIRMATION_PAGE\"}";
    }

    private String multiSignaturePolicy(String signingSequencePolicy)
    {
        return multiPolicy(signingSequencePolicy, 4);
    }

    private String multiSealPolicy(String signingSequencePolicy)
    {
        return multiPolicy(signingSequencePolicy, 1);
    }

    private String multiPolicy(String signingSequencePolicy, int placementCount)
    {
        String sequence = signingSequencePolicy == null ? ""
                : ",\"signingSequencePolicy\":\"" + signingSequencePolicy + "\"";
        StringBuilder placements = new StringBuilder();
        for (int index = 0; index < placementCount; index++)
        {
            if (index > 0)
            {
                placements.append(',');
            }
            placements.append("{\"pageNumber\":").append(index + 1)
                    .append(",\"x\":10,\"y\":10,\"width\":20,\"height\":10}");
        }
        return "{\"mode\":\"PLACED_MULTI\","
                + "\"templateType\":\"ONBOARD_LABOR_CONTRACT\","
                + "\"templateVersion\":\"20260721-v7\","
                + "\"reviewPdfHash\":\"" + "a".repeat(64) + "\","
                + "\"placementConfigVersion\":\"test-v1\","
                + "\"profileId\":\"labor-v7-anchor-relative-v1\","
                + "\"expectedBodyPageCount\":18" + sequence + ","
                + "\"placements\":[" + placements + "],"
                + "\"protectedRegions\":[{\"pageNumber\":1,\"x\":100,"
                + "\"y\":100,\"width\":20,\"height\":10}]}";
    }

    private DryRunEvidence verifyEverySignedLaborExport(Connection connection,
            List<CoverageRow> rows) throws Exception
    {
        BusinessFingerprints before = businessFingerprints(connection);
        if (rows.isEmpty())
        {
            return new DryRunEvidence(Set.of(), 0, before,
                    businessFingerprints(connection), 0);
        }

        Path fileRoot = Path.of(requiredEnv("ERP_SIGN_PLACEMENT_FILE_ROOT"))
                .toAbsolutePath().normalize();
        Path dryRunTemp = Path.of(requiredEnv("ERP_SIGN_PLACEMENT_DRY_RUN_TEMP"))
                .toAbsolutePath().normalize();
        assertThat(fileRoot).as("production file root").isDirectory();
        Files.createDirectories(dryRunTemp);

        Map<Long, List<CoverageRow>> byPackage = new TreeMap<>();
        rows.forEach(row -> byPackage.computeIfAbsent(
                row.packageId(), ignored -> new ArrayList<>()).add(row));
        Set<Long> verified = new HashSet<>();
        List<Path> derivedPaths = new ArrayList<>();
        int attempts = 0;
        for (Map.Entry<Long, List<CoverageRow>> entry : byPackage.entrySet())
        {
            OaSignPackage signPackage = loadPackageForDryRun(connection, entry.getKey());
            List<OaSignPackageDocument> documents = loadDocumentsForDryRun(
                    connection, entry.getKey());
            OaSignFinalConfirmation confirmation = loadConfirmationForDryRun(
                    connection, signPackage.getPackageId(),
                    signPackage.getFinalDocumentVersion());
            List<OaSignFinalConfirmationDocument> confirmationDocuments =
                    loadConfirmationDocumentsForDryRun(connection,
                            signPackage.getPackageId(),
                            signPackage.getFinalDocumentVersion());
            OaSignPackageServiceImpl service = formalExportService(
                    signPackage, documents, confirmation, confirmationDocuments,
                    fileRoot, dryRunTemp);
            Map<Long, OaSignPackageDocument> documentsById = new HashMap<>();
            documents.forEach(document -> documentsById.put(
                    document.getDocumentId(), document));

            for (CoverageRow row : entry.getValue())
            {
                assertThat(preliminarySupported(row))
                        .as("aggregate preconditions for document %s", row.documentId())
                        .isTrue();
                OaSignPackageDocument target = documentsById.get(row.documentId());
                assertThat(target).as("labor document %s", row.documentId()).isNotNull();
                String firstHash = null;
                for (int attempt = 0; attempt < 2; attempt++)
                {
                    OaSignPackageFile exported = service
                            .dryRunConfirmedFinalDocumentExport(signPackage, target);
                    attempts++;
                    Path output = exported.getPath().toAbsolutePath().normalize();
                    try
                    {
                        assertThat(output).isRegularFile();
                        String outputHash = sha256(output);
                        if (firstHash == null)
                        {
                            firstHash = outputHash;
                        }
                        else
                        {
                            assertThat(outputHash)
                                    .as("sequential formal export bytes for document %s",
                                            row.documentId())
                                    .isEqualTo(firstHash);
                        }
                        try (var pdf = Loader.loadPDF(output.toFile()))
                        {
                            assertThat(pdf.getNumberOfPages())
                                    .as("formal export page count for document %s",
                                            row.documentId())
                                    .isPositive();
                        }
                    }
                    finally
                    {
                        if (exported.isDeleteAfterStreaming())
                        {
                            derivedPaths.add(output);
                            Files.deleteIfExists(output);
                        }
                    }
                }
                verified.add(row.documentId());
            }
        }
        long remaining = derivedPaths.stream().filter(Files::exists).count();
        BusinessFingerprints after = businessFingerprints(connection);
        assertThat(after).as("read-only formal export database fingerprints")
                .isEqualTo(before);
        assertThat(remaining).as("disposable formal export files").isZero();
        return new DryRunEvidence(Set.copyOf(verified), attempts, before, after, remaining);
    }

    private OaSignPackageServiceImpl formalExportService(OaSignPackage signPackage,
            List<OaSignPackageDocument> documents, OaSignFinalConfirmation confirmation,
            List<OaSignFinalConfirmationDocument> confirmationDocuments,
            Path fileRoot, Path dryRunTemp)
    {
        OaSignFileProperties properties = new OaSignFileProperties();
        String storageRoot = System.getenv("ERP_SIGN_PLACEMENT_STORAGE_ROOT");
        properties.getStorage().setRootPath(storageRoot == null || storageRoot.isBlank()
                ? dryRunTemp.resolve("unused-storage").toString() : storageRoot);
        properties.getStorage().setTempPath(dryRunTemp.resolve("storage-temp").toString());
        String managedPrefix = System.getenv().getOrDefault(
                "ERP_SIGN_PLACEMENT_STORAGE_PUBLIC_PREFIX",
                "/profile/private/sign-package");
        properties.getStorage().setPublicPrefix(managedPrefix);
        OaSignFileStorageService storage = new OaSignFileStorageService(properties);

        OaSignDocumentService documentService = new OaSignDocumentService();
        ReflectionTestUtils.setField(documentService, "localFilePath", fileRoot.toString());
        ReflectionTestUtils.setField(documentService, "localFilePrefix",
                System.getenv().getOrDefault("ERP_SIGN_PLACEMENT_FILE_PREFIX", "/profile"));
        ReflectionTestUtils.setField(documentService, "localPublicFilePrefix",
                System.getenv().getOrDefault(
                        "ERP_SIGN_PLACEMENT_PUBLIC_FILE_PREFIX", "/file/public"));
        ReflectionTestUtils.setField(documentService, "localFileGatewayPrefix",
                System.getenv().getOrDefault(
                        "ERP_SIGN_PLACEMENT_FILE_GATEWAY_PREFIX", "/prod-api"));
        if (storageRoot != null && !storageRoot.isBlank())
        {
            ReflectionTestUtils.setField(documentService, "fileStorageService", storage);
        }

        OaSignPackageDocumentMapper documentMapper =
                mock(OaSignPackageDocumentMapper.class);
        OaSignFinalConfirmationMapper confirmationMapper =
                mock(OaSignFinalConfirmationMapper.class);
        when(documentMapper.selectDocumentsByPackageId(signPackage.getPackageId()))
                .thenReturn(documents);
        when(confirmationMapper.selectByPackageAndVersion(signPackage.getPackageId(),
                signPackage.getFinalDocumentVersion())).thenReturn(confirmation);
        when(confirmationMapper.selectDocumentsByPackageAndVersion(
                signPackage.getPackageId(), signPackage.getFinalDocumentVersion()))
                .thenReturn(confirmationDocuments);

        OaSignPackageServiceImpl service = new OaSignPackageServiceImpl();
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "finalConfirmationMapper", confirmationMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        ReflectionTestUtils.setField(service, "fileStorageService", storage);
        ReflectionTestUtils.setField(service, "signedPdfService",
                new OaSignedPdfService(storage));
        ReflectionTestUtils.setField(service, "placementPolicyService",
                new OaSignPlacementPolicyService());
        return service;
    }

    private ReadOnlyFileContext readOnlyFileContext(Path fileRoot, Path dryRunTemp)
    {
        OaSignFileProperties properties = new OaSignFileProperties();
        String storageRoot = System.getenv("ERP_SIGN_PLACEMENT_STORAGE_ROOT");
        properties.getStorage().setRootPath(storageRoot == null || storageRoot.isBlank()
                ? dryRunTemp.resolve("unused-storage").toString() : storageRoot);
        properties.getStorage().setTempPath(dryRunTemp.resolve("storage-temp").toString());
        properties.getStorage().setPublicPrefix(System.getenv().getOrDefault(
                "ERP_SIGN_PLACEMENT_STORAGE_PUBLIC_PREFIX",
                "/profile/private/sign-package"));
        OaSignFileStorageService storage = new OaSignFileStorageService(properties);
        OaSignDocumentService documentService = new OaSignDocumentService();
        ReflectionTestUtils.setField(documentService, "localFilePath", fileRoot.toString());
        ReflectionTestUtils.setField(documentService, "localFilePrefix",
                System.getenv().getOrDefault("ERP_SIGN_PLACEMENT_FILE_PREFIX", "/profile"));
        ReflectionTestUtils.setField(documentService, "localPublicFilePrefix",
                System.getenv().getOrDefault(
                        "ERP_SIGN_PLACEMENT_PUBLIC_FILE_PREFIX", "/file/public"));
        ReflectionTestUtils.setField(documentService, "localFileGatewayPrefix",
                System.getenv().getOrDefault(
                        "ERP_SIGN_PLACEMENT_FILE_GATEWAY_PREFIX", "/prod-api"));
        if (storageRoot != null && !storageRoot.isBlank())
        {
            ReflectionTestUtils.setField(documentService, "fileStorageService", storage);
        }
        return new ReadOnlyFileContext(documentService, storage, fileRoot);
    }

    private Path resolveReadOnlyFile(ReadOnlyFileContext files, String fileUrl)
            throws Exception
    {
        assertThat(fileUrl).isNotBlank();
        if (files.storage().isManagedPublicUrl(fileUrl))
        {
            Path managed = files.storage().resolveAuthorizedPublicUrl(fileUrl)
                    .toAbsolutePath().normalize();
            assertThat(managed).isRegularFile();
            return managed;
        }
        try
        {
            return files.documentService().resolveGeneratedSignPackageFile(fileUrl)
                    .toAbsolutePath().normalize();
        }
        catch (RuntimeException notGeneratedPackageFile)
        {
            String path = fileUrl;
            if (path.startsWith("http://") || path.startsWith("https://"))
            {
                path = URI.create(path).getPath();
            }
            String gatewayPrefix = System.getenv().getOrDefault(
                    "ERP_SIGN_PLACEMENT_FILE_GATEWAY_PREFIX", "/prod-api");
            if (path.startsWith(gatewayPrefix + "/"))
            {
                path = path.substring(gatewayPrefix.length());
            }
            String filePrefix = System.getenv().getOrDefault(
                    "ERP_SIGN_PLACEMENT_FILE_PREFIX", "/profile");
            String publicPrefix = System.getenv().getOrDefault(
                    "ERP_SIGN_PLACEMENT_PUBLIC_FILE_PREFIX", "/file/public");
            Path candidate;
            if (path.startsWith(filePrefix + "/"))
            {
                candidate = files.fileRoot().resolve(
                        path.substring(filePrefix.length() + 1)).normalize();
            }
            else if (path.startsWith(publicPrefix + "/"))
            {
                candidate = files.fileRoot().resolve("public").resolve(
                        path.substring(publicPrefix.length() + 1)).normalize();
            }
            else
            {
                throw new AssertionError("read-only verifier rejected unmanaged file URL");
            }
            assertThat(candidate).startsWith(files.fileRoot()).isRegularFile();
            return candidate;
        }
    }

    private OaSignPackage loadPackageForDryRun(Connection connection, long packageId)
            throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT package_id, package_no, employee_id, employee_name_snapshot,
                       legal_entity_id_snapshot, legal_entity_name_snapshot,
                       legal_representative_snapshot, company_frozen_time,
                       status, signed_time,
                       initial_signed_time, signing_sequence, signature_sample_file_url,
                       signature_sample_hash, signature_sample_time,
                       seal_image_url_snapshot, seal_image_hash_snapshot,
                       final_document_version, final_document_root_hash,
                       final_generated_time, final_confirmed_time,
                       final_confirmation_status, final_archive_root_hash,
                       final_evidence_generated_time
                  FROM oa_sign_package WHERE package_id = ?
                """))
        {
            statement.setLong(1, packageId);
            try (ResultSet result = statement.executeQuery())
            {
                assertThat(result.next()).isTrue();
                OaSignPackage value = new OaSignPackage();
                value.setPackageId(result.getLong("package_id"));
                value.setPackageNo(result.getString("package_no"));
                value.setEmployeeId(nullableLong(result, "employee_id"));
                value.setEmployeeNameSnapshot(result.getString("employee_name_snapshot"));
                value.setLegalEntityIdSnapshot(nullableLong(
                        result, "legal_entity_id_snapshot"));
                value.setLegalEntityNameSnapshot(
                        result.getString("legal_entity_name_snapshot"));
                value.setLegalRepresentativeSnapshot(
                        result.getString("legal_representative_snapshot"));
                value.setCompanyFrozenTime(result.getTimestamp("company_frozen_time"));
                value.setStatus(result.getString("status"));
                value.setSignedTime(result.getTimestamp("signed_time"));
                value.setInitialSignedTime(result.getTimestamp("initial_signed_time"));
                value.setSigningSequence(result.getString("signing_sequence"));
                value.setSignatureSampleFileUrl(
                        result.getString("signature_sample_file_url"));
                value.setSignatureSampleHash(result.getString("signature_sample_hash"));
                value.setSignatureSampleTime(result.getTimestamp("signature_sample_time"));
                value.setSealImageUrlSnapshot(result.getString("seal_image_url_snapshot"));
                value.setSealImageHashSnapshot(result.getString("seal_image_hash_snapshot"));
                value.setFinalDocumentVersion(result.getString("final_document_version"));
                value.setFinalDocumentRootHash(result.getString("final_document_root_hash"));
                value.setFinalGeneratedTime(result.getTimestamp("final_generated_time"));
                value.setFinalConfirmedTime(result.getTimestamp("final_confirmed_time"));
                value.setFinalConfirmationStatus(
                        result.getString("final_confirmation_status"));
                value.setFinalArchiveRootHash(result.getString("final_archive_root_hash"));
                value.setFinalEvidenceGeneratedTime(
                        result.getTimestamp("final_evidence_generated_time"));
                assertThat(result.next()).isFalse();
                return value;
            }
        }
    }

    private List<OaSignPackageDocument> loadDocumentsForDryRun(Connection connection,
            long packageId) throws Exception
    {
        List<OaSignPackageDocument> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT document_id, package_id, template_id, template_type, document_name,
                       template_version_snapshot, source_file_url_snapshot,
                       review_pdf_url, review_pdf_hash, final_pdf_url, final_pdf_hash,
                       final_content_hash, final_archive_pdf_url, final_archive_pdf_hash,
                       signature_file_url, signature_hash, final_document_version,
                       employee_visible, employee_sign_required, signature_position_json,
                       company_seal_position_json, company_seal_required,
                       document_policy_mode, signed
                  FROM oa_sign_package_document
                 WHERE package_id = ? ORDER BY sort_order, document_id
                """))
        {
            statement.setLong(1, packageId);
            try (ResultSet result = statement.executeQuery())
            {
                while (result.next())
                {
                    OaSignPackageDocument value = new OaSignPackageDocument();
                    value.setDocumentId(result.getLong("document_id"));
                    value.setPackageId(result.getLong("package_id"));
                    value.setTemplateId(nullableLong(result, "template_id"));
                    value.setTemplateType(result.getString("template_type"));
                    value.setDocumentName(result.getString("document_name"));
                    value.setTemplateVersionSnapshot(
                            result.getString("template_version_snapshot"));
                    value.setSourceFileUrlSnapshot(
                            result.getString("source_file_url_snapshot"));
                    value.setReviewPdfUrl(result.getString("review_pdf_url"));
                    value.setReviewPdfHash(result.getString("review_pdf_hash"));
                    value.setFinalPdfUrl(result.getString("final_pdf_url"));
                    value.setFinalPdfHash(result.getString("final_pdf_hash"));
                    value.setFinalContentHash(result.getString("final_content_hash"));
                    value.setFinalArchivePdfUrl(
                            result.getString("final_archive_pdf_url"));
                    value.setFinalArchivePdfHash(
                            result.getString("final_archive_pdf_hash"));
                    value.setSignatureFileUrl(result.getString("signature_file_url"));
                    value.setSignatureHash(result.getString("signature_hash"));
                    value.setFinalDocumentVersion(
                            result.getString("final_document_version"));
                    value.setEmployeeVisible(result.getString("employee_visible"));
                    value.setEmployeeSignRequired(
                            result.getString("employee_sign_required"));
                    value.setSignaturePositionJson(
                            result.getString("signature_position_json"));
                    value.setCompanySealPositionJson(
                            result.getString("company_seal_position_json"));
                    value.setCompanySealRequired(
                            result.getString("company_seal_required"));
                    value.setDocumentPolicyMode(result.getString("document_policy_mode"));
                    value.setSigned(result.getString("signed"));
                    values.add(value);
                }
            }
        }
        return values;
    }

    private OaSignFinalConfirmation loadConfirmationForDryRun(Connection connection,
            long packageId, String finalDocumentVersion) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT confirmation_id, package_id, employee_id, final_document_version,
                       document_root_hash, confirmed_time
                  FROM oa_sign_final_confirmation
                 WHERE package_id = ? AND final_document_version = ?
                """))
        {
            statement.setLong(1, packageId);
            statement.setString(2, finalDocumentVersion);
            try (ResultSet result = statement.executeQuery())
            {
                assertThat(result.next()).isTrue();
                OaSignFinalConfirmation value = new OaSignFinalConfirmation();
                value.setConfirmationId(result.getLong("confirmation_id"));
                value.setPackageId(result.getLong("package_id"));
                value.setEmployeeId(nullableLong(result, "employee_id"));
                value.setFinalDocumentVersion(result.getString("final_document_version"));
                value.setDocumentRootHash(result.getString("document_root_hash"));
                value.setConfirmedTime(result.getTimestamp("confirmed_time"));
                assertThat(result.next()).isFalse();
                return value;
            }
        }
    }

    private List<OaSignFinalConfirmationDocument> loadConfirmationDocumentsForDryRun(
            Connection connection, long packageId, String finalDocumentVersion)
            throws Exception
    {
        List<OaSignFinalConfirmationDocument> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT confirmation_document_id, confirmation_id, package_id, document_id,
                       final_document_version, final_pdf_hash
                  FROM oa_sign_final_confirmation_document
                 WHERE package_id = ? AND final_document_version = ?
                 ORDER BY document_id, confirmation_document_id
                """))
        {
            statement.setLong(1, packageId);
            statement.setString(2, finalDocumentVersion);
            try (ResultSet result = statement.executeQuery())
            {
                while (result.next())
                {
                    OaSignFinalConfirmationDocument value =
                            new OaSignFinalConfirmationDocument();
                    value.setConfirmationDocumentId(
                            result.getLong("confirmation_document_id"));
                    value.setConfirmationId(result.getLong("confirmation_id"));
                    value.setPackageId(result.getLong("package_id"));
                    value.setDocumentId(result.getLong("document_id"));
                    value.setFinalDocumentVersion(
                            result.getString("final_document_version"));
                    value.setFinalPdfHash(result.getString("final_pdf_hash"));
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    private BusinessFingerprints businessFingerprints(Connection connection) throws Exception
    {
        return new BusinessFingerprints(
                tableFingerprint(connection,
                        "SELECT * FROM oa_sign_package ORDER BY package_id"),
                tableFingerprint(connection,
                        "SELECT * FROM oa_sign_package_document ORDER BY document_id"),
                tableFingerprint(connection,
                        "SELECT * FROM oa_sign_final_confirmation ORDER BY confirmation_id"),
                tableFingerprint(connection, """
                        SELECT * FROM oa_sign_final_confirmation_document
                         ORDER BY confirmation_document_id
                        """));
    }

    private DatabaseIdentity loadDatabaseIdentity(Connection connection) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT @@server_uuid AS server_uuid,
                       @@hostname AS server_hostname,
                       @@port AS server_port,
                       DATABASE() AS database_name
                """); ResultSet result = statement.executeQuery())
        {
            assertThat(result.next()).isTrue();
            DatabaseIdentity identity = new DatabaseIdentity(
                    result.getString("server_uuid"),
                    result.getString("server_hostname"),
                    result.getInt("server_port"),
                    result.getString("database_name"));
            assertThat(identity.serverUuid()).isNotBlank();
            assertThat(identity.serverHostname()).isNotBlank();
            assertThat(identity.serverPort()).isPositive();
            assertThat(identity.databaseName()).isNotBlank();
            assertThat(result.next()).isFalse();
            return identity;
        }
    }

    private String tableFingerprint(Connection connection, String sql) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery())
        {
            ResultSetMetaData metadata = result.getMetaData();
            while (result.next())
            {
                for (int column = 1; column <= metadata.getColumnCount(); column++)
                {
                    digest.update(metadata.getColumnLabel(column)
                            .getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '=');
                    String value = result.getString(column);
                    digest.update((value == null ? "<NULL>" : value)
                            .getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                }
                digest.update((byte) 0);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void writeEvidence(String stage, Map<Long, String> hashes,
            List<EnabledLaborBinding> enabledLabor,
            Map<Long, List<ActivePlanTemplateCount>> activePlanTemplates,
            ActiveLegalEntitySummary legalEntities,
            DatabaseIdentity databaseIdentity,
            List<InFlightGroup> inFlightCoverage,
            InFlightDryRunEvidence inFlightDryRun,
            List<CoverageGroup> coverage, DryRunEvidence dryRun) throws Exception
    {
        String output = System.getenv("ERP_SIGN_PLACEMENT_EVIDENCE_OUTPUT");
        if (output == null || output.isBlank())
        {
            return;
        }
        Path path = Path.of(output).toAbsolutePath().normalize();
        if (path.getParent() == null)
        {
            throw new IllegalArgumentException("evidence output must have a parent directory");
        }
        Files.createDirectories(path.getParent());
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schemaVersion", 1);
        evidence.put("databaseVerifierExecuted", true);
        evidence.put("stage", stage);
        evidence.put("databaseIdentity", databaseIdentity);
        evidence.put("formalHashImplementation",
                OaSignPlanVersionFingerprint.class.getName());
        evidence.put("recomputedCandidateHashes", hashes);
        evidence.put("reviewedActiveLaborPlanCount", enabledLabor.size());
        evidence.put("reviewedActivePlanHandbookCounts", enabledLabor.stream()
                .collect(java.util.stream.Collectors.toMap(
                        value -> Long.toString(value.planId()),
                        EnabledLaborBinding::employeeVisibleHandbookCount,
                        (left, right) -> left, TreeMap::new)));
        evidence.put("reviewedActivePlanTemplateCounts", activePlanTemplates);
        evidence.put("activeLegalEntityCount", legalEntities.activeCount());
        evidence.put("incompleteActiveLegalEntityCount", legalEntities.incompleteCount());
        long inFlightPackages = inFlightCoverage.stream()
                .mapToLong(InFlightGroup::packageCount).sum();
        evidence.put("nonTerminalLaborPackageCount", inFlightPackages);
        evidence.put("unsupportedNonTerminalLaborGroupCount", inFlightCoverage.stream()
                .filter(group -> !group.supported()).count());
        evidence.put("nonTerminalLaborGroups", inFlightCoverage);
        evidence.put("nonTerminalLaborActualFileVerification", Map.of(
                "verifiedDocumentCount", inFlightDryRun.verifiedDocumentIds().size(),
                "actualFileCheckCount", inFlightDryRun.actualFileCheckCount(),
                "businessFingerprintsBefore", inFlightDryRun.before(),
                "businessFingerprintsAfter", inFlightDryRun.after(),
                "businessFingerprintsUnchanged",
                inFlightDryRun.before().equals(inFlightDryRun.after())));
        evidence.put("signedConfirmedLaborContractGroupCount", coverage.size());
        long coveredDocuments = coverage.stream()
                .mapToLong(CoverageGroup::documentCount).sum();
        evidence.put("signedConfirmedLaborContractDocumentCount", coveredDocuments);
        evidence.put("historicalCoverageStatus", coveredDocuments == 0
                ? "COVERAGE_ZERO_NO_HISTORICAL_EXPORT_CLAIM"
                : "FORMAL_EXPORT_DRY_RUN_VERIFIED");
        evidence.put("unsupportedGroupCount",
                coverage.stream().filter(group -> !group.supported()).count());
        evidence.put("coverageGroups", coverage);
        evidence.put("formalExportDryRun", Map.of(
                "verifiedDocumentCount", dryRun.verifiedDocumentIds().size(),
                "attemptCount", dryRun.attemptCount(),
                "businessFingerprintsBefore", dryRun.before(),
                "businessFingerprintsAfter", dryRun.after(),
                "businessFingerprintsUnchanged", dryRun.before().equals(dryRun.after()),
                "derivedFilesRemaining", dryRun.derivedFilesRemaining()));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), evidence);
    }

    private String sha256(Path path) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path))
        {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0)
            {
                if (read > 0)
                {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private Long nullableLong(ResultSet result, String column) throws Exception
    {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private boolean hasColumn(ResultSet result, String column) throws Exception
    {
        for (int index = 1; index <= result.getMetaData().getColumnCount(); index++)
        {
            if (column.equalsIgnoreCase(result.getMetaData().getColumnLabel(index)))
            {
                return true;
            }
        }
        return false;
    }

    private String requiredEnv(String name)
    {
        String value = System.getenv(name);
        assertThat(value).as("environment %s", name).isNotBlank();
        return value;
    }

    private record CoverageGroup(String templateVersion, String sourceFileHash,
            String documentPolicyMode, String evidenceState,
            String legalRepresentativeState, String handbookState,
            long documentCount, boolean supported)
    {
    }

    private record InFlightRow(long packageId, long documentId, String packageStatus,
            String signingSequence, String templateVersion, String sourceFileHash,
            String documentPolicyMode, String sourceFileUrlSnapshot, String reviewPdfUrl,
            String reviewPdfHash, String employeeSignRequired, String companySealRequired,
            String signaturePositionJson, String companySealPositionJson,
            Long legalEntityIdSnapshot, String legalEntityNameSnapshot,
            String legalRepresentativeSnapshot, Date companyFrozenTime,
            String sealImageUrlSnapshot, String sealImageHashSnapshot,
            String finalDocumentVersion, String finalDocumentRootHash,
            Date finalGeneratedTime, String finalConfirmationStatus,
            String signatureEvidenceState, String finalCandidateState,
            String handbookState)
    {
        InFlightRow withTemplateVersion(String value)
        {
            return copy(value, sourceFileHash, signaturePositionJson,
                    companySealPositionJson, handbookState);
        }

        InFlightRow withSourceHash(String value)
        {
            return copy(templateVersion, value, signaturePositionJson,
                    companySealPositionJson, handbookState);
        }

        InFlightRow withHandbookState(String value)
        {
            return copy(templateVersion, sourceFileHash, signaturePositionJson,
                    companySealPositionJson, value);
        }

        InFlightRow withLifecycle(String status, String confirmationStatus,
                String signatureState, String candidateState)
        {
            return new InFlightRow(packageId, documentId, status, signingSequence,
                    templateVersion, sourceFileHash, documentPolicyMode,
                    sourceFileUrlSnapshot, reviewPdfUrl, reviewPdfHash,
                    employeeSignRequired, companySealRequired, signaturePositionJson,
                    companySealPositionJson, legalEntityIdSnapshot, legalEntityNameSnapshot,
                    legalRepresentativeSnapshot, companyFrozenTime, sealImageUrlSnapshot,
                    sealImageHashSnapshot, finalDocumentVersion, finalDocumentRootHash,
                    finalGeneratedTime, confirmationStatus, signatureState, candidateState,
                    handbookState);
        }

        private InFlightRow copy(String version, String sourceHash, String signatureJson,
                String sealJson, String handbook)
        {
            return new InFlightRow(packageId, documentId, packageStatus, signingSequence,
                    version, sourceHash, documentPolicyMode, sourceFileUrlSnapshot,
                    reviewPdfUrl, reviewPdfHash, employeeSignRequired, companySealRequired,
                    signatureJson, sealJson, legalEntityIdSnapshot, legalEntityNameSnapshot,
                    legalRepresentativeSnapshot, companyFrozenTime, sealImageUrlSnapshot,
                    sealImageHashSnapshot, finalDocumentVersion, finalDocumentRootHash,
                    finalGeneratedTime, finalConfirmationStatus, signatureEvidenceState,
                    finalCandidateState, handbook);
        }
    }

    private record InFlightKey(String packageStatus, String signingSequence,
            String templateVersion, String sourceFileHash, String signaturePolicyMode,
            String sealPolicyMode, String signatureSequencePolicy,
            String sealSequencePolicy, String evidenceState,
            String finalConfirmationStatus, String signatureEvidenceState,
            String finalCandidateState, String handbookState, boolean supported)
            implements Comparable<InFlightKey>
    {
        @Override
        public int compareTo(InFlightKey other)
        {
            return canonical().compareTo(other.canonical());
        }

        private String canonical()
        {
            return String.join("\u0000", packageStatus, signingSequence, templateVersion,
                    sourceFileHash, signaturePolicyMode, sealPolicyMode,
                    signatureSequencePolicy, sealSequencePolicy, evidenceState,
                    String.valueOf(finalConfirmationStatus), signatureEvidenceState,
                    finalCandidateState, handbookState, Boolean.toString(supported));
        }
    }

    private record InFlightGroup(String packageStatus, String signingSequence,
            String templateVersion, String sourceFileHash, String signaturePolicyMode,
            String sealPolicyMode, String signatureSequencePolicy,
            String sealSequencePolicy, String evidenceState,
            String finalConfirmationStatus, String signatureEvidenceState,
            String finalCandidateState, String handbookState, long packageCount,
            boolean supported)
    {
    }

    private record InFlightDryRunEvidence(Set<Long> verifiedDocumentIds,
            int actualFileCheckCount, BusinessFingerprints before,
            BusinessFingerprints after)
    {
    }

    private record ReadOnlyFileContext(OaSignDocumentService documentService,
            OaSignFileStorageService storage, Path fileRoot)
    {
    }

    private record CoverageRow(long packageId, long documentId, String templateVersion,
            String sourceFileHash, String documentPolicyMode, String evidenceState,
            String legalRepresentativeState, String handbookState, Date confirmedTime)
    {
        CoverageRow withDocumentPolicyMode(String value)
        {
            return new CoverageRow(packageId, documentId, templateVersion, sourceFileHash,
                    value, evidenceState, legalRepresentativeState, handbookState,
                    confirmedTime);
        }

        CoverageRow withEvidenceState(String value)
        {
            return new CoverageRow(packageId, documentId, templateVersion, sourceFileHash,
                    documentPolicyMode, value, legalRepresentativeState, handbookState,
                    confirmedTime);
        }

        CoverageRow withRepresentativeState(String value)
        {
            return new CoverageRow(packageId, documentId, templateVersion, sourceFileHash,
                    documentPolicyMode, evidenceState, value, handbookState, confirmedTime);
        }

        CoverageRow withHandbookState(String value)
        {
            return new CoverageRow(packageId, documentId, templateVersion, sourceFileHash,
                    documentPolicyMode, evidenceState, legalRepresentativeState, value,
                    confirmedTime);
        }
    }

    private record CoverageKey(String templateVersion, String sourceFileHash,
            String documentPolicyMode, String evidenceState,
            String legalRepresentativeState, String handbookState)
            implements Comparable<CoverageKey>
    {
        @Override
        public int compareTo(CoverageKey other)
        {
            return canonical().compareTo(other.canonical());
        }

        private String canonical()
        {
            return String.join("\u0000", templateVersion, sourceFileHash,
                    documentPolicyMode, evidenceState, legalRepresentativeState,
                    handbookState);
        }
    }

    private record BusinessFingerprints(String packages, String documents,
            String confirmations, String confirmationDocuments)
    {
    }

    private record DatabaseIdentity(String serverUuid, String serverHostname,
            int serverPort, String databaseName)
    {
    }

    private record DryRunEvidence(Set<Long> verifiedDocumentIds, int attemptCount,
            BusinessFingerprints before, BusinessFingerprints after,
            long derivedFilesRemaining)
    {
    }

    private record ActiveLegalEntitySummary(long activeCount, long incompleteCount)
    {
    }

    private record EnabledLaborBinding(long planId, long versionId, String versionHash,
            long templateId, String templateVersion, String sourceFileUrl,
            String sourceFileHash, long employeeVisibleHandbookCount)
    {
        private EnabledLaborBinding withEmployeeVisibleHandbookCount(long count)
        {
            return new EnabledLaborBinding(planId, versionId, versionHash, templateId,
                    templateVersion, sourceFileUrl, sourceFileHash, count);
        }
    }

    private record ActivePlanTemplateCount(String templateType, long totalCount,
            long employeeVisibleCount)
    {
    }
}
