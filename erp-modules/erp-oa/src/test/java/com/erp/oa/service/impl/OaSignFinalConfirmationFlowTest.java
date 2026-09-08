package com.erp.oa.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.erp.common.core.context.SecurityContextHolder;
import com.erp.oa.constant.OaSignPackageStatus;
import com.erp.oa.domain.OaSignFinalConfirmation;
import com.erp.oa.domain.OaSignEvent;
import com.erp.oa.domain.OaSignPackage;
import com.erp.oa.domain.OaSignPackageDocument;
import com.erp.oa.domain.dto.OaSignFinalConfirmRequest;
import com.erp.oa.mapper.OaSignEventMapper;
import com.erp.oa.mapper.OaSignFileEvidenceMapper;
import com.erp.oa.mapper.OaSignFinalConfirmationMapper;
import com.erp.oa.mapper.OaSignPackageDocumentMapper;
import com.erp.oa.mapper.OaSignPackageMapper;
import com.erp.oa.domain.vo.SignedPdfResult;

@DisplayName("员工最终合同确认流程")
class OaSignFinalConfirmationFlowTest
{
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.remove();
    }

    @Test
    @DisplayName("补齐公司和印章后员工只确认最终合同，不再次提交签名")
    void shouldConfirmFinalContractWithoutAnotherSignature() throws Exception
    {
        OaSignPackageServiceImpl service = new OaSignPackageServiceImpl();
        OaSignPackageMapper packageMapper = mock(OaSignPackageMapper.class);
        OaSignPackageDocumentMapper documentMapper = mock(OaSignPackageDocumentMapper.class);
        OaSignFinalConfirmationMapper confirmationMapper = mock(OaSignFinalConfirmationMapper.class);
        OaSignEventMapper eventMapper = mock(OaSignEventMapper.class);
        OaSignFileEvidenceMapper evidenceMapper = mock(OaSignFileEvidenceMapper.class);
        OaSignDocumentService documentService = mock(OaSignDocumentService.class);
        OaSignedPdfService signedPdfService = mock(OaSignedPdfService.class);
        OaSignPlacementPolicyService placementPolicyService =
                mock(OaSignPlacementPolicyService.class);
        ReflectionTestUtils.setField(service, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "finalConfirmationMapper", confirmationMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "evidenceMapper", evidenceMapper);
        ReflectionTestUtils.setField(service, "documentService", documentService);
        ReflectionTestUtils.setField(service, "signedPdfService", signedPdfService);
        ReflectionTestUtils.setField(service, "placementPolicyService", placementPolicyService);

        SecurityContextHolder.setUserId("960");
        SecurityContextHolder.setUserName("员工甲");
        Path finalPdf = tempDir.resolve("final-contract.pdf");
        Files.writeString(finalPdf, "%PDF-1.4 final", StandardCharsets.ISO_8859_1);
        String finalPdfHash = sha256(Files.readAllBytes(finalPdf));
        byte[] firstSignaturePng = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x41 };
        Path firstSignature = tempDir.resolve("first-signature.png");
        Files.write(firstSignature, firstSignaturePng);
        String contentHash = sha256("stable-final-content".getBytes(StandardCharsets.UTF_8));
        String rootHash = sha256(("51:" + contentHash + "\n")
                .getBytes(StandardCharsets.UTF_8));
        String archiveHash = sha256("confirmed-archive".getBytes(StandardCharsets.UTF_8));

        OaSignPackage signPackage = new OaSignPackage();
        signPackage.setPackageId(500L);
        signPackage.setEmployeeId(960L);
        signPackage.setStatus(OaSignPackageStatus.PENDING_FINAL_CONFIRM);
        signPackage.setSigningSequence("SIGNATURE_FIRST");
        signPackage.setSignatureSampleFileUrl("/profile/task-signature.png");
        signPackage.setSignatureSampleHash(sha256(firstSignaturePng));
        signPackage.setSignatureSampleTime(new Date());
        signPackage.setFinalDocumentVersion("最终合同第1版");
        signPackage.setFinalDocumentRootHash(rootHash);
        signPackage.setSignDeadline(Date.from(Instant.parse("2099-12-31T15:59:59Z")));
        signPackage.setDeadlinePolicySource("PLAN_VERSION");
        signPackage.setDeadlineDaysSnapshot(7);
        signPackage.setVersion(7L);
        OaSignPackageDocument document = new OaSignPackageDocument();
        document.setDocumentId(51L);
        document.setPackageId(500L);
        document.setEmployeeVisible("Y");
        document.setFinalDocumentVersion("最终合同第1版");
        document.setFinalPdfUrl("/profile/final-contract.pdf");
        document.setFinalPdfHash(finalPdfHash);
        document.setFinalContentHash(contentHash);
        document.setFinalReadConfirmed("Y");
        document.setFinalReadConfirmedTime(new Date());
        when(packageMapper.selectOaSignPackageById(500L)).thenReturn(signPackage);
        when(documentMapper.selectDocumentsByPackageId(500L)).thenReturn(List.of(document));
        when(documentService.resolveGeneratedSignPackageFile(document.getFinalPdfUrl()))
                .thenReturn(finalPdf);
        when(documentService.resolveGeneratedSignPackageFile(
                signPackage.getSignatureSampleFileUrl())).thenReturn(firstSignature);
        when(placementPolicyService.requiresEmployeeSignature(document)).thenReturn(true);
        SignedPdfResult archive = new SignedPdfResult(
                "task-none/package-500/final-v1/archive.pdf",
                "/profile/private/sign-package/task-none/package-500/final-v1/archive.pdf",
                archiveHash, 128L, null, null, null, 0L, null, 2, contentHash);
        when(signedPdfService.archiveConfirmedFinalPdf(any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(archive);
        when(confirmationMapper.insertConfirmation(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, OaSignFinalConfirmation.class).setConfirmationId(800L);
            return 1;
        });
        when(confirmationMapper.insertConfirmationDocument(any())).thenReturn(1);
        when(documentMapper.updateOaSignPackageDocument(any())).thenReturn(1);
        when(packageMapper.updateFinalConfirmed(eq(500L),
                eq(OaSignPackageStatus.PENDING_FINAL_CONFIRM), eq(7L), any(), any(), any(),
                eq("员工甲")))
                .thenReturn(1);
        OaSignEvent opened = new OaSignEvent();
        opened.setPackageId(500L);
        opened.setDocumentId(51L);
        opened.setEventType("FINAL_DOCUMENT_OPENED");
        opened.setDocumentHash(finalPdfHash);
        opened.setEventPayload("version=最终合同第1版;hash=" + finalPdfHash);
        when(eventMapper.selectEventsByPackageId(500L)).thenReturn(List.of(opened));

        OaSignFinalConfirmRequest request = new OaSignFinalConfirmRequest();
        request.setFinalDocumentVersion("最终合同第1版");
        request.setDocumentRootHash(rootHash);
        request.setConfirmationText("本人已阅读并确认最终合同中的公司及印章信息");
        request.setRequestId("final-confirm-500");

        OaSignPackage result = service.confirmFinalPackage(500L, request,
                "127.0.0.1", "测试浏览器");

        assertThat(result).isNotSameAs(signPackage);
        assertThat(result.getPackageId()).isEqualTo(signPackage.getPackageId());
        assertThat(result.getEmployeeId()).isNull();
        ArgumentCaptor<OaSignFinalConfirmation> confirmation =
                ArgumentCaptor.forClass(OaSignFinalConfirmation.class);
        verify(confirmationMapper).insertConfirmation(confirmation.capture());
        assertThat(confirmation.getValue().getIdentityMethod()).isEqualTo("LOGIN_TOKEN");
        assertThat(confirmation.getValue().getFinalDocumentVersion()).isEqualTo("最终合同第1版");
        assertThat(confirmation.getValue().getConfirmedTime().getTime() % 1000L).isZero();
        ArgumentCaptor<OaSignEvent> confirmedEvent = ArgumentCaptor.forClass(OaSignEvent.class);
        verify(eventMapper).insertOaSignEvent(confirmedEvent.capture());
        assertThat(confirmedEvent.getValue().getEventType()).isEqualTo("FINAL_CONTRACT_CONFIRMED");
        assertThat(confirmedEvent.getValue().getCreateTime())
                .isEqualTo(confirmation.getValue().getConfirmedTime());
        verify(confirmationMapper).insertConfirmationDocument(any());
        verify(packageMapper).updateFinalConfirmed(eq(500L),
                eq(OaSignPackageStatus.PENDING_FINAL_CONFIRM), eq(7L), any(), any(), any(),
                eq("员工甲"));
        ArgumentCaptor<OaSignPackageDocument> documentUpdate =
                ArgumentCaptor.forClass(OaSignPackageDocument.class);
        verify(documentMapper).updateOaSignPackageDocument(documentUpdate.capture());
        assertThat(documentUpdate.getValue().getFinalArchivePdfUrl())
                .isEqualTo(archive.getSignedPdfUrl());
        assertThat(documentUpdate.getValue().getFinalArchivePdfHash())
                .isEqualTo(archiveHash);
        assertThat(documentUpdate.getValue().getSigned()).isEqualTo("Y");
        assertThat(documentUpdate.getValue().getStatus())
                .isEqualTo(OaSignPackageStatus.SIGNED);
        ArgumentCaptor<byte[]> archivedSignature = ArgumentCaptor.forClass(byte[].class);
        verify(signedPdfService).archiveConfirmedFinalPdf(any(), any(), any(), any(),
                any(), any(), any(), archivedSignature.capture(), any(), any(), any(),
                any(), any(), any());
        assertThat(archivedSignature.getValue()).containsExactly(firstSignaturePng);
    }

    private static String sha256(byte[] bytes) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
