package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Date;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.vo.OaSignOnboardDataRequestView;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

class OaSignStagedPackageMapperBindingTest
{
    @Test
    void dataRequestViewExposesStringSerializedStagedTaskAndPackageIds() throws Exception
    {
        JsonSerialize taskId = OaSignOnboardDataRequestView.class
                .getDeclaredField("taskId").getAnnotation(JsonSerialize.class);
        JsonSerialize packageId = OaSignOnboardDataRequestView.class
                .getDeclaredField("packageId").getAnnotation(JsonSerialize.class);

        assertThat(taskId).isNotNull();
        assertThat(taskId.using()).isEqualTo(ToStringSerializer.class);
        assertThat(packageId).isNotNull();
        assertThat(packageId.using()).isEqualTo(ToStringSerializer.class);
    }

    @Test
    void stagedSignatureFirstLifecycleUsesExactPackageTaskVersionCas() throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignPackageMapper.xml");
        Date now = new Date();
        OaSignPackage signPackage = stagedPackage(now);

        String sendShell = sql(configuration, "sendStagedShell", signPackage, null, 3L);
        String recordSignature = sql(configuration, "recordStagedEmployeeSignature",
                signPackage, "part_viewed", 4L);
        String prepareCandidate = sql(configuration, "prepareStagedFinalCandidate",
                signPackage, "STAGED-101-V1", 5L);
        String sendCandidate = sql(configuration, "sendStagedFinalCandidate",
                signPackage, null, 6L);

