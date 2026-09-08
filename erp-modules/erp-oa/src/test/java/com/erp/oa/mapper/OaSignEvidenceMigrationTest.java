package com.erp.oa.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.erp.oa.constant.OaSignFileEvidenceType;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;

@DisplayName("签约文件证据迁移")
class OaSignEvidenceMigrationTest
{
    @Test
    @DisplayName("迁移只新增兼容证据字段且部署脚本一致")
    void shouldShipCompatibleEvidenceMigration() throws Exception
    {
        String sql = readRepoFile("sql/erp_oa_sign_evidence_20260711.sql");
        String dockerSql = readRepoFile("docker/mysql/db/erp_oa_sign_evidence_20260711.sql");

        assertThat(dockerSql).isEqualTo(sql);
        assertThat(sql)
                .contains("ADD COLUMN document_version varchar(64)")
                .contains("ADD COLUMN sign_deadline datetime")
                .contains("ADD COLUMN version bigint NOT NULL DEFAULT 0")
                .contains("ADD COLUMN review_pdf_url varchar(500)")
                .contains("ADD COLUMN review_pdf_hash varchar(64)")
                .contains("ADD COLUMN signed_pdf_url varchar(500)")
                .contains("ADD COLUMN signed_pdf_hash varchar(64)")
                .contains("ADD COLUMN signature_file_url varchar(500)")
                .contains("ADD COLUMN signature_hash varchar(64)")
                .contains("ADD COLUMN certificate_hash varchar(64)")
                .contains("ADD COLUMN request_id varchar(64)")
                .contains("uk_oa_sign_event_request")
                .contains("event_type, request_id")
                .contains("CREATE TABLE IF NOT EXISTS oa_sign_file_evidence")
                .contains("UNIQUE KEY uk_oa_sign_file_evidence_version")
                .contains("document_id, document_version, evidence_type")
                .doesNotContain("SET review_pdf_hash =")
                .doesNotContain("SET signed_pdf_hash =")
                .doesNotContain("SET certificate_hash =");
    }

    @Test
    @DisplayName("应用枚举和领域对象公开全部证据元数据")
    void shouldExposeEvidenceMetadataInApplicationModel()
    {
        assertThat(OaSignFileEvidenceType.values()).extracting(Enum::name)
                .containsExactly("TEMPLATE_SOURCE", "RENDERED_SOURCE", "REVIEW_PDF",
                        "SIGNATURE_SAMPLE", "SIGNATURE_IMAGE",
                        "SIGNED_PDF", "SIGN_CERTIFICATE", "COMPANY_SEAL",
                        "FINAL_RENDERED_SOURCE", "FINAL_REVIEW_PDF", "FINAL_SIGNED_PDF",
                        "FINAL_PENDING_PDF", "FINAL_ARCHIVE_PDF");

        OaSignPackage signPackage = new OaSignPackage();
        assertThat(signPackage.getVersion()).as("new packages must satisfy the non-null database default").isZero();
        signPackage.setDocumentVersion("SP-10-V1");
        signPackage.setVersion(0L);
        assertThat(signPackage.getDocumentVersion()).isEqualTo("SP-10-V1");
        assertThat(signPackage.getVersion()).isZero();

        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setReviewPdfHash("review");
        document.setSignedPdfHash("signed");
        document.setSignatureHash("signature");
        document.setCertificateHash("certificate");
        document.setFinalContentHash("stable-content");
        document.setFinalArchivePdfHash("archive");
        assertThat(document.getReviewPdfHash()).isEqualTo("review");
        assertThat(document.getSignedPdfHash()).isEqualTo("signed");
        assertThat(document.getSignatureHash()).isEqualTo("signature");
        assertThat(document.getCertificateHash()).isEqualTo("certificate");
        assertThat(document.getFinalContentHash()).isEqualTo("stable-content");
        assertThat(document.getFinalArchivePdfHash()).isEqualTo("archive");

        OaSignEvent event = new OaSignEvent();
        event.setRequestId("request-1");
        assertThat(event.getRequestId()).isEqualTo("request-1");
    }

    @Test
    @DisplayName("双顺序证据迁移固化确认快照和签名幂等负载")
    void shouldShipDualSequenceEvidenceMigration() throws Exception
    {
        String migration = "erp_oa_sign_dual_sequence_evidence_20260720.sql";
        String sql = readRepoFile("sql/" + migration);
        String dockerSql = readRepoFile("docker/mysql/db/" + migration);
        String bootstrapFiles = readRepoFile("docker/mysql/bootstrap-files.list");

        assertThat(dockerSql).isEqualTo(sql);
        assertThat(bootstrapFiles.lines().filter(migration::equals).count()).isEqualTo(1L);
        assertThat(sql)
                .contains("'oa_sign_package', 'signing_sequence'")
                .contains("'oa_sign_onboard_data_request', 'fact_snapshot_json'")
                .contains("'oa_sign_onboard_data_request', 'confirmation_snapshot_version'")
                .contains("'oa_sign_onboard_data_request', 'confirmation_snapshot_hash'")
                .contains("'oa_sign_onboard_data_request', 'signature_payload_hash'")
                .contains("'oa_sign_onboard_data_request', 'signature_sample_bytes'")
                .contains("UNIQUE INDEX uk_oa_sign_onboard_signature_request (signature_request_id)")
                .doesNotContain("DELETE FROM oa_sign_")
                .doesNotContain("UPDATE oa_sign_");
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