        assertThat(sendShell)
                .contains("status = 'pending_sign'", "document_version = ?", "sent_time = ?")
                .contains("package_id = ?", "task_id = ?", "status = 'draft'", "version = ?")
                .contains("signing_sequence = 'SIGNATURE_FIRST'")
                .contains("document_version is null", "sent_time is null", "initial_signed_time is null")
                .contains("final_confirmation_status is null", "version = version + 1");
        assertThat(recordSignature)
                .contains("status = 'pending_company'", "signed_time = ?", "initial_signed_time = ?")
                .contains("signature_sample_file_url = ?", "signature_sample_hash = ?")
                .contains("final_confirmation_status = 'WAITING_COMPANY'")
                .contains("sign_deadline >= ?")
                .contains("status = ?", "? in ('pending_sign', 'part_viewed')")
                .contains("signed_time is null", "initial_signed_time is null")
                .contains("signature_sample_file_url is null", "version = version + 1");
        assertThat(prepareCandidate)
                .contains("legal_entity_id_snapshot = ?", "seal_id_snapshot = ?")
                .contains("document_version = ?", "final_document_version = ?")
                .contains("final_document_root_hash = ?", "final_generated_time = ?")
                .contains("final_confirmation_status = 'PREPARED_NOT_SENT'")
                .contains("status = 'pending_company'", "document_version = ?")
                .contains("final_confirmation_status = 'WAITING_COMPANY'")
                .contains("final_document_version is null", "version = version + 1");
        assertThat(sendCandidate)
                .contains("status = 'pending_final_confirm'")
                .contains("final_confirmation_status = 'PENDING'")
                .contains("status = 'pending_company'", "version = ?")
                .contains("final_confirmation_status = 'PREPARED_NOT_SENT'")
                .contains("company_frozen_time is not null", "document_version = ?",
                        "final_document_version = ?")
                .contains("version = version + 1")
                .doesNotContain("sent_time = ?");
    }

    @Test
    void timelyFrozenStagedSignatureIsProtectedAtExpirySelectionAndTerminalCas()
            throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignPackageMapper.xml");
        String candidates = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignPackageMapper.selectExpiredCandidates")
                .getBoundSql(Map.of("dueTime", new Date(), "limit", 100))
                .getSql().replaceAll("\\s+", " ").trim();
        String terminal = configuration.getMappedStatement(
                "com.erp.oa.mapper.OaSignPackageMapper.markTerminalWithVersion")
                .getBoundSql(Map.of("packageId", 101L, "currentStatus", "pending_sign",
                        "targetStatus", "expired", "expectedVersion", 4L,
                        "terminalTime", new Date(), "reasonCode", "DEADLINE",
                        "reasonDetail", "deadline", "updateBy", "system"))
                .getSql().replaceAll("\\s+", " ").trim();

        for (String sql : java.util.List.of(candidates, terminal))
        {
            assertThat(sql)
                    .contains("upper(trim(signing_sequence)) = 'SIGNATURE_FIRST'")
                    .contains("from oa_sign_onboard_import_row staged_row")
                    .contains("inner join oa_sign_onboard_data_request staged_request")
                    .contains("staged_row.package_id = oa_sign_package.package_id")
                    .contains("staged_row.task_id = oa_sign_package.task_id")
                    .contains("'SUBMITTED', 'APPROVED', 'PROFILE_SYNC_FAILED', 'COMPLETED'")
                    .contains("staged_request.signature_sample_bytes is not null")
                    .contains("octet_length(staged_request.signature_sample_bytes) > 0")
                    .contains("staged_request.signature_sample_time <= oa_sign_package.sign_deadline");
        }
        assertThat(terminal)
                .contains("? != 'expired' or not", "? in ('pending_sign', 'part_viewed')");
        assertThat(candidates).contains("status in ('pending_sign', 'part_viewed')");
    }

    @Test
    void finalCandidateSendAcceptsOnlyStrictSampleOrLegacySignedDocumentEvidence()
            throws Exception
    {
        Configuration configuration = configuration("mapper/oa/OaSignPackageMapper.xml");
        OaSignPackage signPackage = stagedPackage(new Date());
        String sendCandidate = sql(configuration, "sendStagedFinalCandidate",
                signPackage, null, 6L);

        assertThat(sendCandidate)
                .contains(
                        "signature_sample_file_url is not null",
                        "signature_sample_hash is not null",
                        "signature_sample_time is not null",
                        "from oa_sign_file_evidence staged_sample",
                        "staged_sample.document_id is null",
                        "upper(trim(staged_sample.evidence_type)) = 'SIGNATURE_SAMPLE'",
                        ") = 1",
                        "staged_sample.document_version regexp '^SAMPLE-[1-9][0-9]*$'",
                        "lower(trim(staged_sample.file_hash)) = lower(trim(signature_sample_hash))",
                        "staged_sample.file_size > 0",
                        "staged_sample.generated_time = signature_sample_time");
        assertThat(sendCandidate)
                .contains(
                        "signature_sample_file_url is null",
                        "signature_sample_hash is null",
                        "signature_sample_time is null",
                        "from oa_sign_file_evidence orphan_sample",
                        "upper(trim(orphan_sample.evidence_type)) = 'SIGNATURE_SAMPLE'",
                        "from oa_sign_package_document legacy_signed_document",
                        "upper(trim(legacy_signed_document.employee_sign_required)) = 'Y'",
                        "legacy_signed_document.signed is null",
                        "legacy_signed_document.document_version != oa_sign_package.document_version",
                        "count(distinct lower(trim(legacy_signature_document.signature_hash)))",
                        "from oa_sign_package_document legacy_evidence_document",
                        "upper(trim(legacy_signature_evidence.evidence_type)) = 'SIGNATURE_IMAGE'",
                        "upper(trim(legacy_signed_pdf_evidence.evidence_type)) = 'SIGNED_PDF'",
                        "upper(trim(legacy_certificate_evidence.evidence_type)) = 'SIGN_CERTIFICATE'",
                        "lower(trim(legacy_signature_evidence.file_hash)) = lower(trim(legacy_evidence_document.signature_hash))",
                        "lower(trim(legacy_signed_pdf_evidence.file_hash)) = lower(trim(legacy_evidence_document.signed_pdf_hash))",
                        "lower(trim(legacy_certificate_evidence.file_hash)) = lower(trim(legacy_evidence_document.certificate_hash))");
    }

    private OaSignPackage stagedPackage(Date now)
    {
        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(101L);
        signPackage.setTaskId(201L);
        signPackage.setSigningSequence("SIGNATURE_FIRST");
        signPackage.setDocumentVersion("STAGED-101-V2");
        signPackage.setSentTime(now);
        signPackage.setSignDeadline(new Date(now.getTime() + 86_400_000L));
        signPackage.setDeadlinePolicySource("PLAN_VERSION_SNAPSHOT");
        signPackage.setDeadlineDaysSnapshot(1);
        signPackage.setConfirmStatus("CONFIRMED");
        signPackage.setSignedTime(now);
        signPackage.setInitialSignedTime(now);
        signPackage.setSignatureSampleFileUrl("/signature/101.png");
        signPackage.setSignatureSampleHash("a".repeat(64));
        signPackage.setSignatureSampleTime(now);
        signPackage.setLegalEntityIdSnapshot(301L);
        signPackage.setLegalEntityNameSnapshot("签约公司");
        signPackage.setSealIdSnapshot(401L);
        signPackage.setSealNameSnapshot("公章");
        signPackage.setCompanyFrozenTime(now);
        signPackage.setFinalDocumentVersion("FINAL-101-V1");
        signPackage.setFinalDocumentRootHash("b".repeat(64));
        signPackage.setFinalGeneratedTime(now);
        signPackage.setUpdateBy("tester");
        return signPackage;
    }

    private String sql(Configuration configuration, String method,
            OaSignPackage signPackage, String extra, Long expectedVersion)
    {
        java.util.Map<String, Object> parameters = new java.util.HashMap<>();
        parameters.put("signPackage", signPackage);
        parameters.put("expectedVersion", expectedVersion);
        if ("recordStagedEmployeeSignature".equals(method))
        {
            parameters.put("currentStatus", extra);
        }
        if ("prepareStagedFinalCandidate".equals(method))
        {
            parameters.put("expectedDocumentVersion", extra);
        }
        BoundSql boundSql = configuration.getMappedStatement(
                OaSignPackageMapper.class.getName() + "." + method)
                .getBoundSql(parameters);
        return boundSql.getSql().replaceAll("\\s+", " ").trim();
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
